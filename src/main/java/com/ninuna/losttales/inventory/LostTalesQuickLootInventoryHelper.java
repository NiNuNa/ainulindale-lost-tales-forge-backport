package com.ninuna.losttales.inventory;

import com.ninuna.losttales.block.custom.LostTalesBlockUrnTall;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import java.lang.reflect.Method;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryLargeChest;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraft.world.World;

/**
 * Shared resolver for the Quick Loot HUD.
 *
 * <p>Keeping client and server resolution in one common helper prevents small
 * differences between the HUD target, the server snapshot, and the server drop
 * request.  That matters for legacy 1.7.10 blocks like the two-block
 * loutrophoros and vanilla double chests, where the clicked block coordinates
 * are not always the same thing as the inventory that should be read.</p>
 */
public final class LostTalesQuickLootInventoryHelper {
    public static final double MAX_INTERACTION_DISTANCE_SQ = 64.0D;
    private static final String DOUBLE_CHEST_NAME = "container.chestDouble";

    private static Method vanillaChestResolver;
    private static boolean lookedUpVanillaChestResolver;

    private LostTalesQuickLootInventoryHelper() {}

    public static InventoryAccess resolve(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return null;
        }

        int[] normalized = normalizeBlockCoords(world, x, y, z);
        if (normalized == null) {
            return null;
        }

        x = normalized[0];
        y = normalized[1];
        z = normalized[2];

        Block block = world.getBlock(x, y, z);
        if (block instanceof BlockChest) {
            Method vanillaResolver = getVanillaChestResolver();
            if (vanillaResolver != null) {
                IInventory chest = resolveVanillaChest((BlockChest) block, vanillaResolver, world, x, y, z);
                return chest == null ? null : new InventoryAccess(x, y, z, chest, false);
            }

            IInventory fallbackChest = resolveManualDoubleChest(world, x, y, z, block);
            if (fallbackChest != null) {
                return new InventoryAccess(x, y, z, fallbackChest, false);
            }
        }

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (!(tileEntity instanceof IInventory) || tileEntity.isInvalid()) {
            return null;
        }

        IInventory inventory = (IInventory) tileEntity;
        return new InventoryAccess(x, y, z, inventory, isSealed(inventory));
    }

    public static boolean isUsableBy(EntityPlayer player, InventoryAccess access) {
        if (player == null || access == null || player.worldObj == null) {
            return false;
        }
        if (!player.worldObj.blockExists(access.x, access.y, access.z)) {
            return false;
        }
        double dx = player.posX - ((double) access.x + 0.5D);
        double dy = player.posY - ((double) access.y + 0.5D);
        double dz = player.posZ - ((double) access.z + 0.5D);
        return dx * dx + dy * dy + dz * dz <= MAX_INTERACTION_DISTANCE_SQ
                && access.inventory != null
                && access.inventory.isUseableByPlayer(player);
    }

    public static boolean isSealed(IInventory inventory) {
        return inventory instanceof LostTalesTileEntityUrn && ((LostTalesTileEntityUrn) inventory).isSealed();
    }

    private static int[] normalizeBlockCoords(World world, int x, int y, int z) {
        Block block = world.getBlock(x, y, z);
        if (block instanceof LostTalesBlockUrnTall && world.getBlockMetadata(x, y, z) == 4) {
            if (y <= 0 || world.getBlock(x, y - 1, z) != block) {
                return null;
            }
            return new int[] { x, y - 1, z };
        }
        return new int[] { x, y, z };
    }

    /**
     * Uses vanilla's own chest resolver when available.  In 1.7.10 MCP this is
     * exposed as func_149951_m and handles both merged double chests and vanilla
     * "blocked chest" checks. Reflection keeps this helper resilient to mapping
     * name differences in custom development workspaces.
     */
    private static IInventory resolveVanillaChest(BlockChest block, Method method, World world, int x, int y, int z) {
        try {
            Object result = method.invoke(block, world, Integer.valueOf(x), Integer.valueOf(y), Integer.valueOf(z));
            return result instanceof IInventory ? (IInventory) result : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method getVanillaChestResolver() {
        if (!lookedUpVanillaChestResolver) {
            lookedUpVanillaChestResolver = true;
            try {
                vanillaChestResolver = BlockChest.class.getMethod("func_149951_m", World.class, Integer.TYPE, Integer.TYPE, Integer.TYPE);
            } catch (Throwable ignored) {
                vanillaChestResolver = null;
            }
        }
        return vanillaChestResolver;
    }

    private static IInventory resolveManualDoubleChest(World world, int x, int y, int z, Block block) {
        TileEntity center = world.getTileEntity(x, y, z);
        if (!(center instanceof TileEntityChest)) {
            return null;
        }

        TileEntityChest chest = (TileEntityChest) center;
        TileEntityChest west = getAdjacentChest(world, x - 1, y, z, block);
        if (west != null) {
            return new InventoryLargeChest(DOUBLE_CHEST_NAME, west, chest);
        }

        TileEntityChest east = getAdjacentChest(world, x + 1, y, z, block);
        if (east != null) {
            return new InventoryLargeChest(DOUBLE_CHEST_NAME, chest, east);
        }

        TileEntityChest north = getAdjacentChest(world, x, y, z - 1, block);
        if (north != null) {
            return new InventoryLargeChest(DOUBLE_CHEST_NAME, north, chest);
        }

        TileEntityChest south = getAdjacentChest(world, x, y, z + 1, block);
        if (south != null) {
            return new InventoryLargeChest(DOUBLE_CHEST_NAME, chest, south);
        }

        return chest;
    }

    private static TileEntityChest getAdjacentChest(World world, int x, int y, int z, Block expectedBlock) {
        if (!world.blockExists(x, y, z) || world.getBlock(x, y, z) != expectedBlock) {
            return null;
        }
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        return tileEntity instanceof TileEntityChest ? (TileEntityChest) tileEntity : null;
    }

    public static final class InventoryAccess {
        private final int x;
        private final int y;
        private final int z;
        private final IInventory inventory;
        private final boolean sealed;

        private InventoryAccess(int x, int y, int z, IInventory inventory, boolean sealed) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.inventory = inventory;
            this.sealed = sealed;
        }

        public int getX() { return this.x; }

        public int getY() { return this.y; }

        public int getZ() { return this.z; }

        public IInventory getInventory() { return this.inventory; }

        public boolean isSealed() { return this.sealed; }
    }
}
