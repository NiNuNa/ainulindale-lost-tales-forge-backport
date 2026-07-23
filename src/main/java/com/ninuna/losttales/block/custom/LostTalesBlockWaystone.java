package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.LostTalesWaystoneLifecycleService;
import com.ninuna.losttales.block.base.LostTalesBlockDirectionalContainerBase;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.gui.LostTalesGuiIds;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRepository;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSyncManager;
import java.util.Random;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/** Separate two-block waystone using the watch-stone model only as a visual. */
public final class LostTalesBlockWaystone
        extends LostTalesBlockDirectionalContainerBase {
    public static final int UPPER_METADATA = 4;

    public LostTalesBlockWaystone() {
        super(Material.rock, ELostTalesUser.SCOSHER);
        setStepSound(soundTypeStone);
        setHardness(3.0F);
        setResistance(6000000.0F);
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public boolean hasTileEntity(int metadata) {
        return metadata != UPPER_METADATA;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return metadata == UPPER_METADATA
                ? null : new LostTalesTileEntityWaystone();
    }

    @Override
    public void onBlockPlacedBy(
            final World world, final int x, final int y, final int z,
            EntityLivingBase entity, ItemStack stack) {
        int orientation = ((MathHelper.floor_double(
                entity.rotationYaw * 4.0F / 360.0F + 0.5D) & 3) + 2) % 4;
        int metadata = orientation == 0 ? 1
                : orientation == 1 ? 3
                : orientation == 2 ? 0 : 2;
        world.setBlockMetadataWithNotify(x, y, z, metadata, 2);
        boolean upperPlaced = world.setBlock(
                x, y + 1, z, this, UPPER_METADATA, 2);
        if (!upperPlaced) {
            if (!world.isRemote
                    && entity instanceof EntityPlayerMP) {
                EntityPlayerMP player = (EntityPlayerMP)entity;
                rollbackFailedPlacement(world, x, y, z, player);
                player.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.waystone.place_failed"));
            }
            return;
        }
        if (world.isRemote || !(entity instanceof EntityPlayerMP)) {
            return;
        }

        EntityPlayerMP player = (EntityPlayerMP)entity;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof LostTalesTileEntityWaystone)) {
            rollbackFailedPlacement(world, x, y, z, player);
            return;
        }
        try {
            UUID token = UUID.randomUUID();
            LostTalesMapMarkerRecord record =
                    LostTalesMapMarkerRepository.createPlayerMarker(
                            player, x, y, z, token);
            ((LostTalesTileEntityWaystone)tile).linkTo(record);
            LostTalesMapMarkerSyncManager.syncAll();
        } catch (RuntimeException exception) {
            rollbackFailedPlacement(world, x, y, z, player);
            player.addChatMessage(new ChatComponentTranslation(
                    "chat.losttales.waystone.place_failed"));
        }
    }

    @Override
    public boolean onBlockActivated(
            World world, int x, int y, int z, EntityPlayer player,
            int side, float hitX, float hitY, float hitZ) {
        int baseY = world.getBlockMetadata(x, y, z) == UPPER_METADATA
                ? y - 1 : y;
        if (!world.isRemote) {
            TileEntity tile = world.getTileEntity(x, baseY, z);
            if (tile instanceof LostTalesTileEntityWaystone) {
                player.openGui(LostTalesMod.instance,
                        LostTalesGuiIds.WAYSTONE,
                        world, x, baseY, z);
            }
        }
        return true;
    }

    @Override
    public boolean removedByPlayer(
            World world, EntityPlayer player,
            int x, int y, int z, boolean willHarvest) {
        int metadata = world.getBlockMetadata(x, y, z);
        int baseY = metadata == UPPER_METADATA ? y - 1 : y;
        TileEntity tile = world.getTileEntity(x, baseY, z);
        LostTalesMapMarkerRecord record = null;
        if (tile instanceof LostTalesTileEntityWaystone
                && !world.isRemote) {
            LostTalesTileEntityWaystone waystone =
                    (LostTalesTileEntityWaystone)tile;
            record = waystone.isLinked()
                    ? LostTalesMapMarkerStorage.get(world)
                            .getRecord(waystone.getMarkerId())
                    : null;
        }
        if (!(player instanceof EntityPlayerMP)
                || !LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                        (EntityPlayerMP)player, record,
                        world, x, baseY, z, true)) {
            if (!world.isRemote) {
                player.addChatMessage(new ChatComponentTranslation(
                        "chat.losttales.waystone.break_denied"));
            }
            return false;
        }

        if (metadata != UPPER_METADATA) {
            return super.removedByPlayer(
                    world, player, x, y, z, willHarvest);
        }
        world.setBlockToAir(x, baseY, z);
        if (!player.capabilities.isCreativeMode) {
            dropBlockAsItem(world, x, baseY, z, 0, 0);
        }
        return true;
    }

    @Override
    public void breakBlock(
            final World world, final int x, final int y, final int z,
            Block block, int metadata) {
        final int baseY = metadata == UPPER_METADATA ? y - 1 : y;
        TileEntity tile = world.getTileEntity(x, baseY, z);
        if (tile instanceof LostTalesTileEntityWaystone) {
            LostTalesWaystoneLifecycleService.onBlockRemoved(
                    (LostTalesTileEntityWaystone)tile,
                    "waystone_block_removed");
        }
        LostTalesWaystoneLifecycleService.runPreservingMarker(
                new Runnable() {
                    @Override
                    public void run() {
                        if (metadata == UPPER_METADATA) {
                            if (world.getBlock(x, baseY, z)
                                    == ELostTalesBlock.WAYSTONE.getBlock()) {
                                world.setBlockToAir(x, baseY, z);
                            }
                        } else if (world.getBlock(x, y + 1, z)
                                == ELostTalesBlock.WAYSTONE.getBlock()) {
                            world.setBlockToAir(x, y + 1, z);
                        }
                    }
                });
        super.breakBlock(world, x, y, z, block, metadata);
    }

    @Override
    public void onNeighborBlockChange(
            World world, int x, int y, int z, Block neighbor) {
        int metadata = world.getBlockMetadata(x, y, z);
        boolean valid = metadata == UPPER_METADATA
                ? world.getBlock(x, y - 1, z) == this
                : world.getBlock(x, y + 1, z) == this;
        if (!valid) {
            world.setBlockToAir(x, y, z);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return y >= 0 && y + 1 < world.getActualHeight()
                && super.canPlaceBlockAt(world, x, y, z)
                && world.isAirBlock(x, y + 1, z);
    }

    @Override
    public void setBlockBoundsBasedOnState(
            IBlockAccess world, int x, int y, int z) {
        if (world.getBlockMetadata(x, y, z) == UPPER_METADATA) {
            setBlockBounds(0.05F, 0.0F, 0.05F,
                    0.95F, 0.95F, 0.95F);
        } else {
            setBlockBounds(0.05F, 0.0F, 0.05F,
                    0.95F, 1.0F, 0.95F);
        }
    }

    @Override public int getRenderType() { return -1; }
    @Override public boolean isOpaqueCube() { return false; }
    @Override public boolean isNormalCube() { return false; }
    @Override public boolean renderAsNormalBlock() { return false; }
    @Override public int getMobilityFlag() { return 2; }
    @Override public boolean canDropFromExplosion(
            net.minecraft.world.Explosion explosion) { return false; }

    @Override
    public int quantityDropped(int metadata, int fortune, Random random) {
        return metadata == UPPER_METADATA ? 0 : 1;
    }

    @Override
    public Item getItemDropped(int metadata, Random random, int fortune) {
        return metadata == UPPER_METADATA
                ? null : Item.getItemFromBlock(this);
    }

    private void rollbackFailedPlacement(
            final World world, final int x, final int y, final int z,
            EntityPlayerMP player) {
        LostTalesWaystoneLifecycleService.runPreservingMarker(
                new Runnable() {
                    @Override
                    public void run() {
                        world.setBlockToAir(x, y + 1, z);
                        world.setBlockToAir(x, y, z);
                    }
                });
        if (!player.capabilities.isCreativeMode) {
            ItemStack returned = new ItemStack(this);
            if (!player.inventory.addItemStackToInventory(returned)) {
                player.dropPlayerItemWithRandomChoice(returned, false);
            }
        }
    }
}
