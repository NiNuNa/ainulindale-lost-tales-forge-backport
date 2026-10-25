package com.ninuna.losttales.storage;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The world's memory of the clock (DATA-09): a time more than a day past
 * the last one seen is a jump, the stretch it spans is held until an
 * operator accepts the clock, a clock set back moves nothing, and a
 * record that cannot be read is kept as it was and holds everything.
 */
public final class WorldClockTest {

    private static final long DAY = WorldClock.JUMP_MILLIS;
    private static final long START = 1700000000000L;

    @Test
    public void theFirstLookOnlyRemembersTheTime() {
        WorldClock.Store clock = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        assertNull(clock.observe(START));
        assertEquals(START, clock.getLastSeen());
        assertFalse(clock.isHolding());
        assertTrue(clock.isDirty());
    }

    @Test
    public void aDayOrLessIsNoJump() {
        WorldClock.Store clock = seen(START);
        assertNull(clock.observe(START + 60000L));
        assertNull(clock.observe(START + 60000L + DAY));
        assertFalse(clock.isHolding());
        assertEquals(START + 60000L + DAY, clock.getLastSeen());
    }

    @Test
    public void moreThanADayIsAJumpAndItsStretchIsHeld() {
        WorldClock.Store clock = seen(START);
        WorldClock.Jump jump = clock.observe(START + DAY + 1L);
        assertNotNull(jump);
        assertEquals(START, jump.getFrom());
        assertEquals(START + DAY + 1L, jump.getTo());
        assertTrue(clock.isHolding());
        assertFalse("due before the jump, it was due anyway", clock.holds(START));
        assertTrue(clock.holds(START + 1L));
        assertTrue(clock.holds(START + DAY + 1L));
        assertFalse("due after the jump, time brings it", clock.holds(START + DAY + 2L));
        assertEquals(START + DAY + 1L, clock.getLastSeen());
        assertNull("the clock running on is no second jump", clock.observe(START + DAY + 60001L));
    }

    @Test
    public void aSecondJumpWidensTheStretchHeld() {
        WorldClock.Store clock = seen(START);
        clock.observe(START + 3 * DAY);
        clock.observe(START + 3 * DAY + 60000L);
        assertNotNull(clock.observe(START + 10 * DAY));
        assertEquals(START, clock.getHeldFrom());
        assertEquals(START + 10 * DAY, clock.getHeldTo());
        assertTrue(clock.holds(START + 5 * DAY));
    }

    @Test
    public void aClockSetBackMovesNothing() {
        WorldClock.Store clock = seen(START);
        assertNull(clock.observe(START - 5 * DAY));
        assertEquals(START, clock.getLastSeen());
        assertNull("back to where it was, no jump", clock.observe(START + 60000L));
        assertFalse(clock.isHolding());
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
        NBTTagCompound saved = new NBTTagCompound();
        clock.writeToNBT(saved);
        WorldClock.Store read = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        read.readFromNBT(saved);
        assertEquals(START + 2 * DAY, read.getLastSeen());
        assertEquals(START, read.getHeldFrom());
        assertEquals(START + 2 * DAY, read.getHeldTo());
        assertFalse(read.isPreserved());
    }

    /** Another version, or a shape no writer makes, is kept as it was and holds everything. */
    @Test
    public void aRecordThatCannotBeReadHoldsEverything() {
        NBTTagCompound newer = new NBTTagCompound();
        newer.setInteger("DataVersion", 2);
        newer.setLong("LastSeen", START);
        newer.setString("Something", "of a later build");
        assertUnreadable(newer);

        NBTTagCompound backwards = new NBTTagCompound();
        backwards.setInteger("DataVersion", 1);
        backwards.setLong("LastSeen", START);
        backwards.setLong("HeldFrom", START);
        backwards.setLong("HeldTo", START - 1L);
        assertUnreadable(backwards);

        NBTTagCompound missing = new NBTTagCompound();
        missing.setInteger("DataVersion", 1);
        missing.setLong("LastSeen", START);
        assertUnreadable(missing);
    }

    private static void assertUnreadable(NBTTagCompound saved) {
        WorldClock.Store clock = new WorldClock.Store(WorldClock.Store.DATA_NAME);
        clock.readFromNBT(saved);
        assertTrue(clock.isPreserved());
        assertTrue(clock.isHolding());
        assertTrue(clock.holds(0L));
        assertTrue(clock.holds(Long.MAX_VALUE));
        assertNull(clock.observe(START + 100 * DAY));
        assertFalse("nothing to accept while it cannot be read", clock.accept(START));
        NBTTagCompound written = new NBTTagCompound();
        clock.writeToNBT(written);
        assertEquals("kept as it was", saved, written);
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
}
