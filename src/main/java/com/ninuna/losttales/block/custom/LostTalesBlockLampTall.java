package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.block.base.LostTalesBlockLampBase;
import com.ninuna.losttales.entity.ELostTalesUser;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Random;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockLampTall extends LostTalesBlockLampBase {

    public LostTalesBlockLampTall(ELostTalesUser credits) {
        super(credits);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        if (world.getBlock(x, y - 1, z) == this) {
            this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 0.64F, 0.81F);
        } else {
            this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 1.0F, 0.81F);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack itemStack) {
        super.onBlockPlacedBy(world, x, y, z, entity, itemStack);
        world.setBlock(x, y + 1, z, this, 4, 2);
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && world.isAirBlock(x, y + 1, z) && !world.isAirBlock(x, y - 1, z);
    }

    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        if (world.getBlockMetadata(x, y, z) == 4) {
            return !world.isAirBlock(x, y - 1, z) && world.getBlock(x, y - 1, z) == this;
        } else {
            return !world.isAirBlock(x, y - 1, z) && world.getBlock(x, y + 1, z) == this;
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

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(World world, int x, int y, int z, Random random) {
        if (world.getBlockMetadata(x, y, z) == 4) {
            world.spawnParticle("smoke", x + 0.5, y + 0.5, z + 0.5, 0.0, 0.0, 0.0);
            world.spawnParticle("flame", x + 0.5, y + 0.5, z + 0.5, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public int getLightValue(IBlockAccess world, int x, int y, int z) {
        return world.getBlockMetadata(x, y, z) == 4 ? 14 : 0;
    }
}