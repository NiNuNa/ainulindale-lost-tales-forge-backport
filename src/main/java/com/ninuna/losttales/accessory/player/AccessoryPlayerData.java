package com.ninuna.losttales.accessory.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Versioned live accessory state stored with the Forge player entity. */
public final class AccessoryPlayerData implements IExtendedEntityProperties {

    public static final String PROPERTY_ID = "LostTalesAccessory";
    private static final int DATA_VERSION = 2;
    public static final int MAX_REJECTED_ENTRIES = 16;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_EQUIPPED = "Equipped";
    private static final String TAG_REJECTED = "Rejected";

    private EntityPlayer owner;
    private AccessoryInventory inventory;
    private final List<NBTTagCompound> rejectedEntries =
            new ArrayList<NBTTagCompound>();
    private long lastServerRevision = -1L;
    private boolean serverRejectedEntryPresent;

    public static AccessoryPlayerData get(EntityPlayer player) {
        if (player == null) {
            return null;
        }
        Object value = player.getExtendedProperties(PROPERTY_ID);
        return value instanceof AccessoryPlayerData
                ? (AccessoryPlayerData)value : null;
    }

    /**
     * Normal players are attached by EntityConstructing. This fallback also
     * covers fake players created before Lost Tales registers its event hook.
     */
    public static AccessoryPlayerData getOrCreate(EntityPlayer player) {
        AccessoryPlayerData existing = get(player);
        if (existing != null || player == null) {
            return existing;
        }
        synchronized (player) {
            existing = get(player);
            if (existing != null) {
                return existing;
            }
            AccessoryPlayerData created = new AccessoryPlayerData();
            String registered = player.registerExtendedProperties(
                    PROPERTY_ID, created);
            if (!PROPERTY_ID.equals(registered)) {
                return null;
            }
            created.init(player, player.worldObj);
            return created;
        }
    }

    public AccessoryInventory getInventory() {
        return this.inventory;
    }

    public NBTTagCompound getRejectedEntry() {
        return this.rejectedEntries.isEmpty() ? null
                : (NBTTagCompound)this.rejectedEntries.get(0).copy();
    }

    public List<NBTTagCompound> getRejectedEntries() {
        ArrayList<NBTTagCompound> copy =
                new ArrayList<NBTTagCompound>(this.rejectedEntries.size());
        for (NBTTagCompound entry : this.rejectedEntries) {
            copy.add((NBTTagCompound)entry.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public boolean hasRejectedEntry() {
        return !this.rejectedEntries.isEmpty()
                || this.serverRejectedEntryPresent;
    }

    /** Replaces or removes the first entry after a partial recovery attempt. */
    public void replaceRejectedEntry(ItemStack stack) {
        replaceRejectedEntry(0, stack);
    }

    public void replaceRejectedEntry(int index, ItemStack stack) {
        if (index < 0 || index >= this.rejectedEntries.size()) {
            return;
        }
        if (stack == null) {
            this.rejectedEntries.remove(index);
            this.serverRejectedEntryPresent = !this.rejectedEntries.isEmpty();
            return;
        }
        NBTTagCompound encoded = new NBTTagCompound();
        stack.writeToNBT(encoded);
        this.rejectedEntries.set(index, encoded);
        this.serverRejectedEntryPresent = true;
    }

    /** Preserves a server-rejected live stack for later inventory recovery. */
    public boolean quarantine(ItemStack stack) {
        if (stack == null
                || this.rejectedEntries.size() >= MAX_REJECTED_ENTRIES) {
            return false;
        }
        NBTTagCompound encoded = new NBTTagCompound();
        stack.writeToNBT(encoded);
        addRejectedEntry(encoded);
        return true;
    }

    public void replaceState(
            ItemStack equipped, List<NBTTagCompound> rejected) {
        if (this.inventory == null) {
            throw new IllegalStateException("accessory inventory is unavailable");
        }
        this.inventory.replaceValidated(equipped);
        this.rejectedEntries.clear();
        if (rejected != null) {
            for (NBTTagCompound entry : rejected) {
                addRejectedEntry(entry);
            }
        }
        this.serverRejectedEntryPresent = !this.rejectedEntries.isEmpty();
    }

    public void copyFrom(AccessoryPlayerData source) {
        if (source == null || source.inventory == null) {
            return;
        }
        ItemStack equipped = source.inventory.getStackInSlot(
                AccessoryInventory.RING_SLOT);
        replaceState(equipped, source.getRejectedEntries());
    }

    /** Applies an owner-only server snapshot on the logical client. */
    public boolean applyServerSync(
            long revision, ItemStack equipped, boolean rejectedPresent) {
        if (revision < 0L || this.lastServerRevision >= 0L
                && revision <= this.lastServerRevision
                || this.inventory == null
                || !this.inventory.applyServerSnapshot(equipped)) {
            return false;
        }
        this.lastServerRevision = revision;
        this.serverRejectedEntryPresent = rejectedPresent;
        return true;
    }

    @Override
    public void saveNBTData(NBTTagCompound playerTag) {
        if (playerTag == null) {
            return;
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(TAG_VERSION, DATA_VERSION);
        ItemStack equipped = this.inventory == null
                ? null : this.inventory.getStackInSlot(
                AccessoryInventory.RING_SLOT);
        if (equipped != null) {
            NBTTagCompound item = new NBTTagCompound();
            equipped.writeToNBT(item);
            data.setTag(TAG_EQUIPPED, item);
        }
        if (!this.rejectedEntries.isEmpty()) {
            net.minecraft.nbt.NBTTagList rejected =
                    new net.minecraft.nbt.NBTTagList();
            for (NBTTagCompound entry : this.rejectedEntries) {
                rejected.appendTag(entry.copy());
            }
            data.setTag(TAG_REJECTED, rejected);
        }
        playerTag.setTag(PROPERTY_ID, data);
    }

    @Override
    public void loadNBTData(NBTTagCompound playerTag) {
        this.rejectedEntries.clear();
        this.serverRejectedEntryPresent = false;
        if (this.inventory == null || playerTag == null
                || !playerTag.hasKey(
                PROPERTY_ID, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound data = playerTag.getCompoundTag(PROPERTY_ID);
        int version = data.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                ? data.getInteger(TAG_VERSION) : 0;
        if (version == 1
                && data.hasKey(TAG_REJECTED, Constants.NBT.TAG_COMPOUND)) {
            addRejectedEntry(data.getCompoundTag(TAG_REJECTED));
        } else if (data.hasKey(TAG_REJECTED, Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList rejected = data.getTagList(
                    TAG_REJECTED, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < rejected.tagCount()
                    && index < MAX_REJECTED_ENTRIES; index++) {
                addRejectedEntry(rejected.getCompoundTagAt(index));
            }
        } else if (data.hasKey(TAG_REJECTED, Constants.NBT.TAG_COMPOUND)) {
            // Preserve a legacy/future singleton even when the version is unknown.
            addRejectedEntry(data.getCompoundTag(TAG_REJECTED));
        }
        if (!data.hasKey(TAG_EQUIPPED, Constants.NBT.TAG_COMPOUND)) {
            return;
        }
        NBTTagCompound raw = data.getCompoundTag(TAG_EQUIPPED);
        ItemStack stack;
        try {
            stack = ItemStack.loadItemStackFromNBT(raw);
        } catch (RuntimeException exception) {
            stack = null;
        }
        if (stack == null || !this.inventory.restoreValidated(stack)) {
            addRejectedEntry(raw);
        }
    }

    @Override
    public void init(Entity entity, World world) {
        if (!(entity instanceof EntityPlayer)) {
            throw new IllegalArgumentException(
                    "Accessory data may only be attached to players");
        }
        this.owner = (EntityPlayer)entity;
        this.inventory = new AccessoryInventory(this.owner);
    }

    private void addRejectedEntry(NBTTagCompound entry) {
        if (entry == null
                || this.rejectedEntries.size() >= MAX_REJECTED_ENTRIES) {
            return;
        }
        this.rejectedEntries.add((NBTTagCompound)entry.copy());
        this.serverRejectedEntryPresent = true;
    }
}
