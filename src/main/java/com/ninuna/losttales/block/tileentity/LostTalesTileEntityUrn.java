package com.ninuna.losttales.block.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
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
 * Urn/amphora tile entity with the modern urn storage behavior backported onto it.
 * The common proxy still registers this class with the original legacy tile entity
 * id ("pot") so existing 1.7.10 worlds do not need a tile-entity migration.
 */
public class LostTalesTileEntityUrn extends TileEntity implements IInventory, IAnimatable {
    private static final int CLIENT_EVENT_FILL = 0;
    private static final int CLIENT_EVENT_FULL = 1;

    private final AnimationFactory factory = new AnimationFactory(this);
    private ItemStack[] inventory = new ItemStack[2];
    private boolean sealed;
    private boolean respawn;
    private int desiredSlots = 2;
    private String queuedAnimation;

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

    public void setInventorySize(int slots) {
        if (slots <= 0) slots = 2;
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
        if (stack == null || stack.stackSize <= 0 || this.sealed) return false;

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

    public void setRespawn(boolean respawn) {
        this.respawn = respawn;
        this.markDirtyAndSync();
    }

    public boolean isRespawn() {
        return this.respawn;
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

    @Override
    public String getInventoryName() {
        if (this.getBlockType() != null) {
            return this.getBlockType().getLocalizedName() + (this.sealed ? " (Sealed)" : "");
        }
        return this.sealed ? "Sealed Urn" : "Urn";
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
        return !this.sealed;
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        this.sealed = nbt.getBoolean("sealed");
        this.respawn = nbt.getBoolean("respawn");
        this.desiredSlots = nbt.hasKey("slots") ? nbt.getInteger("slots") : this.desiredSlots;
        this.setInventorySize(this.desiredSlots);

        NBTTagList list = nbt.getTagList("Items", 10);
        for (int i = 0; i < this.inventory.length; i++) {
            this.inventory[i] = null;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound itemTag = list.getCompoundTagAt(i);
            int slot = itemTag.getByte("Slot") & 255;
            if (slot >= 0 && slot < this.inventory.length) {
                this.inventory[slot] = ItemStack.loadItemStackFromNBT(itemTag);
            }
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setBoolean("sealed", this.sealed);
        nbt.setBoolean("respawn", this.respawn);
        nbt.setInteger("slots", this.desiredSlots);

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
        nbt.setTag("Items", list);
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
