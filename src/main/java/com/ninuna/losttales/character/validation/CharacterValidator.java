package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.identity.PlayableIdentity;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import net.minecraft.entity.player.EntityPlayerMP;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

/** Centralized authoritative validation for character-management operations. */
public final class CharacterValidator {

    public static final int MIN_NAME_LENGTH = 2;
    public static final int MAX_NAME_LENGTH = 32;
    public static final int MAX_IDENTIFIER_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 256;
    public static final int MIN_AGE = 1;
    public static final int MAX_AGE = 100000;

    private CharacterValidator() {}

    public static CharacterValidationResult validatePlayerCanManage(EntityPlayerMP player) {
        if (player == null || player.worldObj == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_PLAYER);
        }
        if (player.worldObj.isRemote) {
            return CharacterValidationResult.failure(CharacterErrorId.CLIENT_SIDE_REQUEST);
        }
        if (!player.isEntityAlive()) {
            return CharacterValidationResult.failure(CharacterErrorId.PLAYER_DEAD);
        }
        if (player.isPlayerSleeping()) {
            return CharacterValidationResult.failure(CharacterErrorId.PLAYER_SLEEPING);
        }
        return CharacterValidationResult.success();
    }

    public static CharacterValidationResult validateExpectedRevision(
            CharacterRoster roster, long expectedRevision) {
        if (roster == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INTERNAL_ERROR);
        }
        if (expectedRevision != CharacterCreationRequest.REVISION_NOT_CHECKED
                && expectedRevision != roster.getRevision()) {
            return CharacterValidationResult.failure(CharacterErrorId.STALE_ROSTER);
        }
        return CharacterValidationResult.success();
    }


    /**
     * Everything a character looks like and says about itself, asked once
     * for the making of one and the changing of one alike.
     *
     * <p>{@code exceptCharacterId} names the character whose own name is
     * not a clash with itself — the one being changed. Null when a name
     * is being taken for the first time and every name already in the
     * roster belongs to somebody else.</p>
     *
     * <p>{@code keptRaceId} is the race that character already is, which
     * it may go on being even when nobody may newly choose it. Null when
     * a race is being taken rather than kept.</p>
     */
    public static CharacterAppearanceValidationResult validateAppearance(
            CharacterRoster roster, UUID exceptCharacterId,
            String requestedName, String requestedRaceId,
            String requestedGenderId, String requestedSkinId,
            String requestedBodyTypeId, String requestedChestTypeId,
            String requestedDescription, int requestedAge) {
        return validateAppearance(roster, exceptCharacterId, null,
                requestedName, requestedRaceId, requestedGenderId,
                requestedSkinId, requestedBodyTypeId, requestedChestTypeId,
                requestedDescription, requestedAge);
    }

    public static CharacterAppearanceValidationResult validateAppearance(
            CharacterRoster roster, UUID exceptCharacterId, String keptRaceId,
            String requestedName, String requestedRaceId,
            String requestedGenderId, String requestedSkinId,
            String requestedBodyTypeId, String requestedChestTypeId,
            String requestedDescription, int requestedAge) {
        if (roster == null) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INTERNAL_ERROR);
        }

        String normalizedName = normalizeName(requestedName);
        if (normalizedName.length() == 0) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_NAME_EMPTY);
        }
        int nameLength = normalizedName.codePointCount(0, normalizedName.length());
        if (nameLength < MIN_NAME_LENGTH || nameLength > MAX_NAME_LENGTH) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_NAME_LENGTH);
        }
        if (!containsOnlyAllowedNameCharacters(normalizedName)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_NAME_CHARACTERS);
        }
        String normalizedNameKey = normalizeNameKey(normalizedName);
        for (RoleplayCharacter existing : roster.getCharacters()) {
            if (exceptCharacterId != null
                    && exceptCharacterId.equals(existing.getCharacterId())) {
                continue;
            }
            if (normalizedNameKey.equals(normalizeNameKey(existing.getName()))) {
                return CharacterAppearanceValidationResult.failure(
                        CharacterErrorId.DUPLICATE_NAME);
            }
        }

        String raceId = CharacterRaceRegistry.canonicalizeIdentifier(requestedRaceId);
        if (!isValidIdentifierLength(raceId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_RACE);
        }
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_RACE);
        }
        // A race nobody may newly choose may still be kept by a character
        // who already is one; what is refused is taking it.
        if (!race.isSelectable() && !raceId.equals(
                CharacterRaceRegistry.canonicalizeIdentifier(keptRaceId))) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_RACE);
        }

        String genderId = CharacterGenderRegistry.normalizeIdentifier(requestedGenderId);
        if (!isValidIdentifierLength(genderId)
                || !CharacterGenderRegistry.contains(genderId)
                || !race.isGenderAllowed(genderId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_GENDER);
        }

        String skinId = CharacterSkinRegistry.normalizeIdentifier(requestedSkinId);
        if (!isValidIdentifierLength(skinId)
                || !CharacterSkinRegistry.isCompatible(skinId, race.getId(), genderId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_SKIN);
        }

        // Body type is a choice of its own; an empty request takes the
        // default for the sex, anything unknown is refused.
        String bodyTypeId = CharacterBodyTypeRegistry.normalizeIdentifier(
                requestedBodyTypeId);
        if (bodyTypeId.length() == 0) {
            bodyTypeId = CharacterBodyTypeRegistry.defaultFor(genderId);
        } else if (!isValidIdentifierLength(bodyTypeId)
                || !CharacterBodyTypeRegistry.contains(bodyTypeId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_BODY_TYPE);
        }
        String chestTypeId = CharacterChestTypeRegistry.normalizeIdentifier(
                requestedChestTypeId);
        if (chestTypeId.length() == 0) {
            chestTypeId = CharacterChestTypeRegistry.defaultFor(genderId);
        } else if (!isValidIdentifierLength(chestTypeId)
                || !CharacterChestTypeRegistry.contains(chestTypeId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_CHEST_TYPE);
        }

        String description = normalizeDescription(requestedDescription);
        if (!isValidDescription(description)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_DESCRIPTION);
        }

        if (requestedAge < MIN_AGE || requestedAge > MAX_AGE) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_AGE);
        }

        return CharacterAppearanceValidationResult.success(
                new ValidatedCharacterAppearance(normalizedName, normalizedNameKey,
                        race.getId(), genderId, skinId, bodyTypeId, chestTypeId,
                        description, requestedAge));
    }

    public static CharacterCreationValidationResult validateCreation(
            CharacterRoster roster, CharacterCreationRequest request,
            CharacterFactionResolver factionResolver) {
        if (roster == null || request == null || factionResolver == null) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INTERNAL_ERROR);
        }

        CharacterValidationResult revision = validateExpectedRevision(
                roster, request.getExpectedRosterRevision());
        if (!revision.isValid()) {
            return CharacterCreationValidationResult.failure(revision.getErrorId());
        }

        int slotIndex = request.getSlotIndex();
        if (!CharacterRoster.isCreatableSlotIndex(slotIndex)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_SLOT);
        }
        if (slotIndex >= roster.getUnlockedSlotCount()) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.SLOT_HIDDEN);
        }
        if (roster.getCharacterAtSlot(slotIndex) != null) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.SLOT_OCCUPIED);
        }
        if (roster.roleplayCharacterCount() >= CharacterRoster.MAX_SLOTS) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.MAX_CHARACTERS);
        }

        CharacterAppearanceValidationResult appearance = validateAppearance(
                roster, null, request.getName(), request.getRaceId(),
                request.getGenderId(), request.getSkinId(),
                request.getBodyTypeId(), request.getChestTypeId(),
                request.getDescription(), request.getAge());
        if (!appearance.isValid()) {
            return CharacterCreationValidationResult.failure(appearance.getErrorId());
        }
        String normalizedName = appearance.getAppearance().getName();
        String normalizedNameKey = appearance.getAppearance().getNormalizedNameKey();
        String genderId = appearance.getAppearance().getGenderId();
        String skinId = appearance.getAppearance().getSkinId();
        String bodyTypeId = appearance.getAppearance().getBodyTypeId();
        String chestTypeId = appearance.getAppearance().getChestTypeId();
        String description = appearance.getAppearance().getDescription();
        int age = appearance.getAppearance().getAge();
        CharacterRaceDefinition race = CharacterRaceRegistry.get(
                appearance.getAppearance().getRaceId());

        String requestedFactionId = normalizeStableIdentifier(request.getStartingFactionId());
        if (!isValidIdentifierLength(requestedFactionId)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_STARTING_FACTION);
        }
        if (!factionResolver.isAvailable()) {
            return CharacterCreationValidationResult.failure(
                    CharacterErrorId.LOTR_INTEGRATION_UNAVAILABLE);
        }
        CharacterFactionDefinition faction = factionResolver.resolve(requestedFactionId);
        if (faction == null) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_STARTING_FACTION);
        }
        if (!faction.isPlayable()) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.STARTING_FACTION_UNAVAILABLE);
        }
        boolean unconventional = request.hasUnconventionalSettings();
        if (!unconventional && !race.isCompatibleWith(faction)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INCOMPATIBLE_RACE_FACTION);
        }

        String requestedWaypointId = normalizeStableIdentifier(
                request.getStartingWaypointId());
        if (requestedWaypointId.length() > MAX_IDENTIFIER_LENGTH) {
            return CharacterCreationValidationResult.failure(
                    CharacterErrorId.INVALID_STARTING_WAYPOINT);
        }
        String waypointId = factionResolver.resolveStartingWaypointId(
                faction.getId(), requestedWaypointId, unconventional);
        if (waypointId == null || !isValidIdentifierLength(waypointId)) {
            return CharacterCreationValidationResult.failure(
                    CharacterErrorId.STARTING_WAYPOINT_UNAVAILABLE);
        }

        return CharacterCreationValidationResult.success(new ValidatedCharacterCreation(
                slotIndex,
                normalizedName,
                normalizedNameKey,
                race.getId(),
                genderId,
                skinId,
                age,
                faction.getId(),
                waypointId,
                unconventional,
                description,
                bodyTypeId,
                chestTypeId
        ));
    }

    /**
     * A switch target: the roster's own account is always selectable, a
     * character must belong to the roster. Both check the roster revision.
     */
    public static CharacterValidationResult validateSelectionTarget(
            CharacterRoster roster, PlayableIdentity target, long expectedRevision) {
        CharacterValidationResult revision = validateExpectedRevision(roster, expectedRevision);
        if (!revision.isValid()) {
            return revision;
        }
        if (target == null || !roster.getOwnerId().equals(target.getOwnerId())) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_CHARACTER_ID);
        }
        if (target.isAccount()) {
            return CharacterValidationResult.success();
        }
        return validateCharacterReference(roster, target.getCharacterId(), expectedRevision);
    }

    public static CharacterValidationResult validateCharacterReference(
            CharacterRoster roster, UUID characterId, long expectedRevision) {
        CharacterValidationResult revision = validateExpectedRevision(roster, expectedRevision);
        if (!revision.isValid()) {
            return revision;
        }
        if (characterId == null) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_CHARACTER_ID);
        }
        RoleplayCharacter character = roster.getCharacter(characterId);
        if (character == null || !roster.getOwnerId().equals(character.getOwnerId())) {
            return CharacterValidationResult.failure(CharacterErrorId.CHARACTER_NOT_FOUND);
        }
        return CharacterValidationResult.success();
    }

    /** A name as stored: NFC, trimmed, one space between words. */
    public static String normalizeName(String input) {
        return normalizeWhitespace(input);
    }

    public static String normalizeNameKey(String input) {
        return normalizeName(input).toLowerCase(Locale.ROOT);
    }

    /** A biography as stored: the same rule a name follows. */
    public static String normalizeDescription(String input) {
        return normalizeWhitespace(input);
    }

    /**
     * NFC-normalised text with its whitespace folded: none leading or
     * trailing, one plain space between words. Formatting codes are
     * not touched here; the validators refuse them.
     */
    private static String normalizeWhitespace(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        StringBuilder output = new StringBuilder(normalized.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint)) {
                if (output.length() > 0) {
                    pendingSpace = true;
                }
                continue;
            }
            if (pendingSpace) {
                output.append(' ');
                pendingSpace = false;
            }
            output.appendCodePoint(codePoint);
        }
        return output.toString();
    }

    public static boolean isValidDescription(String value) {
        if (value == null || value.codePointCount(0, value.length())
                > MAX_DESCRIPTION_LENGTH) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint == 0x00A7 || Character.isISOControl(codePoint)
                    || !Character.isDefined(codePoint)) {
                return false;
            }
        }
        return true;
    }

    public static String normalizeStableIdentifier(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidIdentifierLength(String value) {
        return value != null && value.length() > 0 && value.length() <= MAX_IDENTIFIER_LENGTH;
    }

    private static boolean containsOnlyAllowedNameCharacters(String value) {
        boolean containsLetter = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isLetter(codePoint)) {
                containsLetter = true;
                continue;
            }
            if (Character.isDigit(codePoint) || codePoint == ' '
                    || codePoint == '-' || codePoint == '\'' || codePoint == 0x2019) {
                continue;
            }
            return false;
        }
        return containsLetter;
    }
}
