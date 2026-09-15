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
 *
 * <p>Once the command runs, its note becomes the player's running
 * context ({@link #beginCommand}): the tab the command's answers are
 * kept under, answered line by line ({@link #answerLine}) for as long
 * as a note is fresh and for at most {@link #MAX_LINES_PER_COMMAND}
 * lines, so a listing command cannot fill the history with itself.</p>
 */
public final class ChatCommandContexts {
    /** How long a note answers for a command; commands follow at once. */
    static final long VALID_MILLIS = 5000L;
    /** The most answer lines one command has kept under its tab. */
    static final int MAX_LINES_PER_COMMAND = 32;
    /** More players than this have no note kept at once. */
    private static final int MAX_ENTRIES = 512;
    private static final ConcurrentHashMap<UUID, Entry> CONTEXTS =
            new ConcurrentHashMap<UUID, Entry>();
    /** The tab each player's running command was typed in. */
    private static final ConcurrentHashMap<UUID, Entry> RUNNING =
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

    /**
     * Takes the player's note as their command starts to run, and keeps
     * it as the running context the command's answers are filed under.
     * Answers what {@link #take} would have; a command with no note
     * runs with no context, and its answers are kept nowhere.
     */
    public static String beginCommand(UUID playerId, long nowMillis) {
        String tabId = take(playerId, nowMillis);
        if (playerId == null) {
            return "";
        }
        if (tabId.length() == 0) {
            RUNNING.remove(playerId);
            return "";
        }
        if (RUNNING.size() >= MAX_ENTRIES) {
            purge(RUNNING, nowMillis);
        }
        if (RUNNING.size() < MAX_ENTRIES) {
            RUNNING.put(playerId, new Entry(tabId, nowMillis));
        }
        return tabId;
    }

    /**
     * The tab a line sent to the player right now is an answer to:
     * their running command's, while the command is fresh and has kept
     * fewer than {@link #MAX_LINES_PER_COMMAND} lines; else empty, and
     * the line is nobody's answer.
     */
    public static String answerLine(UUID playerId, long nowMillis) {
        Entry entry = playerId == null ? null : RUNNING.get(playerId);
        if (entry == null) {
            return "";
        }
        if (!entry.isFresh(nowMillis) || entry.linesKept >= MAX_LINES_PER_COMMAND) {
            RUNNING.remove(playerId);
            return "";
        }
        entry.linesKept++;
        return entry.tabId;
    }

    /** Drops every stale note. */
    private static void purge(long nowMillis) {
        purge(CONTEXTS, nowMillis);
    }

    private static void purge(Map<UUID, Entry> entries, long nowMillis) {
        Iterator<Map.Entry<UUID, Entry>> walk = entries.entrySet().iterator();
        while (walk.hasNext()) {
            Map.Entry<UUID, Entry> entry = walk.next();
            if (entry.getValue() == null
                    || !entry.getValue().isFresh(nowMillis)) {
                walk.remove();
            }
        }
    }

    public static void clear() {
        CONTEXTS.clear();
        RUNNING.clear();
    }

    static int size() {
        return CONTEXTS.size();
    }

    private static final class Entry {
        final String tabId;
        final long notedMillis;
        /** Answer lines kept under a running command's tab so far. */
        int linesKept;

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
