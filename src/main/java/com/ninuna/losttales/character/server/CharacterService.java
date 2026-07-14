package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterCreationValidationResult;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.character.validation.ValidatedCharacterCreation;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.server.PartyOperationResult;
import com.ninuna.losttales.party.server.PartyService;
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
    private final CharacterCapeEligibilityPolicy capeEligibilityPolicy;

    public CharacterService(CharacterFactionResolver factionResolver) {
        this(factionResolver, new AllowlistedCharacterCapeEligibilityPolicy());
    }

    public CharacterService(CharacterFactionResolver factionResolver,
                            CharacterCapeEligibilityPolicy capeEligibilityPolicy) {
        if (factionResolver == null) {
            throw new IllegalArgumentException("factionResolver must not be null");
        }
        if (capeEligibilityPolicy == null) {
            throw new IllegalArgumentException("capeEligibilityPolicy must not be null");
        }
        this.factionResolver = factionResolver;
        this.capeEligibilityPolicy = capeEligibilityPolicy;
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
        RoleplayCharacter character = createUniqueCharacter(
                data, player.getUniqueID(), creation);
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

    public CharacterOperationResult selectCharacter(
            EntityPlayerMP player, int requestId,
            long expectedRosterRevision, UUID characterId) {
        return CharacterSwitchCoordinator.getInstance().selectCharacter(
                player, requestId, expectedRosterRevision, characterId);
    }

    public synchronized CharacterOperationResult updateCapeSettings(
            EntityPlayerMP player, long expectedRosterRevision, UUID characterId,
            boolean showMinecraftCape, int cosmeticCapeId) {
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
                error = CharacterErrorId.CAPE_UPDATE_NOT_ALLOWED;
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
        CharacterValidationResult referenceValidation =
                CharacterValidator.validateCharacterReference(
                        roster, characterId, expectedRosterRevision);
        if (!referenceValidation.isValid()) {
            return CharacterOperationResult.failure(
                    referenceValidation.getErrorId(), roster);
        }
        if (!CharacterCapeCatalog.isValidSelection(cosmeticCapeId)) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_CAPE, roster);
        }

        RoleplayCharacter character = roster.getCharacter(characterId);
        CharacterValidationResult eligibility = this.capeEligibilityPolicy.validate(
                player, character, cosmeticCapeId);
        if (!eligibility.isValid()) {
            CharacterErrorId error = eligibility.getErrorId();
            if (error == CharacterErrorId.NONE) {
                error = CharacterErrorId.CAPE_NOT_ELIGIBLE;
            }
            return CharacterOperationResult.failure(error, roster);
        }

        boolean changed = character.setCapeSettings(
                showMinecraftCape, cosmeticCapeId);
        if (changed) {
            roster.incrementRevision();
            data.saveRoster(roster);
        }
        return CharacterOperationResult.success(changed, roster, character);
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

        if (characterId.equals(roster.getActiveCharacterId())) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_ACTIVE_CHARACTER, roster);
        }

        RoleplayCharacter character = roster.getCharacter(characterId);
        PartyOperationResult partyCleanup = PartyService.getInstance()
                .removeCharacterForDeletion(player.worldObj, character);
        if (!partyCleanup.isSuccessful()) {
            CharacterErrorId error;
            if (partyCleanup.getErrorId() == PartyErrorId.PARTY_STORAGE_READ_ONLY) {
                error = CharacterErrorId.PARTY_STORAGE_READ_ONLY;
            } else if (partyCleanup.getErrorId()
                    == PartyErrorId.INVITATION_STORAGE_READ_ONLY) {
                error = CharacterErrorId.PARTY_INVITATION_STORAGE_READ_ONLY;
            } else {
                error = CharacterErrorId.PARTY_CLEANUP_FAILED;
            }
            return CharacterOperationResult.failure(error, roster);
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

    private RoleplayCharacter createUniqueCharacter(CharacterWorldData data, UUID ownerId,
                                                     ValidatedCharacterCreation creation) {
        for (int attempt = 0; attempt < UUID_GENERATION_ATTEMPTS; attempt++) {
            UUID characterId = UUID.randomUUID();
            if (containsCharacterId(data, characterId)) {
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

    private boolean containsCharacterId(CharacterWorldData data, UUID characterId) {
        if (data == null || characterId == null) {
            return false;
        }
        for (CharacterRoster roster : data.getRosters()) {
            if (roster != null && roster.getCharacter(characterId) != null) {
                return true;
            }
        }
        return false;
    }

    private static final class Holder {
        private static final CharacterService INSTANCE =
                new CharacterService(
                        LotrCharacterAdapter.getInstance(),
                        new AllowlistedCharacterCapeEligibilityPolicy());
    }
}
