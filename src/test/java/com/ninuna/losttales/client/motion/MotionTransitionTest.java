package com.ninuna.losttales.client.motion;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The shared transition's contract: elapsed-time sampling, so the motion
 * is the same at any frame rate, and a reversal that sets out from the
 * value on screen instead of teleporting to the far end first — the
 * failure the chat's chevron showed when it was flipped twice in quick
 * succession. Its times and curves are its motion's.
 */
public final class MotionTransitionTest {
    private static final long MILLIS = 1000000L;
    private static final String LINEAR = "test.linear";
    private static final String SMOOTH = "test.smooth";
    private static final String SWING = "test.swing";
    private static final String UNEVEN = "test.uneven";

    private MotionTestSettings settings;

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
        MotionTestSettings.preview("{"
                + "\"test.linear\": {\"duration\": 200, \"curve\": \"linear\"},"
                + "\"test.smooth\": {\"duration\": 200, \"curve\": \"ease_in_out\"},"
                + "\"test.swing\": {\"duration\": 200, \"curve\": \"back_out\"},"
                + "\"test.uneven\": {\"duration\": 200, \"curve\": \"linear\","
                + " \"off\": {\"duration\": 100}}}");
    }

    @After
    public void tearDown() {
        this.settings.restore();
    }

    @Test
    public void firstSightSettlesRatherThanTravelling() {
        MotionTransition transition = new MotionTransition(LINEAR);
        assertEquals(1.0F, transition.advance(0L, true), 0.0001F);
        assertTrue(transition.isSettled());
    }

    @Test
    public void travelsAcrossItsDurationAndStops() {
        MotionTransition transition = new MotionTransition(LINEAR);
        transition.advance(0L, false);
        // The leg begins at the frame the state flips, so that frame is
        // still the value it flipped from.
        assertEquals(0.0F, transition.advance(0L, true), 0.0001F);
        assertEquals(0.5F, transition.advance(100L * MILLIS, true), 0.0001F);
        assertFalse(transition.isSettled());
        assertEquals(1.0F, transition.advance(200L * MILLIS, true), 0.0001F);
        assertTrue(transition.isSettled());
        // Past the end it stays put rather than running on.
        assertEquals(1.0F, transition.advance(900L * MILLIS, true), 0.0001F);
    }

    @Test
    public void eachWayTakesItsOwnBeat() {
        MotionTransition transition = new MotionTransition(UNEVEN);
        transition.advance(0L, true);
        transition.advance(0L, false);
        assertEquals(0.5F, transition.advance(50L * MILLIS, false), 0.0001F);
        assertEquals(0.0F, transition.advance(100L * MILLIS, false), 0.0001F);
    }

    @Test
    public void theSampleDoesNotDependOnHowOftenItIsAdvanced() {
        MotionTransition coarse = new MotionTransition(SMOOTH);
        MotionTransition fine = new MotionTransition(SMOOTH);
        coarse.advance(0L, false);
        fine.advance(0L, false);
        coarse.advance(1L, true);
        fine.advance(1L, true);
        for (long nanos = 10L * MILLIS; nanos <= 120L * MILLIS;
             nanos += 10L * MILLIS) {
            fine.advance(nanos, true);
        }
        coarse.advance(120L * MILLIS, true);
        assertEquals(coarse.value(), fine.value(), 0.0001F);
    }

    @Test
    public void aReversalSetsOutFromWhatIsOnScreen() {
        MotionTransition transition = new MotionTransition(LINEAR);
        transition.advance(0L, false);
        transition.advance(1L, true);
        float midway = transition.advance(60L * MILLIS, true);
        assertEquals(0.3F, midway, 0.01F);
        // Turned around: the very next sample is where it already was,
        // never the end it was travelling toward.
        float reversed = transition.advance(60L * MILLIS, false);
        assertEquals(midway, reversed, 0.0001F);
    }

    @Test
    public void aReversalOnlyCostsTheDistanceLeftToCover() {
        MotionTransition transition = new MotionTransition(LINEAR);
        transition.advance(0L, false);
        transition.advance(1L, true);
        transition.advance(60L * MILLIS, true);
        transition.advance(60L * MILLIS, false);
        // Three tenths of the way out is three tenths of the way back:
        // a full duration later it would still be travelling.
        assertEquals(0.0F, transition.advance(121L * MILLIS, false), 0.0001F);
        assertTrue(transition.isSettled());
    }

    @Test
    public void rapidTogglingNeverLeavesTheRange() {
        MotionTransition transition = new MotionTransition(SMOOTH);
        transition.advance(0L, false);
        boolean on = true;
        for (long nanos = MILLIS; nanos < 400L * MILLIS;
             nanos += 7L * MILLIS) {
            float value = transition.advance(nanos, on);
            assertTrue("value left 0..1: " + value,
                    value >= -0.0001F && value <= 1.0001F);
            on = !on;
        }
    }

    @Test
    public void motionOffLandsAtOnce() {
        MotionTransition transition = new MotionTransition(LINEAR);
        transition.advance(0L, false);
        LostTalesConfig.animations = false;
        assertEquals(1.0F, transition.advance(MILLIS, true), 0.0001F);
        assertTrue(transition.isSettled());
    }

    @Test
    public void speedScalesTheTime() {
        MotionTransition transition = new MotionTransition(LINEAR);
        transition.advance(0L, false);
        LostTalesConfig.animationSpeed = 2.0D;
        transition.advance(0L, true);
        assertEquals(0.5F, transition.advance(50L * MILLIS, true), 0.0001F);
        assertEquals(1.0F, transition.advance(100L * MILLIS, true), 0.0001F);
    }

    @Test
    public void reducedMotionSetsTravelDownAndKeepsFadesShort() {
        LostTalesConfig.reducedMotion = true;
        MotionTransition slide = new MotionTransition(LINEAR, true);
        slide.advance(0L, false);
        assertEquals(1.0F, slide.advance(MILLIS, true), 0.0001F);
        MotionTransition fade = new MotionTransition(LINEAR);
        fade.advance(0L, false);
        fade.advance(0L, true);
        assertEquals(0.5F, fade.advance(
                Motions.REDUCED_MILLIS / 2 * MILLIS, true), 0.0001F);
        assertEquals(1.0F, fade.advance(Motions.REDUCED_MILLIS * MILLIS, true),
                0.0001F);
    }

    @Test
    public void reducedMotionTradesASwingForAPlainCurve() {
        LostTalesConfig.reducedMotion = true;
        MotionTransition transition = new MotionTransition(SWING);
        transition.advance(0L, false);
        transition.advance(0L, true);
        for (long nanos = 0L; nanos <= 100L * MILLIS; nanos += MILLIS) {
            assertTrue(transition.advance(nanos, true) <= 1.0F);
        }
    }

    @Test
    public void clampedBoundsAnOvershootingCurve() {
        MotionTransition transition = new MotionTransition(SWING);
        transition.advance(0L, false);
        transition.advance(1L, true);
        boolean passed = false;
        for (long nanos = MILLIS; nanos <= 200L * MILLIS; nanos += MILLIS) {
            transition.advance(nanos, true);
            passed |= transition.value() > 1.0F;
            assertTrue(transition.clamped() >= 0.0F);
            assertTrue(transition.clamped() <= 1.0F);
        }
        assertTrue("the curve does swing past its end", passed);
    }

    @Test
    public void aMotionNoFileHasLandsAtOnce() {
        MotionTransition transition = new MotionTransition("test.missing");
        transition.advance(0L, false);
        assertEquals(1.0F, transition.advance(MILLIS, true), 0.0001F);
    }
}
