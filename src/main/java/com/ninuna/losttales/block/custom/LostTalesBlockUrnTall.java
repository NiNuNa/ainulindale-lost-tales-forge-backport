package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.entity.ELostTalesUser;
import java.util.Random;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockUrnTall extends LostTalesBlockUrnBase {

    public LostTalesBlockUrnTall(ELostTalesUser credits) {
        super(credits);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int i, int j, int k) {
        if (world.getBlock(i, j - 1, k) == this) {
            this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 0.64F, 0.81F);
        } else {
            this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 1.0F, 0.81F);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entity, ItemStack itemStack) {
        super.onBlockPlacedBy(world, i, j, k, entity, itemStack);
        world.setBlock(i, j + 1, k, this, 4, 2);
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
        if (world.getBlockMetadata(i, j, k) == 4) {
            return !world.isAirBlock(i, j - 1, k) && world.getBlock(i, j - 1, k) == this;
        } else {
            return !world.isAirBlock(i, j - 1, k) && world.getBlock(i, j + 1, k) == this;
        }
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        if (meta == 4){
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
        if (world.getBlockMetadata(x, y, z) == 4) {
            return world.getBlock(x, y - 1, z) == this ? new int[]{x, y - 1, z} : null;
        }
        return new int[]{x, y, z};
    }
}
