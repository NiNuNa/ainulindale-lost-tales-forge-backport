package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatDeliveryMark;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A sender hears about their own line's Discord post at most twice: a
 * clock once it has waited five seconds, then the clock lifted or a
 * crimson mark, the worst of its copies deciding which.
 */
public final class DiscordDeliveryTrackerTest {
    private static final UUID SENDER =
            UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final long WAIT = DiscordDeliveryTracker.RETRY_MARK_AFTER_MILLIS;

    @Test
    public void aLineWaitingFiveSecondsShowsExactlyOneClock() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, SENDER, 1000L);
        assertTrue(tracker.due(1000L + WAIT - 1L).isEmpty());
        List<DiscordDeliveryTracker.Mark> marks = tracker.due(1000L + WAIT);
        assertEquals(1, marks.size());
        DiscordDeliveryTracker.Mark clock = marks.get(0);
        assertEquals(SENDER, clock.senderId);
        assertEquals(7L, clock.messageId);
        assertEquals(ChatDeliveryMark.State.RETRYING, clock.state);
        assertEquals(ChatDeliveryMark.Reason.WAITING, clock.reason);
        // One clock a line, however long it goes on waiting.
        assertTrue(tracker.due(1000L + 10L * WAIT).isEmpty());
    }

    @Test
    public void theClockSaysWhatTheLineWaitsOn() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, SENDER, 0L);
        tracker.queued(8L, SENDER, 0L);
        tracker.limited(7L);
        tracker.failed(8L);
        List<DiscordDeliveryTracker.Mark> marks = tracker.due(WAIT);
        assertEquals(2, marks.size());
        assertEquals(ChatDeliveryMark.Reason.LIMITED, marks.get(0).reason);
        assertEquals(ChatDeliveryMark.Reason.FAILING, marks.get(1).reason);
    }

    @Test
    public void theClockIsLiftedOnlyWhenOneWasShown() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, SENDER, 0L);
        assertNull("a line out in time says nothing", tracker.delivered(7L));
        assertEquals(0, tracker.size());

        tracker.queued(8L, SENDER, 0L);
        assertEquals(1, tracker.due(WAIT).size());
        DiscordDeliveryTracker.Mark lifted = tracker.delivered(8L);
        assertEquals(8L, lifted.messageId);
        assertEquals(ChatDeliveryMark.State.NONE, lifted.state);
        assertEquals(ChatDeliveryMark.Reason.NONE, lifted.reason);
        assertEquals(0, tracker.size());
    }

    @Test
    public void aRefusedLineIsMarkedAtOnce() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, SENDER, 0L);
        DiscordDeliveryTracker.Mark failed =
                tracker.lost(7L, ChatDeliveryMark.Reason.REFUSED);
        assertEquals(SENDER, failed.senderId);
        assertEquals(ChatDeliveryMark.State.FAILED, failed.state);
        assertEquals(ChatDeliveryMark.Reason.REFUSED, failed.reason);
        assertEquals(0, tracker.size());
        assertTrue(tracker.due(WAIT).isEmpty());
    }

    @Test
    public void theWorstCopyOfALineIsWhatItsSenderHears() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        // One copy lost and one delivered: the crimson mark stands.
        tracker.queued(7L, SENDER, 0L);
        tracker.queued(7L, SENDER, 0L);
        assertEquals(ChatDeliveryMark.State.FAILED,
                tracker.lost(7L, ChatDeliveryMark.Reason.WEBHOOK_OFF).state);
        assertTrue("no clock after a crimson mark", tracker.due(WAIT).isEmpty());
        assertNull(tracker.delivered(7L));
        assertEquals(0, tracker.size());

        // A clock, then one copy out and one still waiting: nothing yet.
        tracker.queued(8L, SENDER, 0L);
        tracker.queued(8L, SENDER, 0L);
        assertEquals(1, tracker.due(WAIT).size());
        assertNull(tracker.delivered(8L));

        // A second copy lost is not told twice.
        tracker.queued(9L, SENDER, 0L);
        tracker.queued(9L, SENDER, 0L);
        assertEquals(ChatDeliveryMark.State.FAILED,
                tracker.lost(9L, ChatDeliveryMark.Reason.REFUSED).state);
        assertNull(tracker.lost(9L, ChatDeliveryMark.Reason.GAVE_UP));

        // The clocked line's last copy out lifts its clock.
        assertEquals(ChatDeliveryMark.State.NONE, tracker.delivered(8L).state);
        assertEquals(0, tracker.size());
    }

    /** A copy that reaches its lane after its line was lost adds nothing. */
    @Test
    public void aCopyOfALostLineQueuedLaterSaysNothing() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, SENDER, 0L);
        assertEquals(ChatDeliveryMark.State.FAILED,
                tracker.lost(7L, ChatDeliveryMark.Reason.WEBHOOK_OFF).state);
        tracker.queued(7L, SENDER, 0L);
        assertEquals(0, tracker.size());
        assertTrue(tracker.due(WAIT).isEmpty());
        assertNull(tracker.delivered(7L));
    }

    @Test
    public void theNextClockIsDueFiveSecondsAfterTheOldestLineWithoutOne() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        assertEquals(Long.MAX_VALUE, tracker.nextDueMillis());
        tracker.queued(7L, SENDER, 1000L);
        tracker.queued(8L, SENDER, 3000L);
        assertEquals(1000L + WAIT, tracker.nextDueMillis());
        tracker.due(1000L + WAIT);
        assertEquals(3000L + WAIT, tracker.nextDueMillis());
        tracker.due(3000L + WAIT);
        assertEquals(Long.MAX_VALUE, tracker.nextDueMillis());
    }

    @Test
    public void aStopMarksEveryLineStillWaitingAndForgetsThemAll() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, SENDER, 0L);
        tracker.queued(9L, SENDER, 0L);
        tracker.queued(9L, SENDER, 0L);
        tracker.lost(9L, ChatDeliveryMark.Reason.REFUSED);
        tracker.queued(8L, SENDER, WAIT);
        // 7 shows a clock, 8 is too young for one, 9 was told already.
        assertEquals(1, tracker.due(WAIT).size());
        List<DiscordDeliveryTracker.Mark> stopped = tracker.abandon();
        assertEquals(2, stopped.size());
        assertEquals(7L, stopped.get(0).messageId);
        assertEquals(8L, stopped.get(1).messageId);
        for (DiscordDeliveryTracker.Mark mark : stopped) {
            assertEquals(ChatDeliveryMark.State.FAILED, mark.state);
            assertEquals(ChatDeliveryMark.Reason.STOPPED, mark.reason);
        }
        assertEquals(0, tracker.size());
        assertEquals(Long.MAX_VALUE, tracker.nextDueMillis());
        assertTrue(tracker.abandon().isEmpty());
    }

    @Test
    public void theOldestLineFallsOutPastTheBound() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        for (int index = 0; index <= DiscordDeliveryTracker.MAX_TRACKED; index++) {
            tracker.queued(1L + index, SENDER, index);
        }
        assertEquals(DiscordDeliveryTracker.MAX_TRACKED, tracker.size());
        assertNull("the oldest line is no longer followed",
                tracker.lost(1L, ChatDeliveryMark.Reason.REFUSED));
        assertEquals(ChatDeliveryMark.State.FAILED,
                tracker.lost(2L, ChatDeliveryMark.Reason.REFUSED).state);
    }

    @Test
    public void aLineWithoutASenderIsNeverTracked() {
        DiscordDeliveryTracker tracker = new DiscordDeliveryTracker();
        tracker.queued(7L, null, 0L);
        // Nor is an id no server hands out.
        tracker.queued(0L, SENDER, 0L);
        assertEquals(0, tracker.size());
        assertTrue(tracker.due(WAIT).isEmpty());
        assertNull(tracker.lost(7L, ChatDeliveryMark.Reason.REFUSED));
        assertNull(tracker.delivered(7L));
        assertTrue(tracker.abandon().isEmpty());
    }
}
