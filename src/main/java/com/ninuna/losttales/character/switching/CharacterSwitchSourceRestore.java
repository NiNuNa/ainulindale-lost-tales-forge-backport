package com.ninuna.losttales.character.switching;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * An operator's discard of a switch journal (CHAR-37). The character the
 * switch was leaving comes back exactly as the generation the journal names
 * for it, the ghost too, and what the player holds then is set aside in
 * the quarantine of their player-state store, never dropped. Until the
 * restore is made the journal waits as
 * {@link CharacterSwitchTransactionStatus#RESTORE_SOURCE} and switching
 * stays off: it is made at once for a player online and alive, else at
 * their next join. What the stores decide without a live player is here.
 */
public final class CharacterSwitchSourceRestore {

    /** Why the restore cannot be made yet; it is tried again at each join. */
    public enum Refusal {
        /** The character the switch left is no longer in the roster. */
        SOURCE_GONE("source_gone"),
        /** The generation the journal names is no longer in its record, or cannot be read. */
        STATE_GONE("state_gone"),
        /** The roster store or the player-state store cannot be written. */
        STORE_UNAVAILABLE("store_unavailable"),
        /** The player-state store's quarantine has no room; the store is read-only. */
        QUARANTINE_FULL("quarantine_full"),
        /** What the player holds could not be captured whole. */
        STATE_UNREADABLE("state_unreadable");

        private final String id;

        Refusal(String id) {
            this.id = id;
        }

        public String getId() {
            return this.id;
        }
    }

    private static final String TAG_TRANSACTION = "TransactionUUID";
    private static final String TAG_SOURCE = "SourceCharacterUUID";
    private static final String TAG_TARGET = "TargetCharacterUUID";
    private static final String TAG_PLAYED = "PlayedCharacterUUID";
    private static final String TAG_SOURCE_STATE_GENERATION = "SourceStateGeneration";
    private static final String TAG_SET_ASIDE_AT = "SetAsideAt";
    private static final String TAG_COMPONENTS = "Components";

    private CharacterSwitchSourceRestore() {}

    /**
     * Why none of the account's characters may be deleted or released now,
     * or {@link CharacterErrorId#NONE}: a held journal names the character
     * it puts back, which has to stay in the roster with its saved life
     * until the journal is settled. A switch store that cannot be read
     * cannot say, and refuses too.
     */
    public static CharacterErrorId characterRemovalRefusal(World world, UUID ownerId) {
        CharacterSwitchWorldData data;
        try {
            data = CharacterSwitchStorage.get(world);
        } catch (RuntimeException exception) {
            LostTalesLog.warning("Could not read the character switch store for "
                    + "owner %s: %s", ownerId, exception.toString());
            return CharacterErrorId.SWITCH_STORAGE_READ_ONLY;
        }
        return characterRemovalRefusal(data.isReadOnlyForNewerVersion(),
                data.isOwnerBlocked(ownerId), data.getAccount(ownerId));
    }

    /** {@link #characterRemovalRefusal(World, UUID)} from what the switch store says. */
    static CharacterErrorId characterRemovalRefusal(boolean storeReadOnly,
                                                    boolean ownerBlocked,
                                                    CharacterSwitchAccountState account) {
        if (storeReadOnly) {
            return CharacterErrorId.SWITCH_STORAGE_READ_ONLY;
        }
        CharacterSwitchTransaction transaction =
                account == null ? null : account.getTransaction();
        if (ownerBlocked || transaction != null && transaction.isHeld()) {
            return CharacterErrorId.SWITCH_JOURNAL_HELD;
        }
        return CharacterErrorId.NONE;
    }

    /** Marks the account's journal for its restore; the account stays frozen until it is made. */
    static void markForRestore(CharacterSwitchAccountState account, long timestamp) {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction == null) {
            throw new IllegalStateException("The account holds no switch journal");
        }
        transaction.markRestoreSource(timestamp);
        account.setFrozen(true);
    }

    /**
     * Why the stores refuse the restore, or null when they allow it: both
     * stores writable, the source still in the roster (the ghost always
     * is), the generation the journal names still in the source's record,
     * and room in the quarantine.
     */
    static Refusal refusal(CharacterSwitchTransaction transaction,
                           CharacterRoster roster,
                           CharacterPlayerStateAccount states,
                           boolean storesWritable,
                           boolean quarantineHasRoom) {
        if (!storesWritable) {
            return Refusal.STORE_UNAVAILABLE;
        }
        UUID sourceId = transaction.getSourceCharacterId();
        if (roster == null || sourceId != null && roster.getCharacter(sourceId) == null) {
            return Refusal.SOURCE_GONE;
        }
        UUID gameplayId = sourceId == null ? roster.getOwnerId() : sourceId;
        CharacterPlayerStateRecord record = states == null
                ? null : states.getRecord(gameplayId);
        if (record == null
                || record.find(transaction.getSourceStateGeneration()) == null) {
            return Refusal.STATE_GONE;
        }
        if (!quarantineHasRoom) {
            return Refusal.QUARANTINE_FULL;
        }
        return null;
    }

    /**
     * What is set aside: the parts of the player a switch moves, as they
     * were, with the journal they were set aside for and whom the roster
     * named as played. A side or a played identity left out is the ghost.
     */
    static NBTTagCompound setAside(CharacterSwitchTransaction transaction,
                                   UUID playedCharacterId,
                                   long timestamp,
                                   NBTTagCompound components) {
        NBTTagCompound kept = new NBTTagCompound();
        NbtTags.writeUuid(kept, TAG_TRANSACTION, transaction.getTransactionId());
        NbtTags.writeUuid(kept, TAG_SOURCE, transaction.getSourceCharacterId());
        NbtTags.writeUuid(kept, TAG_TARGET, transaction.getTargetCharacterId());
        NbtTags.writeUuid(kept, TAG_PLAYED, playedCharacterId);
        kept.setLong(TAG_SOURCE_STATE_GENERATION, transaction.getSourceStateGeneration());
        kept.setLong(TAG_SET_ASIDE_AT, Math.max(0L, timestamp));
        kept.setTag(TAG_COMPONENTS, components == null
                ? new NBTTagCompound() : components.copy());
        return kept;
    }

    /**
     * Puts the roster back on the source, the ghost being none. Answers
     * whether it moved; only then does its revision step on.
     */
    static boolean playSource(CharacterRoster roster,
                              CharacterSwitchTransaction transaction) {
        UUID sourceId = transaction.getSourceCharacterId();
        UUID played = roster.getActiveCharacterId();
        if (sourceId == null ? played == null : sourceId.equals(played)) {
            return false;
        }
        roster.setActiveCharacterId(sourceId);
        roster.incrementRevision();
        return true;
    }

    /**
     * Once the source is back and the player's own files hold it: the
     * cooldown as it was before the switch, which did not happen, the
     * journal gone and the account thawed.
     */
    static void settle(CharacterSwitchAccountState account) {
        CharacterSwitchTransaction transaction = account.getTransaction();
        account.restorePreviousCooldown(transaction);
        account.setTransaction(null);
        account.setFrozen(false);
    }

    /** A restore that failed once the player was touched: held for an operator again. */
    static void holdAgain(CharacterSwitchAccountState account, long timestamp) {
        CharacterSwitchTransaction transaction = account.getTransaction();
        if (transaction != null) {
            transaction.markRecoveryRequired(timestamp);
        }
        account.setFrozen(true);
    }
}
