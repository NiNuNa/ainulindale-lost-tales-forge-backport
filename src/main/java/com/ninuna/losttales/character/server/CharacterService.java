package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipStorage;
import com.ninuna.losttales.character.lore.ownership.LoreCharacterOwnershipWorldData;
import com.ninuna.losttales.character.model.CharacterKind;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.switching.CharacterSwitchCoordinator;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.character.validation.CharacterAppearanceValidationResult;
import com.ninuna.losttales.character.validation.CharacterCreationValidationResult;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.character.validation.ValidatedCharacterAppearance;
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

    /**
     * Makes the account's own identity if this world has not made it yet,
     * and plays as it when nothing else is being played.
     *
     * <p>The record carries the account's own UUID as its character id.
     * That is what keeps a world that already existed working: party
     * membership, LOTR bounty records, personal map markers and the
     * account's saved player state are all filed under the gameplay id,
     * which for the account was its own UUID and for this character is
     * the same value. Nothing is re-keyed, and nothing has to be.</p>
     *
     * <p>It belongs to no faction, exactly as the account did before it
     * was a character, and wears the account's own skin and the cape the
     * account was already wearing.</p>
     */
    public synchronized CharacterOperationResult ensureDefaultCharacter(
            EntityPlayerMP player) {
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
        CharacterRoster roster = data.getOrCreateRoster(player.getUniqueID());
        if (roster.getDefaultCharacter() != null) {
            return CharacterOperationResult.success(false, roster,
                    roster.getActiveCharacter());
        }
        UUID ownerId = player.getUniqueID();
        if (data.containsCharacter(ownerId)) {
            // Some other roster already holds a character under this id.
            // Minting a second would make the id ambiguous and cost both
            // of them their party membership and their markers.
            FMLLog.warning("[%s] Not making a default character for %s: "
                            + "a character already exists under that id",
                    LostTalesMetaData.MOD_ID, ownerId);
            return CharacterOperationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR, roster);
        }
        RoleplayCharacter defaultCharacter = buildDefaultCharacter(player, roster);
        if (defaultCharacter == null || !roster.addCharacter(defaultCharacter)) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR, roster);
        }
        if (roster.getActiveCharacterId() == null) {
            // The account was the identity being played, and this record
            // is that identity: the same gameplay id, the same saved
            // state, now with a name and a face of its own.
            roster.setActiveCharacterId(defaultCharacter.getCharacterId());
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        FMLLog.info("[%s] Made the default character for %s in slot %d",
                LostTalesMetaData.MOD_ID, ownerId,
                Integer.valueOf(CharacterRoster.DEFAULT_SLOT_INDEX));
        return CharacterOperationResult.success(true, roster, defaultCharacter);
    }

    /** The account's identity as a character record, from server-side facts. */
    private RoleplayCharacter buildDefaultCharacter(EntityPlayerMP player,
                                                     CharacterRoster roster) {
        UUID ownerId = player.getUniqueID();
        String accountName = player.getGameProfile() == null
                || player.getGameProfile().getName() == null
                || player.getGameProfile().getName().trim().length() == 0
                ? player.getCommandSenderName()
                : player.getGameProfile().getName().trim();
        String name = CharacterValidator.normalizeName(accountName);
        if (name == null || name.length() == 0) {
            // A record with no name is one the codec would skip, so the
            // identity would vanish on the next load.
            FMLLog.warning("[%s] Cannot name the default character for %s",
                    LostTalesMetaData.MOD_ID, ownerId);
            return null;
        }
        String raceId = CharacterRaceRegistry.HUMAN;
        String genderId = CharacterRaceRegistry.normalizeGenderForRace(
                raceId, CharacterGenderRegistry.MALE);
        String skinId = CharacterSkinRegistry.isCompatible(
                CharacterSkinRegistry.ACCOUNT_SKIN_ID, raceId, genderId)
                ? CharacterSkinRegistry.ACCOUNT_SKIN_ID
                : CharacterSkinRegistry.getDefaultSkinId(raceId, genderId, ownerId);
        return RoleplayCharacter.builder(ownerId, ownerId)
                .kind(CharacterKind.DEFAULT)
                .slot(CharacterRoster.DEFAULT_SLOT_INDEX)
                .name(name)
                .race(raceId)
                .gender(genderId)
                .skin(skinId)
                // The account's own arm width, so the identity a player
                // already had keeps the model their skin is painted for.
                // Without it the record takes the gender's default, which
                // for the male gender chosen above is always the wide arm,
                // and a slim skin would be sampled a texel too far.
                .bodyType(CharacterAppearanceSyncManager.accountBodyType(player))
                .age(CharacterValidator.MIN_AGE)
                // The account's own identity chose no side: it is Unaligned.
                .startingFaction(LotrCharacterAdapter.UNALIGNED_FACTION_ID)
                .createdAt(System.currentTimeMillis())
                .minecraftCapeVisible(roster.isAccountMinecraftCapeVisible())
                .cosmeticCape(roster.getAccountCosmeticCapeId())
                .build();
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
        // identity they are playing and selects the character when they
        // choose to, through the ordinary switch and its safeguards.
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

    /** Plays as the given identity: one of the roster's characters, or the account itself. */
    public CharacterOperationResult selectIdentity(
            EntityPlayerMP player, int requestId,
            long expectedRosterRevision, PlayableIdentity target) {
        return CharacterSwitchCoordinator.getInstance().selectIdentity(
                player, requestId, expectedRosterRevision,
                asDefaultCharacter(player, target));
    }

    /**
     * The account asked for as the character that is the account. Once a
     * world has made the default character, the bare account identity and
     * that character are the same person — the same gameplay id, the same
     * saved state — so a request for one is answered with the other and
     * the roster never shows a player two rows for one identity.
     *
     * <p>Anything else is passed through untouched, including the account
     * on a world that has not made the character yet.</p>
     */
    private PlayableIdentity asDefaultCharacter(EntityPlayerMP player,
                                                 PlayableIdentity target) {
        if (target == null || !target.isAccount() || player == null
                || player.worldObj == null || player.worldObj.isRemote) {
            return target;
        }
        try {
            CharacterWorldData data = getData(player);
            CharacterRoster roster = data == null
                    ? null : data.getRoster(target.getOwnerId());
            RoleplayCharacter account = roster == null
                    ? null : roster.getDefaultCharacter();
            return account == null ? target
                    : PlayableIdentity.character(target.getOwnerId(),
                            account.getCharacterId());
        } catch (RuntimeException unreadable) {
            // A store that cannot be read names no character, and the
            // account identity it already had still stands.
            return target;
        }
    }

    /**
     * Takes the account's template onto this world's default character,
     * once.
     *
     * <p>A world reads a template on the login where its default
     * character exists and has not been read for yet, and never again:
     * from then on the character is this world's. The reading is spent
     * even when the account offered nothing, so a template written later
     * is for the next world rather than this one.</p>
     *
     * <p>Everything in the request is checked against this server's own
     * content exactly as a character somebody is making is checked. The
     * character's id, slot, faction, level, progression and creation time
     * are not the template's to say and are left as they were — which is
     * what keeps the account's items, statistics, alignment and party
     * membership where they are, all of them filed under the id this
     * record already has.</p>
     */
    public synchronized CharacterOperationResult adoptTemplate(
            EntityPlayerMP player, CharacterTemplateAdoption adoption) {
        CharacterValidationResult playerValidation = validateServerPlayer(player);
        if (!playerValidation.isValid()) {
            return CharacterOperationResult.failure(playerValidation.getErrorId(), null);
        }
        if (adoption == null) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
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
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, null);
        }
        // The revision the offer was made against is not checked: the
        // offer names no character and no slot, so there is nothing a
        // revision protects, and the roster is re-read and re-checked
        // here anyway. The login sequence itself moves the revision —
        // the roster exists before its default character does — and an
        // offer made against the earlier snapshot must still be taken.
        RoleplayCharacter current = roster.getDefaultCharacter();
        if (current == null || roster.isTemplateTaken()) {
            // Either there is nothing to take it onto yet, or this world
            // has had its one reading. Neither is the player's mistake.
            return CharacterOperationResult.success(false, roster, current);
        }
        if (!adoption.isOffered()) {
            roster.markTemplateTaken();
            roster.incrementRevision();
            data.saveRoster(roster);
            return CharacterOperationResult.success(true, roster, current);
        }
        CharacterAppearanceValidationResult appearance =
                CharacterValidator.validateAppearance(roster,
                        current.getCharacterId(), current.getRaceId(),
                        adoption.getName(),
                        adoption.getRaceId(), adoption.getGenderId(),
                        adoption.getSkinId(), adoption.getBodyTypeId(),
                        adoption.getChestTypeId(), adoption.getDescription(),
                        adoption.getAge());
        if (!appearance.isValid()) {
            return refuseTemplate(data, roster, appearance.getErrorId());
        }
        CharacterValidationResult cape = this.capeEligibilityPolicy.validate(
                player, current, adoption.getCosmeticCapeId());
        if (!cape.isValid()) {
            return refuseTemplate(data, roster, cape.getErrorId());
        }
        ValidatedCharacterAppearance wanted = appearance.getAppearance();
        RoleplayCharacter adopted = RoleplayCharacter.builder(current)
                .name(wanted.getName())
                .race(wanted.getRaceId())
                .gender(wanted.getGenderId())
                .skin(wanted.getSkinId())
                .bodyType(wanted.getBodyTypeId())
                .chestType(wanted.getChestTypeId())
                .description(wanted.getDescription())
                .age(wanted.getAge())
                .minecraftCapeVisible(adoption.isMinecraftCapeVisible())
                .cosmeticCape(adoption.getCosmeticCapeId())
                .build();
        if (!roster.replaceCharacter(adopted)) {
            return CharacterOperationResult.failure(CharacterErrorId.INTERNAL_ERROR, roster);
        }
        roster.markTemplateTaken();
        roster.incrementRevision();
        data.saveRoster(roster);
        FMLLog.info("[%s] The default character for %s took the account template",
                LostTalesMetaData.MOD_ID, player.getUniqueID());
        return CharacterOperationResult.success(true, roster, adopted);
    }

    /**
     * A template this server does not accept. The world's one reading is
     * spent all the same: the default character stays as it is, to be
     * edited in the world, and a template fixed later is for the next
     * world. The refusal goes back with the roster so the client can say
     * why.
     */
    private static CharacterOperationResult refuseTemplate(
            CharacterWorldData data, CharacterRoster roster, CharacterErrorId errorId) {
        roster.markTemplateTaken();
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.failure(errorId, roster);
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
        // A null character id names the account: the roster keeps its cape.
        CharacterValidationResult referenceValidation = characterId == null
                ? CharacterValidator.validateExpectedRevision(roster, expectedRosterRevision)
                : CharacterValidator.validateCharacterReference(
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

        boolean changed = character == null
                ? roster.setAccountCapeSettings(showMinecraftCape, cosmeticCapeId)
                : character.setCapeSettings(showMinecraftCape, cosmeticCapeId);
        if (changed) {
            roster.incrementRevision();
            data.saveRoster(roster);
        }
        return CharacterOperationResult.success(changed, roster, character);
    }

    /**
     * A character's description and age, which its player may change at
     * any time; everything else creation settled stays as it is. A lore
     * character's are refused: its record passes from player to player,
     * and what one player wrote would be read as the next one's.
     */
    public synchronized CharacterOperationResult updateProfile(
            EntityPlayerMP player, long expectedRosterRevision, UUID characterId,
            String requestedDescription, int requestedAge) {
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
        String description = CharacterValidator.normalizeDescription(
                requestedDescription);
        CharacterValidationResult profile = CharacterValidator.validateProfile(
                description, requestedAge);
        if (!profile.isValid()) {
            return CharacterOperationResult.failure(profile.getErrorId(), roster);
        }

        RoleplayCharacter current = roster.getCharacter(characterId);
        if (current.getAge() == requestedAge
                && current.getDescription().equals(description)) {
            return CharacterOperationResult.success(false, roster, current);
        }
        RoleplayCharacter updated = RoleplayCharacter.builder(current)
                .description(description)
                .age(requestedAge)
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
        // The account's own identity is always there. Deleting it would
        // leave the account with nothing to fall back to, and the state
        // filed under its id — its party membership, its markers, its
        // saved player state — with nothing to belong to.
        if (character != null && character.isDefault()) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.DELETE_DEFAULT_CHARACTER, roster);
        }
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
        try {
            LoreCharacterOwnershipWorldData loreOwnership =
                    LoreCharacterOwnershipStorage.get(player.worldObj);
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
                    .description(creation.getDescription())
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
