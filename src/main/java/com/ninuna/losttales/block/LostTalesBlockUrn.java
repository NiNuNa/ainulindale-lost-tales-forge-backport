package com.ninuna.losttales.block;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.sound.ELostTalesBlockSoundType;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import java.util.Random;

public class LostTalesBlockUrn extends LostTalesBlockDirectionalContainerBase {
    public static final Item LEGACY_SEALING_ITEM = Items.clay_ball;

    private final int inventorySlots;
    private final boolean tall;
    private final String modelName;

    public LostTalesBlockUrn(String modelName, int inventorySlots, boolean tall, ELostTalesUser credits) {
        super(Material.circuits, credits);
        this.modelName = modelName;
        this.inventorySlots = inventorySlots;
        this.tall = tall;
        this.setStepSound(ELostTalesBlockSoundType.CLAY.getSoundType());
        this.setHardness(0.12F);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        if (this.tall && world.getBlockMetadata(x, y, z) == 4) {
            this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 0.64F, 0.81F);
        } else {
            this.setBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 1.0F, 0.81F);
        }
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack itemStack) {
        int l = ((MathHelper.floor_double((double) (entity.rotationYaw * 4.0F / 360.0F) + 0.5D) & 3) + 2) % 4;
        int meta = l == 0 ? 1 : l == 1 ? 3 : l == 2 ? 0 : 2;
        world.setBlockMetadataWithNotify(x, y, z, meta, 2);
        this.configureTileEntity(world, x, y, z);

        if (this.tall) {
            world.setBlock(x, y + 1, z, this, 4, 2);
            this.configureTileEntity(world, x, y + 1, z);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && this.canBlockStay(world, x, y, z) && (!this.tall || world.isAirBlock(x, y + 1, z));
    }

    @Override
    public boolean canBlockStay(World world, int x, int y, int z) {
        if (this.tall && world.getBlockMetadata(x, y, z) == 4) {
            return world.getBlock(x, y - 1, z) == this;
        }
        return !world.isAirBlock(x, y - 1, z) && (!this.tall || world.getBlock(x, y + 1, z) == this);
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
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int side, float hitX, float hitY, float hitZ) {
        int[] bottom = this.getBottomCoords(world, x, y, z);
        if (bottom == null) return false;

        TileEntity tileEntity = world.getTileEntity(bottom[0], bottom[1], bottom[2]);
        if (!(tileEntity instanceof LostTalesTileEntityUrn)) return false;
        LostTalesTileEntityUrn urn = (LostTalesTileEntityUrn) tileEntity;
        this.configureTileEntity(world, bottom[0], bottom[1], bottom[2]);

        ItemStack held = player.getHeldItem();
        if (!world.isRemote) {
            if (held != null && held.getItem() == ELostTalesItem.CREATION_TOOL_LOOT_RESPAWNER.getItem() && player.capabilities.isCreativeMode) {
                urn.setRespawn(!urn.isRespawn());
                world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, urn.isRespawn() ? "random.orb" : "random.fizz", 0.6F, 1.0F);
                return true;
            }

            if (urn.isSealed()) {
                urn.playFullAnimation();
                world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.click", 0.5F, 0.8F);
                return true;
            }

            if (held != null && held.getItem() == LEGACY_SEALING_ITEM) {
                urn.setSealed(true);
                urn.playFillAnimation();
                if (!player.capabilities.isCreativeMode) {
                    held.stackSize--;
                    if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
                world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.orb", 0.5F, 1.2F);
                return true;
            }

            if (held != null && urn.insertOne(held)) {
                urn.playFillAnimation();
                if (!player.capabilities.isCreativeMode) {
                    held.stackSize--;
                    if (held.stackSize <= 0) player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
                }
                world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.pop", 0.4F, 1.0F);
                return true;
            }

            urn.playFullAnimation();
            world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.click", 0.4F, 1.0F);
        }
        return true;
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && meta != 4) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityUrn) {
                LostTalesTileEntityUrn urn = (LostTalesTileEntityUrn) tileEntity;
                for (int i = 0; i < urn.getSizeInventory(); i++) {
                    ItemStack stack = urn.getStackInSlot(i);
                    if (stack != null) {
                        float ox = world.rand.nextFloat() * 0.8F + 0.1F;
                        float oy = world.rand.nextFloat() * 0.8F + 0.1F;
                        float oz = world.rand.nextFloat() * 0.8F + 0.1F;
                        EntityItem entityItem = new EntityItem(world, (double) x + ox, (double) y + oy, (double) z + oz, stack.copy());
                        world.spawnEntityInWorld(entityItem);
                    }
                }
            }
        }

        if (this.tall) {
            if (meta == 4 && world.getBlock(x, y - 1, z) == this) {
                world.setBlockToAir(x, y - 1, z);
            } else if (meta != 4 && world.getBlock(x, y + 1, z) == this) {
                world.setBlockToAir(x, y + 1, z);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    @Override
    public Item getItemDropped(int meta, Random random, int fortune) {
        return meta == 4 ? null : super.getItemDropped(meta, random, fortune);
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
        LostTalesTileEntityUrn urn = new LostTalesTileEntityUrn();
        urn.setInventorySize(this.inventorySlots);
        return urn;
    }

    public String getModelName() {
        return this.modelName;
    }

    public boolean isTall() {
        return this.tall;
    }

    public int getInventorySlots() {
        return this.inventorySlots;
    }

    private void configureTileEntity(World world, int x, int y, int z) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof LostTalesTileEntityUrn) {
            ((LostTalesTileEntityUrn) tileEntity).setInventorySize(this.inventorySlots);
        }
    }

    private int[] getBottomCoords(World world, int x, int y, int z) {
        if (this.tall && world.getBlockMetadata(x, y, z) == 4) {
            return world.getBlock(x, y - 1, z) == this ? new int[]{x, y - 1, z} : null;
        }
        return new int[]{x, y, z};
    }
}
