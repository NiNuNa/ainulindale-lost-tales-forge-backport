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
    public void trueNorthIsSettledOnRatherThanHeld() {
        assertEquals(0.0F,
                LostTalesLotrMapRotation.degreesForInput(0.0F), 0.0F);
        // All but square is drawn square, so the map has somewhere to come
        // to rest instead of stopping a fraction of a degree off it.
        assertEquals(0.0F, LostTalesLotrMapRotation.magnetiseDegrees(
                LostTalesLotrMapRotation.SETTLE_DEGREES * 0.5F), 0.0F);
        assertEquals(0.0F, LostTalesLotrMapRotation.magnetiseDegrees(
                -LostTalesLotrMapRotation.SETTLE_DEGREES * 0.5F), 0.0F);
        // Past the magnet the drag is honoured exactly: no zone the player
        // has to fight their way out of.
        float past = LostTalesLotrMapRotation.MAGNET_DEGREES + 3.0F;
        assertEquals(past,
                LostTalesLotrMapRotation.magnetiseDegrees(past), 0.0F);
        assertEquals(-past,
                LostTalesLotrMapRotation.magnetiseDegrees(-past), 0.0F);
    }

    /**
     * The magnet must not be a wall. Whatever the player does inside the zone
     * has to show on screen, and both of its edges have to be seamless, or
     * the map either sticks at north or jumps as it passes.
     */
    @Test
    public void theMagnetNeverStopsTheMapMoving() {
        assertEquals("the magnet must hand over exactly where it ends",
                LostTalesLotrMapRotation.MAGNET_DEGREES,
                LostTalesLotrMapRotation.magnetiseDegrees(
                        LostTalesLotrMapRotation.MAGNET_DEGREES), 0.001F);
        float previous = -1.0F;
        for (float degrees = LostTalesLotrMapRotation.SETTLE_DEGREES;
             degrees <= LostTalesLotrMapRotation.MAGNET_DEGREES;
             degrees += 0.02F) {
            float settled =
                    LostTalesLotrMapRotation.magnetiseDegrees(degrees);
            assertTrue("the magnet turned the map backwards",
                    settled >= previous);
            assertTrue("the magnet must pull towards north, not past it",
                    settled <= degrees + 0.001F);
            previous = settled;
        }
        assertTrue("the map must move at all inside the magnet",
                previous > 0.0F);
    }

    /**
     * The whole point of shrinking the detent: a drag that would have been
     * swallowed now turns the map.
     */
    @Test
    public void aShortDragOffSquareTurnsTheMap() {
        float shortDrag = 20.0F * LostTalesLotrMapRotation.inputPerPixel();
        assertTrue(LostTalesLotrMapRotation.degreesForInput(shortDrag)
                > 0.0F);
        assertTrue(LostTalesLotrMapRotation.degreesForInput(-shortDrag)
                < 0.0F);
    }

    @Test
    public void theNormalLimitAndElasticLimitAreDistinct() {
        assertEquals(LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(1.0F), 0.001F);
        assertEquals(-LostTalesLotrMapRotation.MAX_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(-1.0F), 0.001F);
        assertTrue(LostTalesLotrMapRotation.degreesForInput(1.1F)
                > LostTalesLotrMapRotation.MAX_DEGREES);
        assertEquals(LostTalesLotrMapRotation.MAX_VISUAL_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(4.0F), 0.001F);
        assertEquals(-LostTalesLotrMapRotation.MAX_VISUAL_DEGREES,
                LostTalesLotrMapRotation.degreesForInput(-4.0F), 0.001F);
    }

    /**
     * The resistance the turn is meant to have: the same amount of drag buys
     * fewer and fewer degrees the further the map is already turned.
     */
    @Test
    public void turningGetsHarderTheFurtherItGoes() {
        float step = 0.05F;
        float previousGain = Float.MAX_VALUE;
        // From clear of the magnet, which bends the first fraction of a
        // degree on purpose and is measured by its own tests.
        for (float input = 0.05F;
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
        float scaleY = LostTalesLotrMapRotation.leanScaleY(1.0F);
        assertTrue("leaning must divide, not multiply", coefficient < 0.0F);

        float[] centre = { CENTER_X, CENTER_Y };
        LostTalesLotrMapRotation.applyLean(
                centre, CENTER_X, CENTER_Y, coefficient, scaleY);
        assertEquals(CENTER_X, centre[0], 0.0001F);
        assertEquals(CENTER_Y, centre[1], 0.0001F);

        float[] near = { CENTER_X + 100.0F, CENTER_Y + 180.0F };
        LostTalesLotrMapRotation.applyLean(
                near, CENTER_X, CENTER_Y, coefficient, scaleY);
        assertTrue("the near corner must spread outward",
                near[0] > CENTER_X + 100.0F);
        assertTrue(near[1] > CENTER_Y + 180.0F);

        float[] far = { CENTER_X + 100.0F, CENTER_Y - 180.0F };
        LostTalesLotrMapRotation.applyLean(
                far, CENTER_X, CENTER_Y, coefficient, scaleY);
        assertTrue("the far corner must draw inward",
                far[0] < CENTER_X + 100.0F);
        assertTrue(far[1] > CENTER_Y - 180.0F);
    }

    /**
     * The half of the tilt that was missing: a leaning sheet has to lie down
     * as well as keystone, or it reads as warped paper rather than as a
     * surface being looked at from an angle.
     */
    @Test
    public void leaningAlsoLaysTheSheetDown() {
        assertEquals("a flat map is not foreshortened at all", 1.0F,
                LostTalesLotrMapRotation.leanScaleY(0.0F), 0.0F);
        float previous = 1.0F;
        for (float lean = 0.1F; lean <= 1.0F; lean += 0.1F) {
            float scaleY = LostTalesLotrMapRotation.leanScaleY(lean);
            assertTrue("the sheet must recede as the eye drops",
                    scaleY < previous);
            assertTrue(scaleY > 0.0F);
            previous = scaleY;
        }
        assertEquals((float)Math.cos(Math.toRadians(
                        LostTalesLotrMapRotation.MAX_PITCH_DEGREES)),
                LostTalesLotrMapRotation.leanScaleY(1.0F), 0.0001F);
    }

    @Test
    public void aLeanIsExactlyUndoable() {
        for (float lean = 0.0F; lean <= 1.0F; lean += 0.25F) {
            float coefficient = LostTalesLotrMapRotation.leanCoefficient(
                    lean, 360.0F);
            float scaleY = LostTalesLotrMapRotation.leanScaleY(lean);
            for (float offset = -170.0F; offset <= 170.0F; offset += 85.0F) {
                float[] point = { CENTER_X + 60.0F, CENTER_Y + offset };
                LostTalesLotrMapRotation.applyLean(
                        point, CENTER_X, CENTER_Y, coefficient, scaleY);
                LostTalesLotrMapRotation.removeLean(
                        point, CENTER_X, CENTER_Y, coefficient, scaleY);

                assertEquals(CENTER_X + 60.0F, point[0], 0.01F);
                assertEquals(CENTER_Y + offset, point[1], 0.01F);
            }
        }
    }

    /**
     * What the player actually does: turn and lean at once, then click. The
     * pointer has to resolve to the ground that was drawn under it, or every
     * marker on a tilted map is a near miss.
     */
    @Test
    public void turningAndLeaningTogetherStaysExactlyReversible() {
        for (float degrees = -LostTalesLotrMapRotation.MAX_DEGREES;
             degrees <= LostTalesLotrMapRotation.MAX_DEGREES;
             degrees += 11.25F) {
            for (float lean = 0.0F; lean <= 1.0F; lean += 0.5F) {
                float coefficient = LostTalesLotrMapRotation.leanCoefficient(
                        lean, 360.0F);
                float scaleY = LostTalesLotrMapRotation.leanScaleY(lean);
                for (float offsetX = -280.0F; offsetX <= 280.0F;
                     offsetX += 140.0F) {
                    for (float offsetY = -160.0F; offsetY <= 160.0F;
                         offsetY += 80.0F) {
                        float[] point = {
                                CENTER_X + offsetX, CENTER_Y + offsetY
                        };
                        LostTalesLotrMapRotation.rotateAbout(
                                point, CENTER_X, CENTER_Y, degrees);
                        LostTalesLotrMapRotation.applyLean(
                                point, CENTER_X, CENTER_Y,
                                coefficient, scaleY);
                        LostTalesLotrMapRotation.removeLean(
                                point, CENTER_X, CENTER_Y,
                                coefficient, scaleY);
                        LostTalesLotrMapRotation.rotateAbout(
                                point, CENTER_X, CENTER_Y, -degrees);

                        assertEquals(CENTER_X + offsetX, point[0], 0.05F);
                        assertEquals(CENTER_Y + offsetY, point[1], 0.05F);
                    }
                }
            }
        }
    }

    @Test
    public void aFlatMapIsLeftAloneAndCutToItsOwnSize() {
        float[] point = { 12.0F, 34.0F };
        LostTalesLotrMapRotation.applyLean(
                point, CENTER_X, CENTER_Y,
                LostTalesLotrMapRotation.leanCoefficient(0.0F, 360.0F),
                LostTalesLotrMapRotation.leanScaleY(0.0F));

        assertEquals(12.0F, point[0], 0.0F);
        assertEquals(34.0F, point[1], 0.0F);
        assertEquals(1.0F,
                LostTalesLotrMapRotation.leanCoverage(0.0F), 0.0F);
        assertTrue(LostTalesLotrMapRotation.leanCoverage(1.0F) > 1.0F);
    }

    /**
     * The complaint this was written for: an axis near its limit that is
     * dragged back towards square has to come back at once. The stiffness is
     * there to make the limit feel deliberate, not to make leaving it a
     * chore.
     */
    @Test
    public void reversingATurnIsNotResistedByTheLimit() {
        float step = LostTalesLotrMapRotation.inputPerPixel();
        float atSquare = degreesMoved(0.0F, step);
        float outwardWayOut = degreesMoved(0.5F, step);
        float inwardWayOut = degreesMoved(0.5F, -step);

        assertTrue("the map must still stiffen on the way out",
                outwardWayOut < atSquare * 0.5F);
        assertEquals("coming back must move as freely as leaving square did",
                atSquare, inwardWayOut, atSquare * 0.05F);
        assertTrue("reversal must be the immediate direction",
                inwardWayOut > outwardWayOut * 3.0F);
    }

    /**
     * The release is a release, not a slingshot: however far out the map is,
     * one pixel of drag back towards square may never unwind more than the
     * whole gesture would.
     */
    @Test
    public void theReleaseIsBoundedRightUpToTheLimit() {
        float step = LostTalesLotrMapRotation.inputPerPixel();
        float atSquare = degreesMoved(0.0F, step);
        for (float input = 0.1F; input <= 1.0F; input += 0.1F) {
            float inward = degreesMoved(input, -step);
            assertTrue("the release outran the map at " + input,
                    inward <= atSquare * 1.1F);
            assertTrue("the release stalled at " + input, inward > 0.0F);
        }
    }

    /** How far one drag actually turns the map, in degrees, before easing. */
    private static float degreesMoved(float input, float delta) {
        return Math.abs(
                LostTalesLotrMapRotation.MAX_DEGREES
                        * (LostTalesLotrMapRotation.resist(
                                Math.abs(LostTalesLotrMapRotation
                                        .advanceInput(input, delta)))
                        - LostTalesLotrMapRotation.resist(
                                Math.abs(input))));
    }

    /** The calibrated curve is unchanged until the elastic range begins. */
    @Test
    public void draggingAwayFromSquareIsUnchanged() {
        float step = 0.05F;
        float accumulated = 0.0F;
        for (int taken = 1; taken <= 20; taken++) {
            accumulated = LostTalesLotrMapRotation.advanceInput(
                    accumulated, step);
            assertEquals(taken * step, accumulated, 0.0001F);
        }
        assertEquals(1.05F,
                LostTalesLotrMapRotation.advanceInput(1.0F, step), 0.0001F);
        assertEquals("elastic input must remain strictly bounded",
                LostTalesLotrMapRotation.MAX_INPUT,
                LostTalesLotrMapRotation.advanceInput(
                        LostTalesLotrMapRotation.MAX_INPUT, step), 0.0F);
    }

    @Test
    public void elasticResistanceRisesTowardsTheVisualLimit() {
        float first = LostTalesLotrMapRotation.degreesForInput(1.06F)
                - LostTalesLotrMapRotation.degreesForInput(1.0F);
        float last = LostTalesLotrMapRotation.degreesForInput(
                LostTalesLotrMapRotation.MAX_INPUT)
                - LostTalesLotrMapRotation.degreesForInput(
                        LostTalesLotrMapRotation.MAX_INPUT - 0.06F);

        assertTrue(first > 0.0F);
        assertTrue("overshoot must get harder towards its bound",
                last < first);
    }

    @Test
    public void normalLimitFlowsDirectlyIntoElasticOvershoot() {
        float step = 0.01F;
        float before = LostTalesLotrMapRotation.degreesForInput(1.0F)
                - LostTalesLotrMapRotation.degreesForInput(1.0F - step);
        float after = LostTalesLotrMapRotation.degreesForInput(1.0F + step)
                - LostTalesLotrMapRotation.degreesForInput(1.0F);

        assertTrue("movement stopped at the normal limit", after > 0.0F);
        assertEquals("the resistance curve changed at the normal limit",
                before, after, before * 0.03F);
        assertTrue("resistance must keep rising through overshoot",
                after < before);
    }

    @Test
    public void releasedOvershootReturnsSmoothlyAndFrameIndependently() {
        float start = LostTalesLotrMapRotation.MAX_INPUT;
        float oneStep = LostTalesLotrMapRotation.releaseOvershootInput(
                start, 0.1F);
        float manySteps = start;
        for (int step = 0; step < 10; step++) {
            manySteps = LostTalesLotrMapRotation.releaseOvershootInput(
                    manySteps, 0.01F);
        }

        assertTrue(oneStep > LostTalesLotrMapRotation.NORMAL_INPUT_LIMIT);
        assertTrue(oneStep < start);
        assertEquals(oneStep, manySteps, 0.0001F);
        assertEquals(LostTalesLotrMapRotation.NORMAL_INPUT_LIMIT,
                LostTalesLotrMapRotation.releasedInput(start), 0.0F);
    }

    /** A reversal that overshoots square keeps paying full price after it. */
    @Test
    public void crossingSquareInOneFrameDoesNotCarryTheReleaseAcross() {
        float crossed = LostTalesLotrMapRotation.advanceInput(0.02F, -0.5F);

        assertTrue("the map must end up the other side of square",
                crossed < 0.0F);
        // Everything past square is ordinary outward travel, so it can never
        // have bought more angle than the same drag would from a standstill.
        assertTrue("the release must stop at square",
                crossed >= -0.5F);
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

    @Test
    public void objectQueriesDoNotWalkAFullDiagonalSquareOnWideScreens() {
        float[] sheet = new float[2];
        float[] visible = new float[2];
        LostTalesLotrMapRotation.rotatedCoverage(
                2048.0F, 768.0F, 0.0F, 0.6F, sheet);
        LostTalesLotrMapRotation.visibleCoverage(
                2048.0F, 768.0F, 0.0F, 0.6F, visible);

        assertTrue("the visible width must still cover the viewport",
                visible[0] >= 2048.0F);
        assertTrue("the visible height must still cover the viewport",
                visible[1] >= 768.0F);
        assertTrue("an ultrawide viewport still queried a diagonal square",
                visible[1] < sheet[1] * 0.6F);
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
        float limit = LostTalesLotrMapRotation.MAX_DEGREES;
        for (float degrees = -limit; degrees <= limit; degrees += 2.5F) {
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
                LostTalesLotrMapRotation.leanForInput(0.0F), 0.0F);
        assertEquals(1.0F,
                LostTalesLotrMapRotation.leanForInput(1.0F), 0.001F);
        // Flat is the end of the lean's range, not its middle, so there is
        // nothing there to settle onto and the first pixel has to tilt.
        assertTrue(LostTalesLotrMapRotation.leanForInput(
                4.0F * LostTalesLotrMapRotation.leanInputPerPixel())
                > 0.0F);

        // The same share of the drag buys the same share of the movement,
        // clear of the magnet the turn has at north and the lean has not.
        for (float input = 0.1F; input <= 1.0F; input += 0.1F) {
            assertEquals(
                    LostTalesLotrMapRotation.degreesForInput(input)
                            / LostTalesLotrMapRotation.MAX_DEGREES,
                    LostTalesLotrMapRotation.leanForInput(input), 0.001F);
        }
    }

    /**
     * The feel the turn is meant to have, measured where a player would feel
     * it: light off square, heavier half way out, heavy at the end. One
     * number over the whole range is exactly what it must not be.
     *
     * <p>Measured at shares of the range rather than at fixed angles, because
     * the shape of the curve is what is being tested and widening the limit
     * must not be able to make this pass or fail by accident.</p>
     */
    @Test
    public void resistanceRisesInThreeFeelableSteps() {
        float step = LostTalesLotrMapRotation.MAX_DEGREES / 22.5F;
        float nearSquare = dragFor(1.5F * step) - dragFor(0.5F * step);
        float halfWay = dragFor(12.0F * step) - dragFor(11.0F * step);
        float nearTheLimit = dragFor(LostTalesLotrMapRotation.MAX_DEGREES)
                - dragFor(LostTalesLotrMapRotation.MAX_DEGREES - step);

        assertTrue("coming off square must be easy", nearSquare < 8.0F);
        assertTrue("half way out must cost noticeably more",
                halfWay > nearSquare * 1.5F);
        assertTrue("the limit must be leaned into",
                nearTheLimit > halfWay * 4.0F);
        // And a full turn must stay inside one comfortable sweep of the hand
        // rather than needing the mouse picked up and put down.
        assertTrue("most of the range must be within one sweep",
                dragFor(LostTalesLotrMapRotation.MAX_DEGREES - 4.5F * step)
                        < 260.0F);
    }

    /** The complaint that started it: the stiffening has to be felt. */
    @Test
    public void theLastDegreesCostFarMoreDragThanTheFirst() {
        float step = LostTalesLotrMapRotation.MAX_DEGREES / 11.25F;
        float first = dragFor(step) - dragFor(0.0F);
        float last = dragFor(LostTalesLotrMapRotation.MAX_DEGREES)
                - dragFor(LostTalesLotrMapRotation.MAX_DEGREES - step);

        assertTrue("the last degrees must cost several times the first",
                last > first * 5.0F);
    }

    /**
     * Turning and leaning are one gesture and share one limit, so the map
     * cannot be tipped dramatically further one way than the other.
     */
    @Test
    public void theTurnAndTheLeanShareOneLimit() {
        assertEquals(LostTalesLotrMapRotation.MAX_ORIENTATION_DEGREES,
                LostTalesLotrMapRotation.MAX_DEGREES, 0.0F);
        assertEquals(LostTalesLotrMapRotation.MAX_ORIENTATION_DEGREES,
                LostTalesLotrMapRotation.MAX_PITCH_DEGREES, 0.0F);
        assertEquals(33.75F,
                LostTalesLotrMapRotation.MAX_ORIENTATION_DEGREES, 0.0F);
        assertEquals("a full lean must drop the eye exactly that far",
                LostTalesLotrMapRotation.MAX_ORIENTATION_DEGREES,
                LostTalesLotrMapRotation.pitchDegrees(1.0F), 0.0001F);
        // Dragging may briefly pass the normal limit, but never its visual
        // safety bound, and both axes get the same amount of overshoot.
        assertEquals(LostTalesLotrMapRotation.MAX_VISUAL_DEGREES,
                LostTalesLotrMapRotation.pitchDegrees(4.0F), 0.0001F);
        assertEquals(LostTalesLotrMapRotation.MAX_VISUAL_DEGREES,
                Math.abs(LostTalesLotrMapRotation.degreesForInput(-9.0F)),
                0.001F);
        assertEquals(LostTalesLotrMapRotation.MAX_VISUAL_LEAN,
                LostTalesLotrMapRotation.leanForInput(9.0F), 0.0001F);
    }

    /**
     * A billboard standing on the far half of a leaning map is further from
     * the eye than one on the near half, and has to be drawn smaller for it.
     */
    @Test
    public void thingsStandingOnALeaningMapRecedeWithIt() {
        float coefficient = LostTalesLotrMapRotation.leanCoefficient(
                1.0F, 360.0F);
        assertEquals("a flat map shrinks nothing", 1.0F,
                LostTalesLotrMapRotation.perspectiveScale(120.0F, 0.0F),
                0.0F);
        assertEquals("the point the eye is aimed at is drawn full size", 1.0F,
                LostTalesLotrMapRotation.perspectiveScale(0.0F, coefficient),
                0.0001F);

        float near = LostTalesLotrMapRotation.perspectiveScale(
                150.0F, coefficient);
        float far = LostTalesLotrMapRotation.perspectiveScale(
                -150.0F, coefficient);
        assertTrue("the near half must be drawn larger", near > 1.0F);
        assertTrue("the far half must be drawn smaller", far < 1.0F);
        assertTrue("nothing may be turned inside out by the divide",
                far > 0.0F);
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

    /**
     * The paper grain is one stretched copy cut to this size, so it has to
     * reach past the sheet at every angle the map can be put in. If it ever
     * stops doing that the grain runs out before the map does, and raising
     * the lean is exactly how that would happen.
     */
    @Test
    public void theGrainCoversTheSheetAtEveryLean() {
        float[] coverage = new float[2];
        for (int screen = 0; screen < WIDTHS.length; screen++) {
            float width = WIDTHS[screen];
            float height = HEIGHTS[screen];
            float grain = LostTalesLotrMapRotation.maxCoverage(width, height);
            for (float lean = 0.0F;
                 lean <= LostTalesLotrMapRotation.MAX_VISUAL_LEAN;
                 lean += 0.05F) {
                LostTalesLotrMapRotation.rotatedCoverage(
                        width, height,
                        LostTalesLotrMapRotation.MAX_DEGREES, lean, coverage);
                assertTrue("grain short of the sheet at lean " + lean,
                        grain >= coverage[0] - 0.001F
                                && grain >= coverage[1] - 0.001F);
            }
        }
    }

    /** Ordinary, wide and very wide viewports. */
    private static final float[] WIDTHS = {854.0F, 960.0F, 1000.0F};
    private static final float[] HEIGHTS = {480.0F, 540.0F, 409.0F};
}
