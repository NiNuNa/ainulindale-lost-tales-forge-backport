package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class DirectionalMovementMathTest {
    @Test
    public void cardinalInputsFaceRelativeToCamera() {
        assertEquals(30.0F, DirectionalMovementMath.resolveMovementYaw(
                30.0F, 1.0F, 0.0F), 0.0F);
        assertEquals(-60.0F, DirectionalMovementMath.resolveMovementYaw(
                30.0F, 0.0F, 1.0F), 0.0F);
        assertEquals(120.0F, DirectionalMovementMath.resolveMovementYaw(
                30.0F, 0.0F, -1.0F), 0.0F);
        assertEquals(-150.0F, DirectionalMovementMath.resolveMovementYaw(
                30.0F, -1.0F, 0.0F), 0.0F);
    }

    @Test
    public void diagonalInputUsesFullThreeHundredSixtyDegreeFacing() {
        assertEquals(-45.0F, DirectionalMovementMath.resolveMovementYaw(
                0.0F, 1.0F, 1.0F), 0.0001F);
        assertEquals(135.0F, DirectionalMovementMath.resolveMovementYaw(
                0.0F, -1.0F, -1.0F), 0.0001F);
    }

    @Test
    public void smoothTurnUsesShortestWrappedPath() {
        assertEquals(-175.0F, DirectionalMovementMath.approachDegrees(
                175.0F, -160.0F, 10.0F), 0.0F);
        assertEquals(175.0F, DirectionalMovementMath.approachDegrees(
                -175.0F, 160.0F, 10.0F), 0.0F);
        assertEquals(-160.0F, DirectionalMovementMath.approachDegrees(
                -165.0F, -160.0F, 10.0F), 0.0F);
    }

    @Test
    public void headTrackingFlipsDirectionAtTheShoulderLimit() {
        assertEquals(80.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, 80.0F, 80.0F), 0.0F);
        assertEquals(-80.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, 81.0F, 80.0F), 0.0F);
        assertEquals(-110.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        170.0F, -110.0F, 80.0F), 0.0F);
        assertEquals(90.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        170.0F, -109.0F, 80.0F), 0.0F);
    }

    @Test
    public void reverseTrackingHasNoBodyForwardDeadZone() {
        assertEquals(-80.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, 81.0F, 80.0F), 0.0F);
        assertEquals(-80.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, 100.0F, 80.0F), 0.0F);
        assertEquals(-45.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, 135.0F, 80.0F), 0.0001F);
        assertEquals(80.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, -81.0F, 80.0F), 0.0F);
        assertEquals(45.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, -135.0F, 80.0F), 0.0001F);
        assertEquals(0.0F,
                DirectionalMovementMath.resolveHeadTrackingYaw(
                        0.0F, 180.0F, 80.0F), 0.0001F);
    }

    @Test
    public void reverseTrackingCorrectsPitchWithoutChangingInput() {
        assertEquals(-30.0F,
                DirectionalMovementMath.resolveHeadTrackingPitch(
                        30.0F, true), 0.0F);
        assertEquals(30.0F,
                DirectionalMovementMath.resolveHeadTrackingPitch(
                        -30.0F, true), 0.0F);
        assertEquals(-30.0F,
                DirectionalMovementMath.resolveHeadTrackingPitch(
                        -30.0F, false), 0.0F);
    }

    @Test
    public void crossShoulderTurnAdvancesInBoundedSteps() {
        assertEquals(44.0F,
                DirectionalMovementMath.approachDegrees(
                        80.0F, -80.0F, 36.0F), 0.0F);
        assertEquals(8.0F,
                DirectionalMovementMath.approachDegrees(
                        44.0F, -80.0F, 36.0F), 0.0F);
        assertEquals(14.0F,
                DirectionalMovementMath.approachValue(
                        -22.0F, 30.0F, 36.0F), 0.0F);
    }
}
