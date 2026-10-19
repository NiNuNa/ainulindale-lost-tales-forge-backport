package com.ninuna.losttales.compat.discord;

/**
 * When one Discord channel's topic is written. Only when the wanted words
 * differ from the words Discord last took; at most once every interval;
 * and never more often than Discord allows, which is
 * {@link #EDITS_PER_WINDOW} topic edits per channel in any
 * {@link #WINDOW_MILLIS}. A change that comes before its turn waits for
 * it, the newest words winning, and one that is undone before its turn
 * (a join, then the same player leaving) writes nothing. A pause Discord
 * asks for after refusing a write holds the next one back for as long as
 * it asked.
 *
 * <p>One stands for each Discord channel whose topic the bridge keeps,
 * for the whole server run, so a restart of the bridge writes nothing the
 * channel already shows and spends no edit Discord has not given back.
 * A last write as the bridge stops skips the interval but not Discord's
 * allowance. The caller gives the time, so the decision is the same
 * under any clock. Any thread; at most one write is out at a time.</p>
 */
final class DiscordTopicPacing {
    /** Topic edits Discord takes per channel within {@link #WINDOW_MILLIS}. */
    static final int EDITS_PER_WINDOW = 2;
    /** Discord's window for topic edits: ten minutes. */
    static final long WINDOW_MILLIS = 600000L;
    /** The shortest interval between two writes: Discord's window shared out evenly. */
    static final long SHORTEST_INTERVAL_MILLIS = WINDOW_MILLIS / EDITS_PER_WINDOW;
    /** No write yet. */
    private static final long NEVER = Long.MIN_VALUE;

    /** The words the server wants the topic to show; empty for none yet. */
    private String wanted = "";
    /** The words Discord last took; empty before the first write this run. */
    private String posted = "";
    /** When Discord took the latest write, and the one before it. */
    private long lastWrite = NEVER;
    private long writeBefore = NEVER;
    /** Until when Discord asked the topic to wait. */
    private long limitedUntil = NEVER;
    /** Whether a write taken has not yet been answered. */
    private boolean writing;

    /** States the words the topic should show. */
    synchronized void want(String topic) {
        this.wanted = topic == null ? "" : topic;
    }

    /** The words Discord last took; empty before the first write. */
    synchronized String posted() {
        return this.posted;
    }

    /**
     * The words to write now, or null when nothing is to be written yet:
     * nothing wanted, the channel already shows it, a write already out,
     * or its turn not come ({@link #nextTurn}). A write taken is ended by
     * {@link #posted(String, long)}, {@link #limited} or {@link #released}.
     */
    synchronized String take(long nowMillis, long intervalMillis, boolean finalAttempt) {
        if (this.writing || this.wanted.length() == 0
                || this.wanted.equals(this.posted)
                || nowMillis < nextTurn(intervalMillis, finalAttempt)) {
            return null;
        }
        this.writing = true;
        return this.wanted;
    }

    /**
     * When the next write may go: once Discord's pause is over, once the
     * write before the last has left Discord's window, and, except for a
     * last write as the bridge stops, once the interval since the last
     * write has passed.
     */
    synchronized long nextTurn(long intervalMillis, boolean finalAttempt) {
        long turn = this.limitedUntil;
        if (this.writeBefore != NEVER) {
            turn = Math.max(turn, this.writeBefore + WINDOW_MILLIS);
        }
        if (!finalAttempt && this.lastWrite != NEVER) {
            turn = Math.max(turn, this.lastWrite + Math.max(0L, intervalMillis));
        }
        return turn;
    }

    /** Whether a write would be taken at its turn: words wanted that the channel does not show. */
    synchronized boolean hasChange() {
        return this.wanted.length() > 0 && !this.wanted.equals(this.posted);
    }

    /**
     * Discord took {@code topic} by {@code nowMillis}, the time its answer
     * came: later than Discord counted the edit, so the window is never
     * reopened early.
     */
    synchronized void posted(String topic, long nowMillis) {
        this.posted = topic == null ? "" : topic;
        this.writeBefore = this.lastWrite;
        this.lastWrite = nowMillis;
        this.writing = false;
    }

    /** Discord refused a write and asked for a pause of {@code pauseMillis}. */
    synchronized void limited(long nowMillis, long pauseMillis) {
        this.limitedUntil = Math.max(this.limitedUntil,
                nowMillis + Math.max(0L, pauseMillis));
        this.writing = false;
    }

    /** A write that did not go through for any other reason; the words stay wanted. */
    synchronized void released() {
        this.writing = false;
    }
}
