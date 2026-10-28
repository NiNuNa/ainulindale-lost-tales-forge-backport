package com.ninuna.losttales.block.tileentity;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.vessel.VesselItems;
import com.ninuna.losttales.block.vessel.VesselRules;
import com.ninuna.losttales.util.LostTalesBlockRotationHelper;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import software.bernie.geckolib3.core.IAnimatable;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType;
import software.bernie.geckolib3.core.controller.AnimationController;
import software.bernie.geckolib3.core.event.predicate.AnimationEvent;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;
/**
 * A vessel's contents: the amphora's, the urn's and the loutrophoros's. It
 * holds items one at a time, can be sealed, and plays its fill and full
 * animations on both sides. It gives up what it holds only as
 * {@link VesselRules} says (CONTENT-09): no hopper or pipe takes from it,
 * though one may fill it while it is unsealed.
 */
public class LostTalesTileEntityUrn extends TileEntity implements ISidedInventory, IAnimatable {
    /** The most slots a vessel has: the loutrophoros'. */
    private static final int MAX_SLOTS = 4;
    private static final int CLIENT_EVENT_FILL = 0;
    private static final int CLIENT_EVENT_FULL = 1;

    private final AnimationFactory factory = new AnimationFactory(this);
    private ItemStack[] inventory = new ItemStack[2];
    private boolean sealed;
    private float rotation;
    private boolean hasStoredRotation;
    private int desiredSlots = 2;
    private String queuedAnimation;
    /** Set as a creative player breaks it, so it drops no empty vessel; never saved. */
    private boolean brokenByCreativePlayer;

    @Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<LostTalesTileEntityUrn>(this, "controller", 0, this::predicate));
    }

    private <E extends IAnimatable> PlayState predicate(AnimationEvent<E> event) {
        if (this.queuedAnimation != null) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation(this.queuedAnimation, ILoopType.EDefaultLoopTypes.PLAY_ONCE));
            event.getController().clearAnimationCache();
            this.queuedAnimation = null;
        }
        return PlayState.CONTINUE;
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    /** One to four slots; anything else read from a save takes two. */
    public void setInventorySize(int slots) {
        if (slots <= 0 || slots > MAX_SLOTS) slots = 2;
        this.desiredSlots = slots;
        if (this.inventory.length != slots) {
            ItemStack[] resized = new ItemStack[slots];
            System.arraycopy(this.inventory, 0, resized, 0, Math.min(this.inventory.length, resized.length));
            this.inventory = resized;
        }
    }

    public void playFillAnimation() {
        this.playAnimation("fill", CLIENT_EVENT_FILL);
    }

    public void playFullAnimation() {
        this.playAnimation("full", CLIENT_EVENT_FULL);
    }

    private void playAnimation(String name, int eventId) {
        this.queuedAnimation = name;
        if (this.worldObj != null && !this.worldObj.isRemote && this.getBlockType() != null) {
            this.worldObj.addBlockEvent(this.xCoord, this.yCoord, this.zCoord, this.getBlockType(), eventId, 0);
        }
    }

    @Override
    public boolean receiveClientEvent(int eventId, int eventData) {
        if (eventId == CLIENT_EVENT_FILL) {
            this.queuedAnimation = "fill";
            return true;
        }
        if (eventId == CLIENT_EVENT_FULL) {
            this.queuedAnimation = "full";
            return true;
        }
        return super.receiveClientEvent(eventId, eventData);
    }

    public boolean insertOne(ItemStack stack) {
        if (stack == null || stack.stackSize <= 0 || this.sealed
                || VesselItems.isFilledVessel(stack)) return false;

        for (int i = 0; i < this.inventory.length; i++) {
            ItemStack existing = this.inventory[i];
            if (existing != null && existing.isItemEqual(stack) && ItemStack.areItemStackTagsEqual(existing, stack) && existing.stackSize < Math.min(existing.getMaxStackSize(), this.getInventoryStackLimit())) {
                existing.stackSize++;
                this.markDirtyAndSync();
                return true;
            }
        }

        for (int i = 0; i < this.inventory.length; i++) {
            if (this.inventory[i] == null) {
                ItemStack copy = stack.copy();
                copy.stackSize = 1;
                this.inventory[i] = copy;
                this.markDirtyAndSync();
                return true;
            }
        }
        return false;
    }

    public void setSealed(boolean sealed) {
        this.sealed = sealed;
        this.markDirtyAndSync();
    }

    public boolean isSealed() {
        return this.sealed;
    }

    /** Whether it holds anything. */
    public boolean holdsItems() {
        for (ItemStack stack : this.inventory) {
            if (stack != null && stack.stackSize > 0) {
                return true;
            }
        }
        return false;
    }

    public void markBrokenByCreativePlayer() {
        this.brokenByCreativePlayer = true;
    }

    public boolean isBrokenByCreativePlayer() {
        return this.brokenByCreativePlayer;
    }

    /** What it holds, as it saves it: each item with its slot. */
    public NBTTagList writeItems() {
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < this.inventory.length; i++) {
            ItemStack stack = this.inventory[i];
            if (stack != null) {
                NBTTagCompound itemTag = new NBTTagCompound();
                itemTag.setByte("Slot", (byte) i);
                stack.writeToNBT(itemTag);
                list.appendTag(itemTag);
            }
        }
        return list;
    }

    /** Takes what a vessel item held, already checked ({@code VesselContents.read}), in place of what it holds. */
    public void fillFrom(NBTTagList items) {
        this.readItems(items);
        this.markDirtyAndSync();
    }

    private void readItems(NBTTagList list) {
        for (int i = 0; i < this.inventory.length; i++) {
            this.inventory[i] = null;
        }
        for (int i = 0; list != null && i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < this.inventory.length) {
                this.inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
    }

    public void setRotation(float rotation) {
        this.rotation = LostTalesBlockRotationHelper.normalizeDegrees(rotation);
        this.hasStoredRotation = true;
        this.markDirtyAndSync();
    }

    public float getRenderRotation(int metadata) {
        return this.hasStoredRotation ? this.rotation : LostTalesBlockRotationHelper.getDirectionalMetadataRotation(metadata);
    }

    private void markDirtyAndSync() {
        this.markDirty();
        if (this.worldObj != null) {
            this.worldObj.markBlockForUpdate(this.xCoord, this.yCoord, this.zCoord);
        }
    }

    @Override
    public int getSizeInventory() {
        return this.inventory.length;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot >= 0 && slot < this.inventory.length ? this.inventory[slot] : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int count) {
        if (slot < 0 || slot >= this.inventory.length || this.inventory[slot] == null) return null;
        ItemStack stack = this.inventory[slot];
        if (stack.stackSize <= count) {
            this.inventory[slot] = null;
            this.markDirtyAndSync();
            return stack;
        }
        ItemStack result = stack.splitStack(count);
        if (stack.stackSize <= 0) this.inventory[slot] = null;
        this.markDirtyAndSync();
        return result;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        if (slot < 0 || slot >= this.inventory.length) return null;
        ItemStack stack = this.inventory[slot];
        this.inventory[slot] = null;
        return stack;
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot < 0 || slot >= this.inventory.length) return;
        this.inventory[slot] = stack;
        if (stack != null && stack.stackSize > this.getInventoryStackLimit()) {
            stack.stackSize = this.getInventoryStackLimit();
        }
        this.markDirtyAndSync();
    }

    /**
     * The lang key of the urn's name, which the game showing it translates,
     * as vanilla's containers name themselves; whether it is sealed the
     * quick-loot snapshot carries beside it.
     */
    @Override
    public String getInventoryName() {
        Block block = this.getBlockType();
        return (block != null ? block : ELostTalesBlock.URN.getBlock())
                .getUnlocalizedName() + ".name";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 64;
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return this.worldObj != null
                && this.worldObj.getTileEntity(this.xCoord, this.yCoord, this.zCoord) == this
                && player.getDistanceSq((double) this.xCoord + 0.5D, (double) this.yCoord + 0.5D, (double) this.zCoord + 0.5D) <= 64.0D;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return !this.sealed && !VesselItems.isFilledVessel(stack);
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int side) {
        int[] slots = new int[this.inventory.length];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = i;
        }
        return slots;
    }

    @Override
    public boolean canInsertItem(int slot, ItemStack stack, int side) {
        return this.isItemValidForSlot(slot, stack);
    }

    @Override
    public boolean canExtractItem(int slot, ItemStack stack, int side) {
        return VesselRules.givesUp(VesselRules.Way.HOPPER, this.sealed);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.sealed = nbt.getBoolean("sealed");
        this.hasStoredRotation = nbt.hasKey("rotation");
        this.rotation = this.hasStoredRotation ? LostTalesBlockRotationHelper.normalizeDegrees(nbt.getFloat("rotation")) : 0.0F;
        this.desiredSlots = nbt.hasKey("slots") ? nbt.getInteger("slots") : this.desiredSlots;
        this.setInventorySize(this.desiredSlots);

        this.readItems(nbt.getTagList("Items", 10));
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("sealed", this.sealed);
        if (this.hasStoredRotation) {
            nbt.setFloat("rotation", this.rotation);
        }
        nbt.setInteger("slots", this.desiredSlots);
        nbt.setTag("Items", this.writeItems());
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound tag = new NBTTagCompound();
        this.writeToNBT(tag);
        return new S35PacketUpdateTileEntity(this.xCoord, this.yCoord, this.zCoord, 1, tag);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity packet) {
        this.readFromNBT(packet.func_148857_g());
    }
}
