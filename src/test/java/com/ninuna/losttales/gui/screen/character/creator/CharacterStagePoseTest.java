package com.ninuna.losttales.gui.screen.character.creator;

import com.ninuna.losttales.client.motion.MotionTestSettings;
import com.ninuna.losttales.config.LostTalesConfig;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** How the stage answers the mouse, and where the head stops following it. */
public class CharacterStagePoseTest {

    private static final float EPSILON = 0.001F;
    /** Thirty pixels dragged sideways turn the figure this far. */
    private static final float THIRTY_PIXELS_OF_TURN =
            30.0F * CharacterStagePose.DEGREES_PER_DRAG_PIXEL;
    /** Two notches of the wheel forward. */
    private static final float TWO_NOTCHES_NEARER = 1.12F * 1.12F;

    private long clock = 1L;

    /** A second of long frames: what is shown has caught up with what was asked. */
    private void settle(CharacterStagePose pose) {
        for (int frame = 0; frame < 60; frame++) {
            this.clock += 100000000L;
            pose.advance(this.clock);
        }
    }

    @Test
    public void startsSquareToTheScreen() {
        CharacterStagePose pose = new CharacterStagePose();
        settle(pose);
        assertEquals(0.0F, pose.getShownYaw(), EPSILON);
        assertEquals(0.0F, pose.getShownPitch(), EPSILON);
        assertEquals(1.0F, pose.getShownZoom(), EPSILON);
    }

    @Test
    public void draggingRightTurnsTheFrontToTheRight() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(10, 0);
        settle(pose);
        assertTrue(pose.getShownYaw() > 0.0F);
        pose.drag(-20, 0);
        settle(pose);
        assertTrue(pose.getShownYaw() < 0.0F);
    }

    @Test
    public void draggingDownLeansTheHeadTowardTheViewerWithinItsLimit() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(0, 10);
        settle(pose);
        assertTrue(pose.getShownPitch() > 0.0F);
        pose.drag(0, 10000);
        settle(pose);
        assertEquals(CharacterStagePose.PITCH_LIMIT, pose.getShownPitch(), EPSILON);
        pose.drag(0, -20000);
        settle(pose);
        assertEquals(-CharacterStagePose.PITCH_LIMIT, pose.getShownPitch(), EPSILON);
    }

    @Test
    public void theYawWrapsRatherThanGrowingWithoutEnd() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(100000, 0);
        settle(pose);
        assertTrue(pose.getShownYaw() >= -180.0F && pose.getShownYaw() < 180.0F);
        assertEquals(-180.0F, CharacterStagePose.wrap(180.0F), EPSILON);
        assertEquals(10.0F, CharacterStagePose.wrap(370.0F), EPSILON);
        assertEquals(-10.0F, CharacterStagePose.wrap(-370.0F), EPSILON);
    }

    @Test
    public void theWheelZoomsWithinItsLimitsAndForwardBringsNearer() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.wheel(1);
        settle(pose);
        assertTrue(pose.getShownZoom() > 1.0F);
        pose.wheel(100);
        settle(pose);
        assertEquals(CharacterStagePose.ZOOM_MAX, pose.getShownZoom(), EPSILON);
        pose.wheel(-200);
        settle(pose);
        assertEquals(CharacterStagePose.ZOOM_MIN, pose.getShownZoom(), EPSILON);
        pose.wheel(0);
        settle(pose);
        assertEquals(CharacterStagePose.ZOOM_MIN, pose.getShownZoom(), EPSILON);
    }

    @Test
    public void resetStandsItSquareAgain() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(30, 30);
        pose.wheel(3);
        settle(pose);
        pose.reset();
        settle(pose);
        assertEquals(0.0F, pose.getShownYaw(), EPSILON);
        assertEquals(0.0F, pose.getShownPitch(), EPSILON);
        assertEquals(1.0F, pose.getShownZoom(), EPSILON);
    }

    @Test
    public void withMotionReducedTheCameraArrivesAtOnce() {
        MotionTestSettings settings = MotionTestSettings.reset();
        try {
            LostTalesConfig.reducedMotion = true;
            CharacterStagePose pose = new CharacterStagePose();
            long now = 1000000000L;
            pose.advance(now);
            pose.drag(30, 0);
            pose.wheel(2);
            pose.advance(now + 16000000L);
            assertEquals(THIRTY_PIXELS_OF_TURN, pose.getShownYaw(), EPSILON);
            assertEquals(TWO_NOTCHES_NEARER, pose.getShownZoom(), EPSILON);
        } finally {
            settings.restore();
        }
    }

    @Test
    public void whatIsShownEasesTowardWhatWasAskedAndArrives() {
        CharacterStagePose pose = new CharacterStagePose();
        long now = 1000000000L;
        pose.advance(now);
        pose.drag(30, 0);
        pose.wheel(2);
        // One short frame later it has moved, but not all the way.
        pose.advance(now + 16000000L);
        assertTrue(pose.getShownYaw() > 0.0F);
        assertTrue(pose.getShownYaw() < THIRTY_PIXELS_OF_TURN);
        assertTrue(pose.getShownZoom() > 1.0F);
        assertTrue(pose.getShownZoom() < TWO_NOTCHES_NEARER);
        // A second later it is there.
        for (int frame = 1; frame <= 60; frame++) {
            pose.advance(now + 16000000L + frame * 16000000L);
        }
        assertEquals(THIRTY_PIXELS_OF_TURN, pose.getShownYaw(), 0.01F);
        assertEquals(TWO_NOTCHES_NEARER, pose.getShownZoom(), 0.001F);
    }

    @Test
    public void theFirstFrameAndAResetShowTheAskedPoseAtOnce() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(40, 10);
        pose.advance(5000000000L);
        assertEquals(40.0F * CharacterStagePose.DEGREES_PER_DRAG_PIXEL,
                pose.getShownYaw(), EPSILON);
        assertEquals(10.0F * 0.45F, pose.getShownPitch(), EPSILON);
        pose.reset();
        pose.advance(6000000000L);
        assertEquals(0.0F, pose.getShownYaw(), EPSILON);
        assertEquals(1.0F, pose.getShownZoom(), EPSILON);
    }

    @Test
    public void theEaseTakesTheShortWayRoundTheCircle() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(170.0F / CharacterStagePose.DEGREES_PER_DRAG_PIXEL, 0.0F);
        pose.advance(1000000000L);
        pose.drag(30.0F / CharacterStagePose.DEGREES_PER_DRAG_PIXEL, 0.0F);
        // Asked for -160 from 170: twenty degrees on, not three hundred back.
        pose.advance(1016000000L);
        assertTrue(pose.getShownYaw() > 170.0F || pose.getShownYaw() < -160.0F);
    }

    @Test
    public void theHeadFollowsAPointerBesideItWhileFacingOut() {
        CharacterStagePose pose = new CharacterStagePose();
        assertEquals(0.0F, pose.headYawToward(0.0F), EPSILON);
        float right = pose.headYawToward(40.0F);
        float left = pose.headYawToward(-40.0F);
        assertTrue(right > 0.0F);
        assertEquals(-right, left, EPSILON);
        assertTrue(right <= CharacterStagePose.HEAD_FOLLOW_LIMIT);
        assertTrue(pose.headPitchToward(40.0F) > 0.0F);
        assertTrue(pose.headPitchToward(-40.0F) < 0.0F);
    }

    @Test
    public void theHeadLetsThePointerGoOnceTheFigureIsTurnedAway() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(180.0F / CharacterStagePose.DEGREES_PER_DRAG_PIXEL, 0.0F);
        // The head follows what is drawn, so the turn has to be shown first.
        pose.advance(1L);
        assertEquals(0.0F, pose.headYawToward(40.0F), EPSILON);
        assertEquals(0.0F, pose.headPitchToward(40.0F), EPSILON);
    }

    @Test
    public void thePageShotAndThePlayersTurnAddUp() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.setShot(CharacterCreatorShot.FULL_BACK);
        pose.drag(30.0F / CharacterStagePose.DEGREES_PER_DRAG_PIXEL, 0.0F);
        pose.advance(1L);
        assertEquals(-150.0F, pose.getShownYaw(), EPSILON);
        assertEquals(CharacterCreatorShot.FULL_BACK.getFocus(), pose.getShownFocus(), EPSILON);
        pose.wheel(1);
        // A frame later, not a nanosecond later: a step that short eases nothing.
        pose.advance(1L + 16000000L);
        assertTrue(pose.getShownZoom() > CharacterCreatorShot.FULL_BACK.getZoom());
        // Letting go keeps the page's shot and drops only the player's part.
        pose.reset();
        pose.advance(3L);
        assertEquals(180.0F, Math.abs(pose.getShownYaw()), EPSILON);
        assertEquals(CharacterCreatorShot.FULL_BACK.getZoom(), pose.getShownZoom(), EPSILON);
    }

    @Test
    public void thePanIsBoundedAndLetGoByReset() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.pan(10.0F, -5.0F);
        settle(pose);
        assertEquals(10.0F, pose.getShownPanX(), EPSILON);
        assertEquals(-5.0F, pose.getShownPanY(), EPSILON);
        pose.pan(10000.0F, 10000.0F);
        settle(pose);
        assertEquals(CharacterStagePose.PAN_LIMIT, pose.getShownPanX(), EPSILON);
        assertEquals(CharacterStagePose.PAN_LIMIT, pose.getShownPanY(), EPSILON);
        pose.reset();
        settle(pose);
        assertEquals(0.0F, pose.getShownPanX(), EPSILON);
    }

    @Test
    public void theFollowEasesOutInsteadOfSnapping() {
        CharacterStagePose pose = new CharacterStagePose();
        float previous = Float.MAX_VALUE;
        // Turning the figure further and further from the pointer, the
        // head's turn shrinks step by step and never jumps back up.
        for (float turn = CharacterStagePose.HEAD_FOLLOW_LIMIT; turn <= 130.0F; turn += 5.0F) {
            CharacterStagePose turned = new CharacterStagePose();
            turned.drag(turn / CharacterStagePose.DEGREES_PER_DRAG_PIXEL, 0.0F);
            turned.advance(1L);
            float follow = Math.abs(turned.headYawToward(0.0F));
            assertTrue("turn " + turn, follow <= previous + EPSILON);
            previous = follow;
        }
        assertEquals(0.0F, previous, EPSILON);
    }
}
