package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityMissiveBoard;
import com.ninuna.losttales.gui.LostTalesGuiIds;
import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Simple server-safe missive board block foundation.
 *
 * The board currently owns a persistent tile-entity inventory. GUI opening,
 * dynamic generation, and server-validated quest acceptance are intentionally
 * added in later stages.
 */
public class LostTalesBlockMissiveBoard extends BlockContainer {

    public LostTalesBlockMissiveBoard() {
        super(Material.wood);
        this.setHardness(2.0F);
        this.setResistance(5.0F);
        this.setStepSound(soundTypeWood);
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityMissiveBoard) {
                player.openGui(LostTalesMod.instance, LostTalesGuiIds.MISSIVE_BOARD, world, x, y, z);
            }
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityMissiveBoard) {
                this.dropInventory(world, x, y, z, (LostTalesTileEntityMissiveBoard) tileEntity);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    private void dropInventory(World world, int x, int y, int z, LostTalesTileEntityMissiveBoard board) {
        for (int slot = 0; slot < board.getSizeInventory(); slot++) {
            ItemStack stack = board.getStackInSlot(slot);
            if (stack != null) {
                ItemStack dropped = stack.copy();
                float ox = world.rand.nextFloat() * 0.8F + 0.1F;
                float oy = world.rand.nextFloat() * 0.8F + 0.1F;
                float oz = world.rand.nextFloat() * 0.8F + 0.1F;
                EntityItem entityItem = new EntityItem(world, (double) x + ox, (double) y + oy, (double) z + oz, dropped);
                entityItem.motionX = world.rand.nextGaussian() * 0.05D;
                entityItem.motionY = world.rand.nextGaussian() * 0.05D + 0.2D;
                entityItem.motionZ = world.rand.nextGaussian() * 0.05D;
                world.spawnEntityInWorld(entityItem);
            }
        }
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new LostTalesTileEntityMissiveBoard();
    }
}
