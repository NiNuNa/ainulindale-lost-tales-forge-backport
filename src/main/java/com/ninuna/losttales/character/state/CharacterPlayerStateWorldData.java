package com.ninuna.losttales.character.state;

import com.ninuna.losttales.LostTalesMetaData;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Versioned character-owned player snapshots, isolated from roster metadata. */
public final class CharacterPlayerStateWorldData extends WorldSavedData {

    public static final String DATA_NAME_PREFIX =
            "losttales_character_player_state_";
    public static final int CURRENT_DATA_VERSION = 12;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ACCOUNTS = "Accounts";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_BOOTSTRAP_VERSION = "BootstrapVersion";
    private static final String TAG_BOOTSTRAPPED_AT = "BootstrappedAt";
    private static final String TAG_RECORDS = "Records";
    private static final String TAG_CHARACTER_UUID = "CharacterUUID";
    private static final String TAG_CURRENT = "Current";
    private static final String TAG_PREVIOUS = "Previous";
    private static final String TAG_GENERATION = "Generation";
    private static final String TAG_CAPTURED_AT = "CapturedAt";
    private static final String TAG_COMPONENTS = "Components";

    private final Map<UUID, CharacterPlayerStateAccount> accounts =
            new LinkedHashMap<UUID, CharacterPlayerStateAccount>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    private final Set<UUID> blockedOwners = new HashSet<UUID>();
    private final UUID expectedOwnerId;
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public CharacterPlayerStateWorldData() {
        this(DATA_NAME_PREFIX + "unbound");
    }

    public CharacterPlayerStateWorldData(String name) {
        super(name);
        this.expectedOwnerId = parseOwnerId(name);
    }

    public static String dataName(UUID ownerId) {
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        return DATA_NAME_PREFIX + ownerId.toString().replace("-", "");
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.accounts.clear();
        this.quarantinedEntries.clear();
        this.blockedOwners.clear();
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
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = version;
            this.preservedNewerData = (NBTTagCompound) compound.copy();
            return;
        }

        boolean repaired = version < CURRENT_DATA_VERSION;
        if (compound.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_LIST)) {
            NBTTagList quarantine = compound.getTagList(
                    TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < quarantine.tagCount(); index++) {
                NBTTagCompound entry = (NBTTagCompound)
                        quarantine.getCompoundTagAt(index).copy();
                this.quarantinedEntries.add(entry);
                UUID blockedOwner = readUuid(entry, "BlockedOwnerUUID");
                if (blockedOwner != null) {
                    this.blockedOwners.add(blockedOwner);
                }
            }
        } else if (compound.hasKey(TAG_QUARANTINE)) {
            repaired = true;
        }

        if (!compound.hasKey(TAG_ACCOUNTS, Constants.NBT.TAG_LIST)) {
            if (compound.hasKey(TAG_ACCOUNTS)) {
                repaired = true;
            }
            if (repaired) {
                markDirty();
            }
            return;
        }

        NBTTagList accountList = compound.getTagList(
                TAG_ACCOUNTS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < accountList.tagCount(); index++) {
            NBTTagCompound raw = accountList.getCompoundTagAt(index);
            UUID ownerId = readUuid(raw, TAG_OWNER_UUID);
            try {
                CharacterPlayerStateAccount account = readAccount(raw);
                if (account == null) {
                    quarantine(raw, "invalid_account", ownerId);
                    repaired = true;
                    continue;
                }
                if (this.expectedOwnerId != null
                        && !this.expectedOwnerId.equals(account.getOwnerId())) {
                    quarantine(raw, "account_file_owner_mismatch",
                            this.expectedOwnerId);
                    repaired = true;
                    continue;
                }
                if (this.accounts.containsKey(account.getOwnerId())) {
                    CharacterPlayerStateAccount existing =
                            this.accounts.remove(account.getOwnerId());
                    quarantine(writeAccount(existing),
                            "duplicate_account_existing", account.getOwnerId());
                    quarantine(raw, "duplicate_account", account.getOwnerId());
                    repaired = true;
                    continue;
                }
                if (this.blockedOwners.contains(account.getOwnerId())) {
                    quarantine(raw, "owner_already_blocked", account.getOwnerId());
                    repaired = true;
                    continue;
                }
                this.accounts.put(account.getOwnerId(), account);
            } catch (UnsupportedVersionException exception) {
                this.readOnlyForNewerVersion = true;
                this.unsupportedDataVersion = exception.version;
                this.preservedNewerData = (NBTTagCompound) compound.copy();
                this.accounts.clear();
                this.quarantinedEntries.clear();
                this.blockedOwners.clear();
                return;
            } catch (RuntimeException exception) {
                CharacterPlayerStateAccount existing = ownerId == null
                        ? null : this.accounts.remove(ownerId);
                if (existing != null) {
                    quarantine(writeAccount(existing),
                            "malformed_account_existing", ownerId);
                }
                quarantine(raw, "malformed_account", ownerId);
                repaired = true;
                warn("Quarantined malformed character player state at index %d: %s",
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

        ArrayList<CharacterPlayerStateAccount> sorted =
                new ArrayList<CharacterPlayerStateAccount>(this.accounts.values());
        Collections.sort(sorted, new Comparator<CharacterPlayerStateAccount>() {
            @Override
            public int compare(CharacterPlayerStateAccount left,
                               CharacterPlayerStateAccount right) {
                return left.getOwnerId().toString().compareTo(
                        right.getOwnerId().toString());
            }
        });
        NBTTagList accountList = new NBTTagList();
        for (CharacterPlayerStateAccount account : sorted) {
            accountList.appendTag(writeAccount(account));
        }
        compound.setTag(TAG_ACCOUNTS, accountList);

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

    public boolean isOwnerBlocked(UUID ownerId) {
        return ownerId != null && this.blockedOwners.contains(ownerId);
    }

    public CharacterPlayerStateAccount getAccount(UUID ownerId) {
        if (!isExpectedOwner(ownerId)) {
            return null;
        }
        return this.accounts.get(ownerId);
    }

    public CharacterPlayerStateAccount getOrCreateAccount(UUID ownerId) {
        ensureWritable();
        requireExpectedOwner(ownerId);
        if (this.blockedOwners.contains(ownerId)) {
            throw new IllegalStateException(
                    "Character player state for owner " + ownerId + " is quarantined");
        }
        CharacterPlayerStateAccount account = this.accounts.get(ownerId);
        if (account == null) {
            account = new CharacterPlayerStateAccount(ownerId);
            this.accounts.put(ownerId, account);
            markDirty();
        }
        return account;
    }

    public void saveAccount(CharacterPlayerStateAccount account) {
        ensureWritable();
        if (account == null) {
            throw new IllegalArgumentException("account must not be null");
        }
        requireExpectedOwner(account.getOwnerId());
        if (this.blockedOwners.contains(account.getOwnerId())) {
            throw new IllegalStateException(
                    "Character player state for owner " + account.getOwnerId()
                            + " is quarantined");
        }
        this.accounts.put(account.getOwnerId(), account);
        markDirty();
    }

    public Collection<CharacterPlayerStateAccount> getAccounts() {
        return Collections.unmodifiableCollection(this.accounts.values());
    }

    public int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private boolean isExpectedOwner(UUID ownerId) {
        return ownerId != null
                && (this.expectedOwnerId == null
                || this.expectedOwnerId.equals(ownerId));
    }

    private void requireExpectedOwner(UUID ownerId) {
        if (!isExpectedOwner(ownerId)) {
            throw new IllegalArgumentException(
                    "Player-state file does not belong to owner " + ownerId);
        }
    }

    private static UUID parseOwnerId(String name) {
        if (name == null || !name.startsWith(DATA_NAME_PREFIX)) {
            return null;
        }
        String compact = name.substring(DATA_NAME_PREFIX.length());
        if (compact.length() != 32) {
            return null;
        }
        try {
            String canonical = compact.substring(0, 8) + "-"
                    + compact.substring(8, 12) + "-"
                    + compact.substring(12, 16) + "-"
                    + compact.substring(16, 20) + "-"
                    + compact.substring(20);
            return UUID.fromString(canonical);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static NBTTagCompound writeAccount(CharacterPlayerStateAccount account) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterPlayerStateAccount.CURRENT_DATA_VERSION);
        writeUuid(tag, TAG_OWNER_UUID, account.getOwnerId());
        tag.setInteger(TAG_BOOTSTRAP_VERSION, account.getBootstrapVersion());
        tag.setLong(TAG_BOOTSTRAPPED_AT, account.getBootstrappedAt());

        ArrayList<CharacterPlayerStateRecord> records =
                new ArrayList<CharacterPlayerStateRecord>(account.getRecords());
        Collections.sort(records, new Comparator<CharacterPlayerStateRecord>() {
            @Override
            public int compare(CharacterPlayerStateRecord left,
                               CharacterPlayerStateRecord right) {
                return left.getCharacterId().toString().compareTo(
                        right.getCharacterId().toString());
            }
        });
        NBTTagList recordList = new NBTTagList();
        for (CharacterPlayerStateRecord record : records) {
            recordList.appendTag(writeRecordCopy(record));
        }
        tag.setTag(TAG_RECORDS, recordList);
        return tag;
    }

    private static CharacterPlayerStateAccount readAccount(NBTTagCompound tag) {
        int version = tag.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? tag.getInteger(TAG_DATA_VERSION) : 0;
        if (version < 0 || version > CharacterPlayerStateAccount.CURRENT_DATA_VERSION) {
            throw new UnsupportedVersionException(version);
        }
        UUID ownerId = readUuid(tag, TAG_OWNER_UUID);
        if (ownerId == null) {
            return null;
        }
        ArrayList<CharacterPlayerStateRecord> records =
                new ArrayList<CharacterPlayerStateRecord>();
        HashSet<UUID> seen = new HashSet<UUID>();
        if (tag.hasKey(TAG_RECORDS, Constants.NBT.TAG_LIST)) {
            NBTTagList recordList = tag.getTagList(
                    TAG_RECORDS, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < recordList.tagCount(); index++) {
                CharacterPlayerStateRecord record = readRecordCopy(
                        recordList.getCompoundTagAt(index));
                if (!seen.add(record.getCharacterId())) {
                    throw new IllegalArgumentException(
                            "duplicate character state record " + record.getCharacterId());
                }
                records.add(record);
            }
        } else if (tag.hasKey(TAG_RECORDS)) {
            throw new IllegalArgumentException("character state records have an invalid type");
        }
        int bootstrapVersion = Math.max(0,
                tag.getInteger(TAG_BOOTSTRAP_VERSION));
        if (bootstrapVersion
                > CharacterPlayerStateAccount.CURRENT_BOOTSTRAP_VERSION) {
            throw new UnsupportedVersionException(bootstrapVersion);
        }
        return new CharacterPlayerStateAccount(
                ownerId,
                bootstrapVersion,
                Math.max(0L, tag.getLong(TAG_BOOTSTRAPPED_AT)),
                records);
    }

    /** Encodes a detached deep copy for journals and ownership transfers. */
    public static NBTTagCompound writeRecordCopy(
            CharacterPlayerStateRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterPlayerStateRecord.CURRENT_DATA_VERSION);
        writeUuid(tag, TAG_CHARACTER_UUID, record.getCharacterId());
        tag.setTag(TAG_CURRENT, writeSnapshot(record.getCurrent()));
        if (record.getPrevious() != null) {
            tag.setTag(TAG_PREVIOUS, writeSnapshot(record.getPrevious()));
        }
        return tag;
    }

    /** Decodes and validates a detached record from a journal or state vault. */
    public static CharacterPlayerStateRecord readRecordCopy(NBTTagCompound tag) {
        if (tag == null) {
            throw new IllegalArgumentException("record data must not be null");
        }
        int version = tag.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? tag.getInteger(TAG_DATA_VERSION) : 0;
        if (version < 0 || version > CharacterPlayerStateRecord.CURRENT_DATA_VERSION) {
            throw new UnsupportedVersionException(version);
        }
        UUID characterId = readUuid(tag, TAG_CHARACTER_UUID);
        if (characterId == null
                || !tag.hasKey(TAG_CURRENT, Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("character state record is incomplete");
        }
        CharacterPlayerStateSnapshot current = readSnapshot(
                tag.getCompoundTag(TAG_CURRENT));
        CharacterPlayerStateSnapshot previous = null;
        if (tag.hasKey(TAG_PREVIOUS, Constants.NBT.TAG_COMPOUND)) {
            previous = readSnapshot(tag.getCompoundTag(TAG_PREVIOUS));
        }
        if (!characterId.equals(current.getCharacterId())
                || previous != null && !characterId.equals(previous.getCharacterId())) {
            throw new IllegalArgumentException("snapshot character identifier mismatch");
        }
        return new CharacterPlayerStateRecord(characterId, current, previous);
    }

    private static NBTTagCompound writeSnapshot(CharacterPlayerStateSnapshot snapshot) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, snapshot.getDataVersion());
        writeUuid(tag, TAG_CHARACTER_UUID, snapshot.getCharacterId());
        tag.setLong(TAG_GENERATION, snapshot.getGeneration());
        tag.setLong(TAG_CAPTURED_AT, snapshot.getCapturedAt());
        NBTTagCompound components = new NBTTagCompound();
        for (Map.Entry<String, NBTTagCompound> entry
                : snapshot.getComponentsView().entrySet()) {
            components.setTag(entry.getKey(), entry.getValue().copy());
        }
        tag.setTag(TAG_COMPONENTS, components);
        return tag;
    }

    private static CharacterPlayerStateSnapshot readSnapshot(NBTTagCompound tag) {
        int version = tag.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? tag.getInteger(TAG_DATA_VERSION) : 0;
        if (version < 0 || version > CharacterPlayerStateSnapshot.CURRENT_DATA_VERSION) {
            throw new UnsupportedVersionException(version);
        }
        UUID characterId = readUuid(tag, TAG_CHARACTER_UUID);
        long generation = tag.getLong(TAG_GENERATION);
        if (characterId == null || generation <= 0L
                || !tag.hasKey(TAG_COMPONENTS, Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("character snapshot is incomplete");
        }
        NBTTagCompound componentTag = tag.getCompoundTag(TAG_COMPONENTS);
        LinkedHashMap<String, NBTTagCompound> components =
                new LinkedHashMap<String, NBTTagCompound>();
        Set<?> keys = componentTag.func_150296_c();
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String) keyObject;
            if (!componentTag.hasKey(key, Constants.NBT.TAG_COMPOUND)) {
                throw new IllegalArgumentException(
                        "component " + key + " has an invalid NBT type");
            }
            components.put(key, (NBTTagCompound)
                    componentTag.getCompoundTag(key).copy());
        }
        return new CharacterPlayerStateSnapshot(
                characterId,
                generation,
                Math.max(0L, tag.getLong(TAG_CAPTURED_AT)),
                version,
                components);
    }

    private void quarantine(NBTTagCompound original, String reason, UUID blockedOwner) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Reason", reason == null ? "unknown" : reason);
        if (blockedOwner != null) {
            writeUuid(entry, "BlockedOwnerUUID", blockedOwner);
            this.blockedOwners.add(blockedOwner);
        }
        entry.setTag("OriginalData", original == null
                ? new NBTTagCompound() : original.copy());
        this.quarantinedEntries.add(entry);
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Character player state is read-only because it uses unsupported version "
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
            // State quarantine must remain available to standalone repair
            // and validation tooling before the Forge logger is initialized.
        }
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        if (tag == null || uuid == null) {
            return;
        }
        tag.setLong(key + "Most", uuid.getMostSignificantBits());
        tag.setLong(key + "Least", uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        if (tag == null || key == null) {
            return null;
        }
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
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String) keyObject;
            NBTBase value = source.getTag(key);
            if (value != null) {
                destination.setTag(key, value.copy());
            }
        }
    }

    private static final class UnsupportedVersionException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final int version;

        private UnsupportedVersionException(int version) {
            this.version = version;
        }
    }
}
