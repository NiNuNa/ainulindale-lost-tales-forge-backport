package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesLotrMapRotationTest {
    private static final float CENTER_X = 320.0F;
    private static final float CENTER_Y = 180.0F;

    @Test
    public void rotationIsClampedToItsOwnBounds() {
        assertEquals(LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.clampDegrees(120.0F), 0.0F);
        assertEquals(-LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.clampDegrees(-120.0F), 0.0F);
        assertEquals(20.0F,
                LostTalesLotrMapRotation.clampDegrees(20.0F), 0.0F);
        // A malformed angle must not lock the map at an angle it cannot leave.
        assertEquals(0.0F,
                LostTalesLotrMapRotation.clampDegrees(Float.NaN), 0.0F);
    }

    /** Square is a detent: a small drag either side of it turns nothing. */
    @Test
    public void trueNorthHoldsAgainstASmallDrag() {
        assertEquals(0.0F,
                LostTalesLotrMapRotation.degreesForInput(0.0F), 0.0F);
        assertEquals(0.0F, LostTalesLotrMapRotation.degreesForInput(
                LostTalesLotrMapRotation.SNAP_INPUT_SHARE * 0.5F), 0.0F);
        assertEquals(0.0F, LostTalesLotrMapRotation.degreesForInput(
                -LostTalesLotrMapRotation.SNAP_INPUT_SHARE * 0.5F), 0.0F);
        // And past it the map turns, from zero rather than with a jump.
        float justPast = LostTalesLotrMapRotation.degreesForInput(
                LostTalesLotrMapRotation.SNAP_INPUT_SHARE + 0.01F);
        assertTrue(justPast > 0.0F);
        assertTrue("leaving the detent must not snap to an angle",
                justPast < 5.0F);
    }

    @Test
    public void aFullDragTurnsTheMapAllTheWayAndNoFurther() {
        assertEquals(LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(1.0F), 0.001F);
        assertEquals(-LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(-1.0F), 0.001F);
        assertEquals(LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(4.0F), 0.001F);
    }

    /**
     * The resistance the turn is meant to have: the same amount of drag buys
     * fewer and fewer degrees the further the map is already turned.
     */
    @Test
    public void turningGetsHarderTheFurtherItGoes() {
        float step = 0.05F;
        float previousGain = Float.MAX_VALUE;
        for (float input = LostTalesLotrMapRotation.SNAP_INPUT_SHARE;
             input + step <= 1.0F; input += step) {
            float gain = LostTalesLotrMapRotation.degreesForInput(
                    input + step)
                    - LostTalesLotrMapRotation.degreesForInput(input);
            assertTrue("the map turned backwards", gain >= 0.0F);
            assertTrue("the turn got easier instead of harder",
                    gain <= previousGain + 0.0001F);
            previousGain = gain;
        }
        assertTrue("the last degrees must cost far more drag than the first",
                previousGain < 1.0F);
    }

    @Test
    public void theDrawnAngleFollowsTheDraggedOneAndSettlesOnIt() {
        float drawn = 0.0F;
        for (int frame = 0; frame < 60; frame++) {
            drawn = LostTalesLotrMapRotation.approachDegrees(
                    drawn, 30.0F, 1.0F / 60.0F);
            assertTrue("the drawn angle overshot", drawn <= 30.0F);
        }
        assertEquals(30.0F, drawn, 0.0001F);

        // Frame rate must not change how fast it gets there.
        float slow = LostTalesLotrMapRotation.approachDegrees(
                0.0F, 30.0F, 0.1F);
        float stepped = 0.0F;
        for (int frame = 0; frame < 10; frame++) {
            stepped = LostTalesLotrMapRotation.approachDegrees(
                    stepped, 30.0F, 0.01F);
        }
        assertEquals(slow, stepped, 0.05F);
    }

    /**
     * The lean: the near edge spreads out, the far edge draws in, and the
     * middle of the screen — where the eye is aimed — does not move.
     */
    @Test
    public void leaningSpreadsTheNearEdgeAndDrawsInTheFarOne() {
        float coefficient = LostTalesLotrMapRotation.leanCoefficient(
                1.0F, 360.0F);
        assertTrue("leaning must divide, not multiply", coefficient < 0.0F);

        float[] centre = { CENTER_X, CENTER_Y };
        LostTalesLotrMapRotation.applyLean(
                centre, CENTER_X, CENTER_Y, coefficient);
        assertEquals(CENTER_X, centre[0], 0.0001F);
        assertEquals(CENTER_Y, centre[1], 0.0001F);

        float[] near = { CENTER_X + 100.0F, CENTER_Y + 180.0F };
        LostTalesLotrMapRotation.applyLean(
                near, CENTER_X, CENTER_Y, coefficient);
        assertTrue("the near corner must spread outward",
                near[0] > CENTER_X + 100.0F);
        assertTrue(near[1] > CENTER_Y + 180.0F);

        float[] far = { CENTER_X + 100.0F, CENTER_Y - 180.0F };
        LostTalesLotrMapRotation.applyLean(
                far, CENTER_X, CENTER_Y, coefficient);
        assertTrue("the far corner must draw inward",
                far[0] < CENTER_X + 100.0F);
        assertTrue(far[1] > CENTER_Y - 180.0F);
    }

    @Test
    public void aLeanIsExactlyUndoable() {
        for (float lean = 0.0F; lean <= 1.0F; lean += 0.25F) {
            float coefficient = LostTalesLotrMapRotation.leanCoefficient(
                    lean, 360.0F);
            for (float offset = -170.0F; offset <= 170.0F; offset += 85.0F) {
                float[] point = { CENTER_X + 60.0F, CENTER_Y + offset };
                LostTalesLotrMapRotation.applyLean(
                        point, CENTER_X, CENTER_Y, coefficient);
                LostTalesLotrMapRotation.removeLean(
                        point, CENTER_X, CENTER_Y, coefficient);

                assertEquals(CENTER_X + 60.0F, point[0], 0.01F);
                assertEquals(CENTER_Y + offset, point[1], 0.01F);
            }
        }
    }

    @Test
    public void aFlatMapIsLeftAloneAndCutToItsOwnSize() {
        float[] point = { 12.0F, 34.0F };
        LostTalesLotrMapRotation.applyLean(
                point, CENTER_X, CENTER_Y,
                LostTalesLotrMapRotation.leanCoefficient(0.0F, 360.0F));

        assertEquals(12.0F, point[0], 0.0F);
        assertEquals(34.0F, point[1], 0.0F);
        assertEquals(1.0F,
                LostTalesLotrMapRotation.leanCoverage(0.0F), 0.0F);
        assertTrue(LostTalesLotrMapRotation.leanCoverage(1.0F) > 1.0F);
    }

    @Test
    public void theCentreOfTheMapIsWhatTheMapTurnsAbout() {
        float[] point = { CENTER_X, CENTER_Y };
        LostTalesLotrMapRotation.rotateAbout(
                point, CENTER_X, CENTER_Y, 45.0F);

        assertEquals(CENTER_X, point[0], 0.0001F);
        assertEquals(CENTER_Y, point[1], 0.0001F);
    }

    @Test
    public void aQuarterTurnMovesAPointExactlyWhereItShould() {
        float[] point = { CENTER_X + 100.0F, CENTER_Y };
        LostTalesLotrMapRotation.rotateAbout(
                point, CENTER_X, CENTER_Y, 90.0F);

        // Screen space runs downward, so a positive angle carries a point on
        // the right down to below the centre.
        assertEquals(CENTER_X, point[0], 0.001F);
        assertEquals(CENTER_Y + 100.0F, point[1], 0.001F);
    }

    @Test
    public void turningBackByTheSameAngleReturnsTheOriginalPoint() {
        for (float degrees = -LostTalesLotrMapRotation.MAX_DEGREES;
             degrees <= LostTalesLotrMapRotation.MAX_DEGREES;
             degrees += 7.5F) {
            float[] point = { CENTER_X + 137.0F, CENTER_Y - 62.0F };
            LostTalesLotrMapRotation.rotateAbout(
                    point, CENTER_X, CENTER_Y, degrees);
            LostTalesLotrMapRotation.rotateAbout(
                    point, CENTER_X, CENTER_Y, -degrees);

            assertEquals(CENTER_X + 137.0F, point[0], 0.001F);
            assertEquals(CENTER_Y - 62.0F, point[1], 0.001F);
        }
    }

    @Test
    public void rotationPreservesDistanceFromTheCentre() {
        float[] point = { CENTER_X + 60.0F, CENTER_Y + 80.0F };
        LostTalesLotrMapRotation.rotateAbout(
                point, CENTER_X, CENTER_Y, 33.0F);

        float deltaX = point[0] - CENTER_X;
        float deltaY = point[1] - CENTER_Y;
        assertEquals(100.0F,
                (float)Math.sqrt(deltaX * deltaX + deltaY * deltaY),
                0.001F);
    }

    /**
     * A drag that follows the pointer on a square map has to follow it on a
     * turned one too, which means undoing the turn on the movement itself.
     */
    @Test
    public void aCameraMovementIsTurnedBackOntoTheMapsOwnAxes() {
        float[] delta = { 10.0F, 0.0F };
        LostTalesLotrMapRotation.rotateCameraDelta(delta, 90.0F);

        assertEquals(0.0F, delta[0], 0.001F);
        assertEquals(-10.0F, delta[1], 0.001F);

        // Untouched while the map is square.
        float[] square = { 10.0F, -4.0F };
        LostTalesLotrMapRotation.rotateCameraDelta(square, 0.0F);
        assertEquals(10.0F, square[0], 0.0F);
        assertEquals(-4.0F, square[1], 0.0F);
    }

    @Test
    public void aFlatSquareMapIsCutToTheViewportExactly() {
        float[] coverage = new float[2];
        LostTalesLotrMapRotation.rotatedCoverage(
                640.0F, 360.0F, 0.0F, 0.0F, coverage);

        assertEquals(640.0F, coverage[0], 0.001F);
        assertEquals(360.0F, coverage[1], 0.001F);
    }

    /**
     * The sheet keeps one shape whatever the angle. Cutting it to fit each
     * angle in turn changed its proportions as it went round, and the paper
     * grain printed on it stretched and settled along with them.
     */
    @Test
    public void aTurnedSheetIsAlwaysTheSameShape() {
        float[] coverage = new float[2];
        float[] previous = null;
        for (float degrees = -22.5F; degrees <= 22.5F; degrees += 2.5F) {
            if (Math.abs(degrees) < 0.001F) {
                continue;
            }
            LostTalesLotrMapRotation.rotatedCoverage(
                    640.0F, 360.0F, degrees, 0.0F, coverage);

            assertEquals("the sheet must stay square",
                    coverage[0], coverage[1], 0.001F);
            assertTrue("it must reach past the viewport's corners",
                    coverage[0] >= 640.0F);
            if (previous != null) {
                assertEquals("its size must not change with the angle",
                        previous[0], coverage[0], 0.001F);
            }
            previous = new float[] { coverage[0], coverage[1] };
        }
    }

    @Test
    public void aLeaningSheetIsCutWiderStill() {
        float[] flat = new float[2];
        float[] leaned = new float[2];
        LostTalesLotrMapRotation.rotatedCoverage(
                640.0F, 360.0F, 10.0F, 0.0F, flat);
        LostTalesLotrMapRotation.rotatedCoverage(
                640.0F, 360.0F, 10.0F, 1.0F, leaned);

        assertTrue(leaned[0] > flat[0]);
        assertEquals(leaned[0], leaned[1], 0.001F);
    }

    /**
     * Leaning and turning are the same gesture in two directions, so they
     * cost the player the same movement and stiffen the same way.
     */
    @Test
    public void leaningCostsTheSameDragAsTurningAndStiffensAlike() {
        assertEquals(LostTalesLotrMapRotation.FULL_TURN_DRAG_PIXELS,
                LostTalesLotrMapRotation.FULL_LEAN_DRAG_PIXELS, 0.0F);
        assertEquals(0.0F,
                LostTalesLotrMapRotation.leanForInput(
                        LostTalesLotrMapRotation.SNAP_INPUT_SHARE * 0.5F),
                0.0F);
        assertEquals(1.0F,
                LostTalesLotrMapRotation.leanForInput(1.0F), 0.001F);

        // The same share of the drag buys the same share of the movement.
        for (float input = 0.1F; input <= 1.0F; input += 0.1F) {
            assertEquals(
                    LostTalesLotrMapRotation.degreesForInput(input)
                            / LostTalesLotrMapRotation.MAX_DEGREES,
                    LostTalesLotrMapRotation.leanForInput(input), 0.001F);
        }
    }

    /** The complaint that started it: the stiffening has to be felt. */
    @Test
    public void theLastDegreesCostFarMoreDragThanTheFirst() {
        float first = dragFor(2.0F) - dragFor(0.0F);
        float last = dragFor(LostTalesLotrMapRotation.MAX_DEGREES)
                - dragFor(LostTalesLotrMapRotation.MAX_DEGREES - 2.0F);

        assertTrue("the last degrees must cost several times the first",
                last > first * 5.0F);
    }

    private static float dragFor(float degrees) {
        float low = 0.0F;
        float high = 1.0F;
        for (int step = 0; step < 60; step++) {
            float middle = (low + high) * 0.5F;
            if (LostTalesLotrMapRotation.degreesForInput(middle) < degrees) {
                low = middle;
            } else {
                high = middle;
            }
        }
        return high * LostTalesLotrMapRotation.FULL_TURN_DRAG_PIXELS;
    }

    @Test
    public void anUnmeasuredMapImageDoesNotPinTheCamera() {
        // Before the map image is measured its size reads as nothing, and
        // clamping to that would pin the camera into a corner.
        assertEquals(812.5F,
                LostTalesLotrMapRotation.clampToMapImage(812.5F, 0), 0.0F);
        assertEquals(-40.0F,
                LostTalesLotrMapRotation.clampToMapImage(-40.0F, 0), 0.0F);
    }

    @Test
    public void theCameraStaysOnTheMapImage() {
        assertEquals(0.0F,
                LostTalesLotrMapRotation.clampToMapImage(-40.0F, 1600), 0.0F);
        assertEquals(1600.0F,
                LostTalesLotrMapRotation.clampToMapImage(9000.0F, 1600),
                0.0F);
        assertEquals(812.5F,
                LostTalesLotrMapRotation.clampToMapImage(812.5F, 1600), 0.0F);
    }
}
