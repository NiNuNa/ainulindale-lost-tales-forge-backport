package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatMessageIds;

/**
 * Where the server's message ids come from. What the numbers mean is
 * {@link ChatMessageIds}; this only hands them out.
 *
 * <p>Ids are milliseconds since the epoch, kept strictly increasing: two
 * messages inside one millisecond, or a clock that steps backwards
 * during a run, take the next id after the last rather than repeating
 * one. They are therefore roughly the time the message was sent and
 * always in the order the server accepted them, which is the order a
 * history is read in.</p>
 */
public final class ChatMessageIdAllocator {
    private static long lastId;

    private ChatMessageIdAllocator() {}

    /** The next id, greater than every id this run has handed out. */
    public static synchronized long next() {
        long now = System.currentTimeMillis();
        lastId = now > lastId ? now : lastId + 1L;
        return lastId;
    }

    /**
     * Moves past {@code id} when it is ahead: what a restored history
     * does with its newest line, so a line said after a restart can
     * never share an id with one the save kept, whatever the clock says.
     */
    public static synchronized void seed(long id) {
        if (id > lastId) {
            lastId = id;
        }
    }

    /** Cleared with the rest of the server's chat state. */
    public static synchronized void reset() {
        lastId = 0L;
    }
}
