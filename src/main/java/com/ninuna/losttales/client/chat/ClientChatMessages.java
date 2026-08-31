package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Iterator;
import java.util.LinkedHashMap;

/**
 * What each message on screen was made of, kept so it can be made
 * again.
 *
 * <p>A message is drawn from a good deal more than its words: who
 * signed it and in what colours, the head beside the name, the title,
 * the tab it was filed under, the items and places it shared. When the
 * server says one of them now reads differently, only the words have
 * changed — so the line is <em>rebuilt</em> from everything it was
 * built from the first time, with the new text in place of the old,
 * rather than having its text patched where it lies. Patching would
 * have to find the body among the parts and put it back styled, which
 * is the same work done less reliably.</p>
 *
 * <p>Held only for messages the server named, bounded like the rest of
 * the client's chat memory, and cleared with it. A message that has
 * fallen out is one whose line is long gone from the history too.</p>
 */
final class ClientChatMessages {
    private static final LinkedHashMap<Long, Remembered> ENTRIES =
            new LinkedHashMap<Long, Remembered>();

    private ClientChatMessages() {}

    /** Remembers what a printed message was built from. */
    static synchronized void remember(LostTalesChatMessagePacket packet,
                                      ChatTab tab, int[] showcaseIds) {
        if (packet == null || tab == null
                || !ChatMessageIds.isServerId(packet.getMessageId())) {
            return;
        }
        ENTRIES.put(Long.valueOf(packet.getMessageId()),
                new Remembered(packet, tab, showcaseIds, false));
        while (ENTRIES.size() > ClientChatChannelViews.maxTrackedLines()) {
            Iterator<Long> oldest = ENTRIES.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** What that message was built from, or null when it is forgotten. */
    static synchronized Remembered get(long messageId) {
        return ENTRIES.get(Long.valueOf(messageId));
    }

    /** Records that a message now says something else. */
    static synchronized void rewrite(long messageId,
                                     LostTalesChatMessagePacket packet) {
        Remembered entry = ENTRIES.get(Long.valueOf(messageId));
        if (entry == null || packet == null) {
            return;
        }
        // Keeps its place in the order, so an edit does not change what
        // is forgotten next; the message is edited from here on, and a
        // later rebuild of the line keeps saying so.
        ENTRIES.put(Long.valueOf(messageId),
                new Remembered(packet, entry.tab, entry.showcaseIds, true));
    }

    /**
     * Swaps what a message is built from without making it an edited
     * one: what a reply takes when the message it quotes changes under
     * it — the reply itself still says exactly what it said.
     */
    static synchronized void refresh(long messageId,
                                     LostTalesChatMessagePacket packet) {
        Remembered entry = ENTRIES.get(Long.valueOf(messageId));
        if (entry == null || packet == null) {
            return;
        }
        ENTRIES.put(Long.valueOf(messageId), new Remembered(packet,
                entry.tab, entry.showcaseIds, entry.edited));
    }

    /**
     * The messages still held that reply to {@code messageId}, oldest
     * first: whose quotes have to follow when it is edited.
     */
    static synchronized java.util.List<Long> replyingTo(long messageId) {
        java.util.List<Long> result = new java.util.ArrayList<Long>();
        if (!ChatMessageIds.isServerId(messageId)) {
            return result;
        }
        for (java.util.Map.Entry<Long, Remembered> entry
                : ENTRIES.entrySet()) {
            if (entry.getValue().packet.getReply().getMessageId()
                    == messageId) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    /** Forgets a message that is no longer shown. */
    static synchronized void forget(long messageId) {
        ENTRIES.remove(Long.valueOf(messageId));
    }

    static synchronized void clear() {
        ENTRIES.clear();
    }

    /** Test and diagnostics hook: messages that could be rebuilt. */
    static synchronized int size() {
        return ENTRIES.size();
    }

    /** One message, and everything drawing it again needs. */
    static final class Remembered {
        final LostTalesChatMessagePacket packet;
        final ChatTab tab;
        final int[] showcaseIds;
        /** Whether the message has been edited; its line says so. */
        final boolean edited;

        private Remembered(LostTalesChatMessagePacket packet, ChatTab tab,
                           int[] showcaseIds, boolean edited) {
            this.packet = packet;
            this.tab = tab;
            this.showcaseIds = showcaseIds;
            this.edited = edited;
        }
    }
}
