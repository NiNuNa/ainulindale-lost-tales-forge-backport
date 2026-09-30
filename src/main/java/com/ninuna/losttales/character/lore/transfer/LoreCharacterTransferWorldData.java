package com.ninuna.losttales.character.lore.transfer;

import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.storage.CharacterNbtCodec;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesIdentifiers;
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
import java.util.UUID;

/** Fail-closed state vault and in-flight transfer journal for lore characters. */
public final class LoreCharacterTransferWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_lore_character_transfers";
    public static final int CURRENT_DATA_VERSION = 1;
    public static final int MAX_ENTRIES = 4096;

    private final Map<String, LoreCharacterVaultEntry> vault =
            new LinkedHashMap<String, LoreCharacterVaultEntry>();
    private final Map<String, LoreCharacterTransferRecord> transactions =
            new LinkedHashMap<String, LoreCharacterTransferRecord>();
    private boolean readOnly;
    private String readOnlyReason = "";
    private NBTTagCompound preservedData;

    public LoreCharacterTransferWorldData(String name) { super(name); }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        reset();
        if (compound == null || compound.func_150296_c().isEmpty()) {
            markDirty();
            return;
        }
        if (!compound.hasKey("DataVersion", Constants.NBT.TAG_INT)) {
            fail(compound, "missing_data_version");
            return;
        }
        int version = compound.getInteger("DataVersion");
        if (version != CURRENT_DATA_VERSION) {
            fail(compound, "unsupported_data_version");
            return;
        }
        if (!compound.hasKey("Vault", Constants.NBT.TAG_LIST)
                || !compound.hasKey("Transactions", Constants.NBT.TAG_LIST)) {
            fail(compound, "missing_lists");
            return;
        }
        NBTTagList vaultList = compound.getTagList(
                "Vault", Constants.NBT.TAG_COMPOUND);
        NBTTagList transactionList = compound.getTagList(
                "Transactions", Constants.NBT.TAG_COMPOUND);
        if (vaultList.tagCount() > MAX_ENTRIES
                || transactionList.tagCount() > MAX_ENTRIES) {
            fail(compound, "entry_limit_exceeded");
            return;
        }
        try {
            for (int index = 0; index < vaultList.tagCount(); index++) {
                LoreCharacterVaultEntry entry = readVault(
                        vaultList.getCompoundTagAt(index));
                if (this.vault.put(entry.getLoreCharacterId(), entry) != null) {
                    throw new IllegalArgumentException("duplicate vault identity");
                }
            }
            for (int index = 0; index < transactionList.tagCount(); index++) {
                LoreCharacterTransferRecord transaction = readTransaction(
                        transactionList.getCompoundTagAt(index));
                if (this.transactions.put(
                        transaction.getLoreCharacterId(), transaction) != null) {
                    throw new IllegalArgumentException(
                            "duplicate transfer identity");
                }
                LoreCharacterVaultEntry entry = this.vault.get(
                        transaction.getLoreCharacterId());
                if (entry == null || !entry.getCharacterId().equals(
                        transaction.getCharacterId())) {
                    throw new IllegalArgumentException(
                            "transfer has no matching vault entry");
                }
            }
        } catch (RuntimeException exception) {
            fail(compound, "malformed_entry");
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnly && this.preservedData != null) {
            NbtTags.copyContents(this.preservedData, compound);
            return;
        }
        compound.setInteger("DataVersion", CURRENT_DATA_VERSION);
        NBTTagList vaultList = new NBTTagList();
        for (LoreCharacterVaultEntry entry : sortedVault()) {
            vaultList.appendTag(writeVault(entry));
        }
        compound.setTag("Vault", vaultList);
        NBTTagList transactionList = new NBTTagList();
        for (LoreCharacterTransferRecord transaction : sortedTransactions()) {
            transactionList.appendTag(writeTransaction(transaction));
        }
        compound.setTag("Transactions", transactionList);
    }

    public synchronized boolean isReadOnly() { return this.readOnly; }
    public synchronized String getReadOnlyReason() { return this.readOnlyReason; }
    public synchronized LoreCharacterVaultEntry getVaultEntry(String loreId) {
        return this.vault.get(LostTalesIdentifiers.normalize(loreId));
    }
    public synchronized int getVaultEntryCount() { return this.vault.size(); }
    public synchronized LoreCharacterTransferRecord getTransaction(String loreId) {
        return this.transactions.get(LostTalesIdentifiers.normalize(loreId));
    }
    public synchronized Collection<LoreCharacterTransferRecord> getTransactions() {
        return Collections.unmodifiableList(
                new ArrayList<LoreCharacterTransferRecord>(
                        this.transactions.values()));
    }

    public synchronized void saveVault(LoreCharacterVaultEntry entry) {
        ensureWritable();
        if (entry == null) throw new IllegalArgumentException("entry is required");
        LoreCharacterVaultEntry existing = this.vault.get(
                entry.getLoreCharacterId());
        if (existing != null && !existing.getCharacterId().equals(
                entry.getCharacterId())) {
            throw new IllegalStateException("Lore-character UUID changed");
        }
        if (existing == null && this.vault.size() >= MAX_ENTRIES) {
            throw new IllegalStateException("Lore-character vault is full");
        }
        this.vault.put(entry.getLoreCharacterId(), entry);
        markDirty();
    }

    public synchronized void begin(LoreCharacterTransferRecord transaction) {
        ensureWritable();
        if (transaction == null || transaction.getStep() != 0
                || this.transactions.containsKey(
                transaction.getLoreCharacterId())) {
            throw new IllegalStateException(
                    "A transfer is already active or is not prepared");
        }
        LoreCharacterVaultEntry entry = this.vault.get(
                transaction.getLoreCharacterId());
        if (entry == null || !entry.getCharacterId().equals(
                transaction.getCharacterId())) {
            throw new IllegalStateException("Transfer has no matching vault copy");
        }
        this.transactions.put(transaction.getLoreCharacterId(), transaction);
        markDirty();
    }

    public synchronized LoreCharacterTransferRecord advance(
            String loreId, UUID transactionId, int expectedStep) {
        ensureWritable();
        LoreCharacterTransferRecord current = getTransaction(loreId);
        if (current == null || !current.getTransactionId().equals(transactionId)
                || current.getStep() != expectedStep) {
            throw new IllegalStateException("Transfer revision is stale");
        }
        LoreCharacterTransferRecord advanced = current.advance(
                expectedStep + 1);
        this.transactions.put(advanced.getLoreCharacterId(), advanced);
        markDirty();
        return advanced;
    }

    public synchronized void complete(String loreId, UUID transactionId) {
        ensureWritable();
        LoreCharacterTransferRecord current = getTransaction(loreId);
        if (current == null || !current.getTransactionId().equals(transactionId)
                || current.getStep() != 3) {
            throw new IllegalStateException("Transfer is not ready to commit");
        }
        this.transactions.remove(current.getLoreCharacterId());
        markDirty();
    }

    private List<LoreCharacterVaultEntry> sortedVault() {
        List<LoreCharacterVaultEntry> result =
                new ArrayList<LoreCharacterVaultEntry>(this.vault.values());
        Collections.sort(result, new Comparator<LoreCharacterVaultEntry>() {
            @Override public int compare(LoreCharacterVaultEntry a,
                                         LoreCharacterVaultEntry b) {
                return a.getLoreCharacterId().compareTo(b.getLoreCharacterId());
            }
        });
        return result;
    }

    private List<LoreCharacterTransferRecord> sortedTransactions() {
        List<LoreCharacterTransferRecord> result =
                new ArrayList<LoreCharacterTransferRecord>(
                        this.transactions.values());
        Collections.sort(result,
                new Comparator<LoreCharacterTransferRecord>() {
                    @Override public int compare(
                            LoreCharacterTransferRecord a,
                            LoreCharacterTransferRecord b) {
                        return a.getLoreCharacterId().compareTo(
                                b.getLoreCharacterId());
                    }
                });
        return result;
    }

    private static NBTTagCompound writeVault(LoreCharacterVaultEntry entry) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("LoreCharacterId", entry.getLoreCharacterId());
        NbtTags.writeUuid(tag, "CapturedOwnerUUID", entry.getCapturedOwnerId());
        tag.setTag("Character", CharacterNbtCodec.writeCharacterRecord(
                entry.getCharacterCopy()));
        tag.setTag("PlayerState", CharacterPlayerStateWorldData.writeRecordCopy(
                entry.getPlayerStateCopy()));
        tag.setLong("UpdatedAt", entry.getUpdatedAt());
        return tag;
    }

    private static LoreCharacterVaultEntry readVault(NBTTagCompound tag) {
        UUID ownerId = NbtTags.readUuid(tag, "CapturedOwnerUUID");
        if (ownerId == null
                || !tag.hasKey("LoreCharacterId", Constants.NBT.TAG_STRING)
                || !tag.hasKey("Character", Constants.NBT.TAG_COMPOUND)
                || !tag.hasKey("PlayerState", Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("vault entry is incomplete");
        }
        RoleplayCharacter character = CharacterNbtCodec.readCharacterRecord(
                tag.getCompoundTag("Character"), ownerId);
        CharacterPlayerStateRecord state =
                CharacterPlayerStateWorldData.readRecordCopy(
                        tag.getCompoundTag("PlayerState"));
        return new LoreCharacterVaultEntry(
                tag.getString("LoreCharacterId"), ownerId, character, state,
                tag.getLong("UpdatedAt"));
    }

    private static NBTTagCompound writeTransaction(
            LoreCharacterTransferRecord transaction) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger("DataVersion",
                LoreCharacterTransferRecord.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, "TransactionUUID", transaction.getTransactionId());
        tag.setString("Type", transaction.getType().name());
        tag.setString("LoreCharacterId", transaction.getLoreCharacterId());
        NbtTags.writeUuid(tag, "CharacterUUID", transaction.getCharacterId());
        NbtTags.writeUuid(tag, "SourceOwnerUUID", transaction.getSourceOwnerId());
        NbtTags.writeUuid(tag, "TargetOwnerUUID", transaction.getTargetOwnerId());
        tag.setInteger("TargetSlot", transaction.getTargetSlot());
        tag.setLong("ExpectedOwnershipRevision",
                transaction.getExpectedOwnershipRevision());
        tag.setInteger("Step", transaction.getStep());
        tag.setLong("CreatedAt", transaction.getCreatedAt());
        return tag;
    }

    private static LoreCharacterTransferRecord readTransaction(
            NBTTagCompound tag) {
        if (tag.getInteger("DataVersion")
                != LoreCharacterTransferRecord.CURRENT_DATA_VERSION) {
            throw new IllegalArgumentException("unsupported transaction version");
        }
        return new LoreCharacterTransferRecord(
                NbtTags.readUuid(tag, "TransactionUUID"),
                LoreCharacterTransferRecord.Type.valueOf(tag.getString("Type")),
                tag.getString("LoreCharacterId"),
                NbtTags.readUuid(tag, "CharacterUUID"),
                NbtTags.readUuid(tag, "SourceOwnerUUID"),
                NbtTags.readUuid(tag, "TargetOwnerUUID"),
                tag.getInteger("TargetSlot"),
                tag.getLong("ExpectedOwnershipRevision"),
                tag.getInteger("Step"),
                tag.getLong("CreatedAt"));
    }

    private void reset() {
        this.vault.clear(); this.transactions.clear(); this.readOnly = false;
        this.readOnlyReason = "";
        this.preservedData = null;
    }

    private void fail(NBTTagCompound source, String reason) {
        this.vault.clear(); this.transactions.clear(); this.readOnly = true;
        this.readOnlyReason = reason;
        this.preservedData = source == null ? new NBTTagCompound()
                : (NBTTagCompound)source.copy();
    }

    private void ensureWritable() {
        if (this.readOnly) {
            throw new IllegalStateException(
                    "Lore-character transfer storage is read-only: "
                            + this.readOnlyReason);
        }
    }
}
