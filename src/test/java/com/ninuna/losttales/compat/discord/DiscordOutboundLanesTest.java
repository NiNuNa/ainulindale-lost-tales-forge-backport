package com.ninuna.losttales.compat.discord;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * One lane per webhook, each on a clock of its own: a limit on one
 * webhook holds back that webhook's items and nobody else's.
 */
public final class DiscordOutboundLanesTest {

    @Test
    public void lanesKeepOrderAndAreWorkedInTheOrderFirstUsed() {
        DiscordOutboundLanes<String> lanes = new DiscordOutboundLanes<String>();
        assertTrue(lanes.add("b", "b1"));
        assertTrue(lanes.add("a", "a1"));
        assertTrue(lanes.add("b", "b2"));
        assertEquals(Arrays.asList("b", "a"), lanes.due(0L));
        assertEquals("b1", lanes.peek("b"));
        lanes.poll("b");
        assertEquals("b2", lanes.peek("b"));
        assertEquals(Arrays.asList("b2"), lanes.items("b"));
        assertEquals(3 - 1, lanes.size());
        assertNull(lanes.peek("c"));
        assertTrue(lanes.items("c").isEmpty());
        assertEquals(Arrays.asList("b", "a"), lanes.webhooks());
    }

    @Test
    public void aLimitedLaneWaitsAloneAndComesBackOnItsClock() {
        DiscordOutboundLanes<String> lanes = new DiscordOutboundLanes<String>();
        lanes.add("slow", "s1");
        lanes.add("fast", "f1");
        lanes.delay("slow", 5000L);
        assertEquals(Collections.singletonList("fast"), lanes.due(1000L));
        // A lane due now is the earliest clock there is.
        assertEquals(0L, lanes.nextDueMillis());
        lanes.poll("fast");
        assertEquals(5000L, lanes.nextDueMillis());
        assertEquals(Collections.singletonList("slow"), lanes.due(5000L));
        assertTrue(lanes.due(4999L).isEmpty());
        // A lane with nothing waiting has no claim on the clock.
        lanes.poll("slow");
        assertEquals(Long.MAX_VALUE, lanes.nextDueMillis());
        assertTrue(lanes.due(Long.MAX_VALUE).isEmpty());
    }

    /**
     * A failed send holds its own lane back, longer after each failure
     * in a row up to a minute, and a success starts the pauses over.
     */
    @Test
    public void aFailingLaneWaitsLongerEachTimeAndStartsOverAfterASuccess() {
        DiscordOutboundLanes<String> lanes = new DiscordOutboundLanes<String>();
        lanes.add("failing", "f1");
        lanes.add("fine", "o1");
        long shortest = DiscordOutboundLanes.MIN_RETRY_MILLIS;
        assertEquals(1000L + shortest, lanes.failed("failing", 1000L));
        // Only the failing lane waits.
        assertEquals(Collections.singletonList("fine"), lanes.due(1000L));
        assertEquals(1000L + 2L * shortest, lanes.failed("failing", 1000L));
        assertEquals(1000L + 4L * shortest, lanes.failed("failing", 1000L));
        for (int index = 0; index < 10; index++) {
            lanes.failed("failing", 1000L);
        }
        assertEquals(1000L + DiscordOutboundLanes.MAX_RETRY_MILLIS,
                lanes.failed("failing", 1000L));
        lanes.succeeded("failing");
        assertEquals(1000L + shortest, lanes.failed("failing", 1000L));
        // A lane that never existed has nothing to hold back.
        assertEquals(1000L, lanes.failed("none", 1000L));
    }

    /**
     * An item's failures are counted by the lane it waits in, from when
     * it came to the head: one correction waiting in two lanes is given
     * up by each on its own count.
     */
    @Test
    public void anItemsFailuresAreCountedByItsLane() {
        DiscordOutboundLanes<String> lanes = new DiscordOutboundLanes<String>();
        lanes.add("a", "edit");
        lanes.add("b", "edit");
        lanes.add("a", "next");
        lanes.failed("a", 0L);
        lanes.failed("a", 0L);
        assertEquals(2, lanes.headFailures("a"));
        assertEquals(0, lanes.headFailures("b"));
        lanes.poll("a");
        assertEquals(0, lanes.headFailures("a"));
        lanes.failed("a", 0L);
        assertEquals(1, lanes.headFailures("a"));
        lanes.drop("a");
        assertEquals(0, lanes.headFailures("a"));
        assertEquals(0, lanes.headFailures("none"));
    }

    @Test
    public void aLaneIsBoundedAndCanBeDropped() {
        DiscordOutboundLanes<Integer> lanes = new DiscordOutboundLanes<Integer>();
        for (int index = 0; index < DiscordOutboundLanes.MAX_PER_LANE; index++) {
            assertTrue(lanes.add("w", Integer.valueOf(index)));
        }
        assertFalse(lanes.add("w", Integer.valueOf(-1)));
        assertEquals(DiscordOutboundLanes.MAX_PER_LANE, lanes.size());
        assertTrue(lanes.add("other", Integer.valueOf(0)));
        lanes.drop("w");
        assertNull(lanes.peek("w"));
        assertEquals(1, lanes.size());
        // Dropping and delaying a lane that never existed is nothing.
        lanes.drop("none");
        lanes.delay("none", 10L);
        assertEquals(Collections.singletonList("other"), lanes.due(0L));
    }
}
