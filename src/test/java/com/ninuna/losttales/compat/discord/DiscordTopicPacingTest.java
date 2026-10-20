package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A topic is written only when its words change, at most once an
 * interval, never shorter than Discord's ten-minute window, so of
 * Discord's two topic edits per channel in ten minutes one is always kept
 * for the offline topic at a stop; a change that waits for its turn is
 * written when the turn comes, the newest words winning. What Discord
 * took and when comes back from the save, so a restart keeps that pace.
 */
public final class DiscordTopicPacingTest {
    private static final long MINUTE = 60000L;
    private static final long WINDOW = DiscordTopicPacing.WINDOW_MILLIS;
    /** The shortest interval the config takes. */
    private static final long INTERVAL = WINDOW;
    /** A start time well away from zero, as the server's clock is. */
    private static final long START = 1000000000L;
    private static final String CHANNEL = "123456789";

    /** Writes {@code topic} as a worker would at {@code now}; the words written, or null. */
    private static String write(DiscordTopicPacing pacing, long now, boolean last) {
        String taken = pacing.take(now, INTERVAL, last);
        if (taken != null) {
            pacing.posted(taken, now);
        }
        return taken;
    }

    @Test
    public void theSameWordsAreNeverWrittenTwice() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        assertNull("nothing wanted yet", write(pacing, START, false));
        pacing.want("Server online • 1/20 players • 20 TPS");
        assertEquals("Server online • 1/20 players • 20 TPS",
                write(pacing, START, false));
        pacing.want("Server online • 1/20 players • 20 TPS");
        assertNull("a quiet interval writes nothing",
                write(pacing, START + INTERVAL, false));
        assertNull(write(pacing, START + 10L * INTERVAL, true));
    }

    @Test
    public void aChangeWaitsForItsTurnAndTheNewestWordsWin() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("1 player");
        assertEquals("1 player", write(pacing, START, false));
        pacing.want("2 players");
        assertNull("too soon after the last write", write(pacing, START + MINUTE, false));
        pacing.want("3 players");
        assertEquals(START + INTERVAL, pacing.nextTurn(INTERVAL, false));
        assertNull(write(pacing, START + INTERVAL - 1L, false));
        assertEquals("the newest words, once", "3 players",
                write(pacing, START + INTERVAL, false));
        assertNull(write(pacing, START + INTERVAL + 1L, false));
        pacing.want("4 players");
        assertEquals("a longer interval stands", START + INTERVAL + 3L * WINDOW,
                pacing.nextTurn(3L * WINDOW, false));
    }

    @Test
    public void aChangeUndoneBeforeItsTurnWritesNothing() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("1 player");
        write(pacing, START, false);
        pacing.want("2 players");
        pacing.want("1 player");
        assertNull("a join and the same player's leave", write(pacing,
                START + 2L * INTERVAL, false));
    }

    /**
     * A last write as the bridge stops skips the interval and takes the
     * edit kept in hand, but never Discord's two edits in ten minutes:
     * the offline topic waits for no turn and is not sent into a refusal
     * either.
     */
    @Test
    public void aLastWriteSkipsTheIntervalButNotDiscordsAllowance() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("online");
        write(pacing, START, false);
        pacing.want("Server offline");
        assertEquals("Server offline", write(pacing, START + MINUTE, true));

        // Two last writes, as a link let go and taken again would make,
        // and a third within the window waits.
        pacing.want("online");
        assertNull("a write while running keeps the stop's edit",
                write(pacing, START + 2L * MINUTE, false));
        pacing.want("Server offline again");
        assertNull("two edits in the last ten minutes",
                write(pacing, START + 2L * MINUTE, true));
        assertEquals("Server offline again", write(pacing, START + WINDOW, true));
    }

    /**
     * A topic changing twice soon after a start still leaves the stop its
     * edit: the second change waits a whole window, and the offline topic
     * goes at once.
     */
    @Test
    public void twoChangesSoonAfterAStartLeaveTheStopItsEdit() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("Server online • 0/20 players");
        assertEquals("Server online • 0/20 players", write(pacing, START, false));
        pacing.want("Server online • 1/20 players");
        assertNull(write(pacing, START + 5L * MINUTE, false));
        pacing.want("Server offline");
        assertEquals("Server offline", write(pacing, START + 6L * MINUTE, true));
    }

    @Test
    public void aPauseDiscordAskedForHoldsTheNextWrite() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("1 player");
        assertEquals("1 player", pacing.take(START, INTERVAL, false));
        pacing.limited(START, 9L * MINUTE);
        assertNull(write(pacing, START + 8L * MINUTE, true));
        assertEquals("1 player", write(pacing, START + 9L * MINUTE, false));
    }

    /** One write at a time: a second worker finds the first one's write still out. */
    @Test
    public void oneWriteIsOutAtATime() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("1 player");
        assertEquals("1 player", pacing.take(START, INTERVAL, false));
        assertNull(pacing.take(START, INTERVAL, true));
        pacing.released();
        assertEquals("the words stay wanted", "1 player",
                pacing.take(START + 1L, INTERVAL, false));
        pacing.posted("1 player", START + 2L);
        assertEquals("1 player", pacing.posted());
        assertTrue(!pacing.hasChange());
    }

    /**
     * A day of a topic changing every second, with the shortest interval
     * and a last write tried every minute besides: never three writes in
     * ten minutes.
     */
    @Test
    public void aBusyDayNeverOutrunsDiscord() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        List<Long> writes = new ArrayList<Long>();
        long day = 24L * 60L * MINUTE;
        for (long now = START; now < START + day; now += 1000L) {
            pacing.want("players " + (now / 1000L % 7L));
            boolean last = (now - START) % MINUTE == 0L;
            if (write(pacing, now, last) != null) {
                writes.add(Long.valueOf(now));
            }
        }
        assertTrue(writes.size() > 200);
        for (int index = 2; index < writes.size(); index++) {
            assertTrue("never three edits in ten minutes", writes.get(index).longValue()
                    - writes.get(index - 2).longValue()
                    >= DiscordTopicPacing.WINDOW_MILLIS);
        }
    }

    /**
     * A day of a topic changing every second while the server runs: at
     * every moment of it the offline topic could go at once.
     */
    @Test
    public void aStopAlwaysFindsAnEditInHand() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        int written = 0;
        long day = 24L * 60L * MINUTE;
        for (long now = START; now < START + day; now += 1000L) {
            pacing.want("players " + (now / 1000L % 7L));
            if (write(pacing, now, false) != null) {
                written++;
            }
            assertTrue("the stop's edit is in hand",
                    pacing.nextTurn(INTERVAL, true) <= now);
        }
        assertEquals("once a window", day / WINDOW, written);
    }

    /** The interval ships at its least, Discord's window. */
    @Test
    public void theShippedIntervalIsDiscordsWindow() {
        assertEquals(10L * MINUTE, WINDOW);
        assertEquals(LostTalesConfig.discordChannelStatusIntervalSeconds * 1000L, WINDOW);
    }

    /**
     * A server stopped a minute after its offline topic, and the write
     * before it, starts again: its online topic waits until the offline
     * one has left Discord's window, and a stop meanwhile has nothing to
     * write, the topic still saying offline.
     */
    @Test
    public void aQuickRestartKeepsDiscordsPace() {
        DiscordTopicMemory before = new DiscordTopicMemory();
        DiscordTopicPacing running = before.pacingOf(CHANNEL);
        running.want("Server online • 1/20 players");
        assertEquals("Server online • 1/20 players",
                write(running, START - WINDOW / 2L, false));
        running.want("Server offline");
        assertEquals("Server offline", write(running, START, true));

        DiscordTopicMemory after = new DiscordTopicMemory();
        assertEquals(1, after.restore(before.snapshot().topics, START + MINUTE));
        DiscordTopicPacing restarted = after.pacingOf(CHANNEL);
        assertEquals("Server offline", restarted.posted());
        restarted.want("Server online • 0/20 players");
        assertNull("both of Discord's edits are spent",
                write(restarted, START + MINUTE, false));
        assertEquals("the stop's write leaves the window", START + INTERVAL,
                restarted.nextTurn(INTERVAL, false));
        restarted.want("Server offline");
        assertNull("the topic says offline already",
                write(restarted, START + 2L * MINUTE, true));
        restarted.want("Server online • 0/20 players");
        assertEquals("Server online • 0/20 players",
                write(restarted, START + INTERVAL, false));
    }

    /**
     * The save's writes hold a restarted server to Discord's window, not
     * to a longer interval, which counts from the run's own first write.
     */
    @Test
    public void aRestartWaitsForDiscordsWindowNotTheInterval() {
        long hour = 6L * WINDOW;
        DiscordTopicMemory memory = new DiscordTopicMemory();
        memory.restore(Collections.singletonList(new DiscordTopicMemory.SavedTopic(
                CHANNEL, "Server offline", START,
                DiscordTopicMemory.SavedTopic.NO_WRITE)), START + MINUTE);
        DiscordTopicPacing pacing = memory.pacingOf(CHANNEL);
        pacing.want("Server online • 0/20 players");
        assertEquals(START + WINDOW, pacing.nextTurn(hour, false));
        assertEquals("Server online • 0/20 players", pacing.take(START + WINDOW, hour, false));
        pacing.posted("Server online • 0/20 players", START + WINDOW);
        pacing.want("Server online • 1/20 players");
        assertEquals("the run's own writes keep the interval", START + WINDOW + hour,
                pacing.nextTurn(hour, false));
    }

    /** A start finds the words it would write already shown, as after a crash, and writes nothing. */
    @Test
    public void aStartWritesNothingTheTopicAlreadyShows() {
        DiscordTopicMemory memory = new DiscordTopicMemory();
        memory.restore(Collections.singletonList(new DiscordTopicMemory.SavedTopic(
                CHANNEL, "Server online • 0/20 players", START - 3L * WINDOW,
                DiscordTopicMemory.SavedTopic.NO_WRITE)), START);
        DiscordTopicPacing pacing = memory.pacingOf(CHANNEL);
        pacing.want("Server online • 0/20 players");
        assertFalse(pacing.hasChange());
        assertNull(write(pacing, START, false));
        pacing.want("Server online • 1/20 players");
        assertEquals("a change goes at once, the last write long past",
                "Server online • 1/20 players", write(pacing, START, false));
    }

    /** A saved time later than now, as a clock set back leaves, holds a write back no longer than the window. */
    @Test
    public void aClockSetBackWaitsNoLongerThanTheWindow() {
        DiscordTopicMemory memory = new DiscordTopicMemory();
        long hourAhead = START + 60L * MINUTE;
        memory.restore(Collections.singletonList(new DiscordTopicMemory.SavedTopic(
                CHANNEL, "Server offline", hourAhead + 1L, hourAhead)), START);
        DiscordTopicPacing pacing = memory.pacingOf(CHANNEL);
        assertEquals(START + WINDOW, pacing.nextTurn(INTERVAL, false));
        assertEquals(START + WINDOW, pacing.nextTurn(INTERVAL, true));
    }

    /**
     * Every write Discord takes moves the memory's revision on, which is
     * how the save knows it has something to write; a restore does not.
     * The save holds only channels written.
     */
    @Test
    public void theMemoryCountsItsWritesForTheSave() {
        DiscordTopicMemory memory = new DiscordTopicMemory();
        memory.pacingOf("111").want("unwritten");
        memory.restore(Collections.singletonList(new DiscordTopicMemory.SavedTopic(
                "222", "Server offline", START - MINUTE,
                DiscordTopicMemory.SavedTopic.NO_WRITE)), START);
        assertEquals(0L, memory.revision());
        DiscordTopicMemory.Snapshot restored = memory.snapshot();
        assertEquals(1, restored.topics.size());
        assertEquals("222", restored.topics.get(0).channelId);

        DiscordTopicPacing pacing = memory.pacingOf("333");
        pacing.want("Server online");
        write(pacing, START, false);
        assertEquals(1L, memory.revision());
        DiscordTopicMemory.Snapshot snapshot = memory.snapshot();
        assertEquals(1L, snapshot.revision);
        assertEquals(2, snapshot.topics.size());
        DiscordTopicMemory.SavedTopic saved = snapshot.topics.get(1);
        assertEquals("333", saved.channelId);
        assertEquals("Server online", saved.words);
        assertEquals(START, saved.lastWrite);
        assertEquals(DiscordTopicMemory.SavedTopic.NO_WRITE, saved.writeBefore);
    }

    /** Past its bound the save keeps the channels written last. */
    @Test
    public void theSaveKeepsTheChannelsWrittenLast() {
        DiscordTopicMemory memory = new DiscordTopicMemory();
        int max = DiscordTopicMemory.MAX_CHANNELS;
        for (int index = 0; index < max + 3; index++) {
            DiscordTopicPacing pacing = memory.pacingOf(Long.toString(1000L + index));
            pacing.want("Server online");
            // The first three channels are written last.
            pacing.posted("Server online", index < 3 ? START + WINDOW + index
                    : START + index);
        }
        List<DiscordTopicMemory.SavedTopic> topics = memory.snapshot().topics;
        assertEquals(max, topics.size());
        assertEquals("1000", topics.get(0).channelId);
        assertEquals("1002", topics.get(2).channelId);
        assertEquals("the oldest three left out", "1006", topics.get(3).channelId);
    }
}
