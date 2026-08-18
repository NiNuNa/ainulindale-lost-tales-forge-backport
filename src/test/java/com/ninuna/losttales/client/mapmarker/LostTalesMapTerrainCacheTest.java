package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapTerrainCacheTest {
    @Test
    public void tileKeysKeepDimensionsSeparate() {
        LostTalesMapTerrainCache.BoundedTileStore store =
                new LostTalesMapTerrainCache.BoundedTileStore(4);
        store.put(tile(0, 3, -2));
        store.put(tile(100, 3, -2));

        assertNotNull(store.get(0, 3, -2));
        assertNotNull(store.get(100, 3, -2));
        assertEquals(2, store.size());
    }

    @Test
    public void leastRecentlyUsedTileIsEvictedAtTheHardLimit() {
        LostTalesMapTerrainCache.BoundedTileStore store =
                new LostTalesMapTerrainCache.BoundedTileStore(2);
        store.put(tile(100, 1, 1));
        store.put(tile(100, 2, 2));
        assertNotNull(store.get(100, 1, 1));

        store.put(tile(100, 3, 3));

        assertNotNull(store.get(100, 1, 1));
        assertNull(store.get(100, 2, 2));
        assertNotNull(store.get(100, 3, 3));
        assertEquals(2, store.size());
    }

    @Test
    public void copyIsBoundedAndKeepsDimensionsSeparate() {
        LostTalesMapTerrainCache.BoundedTileStore store =
                new LostTalesMapTerrainCache.BoundedTileStore(4);
        LostTalesMapTerrainCache.TerrainTile first = tile(0, 1, 1);
        LostTalesMapTerrainCache.TerrainTile second = tile(0, 2, 2);
        store.put(first);
        store.put(tile(100, 9, 9));
        store.put(second);

        LostTalesMapTerrainCache.TerrainTile[] one =
                new LostTalesMapTerrainCache.TerrainTile[1];
        assertEquals(1, store.copyTo(0, one));
        assertEquals(0, one[0].dimension);

        LostTalesMapTerrainCache.TerrainTile[] all =
                new LostTalesMapTerrainCache.TerrainTile[4];
        assertEquals(2, store.copyTo(0, all));
        assertEquals(0, store.copyTo(-1, all));
    }

    @Test
    public void captureCircleFitsUnderTheCacheLimit() {
        int radius = LostTalesMapTerrainCache.MAX_CAPTURE_RADIUS_CHUNKS;
        int circleTiles = 0;
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                if (LostTalesMapTerrainCache.isInsideCaptureCircle(
                        x, z, radius)) {
                    circleTiles++;
                }
            }
        }
        assertTrue(circleTiles <= LostTalesMapTerrainCache.MAX_CACHED_TILES);
        assertTrue("one fresh circle must fit in the GPU mesh cache",
                circleTiles <= LostTalesMapTerrainRenderer.MAX_MESHES);
        assertTrue(LostTalesMapTerrainCache.MAX_CAPTURE_CANDIDATES_PER_MAP_TICK
                > LostTalesMapTerrainCache.CAPTURES_PER_MAP_TICK);
    }

    @Test
    public void captureBoundaryIsCircularAndInclusive() {
        assertTrue(LostTalesMapTerrainCache.isInsideCaptureCircle(0, 0, 4));
        assertTrue(LostTalesMapTerrainCache.isInsideCaptureCircle(4, 0, 4));
        assertTrue(LostTalesMapTerrainCache.isInsideCaptureCircle(-3, 2, 4));
        assertFalse(LostTalesMapTerrainCache.isInsideCaptureCircle(4, 4, 4));
        assertFalse(LostTalesMapTerrainCache.isInsideCaptureCircle(0, 0, -1));
    }

    @Test
    public void snapshotsRefreshOnlyAfterTheBoundedInterval() {
        long interval = LostTalesMapTerrainCache.REFRESH_INTERVAL_TICKS;
        assertFalse(LostTalesMapTerrainCache.shouldRefresh(
                1000L, 1000L + interval - 1L));
        assertTrue(LostTalesMapTerrainCache.shouldRefresh(
                1000L, 1000L + interval));
        assertTrue(LostTalesMapTerrainCache.shouldRefresh(1000L, 999L));
    }

    @Test
    public void surfaceMaterialDataRemainsAttachedToItsColumn() {
        short[] heights =
                new short[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT];
        int[] colors =
                new int[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT];
        int[] renderColors =
                new int[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT];
        short[] blockIds =
                new short[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT];
        byte[] metadata =
                new byte[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT];
        int index = 7 * LostTalesMapTerrainCache.TILE_WIDTH + 5;
        heights[index] = 91;
        colors[index] = 0x345678;
        renderColors[index] = 0x89ABCD;
        blockIds[index] = 321;
        metadata[index] = 12;
        LostTalesMapTerrainCache.TerrainTile tile =
                new LostTalesMapTerrainCache.TerrainTile(
                        100, 2, 3, heights, colors, renderColors,
                        blockIds, metadata, 40L);

        assertEquals(91, tile.heightAt(5, 7));
        assertEquals(0x345678, tile.colorAt(5, 7));
        assertEquals(0x89ABCD, tile.renderColorAt(5, 7));
        assertEquals(321, tile.blockIdAt(5, 7));
        assertEquals(12, tile.metadataAt(5, 7));
    }

    @Test
    public void identicalRefreshesKeepTheirMeshIdentity() {
        LostTalesMapTerrainCache.TerrainTile first =
                tileWithHeight(100, 2, 3, 81, 10L);
        LostTalesMapTerrainCache.TerrainTile unchanged =
                tileWithHeight(100, 2, 3, 81, 20L);
        LostTalesMapTerrainCache.TerrainTile changed =
                tileWithHeight(100, 2, 3, 82, 20L);

        assertTrue(first.sameSurfaceAs(unchanged));
        assertFalse(first.sameSurfaceAs(changed));
    }

    private static LostTalesMapTerrainCache.TerrainTile tile(
            int dimension, int chunkX, int chunkZ) {
        return new LostTalesMapTerrainCache.TerrainTile(
                dimension, chunkX, chunkZ,
                new short[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new int[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new int[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new short[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new byte[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                0L);
    }

    private static LostTalesMapTerrainCache.TerrainTile tileWithHeight(
            int dimension, int chunkX, int chunkZ,
            int height, long capturedWorldTime) {
        short[] heights =
                new short[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT];
        heights[0] = (short)height;
        return new LostTalesMapTerrainCache.TerrainTile(
                dimension, chunkX, chunkZ, heights,
                new int[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new int[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new short[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                new byte[LostTalesMapTerrainCache.TILE_SAMPLE_COUNT],
                capturedWorldTime);
    }
}
