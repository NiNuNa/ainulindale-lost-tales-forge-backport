package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
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
        if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_SLOT);
        }
        if (slotIndex >= roster.getUnlockedSlotCount()) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.SLOT_HIDDEN);
        }
        if (roster.getCharacterAtSlot(slotIndex) != null) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.SLOT_OCCUPIED);
        }
        if (roster.getCharacterCount() >= CharacterRoster.MAX_SLOTS) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.MAX_CHARACTERS);
        }

        String normalizedName = normalizeName(request.getName());
        if (normalizedName.length() == 0) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_NAME_EMPTY);
        }
        int nameLength = normalizedName.codePointCount(0, normalizedName.length());
        if (nameLength < MIN_NAME_LENGTH || nameLength > MAX_NAME_LENGTH) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_NAME_LENGTH);
        }
        if (!containsOnlyAllowedNameCharacters(normalizedName)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_NAME_CHARACTERS);
        }
        String normalizedNameKey = normalizeNameKey(normalizedName);
        for (RoleplayCharacter existing : roster.getCharacters()) {
            if (normalizedNameKey.equals(normalizeNameKey(existing.getName()))) {
                return CharacterCreationValidationResult.failure(CharacterErrorId.DUPLICATE_NAME);
            }
        }

        String raceId = CharacterRaceRegistry.normalizeIdentifier(request.getRaceId());
        if (!isValidIdentifierLength(raceId)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_RACE);
        }
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_RACE);
        }

        String genderId = CharacterGenderRegistry.normalizeIdentifier(request.getGenderId());
        if (!isValidIdentifierLength(genderId) || !CharacterGenderRegistry.contains(genderId)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_GENDER);
        }

        int age = request.getAge();
        if (age < MIN_AGE || age > MAX_AGE) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_AGE);
        }

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
        if (!race.isCompatibleWith(faction)) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INCOMPATIBLE_RACE_FACTION);
        }

        return CharacterCreationValidationResult.success(new ValidatedCharacterCreation(
                slotIndex,
                normalizedName,
                normalizedNameKey,
                race.getId(),
                genderId,
                age,
                faction.getId()
        ));
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

    public static String normalizeName(String input) {
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

    public static String normalizeNameKey(String input) {
        return normalizeName(input).toLowerCase(Locale.ROOT);
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
