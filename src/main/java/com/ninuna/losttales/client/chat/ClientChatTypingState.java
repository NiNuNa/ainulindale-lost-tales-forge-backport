package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.util.LostTalesWords;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who is typing into which page right now, as the server has told this
 * client. A name is kept from its last "typing" until its "stopped" or,
 * failing that, until {@link #TTL_NANOS} pass without another word
 * from the server — the sender repeats itself every few seconds while
 * it types, so a lost stop costs a few seconds, never a ghost. Names
 * are listed in the order they started, each with the account the server
 * named it by, which is what the dots over a head go by. Cleared on
 * disconnect.
 */
public final class ClientChatTypingState {
    /** A typist not heard from for this long has stopped. */
    static final long TTL_NANOS = 6000L * 1000000L;
    /** Names kept per page; beyond this the oldest goes. */
    static final int MAX_NAMES_PER_PAGE = 32;
    private static final Map<ConversationPage, LinkedHashMap<String, Typist>> TYPING =
            new HashMap<ConversationPage, LinkedHashMap<String, Typist>>();

    /** One typist: until when they count as typing, and their account; null for nobody named. */
    private static final class Typist {
        final long untilNanos;
        final UUID accountId;

        Typist(long untilNanos, UUID accountId) {
            this.untilNanos = untilNanos;
            this.accountId = accountId;
        }
    }

    private ClientChatTypingState() {}

    /** Applies a server statement on the client thread. */
    public static void accept(LostTalesChatTypingSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        // A whisper's typing shows with the character it is addressed to,
        // while a copy reads as it; a channel's goes to whoever reads it.
        if (packet.getChannel() == ChatChannel.WHISPER
                && !ClientChatIdentities.isRead(packet.getRecipientIdentity())) {
            return;
        }
        ConversationPage page = packet.getChannel() == ChatChannel.WHISPER
                ? ConversationPage.whisper(packet.getPartner(), packet.getIdentityName(), packet.getRecipientIdentity())
                : ConversationPage.of(packet.getChannel(), packet.getScopeValue());
        if (!page.isWhisper() && !ClientChatChannelState.isAvailable(page)) { return; }
        // The Narrator and an unseen voice type under the names this game
        // gives them.
        apply(page, packet.isNarrator() ? ChatNames.narrator(LostTalesWords.LANG)
                        : packet.isUnseen() ? ChatNames.unseenVoice(LostTalesWords.LANG)
                        : packet.getIdentityName(), packet.getAccountId(),
                packet.isTyping(), System.nanoTime());
    }

    static synchronized void apply(ConversationPage page, String name, boolean typing,
                                   long nowNanos) {
        apply(page, name, null, typing, nowNanos);
    }

    static synchronized void apply(ConversationPage page, String name,
                                   UUID accountId, boolean typing,
                                   long nowNanos) {
        if (page == null || name == null || name.trim().length() == 0) {
            return;
        }
        LinkedHashMap<String, Typist> names = TYPING.get(page);
        if (!typing) {
            if (names != null) {
                names.remove(name);
                if (names.isEmpty()) {
                    TYPING.remove(page);
                }
            }
            return;
        }
        if (names == null) {
            names = new LinkedHashMap<String, Typist>();
            TYPING.put(page, names);
        }
        // A refresh keeps its place in the order; only a new name joins
        // at the end.
        names.put(name, new Typist(nowNanos + TTL_NANOS, accountId));
        while (names.size() > MAX_NAMES_PER_PAGE) {
            Iterator<String> oldest = names.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The names typing into the page now, oldest first; never null. */
    public static List<String> namesTyping(ConversationPage page) {
        return namesTyping(page, System.nanoTime());
    }

    static synchronized List<String> namesTyping(ConversationPage page, long nowNanos) {
        LinkedHashMap<String, Typist> names = page == null ? null : TYPING.get(ConversationPage.viewedConversation(page));
        if (names == null) {
            return Collections.emptyList();
        }
        Iterator<Map.Entry<String, Typist>> entries = names.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().untilNanos <= nowNanos) {
                entries.remove();
            }
        }
        if (names.isEmpty()) {
            TYPING.remove(ConversationPage.viewedConversation(page));
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                new ArrayList<String>(names.keySet()));
    }

    /**
     * Whether the account is typing into a conversation heard nearby —
     * Proximity or a server channel heard nearby — which is what the dots
     * over a head answer for (CHAT-58): only words said aloud float. The
     * typist is found by the account the server named, never by a name,
     * so a namesake never shows dots.
     */
    public static synchronized boolean isTypingNearby(UUID accountId, long nowNanos) {
        if (accountId == null) {
            return false;
        }
        for (Map.Entry<ConversationPage, LinkedHashMap<String, Typist>> entry : TYPING.entrySet()) {
            if (!isNearby(entry.getKey())) {
                continue;
            }
            for (Typist typist : entry.getValue().values()) {
                if (accountId.equals(typist.accountId)
                        && typist.untilNanos > nowNanos) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether anyone at all is typing into a conversation heard nearby. */
    public static synchronized boolean anyTypingNearby(long nowNanos) {
        for (Map.Entry<ConversationPage, LinkedHashMap<String, Typist>> entry : TYPING.entrySet()) {
            if (!isNearby(entry.getKey())) {
                continue;
            }
            for (Typist typist : entry.getValue().values()) {
                if (typist.untilNanos > nowNanos) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isNearby(ConversationPage page) {
        return page != null && !page.isNpc() && !page.isWhisper()
                && ChatSpeechRange.appliesTo(page.getChannel());
    }

    /** Forgets every typist, and the feed's typing row goes with them. */
    public static synchronized void clear() {
        TYPING.clear();
        ChatFeedTyping.clear();
    }
}
