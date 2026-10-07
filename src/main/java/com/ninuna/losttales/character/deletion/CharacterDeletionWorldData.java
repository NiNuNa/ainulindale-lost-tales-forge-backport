package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterNbtCodec;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned, fail-closed recovery journal for deleted characters. */
public final class CharacterDeletionWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_character_deletions";
    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_TOMBSTONES_PER_OWNER = 128;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_TOMBSTONES = "Tombstones";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_CHARACTER = "Character";
    private static final String TAG_STATE_GENERATION = "StateGeneration";
    private static final String TAG_PREPARED_AT = "PreparedAt";
    private static final String TAG_DELETED_AT = "DeletedAt";
    private static final String TAG_PURGE_AFTER = "PurgeAfter";

    private final Map<UUID, CharacterDeletionTombstone> tombstones =
            new LinkedHashMap<UUID, CharacterDeletionTombstone>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public CharacterDeletionWorldData(String name) {
        super(name);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.tombstones.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        if (compound == null) {
            markDirty();
            return;
        }
        int version = compound.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? compound.getInteger(TAG_DATA_VERSION) : 0;
        // Only this build's version is read; any other is kept as it is
        // and the store goes read-only, as every character store does.
        if (version != CURRENT_DATA_VERSION) {
            preserveReadOnly(compound, version);
            return;
        }

        boolean repaired = false;
        if (compound.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_LIST)) {
            NBTTagList quarantine = compound.getTagList(
                    TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < quarantine.tagCount(); index++) {
                this.quarantinedEntries.add((NBTTagCompound)
                        quarantine.getCompoundTagAt(index).copy());
            }
        } else if (compound.hasKey(TAG_QUARANTINE)) {
            repaired = true;
        }

        if (!compound.hasKey(TAG_TOMBSTONES, Constants.NBT.TAG_LIST)) {
            if (compound.hasKey(TAG_TOMBSTONES)) {
                repaired = true;
            }
            if (repaired) {
                markDirty();
            }
            return;
        }

        NBTTagList list = compound.getTagList(
                TAG_TOMBSTONES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            int tombstoneVersion = raw.hasKey(
                    TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                    ? raw.getInteger(TAG_DATA_VERSION) : 0;
            if (tombstoneVersion
                    != CharacterDeletionTombstone.CURRENT_DATA_VERSION) {
                preserveReadOnly(compound, tombstoneVersion);
                return;
            }
            if (raw.hasKey(TAG_CHARACTER, Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound character = raw.getCompoundTag(TAG_CHARACTER);
                int characterVersion = character.hasKey(
                        TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                        ? character.getInteger(TAG_DATA_VERSION) : 0;
                if (characterVersion != RoleplayCharacter.CURRENT_DATA_VERSION) {
                    preserveReadOnly(compound, characterVersion);
                    return;
                }
            }
        }

        Set<UUID> rejectedCharacterIds = new HashSet<UUID>();
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            try {
                CharacterDeletionTombstone tombstone = readTombstone(raw);
                if (rejectedCharacterIds.contains(tombstone.getCharacterId())) {
                    quarantine(raw, "repeated_duplicate_character");
                    repaired = true;
                    continue;
                }
                CharacterDeletionTombstone duplicate = this.tombstones.remove(
                        tombstone.getCharacterId());
                if (duplicate != null) {
                    rejectedCharacterIds.add(tombstone.getCharacterId());
                    quarantine(writeTombstone(duplicate),
                            "duplicate_character_existing");
                    quarantine(raw, "duplicate_character");
                    repaired = true;
                    continue;
                }
                if (getTombstones(tombstone.getOwnerId()).size()
                        >= MAX_TOMBSTONES_PER_OWNER) {
                    quarantine(raw, "owner_tombstone_limit_exceeded");
                    repaired = true;
                    continue;
                }
                this.tombstones.put(tombstone.getCharacterId(), tombstone);
            } catch (RuntimeException exception) {
                quarantine(raw, "malformed_tombstone");
                repaired = true;
                LostTalesLog.warning("Quarantined malformed character deletion tombstone at index %d: %s",
                        Integer.valueOf(index), exception.toString());
            }
        }
        if (repaired) {
            markDirty();
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            NbtTags.copyContents(this.preservedNewerData, compound);
            return;
        }
        compound.setInteger(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        ArrayList<CharacterDeletionTombstone> sorted =
                new ArrayList<CharacterDeletionTombstone>(
                        this.tombstones.values());
        Collections.sort(sorted, new Comparator<CharacterDeletionTombstone>() {
            @Override
            public int compare(CharacterDeletionTombstone left,
                               CharacterDeletionTombstone right) {
                return left.getCharacterId().toString().compareTo(
                        right.getCharacterId().toString());
            }
        });
        NBTTagList list = new NBTTagList();
        for (CharacterDeletionTombstone tombstone : sorted) {
            list.appendTag(writeTombstone(tombstone));
        }
        compound.setTag(TAG_TOMBSTONES, list);

        NBTTagList quarantine = new NBTTagList();
        for (NBTTagCompound entry : this.quarantinedEntries) {
            quarantine.appendTag(entry.copy());
        }
        compound.setTag(TAG_QUARANTINE, quarantine);
    }

    public boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public CharacterDeletionTombstone getTombstone(UUID characterId) {
        return characterId == null ? null : this.tombstones.get(characterId);
    }

    public List<CharacterDeletionTombstone> getTombstones(UUID ownerId) {
        ArrayList<CharacterDeletionTombstone> result =
                new ArrayList<CharacterDeletionTombstone>();
        if (ownerId != null) {
            for (CharacterDeletionTombstone tombstone
                    : this.tombstones.values()) {
                if (ownerId.equals(tombstone.getOwnerId())) {
                    result.add(tombstone);
                }
            }
        }
        Collections.sort(result, new Comparator<CharacterDeletionTombstone>() {
            @Override
            public int compare(CharacterDeletionTombstone left,
                               CharacterDeletionTombstone right) {
                return left.getCharacterId().toString().compareTo(
                        right.getCharacterId().toString());
            }
        });
        return Collections.unmodifiableList(result);
    }

    /**
     * Committed tombstones past their retention, soonest due first, at
     * most {@code limit}: the owner's, or everyone's for a null owner.
     */
    public List<CharacterDeletionTombstone> getExpired(UUID ownerId, long now,
                                                      int limit) {
        ArrayList<CharacterDeletionTombstone> due =
                new ArrayList<CharacterDeletionTombstone>();
        for (CharacterDeletionTombstone tombstone : this.tombstones.values()) {
            if ((ownerId == null || ownerId.equals(tombstone.getOwnerId()))
                    && tombstone.isPurgeAllowed(now)) {
                due.add(tombstone);
            }
        }
        sortByPurgeTime(due);
        return due.size() <= Math.max(0, limit) ? due
                : new ArrayList<CharacterDeletionTombstone>(
                        due.subList(0, Math.max(0, limit)));
    }

    /** Orders tombstones soonest purged first, then by character id. */
    public static void sortByPurgeTime(List<CharacterDeletionTombstone> tombstones) {
        Collections.sort(tombstones, new Comparator<CharacterDeletionTombstone>() {
            @Override
            public int compare(CharacterDeletionTombstone left,
                               CharacterDeletionTombstone right) {
                if (left.getPurgeAfter() != right.getPurgeAfter()) {
                    return left.getPurgeAfter() < right.getPurgeAfter() ? -1 : 1;
                }
                return left.getCharacterId().toString().compareTo(
                        right.getCharacterId().toString());
            }
        });
    }

    public int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    /** Creates or refreshes an uncommitted deletion journal entry. */
    public void savePrepared(CharacterDeletionTombstone tombstone) {
        ensureWritable();
        if (tombstone == null || tombstone.isCommitted()) {
            throw new IllegalArgumentException(
                    "A prepared deletion tombstone is required");
        }
        CharacterDeletionTombstone existing =
                this.tombstones.get(tombstone.getCharacterId());
        if (existing != null && existing.isCommitted()) {
            throw new IllegalStateException(
                    "A committed deletion tombstone already exists for "
                            + tombstone.getCharacterId());
        }
        if (existing == null
                && getTombstones(tombstone.getOwnerId()).size()
                >= MAX_TOMBSTONES_PER_OWNER) {
            throw new IllegalStateException(
                    "The account has reached the recoverable deletion limit");
        }
        this.tombstones.put(tombstone.getCharacterId(), tombstone);
        markDirty();
    }

    public void saveTombstone(CharacterDeletionTombstone tombstone) {
        ensureWritable();
        if (tombstone == null) {
            throw new IllegalArgumentException("tombstone must not be null");
        }
        this.tombstones.put(tombstone.getCharacterId(), tombstone);
        markDirty();
    }

    public CharacterDeletionTombstone removeTombstone(UUID characterId) {
        ensureWritable();
        CharacterDeletionTombstone removed = characterId == null
                ? null : this.tombstones.remove(characterId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    private static NBTTagCompound writeTombstone(
            CharacterDeletionTombstone tombstone) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION,
                CharacterDeletionTombstone.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_OWNER_UUID, tombstone.getOwnerId());
        tag.setTag(TAG_CHARACTER, CharacterNbtCodec.writeCharacterRecord(
                tombstone.getCharacterCopy()));
        tag.setLong(TAG_STATE_GENERATION, tombstone.getStateGeneration());
        tag.setLong(TAG_PREPARED_AT, tombstone.getPreparedAt());
        tag.setLong(TAG_DELETED_AT, tombstone.getDeletedAt());
        tag.setLong(TAG_PURGE_AFTER, tombstone.getPurgeAfter());
        return tag;
    }

    private static CharacterDeletionTombstone readTombstone(
            NBTTagCompound tag) {
        UUID ownerId = NbtTags.readUuid(tag, TAG_OWNER_UUID);
        if (ownerId == null
                || !tag.hasKey(TAG_CHARACTER, Constants.NBT.TAG_COMPOUND)
                || !tag.hasKey(TAG_STATE_GENERATION, Constants.NBT.TAG_LONG)
                || !tag.hasKey(TAG_PREPARED_AT, Constants.NBT.TAG_LONG)
                || tag.hasKey(TAG_DELETED_AT)
                && !tag.hasKey(TAG_DELETED_AT, Constants.NBT.TAG_LONG)
                || tag.hasKey(TAG_PURGE_AFTER)
                && !tag.hasKey(TAG_PURGE_AFTER, Constants.NBT.TAG_LONG)) {
            throw new IllegalArgumentException(
                    "Deletion tombstone is incomplete");
        }
        RoleplayCharacter character = CharacterNbtCodec.readCharacterRecord(
                tag.getCompoundTag(TAG_CHARACTER), ownerId);
        return new CharacterDeletionTombstone(
                ownerId,
                character,
                tag.getLong(TAG_STATE_GENERATION),
                tag.getLong(TAG_PREPARED_AT),
                tag.getLong(TAG_DELETED_AT),
                tag.getLong(TAG_PURGE_AFTER));
    }

    private void preserveReadOnly(NBTTagCompound compound, int version) {
        this.tombstones.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = true;
        this.unsupportedDataVersion = version;
        this.preservedNewerData = (NBTTagCompound) compound.copy();
    }

    private void quarantine(NBTTagCompound original, String reason) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Reason", reason == null ? "unknown" : reason);
        entry.setTag("OriginalData", original == null
                ? new NBTTagCompound() : original.copy());
        this.quarantinedEntries.add(entry);
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Character deletion data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }
}
