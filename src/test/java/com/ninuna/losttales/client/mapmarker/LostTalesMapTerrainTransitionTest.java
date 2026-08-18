package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.config.LostTalesConfig;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapTerrainTransitionTest {
    @Test
    public void transitionHasExactStableEnds() {
        assertEquals(0.0F, LostTalesMapTerrainTransition.terrainAlpha(
                LostTalesMapTerrainTransition.mapOnlyZoomExp()), 0.0F);
        assertEquals(1.0F, LostTalesMapTerrainTransition.terrainAlpha(
                LostTalesMapTerrainTransition.terrainOnlyZoomExp()), 0.0F);
        assertEquals(0.0F, LostTalesMapTerrainTransition.terrainAlpha(
                LostTalesMapTerrainTransition.mapOnlyZoomExp() - 10.0F), 0.0F);
        assertEquals(1.0F, LostTalesMapTerrainTransition.terrainAlpha(
                LostTalesMapTerrainTransition.terrainOnlyZoomExp() + 10.0F), 0.0F);
    }

    @Test
    public void opacitiesAreComplementaryAndMonotonic() {
        float previous = 0.0F;
        for (float zoom = LostTalesLotrMapGui.SMOOTH_ZOOM_MIN;
             zoom <= LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
             zoom += 0.01F) {
            float terrain = LostTalesMapTerrainTransition.terrainAlpha(zoom);
            float map = LostTalesMapTerrainTransition.mapAlpha(zoom);
            assertTrue(terrain >= previous - 0.0001F);
            assertEquals(1.0F, terrain + map, 0.0001F);
            previous = terrain;
        }
    }

    @Test
    public void malformedZoomKeepsTheKnownMapVisible() {
        assertEquals(0.0F, LostTalesMapTerrainTransition.terrainAlpha(
                Float.NaN), 0.0F);
        assertEquals(1.0F, LostTalesMapTerrainTransition.mapAlpha(
                Float.NaN), 0.0F);
        assertFalse(LostTalesMapTerrainTransition.shouldPrepareTerrain(
                Float.NaN));
    }

    @Test
    public void preparationBeginsBeforeTheVisibleTransition() {
        assertTrue(LostTalesMapTerrainTransition.shouldPrepareTerrain(
                LostTalesMapTerrainTransition.mapOnlyZoomExp()));
        assertTrue(LostTalesMapTerrainTransition.shouldPrepareTerrain(
                LostTalesMapTerrainTransition.mapOnlyZoomExp()
                        - LostTalesMapTerrainTransition.PREPARE_MARGIN));
        assertFalse(LostTalesMapTerrainTransition.shouldPrepareTerrain(
                LostTalesMapTerrainTransition.mapOnlyZoomExp()
                        - LostTalesMapTerrainTransition.PREPARE_MARGIN - 0.01F));
    }

    @Test
    public void configuredThresholdsDriveTheBlend() {
        double oldStart =
                LostTalesConfig.closeMapTerrainTransitionStartZoom;
        double oldEnd = LostTalesConfig.closeMapTerrainTransitionEndZoom;
        try {
            LostTalesConfig.closeMapTerrainTransitionStartZoom = 2.0D;
            LostTalesConfig.closeMapTerrainTransitionEndZoom = 3.0D;

            assertEquals(0.0F,
                    LostTalesMapTerrainTransition.terrainAlpha(2.0F), 0.0F);
            assertEquals(0.5F,
                    LostTalesMapTerrainTransition.terrainAlpha(2.5F), 0.0001F);
            assertEquals(1.0F,
                    LostTalesMapTerrainTransition.terrainAlpha(3.0F), 0.0F);
        } finally {
            LostTalesConfig.closeMapTerrainTransitionStartZoom = oldStart;
            LostTalesConfig.closeMapTerrainTransitionEndZoom = oldEnd;
        }
    }
}
