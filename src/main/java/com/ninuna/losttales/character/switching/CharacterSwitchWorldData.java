package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Versioned persistent account manifests and switch transaction journals. */
public final class CharacterSwitchWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_character_switches";
    public static final int CURRENT_DATA_VERSION = 3;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ACCOUNTS = "Accounts";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_COOLDOWN_STAGE = "CooldownStage";
    private static final String TAG_NEXT_ALLOWED_AT = "NextAllowedAt";
    private static final String TAG_LAST_SWITCH_AT = "LastSuccessfulSwitchAt";
    private static final String TAG_DECAY_ANCHOR_AT = "DecayAnchorAt";
    private static final String TAG_LAST_OBSERVED_CLOCK = "LastObservedWallClock";
    private static final String TAG_FROZEN = "Frozen";
    private static final String TAG_DEATH_PENDING = "DeathPending";
    private static final String TAG_DEATH_PENDING_AT = "DeathPendingAt";
    private static final String TAG_TRANSACTION = "Transaction";

    private static final String TAG_TRANSACTION_UUID = "TransactionUUID";
    private static final String TAG_SOURCE_CHARACTER_UUID = "SourceCharacterUUID";
    private static final String TAG_TARGET_CHARACTER_UUID = "TargetCharacterUUID";
    private static final String TAG_SOURCE_REVISION = "SourceRosterRevision";
    private static final String TAG_TARGET_REVISION = "TargetRosterRevision";
    private static final String TAG_PREPARED_AT = "PreparedAt";
    private static final String TAG_COMPLETED_AT = "CompletedAt";
    private static final String TAG_REQUEST_EPOCH = "RequestEpoch";
    private static final String TAG_REQUEST_ID = "RequestId";
    private static final String TAG_STATUS = "Status";
    private static final String TAG_PREVIOUS = "PreviousCooldown";
    private static final String TAG_COMMITTED = "CommittedCooldown";
    private static final String TAG_SOURCE_STATE_GENERATION = "SourceStateGeneration";
    private static final String TAG_TARGET_STATE_GENERATION = "TargetStateGeneration";

    private final Map<UUID, CharacterSwitchAccountState> accounts =
            new LinkedHashMap<UUID, CharacterSwitchAccountState>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    private final Set<UUID> blockedOwners = new HashSet<UUID>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public CharacterSwitchWorldData(String name) {
        super(name);
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
        // Only this build's version is read; any other is kept as it is
        // and the store goes read-only.
        if (version != CURRENT_DATA_VERSION) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = version;
            this.preservedNewerData = (NBTTagCompound) compound.copy();
            return;
        }

        boolean repaired = false;
        if (compound.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_LIST)) {
            NBTTagList quarantine = compound.getTagList(
                    TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < quarantine.tagCount(); index++) {
                NBTTagCompound entry = (NBTTagCompound)
                        quarantine.getCompoundTagAt(index).copy();
                this.quarantinedEntries.add(entry);
                UUID blockedOwner = NbtTags.readUuid(entry, "BlockedOwnerUUID");
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
            try {
                CharacterSwitchAccountState state = readAccount(raw);
                if (state == null) {
                    quarantine(raw, "invalid_account", NbtTags.readUuid(raw, TAG_OWNER_UUID));
                    repaired = true;
                    continue;
                }
                if (this.accounts.containsKey(state.getOwnerId())) {
                    CharacterSwitchAccountState existing =
                            this.accounts.remove(state.getOwnerId());
                    quarantine(writeAccount(existing),
                            "duplicate_account_existing", state.getOwnerId());
                    quarantine(raw, "duplicate_account", state.getOwnerId());
                    repaired = true;
                    continue;
                }
                if (this.blockedOwners.contains(state.getOwnerId())) {
                    quarantine(raw, "owner_already_blocked", state.getOwnerId());
                    repaired = true;
                    continue;
                }
                this.accounts.put(state.getOwnerId(), state);
            } catch (UnsupportedVersionException exception) {
                this.readOnlyForNewerVersion = true;
                this.unsupportedDataVersion = exception.version;
                this.preservedNewerData = (NBTTagCompound) compound.copy();
                this.accounts.clear();
                this.quarantinedEntries.clear();
                this.blockedOwners.clear();
                return;
            } catch (RuntimeException exception) {
                UUID blockedOwner = NbtTags.readUuid(raw, TAG_OWNER_UUID);
                if (blockedOwner != null) {
                    CharacterSwitchAccountState existing =
                            this.accounts.remove(blockedOwner);
                    if (existing != null) {
                        quarantine(writeAccount(existing),
                                "malformed_account_existing", blockedOwner);
                    }
                }
                quarantine(raw, "malformed_account", blockedOwner);
                repaired = true;
                LostTalesLog.warning("Quarantined malformed character switch account at index %d: %s",
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

        ArrayList<CharacterSwitchAccountState> sorted =
                new ArrayList<CharacterSwitchAccountState>(this.accounts.values());
        Collections.sort(sorted, new Comparator<CharacterSwitchAccountState>() {
            @Override
            public int compare(CharacterSwitchAccountState left,
                               CharacterSwitchAccountState right) {
                return left.getOwnerId().toString().compareTo(right.getOwnerId().toString());
            }
        });
        NBTTagList accountList = new NBTTagList();
        for (CharacterSwitchAccountState state : sorted) {
            accountList.appendTag(writeAccount(state));
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

    public CharacterSwitchAccountState getAccount(UUID ownerId) {
        return ownerId == null ? null : this.accounts.get(ownerId);
    }

    public CharacterSwitchAccountState getOrCreateAccount(UUID ownerId) {
        ensureWritable();
        if (ownerId == null) {
            throw new IllegalArgumentException("ownerId must not be null");
        }
        if (this.blockedOwners.contains(ownerId)) {
            throw new IllegalStateException(
                    "Character switch state for owner " + ownerId + " is quarantined");
        }
        CharacterSwitchAccountState state = this.accounts.get(ownerId);
        if (state == null) {
            state = new CharacterSwitchAccountState(ownerId);
            this.accounts.put(ownerId, state);
            markDirty();
        }
        return state;
    }

    public void saveAccount(CharacterSwitchAccountState state) {
        ensureWritable();
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (this.blockedOwners.contains(state.getOwnerId())) {
            throw new IllegalStateException(
                    "Character switch state for owner " + state.getOwnerId()
                            + " is quarantined");
        }
        this.accounts.put(state.getOwnerId(), state);
        markDirty();
    }

    public int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    public boolean isOwnerBlocked(UUID ownerId) {
        return ownerId != null && this.blockedOwners.contains(ownerId);
    }

    private static NBTTagCompound writeAccount(CharacterSwitchAccountState state) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterSwitchAccountState.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_OWNER_UUID, state.getOwnerId());
        tag.setInteger(TAG_COOLDOWN_STAGE, state.getCooldownStage());
        tag.setLong(TAG_NEXT_ALLOWED_AT, state.getNextAllowedAt());
        tag.setLong(TAG_LAST_SWITCH_AT, state.getLastSuccessfulSwitchAt());
        tag.setLong(TAG_DECAY_ANCHOR_AT, state.getDecayAnchorAt());
        tag.setLong(TAG_LAST_OBSERVED_CLOCK, state.getLastObservedWallClock());
        tag.setBoolean(TAG_FROZEN, state.isFrozen());
        tag.setBoolean(TAG_DEATH_PENDING, state.isDeathPending());
        tag.setLong(TAG_DEATH_PENDING_AT, state.getDeathPendingAt());
        if (state.getTransaction() != null) {
            tag.setTag(TAG_TRANSACTION, writeTransaction(state.getTransaction()));
        }
        return tag;
    }

    private static CharacterSwitchAccountState readAccount(NBTTagCompound tag) {
        int version = tag.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? tag.getInteger(TAG_DATA_VERSION) : 0;
        if (version != CharacterSwitchAccountState.CURRENT_DATA_VERSION) {
            throw new UnsupportedVersionException(version);
        }
        UUID ownerId = NbtTags.readUuid(tag, TAG_OWNER_UUID);
        if (ownerId == null) {
            return null;
        }
        CharacterSwitchTransaction transaction = null;
        if (tag.hasKey(TAG_TRANSACTION, Constants.NBT.TAG_COMPOUND)) {
            transaction = readTransaction(tag.getCompoundTag(TAG_TRANSACTION));
        }
        return new CharacterSwitchAccountState(
                ownerId,
                Math.max(0, tag.getInteger(TAG_COOLDOWN_STAGE)),
                Math.max(0L, tag.getLong(TAG_NEXT_ALLOWED_AT)),
                Math.max(0L, tag.getLong(TAG_LAST_SWITCH_AT)),
                Math.max(0L, tag.getLong(TAG_DECAY_ANCHOR_AT)),
                Math.max(0L, tag.getLong(TAG_LAST_OBSERVED_CLOCK)),
                tag.getBoolean(TAG_FROZEN),
                tag.getBoolean(TAG_DEATH_PENDING),
                Math.max(0L, tag.getLong(TAG_DEATH_PENDING_AT)),
                transaction);
    }

    private static NBTTagCompound writeTransaction(CharacterSwitchTransaction transaction) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterSwitchTransaction.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_TRANSACTION_UUID, transaction.getTransactionId());
        if (transaction.getSourceCharacterId() != null) {
            NbtTags.writeUuid(tag, TAG_SOURCE_CHARACTER_UUID, transaction.getSourceCharacterId());
        }
        if (transaction.getTargetCharacterId() != null) {
            NbtTags.writeUuid(tag, TAG_TARGET_CHARACTER_UUID, transaction.getTargetCharacterId());
        }
        tag.setLong(TAG_SOURCE_REVISION, transaction.getSourceRosterRevision());
        tag.setLong(TAG_TARGET_REVISION, transaction.getTargetRosterRevision());
        tag.setLong(TAG_PREPARED_AT, transaction.getPreparedAt());
        tag.setLong(TAG_COMPLETED_AT, transaction.getCompletedAt());
        tag.setLong(TAG_REQUEST_EPOCH, transaction.getRequestEpoch());
        tag.setInteger(TAG_REQUEST_ID, transaction.getRequestId());
        tag.setString(TAG_STATUS, transaction.getStatus().getId());
        tag.setLong(TAG_SOURCE_STATE_GENERATION,
                transaction.getSourceStateGeneration());
        tag.setLong(TAG_TARGET_STATE_GENERATION,
                transaction.getTargetStateGeneration());
        tag.setTag(TAG_PREVIOUS, writeCooldownValues(
                transaction.getPreviousCooldownStage(),
                transaction.getPreviousNextAllowedAt(),
                transaction.getPreviousLastSuccessfulSwitchAt(),
                transaction.getPreviousDecayAnchorAt(),
                transaction.getPreviousLastObservedWallClock()));
        tag.setTag(TAG_COMMITTED, writeCooldownValues(
                transaction.getCommittedCooldownStage(),
                transaction.getCommittedNextAllowedAt(),
                transaction.getCommittedLastSuccessfulSwitchAt(),
                transaction.getCommittedDecayAnchorAt(),
                transaction.getCommittedLastObservedWallClock()));
        return tag;
    }

    private static CharacterSwitchTransaction readTransaction(NBTTagCompound tag) {
        int version = tag.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? tag.getInteger(TAG_DATA_VERSION) : 0;
        if (version != CharacterSwitchTransaction.CURRENT_DATA_VERSION) {
            throw new UnsupportedVersionException(version);
        }
        UUID transactionId = NbtTags.readUuid(tag, TAG_TRANSACTION_UUID);
        // A side left out is the account.
        UUID sourceId = NbtTags.readUuid(tag, TAG_SOURCE_CHARACTER_UUID);
        UUID targetId = NbtTags.readUuid(tag, TAG_TARGET_CHARACTER_UUID);
        if (transactionId == null) {
            throw new IllegalArgumentException("transaction identifiers are missing");
        }
        NBTTagCompound previous = tag.hasKey(TAG_PREVIOUS, Constants.NBT.TAG_COMPOUND)
                ? tag.getCompoundTag(TAG_PREVIOUS) : new NBTTagCompound();
        NBTTagCompound committed = tag.hasKey(TAG_COMMITTED, Constants.NBT.TAG_COMPOUND)
                ? tag.getCompoundTag(TAG_COMMITTED) : new NBTTagCompound();
        return new CharacterSwitchTransaction(
                transactionId,
                sourceId,
                targetId,
                tag.getLong(TAG_SOURCE_REVISION),
                tag.getLong(TAG_TARGET_REVISION),
                tag.getLong(TAG_PREPARED_AT),
                tag.getLong(TAG_REQUEST_EPOCH),
                tag.getInteger(TAG_REQUEST_ID),
                previous.getInteger(TAG_COOLDOWN_STAGE),
                previous.getLong(TAG_NEXT_ALLOWED_AT),
                previous.getLong(TAG_LAST_SWITCH_AT),
                previous.getLong(TAG_DECAY_ANCHOR_AT),
                previous.getLong(TAG_LAST_OBSERVED_CLOCK),
                committed.getInteger(TAG_COOLDOWN_STAGE),
                committed.getLong(TAG_NEXT_ALLOWED_AT),
                committed.getLong(TAG_LAST_SWITCH_AT),
                committed.getLong(TAG_DECAY_ANCHOR_AT),
                committed.getLong(TAG_LAST_OBSERVED_CLOCK),
                tag.getLong(TAG_SOURCE_STATE_GENERATION),
                tag.getLong(TAG_TARGET_STATE_GENERATION),
                CharacterSwitchTransactionStatus.fromId(tag.getString(TAG_STATUS)),
                tag.getLong(TAG_COMPLETED_AT));
    }

    private static NBTTagCompound writeCooldownValues(int stage, long nextAllowedAt,
                                                        long lastSwitchAt, long decayAnchorAt,
                                                        long lastObservedClock) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_COOLDOWN_STAGE, stage);
        tag.setLong(TAG_NEXT_ALLOWED_AT, nextAllowedAt);
        tag.setLong(TAG_LAST_SWITCH_AT, lastSwitchAt);
        tag.setLong(TAG_DECAY_ANCHOR_AT, decayAnchorAt);
        tag.setLong(TAG_LAST_OBSERVED_CLOCK, lastObservedClock);
        return tag;
    }

    private void quarantine(NBTTagCompound original, String reason, UUID blockedOwner) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Reason", reason == null ? "unknown" : reason);
        if (blockedOwner != null) {
            NbtTags.writeUuid(entry, "BlockedOwnerUUID", blockedOwner);
            this.blockedOwners.add(blockedOwner);
        }
        entry.setTag("OriginalData", original == null
                ? new NBTTagCompound() : original.copy());
        this.quarantinedEntries.add(entry);
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Character switch data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }

    private static final class UnsupportedVersionException extends RuntimeException {
        private final int version;

        private UnsupportedVersionException(int version) {
            this.version = version;
        }
    }
}
