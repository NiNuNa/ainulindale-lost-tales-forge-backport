package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.server.CharacterOperationResult;
import com.ninuna.losttales.character.server.CharacterSyncManager;
import com.ninuna.losttales.character.state.CharacterPlayerStateAccount;
import com.ninuna.losttales.character.state.CharacterPlayerStateRecord;
import com.ninuna.losttales.character.state.CharacterPlayerStateService;
import com.ninuna.losttales.character.state.CharacterPlayerStateSnapshot;
import com.ninuna.losttales.character.state.CharacterPlayerStateStorage;
import com.ninuna.losttales.character.state.CharacterPlayerStateWorldData;
import com.ninuna.losttales.character.state.CharacterStateValidationException;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.sync.CharacterRosterSnapshot;
import com.ninuna.losttales.character.sync.DeletedCharacterSummary;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.server.PartyOperationResult;
import com.ninuna.losttales.party.server.PartyService;
import com.ninuna.losttales.util.LostTalesMath;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative deletion, restoration, purge, and inactive-generation
 * rollback coordinator.
 *
 * <p>A deleted character is kept for the server's retention
 * ({@code characterDeletionRetentionDays}). Within it the owner restores it
 * from the foot of the roster; after it the server purges it on its own,
 * at server start and at each login of the owner, a bounded number at a
 * time. Administrators restore and purge by command as well.</p>
 */
public final class CharacterDeletionService {

    private static final CharacterDeletionService INSTANCE =
            new CharacterDeletionService();
    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private CharacterDeletionService() {}

    public static CharacterDeletionService getInstance() {
        return INSTANCE;
    }

    public synchronized CharacterOperationResult delete(
            EntityPlayerMP player,
            CharacterWorldData characterData,
            CharacterRoster roster,
            RoleplayCharacter character) {
        if (player == null || characterData == null || roster == null
                || character == null
                || !player.getUniqueID().equals(character.getOwnerId())) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR, roster);
        }

        CharacterDeletionWorldData deletionData;
        CharacterPlayerStateWorldData playerStateData;
        try {
            deletionData = CharacterDeletionStorage.get(player.worldObj);
            playerStateData = CharacterPlayerStateStorage.get(
                    player.worldObj, player.getUniqueID());
        } catch (RuntimeException exception) {
            logFailure("delete_open_stores", player.getUniqueID(),
                    character.getCharacterId(), exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR, roster);
        }
        if (deletionData.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_RECOVERY_STORAGE_READ_ONLY, roster);
        }
        if (playerStateData.isReadOnlyForNewerVersion()
                || playerStateData.isOwnerBlocked(player.getUniqueID())) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_PLAYER_STATE_STORAGE_READ_ONLY, roster);
        }

        CharacterDeletionTombstone existing = deletionData.getTombstone(
                character.getCharacterId());
        if (existing != null && existing.isCommitted()) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_RECOVERY_REQUIRED, roster);
        }
        if (existing == null
                && deletionData.getTombstones(player.getUniqueID()).size()
                >= CharacterDeletionWorldData.MAX_TOMBSTONES_PER_OWNER) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_RECOVERY_LIMIT, roster);
        }

        CharacterPlayerStateSnapshot current;
        try {
            CharacterPlayerStateAccount account =
                    CharacterPlayerStateService.getInstance().ensureBootstrapped(
                            player, roster, playerStateData);
            current = CharacterPlayerStateService.getInstance().getCurrent(
                    account, character.getCharacterId());
            // The state generation must be durable before the deletion journal
            // is allowed to reference it.
            CharacterPlayerStateStorage.flush(player.worldObj);
        } catch (CharacterStateValidationException exception) {
            logFailure("delete_validate_state", player.getUniqueID(),
                    character.getCharacterId(), exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_PLAYER_STATE_INVALID, roster);
        } catch (RuntimeException exception) {
            logFailure("delete_save_state", player.getUniqueID(),
                    character.getCharacterId(), exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_PLAYER_STATE_STORAGE_READ_ONLY, roster);
        }

        CharacterDeletionTombstone tombstone =
                CharacterDeletionTombstone.prepared(
                        character,
                        current.getGeneration(),
                        Math.max(1L, System.currentTimeMillis()));
        try {
            deletionData.savePrepared(tombstone);
            // Persist the recovery record before touching party membership or
            // the roster. A crash after this point always leaves a restorable
            // character identity and an exact state-generation reference.
            CharacterDeletionStorage.flush(player.worldObj);
        } catch (RuntimeException exception) {
            logFailure("delete_prepare", player.getUniqueID(),
                    character.getCharacterId(), exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_RECOVERY_STORAGE_READ_ONLY, roster);
        }

        PartyOperationResult partyCleanup = PartyService.getInstance()
                .removeCharacterForDeletion(player.worldObj, character);
        if (!partyCleanup.isSuccessful()) {
            return CharacterOperationResult.failure(
                    mapPartyCleanupError(partyCleanup.getErrorId()), roster);
        }

        RoleplayCharacter removed = roster.removeCharacter(
                character.getCharacterId());
        if (removed == null) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.CHARACTER_NOT_FOUND, roster);
        }
        roster.incrementRevision();
        characterData.saveRoster(roster);

        long deletedAt = Math.max(
                tombstone.getPreparedAt(), System.currentTimeMillis());
        long purgeAfter = LostTalesMath.saturatingAdd(deletedAt,
                (long) Math.max(1, LostTalesConfig.characterDeletionRetentionDays)
                        * MILLIS_PER_DAY);
        tombstone.commit(deletedAt, purgeAfter);
        deletionData.saveTombstone(tombstone);
        try {
            // MapStorage saves the roster, party cleanup, snapshot manifest,
            // and tombstone in the same authoritative server save pass.
            CharacterDeletionStorage.flush(player.worldObj);
        } catch (RuntimeException exception) {
            // The in-memory deletion has committed. Reporting failure here
            // would invite a contradictory retry while normal world saving can
            // still persist the dirty stores.
            logFailure("delete_post_commit_flush", player.getUniqueID(),
                    character.getCharacterId(), exception);
        }
        FMLLog.info("[%s] Tombstoned character %s for owner %s at state generation %d; purge allowed after %d",
                LostTalesMetaData.MOD_ID,
                character.getCharacterId(),
                player.getUniqueID(),
                Long.valueOf(current.getGeneration()),
                Long.valueOf(purgeAfter));
        return CharacterOperationResult.success(true, roster, removed);
    }

    public synchronized CharacterDeletionMaintenanceResult restore(
            EntityPlayerMP target, UUID characterId) {
        if (!isValidTarget(target, characterId)) {
            return CharacterDeletionMaintenanceResult.NOT_FOUND;
        }
        try {
            CharacterWorldData characterData = CharacterStorage.get(target.worldObj);
            CharacterDeletionWorldData deletionData =
                    CharacterDeletionStorage.get(target.worldObj);
            if (characterData.isReadOnlyForNewerVersion()
                    || deletionData.isReadOnlyForNewerVersion()) {
                return CharacterDeletionMaintenanceResult.STORAGE_READ_ONLY;
            }
            CharacterDeletionTombstone tombstone =
                    deletionData.getTombstone(characterId);
            if (tombstone == null
                    || !target.getUniqueID().equals(tombstone.getOwnerId())) {
                return CharacterDeletionMaintenanceResult.NOT_FOUND;
            }
            CharacterRoster roster = characterData.getOrCreateRoster(
                    target.getUniqueID());
            RoleplayCharacter existing = roster.getCharacter(characterId);
            if (existing != null) {
                deletionData.removeTombstone(characterId);
                flushCommitted(target.worldObj, "restore_reconcile",
                        target.getUniqueID(), characterId);
                return CharacterDeletionMaintenanceResult.RECONCILED;
            }
            if (characterData.containsCharacter(characterId)) {
                return CharacterDeletionMaintenanceResult.CHARACTER_ID_CONFLICT;
            }

            RoleplayCharacter character = tombstone.getCharacterCopy();
            if (roster.getCharacterAtSlot(character.getSlotIndex()) != null) {
                return CharacterDeletionMaintenanceResult.SLOT_OCCUPIED;
            }
            if (nameRefusal(characterData, character) != CharacterErrorId.NONE) {
                return CharacterDeletionMaintenanceResult.NAME_TAKEN;
            }
            CharacterPlayerStateWorldData playerStateData =
                    CharacterPlayerStateStorage.get(
                            target.worldObj, target.getUniqueID());
            if (playerStateData.isReadOnlyForNewerVersion()
                    || playerStateData.isOwnerBlocked(target.getUniqueID())) {
                return CharacterDeletionMaintenanceResult.PLAYER_STATE_UNAVAILABLE;
            }
            CharacterPlayerStateAccount account = playerStateData.getAccount(
                    target.getUniqueID());
            CharacterPlayerStateRecord record = account == null ? null
                    : account.getRecord(characterId);
            if (record == null
                    || record.getCurrentGeneration()
                    != tombstone.getStateGeneration()) {
                return CharacterDeletionMaintenanceResult.PLAYER_STATE_UNAVAILABLE;
            }
            CharacterPlayerStateService.getInstance().validateSnapshot(
                    record.getCurrent());

            if (!roster.addCharacter(character)) {
                return CharacterDeletionMaintenanceResult.SLOT_OCCUPIED;
            }
            roster.incrementRevision();
            characterData.saveRoster(roster);
            // Publish the restored roster while the tombstone is still
            // durable. If the server stops here, the next restore simply
            // reconciles the stale tombstone instead of losing both copies.
            try {
                CharacterDeletionStorage.flush(target.worldObj);
            } catch (RuntimeException exception) {
                logFailure("restore_publish_roster", target.getUniqueID(),
                        characterId, exception);
                return CharacterDeletionMaintenanceResult.INTERNAL_ERROR;
            }
            deletionData.removeTombstone(characterId);
            flushCommitted(target.worldObj, "restore_commit",
                    target.getUniqueID(), characterId);
            try {
                CharacterSyncManager.sendRoster(
                        target,
                        CharacterSyncManager.UNSOLICITED_REQUEST_ID,
                        roster);
            } catch (RuntimeException exception) {
                logFailure("restore_sync", target.getUniqueID(),
                        characterId, exception);
            }
            FMLLog.info("[%s] Restored character %s for owner %s from state generation %d",
                    LostTalesMetaData.MOD_ID, characterId,
                    target.getUniqueID(),
                    Long.valueOf(record.getCurrentGeneration()));
            return CharacterDeletionMaintenanceResult.SUCCESS;
        } catch (CharacterStateValidationException exception) {
            logFailure("restore_validate_state", target.getUniqueID(),
                    characterId, exception);
            return CharacterDeletionMaintenanceResult.PLAYER_STATE_UNAVAILABLE;
        } catch (RuntimeException exception) {
            logFailure("restore", target.getUniqueID(), characterId, exception);
            return CharacterDeletionMaintenanceResult.INTERNAL_ERROR;
        }
    }

    public synchronized CharacterDeletionMaintenanceResult purge(
            EntityPlayerMP target, UUID characterId) {
        if (!isValidTarget(target, characterId)) {
            return CharacterDeletionMaintenanceResult.NOT_FOUND;
        }
        return purge(target.worldObj, target.getUniqueID(), characterId, true);
    }

    /**
     * Purges one deleted character past its retention. The stores are
     * flushed here only with {@code flush}; a batch flushes once at its
     * end.
     */
    private CharacterDeletionMaintenanceResult purge(World world,
                                                     UUID ownerId,
                                                     UUID characterId,
                                                     boolean flush) {
        try {
            CharacterWorldData characterData = CharacterStorage.get(world);
            CharacterDeletionWorldData deletionData =
                    CharacterDeletionStorage.get(world);
            if (characterData.isReadOnlyForNewerVersion()
                    || deletionData.isReadOnlyForNewerVersion()) {
                return CharacterDeletionMaintenanceResult.STORAGE_READ_ONLY;
            }
            CharacterDeletionTombstone tombstone =
                    deletionData.getTombstone(characterId);
            if (tombstone == null
                    || !ownerId.equals(tombstone.getOwnerId())) {
                return CharacterDeletionMaintenanceResult.NOT_FOUND;
            }
            if (!tombstone.isCommitted()) {
                return CharacterDeletionMaintenanceResult.NOT_COMMITTED;
            }
            if (!tombstone.isPurgeAllowed(System.currentTimeMillis())) {
                return CharacterDeletionMaintenanceResult.RETENTION_ACTIVE;
            }
            if (characterData.containsCharacter(characterId)) {
                return CharacterDeletionMaintenanceResult.CHARACTER_ID_CONFLICT;
            }

            CharacterPlayerStateWorldData playerStateData =
                    CharacterPlayerStateStorage.get(world, ownerId);
            if (playerStateData.isReadOnlyForNewerVersion()
                    || playerStateData.isOwnerBlocked(ownerId)) {
                return CharacterDeletionMaintenanceResult.PLAYER_STATE_UNAVAILABLE;
            }
            CharacterPlayerStateAccount account = playerStateData.getAccount(
                    ownerId);
            if (account != null) {
                account.removeRecord(characterId);
                playerStateData.saveAccount(account);
            }
            deletionData.removeTombstone(characterId);
            if (flush) {
                flushCommitted(world, "purge_commit", ownerId, characterId);
            }
            FMLLog.info("[%s] Permanently purged tombstoned character %s for owner %s",
                    LostTalesMetaData.MOD_ID, characterId, ownerId);
            return CharacterDeletionMaintenanceResult.SUCCESS;
        } catch (RuntimeException exception) {
            logFailure("purge", ownerId, characterId, exception);
            return CharacterDeletionMaintenanceResult.INTERNAL_ERROR;
        }
    }

    /**
     * Purges the owner's deleted characters past their retention, at most
     * {@code limit} of them, soonest due first. Answers how many went.
     */
    public synchronized int purgeExpired(World world, UUID ownerId, int limit) {
        if (world == null || world.isRemote || ownerId == null) {
            return 0;
        }
        try {
            CharacterDeletionWorldData data = CharacterDeletionStorage.get(world);
            if (data.isReadOnlyForNewerVersion()) {
                return 0;
            }
            return purgeAll(world, data.getExpired(ownerId,
                    System.currentTimeMillis(), limit), "login");
        } catch (RuntimeException exception) {
            logFailure("purge_expired_owner", ownerId, null, exception);
            return 0;
        }
    }

    /**
     * Purges every owner's deleted characters past their retention, at most
     * {@code limit} of them, soonest due first; the rest go at their
     * owners' logins or the next start. Answers how many went.
     */
    public synchronized int purgeExpired(World world, int limit) {
        if (world == null || world.isRemote) {
            return 0;
        }
        try {
            CharacterDeletionWorldData data = CharacterDeletionStorage.get(world);
            if (data.isReadOnlyForNewerVersion()) {
                return 0;
            }
            return purgeAll(world, data.getExpired(null,
                    System.currentTimeMillis(), limit), "start");
        } catch (RuntimeException exception) {
            logFailure("purge_expired", null, null, exception);
            return 0;
        }
    }

    private int purgeAll(World world, List<CharacterDeletionTombstone> due,
                         String when) {
        int purged = 0;
        for (CharacterDeletionTombstone tombstone : due) {
            if (purge(world, tombstone.getOwnerId(), tombstone.getCharacterId(),
                    false) == CharacterDeletionMaintenanceResult.SUCCESS) {
                purged++;
            }
        }
        if (purged > 0) {
            flushCommitted(world, "purge_expired_" + when, null, null);
            FMLLog.info("[%s] Purged %d deleted character(s) past their retention at %s",
                    LostTalesMetaData.MOD_ID, Integer.valueOf(purged), when);
        }
        return purged;
    }

    /**
     * The owner restoring one of their deleted characters within its
     * retention, into the first free open slot of their roster. Refused
     * while its name is now another character's, or reserved.
     */
    public synchronized CharacterOperationResult restoreOwn(
            EntityPlayerMP player, long expectedRosterRevision,
            UUID characterId) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.INVALID_PLAYER, null);
        }
        if (characterId == null) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.INVALID_CHARACTER_ID, null);
        }
        CharacterValidationResult manage =
                CharacterValidator.validatePlayerCanManage(player);
        if (!manage.isValid()) {
            CharacterErrorId error = manage.getErrorId();
            if (error == CharacterErrorId.PLAYER_DEAD
                    || error == CharacterErrorId.PLAYER_SLEEPING) {
                error = CharacterErrorId.RESTORE_NOT_ALLOWED;
            }
            return CharacterOperationResult.failure(error, null);
        }
        UUID ownerId = player.getUniqueID();
        World world = player.worldObj;
        CharacterRoster roster = null;
        try {
            CharacterWorldData characterData = CharacterStorage.get(world);
            if (characterData.isReadOnlyForNewerVersion()) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.STORAGE_READ_ONLY, null);
            }
            roster = characterData.getOrCreateRoster(ownerId);
            CharacterDeletionWorldData deletionData =
                    CharacterDeletionStorage.get(world);
            if (deletionData.isReadOnlyForNewerVersion()) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_STORAGE_READ_ONLY, roster);
            }
            CharacterValidationResult revision =
                    CharacterValidator.validateExpectedRevision(
                            roster, expectedRosterRevision);
            if (!revision.isValid()) {
                return CharacterOperationResult.failure(
                        revision.getErrorId(), roster);
            }
            CharacterDeletionTombstone tombstone =
                    deletionData.getTombstone(characterId);
            if (!isRestorable(tombstone, ownerId,
                    System.currentTimeMillis())) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_NOT_FOUND, roster);
            }
            if (characterData.containsCharacter(characterId)) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_NOT_FOUND, roster);
            }
            RoleplayCharacter stored = tombstone.getCharacterCopy();
            CharacterErrorId name = nameRefusal(characterData, stored);
            if (name != CharacterErrorId.NONE) {
                return CharacterOperationResult.failure(name, roster);
            }
            int slot = firstFreeOpenSlot(roster);
            if (slot < 0 || roster.roleplayCharacterCount()
                    >= CharacterRoster.MAX_SLOTS) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_NO_SLOT, roster);
            }
            CharacterPlayerStateWorldData playerStateData =
                    CharacterPlayerStateStorage.get(world, ownerId);
            if (playerStateData.isReadOnlyForNewerVersion()
                    || playerStateData.isOwnerBlocked(ownerId)) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_STATE_UNAVAILABLE, roster);
            }
            CharacterPlayerStateAccount account =
                    playerStateData.getAccount(ownerId);
            CharacterPlayerStateRecord record = account == null ? null
                    : account.getRecord(characterId);
            if (record == null || record.getCurrentGeneration()
                    != tombstone.getStateGeneration()) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_STATE_UNAVAILABLE, roster);
            }
            CharacterPlayerStateService.getInstance().validateSnapshot(
                    record.getCurrent());

            RoleplayCharacter character = RoleplayCharacter.builder(stored)
                    .slot(slot).build();
            if (!roster.addCharacter(character)) {
                return CharacterOperationResult.failure(
                        CharacterErrorId.RESTORE_NO_SLOT, roster);
            }
            roster.unlockNextSlotAfter(slot);
            roster.incrementRevision();
            characterData.saveRoster(roster);
            // The roster is published while the tombstone is still
            // durable: a stop in between leaves a stale tombstone the next
            // restore clears, never a character lost from both.
            CharacterDeletionStorage.flush(world);
            deletionData.removeTombstone(characterId);
            flushCommitted(world, "restore_own_commit", ownerId, characterId);
            FMLLog.info("[%s] Owner %s restored deleted character %s into slot %d",
                    LostTalesMetaData.MOD_ID, ownerId, characterId,
                    Integer.valueOf(slot));
            return CharacterOperationResult.success(true, roster, character);
        } catch (CharacterStateValidationException exception) {
            logFailure("restore_own_validate_state", ownerId, characterId,
                    exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.RESTORE_STATE_UNAVAILABLE, roster);
        } catch (RuntimeException exception) {
            logFailure("restore_own", ownerId, characterId, exception);
            return CharacterOperationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR, roster);
        }
    }

    /**
     * The owner's deleted characters still within their retention, as the
     * roster's foot lists them: soonest purged first. None while the
     * journal cannot be read.
     */
    public synchronized List<DeletedCharacterSummary> deletedOf(World world,
                                                               UUID ownerId) {
        if (world == null || world.isRemote || ownerId == null) {
            return Collections.emptyList();
        }
        try {
            CharacterDeletionWorldData data = CharacterDeletionStorage.get(world);
            if (data.isReadOnlyForNewerVersion()) {
                return Collections.emptyList();
            }
            long now = System.currentTimeMillis();
            List<CharacterDeletionTombstone> kept =
                    new ArrayList<CharacterDeletionTombstone>();
            for (CharacterDeletionTombstone tombstone
                    : data.getTombstones(ownerId)) {
                if (isRestorable(tombstone, ownerId, now)) {
                    kept.add(tombstone);
                }
            }
            CharacterDeletionWorldData.sortByPurgeTime(kept);
            List<DeletedCharacterSummary> deleted =
                    new ArrayList<DeletedCharacterSummary>();
            for (CharacterDeletionTombstone tombstone : kept) {
                if (deleted.size() >= CharacterRosterSnapshot.MAX_DELETED) {
                    break;
                }
                RoleplayCharacter character = tombstone.getCharacterCopy();
                deleted.add(new DeletedCharacterSummary(
                        character.getCharacterId(), character.getName(),
                        character.getRaceId(), character.getSkinId(),
                        DeletedCharacterSummary.daysLeft(now,
                                tombstone.getPurgeAfter())));
            }
            return deleted;
        } catch (RuntimeException exception) {
            logFailure("list_deleted", ownerId, null, exception);
            return Collections.emptyList();
        }
    }

    /** Whether the owner may still restore it: theirs, committed, and within its retention. */
    static boolean isRestorable(CharacterDeletionTombstone tombstone,
                                UUID ownerId, long now) {
        return tombstone != null && ownerId != null
                && ownerId.equals(tombstone.getOwnerId())
                && tombstone.isCommitted()
                && !tombstone.isPurgeAllowed(now);
    }

    /**
     * Why a deleted character cannot come back under its name: a lore
     * character's or a chat voice's now, or another character's on the
     * server; {@link CharacterErrorId#NONE} when it can.
     */
    static CharacterErrorId nameRefusal(CharacterWorldData characterData,
                                        RoleplayCharacter character) {
        String name = character.getName();
        if (LoreCharacterRegistry.getByName(name) != null
                || CharacterNames.isVoice(name)) {
            return CharacterErrorId.NAME_RESERVED;
        }
        return characterData.isNameTaken(name, character.getCharacterId())
                ? CharacterErrorId.DUPLICATE_NAME : CharacterErrorId.NONE;
    }

    /** The first open slot with nothing in it; -1 for none. */
    static int firstFreeOpenSlot(CharacterRoster roster) {
        for (int slot = 0; slot < roster.getUnlockedSlotCount()
                && slot < CharacterRoster.MAX_SLOTS; slot++) {
            if (roster.getCharacterAtSlot(slot) == null) {
                return slot;
            }
        }
        return -1;
    }

    public synchronized CharacterDeletionMaintenanceResult rollbackInactive(
            EntityPlayerMP target, UUID characterId) {
        if (!isValidTarget(target, characterId)) {
            return CharacterDeletionMaintenanceResult.NOT_FOUND;
        }
        try {
            CharacterWorldData characterData = CharacterStorage.get(target.worldObj);
            if (characterData.isReadOnlyForNewerVersion()) {
                return CharacterDeletionMaintenanceResult.STORAGE_READ_ONLY;
            }
            CharacterRoster roster = characterData.getRoster(target.getUniqueID());
            if (roster == null || roster.getCharacter(characterId) == null) {
                return CharacterDeletionMaintenanceResult.NOT_FOUND;
            }
            if (characterId.equals(roster.getActiveCharacterId())) {
                return CharacterDeletionMaintenanceResult.CHARACTER_ACTIVE;
            }
            CharacterPlayerStateWorldData playerStateData =
                    CharacterPlayerStateStorage.get(
                            target.worldObj, target.getUniqueID());
            if (playerStateData.isReadOnlyForNewerVersion()
                    || playerStateData.isOwnerBlocked(target.getUniqueID())) {
                return CharacterDeletionMaintenanceResult.PLAYER_STATE_UNAVAILABLE;
            }
            CharacterPlayerStateAccount account = playerStateData.getAccount(
                    target.getUniqueID());
            CharacterPlayerStateRecord record = account == null ? null
                    : account.getRecord(characterId);
            if (record == null || record.getPrevious() == null) {
                return CharacterDeletionMaintenanceResult.PREVIOUS_GENERATION_UNAVAILABLE;
            }
            CharacterPlayerStateService stateService =
                    CharacterPlayerStateService.getInstance();
            stateService.validateSnapshot(record.getPrevious());
            CharacterPlayerStateSnapshot rollback = record.createNext(
                    Math.max(1L, System.currentTimeMillis()),
                    record.getPrevious().copyComponents());
            stateService.validateSnapshot(rollback);
            long replacedGeneration = record.getCurrentGeneration();
            record.commit(rollback);
            playerStateData.saveAccount(account);
            flushCommitted(target.worldObj, "rollback_commit",
                    target.getUniqueID(), characterId);
            FMLLog.info("[%s] Rolled inactive character %s for owner %s from generation %d into recovery generation %d",
                    LostTalesMetaData.MOD_ID, characterId,
                    target.getUniqueID(),
                    Long.valueOf(replacedGeneration),
                    Long.valueOf(rollback.getGeneration()));
            return CharacterDeletionMaintenanceResult.SUCCESS;
        } catch (CharacterStateValidationException exception) {
            logFailure("rollback_validate_state", target.getUniqueID(),
                    characterId, exception);
            return CharacterDeletionMaintenanceResult.PLAYER_STATE_UNAVAILABLE;
        } catch (RuntimeException exception) {
            logFailure("rollback", target.getUniqueID(), characterId, exception);
            return CharacterDeletionMaintenanceResult.INTERNAL_ERROR;
        }
    }

    public synchronized List<CharacterDeletionTombstone> getTombstones(
            World world, UUID ownerId) {
        if (world == null || ownerId == null) {
            return Collections.emptyList();
        }
        CharacterDeletionWorldData data = CharacterDeletionStorage.get(world);
        if (data.isReadOnlyForNewerVersion()) {
            return Collections.emptyList();
        }
        return data.getTombstones(ownerId);
    }

    public synchronized CharacterDeletionTombstone getTombstone(
            World world, UUID characterId) {
        if (world == null || characterId == null) {
            return null;
        }
        CharacterDeletionWorldData data = CharacterDeletionStorage.get(world);
        return data.isReadOnlyForNewerVersion()
                ? null : data.getTombstone(characterId);
    }

    private static boolean isValidTarget(
            EntityPlayerMP target, UUID characterId) {
        return target != null && target.worldObj != null
                && !target.worldObj.isRemote && characterId != null;
    }

    private static CharacterErrorId mapPartyCleanupError(
            PartyErrorId errorId) {
        if (errorId == PartyErrorId.PARTY_STORAGE_READ_ONLY) {
            return CharacterErrorId.PARTY_STORAGE_READ_ONLY;
        }
        if (errorId == PartyErrorId.INVITATION_STORAGE_READ_ONLY) {
            return CharacterErrorId.PARTY_INVITATION_STORAGE_READ_ONLY;
        }
        return CharacterErrorId.PARTY_CLEANUP_FAILED;
    }

    private static void flushCommitted(
            World world, String phase, UUID ownerId, UUID characterId) {
        try {
            CharacterDeletionStorage.flush(world);
        } catch (RuntimeException exception) {
            logFailure(phase, ownerId, characterId, exception);
        }
    }

    private static void logFailure(
            String phase, UUID ownerId, UUID characterId, Throwable throwable) {
        FMLLog.warning("[%s] Character deletion phase %s failed for owner %s, character %s: %s",
                LostTalesMetaData.MOD_ID,
                phase,
                ownerId,
                characterId,
                throwable == null ? "unknown" : throwable.toString());
    }
}
