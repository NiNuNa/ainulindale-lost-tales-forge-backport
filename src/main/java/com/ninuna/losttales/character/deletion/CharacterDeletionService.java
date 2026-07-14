package com.ninuna.losttales.character.deletion;

import com.ninuna.losttales.LostTalesMetaData;
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
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.server.PartyOperationResult;
import com.ninuna.losttales.party.server.PartyService;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-authoritative deletion, restoration, purge, and inactive-generation
 * rollback coordinator.
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
                            player, roster, playerStateData, null);
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
        long purgeAfter = saturatingAdd(deletedAt,
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
            if (containsCharacterId(characterData, characterId)) {
                return CharacterDeletionMaintenanceResult.CHARACTER_ID_CONFLICT;
            }

            RoleplayCharacter character = tombstone.getCharacterCopy();
            if (roster.getCharacterAtSlot(character.getSlotIndex()) != null) {
                return CharacterDeletionMaintenanceResult.SLOT_OCCUPIED;
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
            if (!tombstone.isCommitted()) {
                return CharacterDeletionMaintenanceResult.NOT_COMMITTED;
            }
            if (!tombstone.isPurgeAllowed(System.currentTimeMillis())) {
                return CharacterDeletionMaintenanceResult.RETENTION_ACTIVE;
            }
            if (containsCharacterId(characterData, characterId)) {
                return CharacterDeletionMaintenanceResult.CHARACTER_ID_CONFLICT;
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
            if (account != null) {
                account.removeRecord(characterId);
                playerStateData.saveAccount(account);
            }
            deletionData.removeTombstone(characterId);
            flushCommitted(target.worldObj, "purge_commit",
                    target.getUniqueID(), characterId);
            FMLLog.info("[%s] Permanently purged tombstoned character %s for owner %s",
                    LostTalesMetaData.MOD_ID, characterId,
                    target.getUniqueID());
            return CharacterDeletionMaintenanceResult.SUCCESS;
        } catch (RuntimeException exception) {
            logFailure("purge", target.getUniqueID(), characterId, exception);
            return CharacterDeletionMaintenanceResult.INTERNAL_ERROR;
        }
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

    private static boolean containsCharacterId(
            CharacterWorldData data, UUID characterId) {
        for (CharacterRoster candidate : data.getRosters()) {
            if (candidate.getCharacter(characterId) != null) {
                return true;
            }
        }
        return false;
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

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
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
