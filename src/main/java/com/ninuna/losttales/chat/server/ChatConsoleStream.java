package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

/**
 * The shared operator console's memory: the last {@link #MAX_EVENTS}
 * administrative events, in the order they happened, so a staff member
 * who joins is shown what went on before them. Written to the world
 * with the chat history ({@link ChatHistoryWorldData}) and read back
 * from it as the server starts, so a restart hands the console its past
 * as it hands every channel theirs; cleared with the rest of the
 * server's chat state. Who is shown an event is
 * {@code LostTalesChatService}'s decision, made from the
 * {@code chat.console.read} capability at the moment of sending and
 * again at the moment of replay.
 *
 * <p>Commands are described here too, and described carefully: what a
 * command was and who ran it is what the console is for, while what it
 * carried may not be — a private message's words, a bot token, a
 * webhook address — and is left out before the entry exists.</p>
 */
public final class ChatConsoleStream {
    /** Events kept; a busy evening of staff work fits. */
    public static final int MAX_EVENTS = 500;
    /** The most a joining staff member is shown. */
    public static final int MAX_REPLAY = 200;
    private static final int MAX_COMMAND_LENGTH = 256;
    private static final String ELIDED = "…";

    private static final LinkedHashMap<Long, ChatConsoleEvent> EVENTS =
            new LinkedHashMap<Long, ChatConsoleEvent>();

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
     * starts, oldest first, each under its own id: one already held
     * stays as it is. The id allocator moves past the newest, so no
     * event said from here on shares an id with a kept one. Answers how
     * many were taken.
     */
    public static synchronized int restore(Collection<ChatConsoleEvent> events) {
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

    private static void trim() {
        while (EVENTS.size() > MAX_EVENTS) {
            Iterator<Long> oldest = EVENTS.keySet().iterator();
            oldest.next();
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

    /**
     * How a command reads in the console: {@code /name arg arg}, with
     * what must not be repeated left out. A private message keeps only
     * whom it went to; a config change keeps the category and key but
     * not the value, since a value may be a secret; a Discord binding
     * keeps its channel and direction but never a webhook address or a
     * channel id. Everything is cut to a line.
     */
    public static String describeCommand(String commandName, String[] args) {
        String name = commandName == null ? "" : commandName.trim().toLowerCase(Locale.ROOT);
        List<String> kept = new ArrayList<String>();
        String[] arguments = args == null ? new String[0] : args;
        if (name.equals("msg") || name.equals("tell") || name.equals("w")) {
            if (arguments.length > 0) {
                kept.add(arguments[0]);
            }
            if (arguments.length > 1) {
                kept.add(ELIDED);
            }
        } else if (name.equals("losttales") && arguments.length > 1
                && arguments[0].equalsIgnoreCase("config")
                && arguments[1].equalsIgnoreCase("set")) {
            for (int index = 0; index < arguments.length && index < 4; index++) {
                kept.add(arguments[index]);
            }
            if (arguments.length > 4) {
                kept.add(ELIDED);
            }
        } else if (name.equals("losttales") && arguments.length > 0
                && arguments[0].equalsIgnoreCase("discord")) {
            for (String argument : arguments) {
                int equals = argument.indexOf('=');
                String option = equals < 0 ? "" : argument.substring(0, equals)
                        .toLowerCase(Locale.ROOT);
                kept.add(option.equals("webhook") || option.equals("channel")
                        ? option + "=" + ELIDED : argument);
            }
        } else {
            Collections.addAll(kept, arguments);
        }
        StringBuilder line = new StringBuilder("/").append(name);
        for (String argument : kept) {
            line.append(' ').append(argument);
        }
        if (line.length() > MAX_COMMAND_LENGTH) {
            line.setLength(MAX_COMMAND_LENGTH - ELIDED.length());
            line.append(ELIDED);
        }
        return line.toString();
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void clear() {
        EVENTS.clear();
    }

    /** Test and diagnostics hook. */
    static synchronized int size() {
        return EVENTS.size();
    }
}
