package com.ninuna.losttales.block.collision;

import java.util.List;
import net.minecraft.util.AxisAlignedBB;

/**
 * Immutable block-local bounds used by decorative blocks whose collision must
 * not depend on the mutable bounds stored on the shared Block instance.
 */
public final class LostTalesBlockBounds {
    private final float minX;
    private final float minY;
    private final float minZ;
    private final float maxX;
    private final float maxY;
    private final float maxZ;

    public LostTalesBlockBounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        if (minX < 0.0F || minY < 0.0F || minZ < 0.0F
                || maxX > 1.0F || maxY > 1.0F || maxZ > 1.0F
                || minX >= maxX || minY >= maxY || minZ >= maxZ) {
            throw new IllegalArgumentException("Block bounds must be non-empty and remain inside one block");
        }

        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public AxisAlignedBB toWorldBox(int x, int y, int z) {
        return AxisAlignedBB.getBoundingBox(
                (double) x + this.minX,
                (double) y + this.minY,
                (double) z + this.minZ,
                (double) x + this.maxX,
                (double) y + this.maxY,
                (double) z + this.maxZ
        );
    }

    @SuppressWarnings("unchecked")
    public void addToCollisionList(int x, int y, int z, AxisAlignedBB queryBox, List collisionBoxes) {
        AxisAlignedBB worldBox = this.toWorldBox(x, y, z);
        if (queryBox != null && worldBox.intersectsWith(queryBox)) {
            collisionBoxes.add(worldBox);
        }
    }

    public float getMinX() {
        return this.minX;
    }

    public float getMinY() {
        return this.minY;
    }

    public float getMinZ() {
        return this.minZ;
    }

    public float getMaxX() {
        return this.maxX;
    }

    public float getMaxY() {
        return this.maxY;
    }

    public float getMaxZ() {
        return this.maxZ;
    }
}
