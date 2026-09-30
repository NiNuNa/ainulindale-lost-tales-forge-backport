package com.ninuna.losttales.character.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.deletion.CharacterDeletionService;
import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;
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
import com.ninuna.losttales.character.validation.CharacterNames;
import com.ninuna.losttales.character.validation.CharacterValidationResult;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.character.validation.ValidatedCharacterAppearance;
import com.ninuna.losttales.character.validation.ValidatedCharacterCreation;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Every change to a player's characters goes through here: making one,
 * the account character, its look, capes, profile and look edits.
 *
 * Callers must invoke this service on the logical server thread. Public
 * mutation methods are synchronized so each change is made whole;
 * packet handlers schedule their work onto the server thread first.
 */
public final class CharacterService {

    private static final int UUID_GENERATION_ATTEMPTS = 8;
    /**
     * What follows an account's name when that name is a lore character's
     * or one of the chat's voices, which no character may take.
     */
    private static final String RESERVED_NAME_SUFFIX = " the Wanderer";

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

    /**
     * Makes the account character if this world has not made it yet, and
     * plays as it when nothing else is being played.
     *
     * <p>The record carries the account's own UUID as its character id.
     * Party membership, LOTR bounty records, personal map markers and the
     * account's saved player state are all filed under the gameplay id,
     * which for the account is its own UUID and for this character is the
     * same value, so everything the account has in the world is the
     * character's. Nothing is re-keyed.</p>
     *
     * <p>It belongs to no faction, as the bare account belongs to none,
     * and wears the account's own skin and the cape the account wears.</p>
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
            FMLLog.warning("[%s] Not making an account character for %s: "
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
            // The account is the identity being played, and this record
            // is that identity: the same gameplay id and the same saved
            // state, with a name and a face of its own.
            roster.setActiveCharacterId(defaultCharacter.getCharacterId());
        }
        roster.incrementRevision();
        data.saveRoster(roster);
        FMLLog.info("[%s] Made the account character for %s in slot %d",
                LostTalesMetaData.MOD_ID, ownerId,
                Integer.valueOf(CharacterRoster.DEFAULT_SLOT_INDEX));
        return CharacterOperationResult.success(true, roster, defaultCharacter);
    }

    /** The account's identity as a character record, from server-side facts. */
    private RoleplayCharacter buildDefaultCharacter(EntityPlayerMP player,
                                                     CharacterRoster roster) {
        UUID ownerId = player.getUniqueID();
        String name = CharacterValidator.normalizeName(
                SeenAccountNames.accountNameOf(player));
        if (name == null || name.length() == 0) {
            // A record with no name is one the codec would skip, so the
            // identity would vanish on the next load.
            FMLLog.warning("[%s] Cannot name the account character for %s",
                    LostTalesMetaData.MOD_ID, ownerId);
            return null;
        }
        name = accountCharacterName(name);
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

    /**
     * The account character's name from the account's: the account's own,
     * unless a lore character or one of the chat's voices goes by it; then
     * the account's name with a word more, so the lore name stays that
     * figure's alone.
     */
    static String accountCharacterName(String accountName) {
        String name = accountName == null ? "" : accountName;
        return LoreCharacterRegistry.getByName(name) != null
                || CharacterNames.isVoice(name)
                ? name + RESERVED_NAME_SUFFIX : name;
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
        // identity they are playing and selects the character when they
        // choose to, through the ordinary switch and its safeguards.
        roster.incrementRevision();
        data.saveRoster(roster);
        return CharacterOperationResult.success(true, roster, character);
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
     * world has made the account character, the bare account identity and
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
     * Takes the account character's look onto this world's account
     * character, once.
     *
     * <p>A world reads the look on the login where its account character
     * exists and has not been read for yet, and never again: from then on
     * the character is this world's. The reading is spent even when the
     * account offered nothing, so a look saved later is for the next world
     * rather than this one.</p>
     *
     * <p>Everything in the request is checked against this server's own
     * content exactly as a character somebody is making is checked. The
     * character's id, slot, faction and creation time are not the look's
     * to say and are left as they were — which is what keeps the account's
     * items, statistics, alignment and party membership where they are,
     * all of them filed under the id this record already has.</p>
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
        // the roster exists before its account character does — and an
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
                        adoption.getChestTypeId(), adoption.getHistory(),
                        adoption.getAge());
        if (!appearance.isValid()) {
            return refuseTemplate(data, roster, appearance.getErrorId());
        }
        if (nameTakenElsewhere(data, player.getUniqueID(),
                appearance.getAppearance().getName())) {
            return refuseTemplate(data, roster, CharacterErrorId.DUPLICATE_NAME);
        }
        if (isAnotherAccountsName(player, appearance.getAppearance().getName())) {
            return refuseTemplate(data, roster, CharacterErrorId.ACCOUNT_NAME);
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
                .profile(current.getProfile().withSection(
                        CharacterProfile.Section.HISTORY, wanted.getHistory()))
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
        FMLLog.info("[%s] The account character for %s took the account character's look",
                LostTalesMetaData.MOD_ID, player.getUniqueID());
        return CharacterOperationResult.success(true, roster, adopted);
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

    /**
     * A look this server does not accept. The world's one reading is
     * spent all the same: the account character stays as it is, and a look
     * fixed later is for the next world. The refusal goes back with the
     * roster so the client can say why.
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
        // The account's own capes are worn only until the world makes the
        // account character; from then on the character's are.
        if (characterId == null && roster.getDefaultCharacter() != null) {
            return CharacterOperationResult.failure(
                    CharacterErrorId.INVALID_CHARACTER_ID, roster);
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
        // The account character is always there. Deleting it would leave
        // the account with nothing to fall back to, and the state filed
        // under its id — its party membership, its markers, its saved
        // player state — with nothing to belong to.
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
