package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapTerrainRendererTest {
    @Test
    public void zoomExponentIsInverseOfTheMapScale() {
        assertEquals(4.0F,
                LostTalesMapTerrainRenderer.zoomExponent(16.0F), 0.0001F);
        assertEquals(-2.0F,
                LostTalesMapTerrainRenderer.zoomExponent(0.25F), 0.0001F);
        assertTrue(Float.isNaN(
                LostTalesMapTerrainRenderer.zoomExponent(0.0F)));
        assertTrue(Float.isNaN(
                LostTalesMapTerrainRenderer.zoomExponent(Float.NaN)));
    }

    @Test
    public void reliefUsesSeaLevelAndHasHardBounds() {
        assertEquals(0, LostTalesMapTerrainRenderer.reliefHeight(63));
        assertEquals(37, LostTalesMapTerrainRenderer.reliefHeight(100));
        assertEquals(LostTalesMapTerrainRenderer.MIN_RELIEF,
                LostTalesMapTerrainRenderer.reliefHeight(-1000));
        assertEquals(LostTalesMapTerrainRenderer.MAX_RELIEF,
                LostTalesMapTerrainRenderer.reliefHeight(1000));
    }

    @Test
    public void detailTracksTheUsefulScreenResolution() {
        assertEquals(4, LostTalesMapTerrainRenderer.sampleStep(0.05F));
        assertEquals(4, LostTalesMapTerrainRenderer.sampleStep(0.2F));
        assertEquals(3, LostTalesMapTerrainRenderer.sampleStep(0.75F));
        assertEquals(3, LostTalesMapTerrainRenderer.sampleStep(4.75F));
        assertEquals(2, LostTalesMapTerrainRenderer.sampleStep(5.5F));
        assertEquals(1, LostTalesMapTerrainRenderer.sampleStep(10.0F));
    }

    @Test
    public void terrainCompilationIsStrictlyBoundedPerFrame() {
        assertEquals(2,
                LostTalesMapTerrainRenderer.MAX_MESH_COMPILES_PER_FRAME);
        assertTrue(LostTalesMapTerrainRenderer.FULL_DETAIL_BLOCK_PIXELS
                > LostTalesMapTerrainRenderer.MEDIUM_DETAIL_BLOCK_PIXELS);
        assertTrue(LostTalesMapTerrainRenderer.MEDIUM_DETAIL_BLOCK_PIXELS
                > LostTalesMapTerrainRenderer.FINE_DETAIL_BLOCK_PIXELS);
    }

    @Test
    public void elasticZoomCannotChangeTheSettledMaximumLod() {
        float settled = (float)Math.pow(2.0D,
                LostTalesLotrMapGui.SMOOTH_ZOOM_MAX);
        float elastic = (float)Math.pow(2.0D,
                LostTalesLotrMapGui.SMOOTH_ZOOM_MAX + 0.4F);
        assertEquals(
                LostTalesMapTerrainRenderer.sampleStep(
                        LostTalesMapTerrainRenderer
                                .stableLodBlockScale(settled)),
                LostTalesMapTerrainRenderer.sampleStep(
                        LostTalesMapTerrainRenderer
                                .stableLodBlockScale(elastic)));
    }

    @Test
    public void lodCellsNeverCrossAChunkBoundary() {
        assertEquals(3, LostTalesMapTerrainRenderer.cellExtent(12, 3));
        assertEquals(1, LostTalesMapTerrainRenderer.cellExtent(15, 3));
        assertEquals(4, LostTalesMapTerrainRenderer.cellExtent(12, 4));
    }

    @Test
    public void closeZoomReliefRemainsBoundedInGuiSpace() {
        assertTrue(LostTalesMapTerrainRenderer.TERRAIN_HEIGHT_SCALE > 0.0F);
        assertTrue(LostTalesMapTerrainRenderer.TERRAIN_HEIGHT_SCALE <= 0.2F);
        assertEquals(9.68F,
                LostTalesMapTerrainRenderer.projectedReliefMargin(
                        1.0F, 0.5F), 0.0001F);
        assertEquals(2.0F,
                LostTalesMapTerrainRenderer.projectedReliefMargin(
                        100.0F, 0.0F), 0.0001F);
    }

    @Test
    public void visibilityIncludesReliefWithoutBecomingUnbounded() {
        assertTrue(LostTalesMapTerrainRenderer.isTileVisible(
                90.0F, 90.0F, 16.0F, 12.0F,
                100.0F, 200.0F, 100.0F, 200.0F));
        assertTrue(LostTalesMapTerrainRenderer.isTileVisible(
                110.0F, 75.0F, 16.0F, 12.0F,
                100.0F, 200.0F, 100.0F, 200.0F));
        assertFalse(LostTalesMapTerrainRenderer.isTileVisible(
                -100.0F, -100.0F, 16.0F, 12.0F,
                100.0F, 200.0F, 100.0F, 200.0F));
    }

    @Test
    public void terrainColorShadingClampsChannels() {
        assertEquals(0x204080,
                LostTalesMapTerrainRenderer.shadeColor(0x204080, 1.0F));
        assertEquals(0xFFFFFF,
                LostTalesMapTerrainRenderer.shadeColor(0xFFFFFF, 4.0F));
        assertEquals(0,
                LostTalesMapTerrainRenderer.shadeColor(0xFFFFFF, -1.0F));
        assertTrue("north-west exposure should brighten the surface",
                LostTalesMapTerrainRenderer.topShade(80, 76, 76) > 1.0F);
        assertTrue("higher north-west terrain should shade the surface",
                LostTalesMapTerrainRenderer.topShade(76, 80, 80) < 0.9F);
    }

    @Test
    public void sepiaPaletteIsMutedAndDeterministic() {
        assertEquals(0x524534,
                LostTalesMapTerrainRenderer.sepiaColor(0xFF0000));
        assertEquals(0,
                LostTalesMapTerrainRenderer.sepiaColor(0));
    }

    @Test
    public void newlyPreparedMeshesFadeWithoutOvershoot() {
        long start = 1000000000L;
        assertEquals(0.0F,
                LostTalesMapTerrainRenderer.meshAvailabilityAlpha(
                        start, start), 0.0F);
        assertEquals(0.5F,
                LostTalesMapTerrainRenderer.meshAvailabilityAlpha(
                        start,
                        start + LostTalesMapTerrainRenderer.MESH_FADE_NANOS
                                / 2L), 0.0001F);
        assertEquals(1.0F,
                LostTalesMapTerrainRenderer.meshAvailabilityAlpha(
                        start,
                        start + LostTalesMapTerrainRenderer.MESH_FADE_NANOS),
                0.0F);
    }
}
