package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.core.LostTalesClassTransformer;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import lotr.client.gui.LOTRGuiMap;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import org.junit.Test;
import sun.misc.Unsafe;

public final class LostTalesLotrMapGuiTest {
    @Test
    public void zoomLimitsFitTheMapAndFillTheCloseTerrainView() {
        int terrainDiameterBlocks =
                (LostTalesMapTerrainCache.MAX_CAPTURE_RADIUS_CHUNKS * 2 + 1)
                        * LostTalesMapTerrainCache.TILE_WIDTH;
        float closeTerrainPixels = terrainDiameterBlocks
                * (float)Math.pow(2.0D,
                        LostTalesLotrMapGui.SMOOTH_ZOOM_MAX)
                / LOTRGenLayerWorld.scale;
        float closeBlockPixels = closeTerrainPixels / terrainDiameterBlocks;

        assertTrue("the close terrain cache remains too small on screen",
                closeTerrainPixels >= 2480.0F);
        assertTrue("the close terrain cache is excessively magnified",
                closeTerrainPixels <= 2540.0F);
        assertEquals("the widest view should remain regional",
                -2.25F, LostTalesLotrMapGui.SMOOTH_ZOOM_MIN, 0.0F);
        assertEquals("maximum zoom must retain the bounded 3x3 terrain LOD",
                3, LostTalesMapTerrainRenderer.sampleStep(closeBlockPixels));
    }

    @Test
    public void factionControlZoneModeSurvivesReplacement()
            throws Exception {
        LOTRGuiMap original = allocate(LOTRGuiMap.class);
        field("controlZoneFaction").set(
                original, LOTRFaction.ISENGARD);
        field("hasControlZones").setBoolean(original, true);
        LostTalesLotrMapGui replacement =
                allocate(LostTalesLotrMapGui.class);

        assertTrue(LostTalesLotrMapGui.copyInitialMode(
                original, replacement));

        assertSame(LOTRFaction.ISENGARD,
                field("controlZoneFaction").get(replacement));
        assertTrue(field("hasControlZones").getBoolean(replacement));
    }

    @Test
    public void conquestGridModeSurvivesReplacement()
            throws Exception {
        LOTRGuiMap original = allocate(LOTRGuiMap.class);
        field("isConquestGrid").setBoolean(original, true);
        LostTalesLotrMapGui replacement =
                allocate(LostTalesLotrMapGui.class);

        assertTrue(LostTalesLotrMapGui.copyInitialMode(
                original, replacement));

        assertTrue(field("isConquestGrid").getBoolean(replacement));
    }

    @Test
    public void smoothZoomIsBoundedAndApproachesTargetWithoutOvershoot() {
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MIN,
                LostTalesLotrMapGui.clampSmoothZoom(-20.0F), 0.0F);
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MAX,
                LostTalesLotrMapGui.clampSmoothZoom(20.0F), 0.0F);

        float zoom = 3.0F;
        for (int tick = 0; tick < 40; tick++) {
            float next = LostTalesLotrMapGui.advanceSmoothZoom(zoom, 4.0F);
            assertTrue(next >= zoom);
            assertTrue(next <= 4.0F);
            zoom = next;
        }
        assertEquals(4.0F, zoom, 0.001F);
    }

    @Test
    public void elasticZoomFlowsThroughBothCapsWithoutADetent() {
        float epsilon = 0.001F;
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MAX,
                LostTalesLotrMapGui.elasticZoomTarget(
                        LostTalesLotrMapGui.SMOOTH_ZOOM_MAX), 0.0F);
        assertEquals(epsilon,
                LostTalesLotrMapGui.elasticZoomTarget(
                        LostTalesLotrMapGui.SMOOTH_ZOOM_MAX + epsilon)
                        - LostTalesLotrMapGui.SMOOTH_ZOOM_MAX,
                0.00001F);
        assertEquals(epsilon,
                LostTalesLotrMapGui.SMOOTH_ZOOM_MIN
                        - LostTalesLotrMapGui.elasticZoomTarget(
                                LostTalesLotrMapGui.SMOOTH_ZOOM_MIN - epsilon),
                0.00001F);
    }

    @Test
    public void elasticZoomResistanceGetsHarderWithEveryWheelStep() {
        float cap = LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
        float step = LostTalesLotrMapGui.SMOOTH_ZOOM_WHEEL_INCREMENT;
        float first = LostTalesLotrMapGui.elasticZoomTarget(cap + step) - cap;
        float second = LostTalesLotrMapGui.elasticZoomTarget(cap + step * 2.0F)
                - LostTalesLotrMapGui.elasticZoomTarget(cap + step);
        float third = LostTalesLotrMapGui.elasticZoomTarget(cap + step * 3.0F)
                - LostTalesLotrMapGui.elasticZoomTarget(cap + step * 2.0F);

        assertTrue(first > second);
        assertTrue(second > third);
        assertTrue(LostTalesLotrMapGui.elasticZoomTarget(
                        cap + LostTalesLotrMapGui.SMOOTH_ZOOM_ELASTIC_INPUT)
                < cap + LostTalesLotrMapGui.SMOOTH_ZOOM_ELASTIC_OVERSHOOT);
    }

    @Test
    public void elasticZoomIsSymmetricAndReleasesToTheRealCaps() {
        float excess = 0.36F;
        float above = LostTalesLotrMapGui.elasticZoomTarget(
                LostTalesLotrMapGui.SMOOTH_ZOOM_MAX + excess)
                - LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
        float below = LostTalesLotrMapGui.SMOOTH_ZOOM_MIN
                - LostTalesLotrMapGui.elasticZoomTarget(
                        LostTalesLotrMapGui.SMOOTH_ZOOM_MIN - excess);

        assertEquals(above, below, 0.00001F);
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MAX,
                LostTalesLotrMapGui.clampSmoothZoom(
                        LostTalesLotrMapGui.SMOOTH_ZOOM_MAX + above), 0.0F);
        assertEquals(LostTalesLotrMapGui.SMOOTH_ZOOM_MIN,
                LostTalesLotrMapGui.clampSmoothZoom(
                        LostTalesLotrMapGui.SMOOTH_ZOOM_MIN - below), 0.0F);
    }

    @Test
    public void releasedElasticZoomEasesBackToTheCap() {
        float cap = LostTalesLotrMapGui.SMOOTH_ZOOM_MAX;
        float overshoot = LostTalesLotrMapGui.elasticZoomTarget(
                cap + LostTalesLotrMapGui.SMOOTH_ZOOM_WHEEL_INCREMENT * 3.0F);
        float zoom = cap;
        for (int tick = 0; tick < 6; tick++) {
            zoom = LostTalesLotrMapGui.advanceSmoothZoom(zoom, overshoot);
        }
        assertTrue(zoom > cap);

        float previous = zoom;
        for (int tick = 0; tick < 50; tick++) {
            zoom = LostTalesLotrMapGui.advanceSmoothZoom(zoom, cap);
            assertTrue(zoom <= previous);
            assertTrue(zoom >= cap);
            previous = zoom;
        }
        assertEquals(cap, zoom, 0.001F);
    }

    @Test
    public void fullscreenBoundsUseTheScaledGuiAndSkipConquestMode()
            throws Exception {
        String property = LostTalesClassTransformer
                .LOTR_MAP_FULLSCREEN_ACTIVE_PROPERTY;
        String previous = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            LostTalesLotrMapGui gui = allocate(LostTalesLotrMapGui.class);
            gui.width = 854;
            gui.height = 480;
            field("isConquestGrid").setBoolean(gui, false);

            LostTalesLotrMapLayout.applyFullscreenBounds(gui);

            assertEquals(0, field("mapXMin").getInt(null));
            assertEquals(854, field("mapXMax").getInt(null));
            assertEquals(0, field("mapYMin").getInt(null));
            assertEquals(480, field("mapYMax").getInt(null));
            assertEquals(854, field("mapWidth").getInt(null));
            assertEquals(480, field("mapHeight").getInt(null));
            assertEquals(456, LostTalesLotrMapLayout.resolveStatusY(
                    gui, 480, 20));

            field("isConquestGrid").setBoolean(gui, true);
            field("mapXMin").setInt(null, 227);
            field("mapXMax").setInt(null, 627);
            field("mapYMin").setInt(null, 104);
            field("mapYMax").setInt(null, 344);

            LostTalesLotrMapLayout.applyFullscreenBounds(gui);

            assertEquals(227, field("mapXMin").getInt(null));
            assertEquals(627, field("mapXMax").getInt(null));
            assertEquals(104, field("mapYMin").getInt(null));
            assertEquals(344, field("mapYMax").getInt(null));
            assertEquals(354, LostTalesLotrMapLayout.resolveStatusY(
                    gui, 344, 20));
        } finally {
            if (previous == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, previous);
            }
        }
    }

    private static Field field(String name) throws Exception {
        Field field = LOTRGuiMap.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Unsafe unsafe = (Unsafe)unsafeField.get(null);
        return type.cast(unsafe.allocateInstance(type));
    }
}
