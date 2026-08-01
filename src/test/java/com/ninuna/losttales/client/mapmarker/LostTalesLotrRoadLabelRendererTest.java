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
}
