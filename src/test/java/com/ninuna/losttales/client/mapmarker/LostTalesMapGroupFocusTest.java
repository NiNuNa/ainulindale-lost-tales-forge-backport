package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public final class LostTalesMapGroupFocusTest {
    /** Half the ink of the pin glyph the map draws markers with. */
    private static final float ICON_HALF_EXTENT = 3.5F;
    private static final float MAX_ZOOM = 4.0F;

    @Test
    public void zoomStopsAsSoonAsTheStackComesApart() {
        List<LostTalesMapMarkerGrouping.Entry> stack = Arrays.asList(
                icon("losttales:a", 100.0F, 50.0F),
                icon("losttales:b", 103.0F, 50.0F));

        float zoomExp = LostTalesMapGroupFocus.separatingZoomExponent(
                stack, 1.0F, MAX_ZOOM, 101.5F, 50.0F);

        assertTrue("the stack must need more zoom than it started with",
                zoomExp > 1.0F);
        assertTrue("far more zoom than needed is not framing, it is a jump",
                zoomExp < MAX_ZOOM);
        assertTrue(isSeparated(stack, 101.5F, 50.0F, zoomExp - 1.0F));
    }

    @Test
    public void theChosenZoomClearsTheGroupingThresholdWithRoomToSpare() {
        List<LostTalesMapMarkerGrouping.Entry> stack = Arrays.asList(
                icon("losttales:a", 100.0F, 50.0F),
                icon("losttales:b", 102.0F, 51.0F),
                icon("losttales:c", 98.0F, 49.0F));

        float zoomExp = LostTalesMapGroupFocus.separatingZoomExponent(
                stack, 0.0F, MAX_ZOOM, 100.0F, 50.0F);

        // Backing off by the tolerance must still separate them, which is
        // what stops the arrival zoom sitting on the threshold and letting
        // rounding regroup the markers straight away.
        assertTrue(isSeparated(stack, 100.0F, 50.0F,
                zoomExp - LostTalesMapGroupFocus.ZOOM_TOLERANCE));
    }

    @Test
    public void markersThatAreAlreadyApartDoNotMoveTheZoom() {
        List<LostTalesMapMarkerGrouping.Entry> stack = Arrays.asList(
                icon("losttales:a", 100.0F, 50.0F),
                icon("losttales:b", 400.0F, 300.0F));

        assertEquals(1.0F + LostTalesMapGroupFocus.ZOOM_SEARCH_STEP
                        + LostTalesMapGroupFocus.ZOOM_TOLERANCE,
                LostTalesMapGroupFocus.separatingZoomExponent(
                        stack, 1.0F, MAX_ZOOM, 250.0F, 175.0F),
                0.0001F);
    }

    @Test
    public void aLoneMarkerHasNothingToFrame() {
        assertEquals(2.5F, LostTalesMapGroupFocus.separatingZoomExponent(
                Collections.singletonList(
                        icon("losttales:a", 100.0F, 50.0F)),
                2.5F, MAX_ZOOM, 100.0F, 50.0F), 0.0F);
        assertEquals(MAX_ZOOM,
                LostTalesMapGroupFocus.separatingZoomExponent(
                        Arrays.asList(icon("losttales:a", 0.0F, 0.0F),
                                icon("losttales:b", 0.0F, 0.0F)),
                        MAX_ZOOM, MAX_ZOOM, 0.0F, 0.0F), 0.0F);
    }

    @Test
    public void markersDeadOnTopOfEachOtherStopAtTheZoomCeiling() {
        assertEquals(MAX_ZOOM,
                LostTalesMapGroupFocus.separatingZoomExponent(
                        Arrays.asList(
                                icon("losttales:a", 100.0F, 50.0F),
                                icon("losttales:b", 100.0F, 50.0F)),
                        0.0F, MAX_ZOOM, 100.0F, 50.0F), 0.0F);
    }

    @Test
    public void easingLeavesAndArrivesFlat() {
        assertEquals(0.0F, LostTalesMapGroupFocus.ease(0.0F), 0.0F);
        assertEquals(1.0F, LostTalesMapGroupFocus.ease(1.0F), 0.0F);
        assertEquals(0.5F, LostTalesMapGroupFocus.ease(0.5F), 0.0001F);
        // Clamped, so a late frame cannot carry the camera past its target.
        assertEquals(0.0F, LostTalesMapGroupFocus.ease(-1.0F), 0.0F);
        assertEquals(1.0F, LostTalesMapGroupFocus.ease(2.0F), 0.0F);
        // Flat at both ends is what makes the movement lift off and settle
        // rather than starting and stopping abruptly.
        assertTrue(LostTalesMapGroupFocus.ease(0.05F) < 0.01F);
        assertTrue(LostTalesMapGroupFocus.ease(0.95F) > 0.99F);
    }

    @Test
    public void progressIsMonotonicAlongTheWholeMovement() {
        float previous = -1.0F;
        for (int step = 0; step <= 100; step++) {
            float eased = LostTalesMapGroupFocus.ease(step / 100.0F);
            assertTrue("progress went backwards", eased >= previous);
            previous = eased;
        }
    }

    @Test
    public void aLongerJourneyTakesLongerBetweenItsBounds() {
        assertEquals(LostTalesMapGroupFocus.MIN_DURATION_NANOS,
                LostTalesMapGroupFocus.durationFor(0.0F));
        assertEquals(LostTalesMapGroupFocus.MAX_DURATION_NANOS,
                LostTalesMapGroupFocus.durationFor(100000.0F));
        assertTrue(LostTalesMapGroupFocus.durationFor(30.0F)
                > LostTalesMapGroupFocus.durationFor(5.0F));
        // A negative distance cannot exist, but it must not shorten the
        // movement below its floor either.
        assertEquals(LostTalesMapGroupFocus.MIN_DURATION_NANOS,
                LostTalesMapGroupFocus.durationFor(-10.0F));
    }

    @Test
    public void theBowedPathKeepsBothOfItsEndpoints() {
        float[] point = new float[2];
        LostTalesMapGroupFocus.pathPoint(
                10.0F, 20.0F, 110.0F, 20.0F, 0.0F, point);
        assertEquals(10.0F, point[0], 0.0001F);
        assertEquals(20.0F, point[1], 0.0001F);

        LostTalesMapGroupFocus.pathPoint(
                10.0F, 20.0F, 110.0F, 20.0F, 1.0F, point);
        assertEquals(110.0F, point[0], 0.0001F);
        assertEquals(20.0F, point[1], 0.0001F);

        // Halfway along it is beside the straight line, not on it.
        LostTalesMapGroupFocus.pathPoint(
                10.0F, 20.0F, 110.0F, 20.0F, 0.5F, point);
        assertEquals(60.0F, point[0], 0.0001F);
        assertTrue(Math.abs(point[1] - 20.0F) > 0.5F);
        assertTrue(Math.abs(point[1] - 20.0F)
                <= LostTalesMapGroupFocus.PATH_ARC_MAX + 0.0001F);
    }

    @Test
    public void aCameraThatIsAlreadyThereHasNoPathToBow() {
        float[] point = new float[2];
        LostTalesMapGroupFocus.pathPoint(
                40.0F, 40.0F, 40.0F, 40.0F, 0.5F, point);

        assertEquals(40.0F, point[0], 0.0001F);
        assertEquals(40.0F, point[1], 0.0001F);
    }

    private static boolean isSeparated(
            List<LostTalesMapMarkerGrouping.Entry> stack,
            float centerX, float centerY, float zoomIncrease) {
        float factor = (float)Math.pow(2.0D, zoomIncrease);
        List<LostTalesMapMarkerGrouping.Entry> scaled =
                new ArrayList<LostTalesMapMarkerGrouping.Entry>();
        for (LostTalesMapMarkerGrouping.Entry entry : stack) {
            scaled.add(LostTalesMapMarkerGrouping.scaledAbout(
                    entry, centerX, centerY, factor));
        }
        return LostTalesMapMarkerGrouping.group(scaled,
                Collections.<String, String>emptyMap())
                .getGroups().size() == scaled.size();
    }

    private static LostTalesMapMarkerGrouping.Entry icon(
            String id, float centerX, float centerY) {
        return new LostTalesMapMarkerGrouping.Entry(
                id, id, 0,
                LostTalesMapMarkerGrouping.GroupingCategory.LOCATION,
                LostTalesMapMarkerRenderedGeometry.artLeft(
                        centerX, ICON_HALF_EXTENT),
                LostTalesMapMarkerRenderedGeometry.artTop(
                        centerY, ICON_HALF_EXTENT),
                LostTalesMapMarkerRenderedGeometry.artRight(
                        centerX, ICON_HALF_EXTENT),
                LostTalesMapMarkerRenderedGeometry.artBottom(
                        centerY, ICON_HALF_EXTENT));
    }
}
