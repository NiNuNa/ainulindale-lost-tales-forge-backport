package com.ninuna.losttales.world.waystone;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A house a generated waystone stands in (WAY-17): a LOTR structure scan
 * and the rules beside it. The rules say where the waystone's lower half
 * stands, from the scan's origin; where a traveller arrives, from the
 * waystone; how many layers above the house's lowest are cleared before it
 * stands; how deep its foundation may fill under it; and which block fills
 * it. The house covers the scan's own width and depth.
 */
public final class LostTalesWaystoneHouse {
    private final String id;
    private final List<LostTalesStructureScan.Block> blocks;
    private final int[] waystone;
    private final int[] arrival;
    private final int clearance;
    private final int foundation;
    private final String foundationBlock;
    private final int minX;
    private final int maxX;
    private final int minY;
    private final int maxY;
    private final int minZ;
    private final int maxZ;

    public LostTalesWaystoneHouse(String id, List<LostTalesStructureScan.Block> blocks,
                                  int[] waystone, int[] arrival, int clearance,
                                  int foundation, String foundationBlock) {
        this.id = id == null ? "" : id;
        this.blocks = Collections.unmodifiableList(
                new ArrayList<LostTalesStructureScan.Block>(blocks));
        this.waystone = copy(waystone);
        this.arrival = copy(arrival);
        this.clearance = clearance;
        this.foundation = foundation;
        this.foundationBlock = foundationBlock == null ? "" : foundationBlock;
        int lowX = Integer.MAX_VALUE;
        int highX = Integer.MIN_VALUE;
        int lowY = Integer.MAX_VALUE;
        int highY = Integer.MIN_VALUE;
        int lowZ = Integer.MAX_VALUE;
        int highZ = Integer.MIN_VALUE;
        for (LostTalesStructureScan.Block block : this.blocks) {
            lowX = Math.min(lowX, block.x);
            highX = Math.max(highX, block.x);
            lowY = Math.min(lowY, block.y);
            highY = Math.max(highY, block.y);
            lowZ = Math.min(lowZ, block.z);
            highZ = Math.max(highZ, block.z);
        }
        boolean empty = this.blocks.isEmpty();
        this.minX = empty ? 0 : lowX;
        this.maxX = empty ? 0 : highX;
        this.minY = empty ? 0 : lowY;
        this.maxY = empty ? 0 : highY;
        this.minZ = empty ? 0 : lowZ;
        this.maxZ = empty ? 0 : highZ;
    }

    /** {@code losttales:} and the rules file's name: {@code losttales:glowstone_house}. */
    public String getId() { return this.id; }
    public List<LostTalesStructureScan.Block> getBlocks() { return this.blocks; }
    /** Where the waystone's lower half stands, from the scan's origin: x, y, z. */
    public int[] getWaystone() { return copy(this.waystone); }
    /** Where a traveller arrives, from the waystone's lower half: x, y, z. */
    public int[] getArrival() { return copy(this.arrival); }
    /** The layers above the house's lowest that are cleared to air before it stands. */
    public int getClearance() { return this.clearance; }
    /** How deep the foundation may fill under the house. */
    public int getFoundation() { return this.foundation; }
    /** The block the foundation fills with, by its registry name. */
    public String getFoundationBlock() { return this.foundationBlock; }
    public int getMinX() { return this.minX; }
    public int getMaxX() { return this.maxX; }
    public int getMinY() { return this.minY; }
    public int getMaxY() { return this.maxY; }
    public int getMinZ() { return this.minZ; }
    public int getMaxZ() { return this.maxZ; }

    /** Whether the scan lays a block at that place. */
    public boolean laysBlockAt(int x, int y, int z) {
        for (LostTalesStructureScan.Block block : this.blocks) {
            if (block.x == x && block.y == y && block.z == z) {
                return true;
            }
        }
        return false;
    }

    private static int[] copy(int[] values) {
        int[] copy = new int[3];
        if (values != null) {
            System.arraycopy(values, 0, copy, 0, Math.min(3, values.length));
        }
        return copy;
    }
}
