package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.base.LostTalesBlockStatueBase;
import com.ninuna.losttales.block.collision.LostTalesBlockBounds;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityPlushie;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.util.LostTalesBlockRotationHelper;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockPlushie extends LostTalesBlockStatueBase {
    private static final LostTalesBlockBounds SELECTION_BOUNDS = new LostTalesBlockBounds(0.13F, 0.0F, 0.13F, 0.87F, 0.9F, 0.87F);
    private static final LostTalesBlockBounds COLLISION_BOUNDS = new LostTalesBlockBounds(0.1875F, 0.0F, 0.1875F, 0.8125F, 0.5F, 0.8125F);
    private static final float SQUEAK_MIN_FALL_DISTANCE = 0.4F;
    private static final double LIVING_BOUNCE_MULTIPLIER = 0.6D;
    private static final double NON_LIVING_BOUNCE_MULTIPLIER = 0.48D;

    private final EnumRarity rarity;

    public LostTalesBlockPlushie(EnumRarity rarity, ELostTalesUser credits) {
        super(Material.cloth, credits);
        this.setStepSound(soundTypeCloth);
        this.setHardness(0.12F);
        this.rarity = rarity;
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        this.applyBounds(SELECTION_BOUNDS);
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return COLLISION_BOUNDS.toWorldBox(x, y, z);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB queryBox, List collisionBoxes, Entity entity) {
        COLLISION_BOUNDS.addToCollisionList(x, y, z, queryBox, collisionBoxes);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return SELECTION_BOUNDS.toWorldBox(x, y, z);
    }

    private void applyBounds(LostTalesBlockBounds bounds) {
        this.setBlockBounds(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(), bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());
    }

    @Override
    public void onBlockPlacedBy(World world, int x, int y, int z, EntityLivingBase entity, ItemStack itemStack) {
        int metadata = LostTalesBlockRotationHelper.getLegacyPlushieMetadata(entity);
        world.setBlockMetadataWithNotify(x, y, z, metadata, 2);

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof LostTalesTileEntityPlushie) {
            LostTalesTileEntityPlushie plushie = (LostTalesTileEntityPlushie) tileEntity;
            plushie.setRotation(LostTalesBlockRotationHelper.getRotationFromSnappedRotationIndex(entity, 16));
            if (!world.isRemote) {
                plushie.setPowered(world.isBlockIndirectlyGettingPowered(x, y, z));
            }
        }

        if (!world.isRemote) {
            this.squeak(world, x, y, z);
        }
    }

    @Override
    public boolean canPlaceBlockAt(World world, int x, int y, int z) {
        return super.canPlaceBlockAt(world, x, y, z) && this.canBlockStay(world, x, y, z);
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
            return;
        }

        if (!world.isRemote) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityPlushie) {
                LostTalesTileEntityPlushie plushie = (LostTalesTileEntityPlushie) tileEntity;
                boolean powered = world.isBlockIndirectlyGettingPowered(x, y, z);
                if (powered && !plushie.isPowered()) {
                    this.squeak(world, x, y, z);
                }
                plushie.setPowered(powered);
            }
        }
    }

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer entityPlayer, int side, float f, float f1, float f2) {
        if (!world.isRemote) {
            this.squeak(world, x, y, z);
        }
        return true;
    }

    @Override
    public void onFallenUpon(World world, int x, int y, int z, Entity entity, float fallDistance) {
        if (fallDistance > SQUEAK_MIN_FALL_DISTANCE && !entity.isSneaking()) {
            if (!world.isRemote) {
                this.squeak(world, x, y, z);
            }

            super.onFallenUpon(world, x, y, z, entity, fallDistance * 0.8F);

            if (entity.motionY < 0.0D) {
                double multiplier = entity instanceof EntityLivingBase ? LIVING_BOUNCE_MULTIPLIER : NON_LIVING_BOUNCE_MULTIPLIER;
                entity.motionY = -entity.motionY * multiplier;
            }
        } else {
            super.onFallenUpon(world, x, y, z, entity, fallDistance);
        }
    }

    private boolean squeak(World world, int x, int y, int z) {
        if (world.isRemote) return true;

        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof LostTalesTileEntityPlushie) {
            ((LostTalesTileEntityPlushie) tileEntity).playSqueakAnimation();
            world.playSoundEffect((double)x + 0.5D, (double)y + 0.5D, (double)z + 0.5D, LostTalesMetaData.MOD_ID + ":plushie.squeak", 0.4F, 0.8F + world.rand.nextFloat() * 0.4F);
            return true;
        }
        return false;
    }

    @Override
    public TileEntity createNewTileEntity(World world, int metadata) {
        return new LostTalesTileEntityPlushie();
    }

    public EnumRarity getRarity() {
        return rarity;
    }
}
