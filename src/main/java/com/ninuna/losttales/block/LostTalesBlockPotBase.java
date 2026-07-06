package com.ninuna.losttales.block;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPot;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.sound.ELostTalesBlockSoundType;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockPotBase extends LostTalesBlockDirectionalContainerBase {

    public LostTalesBlockPotBase(ELostTalesUser credits) {
        super(Material.circuits, credits);
        this.setStepSound(ELostTalesBlockSoundType.CLAY.getSoundType());
        this.setHardness(0.12F);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int i, int j, int k) {
        this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 1.0F, 0.81F);
    }

    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entity, ItemStack itemStack) {
        int l = ((MathHelper.floor_double((double)(entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3) + 2) % 4;

        if (l == 0) {
            world.setBlockMetadataWithNotify(i, j, k, 1, 2);
        }

        if (l == 1) {
            world.setBlockMetadataWithNotify(i, j, k, 3, 2);
        }

        if (l == 2) {
            world.setBlockMetadataWithNotify(i, j, k, 0, 2);
        }

        if (l == 3) {
            world.setBlockMetadataWithNotify(i, j, k, 2, 2);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, int i, int j, int k) {
        return super.canPlaceBlockAt(world, i, j, k) && !world.isAirBlock(i, j - 1, k);
    }

    @Override
    public boolean canBlockStay(World world, int i, int j, int k) {
        return !world.isAirBlock(i, j - 1, k);
    }

    @Override
    public void onNeighborBlockChange(World world, int i, int j, int k, Block block) {
        if (!this.canBlockStay(world, i, j, k)) {
            int meta = world.getBlockMetadata(i, j, k);
            this.dropBlockAsItem(world, i, j, k, meta, 0);
            world.setBlockToAir(i, j, k);
        }
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean isNormalCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new LostTalesTileEntityPot();
    }
}