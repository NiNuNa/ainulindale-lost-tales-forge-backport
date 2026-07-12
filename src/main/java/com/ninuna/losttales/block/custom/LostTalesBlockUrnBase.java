package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.block.base.LostTalesBlockDirectionalContainerBase;
import com.ninuna.losttales.block.collision.LostTalesBlockBounds;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.sound.ELostTalesBlockSoundType;
import com.ninuna.losttales.util.LostTalesBlockRotationHelper;
import java.util.List;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

public class LostTalesBlockUrnBase extends LostTalesBlockDirectionalContainerBase {
    protected static final LostTalesBlockBounds STANDARD_URN_BOUNDS = new LostTalesBlockBounds(0.19F, 0.0F, 0.19F, 0.81F, 1.0F, 0.81F);
    /**
     * The modern branch seals urns with honeycomb. Minecraft 1.7.10 does not
     * have honeycomb, so this backport intentionally uses vanilla clay balls.
     */
    public static final Item SEALING_ITEM = Items.clay_ball;

    public LostTalesBlockUrnBase(ELostTalesUser credits) {
        super(Material.circuits, credits);
        this.setStepSound(ELostTalesBlockSoundType.CLAY.getSoundType());
        this.setHardness(0.12F);
    }

    @Override
    public void setBlockBoundsBasedOnState(IBlockAccess world, int x, int y, int z) {
        this.applyBounds(this.getSelectionBounds(world, x, y, z));
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(World world, int x, int y, int z) {
        return this.getCollisionBounds(world, x, y, z).toWorldBox(x, y, z);
    }

    @Override
    public void addCollisionBoxesToList(World world, int x, int y, int z, AxisAlignedBB queryBox, List collisionBoxes, Entity entity) {
        this.getCollisionBounds(world, x, y, z).addToCollisionList(x, y, z, queryBox, collisionBoxes);
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(World world, int x, int y, int z) {
        return this.getSelectionBounds(world, x, y, z).toWorldBox(x, y, z);
    }

    protected LostTalesBlockBounds getCollisionBounds(IBlockAccess world, int x, int y, int z) {
        return STANDARD_URN_BOUNDS;
    }

    protected LostTalesBlockBounds getSelectionBounds(IBlockAccess world, int x, int y, int z) {
        return STANDARD_URN_BOUNDS;
    }

    private void applyBounds(LostTalesBlockBounds bounds) {
        this.setBlockBounds(bounds.getMinX(), bounds.getMinY(), bounds.getMinZ(), bounds.getMaxX(), bounds.getMaxY(), bounds.getMaxZ());
    }

    @Override
    public void onBlockPlacedBy(World world, int i, int j, int k, EntityLivingBase entity, ItemStack itemStack) {
        world.setBlockMetadataWithNotify(i, j, k, LostTalesBlockRotationHelper.getLegacyDirectionalMetadata(entity), 2);
        this.configurePlacedTileEntity(world, i, j, k, entity);
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

            if (held != null && this.isSealingItem(held)) {
                urn.setSealed(true);
                urn.playFillAnimation();
                this.consumeHeldItem(player, held);
                world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.orb", 0.5F, 1.2F);
                return true;
            }

            if (held != null && urn.insertOne(held)) {
                urn.playFillAnimation();
                this.consumeHeldItem(player, held);
                world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.pop", 0.4F, 1.0F);
                return true;
            }

            urn.playFullAnimation();
            world.playSoundEffect((double) bottom[0] + 0.5D, (double) bottom[1] + 0.5D, (double) bottom[2] + 0.5D, "random.click", 0.4F, 1.0F);
        }
        return true;
    }

    private boolean isSealingItem(ItemStack stack) {
        return stack != null && stack.getItem() == SEALING_ITEM;
    }

    private void consumeHeldItem(EntityPlayer player, ItemStack held) {
        if (player.capabilities.isCreativeMode || held == null) return;
        held.stackSize--;
        if (held.stackSize <= 0) {
            player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
        }
    }

    @Override
    public void breakBlock(World world, int x, int y, int z, Block block, int meta) {
        if (!world.isRemote && meta != 4) {
            TileEntity tileEntity = world.getTileEntity(x, y, z);
            if (tileEntity instanceof LostTalesTileEntityUrn) {
                this.dropInventory(world, x, y, z, (LostTalesTileEntityUrn) tileEntity);
            }
        }
        super.breakBlock(world, x, y, z, block, meta);
    }

    protected void dropInventory(World world, int x, int y, int z, LostTalesTileEntityUrn urn) {
        for (int slot = 0; slot < urn.getSizeInventory(); slot++) {
            ItemStack stack = urn.getStackInSlot(slot);
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
        urn.setInventorySize(this.getInventorySlots());
        return urn;
    }

    public int getInventorySlots() {
        return 2;
    }

    public boolean isTallUrn() {
        return false;
    }

    public String getModelName(boolean sealed) {
        String blockName = this.getUnlocalizedName();
        if (blockName == null) return null;
        if (blockName.startsWith("tile.")) {
            blockName = blockName.substring("tile.".length());
        }
        return sealed ? blockName + "_sealed" : blockName;
    }

    protected void configureTileEntity(World world, int x, int y, int z) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof LostTalesTileEntityUrn) {
            ((LostTalesTileEntityUrn) tileEntity).setInventorySize(this.getInventorySlots());
        }
    }

    protected void configurePlacedTileEntity(World world, int x, int y, int z, EntityLivingBase entity) {
        TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof LostTalesTileEntityUrn) {
            LostTalesTileEntityUrn urn = (LostTalesTileEntityUrn) tileEntity;
            urn.setInventorySize(this.getInventorySlots());
            if (entity != null) {
                urn.setRotation(LostTalesBlockRotationHelper.getRotationFromSnappedRotationIndex(entity, 16));
            }
        }
    }

    protected int[] getBottomCoords(World world, int x, int y, int z) {
        return new int[]{x, y, z};
    }
}
