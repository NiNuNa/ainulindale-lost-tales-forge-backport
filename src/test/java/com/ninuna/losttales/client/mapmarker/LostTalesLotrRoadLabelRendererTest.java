package com.ninuna.losttales.client.mapmarker;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesLotrRoadLabelRendererTest {
    @Test
    public void roadLabelDensityMatchesNativeZoomRelationship() {
        int zoomedOut = LostTalesLotrRoadLabelRenderer
                .calculateLabelCount(100000, 50, 1.0F);
        int zoomedIn = LostTalesLotrRoadLabelRenderer
                .calculateLabelCount(100000, 50, 2.0F);

        assertEquals(zoomedOut * 2, zoomedIn);
    }

    @Test
    public void existingRoadLabelAnchorsNeverMoveWhenCountChanges() {
        assertEquals(0.5D,
                LostTalesLotrRoadLabelRenderer.anchorFraction(0), 0.0D);
        assertEquals(0.25D,
                LostTalesLotrRoadLabelRenderer.anchorFraction(1), 0.0D);
        assertEquals(0.75D,
                LostTalesLotrRoadLabelRenderer.anchorFraction(2), 0.0D);
    }

    @Test
    public void roadLabelAnglesStayReadable() {
        assertEquals(0.0F,
                LostTalesLotrRoadLabelRenderer.uprightAngle(1.0D, 0.0D),
                0.0001F);
        assertEquals(0.0F,
                LostTalesLotrRoadLabelRenderer.uprightAngle(-1.0D, 0.0D),
                0.0001F);
        float diagonal = LostTalesLotrRoadLabelRenderer
                .uprightAngle(-1.0D, 1.0D);
        assertTrue(diagonal >= -90.0F && diagonal <= 90.0F);
    }

    @Test
    public void roadLabelsTurnWithTheMap() {
        // A road running due east is drawn along the map's turn, not across
        // it: the name is ink on the paper and turns with the paper.
        assertEquals(20.0F,
                LostTalesLotrRoadLabelRenderer
                        .uprightAngle(1.0D, 0.0D, 20.0F),
                0.0001F);
        assertEquals(-20.0F,
                LostTalesLotrRoadLabelRenderer
                        .uprightAngle(1.0D, 0.0D, -20.0F),
                0.0001F);
        // A square map leaves the angle exactly where the unturned form put
        // it, so nothing moves until the map is actually turned.
        assertEquals(
                LostTalesLotrRoadLabelRenderer.uprightAngle(-1.0D, 1.0D),
                LostTalesLotrRoadLabelRenderer
                        .uprightAngle(-1.0D, 1.0D, 0.0F),
                0.0001F);
    }

    @Test
    public void turnedRoadLabelsStayReadable() {
        for (float degrees = -22.5F; degrees <= 22.5F; degrees += 2.5F) {
            for (int step = 0; step < 16; step++) {
                double radians = Math.PI * step / 8.0D;
                float angle = LostTalesLotrRoadLabelRenderer.uprightAngle(
                        Math.cos(radians), Math.sin(radians), degrees);
                assertTrue("upside down at " + degrees + "/" + step,
                        angle >= -90.0F && angle <= 90.0F);
            }
        }
    }
}
