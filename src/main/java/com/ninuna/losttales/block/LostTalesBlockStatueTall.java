package com.ninuna.losttales.block;

import com.ninuna.losttales.entity.ELostTalesUser;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockStatueTall extends LostTalesBlockStatueBase {

    public LostTalesBlockStatueTall(Material material, ELostTalesUser credits) {
        super(material, credits);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int i, int j, int k) {
        if (world.getBlock(i, j - 1, k) == this) {
            this.setBlockBounds(0.05F, 0.0F, 0.05F, 0.95F, 0.95F, 0.95F);
        } else {
            this.setBlockBounds(0.05F, 0.0F, 0.05F, 0.95F, 1.0F, 0.95F);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entity, ItemStack itemStack) {
        super.onBlockPlacedBy(world, i, j, k, entity, itemStack);
        world.setBlock(i, j + 1, k, this, 4, 2);
    }

    @Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
        return super.canPlaceBlockAt(world, i, j, k) && world.isAirBlock(i, j + 1, k);
    }

    @Override
    public boolean canBlockStay(World world, int i, int j, int k) {
        if (world.getBlockMetadata(i, j, k) == 4) {
            return world.getBlock(i, j - 1, k) == this;
        } else {
            return world.getBlock(i, j + 1, k) == this;
        }
    }

    @Override
    public void onNeighborBlockChange(World world, int i, int j, int k, Block block) {
        if (!this.canBlockStay(world, i, j, k)) {
            world.setBlockToAir(i, j, k);
        }
    }

    @Override
    public void breakBlock(World world, int i, int j, int k, Block block, int meta) {
        if (block.getMaterial().isToolNotRequired()){
            this.dropBlockAsItem(world, i, j, k, meta, 0);
        }
        super.breakBlock(world, i, j, k, block, meta);
    }
}