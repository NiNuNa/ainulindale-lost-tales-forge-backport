package com.ninuna.losttales.block;

import com.ninuna.losttales.entity.ELostTalesUser;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public class LostTalesBlockPotTall extends LostTalesBlockPotBase {

    public LostTalesBlockPotTall(ELostTalesUser credits) {
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
    }

    @Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
        return super.canPlaceBlockAt(world, i, j, k) && world.isAirBlock(i, j + 1, k) && !world.isAirBlock(i, j - 1, k);
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
}