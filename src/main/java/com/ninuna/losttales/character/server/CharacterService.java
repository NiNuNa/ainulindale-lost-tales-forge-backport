package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterAppearanceValidationResult;
import com.ninuna.losttales.character.validation.CharacterCreationValidationResult;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.character.validation.ValidatedCharacterAppearance;
import com.ninuna.losttales.character.validation.ValidatedCharacterCreation;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Every change to a player's characters goes through here: making one,
 * its capes, its profile and look edits, deleting it.
 *
 * Callers must invoke this service on the logical server thread. Public
 * mutation methods are synchronized so each change is made whole;
 * packet handlers schedule their work onto the server thread first.
 */
public final class CharacterService {

    private static final int UUID_GENERATION_ATTEMPTS = 8;

    private final CharacterFactionResolver factionResolver;
    private final CharacterCapeEligibilityPolicy capeEligibilityPolicy;

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

    /**
     * The player's roster, made on first sight: the result says whether
     * it was just created (then with no active character) or already
     * existed. What a login and a roster request both ask for.
     */
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
        if (nameTakenElsewhere(data, player.getUniqueID(), creation.getName())) {
            return CharacterOperationResult.failure(CharacterErrorId.DUPLICATE_NAME, roster);
        }
        if (isAnotherAccountsName(player, creation.getName())) {
            return CharacterOperationResult.failure(CharacterErrorId.ACCOUNT_NAME, roster);
        }
        RoleplayCharacter character = createUniqueCharacter(
                data, player.getUniqueID(), creation);
        if (character == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }
        // The cape goes through the same gate a later cape change does.
        CharacterValidationResult cape = this.capeEligibilityPolicy.validate(
                player, character, request.getCosmeticCapeId());
        if (!cape.isValid()) {
            return CharacterOperationResult.failure(cape.getErrorId(), roster);
        }
        character.setCapeSettings(request.isMinecraftCapeVisible(),
                request.getCosmeticCapeId());
        if (!roster.addCharacter(character)) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }

        roster.unlockNextSlotAfter(creation.getSlotIndex());
        // A new character only joins the roster. The player stays on the
        // character they are playing and selects it when they choose to,
        // through the ordinary switch and its safeguards; a first
        // character is played at once by the join that asked for it
        // ({@link CharacterJoin}).
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, character);
    }

    /**
     * Plays as one of the roster's characters. Once a player has made
     * their first character there is no going back to playing without
     * one, so the account itself is refused.
     */
    public CharacterOperationResult selectIdentity(
            EntityPlayerMP player, int requestId,
            long expectedRosterRevision, PlayableIdentity target) {
        if (target == null || target.isAccount()) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.INVALID_CHARACTER_ID, null);
        }
        return CharacterSwitchCoordinator.getInstance().selectIdentity(
                player, requestId, expectedRosterRevision, target);
    }

    /**
     * An administrator's rename of one of {@code ownerId}'s characters, its
     * player online or not: the one way a character's name ever
     * changes. The new name is checked as a new character's is — its form,
     * a lore character's or a voice's name, another character's or another
     * account's — and a lore character keeps its own. Lines already said
     * keep the name they were said under. {@code accountName} is the owner's
     * account, whose own name their characters may bear.
     */
    public synchronized CharacterOperationResult renameByAdmin(World world,
            UUID ownerId, String accountName, UUID characterId, String newName) {
        if (world == null || world.isRemote || ownerId == null || characterId == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INVALID_CHARACTER_ID, null);
        }
        CharacterWorldData data = CharacterStorage.get(world);
        if (data.isReadOnlyForNewerVersion()) {
            return CharacterOperationResult.failure(CharacterErrorId.STORAGE_READ_ONLY, null);
        }
        CharacterRoster roster = data.getRoster(ownerId);
        RoleplayCharacter current = roster == null ? null : roster.getCharacter(characterId);
        if (current == null) {
            return CharacterOperationResult.failure(CharacterErrorId.CHARACTER_NOT_FOUND, roster);
        }
        CharacterErrorId lore = refuseLoreCharacter(world, characterId,
                CharacterErrorId.LORE_CHARACTER_CANNOT_EDIT);
        if (lore != CharacterErrorId.NONE) {
            return CharacterOperationResult.failure(lore, roster);
        }
        CharacterAppearanceValidationResult appearance =
                CharacterValidator.validateAppearance(roster, characterId,
                        current.getRaceId(), newName, current.getRaceId(),
                        current.getGenderId(), current.getSkinId(),
                        current.getBodyTypeId(), current.getChestTypeId(),
                        current.getProfile().section(CharacterProfile.Section.HISTORY),
                        current.getAge());
        if (!appearance.isValid()) {
            return CharacterOperationResult.failure(appearance.getErrorId(), roster);
        }
        String name = appearance.getAppearance().getName();
        if (nameTakenElsewhere(data, ownerId, name)) {
            return CharacterOperationResult.failure(CharacterErrorId.DUPLICATE_NAME, roster);
        }
        if (SeenAccountNames.isAnotherAccountsName(name, accountName,
                SeenAccountNames.ofServer(world, ownerId))) {
            return CharacterOperationResult.failure(CharacterErrorId.ACCOUNT_NAME, roster);
        }
        if (name.equals(current.getName())) {
            return CharacterOperationResult.success(false, roster, current);
        }
        RoleplayCharacter renamed = RoleplayCharacter.builder(current).name(name).build();
        if (!roster.replaceCharacter(renamed)) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        FMLLog.info("[%s] Character %s of %s renamed by an administrator",
                LostTalesMetaData.MOD_ID, characterId, ownerId);
        return CharacterOperationResult.success(true, roster, renamed);
    }

    /**
     * Whether a character of another account already goes by the name.
     * Names are unique on the server, so a whisper by name reaches the one
     * person it names; a roster's own names are the validator's to check.
     */
    private static boolean nameTakenElsewhere(CharacterWorldData data,
                                              UUID ownerId, String name) {
        for (CharacterRoster other : data.getRosters()) {
            if (other == null || ownerId.equals(other.getOwnerId())) {
                continue;
            }
            for (RoleplayCharacter character : other.getCharacters()) {
                if (character != null && CharacterNames.same(name, character.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether a name the player chose is another account's that the
     * server has seen ({@link SeenAccountNames}). The player's own account
     * name is theirs to take.
     */
    private static boolean isAnotherAccountsName(EntityPlayerMP player,
                                                 String name) {
        return SeenAccountNames.isAnotherAccountsName(name,
                SeenAccountNames.accountNameOf(player),
                SeenAccountNames.ofServer(player.worldObj, player.getUniqueID()));
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

        boolean changed = character.setCapeSettings(showMinecraftCape,
                cosmeticCapeId);
        if (changed) {
            roster.incrementRevision();
            data.saveRoster(roster);
        }
        return CharacterOperationResult.success(changed, roster, character);
    }

    /**
     * A character's profile and age, which its player may change at any
     * time; everything else creation settled stays as it is. A lore
     * character's are refused: its record passes from player to player,
     * and what one player wrote would be read as the next one's.
     */
    public synchronized CharacterOperationResult updateProfile(
            EntityPlayerMP player, long expectedRosterRevision, UUID characterId,
            CharacterProfile requestedProfile, int requestedAge) {
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
                error = CharacterErrorId.PROFILE_UPDATE_NOT_ALLOWED;
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
        CharacterValidationResult reference = CharacterValidator.validateCharacterReference(
                roster, characterId, expectedRosterRevision);
        if (!reference.isValid()) {
            return CharacterOperationResult.failure(reference.getErrorId(), roster);
        }
        CharacterErrorId lore = refuseLoreCharacter(player, characterId,
                CharacterErrorId.LORE_CHARACTER_CANNOT_EDIT);
        if (lore != CharacterErrorId.NONE) {
            return CharacterOperationResult.failure(lore, roster);
        }
        CharacterProfile profile = CharacterValidator.normalizeProfile(
                requestedProfile);
        CharacterValidationResult validation = CharacterValidator.validateProfile(
                profile, requestedAge);
        if (!validation.isValid()) {
            return CharacterOperationResult.failure(validation.getErrorId(),
                    roster);
        }

        RoleplayCharacter current = roster.getCharacter(characterId);
        if (current.getAge() == requestedAge
                && current.getProfile().equals(profile)) {
            return CharacterOperationResult.success(false, roster, current);
        }
        RoleplayCharacter updated = RoleplayCharacter.builder(current)
                .profile(profile)
                .age(requestedAge)
                .build();
        if (!roster.replaceCharacter(updated)) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, updated);
    }

    /**
     * A character's look — its skin, arm width and chest — which its player
     * may change at any time, as Save in Change Look sends it; name, race,
     * sex and faction stay. A lore character keeps the look its story
     * gives it.
     */
    public synchronized CharacterOperationResult updateLook(
            EntityPlayerMP player, long expectedRosterRevision, UUID characterId,
            String skinId, String bodyTypeId, String chestTypeId) {
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
                error = CharacterErrorId.LOOK_UPDATE_NOT_ALLOWED;
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
        CharacterValidationResult reference = CharacterValidator.validateCharacterReference(
                roster, characterId, expectedRosterRevision);
        if (!reference.isValid()) {
            return CharacterOperationResult.failure(reference.getErrorId(), roster);
        }
        CharacterErrorId lore = refuseLoreCharacter(player, characterId,
                CharacterErrorId.LORE_CHARACTER_KEEPS_LOOK);
        if (lore != CharacterErrorId.NONE) {
            return CharacterOperationResult.failure(lore, roster);
        }
        RoleplayCharacter current = roster.getCharacter(characterId);
        CharacterAppearanceValidationResult validation =
                CharacterValidator.validateLook(current, skinId, bodyTypeId,
                        chestTypeId);
        if (!validation.isValid()) {
            return CharacterOperationResult.failure(validation.getErrorId(),
                    roster);
        }
        ValidatedCharacterAppearance look = validation.getAppearance();
        if (look.getSkinId().equals(current.getSkinId())
                && look.getBodyTypeId().equals(current.getBodyTypeId())
                && look.getChestTypeId().equals(current.getChestTypeId())) {
            return CharacterOperationResult.success(false, roster, current);
        }
        RoleplayCharacter updated = RoleplayCharacter.builder(current)
                .skin(look.getSkinId())
                .bodyType(look.getBodyTypeId())
                .chestType(look.getChestTypeId())
                .build();
        if (!roster.replaceCharacter(updated)) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, updated);
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
        CharacterErrorId lore = refuseLoreCharacter(player, characterId,
                CharacterErrorId.LORE_CHARACTER_CANNOT_DELETE);
        if (lore != CharacterErrorId.NONE) {
            return CharacterOperationResult.failure(lore, roster);
        }
        return CharacterDeletionService.getInstance().delete(
                player, data, roster, character);
    }

    /**
     * {@code refusal} when the character is a lore character's record,
     * and {@link CharacterErrorId#NONE} when it is not. An ownership index
     * that cannot be read cannot prove it is not, so it refuses too until
     * an administrator repairs the index.
     */
    private static CharacterErrorId refuseLoreCharacter(EntityPlayerMP player,
                                                        UUID characterId,
                                                        CharacterErrorId refusal) {
        return refuseLoreCharacter(player.worldObj, characterId, refusal);
    }

    private static CharacterErrorId refuseLoreCharacter(World world,
                                                        UUID characterId,
                                                        CharacterErrorId refusal) {
        try {
            LoreCharacterOwnershipWorldData loreOwnership =
                    LoreCharacterOwnershipStorage.get(world);
            if (loreOwnership.isReadOnly()) {
                return CharacterErrorId.LORE_CHARACTER_OWNERSHIP_STORAGE_READ_ONLY;
            }
            return loreOwnership.getRecordByCharacterId(characterId) != null
                    ? refusal : CharacterErrorId.NONE;
        } catch (RuntimeException exception) {
            FMLLog.warning("[%s] Lore character ownership could not be read for character %s: %s",
                    LostTalesMetaData.MOD_ID, characterId, exception.toString());
            return CharacterErrorId.LORE_CHARACTER_OWNERSHIP_STORAGE_READ_ONLY;
        }
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
            if (data.containsCharacter(characterId)) {
                continue;
            }
            return RoleplayCharacter.builder(characterId, ownerId)
                    .slot(creation.getSlotIndex())
                    .name(creation.getName())
                    .race(creation.getRaceId())
                    .gender(creation.getGenderId())
                    .skin(creation.getSkinId())
                    .age(creation.getAge())
                    .startingFaction(creation.getStartingFactionId())
                    .createdAt(System.currentTimeMillis())
                    .startingWaypoint(creation.getStartingWaypointId())
                    .unconventionalSettings(creation.hasUnconventionalSettings())
                    .profile(CharacterProfile.EMPTY.withSection(
                            CharacterProfile.Section.HISTORY,
                            creation.getHistory()))
                    .bodyType(creation.getBodyTypeId())
                    .chestType(creation.getChestTypeId())
                    .build();
        }
        return null;
    }

    private static final class Holder {
        private static final CharacterService INSTANCE =
                new CharacterService(
                        LotrCharacterAdapter.getInstance(),
                        new AllowlistedCharacterCapeEligibilityPolicy());
    }
}
