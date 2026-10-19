package com.ninuna.losttales.quest;

import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

/**
 * The quest givers killed in this world, by their entity id, with when
 * they fell (QUEST-32). A quest that fails when its giver is killed fails
 * for a player online as it happens, and for one who was away at their
 * next login, read from here. The latest {@link #MAX_ENTRIES} are kept.
 * Data of another version, or not shaped as written, is kept as it was
 * and nothing more is recorded; an entry that cannot be read is kept
 * aside, untouched.
 */
public final class FallenGiversWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_fallen_givers";
    static final int DATA_VERSION = 1;
    public static final int MAX_ENTRIES = 4096;
    static final String TAG_VERSION = "DataVersion";
    static final String TAG_FALLEN = "Fallen";
    static final String TAG_QUARANTINED = "Quarantined";
    static final String TAG_ENTITY_ID = "EntityId";
    static final String TAG_FELL_AT = "FellAt";

    private final Map<String, Long> fallen = new LinkedHashMap<String, Long>();
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
        if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                || compound.hasKey(TAG_FALLEN)
                && !compound.hasKey(TAG_FALLEN, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(compound, TAG_FALLEN, MAX_ENTRIES)
                || !NbtTags.hasCompoundListWithinLimit(compound, TAG_QUARANTINED, MAX_ENTRIES)) {
            this.preserved = (NBTTagCompound) compound.copy();
            return;
        }
        NBTTagList list = compound.getTagList(TAG_FALLEN, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String entityId = entry.getString(TAG_ENTITY_ID);
            if (!isEntityId(entityId) || !entry.hasKey(TAG_FELL_AT, Constants.NBT.TAG_LONG)
                    || this.fallen.containsKey(entityId)) {
                this.quarantined.add((NBTTagCompound) entry.copy());
                continue;
            }
            this.fallen.put(entityId, Long.valueOf(entry.getLong(TAG_FELL_AT)));
        }
        NBTTagList aside = compound.getTagList(TAG_QUARANTINED, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < aside.tagCount(); index++) {
            this.quarantined.add((NBTTagCompound) aside.getCompoundTagAt(index).copy());
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.preserved != null) {
            NbtTags.copyContents(this.preserved, compound);
            return;
        }
        compound.setInteger(TAG_VERSION, DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Long> entry : this.fallen.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(TAG_ENTITY_ID, entry.getKey());
            tag.setLong(TAG_FELL_AT, entry.getValue().longValue());
            list.appendTag(tag);
        }
        compound.setTag(TAG_FALLEN, list);
        if (!this.quarantined.isEmpty()) {
            NBTTagList aside = new NBTTagList();
            for (NBTTagCompound entry : this.quarantined) {
                aside.appendTag(entry.copy());
            }
            compound.setTag(TAG_QUARANTINED, aside);
        }
    }

    /** Records a giver killed; past {@link #MAX_ENTRIES} the oldest goes. Nothing while read-only. */
    public synchronized boolean record(String entityId, long fellAt) {
        if (this.preserved != null || !isEntityId(entityId)) {
            return false;
        }
        this.fallen.remove(entityId);
        while (this.fallen.size() >= MAX_ENTRIES) {
            Iterator<String> oldest = this.fallen.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        this.fallen.put(entityId, Long.valueOf(fellAt));
        markDirty();
        return true;
    }

    /** Whether the giver with this entity id was killed. */
    public synchronized boolean hasFallen(String entityId) {
        return entityId != null && this.fallen.containsKey(entityId);
    }

    public synchronized boolean isUsable() {
        return this.preserved == null;
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
