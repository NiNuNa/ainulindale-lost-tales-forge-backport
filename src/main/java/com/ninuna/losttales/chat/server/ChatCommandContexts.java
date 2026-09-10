package com.ninuna.losttales.chat.server;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Where each player typed the command they are about to send: the tab
 * id their client reported a moment before the command itself, kept
 * until the command arrives or a few seconds pass. A vanilla command
 * carries no channel, so the console line saying where it was typed
 * has to be told by the client ahead of it.
 *
 * <p>Noted on the network thread and read on the server thread. The
 * note and the chat packet behind it travel in order on one
 * connection, and the note is taken as it is read off the wire, so it
 * is in place before the command is run; queued to the server tick it
 * could land a tick late and miss the command. Nothing here touches the
 * world. Presentation only: the id is an opaque, bounded string the
 * client chose, shown to console readers as a link and trusted for
 * nothing else. Cleared with the rest of the server's chat state.</p>
 */
public final class ChatCommandContexts {
    /** How long a note answers for a command; commands follow at once. */
    static final long VALID_MILLIS = 5000L;
    /** More players than this have no note kept at once. */
    private static final int MAX_ENTRIES = 512;
    private static final ConcurrentHashMap<UUID, Entry> CONTEXTS =
            new ConcurrentHashMap<UUID, Entry>();

    private ChatCommandContexts() {}

    /** Remembers where {@code playerId}'s next command was typed. */
    public static void note(UUID playerId, String tabId, long nowMillis) {
        if (playerId == null || tabId == null || tabId.length() == 0) {
            return;
        }
        if (CONTEXTS.size() >= MAX_ENTRIES) {
            purge(nowMillis);
            if (CONTEXTS.size() >= MAX_ENTRIES) {
                return;
            }
        }
        CONTEXTS.put(playerId, new Entry(tabId, nowMillis));
    }

    /**
     * The tab id noted for the player's command, taken so it answers
     * once; empty when none was noted or the note has gone stale.
     */
    public static String take(UUID playerId, long nowMillis) {
        Entry entry = playerId == null ? null : CONTEXTS.remove(playerId);
        if (entry == null || !entry.isFresh(nowMillis)) {
            return "";
        }
        return entry.tabId;
    }

    /** Drops every stale note. */
    private static void purge(long nowMillis) {
        Iterator<Map.Entry<UUID, Entry>> entries =
                CONTEXTS.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<UUID, Entry> entry = entries.next();
            if (entry.getValue() == null
                    || !entry.getValue().isFresh(nowMillis)) {
                entries.remove();
            }
        }
    }

    public static void clear() {
        CONTEXTS.clear();
    }

    static int size() {
        return CONTEXTS.size();
    }

    private static final class Entry {
        final String tabId;
        final long notedMillis;

        Entry(String tabId, long notedMillis) {
            this.tabId = tabId;
            this.notedMillis = notedMillis;
        }

        boolean isFresh(long nowMillis) {
            long age = nowMillis - this.notedMillis;
            return age >= 0L && age <= VALID_MILLIS;
        }
    }
}
