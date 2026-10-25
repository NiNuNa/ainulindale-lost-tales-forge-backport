package com.ninuna.losttales.block.vessel;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * What a vessel item carries inside (CONTENT-09), in its stack's NBT:
 * {@code losttales_vessel} holding {@code Items}, the same list of slotted
 * item compounds the vessel's block entity saves. An item's NBT is never
 * trusted: it is read back slot by slot, each entry bounded and checked,
 * and a malformed one left out.
 */
public final class VesselContents {
    /** The stack's key for the vessel's contents. */
    public static final String TAG = "losttales_vessel";
    /** The contents' list of slotted items, as the block entity names it. */
    public static final String ITEMS = "Items";
    /** The most entries read from one list, however long it claims to be. */
    public static final int MAX_SCANNED = 64;
    /** The most of one item a slot holds, whatever the item allows. */
    public static final int MAX_COUNT = 64;

    /** What the game knows of an item, by its numeric id. */
    public interface Items {
        /** How many of the item at that damage one stack holds; 0 for no such item. */
        int stackLimit(int itemId, int damage);

        /** Whether the item is a vessel. */
        boolean isVessel(int itemId);
    }

    private VesselContents() {}

    /** Whether a vessel item's NBT says it holds anything. */
    public static boolean holdsItems(NBTTagCompound stackTag) {
        if (stackTag == null || !stackTag.hasKey(TAG, Constants.NBT.TAG_COMPOUND)) {
            return false;
        }
        NBTTagCompound contents = stackTag.getCompoundTag(TAG);
        return contents.hasKey(ITEMS, Constants.NBT.TAG_LIST)
                && contents.getTagList(ITEMS, Constants.NBT.TAG_COMPOUND).tagCount() > 0;
    }

    /**
     * The items a vessel item's NBT holds, as a vessel of {@code slots}
     * slots takes them: each entry a whole item compound in a slot of its
     * own within the vessel, an item the game has, a count from 1 to what
     * one stack holds, and never a vessel holding items of its own.
     * Anything else is left out.
     */
    public static NBTTagList read(NBTTagCompound stackTag, int slots, Items items) {
        NBTTagList kept = new NBTTagList();
        if (!holdsItems(stackTag) || slots <= 0 || items == null) {
            return kept;
        }
        NBTTagList list = stackTag.getCompoundTag(TAG).getTagList(ITEMS, Constants.NBT.TAG_COMPOUND);
        Set<Integer> taken = new HashSet<Integer>();
        int scanned = Math.min(list.tagCount(), MAX_SCANNED);
        for (int index = 0; index < scanned && taken.size() < slots; index++) {
            NBTTagCompound entry = entry(list.getCompoundTagAt(index), slots, items);
            if (entry != null && taken.add(Integer.valueOf(entry.getByte("Slot") & 255))) {
                kept.appendTag(entry);
            }
        }
        return kept;
    }

    /** Writes {@code items} into a vessel item's NBT, or takes the contents out for none; answers the NBT. */
    public static NBTTagCompound write(NBTTagCompound stackTag, NBTTagList items) {
        NBTTagCompound tag = stackTag == null ? new NBTTagCompound() : stackTag;
        if (items == null || items.tagCount() == 0) {
            tag.removeTag(TAG);
            return tag;
        }
        NBTTagCompound contents = new NBTTagCompound();
        contents.setTag(ITEMS, items.copy());
        tag.setTag(TAG, contents);
        return tag;
    }

    /** One entry made whole again, or null where it is malformed. */
    private static NBTTagCompound entry(NBTTagCompound raw, int slots, Items items) {
        if (raw == null || !raw.hasKey("Slot", Constants.NBT.TAG_ANY_NUMERIC)
                || !raw.hasKey("id", Constants.NBT.TAG_ANY_NUMERIC)
                || !raw.hasKey("Count", Constants.NBT.TAG_ANY_NUMERIC)) {
            return null;
        }
        int slot = raw.getByte("Slot") & 255;
        int id = raw.getShort("id");
        int count = raw.getByte("Count");
        int damage = raw.hasKey("Damage", Constants.NBT.TAG_ANY_NUMERIC) ? raw.getShort("Damage") : 0;
        if (slot >= slots || id <= 0 || count < 1 || damage < 0) {
            return null;
        }
        int limit = Math.min(MAX_COUNT, items.stackLimit(id, damage));
        if (limit <= 0) {
            return null;
        }
        NBTTagCompound tag = null;
        if (raw.hasKey("tag")) {
            if (!raw.hasKey("tag", Constants.NBT.TAG_COMPOUND)) {
                return null;
            }
            tag = raw.getCompoundTag("tag");
            if (!VesselRules.mayHold(items.isVessel(id), holdsItems(tag))) {
                return null;
            }
        }
        NBTTagCompound entry = new NBTTagCompound();
        entry.setByte("Slot", (byte) slot);
        entry.setShort("id", (short) id);
        entry.setByte("Count", (byte) Math.min(count, limit));
        entry.setShort("Damage", (short) damage);
        if (tag != null) {
            entry.setTag("tag", tag.copy());
        }
        return entry;
    }
}
