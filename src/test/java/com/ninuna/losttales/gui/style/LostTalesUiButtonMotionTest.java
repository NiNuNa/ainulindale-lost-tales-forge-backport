package com.ninuna.losttales.gui.style;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.client.motion.MotionTrack;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion.Character;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A button's four beats. Arriving it dips, rises past its mark and
 * settles onto it; held it sits a pixel below its row; let go it springs
 * back and rings out; left alone it returns exactly to its row. Every
 * place it holds is a whole pixel, the pose depends on the instant
 * rather than on how often the screen was drawn, and each character
 * plays the same beats with its own reach, pace and turn.
 */
public final class LostTalesUiButtonMotionTest {

    private static final long START = 1000L * 1000000L;
    private static final long MILLIS = 1000000L;
    private static final double EPSILON = 1.0E-6D;
    /** How far the lift rises and how deep it presses, as its motion says. */
    private static final double RISE = -liftPose("on");
    private static final double PRESS = liftPose("pressed");

    private MotionTestSettings settings;

    private static double liftPose(String pose) {
        return Motions.get(MotionIds.UI_BUTTON_LIFT).part(
                LostTalesUiButtonMotion.GLYPH).poseValue(pose, MotionTrack.Y);
    }

    @Before
    public void setUp() {
        this.settings = MotionTestSettings.reset();
    }

    @After
    public void tearDown() {
        this.settings.restore();
    }

    /** A button at rest with the pointer away. */
    private static LostTalesUiButtonMotion resting(Character character) {
        LostTalesUiButtonMotion motion =
                new LostTalesUiButtonMotion(character);
        motion.advance(START, false, false, false);
        return motion;
    }

    private static LostTalesUiButtonMotion resting() {
        return resting(Character.LIFT);
    }

    /** The same, risen and settled under the pointer. */
    private static LostTalesUiButtonMotion risen() {
        LostTalesUiButtonMotion motion = resting();
        motion.advance(START, true, true, false);
        motion.advance(START + 400L * MILLIS, true, true, false);
        return motion;
    }

    @Test
    public void aButtonStartsWhereItAlreadyIs() {
        assertEquals(0.0D, resting().offsetY(), EPSILON);
        assertEquals(0.0F, resting().turnDegrees(), EPSILON);
        assertTrue(resting().isSettled());
        assertEquals(0.0F, resting().lit(), EPSILON);
    }

    @Test
    public void arrivingDipsBeforeItRises() {
        LostTalesUiButtonMotion motion = resting();
        motion.advance(START, true, true, false);
        // Early in the arrival, while the anticipation is playing.
        motion.advance(START + 20L * MILLIS, true, true, false);
        assertTrue("dips against the rise", motion.offsetY() > 0.0D);
        assertTrue("and only a little", motion.offsetY() < RISE);
    }

    @Test
    public void theRiseReachesPastItsMarkAndSettlesOnIt() {
        LostTalesUiButtonMotion motion = resting();
        motion.advance(START, true, true, false);
        double furthest = 0.0D;
        for (int millis = 0; millis <= 400; millis += 4) {
            motion.advance(START + millis * MILLIS, true, true,
                    false);
            furthest = Math.min(furthest, motion.offsetY());
        }
        assertTrue("reaches past the mark", furthest < -RISE);
        assertTrue("but never past its own clearing", furthest > -(RISE + 1.0D));
        assertEquals(-RISE, motion.offsetY(), EPSILON);
        assertTrue(motion.isSettled());
    }

    @Test
    public void aHeldButtonSettlesOnePixelBelowItsRow() {
        LostTalesUiButtonMotion motion = risen();
        long pressed = START + 400L * MILLIS;
        motion.advance(pressed, true, true, true);
        motion.advance(pressed + 200L * MILLIS, true, true, true);
        assertEquals(PRESS, motion.offsetY(), EPSILON);
        assertTrue(motion.isSettled());
    }

    @Test
    public void lettingGoSpringsBackPastTheMarkThenRingsOut() {
        LostTalesUiButtonMotion motion = risen();
        long pressed = START + 400L * MILLIS;
        motion.advance(pressed, true, true, true);
        motion.advance(pressed + 100L * MILLIS, true, true, true);
        long released = pressed + 100L * MILLIS;
        double highest = 0.0D;
        double lowestAfterSpring = -10.0D;
        boolean sprung = false;
        for (int millis = 0; millis <= 400; millis += 4) {
            motion.advance(released + millis * MILLIS, true, true,
                    false);
            double place = motion.offsetY();
            highest = Math.min(highest, place);
            sprung |= place < -RISE - 0.5D;
            if (sprung) {
                lowestAfterSpring = Math.max(lowestAfterSpring, place);
            }
        }
        assertTrue("springs up past the mark", sprung);
        assertTrue("stays within a pixel of its clearing",
                highest > -(RISE + 1.5D));
        assertTrue("then leans back the other way",
                lowestAfterSpring > -RISE + 0.1D);
        assertEquals("and settles back on its mark", -RISE,
                motion.offsetY(), EPSILON);
    }

    @Test
    public void leavingReturnsExactlyToItsRow() {
        LostTalesUiButtonMotion motion = risen();
        long left = START + 400L * MILLIS;
        motion.advance(left, false, false, false);
        motion.advance(left + 40L * MILLIS, false, false, false);
        assertTrue("still on its way down", motion.offsetY() < 0.0D);
        motion.advance(left + 300L * MILLIS, false, false, false);
        assertEquals(0.0D, motion.offsetY(), EPSILON);
        assertTrue(motion.isSettled());
    }

    /** A press is not cut short by the pointer moving off the button. */
    @Test
    public void thePointerLeavingDoesNotInterruptASpring() {
        LostTalesUiButtonMotion motion = risen();
        long pressed = START + 400L * MILLIS;
        motion.advance(pressed, true, true, true);
        long released = pressed + 100L * MILLIS;
        motion.advance(released, true, true, false);
        motion.advance(released + 20L * MILLIS, false, false, false);
        assertTrue("the spring is still playing",
                motion.offsetY() < PRESS);
        motion.advance(released + 600L * MILLIS, false, false, false);
        assertEquals(0.0D, motion.offsetY(), EPSILON);
    }

    @Test
    public void theSamePoseWhateverTheFrameRate() {
        LostTalesUiButtonMotion coarse = resting();
        LostTalesUiButtonMotion fine = resting();
        coarse.advance(START, true, true, false);
        fine.advance(START, true, true, false);
        long span = 120L * MILLIS;
        coarse.advance(START + span, true, true, false);
        for (int step = 1; step <= 60; step++) {
            fine.advance(START + span * step / 60, true, true, false);
        }
        assertEquals(coarse.offsetY(), fine.offsetY(), EPSILON);
    }

    /* ---- character ---- */

    /** Only the turning characters turn, and none of them turn at rest. */
    @Test
    public void onlyATurningCharacterTurns() {
        for (Character character : Character.values()) {
            LostTalesUiButtonMotion motion = resting(character);
            assertEquals(character + " is straight at rest", 0.0F,
                    motion.turnDegrees(), EPSILON);
        }
        assertEquals(0.0F, Character.LIFT.getTurnDegrees(), EPSILON);
        assertEquals(0.0F, Character.SNAP.getTurnDegrees(), EPSILON);
        assertTrue(Character.TURN.getTurnDegrees() > 0.0F);
        assertTrue("a turn stays inside what a small glyph can take",
                Character.TURN.getTurnDegrees() <= 6.0F);
    }

    /**
     * A turn is a transient about the middle of the sprite: the glyph
     * spins while it travels and is square again the moment it settles,
     * on its row, risen, or pressed. A glyph left standing at an angle
     * would read as pivoting off its middle and would show its
     * stairsteps.
     */
    @Test
    public void aTurnHappensOnlyWhileTravelling() {
        LostTalesUiButtonMotion motion = resting(Character.TURN);
        assertEquals("square at rest", 0.0F, motion.turnDegrees(), EPSILON);

        motion.advance(START, true, true, false);
        float furthest = 0.0F;
        for (int millis = 0; millis <= 400; millis += 4) {
            motion.advance(START + millis * MILLIS, true, true, false);
            furthest = Math.max(furthest, Math.abs(motion.turnDegrees()));
        }
        assertTrue("turns on the way up", furthest > 1.0F);
        assertEquals("square once risen", 0.0F, motion.turnDegrees(),
                1.0E-4F);

        long pressed = START + 400L * MILLIS;
        motion.advance(pressed, true, true, true);
        furthest = 0.0F;
        for (int millis = 0; millis <= 300; millis += 4) {
            motion.advance(pressed + millis * MILLIS, true, true, true);
            furthest = Math.max(furthest, Math.abs(motion.turnDegrees()));
        }
        assertTrue("turns furthest on a press, its longest journey",
                furthest > Character.TURN.getTurnDegrees());
        assertEquals("square once pressed home", 0.0F, motion.turnDegrees(),
                1.0E-4F);

        long left = pressed + 300L * MILLIS;
        motion.advance(left, false, false, false);
        motion.advance(left + 900L * MILLIS, false, false, false);
        assertEquals("and square again back on its row", 0.0F,
                motion.turnDegrees(), 1.0E-4F);
    }

    /** No character ever turns further than a small glyph can take. */
    @Test
    public void aTurnStaysWithinWhatASmallGlyphCanTake() {
        for (Character character : Character.values()) {
            LostTalesUiButtonMotion motion = resting(character);
            motion.advance(START, true, true, false);
            long at = START;
            for (int millis = 0; millis <= 1200; millis += 4) {
                at = START + millis * MILLIS;
                boolean held = millis > 400 && millis < 700;
                motion.advance(at, true, true, held);
                assertTrue(character + " turn bounded at " + millis,
                        Math.abs(motion.turnDegrees())
                                <= 1.5F * character.getTurnDegrees() + 1.0E-4F);
            }
        }
    }

    /** A snappier character reaches further and gets there sooner. */
    @Test
    public void aSnapReachesFurtherAndSettlesSooner() {
        LostTalesUiButtonMotion snap = resting(Character.SNAP);
        LostTalesUiButtonMotion lift = resting();
        snap.advance(START, true, true, false);
        lift.advance(START, true, true, false);
        long at = START + 400L * MILLIS;
        snap.advance(at, true, true, false);
        lift.advance(at, true, true, false);
        assertTrue("a snap rises further", snap.offsetY() < lift.offsetY());

        // Partway through the arrival the quicker one is already ahead.
        LostTalesUiButtonMotion quick = resting(Character.SNAP);
        LostTalesUiButtonMotion plain = resting();
        quick.advance(START, true, true, false);
        plain.advance(START, true, true, false);
        long midway = START + 90L * MILLIS;
        quick.advance(midway, true, true, false);
        plain.advance(midway, true, true, false);
        assertTrue("and gets there sooner",
                quick.offsetY() < plain.offsetY());
    }

    /**
     * An idle button is perfectly still, in every character. A slow
     * wander of about a pixel does not read as breathing on artwork
     * sampled one texel to one pixel: each crossing of a pixel row is a
     * visible jump rather than a drift.
     */
    @Test
    public void anIdleButtonIsPerfectlyStill() {
        for (Character character : Character.values()) {
            LostTalesUiButtonMotion motion = resting(character);
            for (int millis = 0; millis <= 5000; millis += 40) {
                motion.advance(START + millis * MILLIS, false, false, false);
                assertEquals(character + " is still when idle", 0.0D,
                        motion.offsetY(), EPSILON);
                assertEquals(character + " is straight when idle", 0.0F,
                        motion.turnDegrees(), EPSILON);
            }
        }
    }

    /**
     * A click shorter than the drop still shows it. The mouse can be
     * down for one frame, or none at all between two draws, so the press
     * is held long enough to be seen before the spring begins.
     */
    @Test
    public void aClickTooQuickToSeeStillShowsItsPress() {
        LostTalesUiButtonMotion motion = risen();
        long pressed = START + 400L * MILLIS;
        // Down and up again within a single frame's worth of time.
        motion.advance(pressed, true, true, true);
        motion.advance(pressed + 4L * MILLIS, true, true, false);
        double deepest = -10.0D;
        boolean pressSeen = false;
        for (int millis = 8; millis <= 600; millis += 4) {
            motion.advance(pressed + millis * MILLIS, true, true, false);
            deepest = Math.max(deepest, motion.offsetY());
            pressSeen |= motion.offsetY() > RISE * 0.5D;
        }
        assertTrue("the drop is reached", pressSeen);
        assertEquals("all the way onto the surface",
                PRESS, deepest, 0.05D);
        assertEquals("and it settles back under the pointer", -RISE,
                motion.offsetY(), EPSILON);
    }

    /** A press held down stays down for as long as it is held. */
    @Test
    public void aLongPressStaysDown() {
        LostTalesUiButtonMotion motion = risen();
        long pressed = START + 400L * MILLIS;
        motion.advance(pressed, true, true, true);
        for (int millis = 0; millis <= 2000; millis += 40) {
            motion.advance(pressed + millis * MILLIS, true, true, true);
        }
        assertEquals(PRESS, motion.offsetY(), EPSILON);
        assertTrue(motion.isSettled());
    }

    /**
     * With the player's animation setting off a button is drawn where it
     * was laid out, while its beats keep landing underneath so turning
     * the setting back on finds it where it should stand.
     */
    @Test
    public void animationOffHoldsEveryButtonStill() {
        LostTalesUiButtonMotion motion = resting(Character.TURN);
        LostTalesConfig.animations = false;
        motion.advance(START, true, true, false);
        motion.advance(START + 90L * MILLIS, true, true, false);
        assertEquals(0.0D, motion.offsetY(), EPSILON);
        assertEquals(0.0F, motion.turnDegrees(), EPSILON);
        assertTrue("the crossing to the lit artwork still runs",
                motion.lit() > 0.0F);
        // Switched back on, the button stands where the beat left it.
        LostTalesConfig.animations = true;
        motion.advance(START + 400L * MILLIS, true, true, false);
        assertEquals(-RISE, motion.offsetY(), EPSILON);
    }

    /**
     * Every place a button holds is a whole pixel, so a settled pose
     * lands on the display grid at every GUI Scale.
     */
    @Test
    public void everyPlaceItHoldsIsAWholePixel() {
        for (double place : new double[] {0.0D, -RISE, PRESS}) {
            assertEquals(place, Math.round(place), EPSILON);
        }
    }
}
