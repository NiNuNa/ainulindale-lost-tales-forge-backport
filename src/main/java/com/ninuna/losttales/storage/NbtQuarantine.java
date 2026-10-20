package com.ninuna.losttales.storage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * The quarantine a world store keeps beside its data: every record its
 * codec could not read, kept whole with the reason it was set aside, so
 * no save ever loses it. Each store writes it the same way, under
 * {@code Quarantine} in its root: a data version and the list of records
 * under {@code Entries}.
 *
 * <p>Every store writes its quarantine, so one this build cannot read
 * keeps the whole store read-only: one that is missing or not a
 * compound, one at any version but the current, and one whose records
 * are not a list of compounds.</p>
 */
public final class NbtQuarantine {
    /** The version every quarantine is written at. */
    public static final int CURRENT_DATA_VERSION = 1;

    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";

    private NbtQuarantine() {}

    /** Writes the records under the store's root, each as its own copy; nulls are skipped. */
    public static void write(NBTTagCompound root,
                             Collection<NBTTagCompound> entries) {
        NBTTagCompound quarantine = new NBTTagCompound();
        quarantine.setInteger(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    list.appendTag(entry.copy());
                }
            }
        }
        quarantine.setTag(TAG_ENTRIES, list);
        root.setTag(TAG_QUARANTINE, quarantine);
    }

    /** Reads the quarantine under a store's root, as this build writes it. */
    public static Read read(NBTTagCompound root) {
        if (root == null
                || !root.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND)) {
            return Read.unsupported(-1);
        }
        NBTTagCompound quarantine = root.getCompoundTag(TAG_QUARANTINE);
        if (!quarantine.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)) {
            return Read.unsupported(-1);
        }
        int version = quarantine.getInteger(TAG_DATA_VERSION);
        if (version != CURRENT_DATA_VERSION) {
            return Read.unsupported(version);
        }
        if (!quarantine.hasKey(TAG_ENTRIES, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(quarantine, TAG_ENTRIES,
                        Integer.MAX_VALUE)) {
            return Read.unsupported(-1);
        }
        List<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        NBTTagList list = quarantine.getTagList(TAG_ENTRIES,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            entries.add((NBTTagCompound) list.getCompoundTagAt(index).copy());
        }
        return new Read(true, -1, entries);
    }

    /**
     * A record set aside at {@code index} of its list, under
     * {@code indexKey}, with the reason and a copy of what it held.
     */
    public static NBTTagCompound entry(String reason, String indexKey,
                                       int index, NBTTagCompound original) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        entry.setInteger(indexKey, index);
        if (original != null) {
            entry.setTag(TAG_ORIGINAL_DATA, original.copy());
        }
        return entry;
    }

    /** What a store's quarantine read as. */
    public static final class Read {
        private final boolean supported;
        private final int unsupportedVersion;
        private final List<NBTTagCompound> entries;

        private Read(boolean supported, int unsupportedVersion,
                     List<NBTTagCompound> entries) {
            this.supported = supported;
            this.unsupportedVersion = unsupportedVersion;
            this.entries = Collections.unmodifiableList(entries);
        }

        private static Read unsupported(int version) {
            return new Read(false, version,
                    Collections.<NBTTagCompound>emptyList());
        }

        /** Whether this build can read it; if not, the store stays read-only. */
        public boolean isSupported() {
            return this.supported;
        }

        /** The version it was stored at when that is what cannot be read; -1 for a bad shape. */
        public int getUnsupportedVersion() {
            return this.unsupportedVersion;
        }

        /** The records it holds, in order. */
        public List<NBTTagCompound> getEntries() {
            return this.entries;
        }

    }
}
