package com.ninuna.losttales.block;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityLamp;
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

public class LostTalesBlockLampBase extends LostTalesBlockDirectionalContainerBase {
    public LostTalesBlockLampBase(ELostTalesUser credits) {
        super(Material.circuits, credits);
        this.setStepSound(ELostTalesBlockSoundType.CLAY.getSoundType());
        this.setHardness(0.12F);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 1.0F, 0.81F);
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack itemStack) {
        int l = ((MathHelper.floor_double((double)(entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3) + 2) % 4;

        if (l == 0) {
            world.setBlockMetadataWithNotify(x, y, z, 1, 2);
        }

        if (l == 1) {
            world.setBlockMetadataWithNotify(x, y, z, 3, 2);
        }

        if (l == 2) {
            world.setBlockMetadataWithNotify(x, y, z, 0, 2);
        }

        if (l == 3) {
            world.setBlockMetadataWithNotify(x, y, z, 2, 2);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && !world.isAirBlock(x, y - 1, z);
    }

    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        return !world.isAirBlock(x, y - 1, z);
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block block) {
        if (!this.canBlockStay(world, x, y, z)) {
            int meta = world.getBlockMetadata(x, y, z);
            this.dropBlockAsItem(world, x, y, z, meta, 0);
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return 14;
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
        return new LostTalesTileEntityLamp();
    }
}