package com.ninuna.losttales.character.state;

import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * When each identity was last played or heard on this server, so a member
 * list names under *Offline* only the characters somebody has met lately
 * and never a character its owner has kept to themselves. An identity is seen when its player logs out playing it,
 * switches away from it, or speaks as it; a time is written at most once
 * {@link #RECORD_EVERY_MILLIS} per identity, and one older than
 * {@link #FORGET_AFTER_MILLIS} is forgotten as the store loads.
 *
 * <p>Fails closed: a store of another data version is kept whole and says
 * nobody has been seen, so a member list shows fewer names, never more. A
 * record that cannot be read is quarantined.</p>
 */
public final class CharacterLastSeenWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_character_last_seen";
    static final int DATA_VERSION = 1;
    static final int MAX_ENTRIES = 65536;
    static final long RECORD_EVERY_MILLIS = 3600000L;
    static final long FORGET_AFTER_MILLIS = 90L * 24L * 3600000L;

    private static final String TAG_VERSION = "DataVersion";
    private static final String TAG_SEEN = "Seen";
    private static final String TAG_ID = "Id";
    private static final String TAG_AT = "At";

    private final Map<UUID, Long> seen = new LinkedHashMap<UUID, Long>();
    private final List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
    private NBTTagCompound unsupported;

    public CharacterLastSeenWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.seen.clear();
        this.quarantined.clear();
        this.unsupported = null;
        NbtQuarantine.Read quarantine = NbtQuarantine.readCurrentVersionOnly(compound);
        if (compound.getInteger(TAG_VERSION) != DATA_VERSION || !quarantine.isSupported()
                || !compound.hasKey(TAG_SEEN, Constants.NBT.TAG_LIST)) {
            this.unsupported = (NBTTagCompound) compound.copy();
            return;
        }
        this.quarantined.addAll(quarantine.getEntries());
        long now = System.currentTimeMillis();
        NBTTagList list = compound.getTagList(TAG_SEEN, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            UUID id = NbtTags.readUuid(entry, TAG_ID);
            if (id == null || !entry.hasKey(TAG_AT, Constants.NBT.TAG_LONG)
                    || this.seen.size() >= MAX_ENTRIES) {
                this.quarantined.add(NbtQuarantine.entry(
                        id == null ? "missing id" : "unreadable time", "Index", index, entry));
                markDirty();
                continue;
            }
            long at = entry.getLong(TAG_AT);
            if (now - at > FORGET_AFTER_MILLIS) {
                markDirty();
                continue;
            }
            this.seen.put(id, Long.valueOf(at));
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.unsupported != null) {
            NbtTags.copyContents(this.unsupported, compound);
            return;
        }
        compound.setInteger(TAG_VERSION, DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<UUID, Long> entry : this.seen.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            NbtTags.writeUuid(tag, TAG_ID, entry.getKey());
            tag.setLong(TAG_AT, entry.getValue().longValue());
            list.appendTag(tag);
        }
        compound.setTag(TAG_SEEN, list);
        NbtQuarantine.write(compound, this.quarantined);
    }

    /** The identity was played or heard at {@code now}. */
    public synchronized void saw(UUID identityId, long now) {
        if (identityId == null || this.unsupported != null) {
            return;
        }
        Long before = this.seen.get(identityId);
        if (before != null && now - before.longValue() < RECORD_EVERY_MILLIS) {
            return;
        }
        this.seen.remove(identityId);
        this.seen.put(identityId, Long.valueOf(now));
        Iterator<UUID> oldest = this.seen.keySet().iterator();
        while (this.seen.size() > MAX_ENTRIES && oldest.hasNext()) {
            oldest.next();
            oldest.remove();
        }
        markDirty();
    }

    /** Whether the identity was played or heard within {@code window} of {@code now}. */
    public synchronized boolean seenWithin(UUID identityId, long window, long now) {
        Long at = identityId == null ? null : this.seen.get(identityId);
        return at != null && now - at.longValue() <= window;
    }
}
