package com.ninuna.losttales.accessory.effect;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A wearer's changes go out at most once a second, and the newest wins (ACC-08). */
public final class AccessoryEffectStateTest {

    private static final String RING = "losttales:one_ring";

    @Test
    public void theFirstChangeGoesOutAtOnce() {
        AccessoryEffectState state = new AccessoryEffectState();
        state.wear(RING);

        assertTrue(state.takeChange(500L));
        assertEquals(RING, state.sent());
        assertEquals(1L, state.sequence());
    }

    @Test
    public void nothingChangedSendsNothing() {
        AccessoryEffectState state = new AccessoryEffectState();
        state.wear("");

        assertFalse(state.takeChange(0L));
        assertEquals(0L, state.sequence());
    }

    @Test
    public void aSecondChangeWithinTheSecondWaitsItsTurn() {
        AccessoryEffectState state = new AccessoryEffectState();
        state.wear(RING);
        assertTrue(state.takeChange(100L));

        state.wear("");
        assertFalse(state.takeChange(101L));
        assertFalse(state.takeChange(119L));
        assertEquals(RING, state.sent());

        assertTrue(state.takeChange(120L));
        assertEquals("", state.sent());
        assertEquals(2L, state.sequence());
    }

    @Test
    public void aChangeUndoneWithinTheSecondIsNeverSent() {
        AccessoryEffectState state = new AccessoryEffectState();
        state.wear(RING);
        assertTrue(state.takeChange(100L));

        state.wear("");
        assertFalse(state.takeChange(105L));
        state.wear(RING);
        assertFalse(state.takeChange(125L));
        assertEquals(1L, state.sequence());
    }

    @Test
    public void ringsTakenOffAndOnEveryTickGoOutOnceASecond() {
        AccessoryEffectState state = new AccessoryEffectState();
        int sent = 0;
        for (long tick = 0L; tick < 200L; tick++) {
            state.wear(tick % 2L == 0L ? RING : "");
            if (state.takeChange(tick)) {
                sent++;
            }
        }
        assertTrue("sent " + sent, sent <= 200 / (int)AccessoryEffectState.TICKS_BETWEEN_CHANGES);
    }

    @Test
    public void aDeathOrALogoutIsToldAtOnce() {
        AccessoryEffectState state = new AccessoryEffectState();
        state.wear(RING);
        assertTrue(state.takeChange(100L));

        assertTrue(state.takeRemoval(101L));
        assertEquals("", state.sent());
        assertEquals("", state.worn());
        assertEquals(2L, state.sequence());
        assertFalse("nothing worn is told once", state.takeRemoval(102L));
    }

    @Test
    public void aRemovalOfWhatWasNeverSentSendsNothing() {
        AccessoryEffectState state = new AccessoryEffectState();
        state.wear(RING);

        assertFalse(state.takeRemoval(0L));
        assertEquals(0L, state.sequence());
    }
}
