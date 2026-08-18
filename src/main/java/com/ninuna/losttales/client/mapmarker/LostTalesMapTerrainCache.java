package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.block.material.MapColor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.init.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;

/**
 * Bounded surface snapshots of terrain the multiplayer client already knows.
 *
 * <p>Snapshots are taken on the client thread and contain only immutable
 * primitive arrays. The collector checks {@link IChunkProvider#chunkExists}
 * before asking for a chunk; it never loads, generates, or requests terrain.
 * The cache is session-only and is discarded when the client world changes.
 * Surface height, colour, block ID, and metadata are retained so a later
 * rendering stage can build textured reusable meshes without reading a live
 * world during the render pass.</p>
 */
@SideOnly(Side.CLIENT)
public final class LostTalesMapTerrainCache {
    static final int TILE_WIDTH = 16;
    static final int TILE_SAMPLE_COUNT = TILE_WIDTH * TILE_WIDTH;
    static final int MAX_CACHED_TILES = 2048;
    /** A complete 33-chunk-diameter circle fits below the hard cache limit. */
    static final int MAX_CAPTURE_RADIUS_CHUNKS = 16;
    /** Cheap chunk-existence probes do not consume the snapshot budget. */
    static final int MAX_CAPTURE_CANDIDATES_PER_MAP_TICK = 64;
    static final int CAPTURES_PER_MAP_TICK = 4;
    /** Old snapshots become eligible during a later bounded queue pass. */
    static final long REFRESH_INTERVAL_TICKS = 1200L;

    private static final BoundedTileStore TILES =
            new BoundedTileStore(MAX_CACHED_TILES);
    private static final ArrayDeque<Long> CAPTURE_QUEUE =
            new ArrayDeque<Long>();

    private static WorldClient activeWorld;
    private static int activeDimension = Integer.MIN_VALUE;
    private static int queuedCenterX = Integer.MIN_VALUE;
    private static int queuedCenterZ = Integer.MIN_VALUE;
    private static int queuedRadius = -1;
    private static boolean snapshotFailureLogged;

    private LostTalesMapTerrainCache() {}

    /** Performs a small, fixed amount of main-thread snapshot work. */
    static void update(Minecraft minecraft, float zoomExp) {
        if (!LostTalesMapTerrainTransition.shouldPrepareTerrain(zoomExp)
                || minecraft == null || minecraft.theWorld == null
                || minecraft.thePlayer == null) {
            return;
        }
        WorldClient world = minecraft.theWorld;
        int dimension = world.provider.dimensionId;
        if (activeWorld != world || activeDimension != dimension) {
            resetForWorld(world, dimension);
        }

        int centerX = floorToChunk(minecraft.thePlayer.posX);
        int centerZ = floorToChunk(minecraft.thePlayer.posZ);
        int configuredRadius = minecraft.gameSettings == null
                ? 8 : minecraft.gameSettings.renderDistanceChunks + 1;
        int radius = Math.max(1, Math.min(
                MAX_CAPTURE_RADIUS_CHUNKS, configuredRadius));
        if (CAPTURE_QUEUE.isEmpty()
                || queuedCenterX != centerX || queuedCenterZ != centerZ
                || queuedRadius != radius) {
            rebuildCaptureQueue(centerX, centerZ, radius);
        }

        IChunkProvider provider = world.getChunkProvider();
        int candidates = 0;
        int captured = 0;
        while (candidates < MAX_CAPTURE_CANDIDATES_PER_MAP_TICK
                && captured < CAPTURES_PER_MAP_TICK
                && !CAPTURE_QUEUE.isEmpty()) {
            candidates++;
            long packed = CAPTURE_QUEUE.removeFirst().longValue();
            int chunkX = unpackX(packed);
            int chunkZ = unpackZ(packed);
            if (provider == null || !provider.chunkExists(chunkX, chunkZ)) {
                continue;
            }
            Chunk chunk = world.getChunkFromChunkCoords(chunkX, chunkZ);
            if (chunk == null || chunk.isEmpty()
                    || !chunk.isTerrainPopulated) {
                continue;
            }
            long worldTime = world.getTotalWorldTime();
            TerrainTile existing = TILES.get(
                    dimension, chunkX, chunkZ);
            if (existing != null && !shouldRefresh(
                    existing.capturedWorldTime, worldTime)) {
                continue;
            }
            TerrainTile tile = snapshot(chunk, dimension,
                    worldTime);
            if (tile != null) {
                captured++;
                if (existing != null && existing.sameSurfaceAs(tile)) {
                    // Keep the existing immutable arrays and, importantly,
                    // their GPU mesh identity when the chunk did not change.
                    existing.markRefreshed(worldTime);
                } else {
                    TILES.put(tile);
                }
            }
        }
    }

    /** Returns a session snapshot only when it belongs to the named dimension. */
    static TerrainTile get(int dimension, int chunkX, int chunkZ) {
        return TILES.get(dimension, chunkX, chunkZ);
    }

    /**
     * Copies cached snapshots without allocating a collection in the render
     * loop. The returned references remain valid because a snapshot is
     * immutable and capture and rendering both run on the client thread.
     */
    static int copyTiles(int dimension, TerrainTile[] destination) {
        return TILES.copyTo(dimension, destination);
    }

    /** Drops all terrain and scan state at world or connection boundaries. */
    public static void clear() {
        activeWorld = null;
        activeDimension = Integer.MIN_VALUE;
        queuedCenterX = Integer.MIN_VALUE;
        queuedCenterZ = Integer.MIN_VALUE;
        queuedRadius = -1;
        snapshotFailureLogged = false;
        CAPTURE_QUEUE.clear();
        TILES.clear();
    }

    private static void resetForWorld(WorldClient world, int dimension) {
        clear();
        activeWorld = world;
        activeDimension = dimension;
    }

    /** Centre first, then circularly clipped rings so nearby data is ready first. */
    private static void rebuildCaptureQueue(
            int centerX, int centerZ, int radius) {
        CAPTURE_QUEUE.clear();
        CAPTURE_QUEUE.add(Long.valueOf(pack(centerX, centerZ)));
        for (int ring = 1; ring <= radius; ring++) {
            for (int offsetX = -ring; offsetX <= ring; offsetX++) {
                enqueueIfInsideCircle(centerX, centerZ,
                        offsetX, -ring, radius);
                enqueueIfInsideCircle(centerX, centerZ,
                        offsetX, ring, radius);
            }
            for (int offsetZ = -ring + 1;
                 offsetZ <= ring - 1; offsetZ++) {
                enqueueIfInsideCircle(centerX, centerZ,
                        -ring, offsetZ, radius);
                enqueueIfInsideCircle(centerX, centerZ,
                        ring, offsetZ, radius);
            }
        }
        queuedCenterX = centerX;
        queuedCenterZ = centerZ;
        queuedRadius = radius;
    }

    private static void enqueueIfInsideCircle(
            int centerX, int centerZ,
            int offsetX, int offsetZ, int radius) {
        if (isInsideCaptureCircle(offsetX, offsetZ, radius)) {
            CAPTURE_QUEUE.add(Long.valueOf(pack(
                    centerX + offsetX, centerZ + offsetZ)));
        }
    }

    static boolean isInsideCaptureCircle(
            int offsetX, int offsetZ, int radius) {
        if (radius < 0) {
            return false;
        }
        long x = offsetX;
        long z = offsetZ;
        long r = radius;
        return x * x + z * z <= r * r;
    }

    private static TerrainTile snapshot(
            Chunk chunk, int dimension, long worldTime) {
        short[] heights = new short[TILE_SAMPLE_COUNT];
        int[] colors = new int[TILE_SAMPLE_COUNT];
        int[] renderColors = new int[TILE_SAMPLE_COUNT];
        short[] blockIds = new short[TILE_SAMPLE_COUNT];
        byte[] metadataValues = new byte[TILE_SAMPLE_COUNT];
        try {
            for (int localZ = 0; localZ < TILE_WIDTH; localZ++) {
                for (int localX = 0; localX < TILE_WIDTH; localX++) {
                    int index = localZ * TILE_WIDTH + localX;
                    int y = Math.max(0, Math.min(255,
                            chunk.getHeightValue(localX, localZ) - 1));
                    Block block = chunk.getBlock(localX, y, localZ);
                    while (y > 0 && (block == null || block == Blocks.air)) {
                        y--;
                        block = chunk.getBlock(localX, y, localZ);
                    }
                    int metadata = block == null
                            ? 0 : chunk.getBlockMetadata(localX, y, localZ);
                    MapColor mapColor = block == null
                            ? null : block.getMapColor(metadata);
                    heights[index] = (short)y;
                    colors[index] = mapColor == null
                            ? 0 : mapColor.colorValue & 0x00FFFFFF;
                    renderColors[index] = renderColor(block, metadata);
                    blockIds[index] = (short)(block == null
                            ? 0 : Block.getIdFromBlock(block));
                    metadataValues[index] = (byte)metadata;
                }
            }
            return new TerrainTile(dimension,
                    chunk.xPosition, chunk.zPosition,
                    heights, colors, renderColors,
                    blockIds, metadataValues, worldTime);
        } catch (Throwable failure) {
            logSnapshotFailureOnce(chunk, failure);
            return null;
        }
    }

    private static int renderColor(Block block, int metadata) {
        if (block == null) {
            return 0x00FFFFFF;
        }
        try {
            return block.getRenderColor(metadata) & 0x00FFFFFF;
        } catch (Throwable ignored) {
            // Some custom blocks only implement their world-aware colour
            // path. White preserves their authored texture without letting
            // one compatibility failure discard an otherwise valid tile.
            return 0x00FFFFFF;
        }
    }

    private static synchronized void logSnapshotFailureOnce(
            Chunk chunk, Throwable failure) {
        if (snapshotFailureLogged) {
            return;
        }
        snapshotFailureLogged = true;
        FMLLog.warning(
                "[%s] Could not snapshot client terrain chunk %d,%d; "
                        + "the close map will retain its normal background "
                        + "for unavailable terrain (%s)",
                LostTalesMetaData.MOD_ID,
                chunk == null ? 0 : chunk.xPosition,
                chunk == null ? 0 : chunk.zPosition,
                failure == null ? "unknown error" : failure.toString());
    }

    private static int floorToChunk(double coordinate) {
        int block = (int)Math.floor(coordinate);
        return block >> 4;
    }

    static boolean shouldRefresh(long capturedWorldTime, long worldTime) {
        long age = worldTime - capturedWorldTime;
        return age < 0L || age >= REFRESH_INTERVAL_TICKS;
    }

    private static long pack(int chunkX, int chunkZ) {
        return ((long)chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackX(long packed) {
        return (int)(packed >> 32);
    }

    private static int unpackZ(long packed) {
        return (int)packed;
    }

    static final class TerrainTile {
        final int dimension;
        final int chunkX;
        final int chunkZ;
        private final short[] heights;
        private final int[] colors;
        private final int[] renderColors;
        private final short[] blockIds;
        private final byte[] metadataValues;
        private long capturedWorldTime;

        TerrainTile(int dimension, int chunkX, int chunkZ,
                    short[] heights, int[] colors, int[] renderColors,
                    short[] blockIds, byte[] metadataValues,
                    long capturedWorldTime) {
            if (heights == null || heights.length != TILE_SAMPLE_COUNT
                    || colors == null || colors.length != TILE_SAMPLE_COUNT
                    || renderColors == null
                    || renderColors.length != TILE_SAMPLE_COUNT
                    || blockIds == null
                    || blockIds.length != TILE_SAMPLE_COUNT
                    || metadataValues == null
                    || metadataValues.length != TILE_SAMPLE_COUNT) {
                throw new IllegalArgumentException(
                        "A terrain tile must contain one sample per column");
            }
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.heights = heights;
            this.colors = colors;
            this.renderColors = renderColors;
            this.blockIds = blockIds;
            this.metadataValues = metadataValues;
            this.capturedWorldTime = capturedWorldTime;
        }

        int heightAt(int localX, int localZ) {
            return this.heights[localZ * TILE_WIDTH + localX] & 0xFFFF;
        }

        int colorAt(int localX, int localZ) {
            return this.colors[localZ * TILE_WIDTH + localX];
        }

        int renderColorAt(int localX, int localZ) {
            return this.renderColors[localZ * TILE_WIDTH + localX];
        }

        int blockIdAt(int localX, int localZ) {
            return this.blockIds[localZ * TILE_WIDTH + localX] & 0xFFFF;
        }

        int metadataAt(int localX, int localZ) {
            return this.metadataValues[
                    localZ * TILE_WIDTH + localX] & 0xFF;
        }

        boolean sameSurfaceAs(TerrainTile other) {
            return other != null
                    && Arrays.equals(this.heights, other.heights)
                    && Arrays.equals(this.colors, other.colors)
                    && Arrays.equals(this.renderColors, other.renderColors)
                    && Arrays.equals(this.blockIds, other.blockIds)
                    && Arrays.equals(this.metadataValues,
                            other.metadataValues);
        }

        void markRefreshed(long worldTime) {
            this.capturedWorldTime = worldTime;
        }
    }

    /** Access-ordered so rendering a tile also protects it from eviction. */
    static final class BoundedTileStore {
        private final int maximumTiles;
        private final LinkedHashMap<TileKey, TerrainTile> tiles;
        /* Reused for client-thread lookups; it is never inserted in tiles. */
        private final TileKey lookupKey = new TileKey(0, 0, 0);

        BoundedTileStore(int maximumTiles) {
            if (maximumTiles <= 0) {
                throw new IllegalArgumentException(
                        "Terrain tile limit must be positive");
            }
            this.maximumTiles = maximumTiles;
            this.tiles = new LinkedHashMap<TileKey, TerrainTile>(
                    16, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(
                        Map.Entry<TileKey, TerrainTile> eldest) {
                    return size() > BoundedTileStore.this.maximumTiles;
                }
            };
        }

        void put(TerrainTile tile) {
            if (tile != null) {
                this.tiles.put(new TileKey(
                        tile.dimension, tile.chunkX, tile.chunkZ), tile);
            }
        }

        TerrainTile get(int dimension, int chunkX, int chunkZ) {
            this.lookupKey.set(dimension, chunkX, chunkZ);
            return this.tiles.get(this.lookupKey);
        }

        boolean contains(int dimension, int chunkX, int chunkZ) {
            this.lookupKey.set(dimension, chunkX, chunkZ);
            return this.tiles.containsKey(this.lookupKey);
        }

        int copyTo(int dimension, TerrainTile[] destination) {
            if (destination == null || destination.length == 0) {
                return 0;
            }
            int copied = 0;
            for (TerrainTile tile : this.tiles.values()) {
                if (tile.dimension == dimension) {
                    destination[copied++] = tile;
                    if (copied >= destination.length) {
                        break;
                    }
                }
            }
            return copied;
        }

        int size() {
            return this.tiles.size();
        }

        void clear() {
            this.tiles.clear();
        }
    }

    private static final class TileKey {
        private int dimension;
        private int chunkX;
        private int chunkZ;

        private TileKey(int dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        private void set(int dimension, int chunkX, int chunkZ) {
            this.dimension = dimension;
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof TileKey)) {
                return false;
            }
            TileKey key = (TileKey)other;
            return this.dimension == key.dimension
                    && this.chunkX == key.chunkX
                    && this.chunkZ == key.chunkZ;
        }

        @Override
        public int hashCode() {
            int result = this.dimension;
            result = 31 * result + this.chunkX;
            result = 31 * result + this.chunkZ;
            return result;
        }
    }
}
