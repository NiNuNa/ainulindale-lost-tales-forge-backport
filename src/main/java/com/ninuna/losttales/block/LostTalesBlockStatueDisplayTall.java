package com.ninuna.losttales.block;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityStatue;
import com.ninuna.losttales.entity.ELostTalesUser;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

public class LostTalesBlockStatueDisplayTall extends LostTalesBlockStatueTall {

    public LostTalesBlockStatueDisplayTall(Material material, ELostTalesUser credits) {
        super(material, credits);
    }

    @Override
    public void breakBlock(World world, int i, int j, int k, Block block, int meta) {
        LostTalesTileEntityStatue rack = (LostTalesTileEntityStatue)world.getTileEntity(i, j, k);
        if (rack != null) {
            ItemStack weaponItem = rack.getWeaponItem();
            if (weaponItem != null) {
                this.dropBlockAsItem(world, i, j, k, weaponItem);
            }
        }

        if (block.getMaterial().isToolNotRequired()){
            this.dropBlockAsItem(world, i, j, k, meta, 0);
        }

        super.breakBlock(world, i, j, k, block, meta);
    }

    @Override
    public boolean onBlockActivated(World world, int i, int j, int k, EntityPlayer entityPlayer, int side, float f, float f1, float f2) {
        TileEntity tileEntity = world.getTileEntity(i, j, k);
        if (tileEntity instanceof LostTalesTileEntityStatue && world.getBlockMetadata(i, j, k) == 4) {
            LostTalesTileEntityStatue rack = (LostTalesTileEntityStatue) tileEntity;
            ItemStack heldItem = entityPlayer.getHeldItem();
            ItemStack rackItem = rack.getWeaponItem();
            if (rackItem != null) {
                if (!world.isRemote) {
                    if (entityPlayer.getHeldItem() == null) {
                        entityPlayer.setCurrentItemOrArmor(0, rackItem);
                        world.playSoundEffect((double)i + 0.5, (double)j + 0.5, (double)k + 0.5, "random.pop", 0.2F, ((world.rand.nextFloat() - world.rand.nextFloat()) * 0.7F + 1.0F) * 2.0F);
                    } else {
                        this.dropBlockAsItem(world, i, j, k, rackItem);
                    }
                    rack.setWeaponItem((ItemStack)null);
                }

                return true;
            }

            if (rack.canAcceptItem(heldItem)) {
                if (!world.isRemote) {
                    rack.setWeaponItem(heldItem.copy());
                }

                if (!entityPlayer.capabilities.isCreativeMode) {
                    --heldItem.stackSize;
                }

                return true;
            }
        }
        return false;
    }
}