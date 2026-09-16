package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.network.packet.LostTalesChatTypingSyncPacket;
import com.ninuna.losttales.chat.ChatRolePresentation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Who is typing into which tab right now, as the server has told this
 * client. A name is kept from its last "typing" until its "stopped" or,
 * failing that, until {@link #TTL_NANOS} pass without another word
 * from the server — the sender repeats itself every few seconds while
 * it types, so a lost stop costs a few seconds, never a ghost. Names
 * are listed in the order they started. Cleared on disconnect.
 */
public final class ClientChatTypingState {
    /** A typist not heard from for this long has stopped. */
    static final long TTL_NANOS = 6000L * 1000000L;
    /** Names kept per tab; beyond this the oldest goes. */
    static final int MAX_NAMES_PER_TAB = 32;
    private static final Map<ChatTab, LinkedHashMap<String, Long>> TYPING =
            new HashMap<ChatTab, LinkedHashMap<String, Long>>();

    private ClientChatTypingState() {}

    /** Applies a server statement on the client thread. */
    public static void accept(LostTalesChatTypingSyncPacket packet) {
        if (packet == null || packet.isMalformed()) {
            return;
        }
        if (ChatRolePresentation.isInCharacter(packet.getChannel())
                && !packet.getRecipientIdentity().equals(ClientChatIdentities.viewIdentityKey())) {
            return;
        }
        ChatTab tab = packet.getChannel() == ChatChannel.WHISPER
                ? ChatTab.whisper(packet.getPartner(), packet.getIdentityName(), packet.getRecipientIdentity())
                : ChatTab.of(packet.getChannel(), packet.getScopeValue());
        if (!tab.isWhisper() && !ClientChatChannelState.isAvailable(tab)) { return; }
        apply(tab, packet.getIdentityName(), packet.isTyping(),
                System.nanoTime());
    }

    static synchronized void apply(ChatTab tab, String name, boolean typing,
                                   long nowNanos) {
        if (tab == null || name == null || name.trim().length() == 0) {
            return;
        }
        LinkedHashMap<String, Long> names = TYPING.get(tab);
        if (!typing) {
            if (names != null) {
                names.remove(name);
                if (names.isEmpty()) {
                    TYPING.remove(tab);
                }
            }
            return;
        }
        if (names == null) {
            names = new LinkedHashMap<String, Long>();
            TYPING.put(tab, names);
        }
        // A refresh keeps its place in the order; only a new name joins
        // at the end.
        names.put(name, Long.valueOf(nowNanos + TTL_NANOS));
        while (names.size() > MAX_NAMES_PER_TAB) {
            Iterator<String> oldest = names.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** The names typing into the tab now, oldest first; never null. */
    public static List<String> namesTyping(ChatTab tab) {
        return namesTyping(tab, System.nanoTime());
    }

    static synchronized List<String> namesTyping(ChatTab tab, long nowNanos) {
        LinkedHashMap<String, Long> names = tab == null ? null : TYPING.get(ChatTab.viewed(tab));
        if (names == null) {
            return Collections.emptyList();
        }
        Iterator<Map.Entry<String, Long>> entries = names.entrySet().iterator();
        while (entries.hasNext()) {
            if (entries.next().getValue().longValue() <= nowNanos) {
                entries.remove();
            }
        }
        if (names.isEmpty()) {
            TYPING.remove(ChatTab.viewed(tab));
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                new ArrayList<String>(names.keySet()));
    }

    /**
     * Whether a name is typing into a roleplaying conversation this
     * client reads — Global, Proximity, Faction, Party or a whisper —
     * which is what the typing bubble over a head answers for. An NPC
     * conversation is local and never has a typist on the other end.
     */
    public static synchronized boolean isTypingInCharacter(String name, long nowNanos) {
        if (name == null || name.length() == 0) {
            return false;
        }
        for (Map.Entry<ChatTab, LinkedHashMap<String, Long>> entry : TYPING.entrySet()) {
            ChatTab tab = entry.getKey();
            if (tab.isNpc() || !ChatRolePresentation.isInCharacter(tab.getChannel())) {
                continue;
            }
            Long until = entry.getValue().get(name);
            if (until != null && until.longValue() > nowNanos) {
                return true;
            }
        }
        return false;
    }

    /** Whether anyone at all is typing into a roleplaying conversation. */
    public static synchronized boolean anyTypingInCharacter(long nowNanos) {
        for (Map.Entry<ChatTab, LinkedHashMap<String, Long>> entry : TYPING.entrySet()) {
            ChatTab tab = entry.getKey();
            if (tab.isNpc() || !ChatRolePresentation.isInCharacter(tab.getChannel())) {
                continue;
            }
            for (Long until : entry.getValue().values()) {
                if (until.longValue() > nowNanos) {
                    return true;
                }
            }
        }
        return false;
    }

    public static synchronized void clear() {
        TYPING.clear();
    }
}
