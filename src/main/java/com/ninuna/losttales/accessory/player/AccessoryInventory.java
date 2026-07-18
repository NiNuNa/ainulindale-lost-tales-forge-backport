package com.ninuna.losttales.accessory.player;

import com.ninuna.losttales.accessory.AccessoryCompatibilityRegistry;
import com.ninuna.losttales.accessory.AccessorySlotType;
import com.ninuna.losttales.core.LostTalesClassTransformer;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/** One-slot live inventory attached to a player entity. */
public final class AccessoryInventory implements IInventory {

    public static final int SLOT_COUNT = 1;
    public static final int RING_SLOT = 0;

    private final EntityPlayer owner;
    private ItemStack ring;
    private long revision;

    public AccessoryInventory(EntityPlayer owner) {
        if (owner == null) {
            throw new IllegalArgumentException("owner must not be null");
        }
        this.owner = owner;
    }

    public EntityPlayer getOwner() {
        return this.owner;
    }

    public long getRevision() {
        return this.revision;
    }

    @Override
    public int getSizeInventory() {
        return SLOT_COUNT;
    }

    @Override
    public ItemStack getStackInSlot(int slot) {
        return slot == RING_SLOT ? this.ring : null;
    }

    @Override
    public ItemStack decrStackSize(int slot, int amount) {
        if (slot != RING_SLOT || amount <= 0 || this.ring == null) {
            return null;
        }
        ItemStack removed = this.ring;
        this.ring = null;
        markDirty();
        return removed;
    }

    @Override
    public ItemStack getStackInSlotOnClosing(int slot) {
        return decrStackSize(slot, 1);
    }

    @Override
    public void setInventorySlotContents(int slot, ItemStack stack) {
        if (slot != RING_SLOT) {
            return;
        }
        if (stack == null) {
            if (this.ring != null) {
                this.ring = null;
                markDirty();
            }
            return;
        }
        if (stack.stackSize != 1 || !isItemValidForSlot(slot, stack)) {
            return;
        }
        this.ring = stack;
        markDirty();
    }

    public boolean restoreValidated(ItemStack stack) {
        if (stack == null || stack.stackSize != 1
                || !isLifecycleOperational()
                || !AccessoryCompatibilityRegistry.getInstance()
                .isCompatible(AccessorySlotType.RING, stack)) {
            return false;
        }
        this.ring = stack.copy();
        markDirty();
        return true;
    }

    /** Replaces persisted state after the caller has validated the whole snapshot. */
    public void replaceValidated(ItemStack stack) {
        if (stack != null && (stack.stackSize != 1
                || !isLifecycleOperational()
                || !AccessoryCompatibilityRegistry.getInstance()
                .isCompatible(AccessorySlotType.RING, stack))) {
            throw new IllegalArgumentException("invalid accessory stack");
        }
        this.ring = stack == null ? null : stack.copy();
        markDirty();
    }

    /** Applies an authoritative owner snapshot without treating it as a local edit. */
    public boolean applyServerSnapshot(ItemStack stack) {
        if (stack != null && (stack.stackSize != 1
                || !AccessoryCompatibilityRegistry.getInstance()
                .isCompatible(AccessorySlotType.RING, stack))) {
            return false;
        }
        this.ring = stack == null ? null : stack.copy();
        return true;
    }

    @Override
    public String getInventoryName() {
        return "container.losttales.accessory";
    }

    @Override
    public boolean hasCustomInventoryName() {
        return false;
    }

    @Override
    public int getInventoryStackLimit() {
        return 1;
    }

    @Override
    public void markDirty() {
        if (this.revision < Long.MAX_VALUE) {
            this.revision++;
        }
    }

    @Override
    public boolean isUseableByPlayer(EntityPlayer player) {
        return player == this.owner && !this.owner.isDead;
    }

    @Override
    public void openInventory() {}

    @Override
    public void closeInventory() {}

    @Override
    public boolean isItemValidForSlot(int slot, ItemStack stack) {
        return slot == RING_SLOT
                && isLifecycleOperational()
                && AccessoryCompatibilityRegistry.getInstance().canEquip(
                this.owner, AccessorySlotType.RING, stack);
    }

    /** Refuse server-authoritative insertion if the loss-safe death hook failed. */
    private boolean isLifecycleOperational() {
        return this.owner.worldObj != null
                && Boolean.getBoolean(
                LostTalesClassTransformer.ACCESSORY_DEATH_ACTIVE_PROPERTY);
    }
}
