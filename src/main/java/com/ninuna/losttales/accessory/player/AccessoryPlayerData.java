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
    /**
     * The version of this file's own layout. The character snapshot in
     * {@code AccessoryStateComponent} counts separately: it writes a
     * compound of its own from the parsed state below, so the two never
     * read each other's bytes and neither version follows the other.
     */
    private static final int DATA_VERSION = 2;
    /** The most rings kept aside by the slot's own refusals; past it a ring drops. */
    public static final int MAX_REJECTED_ENTRIES = 16;
    /**
     * The safety bound on rings kept aside at all. A worn ring the slot
     * cannot take as it is read is kept even past {@link #MAX_REJECTED_ENTRIES}
     * (ACC-07); a list read past this bound is kept whole as one entry.
     */
    public static final int MAX_KEPT_ENTRIES = 64;
    private static final String TAG_VERSION = "Version";
    private static final String TAG_EQUIPPED = "Equipped";
    private static final String TAG_REJECTED = "Rejected";

    private EntityPlayer owner;
    private AccessoryInventory inventory;
    private final List<NBTTagCompound> rejectedEntries =
            new ArrayList<NBTTagCompound>();
    private long lastServerRevision = -1L;
    private boolean serverRejectedEntryPresent;
    /** A worn ring the last character put on could not wear here, for its one line; never saved. */
    private ItemStack ringLeftOff;

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

    /**
     * Keeps an entry aside whatever the count: the rings the saved data
     * keeps aside, and a worn ring the slot cannot take as it is read, are
     * never lost on that path (ACC-07).
     */
    public void keepAside(NBTTagCompound entry) {
        if (entry == null) {
            return;
        }
        this.rejectedEntries.add((NBTTagCompound)entry.copy());
        this.serverRejectedEntryPresent = true;
    }

    public void replaceState(
            ItemStack equipped, List<NBTTagCompound> rejected) {
        if (this.inventory == null) {
            throw new IllegalStateException("accessory inventory is unavailable");
        }
        this.inventory.replaceValidated(equipped);
        this.ringLeftOff = null;
        this.rejectedEntries.clear();
        if (rejected != null) {
            for (NBTTagCompound entry : rejected) {
                if (entry != null) {
                    this.rejectedEntries.add((NBTTagCompound)entry.copy());
                }
            }
        }
        this.serverRejectedEntryPresent = !this.rejectedEntries.isEmpty();
    }

    /** Notes the worn ring a character put on could not wear here; null for none. */
    public void noteRingLeftOff(ItemStack ring) {
        this.ringLeftOff = ring == null ? null : ring.copy();
    }

    /** The ring {@link #noteRingLeftOff} noted, once; null for none. */
    public ItemStack takeRingLeftOff() {
        ItemStack ring = this.ringLeftOff;
        this.ringLeftOff = null;
        return ring;
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
        if (!data.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                || data.getInteger(TAG_VERSION) != DATA_VERSION) {
            // Only this layout is read. Data at any other version is kept
            // whole as one rejected entry, which recovery cannot decode
            // and so leaves as it is.
            addRejectedEntry(data);
            return;
        }
        if (data.hasKey(TAG_REJECTED, Constants.NBT.TAG_LIST)) {
            net.minecraft.nbt.NBTTagList rejected = data.getTagList(
                    TAG_REJECTED, Constants.NBT.TAG_COMPOUND);
            if (rejected.tagCount() > MAX_KEPT_ENTRIES) {
                // Past the safety bound nothing is read: the data is kept
                // whole as one entry, which recovery leaves as it is.
                keepAside(data);
                return;
            }
            for (int index = 0; index < rejected.tagCount(); index++) {
                keepAside(rejected.getCompoundTagAt(index));
            }
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
            keepAside(raw);
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
