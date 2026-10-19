package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A topic is written only when its words change, at most once an
 * interval, and never more often than Discord's two topic edits per
 * channel in ten minutes; a change that waits for its turn is written
 * when the turn comes, the newest words winning.
 */
public final class DiscordTopicPacingTest {
    private static final long MINUTE = 60000L;
    private static final long INTERVAL = 5L * MINUTE;
    /** A start time well away from zero, as the server's clock is. */
    private static final long START = 1000000000L;

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
     * A last write as the bridge stops skips the interval, but never
     * Discord's two edits in ten minutes: the offline topic waits for no
     * turn and is not sent into a refusal either.
     */
    @Test
    public void aLastWriteSkipsTheIntervalButNotDiscordsAllowance() {
        DiscordTopicPacing pacing = new DiscordTopicPacing();
        pacing.want("online");
        write(pacing, START, false);
        pacing.want("Server offline");
        assertEquals("Server offline", write(pacing, START + MINUTE, true));

        DiscordTopicPacing spent = new DiscordTopicPacing();
        spent.want("1 player");
        write(spent, START, false);
        spent.want("2 players");
        write(spent, START + INTERVAL, false);
        spent.want("Server offline");
        assertNull("two edits in the last ten minutes",
                write(spent, START + INTERVAL + MINUTE, true));
        assertEquals("Server offline",
                write(spent, START + DiscordTopicPacing.WINDOW_MILLIS, true));
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

    @Test
    public void theShortestIntervalSharesDiscordsWindowEvenly() {
        assertEquals(5L * MINUTE, DiscordTopicPacing.SHORTEST_INTERVAL_MILLIS);
        assertEquals(10L * MINUTE, DiscordTopicPacing.WINDOW_MILLIS);
    }
}
