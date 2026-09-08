package com.ninuna.losttales.client.camera;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The framing arithmetic: the subject lands where it is asked, at the size it is asked. */
public class InspectionCameraMathTest {

    private static final double EPSILON = 0.0001D;

    @Test
    public void aSubjectFillingHalfTheScreenStandsAtTheExpectedDistance() {
        // At a 90 degree field of view the screen is two units tall at one
        // unit away, so a 1.8 tall subject filling half of it is 1.8 away.
        assertEquals(1.8D, InspectionCameraMath.distanceFor(1.8D, 0.5D, 90.0D, 1.0D),
                EPSILON);
    }

    @Test
    public void zoomBringsTheCameraNearerInProportion() {
        double base = InspectionCameraMath.distanceFor(1.8D, 0.5D, 70.0D, 1.0D);
        assertEquals(base / 2.0D, InspectionCameraMath.distanceFor(1.8D, 0.5D, 70.0D, 2.0D),
                EPSILON);
    }

    @Test
    public void theDistanceStaysWithinItsBounds() {
        assertEquals(InspectionCameraMath.MIN_DISTANCE,
                InspectionCameraMath.distanceFor(0.5D, 0.9D, 110.0D, 100.0D), EPSILON);
        assertEquals(InspectionCameraMath.MAX_DISTANCE,
                InspectionCameraMath.distanceFor(3.0D, 0.05D, 30.0D, 0.1D), EPSILON);
    }

    @Test
    public void nonsenseInputsFallBackRatherThanExploding() {
        // A zero height, share and zoom and a field of view that is not a
        // number all fall back to a standing human at a default view.
        double sane = InspectionCameraMath.distanceFor(1.8D, 0.5D, 70.0D, 1.0D);
        assertEquals(sane, InspectionCameraMath.distanceFor(0.0D, 0.0D, Double.NaN, 0.0D),
                EPSILON);
    }

    @Test
    public void aSubjectWantedOnTheRightHasTheCameraStepLeft() {
        double side = InspectionCameraMath.sideOffsetFor(4.0D, 90.0D, 2.0D, 0.5D);
        // Half way to the right edge at a two-to-one aspect: the half width
        // at four units is eight, and half of that is four, to the left.
        assertEquals(-4.0D, side, EPSILON);
        assertEquals(0.0D, InspectionCameraMath.sideOffsetFor(4.0D, 90.0D, 2.0D, 0.0D),
                EPSILON);
    }

    @Test
    public void aSubjectWantedLowerHasTheCameraRise() {
        assertTrue(InspectionCameraMath.verticalOffsetFor(4.0D, 90.0D, -0.25D) > 0.0D);
        assertEquals(-1.0D, InspectionCameraMath.verticalOffsetFor(4.0D, 90.0D, 0.25D),
                EPSILON);
    }

    @Test
    public void thePivotDropsFromTheEyeToTheMiddleOfTheBody() {
        assertEquals(1.62D - 0.9D, InspectionCameraMath.pivotDropBelowEye(1.8D, 1.62D),
                EPSILON);
        // Looking at the head rather than the middle: the point rises to the
        // eye and above it.
        assertEquals(0.0D, InspectionCameraMath.pivotDropBelowEye(1.8D, 1.62D, 0.9D),
                EPSILON);
        assertTrue(InspectionCameraMath.pivotDropBelowEye(1.8D, 1.62D, 1.0D) < 0.0D);
        // A hobbit's eye is lower and its body shorter; the drop shrinks with it.
        assertTrue(InspectionCameraMath.pivotDropBelowEye(1.2D, 1.05D)
                < InspectionCameraMath.pivotDropBelowEye(1.8D, 1.62D));
    }

    @Test
    public void pixelsMapToNormalisedScreenPositions() {
        assertEquals(0.0D, InspectionCameraMath.screenX(200.0D, 400.0D), EPSILON);
        assertEquals(1.0D, InspectionCameraMath.screenX(400.0D, 400.0D), EPSILON);
        assertEquals(-1.0D, InspectionCameraMath.screenX(0.0D, 400.0D), EPSILON);
        assertEquals(1.0D, InspectionCameraMath.screenY(0.0D, 300.0D), EPSILON);
        assertEquals(-1.0D, InspectionCameraMath.screenY(300.0D, 300.0D), EPSILON);
        assertEquals(0.0D, InspectionCameraMath.screenX(50.0D, 0.0D), EPSILON);
    }
}
