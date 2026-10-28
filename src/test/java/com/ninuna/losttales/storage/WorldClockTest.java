package com.ninuna.losttales.storage;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The world's memory of the clock (DATA-09, DATA-15): a time more than a
 * day past the last one seen, while the server runs or after a stop that
 * left no mark, is a jump, and the stretch it spans is held until an
 * operator accepts the clock; the gap after a clean stop counts as time
 * passed; a clock set back moves nothing until accepted, and is said; a
 * record that cannot be read is kept as it was and holds everything.
 */
public final class WorldClockTest {

    private static final long DAY = WorldClock.JUMP_MILLIS;
    private static final long START = 1700000000000L;

    @Test
    public void theFirstLookOnlyRemembersTheTime() {
        WorldClock.Store clock = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        assertFalse(clock.observe(START).isJump());
        assertEquals(START, clock.getLastSeen());
        assertFalse(clock.isHolding());
        assertTrue(clock.isDirty());
    }

    @Test
    public void aDayOrLessIsNoJump() {
        WorldClock.Store clock = seen(START);
        assertFalse(clock.observe(START + 60000L).isJump());
        assertFalse(clock.observe(START + 60000L + DAY).isJump());
        assertFalse(clock.isHolding());
        assertEquals(START + 60000L + DAY, clock.getLastSeen());
    }

    @Test
    public void moreThanADayIsAJumpAndItsStretchIsHeld() {
        WorldClock.Store clock = seen(START);
        WorldClock.Look look = clock.observe(START + DAY + 1L);
        assertTrue(look.isJump());
        assertEquals(START, look.getFrom());
        assertEquals(START + DAY + 1L, look.getTo());
        assertTrue(clock.isHolding());
        assertFalse("due before the jump, it was due anyway", clock.holds(START));
        assertTrue(clock.holds(START + 1L));
        assertTrue(clock.holds(START + DAY + 1L));
        assertFalse("due after the jump, time brings it", clock.holds(START + DAY + 2L));
        assertEquals(START + DAY + 1L, clock.getLastSeen());
        assertFalse("the clock running on is no second jump",
                clock.observe(START + DAY + 60001L).isJump());
    }

    @Test
    public void aSecondJumpWidensTheStretchHeld() {
        WorldClock.Store clock = seen(START);
        clock.observe(START + 3 * DAY);
        clock.observe(START + 3 * DAY + 60000L);
        assertTrue(clock.observe(START + 10 * DAY).isJump());
        assertEquals(START, clock.getHeldFrom());
        assertEquals(START + 10 * DAY, clock.getHeldTo());
        assertTrue(clock.holds(START + 5 * DAY));
    }

    /** The gap after a clean stop counts as time passed: a weekend's stop holds nothing. */
    @Test
    public void theGapAfterACleanStopCountsAsTimePassed() {
        WorldClock.Store clock = seen(START);
        clock.stop(START + 1000L);
        assertEquals(START + 1000L, clock.getStoppedAt());
        WorldClock.Store next = reloaded(clock);
        WorldClock.Look look = next.observe(START + 5 * DAY);
        assertFalse(look.isJump());
        assertEquals(START + 1000L, look.getCleanStop());
        assertFalse(next.isHolding());
        assertEquals(START + 5 * DAY, next.getLastSeen());
        assertEquals("the mark is taken", 0L, next.getStoppedAt());
        assertTrue("written at once", next.isDirty());
    }

    /** A crash leaves no mark: the gap after it holds as a jump does. */
    @Test
    public void theGapAfterAStopWithNoMarkHolds() {
        WorldClock.Store clock = seen(START);
        WorldClock.Store next = reloaded(clock);
        WorldClock.Look look = next.observe(START + 5 * DAY);
        assertTrue(look.isJump());
        assertEquals(0L, look.getCleanStop());
        assertTrue(next.holds(START + 2 * DAY));
    }

    /** The mark counts for the first look of a run alone; a jump later in the run holds. */
    @Test
    public void theMarkCountsForTheFirstLookAlone() {
        WorldClock.Store clock = seen(START);
        clock.stop(START + 1000L);
        WorldClock.Store next = reloaded(clock);
        next.observe(START + 2 * DAY);
        WorldClock.Look later = next.observe(START + 5 * DAY);
        assertTrue(later.isJump());
        assertEquals(START + 2 * DAY, next.getHeldFrom());
        WorldClock.Store crashed = reloaded(next);
        assertTrue("no mark is left for a crash after the start",
                crashed.observe(START + 9 * DAY).isJump());
    }

    /** What else asks after the stop in the same run leaves the mark for the next run. */
    @Test
    public void theLooksAfterAStopLeaveTheMark() {
        WorldClock.Store clock = seen(START);
        clock.stop(START + 1000L);
        clock.observe(START + 2000L);
        assertEquals(START + 1000L, clock.getStoppedAt());
        assertEquals(START + 1000L, reloaded(clock).observe(START + 3 * DAY).getCleanStop());
    }

    /** A jump while the server runs stays held over a clean stop. */
    @Test
    public void aJumpBeforeACleanStopStaysHeld() {
        WorldClock.Store clock = seen(START);
        clock.observe(START + 3 * DAY);
        clock.stop(START + 3 * DAY + 1000L);
        WorldClock.Store next = reloaded(clock);
        next.observe(START + 6 * DAY);
        assertTrue(next.isHolding());
        assertEquals(START, next.getHeldFrom());
        assertEquals(START + 3 * DAY, next.getHeldTo());
    }

    @Test
    public void aClockSetBackMovesNothing() {
        WorldClock.Store clock = seen(START);
        assertFalse(clock.observe(START - 5 * DAY).isJump());
        assertEquals(START, clock.getLastSeen());
        assertFalse("back to where it was, no jump", clock.observe(START + 60000L).isJump());
        assertFalse(clock.isHolding());
    }

    /** A clock more than a day behind is said once, and again only after it caught up. */
    @Test
    public void aClockBehindIsSaidOnceARun() {
        WorldClock.Store clock = seen(START + 10 * DAY);
        assertFalse("a day behind is no news", clock.observe(START + 9 * DAY).isNewlyBehind());
        assertTrue(clock.observe(START).isNewlyBehind());
        assertTrue(clock.isBehind(START));
        assertFalse(clock.observe(START + 60000L).isNewlyBehind());
        clock.observe(START + 10 * DAY);
        assertFalse(clock.isBehind(START + 10 * DAY));
        assertTrue(clock.observe(START).isNewlyBehind());
    }

    /** Accepting a clock set back sets the world's time to it, so a jump is seen again. */
    @Test
    public void acceptingAClockBehindSetsTheWorldsTime() {
        WorldClock.Store clock = seen(START + 365 * DAY);
        clock.observe(START);
        assertTrue(clock.accept(START));
        assertEquals(START, clock.getLastSeen());
        assertFalse(clock.isBehind(START));
        assertTrue("the check is no longer blind", clock.observe(START + 3 * DAY).isJump());
    }

    @Test
    public void anAcceptedClockHoldsNothing() {
        WorldClock.Store clock = seen(START);
        clock.observe(START + 4 * DAY);
        assertTrue(clock.accept(START + 4 * DAY + 1000L));
        assertFalse(clock.isHolding());
        assertFalse(clock.holds(START + DAY));
        assertEquals(START + 4 * DAY + 1000L, clock.getLastSeen());
    }

    @Test
    public void theRecordRoundTrips() {
        WorldClock.Store clock = seen(START);
        clock.observe(START + 2 * DAY);
        clock.stop(START + 2 * DAY + 5000L);
        NBTTagCompound saved = new NBTTagCompound();
        clock.writeToNBT(saved);
        WorldClock.Store read = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        read.readFromNBT(saved);
        assertEquals(START + 2 * DAY + 5000L, read.getLastSeen());
        assertEquals(START, read.getHeldFrom());
        assertEquals(START + 2 * DAY, read.getHeldTo());
        assertEquals(START + 2 * DAY + 5000L, read.getStoppedAt());
        assertFalse(read.isPreserved());
    }

    /** Another version, or a shape no writer makes, is kept as it was and holds everything. */
    @Test
    public void aRecordThatCannotBeReadHoldsEverything() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", 3);
        newer.setLong("LastSeen", START);
        newer.setString("Something", "of a later build");
        assertUnreadable(newer);

        NBTTagCompound older = new NBTTagCompound();
        older.setInteger("DataVersion", 1);
        older.setLong("LastSeen", START);
        older.setLong("HeldFrom", 0L);
        older.setLong("HeldTo", 0L);
        assertUnreadable(older);

        NBTTagCompound backwards = written(START, START, START - 1L, 0L);
        assertUnreadable(backwards);

        NBTTagCompound stoppedBeforeTime = written(START, 0L, 0L, -1L);
        assertUnreadable(stoppedBeforeTime);

        NBTTagCompound missing = new NBTTagCompound();
        missing.setInteger("DataVersion", 2);
        missing.setLong("LastSeen", START);
        missing.setLong("HeldFrom", 0L);
        missing.setLong("HeldTo", 0L);
        assertUnreadable(missing);
    }

    private static NBTTagCompound written(long seen, long from, long to, long stopped) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("DataVersion", 2);
        tag.setLong("LastSeen", seen);
        tag.setLong("HeldFrom", from);
        tag.setLong("HeldTo", to);
        tag.setLong("StoppedAt", stopped);
        return tag;
    }

    private static void assertUnreadable(NBTTagCompound saved) {
        WorldClock.Store clock = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        clock.readFromNBT(saved);
        assertTrue(clock.isPreserved());
        assertTrue(clock.isHolding());
        assertTrue(clock.holds(0L));
        assertTrue(clock.holds(Long.MAX_VALUE));
        assertFalse(clock.observe(START + 100 * DAY).isJump());
        assertFalse(clock.isBehind(0L));
        assertFalse("nothing to accept while it cannot be read", clock.accept(START));
        clock.stop(START + 100 * DAY);
        NBTTagCompound written = new NBTTagCompound();
        clock.writeToNBT(written);
        assertEquals("kept as it was", saved, written);
    }

    /** A jump since the last look is held before anything asks what the clock holds. */
    @Test
    public void askingWhatIsHeldLooksAtTheClockFirst() {
        WorldClock.Store clock = seen(START);
        WorldClock.Hold hold = WorldClock.hold(clock, START + 3 * DAY);
        assertTrue(hold.holds(START + DAY));
        assertEquals(START, clock.getHeldFrom());
        assertEquals(START + 3 * DAY, clock.getLastSeen());
    }

    @Test
    public void withNoServerNothingIsHeld() {
        WorldClock.reset();
        assertFalse(WorldClock.hold().holds(START));
        assertFalse(WorldClock.NONE.holds(START));
    }

    private static WorldClock.Store seen(long now) {
        WorldClock.Store clock = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        clock.observe(now);
        return clock;
    }

    /** The store as the next run reads it from the save. */
    private static WorldClock.Store reloaded(WorldClock.Store clock) {
        NBTTagCompound saved = new NBTTagCompound();
        clock.writeToNBT(saved);
        WorldClock.Store read = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        read.readFromNBT(saved);
        return read;
    }
}
