package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.storage.CharacterNbtCodec;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTBase;
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

    public CharacterDeletionWorldData() {
        this(DATA_NAME);
    }

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
        if (version < 0 || version > CURRENT_DATA_VERSION) {
            preserveReadOnly(compound, version);
            return;
        }

        boolean repaired = version < CURRENT_DATA_VERSION;
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
            if (tombstoneVersion < 0
                    || tombstoneVersion
                    > CharacterDeletionTombstone.CURRENT_DATA_VERSION) {
                preserveReadOnly(compound, tombstoneVersion);
                return;
            }
            if (raw.hasKey(TAG_CHARACTER, Constants.NBT.TAG_COMPOUND)) {
                NBTTagCompound character = raw.getCompoundTag(TAG_CHARACTER);
                int characterVersion = character.hasKey(
                        TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                        ? character.getInteger(TAG_DATA_VERSION) : 0;
                if (characterVersion < 0
                        || characterVersion > RoleplayCharacter.CURRENT_DATA_VERSION) {
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
                warn("Quarantined malformed character deletion tombstone at index %d: %s",
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
            copyTagContents(this.preservedNewerData, compound);
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

    public int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
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

    public int getTombstoneCount() {
        return this.tombstones.size();
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
        writeUuid(tag, TAG_OWNER_UUID, tombstone.getOwnerId());
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
        UUID ownerId = readUuid(tag, TAG_OWNER_UUID);
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

    private static void warn(String message, Object... arguments) {
        Object[] allArguments = new Object[arguments.length + 1];
        allArguments[0] = LostTalesMetaData.MOD_ID;
        System.arraycopy(arguments, 0, allArguments, 1, arguments.length);
        try {
            FMLLog.warning("[%s] " + message, allArguments);
        } catch (Throwable ignored) {
            // Storage recovery remains authoritative even before FML logging
            // is available to standalone validation and repair tooling.
        }
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        if (uuid != null) {
            tag.setLong(key + "Most", uuid.getMostSignificantBits());
            tag.setLong(key + "Least", uuid.getLeastSignificantBits());
        }
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        String most = key + "Most";
        String least = key + "Least";
        if (!tag.hasKey(most, Constants.NBT.TAG_LONG)
                || !tag.hasKey(least, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(most), tag.getLong(least));
    }

    private static void copyTagContents(NBTTagCompound source,
                                        NBTTagCompound destination) {
        Set<?> keys = source.func_150296_c();
        for (Object keyObject : keys) {
            if (keyObject instanceof String) {
                String key = (String) keyObject;
                NBTBase value = source.getTag(key);
                if (value != null) {
                    destination.setTag(key, value.copy());
                }
            }
        }
    }
}
