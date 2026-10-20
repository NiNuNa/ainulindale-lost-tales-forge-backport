package com.ninuna.losttales.world.waystone;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.LostTalesWaystoneLifecycleService;
import com.ninuna.losttales.block.custom.LostTalesBlockWaystone;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.WorldServer;

/**
 * Builds a waystone's house (WAY-17) at the nearest good site within 16
 * blocks of its marker. Every column of the house's width and depth is
 * checked before a block changes: a solid surface, a foundation no deeper
 * than the house allows, the air the house clears, and no LOTR banner's
 * land. The foundation fills under it, the cleared layers go to air, the
 * scan's blocks stand, and the waystone stands where the house's rules
 * put it. A marker's place is where the waystone stands.
 */
public final class LostTalesWaystoneHousePlacer
        implements LostTalesWaystoneStructurePlacer {
    private static final int MAX_SITE_SEARCH_RADIUS = 16;
    /** The sites tried round a marker, nearest ring first. */
    static final int[][] SITE_SEARCH_OFFSETS =
            createSiteSearchOffsets(MAX_SITE_SEARCH_RADIUS);

    private final LostTalesWaystoneHouse house;
    private final List<ScanBlock> scan;
    private final Block foundationBlock;
    private final int minX;
    private final int maxX;
    private final int minZ;
    private final int maxZ;
    private final int anchorX;
    private final int anchorY;
    private final int anchorZ;

    /** A placer for {@code house}, whose blocks the game has; one it lacks is refused. */
    public LostTalesWaystoneHousePlacer(LostTalesWaystoneHouse house) {
        if (house == null) {
            throw new IllegalArgumentException("a placer needs a house");
        }
        this.house = house;
        List<ScanBlock> blocks = new ArrayList<ScanBlock>();
        for (LostTalesStructureScan.Block block : house.getBlocks()) {
            blocks.add(new ScanBlock(block.x, block.y, block.z,
                    resolve(block.name), block.metadata));
        }
        this.scan = blocks;
        this.foundationBlock = resolve(house.getFoundationBlock());
        this.minX = house.getMinX();
        this.maxX = house.getMaxX();
        this.minZ = house.getMinZ();
        this.maxZ = house.getMaxZ();
        int[] waystone = house.getWaystone();
        this.anchorX = waystone[0];
        this.anchorY = waystone[1];
        this.anchorZ = waystone[2];
    }

    @Override
    public String getId() {
        return this.house.getId();
    }

    /** Where a traveller arrives, from the waystone's lower half. */
    public int[] getArrival() {
        return this.house.getArrival();
    }

    /** Whether the game has a block of that name, as a house's file names it. */
    static boolean hasBlock(String name) {
        return name != null && name.length() > 0 && !name.matches("[0-9]+")
                && Block.blockRegistry.containsKey(name);
    }

    private static Block resolve(String name) {
        if (!hasBlock(name)) {
            throw new IllegalArgumentException("the game has no block " + name);
        }
        return Block.getBlockFromName(name);
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
            final int waystoneX, final int waystoneZ) {
        final int originX = waystoneX - this.anchorX;
        final int originZ = waystoneZ - this.anchorZ;
        if (!areFootprintChunksLoaded(world, originX, originZ)) {
            return LostTalesWaystonePlacementResult.deferred(
                    "required_chunks_not_loaded");
        }

        int highestSupportY = Integer.MIN_VALUE;
        int lowestSupportY = Integer.MAX_VALUE;
        final int[][] supportYByColumn =
                new int[this.maxX - this.minX + 1][this.maxZ - this.minZ + 1];
        for (int localX = this.minX; localX <= this.maxX; localX++) {
            for (int localZ = this.minZ; localZ <= this.maxZ; localZ++) {
                int worldX = originX + localX;
                int worldZ = originZ + localZ;
                int candidateSupportY = findSupportY(
                        world, worldX, worldZ);
                if (!isSafeSupport(
                        world, worldX, candidateSupportY, worldZ)) {
                    return LostTalesWaystonePlacementResult.blocked(
                            "unsuitable_surface");
                }
                supportYByColumn[localX - this.minX][localZ - this.minZ] =
                        candidateSupportY;
                highestSupportY = Math.max(
                        highestSupportY, candidateSupportY);
                lowestSupportY = Math.min(
                        lowestSupportY, candidateSupportY);
            }
        }
        final int waystoneY = record.hasExplicitY()
                ? floor(record.getY())
                : highestSupportY + 1 + this.anchorY;
        final int originY = waystoneY - this.anchorY;
        final int foundationTopY = originY - 1;
        final int clearance = this.house.getClearance();
        if (highestSupportY > foundationTopY
                || foundationTopY - lowestSupportY
                        > this.house.getFoundation()) {
            return LostTalesWaystonePlacementResult.blocked(
                    record.hasExplicitY()
                            ? "configured_height_unsupported"
                            : "surface_too_steep");
        }
        if (originY < 1
                || originY + clearance >= world.getActualHeight()) {
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
                waystoneX, waystoneY, waystoneZ) != null) {
            return LostTalesWaystonePlacementResult.blocked(
                    "waystone_position_already_linked");
        }

        final LinkedHashMap<String, PlannedBlock> plan =
                new LinkedHashMap<String, PlannedBlock>();
        for (int localX = this.minX; localX <= this.maxX; localX++) {
            for (int localZ = this.minZ; localZ <= this.maxZ; localZ++) {
                int x = originX + localX;
                int z = originZ + localZ;
                int columnSupportY =
                        supportYByColumn[localX - this.minX][localZ - this.minZ];
                for (int y = columnSupportY + 1;
                     y <= foundationTopY; y++) {
                    if (!isReplaceable(world, x, y, z)) {
                        return LostTalesWaystonePlacementResult.blocked(
                                "foundation_obstructed");
                    }
                    put(plan, x, y, z, this.foundationBlock, 0);
                }
                for (int localY = 0;
                     localY <= clearance; localY++) {
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
        put(plan, waystoneX, waystoneY, waystoneZ,
                ELostTalesBlock.WAYSTONE.getBlock(), 0);
        put(plan, waystoneX, waystoneY + 1, waystoneZ,
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
                waystoneX, waystoneY, waystoneZ);
        if (!(tile instanceof LostTalesTileEntityWaystone)) {
            rollback(world, originals);
            return LostTalesWaystonePlacementResult.blocked(
                    "waystone_tile_missing");
        }
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord linked = record.withLink(
                world.provider.dimensionId,
                waystoneX, waystoneY, waystoneZ, token);
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

    private boolean isFootprintProtected(
            WorldServer world, int originX,
            int waystoneY, int originZ) {
        for (int localX = this.minX; localX <= this.maxX; localX++) {
            for (int localZ = this.minZ; localZ <= this.maxZ; localZ++) {
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

    private boolean areFootprintChunksLoaded(
            WorldServer world, int originX, int originZ) {
        int minChunkX = (originX + this.minX) >> 4;
        int maxChunkX = (originX + this.maxX) >> 4;
        int minChunkZ = (originZ + this.minZ) >> 4;
        int maxChunkZ = (originZ + this.maxZ) >> 4;
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
