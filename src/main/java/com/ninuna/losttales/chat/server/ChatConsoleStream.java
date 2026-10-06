package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatReactionSummary;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Server Log's memory: the last {@link #MAX_EVENTS}
 * administrative events, in the order they happened, so a staff member
 * who joins is shown what went on before them. Written to the world
 * with the chat history ({@link ChatHistoryWorldData}) and read back
 * from it as the server starts, so a restart hands the console its past
 * as it hands every channel theirs; cleared with the rest of the
 * server's chat state. Who is shown an event is
 * {@code LostTalesChatService}'s decision, made from the
 * {@code chat.server_console.read} capability at the moment of sending and
 * again at the moment of replay.
 *
 * <p>An entry is a line of the Server Log like any other: its id
 * comes from the clock messages take theirs from, so a link, a reply and
 * a reaction name it as they name a message, and its reactions are kept
 * here beside it and saved with it.</p>
 *
 * <p>A command's entry says what it was and who ran it; what it carried
 * (a private message's words, a bot token, a webhook address) is left out
 * before the entry exists ({@link com.ninuna.losttales.chat.ChatCommandText}).</p>
 */
public final class ChatConsoleStream {
    /** Events kept; a busy evening of staff work fits. */
    public static final int MAX_EVENTS = 500;
    /** The most a joining staff member is shown. */
    public static final int MAX_REPLAY = 200;

    private static final LinkedHashMap<Long, ChatConsoleEvent> EVENTS =
            new LinkedHashMap<Long, ChatConsoleEvent>();
    /** The reactions on kept entries, by the entry's id; an entry nobody reacted to has none here. */
    private static final Map<Long, ChatReactions> REACTIONS =
            new HashMap<Long, ChatReactions>();

    private ChatConsoleStream() {}

    /**
     * Remembers an event; the oldest goes once the ring is full, and the
     * save is told.
     */
    public static synchronized void record(ChatConsoleEvent event) {
        if (event == null) {
            return;
        }
        EVENTS.put(Long.valueOf(event.getId()), event);
        trim();
        ChatHistory.changed();
    }

    /**
     * Hands the save's kept events back to the stream as the server
     * starts, oldest first, each under its own id with the reactions it
     * was saved with: one already held stays as it is. The id allocator
     * moves past the newest, so no event said from here on shares an id
     * with a kept one. Answers how many were taken.
     */
    public static synchronized int restore(Collection<ChatConsoleEvent> events,
                                           Map<Long, ChatReactions> reactions) {
        int kept = 0;
        long newest = 0L;
        if (events != null) {
            List<ChatConsoleEvent> ordered = new ArrayList<ChatConsoleEvent>();
            for (ChatConsoleEvent event : events) {
                if (event != null && event.getId() > 0L) {
                    ordered.add(event);
                }
            }
            Collections.sort(ordered, BY_ID);
            for (ChatConsoleEvent event : ordered) {
                Long id = Long.valueOf(event.getId());
                if (EVENTS.containsKey(id)) {
                    continue;
                }
                EVENTS.put(id, event);
                ChatReactions saved = reactions == null ? null : reactions.get(id);
                if (saved != null && !saved.isEmpty()) {
                    REACTIONS.put(id, saved);
                }
                kept++;
                newest = Math.max(newest, id.longValue());
            }
            trim();
        }
        ChatMessageIdAllocator.seed(newest);
        return kept;
    }

    /** Every kept event, oldest first: what the save writes. */
    public static synchronized List<ChatConsoleEvent> snapshot() {
        return new ArrayList<ChatConsoleEvent>(EVENTS.values());
    }

    /** The kept entries' reactions, by id, each a copy: what the save writes beside them. */
    public static synchronized Map<Long, ChatReactions> reactionsSnapshot() {
        Map<Long, ChatReactions> copies = new HashMap<Long, ChatReactions>();
        for (Map.Entry<Long, ChatReactions> kept : REACTIONS.entrySet()) {
            ChatReactions copy = new ChatReactions();
            for (Map.Entry<String, Map<UUID, String>> kind
                    : kept.getValue().snapshot().entrySet()) {
                for (Map.Entry<UUID, String> reactor : kind.getValue().entrySet()) {
                    copy.restore(kind.getKey(), reactor.getKey(), reactor.getValue(),
                            kept.getValue().originOf(kind.getKey(), reactor.getKey()));
                }
            }
            copies.put(kept.getKey(), copy);
        }
        return copies;
    }

    /** The kept entry {@code id} names, or null for none kept. */
    public static synchronized ChatConsoleEvent find(long id) {
        return EVENTS.get(Long.valueOf(id));
    }

    /**
     * Adds or takes back one reaction to a kept entry, as a reaction to
     * a message is kept ({@link ChatReactions#set}); answers whether
     * anything changed. Who may react — whoever reads the console — is
     * the caller's to ask.
     */
    public static synchronized boolean react(long id, UUID reactor, String name,
                                             String emoji, boolean add) {
        Long key = Long.valueOf(id);
        if (!EVENTS.containsKey(key)) {
            return false;
        }
        ChatReactions reactions = REACTIONS.get(key);
        if (reactions == null) {
            reactions = new ChatReactions();
        }
        if (!reactions.set(emoji, reactor, name, add)) {
            return false;
        }
        if (reactions.isEmpty()) {
            REACTIONS.remove(key);
        } else {
            REACTIONS.put(key, reactions);
        }
        ChatHistory.changed();
        return true;
    }

    /**
     * The reactions on a kept entry as {@code viewer} is shown them; none
     * for none kept. The Server Log is read and reacted to as the account.
     */
    public static synchronized ChatReactionSummary reactionsFor(long id, UUID viewer) {
        ChatReactions reactions = REACTIONS.get(Long.valueOf(id));
        return reactions == null ? ChatReactionSummary.EMPTY
                : reactions.summaryFor(viewer == null ? Collections.<UUID>emptySet()
                        : Collections.singleton(viewer));
    }

    private static void trim() {
        while (EVENTS.size() > MAX_EVENTS) {
            Iterator<Long> oldest = EVENTS.keySet().iterator();
            REACTIONS.remove(oldest.next());
            oldest.remove();
        }
    }

    private static final Comparator<ChatConsoleEvent> BY_ID =
            new Comparator<ChatConsoleEvent>() {
                @Override
                public int compare(ChatConsoleEvent left, ChatConsoleEvent right) {
                    return left.getId() < right.getId() ? -1
                            : left.getId() > right.getId() ? 1 : 0;
                }
            };

    /**
     * The kept events newer than {@code sinceId}, oldest first, the newest
     * {@link #MAX_REPLAY} of them when there are more.
     */
    public static synchronized List<ChatConsoleEvent> replay(long sinceId) {
        List<ChatConsoleEvent> all = new ArrayList<ChatConsoleEvent>(EVENTS.values());
        List<ChatConsoleEvent> newest = new ArrayList<ChatConsoleEvent>();
        for (int index = all.size() - 1; index >= 0
                && newest.size() < MAX_REPLAY; index--) {
            if (all.get(index).getId() > sinceId) {
                newest.add(all.get(index));
            }
        }
        Collections.reverse(newest);
        return newest;
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        EVENTS.clear();
        REACTIONS.clear();
    }
}
