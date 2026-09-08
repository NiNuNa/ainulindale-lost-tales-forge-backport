package com.ninuna.losttales.gui.screen.character.creator;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** How the stage answers the mouse, and where the head stops following it. */
public class CharacterStagePoseTest {

    private static final float EPSILON = 0.001F;

    @Test
    public void startsSquareToTheScreen() {
        CharacterStagePose pose = new CharacterStagePose();
        assertEquals(0.0F, pose.getYaw(), EPSILON);
        assertEquals(0.0F, pose.getPitch(), EPSILON);
        assertEquals(1.0F, pose.getZoom(), EPSILON);
    }

    @Test
    public void draggingRightTurnsTheFrontToTheRight() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(10, 0);
        assertTrue(pose.getYaw() > 0.0F);
        pose.drag(-20, 0);
        assertTrue(pose.getYaw() < 0.0F);
    }

    @Test
    public void draggingDownLeansTheHeadTowardTheViewerWithinItsLimit() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(0, 10);
        assertTrue(pose.getPitch() > 0.0F);
        pose.drag(0, 10000);
        assertEquals(CharacterStagePose.PITCH_LIMIT, pose.getPitch(), EPSILON);
        pose.drag(0, -20000);
        assertEquals(-CharacterStagePose.PITCH_LIMIT, pose.getPitch(), EPSILON);
    }

    @Test
    public void theYawWrapsRatherThanGrowingWithoutEnd() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(100000, 0);
        assertTrue(pose.getYaw() >= -180.0F && pose.getYaw() < 180.0F);
        assertEquals(-180.0F, CharacterStagePose.wrap(180.0F), EPSILON);
        assertEquals(10.0F, CharacterStagePose.wrap(370.0F), EPSILON);
        assertEquals(-10.0F, CharacterStagePose.wrap(-370.0F), EPSILON);
    }

    @Test
    public void theWheelZoomsWithinItsLimitsAndForwardBringsNearer() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.wheel(1);
        assertTrue(pose.getZoom() > 1.0F);
        pose.wheel(100);
        assertEquals(CharacterStagePose.ZOOM_MAX, pose.getZoom(), EPSILON);
        pose.wheel(-200);
        assertEquals(CharacterStagePose.ZOOM_MIN, pose.getZoom(), EPSILON);
        pose.wheel(0);
        assertEquals(CharacterStagePose.ZOOM_MIN, pose.getZoom(), EPSILON);
    }

    @Test
    public void resetStandsItSquareAgain() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(30, 30);
        pose.wheel(3);
        pose.reset();
        assertEquals(0.0F, pose.getYaw(), EPSILON);
        assertEquals(0.0F, pose.getPitch(), EPSILON);
        assertEquals(1.0F, pose.getZoom(), EPSILON);
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
        assertTrue(pose.getShownYaw() < pose.getYaw());
        assertTrue(pose.getShownZoom() > 1.0F);
        assertTrue(pose.getShownZoom() < pose.getZoom());
        // A second later it is there.
        for (int frame = 1; frame <= 60; frame++) {
            pose.advance(now + 16000000L + frame * 16000000L);
        }
        assertEquals(pose.getYaw(), pose.getShownYaw(), 0.01F);
        assertEquals(pose.getZoom(), pose.getShownZoom(), 0.001F);
    }

    @Test
    public void theFirstFrameAndAResetShowTheAskedPoseAtOnce() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.drag(40, 10);
        pose.advance(5000000000L);
        assertEquals(pose.getYaw(), pose.getShownYaw(), EPSILON);
        assertEquals(pose.getPitch(), pose.getShownPitch(), EPSILON);
        pose.reset();
        pose.advance(6000000000L);
        assertEquals(0.0F, pose.getShownYaw(), EPSILON);
        assertEquals(1.0F, pose.getShownZoom(), EPSILON);
    }

    @Test
    public void theEaseTakesTheShortWayRoundTheCircle() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.turn(170.0F);
        pose.advance(1000000000L);
        pose.turn(30.0F);
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
        pose.turn(180.0F);
        // The head follows what is drawn, so the turn has to be shown first.
        pose.advance(1L);
        assertEquals(0.0F, pose.headYawToward(40.0F), EPSILON);
        assertEquals(0.0F, pose.headPitchToward(40.0F), EPSILON);
    }

    @Test
    public void thePageShotAndThePlayersTurnAddUp() {
        CharacterStagePose pose = new CharacterStagePose();
        pose.setShot(CharacterCreatorShot.FULL_BACK);
        pose.turn(30.0F);
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
        assertEquals(10.0F, pose.getPanX(), EPSILON);
        assertEquals(-5.0F, pose.getPanY(), EPSILON);
        pose.pan(10000.0F, 10000.0F);
        assertEquals(CharacterStagePose.PAN_LIMIT, pose.getPanX(), EPSILON);
        assertEquals(CharacterStagePose.PAN_LIMIT, pose.getPanY(), EPSILON);
        pose.reset();
        assertEquals(0.0F, pose.getPanX(), EPSILON);
    }

    @Test
    public void theFollowEasesOutInsteadOfSnapping() {
        CharacterStagePose pose = new CharacterStagePose();
        float previous = Float.MAX_VALUE;
        // Turning the figure further and further from the pointer, the
        // head's turn shrinks step by step and never jumps back up.
        for (float turn = CharacterStagePose.HEAD_FOLLOW_LIMIT; turn <= 130.0F; turn += 5.0F) {
            CharacterStagePose turned = new CharacterStagePose();
            turned.turn(turn);
            turned.advance(1L);
            float follow = Math.abs(turned.headYawToward(0.0F));
            assertTrue("turn " + turn, follow <= previous + EPSILON);
            previous = follow;
        }
        assertEquals(0.0F, previous, EPSILON);
    }
}
