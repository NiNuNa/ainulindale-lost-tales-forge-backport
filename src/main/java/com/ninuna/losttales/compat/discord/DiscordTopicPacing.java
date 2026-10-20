package com.ninuna.losttales.compat.discord;

import java.util.concurrent.atomic.AtomicLong;

/**
 * When one Discord channel's topic is written. Only when the wanted words
 * differ from the words Discord last took; at most once every interval;
 * and within what Discord allows, which is {@link #EDITS_PER_WINDOW}
 * topic edits per channel in any {@link #WINDOW_MILLIS}, one of them
 * always kept in hand for the offline topic at a stop: the interval is
 * never shorter than Discord's window, so while the server runs the
 * topic is written at most once in it. A change that comes before its
 * turn waits for it, the newest words winning, and one that is undone
 * before its turn (a join, then the same player leaving) writes nothing.
 * A pause Discord asks for after refusing a write holds the next one back
 * for as long as it asked.
 *
 * <p>One stands for each Discord channel whose topic the bridge keeps,
 * held by a {@link DiscordTopicMemory} that the world save keeps across
 * a restart: neither a restart of the bridge nor one of the server
 * writes words the channel already shows or spends an edit Discord has
 * not given back. A last write as the bridge stops skips the interval
 * and takes the edit kept in hand, never more than Discord's allowance.
 * The caller gives the time, so the decision is the same under any
 * clock. Any thread; at most one write is out at a time.</p>
 */
final class DiscordTopicPacing {
    /** Topic edits Discord takes per channel within {@link #WINDOW_MILLIS}. */
    static final int EDITS_PER_WINDOW = 2;
    /**
     * Discord's window for topic edits, ten minutes, and the shortest
     * interval between two writes: {@code channelStatusIntervalSeconds}
     * starts there, so one of Discord's two edits is always kept for the
     * stop.
     */
    static final long WINDOW_MILLIS = 600000L;
    /** No write yet. */
    private static final long NEVER = Long.MIN_VALUE;

    /** Moves on with every write Discord took; shared by the pacings of one memory. */
    private final AtomicLong revision;
    /** The words the server wants the topic to show; empty for none yet. */
    private String wanted = "";
    /** The words Discord last took; empty before the first write. */
    private String posted = "";
    /** When Discord took the latest write, and the one before it, in this run or the save's. */
    private long lastWrite = NEVER;
    private long writeBefore = NEVER;
    /** When Discord took the latest write of this server run: where the interval counts from. */
    private long lastWriteThisRun = NEVER;
    /** Until when Discord asked the topic to wait. */
    private long limitedUntil = NEVER;
    /** Whether a write taken has not yet been answered. */
    private boolean writing;

    DiscordTopicPacing() {
        this(new AtomicLong());
    }

    /** A pacing whose writes move {@code revision} on, its memory's. */
    DiscordTopicPacing(AtomicLong revision) {
        this.revision = revision == null ? new AtomicLong() : revision;
    }

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
     * When the next write may go: once Discord's pause is over and the
     * write before the last has left Discord's window, which is all a
     * last write as the bridge stops waits for. Any other write also waits
     * for the last write to leave the window, so the edit it would spend
     * is never the one kept for the stop, and for the interval, never
     * shorter than the window, since the last write of this server run:
     * the writes the save brought back hold it to Discord's window alone.
     */
    synchronized long nextTurn(long intervalMillis, boolean finalAttempt) {
        long turn = this.limitedUntil;
        if (this.writeBefore != NEVER) {
            turn = Math.max(turn, this.writeBefore + WINDOW_MILLIS);
        }
        if (!finalAttempt && this.lastWrite != NEVER) {
            turn = Math.max(turn, this.lastWrite + WINDOW_MILLIS);
        }
        if (!finalAttempt && this.lastWriteThisRun != NEVER) {
            turn = Math.max(turn, this.lastWriteThisRun + intervalMillis);
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
        this.lastWriteThisRun = nowMillis;
        this.writing = false;
        this.revision.incrementAndGet();
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

    /**
     * What the save keeps of this topic: the words Discord last took and
     * when it took the last two writes. Null before the first write.
     */
    synchronized DiscordTopicMemory.SavedTopic saved(String channelId) {
        if (this.lastWrite == NEVER) {
            return null;
        }
        return new DiscordTopicMemory.SavedTopic(channelId, this.posted,
                this.lastWrite, this.writeBefore == NEVER
                        ? DiscordTopicMemory.SavedTopic.NO_WRITE : this.writeBefore);
    }

    /**
     * Takes up what the save kept of this topic, as the server starts: the
     * words the channel shows and when Discord took the last two writes,
     * which hold the next write to Discord's window but start no interval.
     * A time later than {@code nowMillis}, as a clock set back leaves,
     * counts as now, so the wait it sets is never longer than Discord's
     * window.
     */
    synchronized void restore(DiscordTopicMemory.SavedTopic saved, long nowMillis) {
        if (saved == null) {
            return;
        }
        this.posted = saved.words;
        this.lastWrite = Math.min(saved.lastWrite, nowMillis);
        this.writeBefore = saved.writeBefore == DiscordTopicMemory.SavedTopic.NO_WRITE
                ? NEVER : Math.min(saved.writeBefore, this.lastWrite);
    }
}
