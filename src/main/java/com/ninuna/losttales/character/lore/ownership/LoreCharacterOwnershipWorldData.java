package com.ninuna.losttales.character.lore.ownership;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.lore.LoreCharacterDefinition;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Durable one-record-per-identity ownership index.
 *
 * Mutations are synchronized and revision-checked. Corrupt or duplicate saved
 * ownership records make the entire store read-only: silently discarding one
 * could make an already-owned identity appear available and permit duplication.
 */
public final class LoreCharacterOwnershipWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_lore_character_ownership";
    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_RECORDS = 4096;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_RECORDS = "Records";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_LORE_CHARACTER_ID = "LoreCharacterId";
    private static final String TAG_CHARACTER_UUID = "CharacterUUID";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_REVISION = "Revision";
    private static final String TAG_CREATED_AT = "CreatedAt";
    private static final String TAG_LAST_CLAIMED_AT = "LastClaimedAt";
    private static final String TAG_LAST_RELEASED_AT = "LastReleasedAt";

    private final Map<String, LoreCharacterOwnershipRecord> recordsByLoreId =
            new LinkedHashMap<String, LoreCharacterOwnershipRecord>();
    private final Map<UUID, LoreCharacterOwnershipRecord> recordsByCharacterId =
            new LinkedHashMap<UUID, LoreCharacterOwnershipRecord>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    private boolean readOnly;
    private int unsupportedDataVersion = -1;
    private String readOnlyReason = "";
    private NBTTagCompound preservedData;

    public LoreCharacterOwnershipWorldData() {
        this(DATA_NAME);
    }

    public LoreCharacterOwnershipWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        reset();
        if (compound == null || compound.func_150296_c().isEmpty()) {
            markDirty();
            return;
        }
        if (!compound.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)) {
            failReadOnly(compound, -1, "missing_data_version", compound);
            return;
        }
        int version = compound.getInteger(TAG_DATA_VERSION);
        if (version != CURRENT_DATA_VERSION) {
            failReadOnly(compound, version, "unsupported_data_version", compound);
            return;
        }
        if (!compound.hasKey(TAG_RECORDS, Constants.NBT.TAG_LIST)) {
            failReadOnly(compound, version, "missing_or_invalid_records", compound);
            return;
        }
        if (compound.hasKey(TAG_QUARANTINE)
                && !compound.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_LIST)) {
            failReadOnly(compound, version, "invalid_quarantine", compound);
            return;
        }
        if (compound.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_LIST)) {
            NBTTagList quarantine = compound.getTagList(
                    TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < quarantine.tagCount(); index++) {
                this.quarantinedEntries.add((NBTTagCompound)
                        quarantine.getCompoundTagAt(index).copy());
            }
        }

        NBTTagList records = compound.getTagList(
                TAG_RECORDS, Constants.NBT.TAG_COMPOUND);
        if (records.tagCount() > MAX_RECORDS) {
            failReadOnly(compound, version, "record_limit_exceeded", compound);
            return;
        }
        for (int index = 0; index < records.tagCount(); index++) {
            NBTTagCompound raw = records.getCompoundTagAt(index);
            LoreCharacterOwnershipRecord record;
            try {
                record = readRecord(raw);
            } catch (RuntimeException exception) {
                failReadOnly(compound, version,
                        "malformed_record_" + index, raw);
                warn("Lore-character ownership record %d is malformed; storage is read-only: %s",
                        Integer.valueOf(index), exception.toString());
                return;
            }
            if (this.recordsByLoreId.containsKey(record.getLoreCharacterId())) {
                failReadOnly(compound, version,
                        "duplicate_lore_character_id", raw);
                return;
            }
            if (this.recordsByCharacterId.containsKey(record.getCharacterId())) {
                failReadOnly(compound, version,
                        "duplicate_character_uuid", raw);
                return;
            }
            this.recordsByLoreId.put(record.getLoreCharacterId(), record);
            this.recordsByCharacterId.put(record.getCharacterId(), record);
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnly && this.preservedData != null) {
            copyTagContents(this.preservedData, compound);
            return;
        }
        compound.setInteger(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        List<LoreCharacterOwnershipRecord> records =
                new ArrayList<LoreCharacterOwnershipRecord>(
                        this.recordsByLoreId.values());
        Collections.sort(records,
                new Comparator<LoreCharacterOwnershipRecord>() {
                    @Override
                    public int compare(LoreCharacterOwnershipRecord left,
                                       LoreCharacterOwnershipRecord right) {
                        return left.getLoreCharacterId().compareTo(
                                right.getLoreCharacterId());
                    }
                });
        NBTTagList recordList = new NBTTagList();
        for (LoreCharacterOwnershipRecord record : records) {
            recordList.appendTag(writeRecord(record));
        }
        compound.setTag(TAG_RECORDS, recordList);

        NBTTagList quarantine = new NBTTagList();
        for (NBTTagCompound entry : this.quarantinedEntries) {
            quarantine.appendTag(entry.copy());
        }
        compound.setTag(TAG_QUARANTINE, quarantine);
    }

    public synchronized boolean isReadOnly() {
        return this.readOnly;
    }

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public synchronized String getReadOnlyReason() {
        return this.readOnlyReason;
    }

    public synchronized LoreCharacterOwnershipRecord getRecord(
            String loreCharacterId) {
        return this.recordsByLoreId.get(
                LoreCharacterOwnershipRecord.normalizeIdentifier(
                        loreCharacterId));
    }

    public synchronized LoreCharacterOwnershipRecord getRecordByCharacterId(
            UUID characterId) {
        return characterId == null ? null
                : this.recordsByCharacterId.get(characterId);
    }

    public synchronized Collection<LoreCharacterOwnershipRecord> getRecords() {
        return Collections.unmodifiableList(
                new ArrayList<LoreCharacterOwnershipRecord>(
                        this.recordsByLoreId.values()));
    }

    public synchronized int getRecordCount() {
        return this.recordsByLoreId.size();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    public synchronized List<NBTTagCompound> getQuarantinedEntriesCopy() {
        List<NBTTagCompound> result = new ArrayList<NBTTagCompound>();
        for (NBTTagCompound entry : this.quarantinedEntries) {
            result.add((NBTTagCompound) entry.copy());
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Atomically claims an identity when the caller has the current revision.
     * Revision zero represents an identity which has never been claimed.
     */
    public synchronized LoreCharacterOwnershipResult tryClaim(
            String loreCharacterId,
            UUID ownerId,
            UUID proposedCharacterId,
            long expectedRevision,
            long timestamp) {
        if (this.readOnly) {
            return result(LoreCharacterOwnershipResult.Status.STORAGE_READ_ONLY,
                    getRecord(loreCharacterId));
        }
        String normalizedLoreId =
                LoreCharacterOwnershipRecord.normalizeIdentifier(
                        loreCharacterId);
        if (!LoreCharacterOwnershipRecord.isValidIdentifier(normalizedLoreId)
                || ownerId == null || expectedRevision < 0L) {
            return result(LoreCharacterOwnershipResult.Status.INVALID_REQUEST,
                    getRecord(normalizedLoreId));
        }
        LoreCharacterRegistry.ensureLoaded();
        if (!LoreCharacterRegistry.getLoadErrors().isEmpty()) {
            return result(
                    LoreCharacterOwnershipResult.Status.DEFINITION_REGISTRY_INVALID,
                    getRecord(normalizedLoreId));
        }
        LoreCharacterDefinition definition =
                LoreCharacterRegistry.get(normalizedLoreId);
        if (definition == null) {
            return result(
                    LoreCharacterOwnershipResult.Status.UNKNOWN_LORE_CHARACTER,
                    getRecord(normalizedLoreId));
        }
        if (!definition.hasAppearance()) {
            return result(
                    LoreCharacterOwnershipResult.Status.APPEARANCE_NOT_CONFIGURED,
                    getRecord(normalizedLoreId));
        }

        LoreCharacterOwnershipRecord existing =
                this.recordsByLoreId.get(normalizedLoreId);
        if (existing != null && existing.isClaimed()) {
            return result(ownerId.equals(existing.getOwnerId())
                            ? LoreCharacterOwnershipResult.Status.ALREADY_OWNED_BY_REQUESTER
                            : LoreCharacterOwnershipResult.Status.ALREADY_CLAIMED,
                    existing);
        }
        long actualRevision = existing == null ? 0L : existing.getRevision();
        if (expectedRevision != actualRevision) {
            return result(LoreCharacterOwnershipResult.Status.STALE_REVISION,
                    existing);
        }

        LoreCharacterOwnershipRecord claimed;
        if (existing == null) {
            if (proposedCharacterId == null) {
                return result(
                        LoreCharacterOwnershipResult.Status.INVALID_REQUEST, null);
            }
            if (this.recordsByCharacterId.containsKey(proposedCharacterId)) {
                return result(
                        LoreCharacterOwnershipResult.Status.CHARACTER_ID_CONFLICT,
                        null);
            }
            if (this.recordsByLoreId.size() >= MAX_RECORDS) {
                return result(
                        LoreCharacterOwnershipResult.Status.RECORD_LIMIT_REACHED,
                        null);
            }
            claimed = LoreCharacterOwnershipRecord.firstClaim(
                    normalizedLoreId, proposedCharacterId,
                    ownerId, timestamp);
        } else {
            claimed = existing.claim(ownerId, timestamp);
            this.recordsByCharacterId.remove(existing.getCharacterId());
        }
        this.recordsByLoreId.put(normalizedLoreId, claimed);
        this.recordsByCharacterId.put(claimed.getCharacterId(), claimed);
        markDirty();
        return result(LoreCharacterOwnershipResult.Status.CLAIMED, claimed);
    }

    /** Atomically releases an identity only for its current owner/revision. */
    public synchronized LoreCharacterOwnershipResult tryRelease(
            String loreCharacterId,
            UUID ownerId,
            long expectedRevision,
            long timestamp) {
        if (this.readOnly) {
            return result(LoreCharacterOwnershipResult.Status.STORAGE_READ_ONLY,
                    getRecord(loreCharacterId));
        }
        String normalizedLoreId =
                LoreCharacterOwnershipRecord.normalizeIdentifier(
                        loreCharacterId);
        if (!LoreCharacterOwnershipRecord.isValidIdentifier(normalizedLoreId)
                || ownerId == null || expectedRevision < 0L) {
            return result(LoreCharacterOwnershipResult.Status.INVALID_REQUEST,
                    getRecord(normalizedLoreId));
        }
        LoreCharacterOwnershipRecord existing =
                this.recordsByLoreId.get(normalizedLoreId);
        if (existing == null) {
            return result(LoreCharacterOwnershipResult.Status.NOT_CLAIMED, null);
        }
        if (!existing.isClaimed()) {
            return result(LoreCharacterOwnershipResult.Status.ALREADY_RELEASED,
                    existing);
        }
        if (!ownerId.equals(existing.getOwnerId())) {
            return result(LoreCharacterOwnershipResult.Status.NOT_OWNER,
                    existing);
        }
        if (expectedRevision != existing.getRevision()) {
            return result(LoreCharacterOwnershipResult.Status.STALE_REVISION,
                    existing);
        }

        LoreCharacterOwnershipRecord released =
                existing.release(ownerId, timestamp);
        this.recordsByLoreId.put(normalizedLoreId, released);
        this.recordsByCharacterId.put(released.getCharacterId(), released);
        markDirty();
        return result(LoreCharacterOwnershipResult.Status.RELEASED, released);
    }

    private static LoreCharacterOwnershipResult result(
            LoreCharacterOwnershipResult.Status status,
            LoreCharacterOwnershipRecord record) {
        return LoreCharacterOwnershipResult.of(status, record);
    }

    private static NBTTagCompound writeRecord(
            LoreCharacterOwnershipRecord record) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION,
                LoreCharacterOwnershipRecord.CURRENT_DATA_VERSION);
        tag.setString(TAG_LORE_CHARACTER_ID, record.getLoreCharacterId());
        writeUuid(tag, TAG_CHARACTER_UUID, record.getCharacterId());
        writeUuid(tag, TAG_OWNER_UUID, record.getOwnerId());
        tag.setLong(TAG_REVISION, record.getRevision());
        tag.setLong(TAG_CREATED_AT, record.getCreatedAt());
        tag.setLong(TAG_LAST_CLAIMED_AT, record.getLastClaimedAt());
        tag.setLong(TAG_LAST_RELEASED_AT, record.getLastReleasedAt());
        return tag;
    }

    private static LoreCharacterOwnershipRecord readRecord(
            NBTTagCompound tag) {
        if (tag == null
                || !tag.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                || tag.getInteger(TAG_DATA_VERSION)
                != LoreCharacterOwnershipRecord.CURRENT_DATA_VERSION
                || !tag.hasKey(TAG_LORE_CHARACTER_ID, Constants.NBT.TAG_STRING)
                || !tag.hasKey(TAG_REVISION, Constants.NBT.TAG_LONG)
                || !tag.hasKey(TAG_CREATED_AT, Constants.NBT.TAG_LONG)
                || !tag.hasKey(TAG_LAST_CLAIMED_AT, Constants.NBT.TAG_LONG)
                || !tag.hasKey(TAG_LAST_RELEASED_AT, Constants.NBT.TAG_LONG)) {
            throw new IllegalArgumentException("ownership record is incomplete");
        }
        UUID characterId = readRequiredUuid(tag, TAG_CHARACTER_UUID);
        UUID ownerId = readOptionalUuid(tag, TAG_OWNER_UUID);
        return new LoreCharacterOwnershipRecord(
                tag.getString(TAG_LORE_CHARACTER_ID),
                characterId,
                ownerId,
                tag.getLong(TAG_REVISION),
                tag.getLong(TAG_CREATED_AT),
                tag.getLong(TAG_LAST_CLAIMED_AT),
                tag.getLong(TAG_LAST_RELEASED_AT));
    }

    private void reset() {
        this.recordsByLoreId.clear();
        this.recordsByCharacterId.clear();
        this.quarantinedEntries.clear();
        this.readOnly = false;
        this.unsupportedDataVersion = -1;
        this.readOnlyReason = "";
        this.preservedData = null;
    }

    private void failReadOnly(NBTTagCompound source, int version,
                              String reason, NBTTagCompound malformedEntry) {
        this.recordsByLoreId.clear();
        this.recordsByCharacterId.clear();
        this.readOnly = true;
        this.unsupportedDataVersion = version;
        this.readOnlyReason = reason == null ? "unknown" : reason;
        this.preservedData = source == null
                ? new NBTTagCompound() : (NBTTagCompound) source.copy();
        NBTTagCompound quarantine = new NBTTagCompound();
        quarantine.setString("Reason", this.readOnlyReason);
        quarantine.setTag("OriginalData", malformedEntry == null
                ? new NBTTagCompound() : malformedEntry.copy());
        this.quarantinedEntries.add(quarantine);
        warn("Lore-character ownership storage is read-only: %s",
                this.readOnlyReason);
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        if (uuid != null) {
            tag.setLong(key + "Most", uuid.getMostSignificantBits());
            tag.setLong(key + "Least", uuid.getLeastSignificantBits());
        }
    }

    private static UUID readRequiredUuid(NBTTagCompound tag, String key) {
        UUID value = readOptionalUuid(tag, key);
        if (value == null) {
            throw new IllegalArgumentException(key + " is required");
        }
        return value;
    }

    private static UUID readOptionalUuid(NBTTagCompound tag, String key) {
        String most = key + "Most";
        String least = key + "Least";
        boolean hasMost = tag.hasKey(most, Constants.NBT.TAG_LONG);
        boolean hasLeast = tag.hasKey(least, Constants.NBT.TAG_LONG);
        if (hasMost != hasLeast) {
            throw new IllegalArgumentException(key + " is incomplete");
        }
        return hasMost ? new UUID(tag.getLong(most), tag.getLong(least)) : null;
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

    private static void warn(String message, Object... arguments) {
        Object[] all = new Object[arguments.length + 1];
        all[0] = LostTalesMetaData.MOD_ID;
        System.arraycopy(arguments, 0, all, 1, arguments.length);
        try {
            FMLLog.warning("[%s] " + message, all);
        } catch (Throwable ignored) {}
    }
}
