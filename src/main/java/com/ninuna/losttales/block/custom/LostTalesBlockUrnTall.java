package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.block.collision.LostTalesBlockBounds;
import com.ninuna.losttales.entity.ELostTalesUser;
import java.util.Random;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockUrnTall extends LostTalesBlockUrnBase {
    private static final int UPPER_PART_METADATA = 4;
    private static final LostTalesBlockBounds UPPER_URN_BOUNDS = new LostTalesBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 0.64F, 0.81F);

    public LostTalesBlockUrnTall(ELostTalesUser credits) {
        super(credits);
    }

    @Override
    protected LostTalesBlockBounds getCollisionBounds(IBlockAccess world, int x, int y, int z) {
        return this.isUpperPart(world, x, y, z) ? UPPER_URN_BOUNDS : STANDARD_URN_BOUNDS;
    }

    @Override
    protected LostTalesBlockBounds getSelectionBounds(IBlockAccess world, int x, int y, int z) {
        return this.isUpperPart(world, x, y, z) ? UPPER_URN_BOUNDS : STANDARD_URN_BOUNDS;
    }

    private boolean isUpperPart(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == UPPER_PART_METADATA;
    }

    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entity, ItemStack itemStack) {
        super.onBlockPlacedBy(world, i, j, k, entity, itemStack);
        world.setBlock(i, j + 1, k, this, UPPER_PART_METADATA, 2);
        this.configurePlacedTileEntity(world, i, j + 1, k, entity);
    }

    @Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
        return j < world.getHeight() - 1
                && super.canPlaceBlockAt(world, i, j, k)
                && world.isAirBlock(i, j + 1, k)
                && !world.isAirBlock(i, j - 1, k);
    }

    @Override
    public boolean canBlockStay(World world, int i, int j, int k) {
        if (world.getBlockMetadata(i, j, k) == UPPER_PART_METADATA) {
            return !world.isAirBlock(i, j - 1, k) && world.getBlock(i, j - 1, k) == this;
        } else {
            return !world.isAirBlock(i, j - 1, k) && world.getBlock(i, j + 1, k) == this;
        }
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        if (meta == UPPER_PART_METADATA){
            return null;
        } else {
            return super.getItemDropped(meta, random, fortune);
        }
    }

    @Override
    public int getInventorySlots() {
        return 4;
    }

    @Override
    public boolean isTallUrn() {
        return true;
    }

    @Override
    protected int[] getBottomCoords(World world, int x, int y, int z) {
        if (world.getBlockMetadata(x, y, z) == UPPER_PART_METADATA) {
            return world.getBlock(x, y - 1, z) == this ? new int[]{x, y - 1, z} : null;
        }
        return new int[]{x, y, z};
    }
}
