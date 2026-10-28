package com.ninuna.losttales.quest;

import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

/**
 * The quest givers killed in this world, by their entity id, oldest first
 * (QUEST-32). A quest that fails when its giver is killed fails for a
 * player online as it happens, and for one who was away at their next
 * login, read from here. The latest {@link #MAX_ENTRIES} are kept.
 *
 * <p>An entry that cannot be read, or the later of two for one giver, is
 * set aside in the quarantine, never dropped (DATA-10). Data of another
 * version, a quarantine this build cannot read, or one that would hold
 * more than {@link NbtQuarantine#MAX_ENTRIES} is kept as it was, logged
 * once as it is read, and nothing more is recorded.</p>
 */
public final class FallenGiversWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_fallen_givers";
    static final int DATA_VERSION = 2;
    public static final int MAX_ENTRIES = 4096;
    static final String TAG_VERSION = "DataVersion";
    static final String TAG_FALLEN = "Fallen";
    static final String TAG_ENTITY_ID = "EntityId";
    /** Where an entry set aside stood in the list of givers killed. */
    static final String TAG_FALLEN_INDEX = "FallenIndex";

    private final Set<String> fallen = new LinkedHashSet<String>();
    private final List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
    private NBTTagCompound preserved;

    public FallenGiversWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.fallen.clear();
        this.quarantined.clear();
        this.preserved = null;
        if (compound.getInteger(TAG_VERSION) != DATA_VERSION) {
            preserve(compound, "it is of version "
                    + compound.getInteger(TAG_VERSION));
            return;
        }
        if (!compound.hasKey(TAG_FALLEN, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(compound, TAG_FALLEN, MAX_ENTRIES)) {
            preserve(compound, "its givers are no list of at most "
                    + MAX_ENTRIES + " entries");
            return;
        }
        NbtQuarantine.Read stored = NbtQuarantine.read(compound);
        if (!stored.isSupported()) {
            preserve(compound, "its quarantine cannot be read");
            return;
        }
        List<NBTTagCompound> aside = new ArrayList<NBTTagCompound>(stored.getEntries());
        NBTTagList list = compound.getTagList(TAG_FALLEN, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String entityId = entry.getString(TAG_ENTITY_ID);
            String problem = !entry.hasKey(TAG_ENTITY_ID, Constants.NBT.TAG_STRING)
                    ? "missing_entity_id"
                    : !isEntityId(entityId) ? "unreadable_entity_id"
                    : this.fallen.contains(entityId) ? "duplicate_giver" : "";
            if (problem.length() > 0) {
                aside.add(NbtQuarantine.entry(problem, TAG_FALLEN_INDEX, index, entry));
                continue;
            }
            this.fallen.add(entityId);
        }
        if (aside.size() > NbtQuarantine.MAX_ENTRIES) {
            this.fallen.clear();
            preserve(compound, "its quarantine would hold " + aside.size()
                    + " entries, past its " + NbtQuarantine.MAX_ENTRIES);
            return;
        }
        this.quarantined.addAll(aside);
        if (aside.size() > stored.getEntries().size()) {
            markDirty();
        }
    }

    private void preserve(NBTTagCompound compound, String because) {
        this.preserved = (NBTTagCompound) compound.copy();
        LostTalesLog.warning("The record of quest givers killed cannot be read,"
                + " since %s; it is kept as it is and records no more", because);
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.preserved != null) {
            NbtTags.copyContents(this.preserved, compound);
            return;
        }
        compound.setInteger(TAG_VERSION, DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (String entityId : this.fallen) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(TAG_ENTITY_ID, entityId);
            list.appendTag(tag);
        }
        compound.setTag(TAG_FALLEN, list);
        NbtQuarantine.write(compound, this.quarantined);
    }

    /** Records a giver killed; past {@link #MAX_ENTRIES} the oldest goes. Nothing while read-only. */
    public synchronized boolean record(String entityId) {
        if (this.preserved != null || !isEntityId(entityId)) {
            return false;
        }
        this.fallen.remove(entityId);
        while (this.fallen.size() >= MAX_ENTRIES) {
            Iterator<String> oldest = this.fallen.iterator();
            oldest.next();
            oldest.remove();
        }
        this.fallen.add(entityId);
        markDirty();
        return true;
    }

    /** Whether the giver with this entity id was killed. */
    public synchronized boolean hasFallen(String entityId) {
        return entityId != null && this.fallen.contains(entityId);
    }

    /** The overworld's record, made on first use; null off the server. */
    public static FallenGiversWorldData of(World world) {
        if (world == null || world.isRemote) {
            return null;
        }
        MapStorage storage;
        try {
            storage = LostTalesDimensionHelper.overworld(world).mapStorage;
        } catch (IllegalStateException noOverworld) {
            return null;
        }
        if (storage == null) {
            return null;
        }
        FallenGiversWorldData data = (FallenGiversWorldData) storage.loadData(
                FallenGiversWorldData.class, DATA_NAME);
        if (data == null) {
            data = new FallenGiversWorldData(DATA_NAME);
            storage.setData(DATA_NAME, data);
        }
        return data;
    }

    private static boolean isEntityId(String value) {
        if (value == null || value.length() == 0 || value.length() > 64) {
            return false;
        }
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException notAnId) {
            return false;
        }
    }
}
