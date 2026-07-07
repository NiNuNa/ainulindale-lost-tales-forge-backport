package com.ninuna.losttales.block.base;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.entity.ELostTalesUser;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockStatueBase extends LostTalesBlockDirectionalContainerBase {

    public LostTalesBlockStatueBase(Material material, ELostTalesUser credits) {
        super(material, credits);
        this.setStepSound(soundTypeStone);
        this.setHardness(2.0F);
        this.setResistance(10.0F);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        this.setBlockBounds(0.05F, 0.0F, 0.05F, 0.95F, 1.0F, 0.95F);
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
        return new LostTalesTileEntityStatue();
    }
}