package com.ninuna.losttales.client.gui.animation;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The shared transition's contract: elapsed-time sampling, so the motion
 * is the same at any frame rate, and a reversal that sets out from the
 * value on screen instead of teleporting to the far end first — the
 * failure the chat's chevron showed when it was flipped twice in quick
 * succession.
 */
public final class LostTalesUiTransitionTest {
    private static final long MILLIS = 1000000L;
    private static final int DURATION = 200;

    @Test
    public void firstSightSettlesRatherThanTravelling() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        assertEquals(1.0F, transition.advance(0L, true, DURATION,
                LostTalesUiEasing.LINEAR), 0.0001F);
        assertTrue(transition.isSettled());
    }

    @Test
    public void travelsAcrossItsDurationAndStops() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        transition.advance(0L, false, DURATION, LostTalesUiEasing.LINEAR);
        // The leg begins at the frame the state flips, so that frame is
        // still the value it flipped from.
        assertEquals(0.0F, transition.advance(0L, true, DURATION,
                LostTalesUiEasing.LINEAR), 0.0001F);
        assertEquals(0.5F, transition.advance(100L * MILLIS, true, DURATION,
                LostTalesUiEasing.LINEAR), 0.0001F);
        assertFalse(transition.isSettled());
        assertEquals(1.0F, transition.advance(200L * MILLIS, true, DURATION,
                LostTalesUiEasing.LINEAR), 0.0001F);
        assertTrue(transition.isSettled());
        // Past the end it stays put rather than running on.
        assertEquals(1.0F, transition.advance(900L * MILLIS, true, DURATION,
                LostTalesUiEasing.LINEAR), 0.0001F);
    }

    @Test
    public void theSampleDoesNotDependOnHowOftenItIsAdvanced() {
        LostTalesUiTransition coarse = new LostTalesUiTransition();
        LostTalesUiTransition fine = new LostTalesUiTransition();
        coarse.advance(0L, false, DURATION, LostTalesUiEasing.SMOOTH);
        fine.advance(0L, false, DURATION, LostTalesUiEasing.SMOOTH);
        coarse.advance(1L, true, DURATION, LostTalesUiEasing.SMOOTH);
        fine.advance(1L, true, DURATION, LostTalesUiEasing.SMOOTH);
        for (long nanos = 10L * MILLIS; nanos <= 120L * MILLIS;
             nanos += 10L * MILLIS) {
            fine.advance(nanos, true, DURATION, LostTalesUiEasing.SMOOTH);
        }
        coarse.advance(120L * MILLIS, true, DURATION,
                LostTalesUiEasing.SMOOTH);
        assertEquals(coarse.value(), fine.value(), 0.0001F);
    }

    @Test
    public void aReversalSetsOutFromWhatIsOnScreen() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        transition.advance(0L, false, DURATION, LostTalesUiEasing.LINEAR);
        transition.advance(1L, true, DURATION, LostTalesUiEasing.LINEAR);
        float midway = transition.advance(60L * MILLIS, true, DURATION,
                LostTalesUiEasing.LINEAR);
        assertEquals(0.3F, midway, 0.01F);
        // Turned around: the very next sample is where it already was,
        // never the end it was travelling toward.
        float reversed = transition.advance(60L * MILLIS, false, DURATION,
                LostTalesUiEasing.LINEAR);
        assertEquals(midway, reversed, 0.0001F);
    }

    @Test
    public void aReversalOnlyCostsTheDistanceLeftToCover() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        transition.advance(0L, false, DURATION, LostTalesUiEasing.LINEAR);
        transition.advance(1L, true, DURATION, LostTalesUiEasing.LINEAR);
        transition.advance(60L * MILLIS, true, DURATION,
                LostTalesUiEasing.LINEAR);
        transition.advance(60L * MILLIS, false, DURATION,
                LostTalesUiEasing.LINEAR);
        // Three tenths of the way out is three tenths of the way back:
        // a full duration later it would still be travelling.
        assertEquals(0.0F, transition.advance(121L * MILLIS, false, DURATION,
                LostTalesUiEasing.LINEAR), 0.0001F);
        assertTrue(transition.isSettled());
    }

    @Test
    public void rapidTogglingNeverLeavesTheRange() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        transition.advance(0L, false, DURATION, LostTalesUiEasing.SMOOTH);
        boolean on = true;
        for (long nanos = MILLIS; nanos < 400L * MILLIS;
             nanos += 7L * MILLIS) {
            float value = transition.advance(nanos, on, DURATION,
                    LostTalesUiEasing.SMOOTH);
            assertTrue("value left 0..1: " + value,
                    value >= -0.0001F && value <= 1.0001F);
            on = !on;
        }
    }

    @Test
    public void zeroDurationLandsAtOnce() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        transition.advance(0L, false, DURATION, LostTalesUiEasing.LINEAR);
        assertEquals(1.0F, transition.advance(MILLIS, true, 0,
                LostTalesUiEasing.LINEAR), 0.0001F);
        assertTrue(transition.isSettled());
    }

    @Test
    public void clampedBoundsAnOvershootingCurve() {
        LostTalesUiTransition transition = new LostTalesUiTransition();
        transition.advance(0L, false, DURATION, LostTalesUiEasing.BACK_OUT);
        transition.advance(1L, true, DURATION, LostTalesUiEasing.BACK_OUT);
        for (long nanos = MILLIS; nanos <= 200L * MILLIS; nanos += MILLIS) {
            transition.advance(nanos, true, DURATION,
                    LostTalesUiEasing.BACK_OUT);
            assertTrue(transition.clamped() >= 0.0F);
            assertTrue(transition.clamped() <= 1.0F);
        }
    }
}
