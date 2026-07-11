package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterCreationValidationResult;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.character.validation.ValidatedCharacterCreation;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Single authoritative entry point for roleplaying character mutations.
 *
 * Callers must invoke this service on the logical server thread. Public
 * mutation methods are synchronized as a defensive atomicity boundary; later
 * packet handlers must still schedule work onto the server thread first.
 */
public final class CharacterService {

    private static final int UUID_GENERATION_ATTEMPTS = 8;

    private final CharacterFactionResolver factionResolver;

    public CharacterService(CharacterFactionResolver factionResolver) {
        if (factionResolver == null) {
            throw new IllegalArgumentException("factionResolver must not be null");
        }
        this.factionResolver = factionResolver;
    }

    public static CharacterService getInstance() {
        return Holder.INSTANCE;
    }

    public synchronized CharacterOperationResult ensureRoster(EntityPlayerMP player) {
        CharacterValidationResult playerValidation = validateServerPlayer(player);
        if (!playerValidation.isValid()) {
            return CharacterOperationResult.failure(playerValidation.getErrorId(), null);
        }

        CharacterWorldData data = getData(player);
        if (data == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        if (data.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(CharacterErrorId.STORAGE_READ_ONLY, null);
        }
        CharacterRoster existing = data.getRoster(player.getUniqueID());
        if (existing != null) {
            return CharacterOperationResult.success(false, existing, existing.getActiveCharacter());
        }
        CharacterRoster created = data.getOrCreateRoster(player.getUniqueID());
        return CharacterOperationResult.success(true, created, null);
    }

    public synchronized CharacterOperationResult getRoster(EntityPlayerMP player) {
        CharacterValidationResult playerValidation = validateServerPlayer(player);
        if (!playerValidation.isValid()) {
            return CharacterOperationResult.failure(playerValidation.getErrorId(), null);
        }
        CharacterWorldData data = getData(player);
        if (data == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        if (data.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(CharacterErrorId.STORAGE_READ_ONLY, null);
        }
        CharacterRoster roster = data.getRoster(player.getUniqueID());
        if (roster == null) {
            roster = data.getOrCreateRoster(player.getUniqueID());
            return CharacterOperationResult.success(true, roster, null);
        }
        return CharacterOperationResult.success(false, roster, roster.getActiveCharacter());
    }

    public synchronized CharacterOperationResult createCharacter(
            EntityPlayerMP player, CharacterCreationRequest request) {
        CharacterValidationResult playerValidation = validateServerPlayer(player);
        if (!playerValidation.isValid()) {
            return CharacterOperationResult.failure(playerValidation.getErrorId(), null);
        }
        CharacterValidationResult managementValidation =
                CharacterValidator.validatePlayerCanManage(player);
        if (!managementValidation.isValid()) {
            return CharacterOperationResult.failure(managementValidation.getErrorId(), null);
        }

        CharacterWorldData data = getData(player);
        if (data == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        if (data.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(CharacterErrorId.STORAGE_READ_ONLY, null);
        }
        CharacterRoster roster = data.getOrCreateRoster(player.getUniqueID());
        CharacterCreationValidationResult validation = CharacterValidator.validateCreation(
                roster, request, this.factionResolver);
        if (!validation.isValid()) {
            return CharacterOperationResult.failure(validation.getErrorId(), roster);
        }

        ValidatedCharacterCreation creation = validation.getCreation();
        RoleplayCharacter character = createUniqueCharacter(roster, player.getUniqueID(), creation);
        if (character == null || !roster.addCharacter(character)) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }

        boolean createdInHighestUnlockedSlot =
                creation.getSlotIndex() == roster.getUnlockedSlotCount() - 1;
        if (createdInHighestUnlockedSlot) {
            roster.unlockNextSlot();
        }
        if (roster.getActiveCharacterId() == null && roster.getCharacterCount() == 1) {
            roster.setActiveCharacterId(character.getCharacterId());
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, character);
    }

    public synchronized CharacterOperationResult selectCharacter(
            EntityPlayerMP player, long expectedRosterRevision, UUID characterId) {
        CharacterValidationResult playerValidation = validateServerPlayer(player);
        if (!playerValidation.isValid()) {
            return CharacterOperationResult.failure(playerValidation.getErrorId(), null);
        }
        CharacterValidationResult managementValidation =
                CharacterValidator.validatePlayerCanManage(player);
        if (!managementValidation.isValid()) {
            CharacterErrorId error = managementValidation.getErrorId();
            if (error == CharacterErrorId.PLAYER_DEAD
                    || error == CharacterErrorId.PLAYER_SLEEPING) {
                error = CharacterErrorId.SWITCH_NOT_ALLOWED;
            }
            return CharacterOperationResult.failure(error, null);
        }

        CharacterWorldData data = getData(player);
        if (data == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        if (data.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(CharacterErrorId.STORAGE_READ_ONLY, null);
        }
        CharacterRoster roster = data.getOrCreateRoster(player.getUniqueID());
        CharacterValidationResult validation = CharacterValidator.validateCharacterReference(
                roster, characterId, expectedRosterRevision);
        if (!validation.isValid()) {
            return CharacterOperationResult.failure(validation.getErrorId(), roster);
        }

        RoleplayCharacter character = roster.getCharacter(characterId);
        if (characterId.equals(roster.getActiveCharacterId())) {
            return CharacterOperationResult.success(false, roster, character);
        }

        roster.setActiveCharacterId(characterId);
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, character);
    }

    public synchronized CharacterOperationResult deleteCharacter(
            EntityPlayerMP player, long expectedRosterRevision, UUID characterId) {
        CharacterValidationResult playerValidation = validateServerPlayer(player);
        if (!playerValidation.isValid()) {
            return CharacterOperationResult.failure(playerValidation.getErrorId(), null);
        }
        CharacterValidationResult managementValidation =
                CharacterValidator.validatePlayerCanManage(player);
        if (!managementValidation.isValid()) {
            CharacterErrorId error = managementValidation.getErrorId();
            if (error == CharacterErrorId.PLAYER_DEAD
                    || error == CharacterErrorId.PLAYER_SLEEPING) {
                error = CharacterErrorId.DELETE_NOT_ALLOWED;
            }
            return CharacterOperationResult.failure(error, null);
        }

        CharacterWorldData data = getData(player);
        if (data == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        if (data.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(CharacterErrorId.STORAGE_READ_ONLY, null);
        }
        CharacterRoster roster = data.getOrCreateRoster(player.getUniqueID());
        CharacterValidationResult validation = CharacterValidator.validateCharacterReference(
                roster, characterId, expectedRosterRevision);
        if (!validation.isValid()) {
            return CharacterOperationResult.failure(validation.getErrorId(), roster);
        }

        RoleplayCharacter removed = roster.removeCharacter(characterId);
        if (removed == null) {
            return CharacterOperationResult.failure(CharacterErrorId.CHARACTER_NOT_FOUND, roster);
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, removed);
    }

    private CharacterValidationResult validateServerPlayer(EntityPlayerMP player) {
        if (player == null || player.worldObj == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_PLAYER);
        }
        if (player.worldObj.isRemote) {
            return CharacterValidationResult.failure(CharacterErrorId.CLIENT_SIDE_REQUEST);
        }
        return CharacterValidationResult.success();
    }

    private CharacterWorldData getData(EntityPlayerMP player) {
        try {
            return CharacterStorage.get(player.worldObj);
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Failed to access character storage for player %s: %s",
                    LostTalesMetaData.MOD_ID, player.getUniqueID(), exception.toString());
            return null;
        }
    }

    private RoleplayCharacter createUniqueCharacter(CharacterRoster roster, UUID ownerId,
                                                     ValidatedCharacterCreation creation) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID characterId = UUID.randomUUID();
            if (roster.getCharacter(characterId) != null) {
                continue;
            }
            return new RoleplayCharacter(
                    characterId,
                    ownerId,
                    creation.getSlotIndex(),
                    creation.getName(),
                    creation.getRaceId(),
                    creation.getGenderId(),
                    creation.getSkinId(),
                    creation.getAge(),
                    creation.getStartingFactionId(),
                    RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL,
                    new CharacterProgression(),
                    System.currentTimeMillis(),
                    RoleplayCharacter.CURRENT_DATA_VERSION
            );
        }
        return null;
    }

    private static final class Holder {
        private static final CharacterService INSTANCE =
                new CharacterService(LotrCharacterAdapter.getInstance());
    }
}
