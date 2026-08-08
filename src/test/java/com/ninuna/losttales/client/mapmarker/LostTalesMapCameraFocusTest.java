package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapCameraFocusTest {
    @Test
    public void easingLeavesAndArrivesAtRest() {
        assertEquals(0.0F, LostTalesMapCameraFocus.ease(0.0F), 0.0F);
        assertEquals(1.0F, LostTalesMapCameraFocus.ease(1.0F), 0.0F);
        assertEquals(0.5F, LostTalesMapCameraFocus.ease(0.5F), 0.0001F);
        // Clamped, so a late frame cannot carry the camera past its target.
        assertEquals(0.0F, LostTalesMapCameraFocus.ease(-1.0F), 0.0F);
        assertEquals(1.0F, LostTalesMapCameraFocus.ease(2.0F), 0.0F);
    }

    /**
     * The complaint the easing was changed for: the old curve barely moved for
     * the first tenth of the movement and crawled through the last tenth.
     */
    @Test
    public void theMovementIsVisibleAtBothEnds() {
        assertTrue("the camera must be moving by the tenth of the way in",
                LostTalesMapCameraFocus.ease(0.1F) > 0.02F);
        assertTrue("the last tenth must not be a crawl",
                LostTalesMapCameraFocus.ease(0.9F) < 0.98F);
    }

    @Test
    public void noPartOfTheMovementRunsAwayWithIt() {
        float previous = 0.0F;
        float fastest = 0.0F;
        for (int step = 1; step <= 100; step++) {
            float eased = LostTalesMapCameraFocus.ease(step / 100.0F);
            assertTrue("progress went backwards", eased >= previous);
            fastest = Math.max(fastest, eased - previous);
            previous = eased;
        }
        // Peak speed stays within half again the average, so there is no
        // abrupt high-speed middle section.
        assertTrue("the middle of the movement is a lurch",
                fastest <= 0.0155F);
    }

    @Test
    public void aLongerJourneyTakesLongerBetweenItsBounds() {
        assertEquals(LostTalesMapCameraFocus.MIN_DURATION_NANOS,
                LostTalesMapCameraFocus.durationFor(0.0F));
        assertEquals(LostTalesMapCameraFocus.MAX_DURATION_NANOS,
                LostTalesMapCameraFocus.durationFor(100000.0F));
        assertTrue(LostTalesMapCameraFocus.durationFor(30.0F)
                > LostTalesMapCameraFocus.durationFor(5.0F));
        // A negative distance cannot exist, but it must not shorten the
        // movement below its floor either.
        assertEquals(LostTalesMapCameraFocus.MIN_DURATION_NANOS,
                LostTalesMapCameraFocus.durationFor(-10.0F));
    }

    @Test
    public void aShortHopIsNotWorthCurving() {
        assertEquals(0.0F, LostTalesMapCameraFocus.curvature(
                LostTalesMapCameraFocus.CURVATURE_MIN_DISTANCE, 1.0F),
                0.0F);
        assertEquals(0.0F,
                LostTalesMapCameraFocus.curvature(0.0F, 1.0F), 0.0F);
    }

    @Test
    public void curvatureGrowsWithDistanceAndWithBeingOffCentre() {
        float nearCentre = LostTalesMapCameraFocus.curvature(400.0F, 0.0F);
        float atTheEdge = LostTalesMapCameraFocus.curvature(400.0F, 1.0F);
        assertTrue(atTheEdge > nearCentre);
        assertTrue(atTheEdge <= LostTalesMapCameraFocus.MAX_CURVATURE);
        assertTrue(LostTalesMapCameraFocus.curvature(400.0F, 1.0F)
                > LostTalesMapCameraFocus.curvature(40.0F, 1.0F));
        // Past the full-bow distance it stops growing rather than running on.
        assertEquals(LostTalesMapCameraFocus.MAX_CURVATURE,
                LostTalesMapCameraFocus.curvature(10000.0F, 1.0F), 0.0001F);
    }

    @Test
    public void theCurvedPathKeepsBothOfItsEndpoints() {
        float[] point = new float[2];
        LostTalesMapCameraFocus.pathPoint(
                10.0F, 20.0F, 110.0F, 90.0F, 0.3F, 0.0F, point);
        assertEquals(10.0F, point[0], 0.0001F);
        assertEquals(20.0F, point[1], 0.0001F);

        LostTalesMapCameraFocus.pathPoint(
                10.0F, 20.0F, 110.0F, 90.0F, 0.3F, 1.0F, point);
        assertEquals(110.0F, point[0], 0.0001F);
        assertEquals(90.0F, point[1], 0.0001F);
    }

    @Test
    public void noCurvatureIsAStraightLineAtAUniformSpeed() {
        float[] point = new float[2];
        LostTalesMapCameraFocus.pathPoint(
                0.0F, 0.0F, 100.0F, 50.0F, 0.0F, 0.25F, point);
        assertEquals(25.0F, point[0], 0.0001F);
        assertEquals(12.5F, point[1], 0.0001F);
    }

    /** Below and to the right: the camera has to set off downward. */
    @Test
    public void aMostlyVerticalTravelLeadsWithTheVerticalAxis() {
        float[] point = new float[2];
        LostTalesMapCameraFocus.pathPoint(
                0.0F, 0.0F, 40.0F, 200.0F, 0.34F, 0.25F, point);

        float straightX = 10.0F;
        float straightY = 50.0F;
        assertTrue("the path must lead downward, not across",
                point[1] > straightY);
        assertTrue(point[0] < straightX);
    }

    /** To the right and slightly down: the camera has to set off rightward. */
    @Test
    public void aMostlyHorizontalTravelLeadsWithTheHorizontalAxis() {
        float[] point = new float[2];
        LostTalesMapCameraFocus.pathPoint(
                0.0F, 0.0F, 200.0F, 40.0F, 0.34F, 0.25F, point);

        assertTrue("the path must lead across, not downward",
                point[0] > 50.0F);
        assertTrue(point[1] < 10.0F);
    }

    /**
     * The excursion that made the old path read as a swing: whatever the
     * curvature, no point on the path may sit far off the straight line.
     */
    @Test
    public void thePathNeverSwingsWideOfItsDestination() {
        float[] point = new float[2];
        float endX = 300.0F;
        float endY = 180.0F;
        float length = (float)Math.sqrt(endX * endX + endY * endY);
        for (int step = 0; step <= 100; step++) {
            LostTalesMapCameraFocus.pathPoint(0.0F, 0.0F, endX, endY,
                    LostTalesMapCameraFocus.MAX_CURVATURE,
                    step / 100.0F, point);
            float lateral = Math.abs(
                    point[0] * endY - point[1] * endX) / length;
            assertTrue("the camera swung away from its destination",
                    lateral <= length * 0.1F);
        }
    }

    @Test
    public void framingAPointUnderAnAnchorIsInvertible() {
        float framed = LostTalesMapCameraFocus.framedPositionAt(
                800.0F, 120.0F, 320.0F, 4.0F);
        assertEquals(800.0F, LostTalesMapCameraFocus.cameraPositionFor(
                framed, 120.0F, 320.0F, 4.0F), 0.0001F);
        // An anchor at the middle of the viewport frames the camera position
        // itself, whatever the zoom.
        assertEquals(800.0F, LostTalesMapCameraFocus.framedPositionAt(
                800.0F, 320.0F, 320.0F, 0.25F), 0.0001F);
    }
}
