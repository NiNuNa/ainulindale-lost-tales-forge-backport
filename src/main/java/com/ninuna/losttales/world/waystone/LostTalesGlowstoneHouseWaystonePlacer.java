package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.LostTalesWaystoneLifecycleService;
import com.ninuna.losttales.block.custom.LostTalesBlockWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

/**
 * Validates the complete 4x5 scan and terrain before mutating any blocks.
 * Configured marker X/Z identify the waystone anchor inside the house.
 */
public final class LostTalesGlowstoneHouseWaystonePlacer
        implements LostTalesWaystoneStructurePlacer {
    public static final String ID = "losttales:glowstone_house";
    private static final String RESOURCE =
            "/assets/losttales/strscans/glowstoneHouse.strscan";
    private static final int MIN_X = 0;
    private static final int MAX_X = 3;
    private static final int MIN_Z = -4;
    private static final int MAX_Z = 0;
    private static final int CLEARANCE_HEIGHT = 5;
    /*
     * Middle-earth terrain is deliberately rolling. A three-block limit made
     * otherwise safe marker sites fail almost everywhere, so permit a modest
     * glowstone foundation while still rejecting cliffs and ravines.
     */
    private static final int MAX_FOUNDATION_DEPTH = 8;
    private static final int MAX_SITE_SEARCH_RADIUS = 16;
    private static final int[][] SITE_SEARCH_OFFSETS =
            createSiteSearchOffsets(MAX_SITE_SEARCH_RADIUS);
    private static final int ANCHOR_X = 1;
    private static final int ANCHOR_Y = 1;
    private static final int ANCHOR_Z = -2;

    private final List<ScanBlock> scan;

    public LostTalesGlowstoneHouseWaystonePlacer() {
        this.scan = Collections.unmodifiableList(loadScan());
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public LostTalesWaystonePlacementResult place(
            final WorldServer world,
            final LostTalesMapMarkerWorldData data,
            final LostTalesMapMarkerRecord record) {
        final int requestedX = floor(record.getX());
        final int requestedZ = floor(record.getZ());
        if (record.hasExplicitY()) {
            return placeAt(world, data, record,
                    requestedX, requestedZ);
        }
        if (!world.getChunkProvider().chunkExists(
                requestedX >> 4, requestedZ >> 4)) {
            return LostTalesWaystonePlacementResult.deferred(
                    "marker_chunk_not_loaded");
        }
        int requestedSupportY =
                findSupportY(world, requestedX, requestedZ);
        if (requestedSupportY >= 0
                && LostTalesWaystonePermissionPolicy
                        .isProtectedFromWorldGeneration(
                                world, requestedX,
                                requestedSupportY + 1,
                                requestedZ)) {
            return LostTalesWaystonePlacementResult.blocked(
                    "lotr_banner_protected");
        }

        boolean deferred = false;
        for (int[] offset : SITE_SEARCH_OFFSETS) {
            LostTalesWaystonePlacementResult result =
                    placeAt(world, data, record,
                            requestedX + offset[0],
                            requestedZ + offset[1]);
            if (result.getStatus()
                    == LostTalesWaystonePlacementResult
                            .Status.SUCCESS) {
                return result;
            }
            if (result.getStatus()
                    == LostTalesWaystonePlacementResult
                            .Status.DEFERRED) {
                deferred = true;
                continue;
            }
            if (offset[0] == 0 && offset[1] == 0
                    && "lotr_banner_protected".equals(
                            result.getReason())) {
                return result;
            }
            if (!isSiteSpecificFailure(result.getReason())) {
                return result;
            }
        }
        return deferred
                ? LostTalesWaystonePlacementResult.deferred(
                        "nearby_site_chunks_not_loaded")
                : LostTalesWaystonePlacementResult.blocked(
                        "no_safe_site_near_marker");
    }

    static int[][] getSiteSearchOffsetsForTest() {
        int[][] copy = new int[SITE_SEARCH_OFFSETS.length][2];
        for (int index = 0;
             index < SITE_SEARCH_OFFSETS.length; index++) {
            copy[index][0] = SITE_SEARCH_OFFSETS[index][0];
            copy[index][1] = SITE_SEARCH_OFFSETS[index][1];
        }
        return copy;
    }

    private static int[][] createSiteSearchOffsets(int maximumRadius) {
        int radiusLimit = Math.max(0, maximumRadius);
        int side = radiusLimit * 2 + 1;
        int[][] offsets = new int[side * side][2];
        int index = 0;
        for (int radius = 0; radius <= radiusLimit; radius++) {
            for (int offsetX = -radius;
                 offsetX <= radius; offsetX++) {
                for (int offsetZ = -radius;
                     offsetZ <= radius; offsetZ++) {
                    if (radius > 0
                            && Math.abs(offsetX) != radius
                            && Math.abs(offsetZ) != radius) {
                        continue;
                    }
                    offsets[index][0] = offsetX;
                    offsets[index][1] = offsetZ;
                    index++;
                }
            }
        }
        if (index != offsets.length) {
            throw new IllegalStateException(
                    "waystone site-search offset count is invalid");
        }
        return offsets;
    }

    private LostTalesWaystonePlacementResult placeAt(
            final WorldServer world,
            final LostTalesMapMarkerWorldData data,
            final LostTalesMapMarkerRecord record,
            final int anchorX, final int anchorZ) {
        final int originX = anchorX - ANCHOR_X;
        final int originZ = anchorZ - ANCHOR_Z;
        if (!areFootprintChunksLoaded(world, originX, originZ)) {
            return LostTalesWaystonePlacementResult.deferred(
                    "required_chunks_not_loaded");
        }

        int highestSupportY = Integer.MIN_VALUE;
        int lowestSupportY = Integer.MAX_VALUE;
        final int[][] supportYByColumn =
                new int[MAX_X - MIN_X + 1][MAX_Z - MIN_Z + 1];
        for (int localX = MIN_X; localX <= MAX_X; localX++) {
            for (int localZ = MIN_Z; localZ <= MAX_Z; localZ++) {
                int worldX = originX + localX;
                int worldZ = originZ + localZ;
                int candidateSupportY = findSupportY(
                        world, worldX, worldZ);
                if (!isSafeSupport(
                        world, worldX, candidateSupportY, worldZ)) {
                    return LostTalesWaystonePlacementResult.blocked(
                            "unsuitable_surface");
                }
                supportYByColumn[localX - MIN_X][localZ - MIN_Z] =
                        candidateSupportY;
                highestSupportY = Math.max(
                        highestSupportY, candidateSupportY);
                lowestSupportY = Math.min(
                        lowestSupportY, candidateSupportY);
            }
        }
        final int waystoneY = record.hasExplicitY()
                ? floor(record.getY())
                : highestSupportY + 1 + ANCHOR_Y;
        final int originY = waystoneY - ANCHOR_Y;
        final int foundationTopY = originY - 1;
        if (highestSupportY > foundationTopY
                || foundationTopY - lowestSupportY
                        > MAX_FOUNDATION_DEPTH) {
            return LostTalesWaystonePlacementResult.blocked(
                    record.hasExplicitY()
                            ? "configured_height_unsupported"
                            : "surface_too_steep");
        }
        if (originY < 1
                || originY + CLEARANCE_HEIGHT >= world.getActualHeight()) {
            return LostTalesWaystonePlacementResult.blocked(
                    "height_out_of_bounds");
        }
        if (isFootprintProtected(
                world, originX, waystoneY, originZ)) {
            return LostTalesWaystonePlacementResult.blocked(
                    "lotr_banner_protected");
        }
        if (data.findByLinkedPosition(
                world.provider.dimensionId,
                anchorX, waystoneY, anchorZ) != null) {
            return LostTalesWaystonePlacementResult.blocked(
                    "waystone_position_already_linked");
        }

        final LinkedHashMap<String, PlannedBlock> plan =
                new LinkedHashMap<String, PlannedBlock>();
        for (int localX = MIN_X; localX <= MAX_X; localX++) {
            for (int localZ = MIN_Z; localZ <= MAX_Z; localZ++) {
                int x = originX + localX;
                int z = originZ + localZ;
                int columnSupportY =
                        supportYByColumn[localX - MIN_X][localZ - MIN_Z];
                for (int y = columnSupportY + 1;
                     y <= foundationTopY; y++) {
                    if (!isReplaceable(world, x, y, z)) {
                        return LostTalesWaystonePlacementResult.blocked(
                                "foundation_obstructed");
                    }
                    put(plan, x, y, z, Blocks.glowstone, 0);
                }
                for (int localY = 0;
                     localY <= CLEARANCE_HEIGHT; localY++) {
                    int y = originY + localY;
                    if (!isReplaceable(world, x, y, z)) {
                        return LostTalesWaystonePlacementResult.blocked(
                                "footprint_obstructed");
                    }
                    put(plan, x, y, z, Blocks.air, 0);
                }
            }
        }
        for (ScanBlock scanBlock : this.scan) {
            put(plan,
                    originX + scanBlock.x,
                    originY + scanBlock.y,
                    originZ + scanBlock.z,
                    scanBlock.block, scanBlock.metadata);
        }
        put(plan, anchorX, waystoneY, anchorZ,
                ELostTalesBlock.WAYSTONE.getBlock(), 0);
        put(plan, anchorX, waystoneY + 1, anchorZ,
                ELostTalesBlock.WAYSTONE.getBlock(),
                LostTalesBlockWaystone.UPPER_METADATA);

        final ArrayList<OriginalBlock> originals =
                snapshot(world, plan.values());
        final boolean[] applied = {true};
        LostTalesWaystoneLifecycleService.runPreservingMarker(
                new Runnable() {
                    @Override
                    public void run() {
                        for (PlannedBlock target : plan.values()) {
                            if (world.getBlock(
                                    target.x, target.y, target.z)
                                        == target.block
                                    && world.getBlockMetadata(
                                    target.x, target.y, target.z)
                                        == target.metadata) {
                                continue;
                            }
                            if (!world.setBlock(
                                    target.x, target.y, target.z,
                                    target.block, target.metadata, 2)) {
                                applied[0] = false;
                                break;
                            }
                        }
                    }
                });
        if (!applied[0]) {
            rollback(world, originals);
            return LostTalesWaystonePlacementResult.blocked(
                    "world_mutation_failed");
        }

        TileEntity tile = world.getTileEntity(
                anchorX, waystoneY, anchorZ);
        if (!(tile instanceof LostTalesTileEntityWaystone)) {
            rollback(world, originals);
            return LostTalesWaystonePlacementResult.blocked(
                    "waystone_tile_missing");
        }
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord linked = record.withLink(
                world.provider.dimensionId,
                anchorX, waystoneY, anchorZ, token);
        try {
            ((LostTalesTileEntityWaystone)tile).linkTo(linked);
            data.saveRecord(linked);
            LostTalesMapMarkerSyncManager.syncAll();
        } catch (RuntimeException exception) {
            ((LostTalesTileEntityWaystone)tile).clearLink();
            rollback(world, originals);
            return LostTalesWaystonePlacementResult.blocked(
                    "link_commit_failed");
        }
        return LostTalesWaystonePlacementResult.success();
    }

    private static boolean isSiteSpecificFailure(String reason) {
        return "unsuitable_surface".equals(reason)
                || "surface_too_steep".equals(reason)
                || "footprint_obstructed".equals(reason)
                || "foundation_obstructed".equals(reason)
                || "height_out_of_bounds".equals(reason)
                || "waystone_position_already_linked".equals(reason)
                || "lotr_banner_protected".equals(reason);
    }

    private static boolean isFootprintProtected(
            WorldServer world, int originX,
            int waystoneY, int originZ) {
        for (int localX = MIN_X; localX <= MAX_X; localX++) {
            for (int localZ = MIN_Z; localZ <= MAX_Z; localZ++) {
                if (LostTalesWaystonePermissionPolicy
                        .isProtectedFromWorldGeneration(
                                world, originX + localX,
                                waystoneY, originZ + localZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean areFootprintChunksLoaded(
            WorldServer world, int originX, int originZ) {
        int minChunkX = (originX + MIN_X) >> 4;
        int maxChunkX = (originX + MAX_X) >> 4;
        int minChunkZ = (originZ + MIN_Z) >> 4;
        int maxChunkZ = (originZ + MAX_Z) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.getChunkProvider().chunkExists(
                        chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isSafeSupport(
            WorldServer world, int x, int y, int z) {
        if (y < 0 || y >= world.getActualHeight()
                || world.getTileEntity(x, y, z) != null) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        return block != null && block != Blocks.air
                && block.getMaterial().isSolid()
                && !block.getMaterial().isLiquid()
                && !block.isLeaves(world, x, y, z)
                && !block.isReplaceable(world, x, y, z);
    }

    private static int findSupportY(
            WorldServer world, int x, int z) {
        int y = world.getTopSolidOrLiquidBlock(x, z) - 1;
        while (y >= 0) {
            Block block = world.getBlock(x, y, z);
            if (block == null || block == Blocks.air
                    || block.isLeaves(world, x, y, z)
                    || (!block.getMaterial().isLiquid()
                        && block.isReplaceable(world, x, y, z))) {
                y--;
                continue;
            }
            return y;
        }
        return -1;
    }

    private static boolean isReplaceable(
            WorldServer world, int x, int y, int z) {
        if (world.getTileEntity(x, y, z) != null) {
            return false;
        }
        Block block = world.getBlock(x, y, z);
        if (block == null || block == Blocks.air) {
            return true;
        }
        return !block.getMaterial().isLiquid()
                && block.isReplaceable(world, x, y, z);
    }

    private static void put(
            Map<String, PlannedBlock> plan,
            int x, int y, int z, Block block, int metadata) {
        plan.put(key(x, y, z),
                new PlannedBlock(x, y, z, block, metadata));
    }

    private static ArrayList<OriginalBlock> snapshot(
            WorldServer world, Iterable<PlannedBlock> targets) {
        ArrayList<OriginalBlock> originals =
                new ArrayList<OriginalBlock>();
        for (PlannedBlock target : targets) {
            originals.add(new OriginalBlock(
                    target.x, target.y, target.z,
                    world.getBlock(target.x, target.y, target.z),
                    world.getBlockMetadata(
                            target.x, target.y, target.z)));
        }
        return originals;
    }

    private static void rollback(
            final WorldServer world,
            final List<OriginalBlock> originals) {
        LostTalesWaystoneLifecycleService.runPreservingMarker(
                new Runnable() {
                    @Override
                    public void run() {
                        for (int index = originals.size() - 1;
                             index >= 0; index--) {
                            OriginalBlock original = originals.get(index);
                            world.setBlock(
                                    original.x, original.y, original.z,
                                    original.block, original.metadata, 2);
                        }
                    }
                });
    }

    private static ArrayList<ScanBlock> loadScan() {
        InputStream stream =
                LostTalesGlowstoneHouseWaystonePlacer.class
                        .getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException(
                    "missing structure scan " + RESOURCE);
        }
        ArrayList<ScanBlock> blocks = new ArrayList<ScanBlock>();
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(
                    new InputStreamReader(stream, "UTF-8"));
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.length() == 0 || trimmed.startsWith("#")) {
                    continue;
                }
                String[] parts = trimmed.split("\\.");
                if (parts.length < 5) {
                    throw new IllegalArgumentException(
                            "malformed scan line " + lineNumber);
                }
                int x = Integer.parseInt(parts[0]);
                int y = Integer.parseInt(parts[1]);
                int z = Integer.parseInt(parts[2]);
                int metadata = Integer.parseInt(parts[4]);
                String blockName = parts[3].replace("\"", "");
                Block block = resolveBlock(blockName);
                if (x < MIN_X || x > MAX_X
                        || z < MIN_Z || z > MAX_Z
                        || y < 0 || y > CLEARANCE_HEIGHT
                        || metadata < 0 || metadata > 15
                        || block == null) {
                    throw new IllegalArgumentException(
                            "invalid scan line " + lineNumber);
                }
                blocks.add(new ScanBlock(
                        x, y, z, block, metadata));
            }
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "unable to read structure scan", exception);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            } else {
                try {
                    stream.close();
                } catch (IOException ignored) {}
            }
        }
        if (blocks.isEmpty()) {
            throw new IllegalStateException("structure scan is empty");
        }
        return blocks;
    }

    private static Block resolveBlock(String name) {
        Block block = Block.getBlockFromName(name);
        if (block == null && name.indexOf(':') < 0) {
            block = (Block)Block.blockRegistry.getObject(
                    "minecraft:" + name);
        }
        return block;
    }

    private static int floor(double value) {
        int truncated = (int)value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

    private static final class ScanBlock {
        private final int x;
        private final int y;
        private final int z;
        private final Block block;
        private final int metadata;

        private ScanBlock(
                int x, int y, int z, Block block, int metadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
        }
    }

    private static final class PlannedBlock {
        private final int x;
        private final int y;
        private final int z;
        private final Block block;
        private final int metadata;

        private PlannedBlock(
                int x, int y, int z, Block block, int metadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
        }
    }

    private static final class OriginalBlock {
        private final int x;
        private final int y;
        private final int z;
        private final Block block;
        private final int metadata;

        private OriginalBlock(
                int x, int y, int z, Block block, int metadata) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.block = block;
            this.metadata = metadata;
        }
    }
}
