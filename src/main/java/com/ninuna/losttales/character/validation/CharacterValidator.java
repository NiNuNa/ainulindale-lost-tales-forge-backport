package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterArmsRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.server.CharacterCreationRequest;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.profanity.ChatProfanityCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityFilter;
import com.ninuna.losttales.util.LostTalesIdentifiers;
import net.minecraft.entity.player.EntityPlayerMP;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import com.ninuna.losttales.character.lore.LoreCharacterRegistry;

/**
 * Every check a character is held to, on the server and in the creator
 * alike: the player's state, the roster's revision and slots, the name,
 * race, body type, look, profile, age, faction and starting waypoint. Each
 * refusal is a {@link CharacterErrorId}. The server checks a name against
 * the other rosters and the accounts it has seen as well
 * ({@code CharacterService}).
 */
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
        if (expectedRevision != roster.getRevision()) {
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
     */
    public static CharacterAppearanceValidationResult validateAppearance(
            CharacterRoster roster, UUID exceptCharacterId,
            String requestedName, String requestedRaceId,
            String requestedBodyTypeId, String requestedSkinId,
            String requestedArmsId, String requestedChestTypeId,
            String requestedHistory, int requestedAge) {
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
        if (!CharacterNames.inOneScript(normalizedName)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_NAME_SCRIPT);
        }
        // A word the chat would filter is no name: the list in force on
        // this side, the bundled one with the server's words over it.
        if (ChatProfanityFilter.hasListedWord(normalizedName,
                ChatProfanityCatalog.effective())) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_NAME_PROFANE);
        }
        // A lore character's name is theirs: whoever claims them plays by
        // it, and nobody else takes it. Nor does anybody speak as one of
        // the chat's own voices.
        if (LoreCharacterRegistry.getByName(normalizedName) != null
                || CharacterNames.isVoice(normalizedName)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.NAME_RESERVED);
        }
        // The server asks every other roster as well (CharacterService).
        for (RoleplayCharacter existing : roster.getCharacters()) {
            if (exceptCharacterId != null
                    && exceptCharacterId.equals(existing.getCharacterId())) {
                continue;
            }
            if (CharacterNames.same(normalizedName, existing.getName())) {
                return CharacterAppearanceValidationResult.failure(
                        CharacterErrorId.DUPLICATE_NAME);
            }
        }

        String raceId = LostTalesIdentifiers.normalize(requestedRaceId);
        if (!isValidIdentifierLength(raceId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_RACE);
        }
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_RACE);
        }
        String bodyTypeId = LostTalesIdentifiers.normalize(requestedBodyTypeId);
        if (!isValidIdentifierLength(bodyTypeId)
                || !CharacterBodyTypeRegistry.contains(bodyTypeId)
                || !race.isBodyTypeAllowed(bodyTypeId)) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.INVALID_BODY_TYPE);
        }

        Look look = look(race.getId(), bodyTypeId, requestedSkinId,
                requestedArmsId, requestedChestTypeId);
        if (look.errorId != CharacterErrorId.NONE) {
            return CharacterAppearanceValidationResult.failure(look.errorId);
        }
        String skinId = look.skinId;
        String armsId = look.armsId;
        String chestTypeId = look.chestTypeId;

        // Whoever makes a character writes its History; the rest of the
        // profile is written afterwards.
        String history = normalizeSection(requestedHistory);
        CharacterValidationResult profile = validateProfile(
                CharacterProfile.EMPTY.withSection(
                        CharacterProfile.Section.HISTORY, history),
                requestedAge);
        if (!profile.isValid()) {
            return CharacterAppearanceValidationResult.failure(
                    profile.getErrorId());
        }

        return CharacterAppearanceValidationResult.success(
                new ValidatedCharacterAppearance(normalizedName,
                        race.getId(), bodyTypeId, skinId, armsId, chestTypeId,
                        history, requestedAge));
    }

    /**
     * A new look for a character that stays who it is: its skin, arms
     * and chest, held to the same checks as the creator's — a skin the
     * character's race and body type may wear, the account skin among them where
     * the race allows it, and arms and a chest the registries know.
     * Empty arms or an empty chest take the body type's own. Name, race, body type,
     * History and age are the character's as they stand.
     */
    public static CharacterAppearanceValidationResult validateLook(
            RoleplayCharacter character, String requestedSkinId,
            String requestedArmsId, String requestedChestTypeId) {
        if (character == null) {
            return CharacterAppearanceValidationResult.failure(
                    CharacterErrorId.CHARACTER_NOT_FOUND);
        }
        Look look = look(character.getRaceId(), character.getBodyTypeId(),
                requestedSkinId, requestedArmsId, requestedChestTypeId);
        if (look.errorId != CharacterErrorId.NONE) {
            return CharacterAppearanceValidationResult.failure(look.errorId);
        }
        return CharacterAppearanceValidationResult.success(
                new ValidatedCharacterAppearance(character.getName(),
                        character.getRaceId(), character.getBodyTypeId(),
                        look.skinId, look.armsId, look.chestTypeId,
                        character.getProfile().section(
                                CharacterProfile.Section.HISTORY),
                        character.getAge()));
    }

    /** A skin, arms and a chest as checked, or why they are refused. */
    private static final class Look {
        final CharacterErrorId errorId;
        final String skinId;
        final String armsId;
        final String chestTypeId;

        Look(CharacterErrorId errorId, String skinId, String armsId,
             String chestTypeId) {
            this.errorId = errorId;
            this.skinId = skinId;
            this.armsId = armsId;
            this.chestTypeId = chestTypeId;
        }

        static Look refused(CharacterErrorId errorId) {
            return new Look(errorId, "", "", "");
        }
    }

    /**
     * The look a race and body type may wear: the skin one they are compatible
     * with, the arms and the chest each a registry's or, empty, the
     * body type's own.
     */
    private static Look look(String raceId, String bodyTypeId,
                             String requestedSkinId,
                             String requestedArmsId,
                             String requestedChestTypeId) {
        String skinId = LostTalesIdentifiers.normalize(requestedSkinId);
        if (!isValidIdentifierLength(skinId)
                || !CharacterSkinRegistry.isCompatible(skinId, raceId, bodyTypeId)) {
            return Look.refused(CharacterErrorId.INVALID_SKIN);
        }
        String armsId = LostTalesIdentifiers.normalize(
                requestedArmsId);
        if (armsId.length() == 0) {
            armsId = CharacterArmsRegistry.defaultFor(bodyTypeId);
        } else if (!isValidIdentifierLength(armsId)
                || !CharacterArmsRegistry.contains(armsId)) {
            return Look.refused(CharacterErrorId.INVALID_ARMS);
        }
        String chestTypeId = LostTalesIdentifiers.normalize(
                requestedChestTypeId);
        if (chestTypeId.length() == 0) {
            chestTypeId = CharacterChestTypeRegistry.defaultFor(bodyTypeId);
        } else if (!isValidIdentifierLength(chestTypeId)
                || !CharacterChestTypeRegistry.contains(chestTypeId)) {
            return Look.refused(CharacterErrorId.INVALID_CHEST_TYPE);
        }
        return new Look(CharacterErrorId.NONE, skinId, armsId, chestTypeId);
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
        if (roster.characterCount() >= CharacterRoster.MAX_SLOTS) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.MAX_CHARACTERS);
        }

        CharacterAppearanceValidationResult appearance = validateAppearance(
                roster, null, request.getName(), request.getRaceId(),
                request.getBodyTypeId(), request.getSkinId(),
                request.getArmsId(), request.getChestTypeId(),
                request.getHistory(), request.getAge());
        if (!appearance.isValid()) {
            return CharacterCreationValidationResult.failure(appearance.getErrorId());
        }
        String normalizedName = appearance.getAppearance().getName();
        String bodyTypeId = appearance.getAppearance().getBodyTypeId();
        String skinId = appearance.getAppearance().getSkinId();
        String armsId = appearance.getAppearance().getArmsId();
        String chestTypeId = appearance.getAppearance().getChestTypeId();
        String history = appearance.getAppearance().getHistory();
        int age = appearance.getAppearance().getAge();
        CharacterRaceDefinition race = CharacterRaceRegistry.get(
                appearance.getAppearance().getRaceId());
        // A race gone from the server stands in for its characters only.
        if (race.isStandIn()) {
            return CharacterCreationValidationResult.failure(CharacterErrorId.INVALID_RACE);
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
                race.getId(),
                bodyTypeId,
                skinId,
                age,
                faction.getId(),
                waypointId,
                unconventional,
                history,
                armsId,
                chestTypeId
        ));
    }

    /**
     * What a character says about itself, as stored (see
     * {@link #normalizeProfile}), and its age. Creation and a later edit
     * hold both to these bounds: each About text, fact and glance to its
     * length and to printable characters, a glance to one of the chat's
     * emoji and a title, and every word of them to the words a name may
     * not hold.
     */
    public static CharacterValidationResult validateProfile(
            CharacterProfile profile, int age) {
        if (profile == null) {
            return CharacterValidationResult.failure(
                    CharacterErrorId.INVALID_PROFILE_TEXT);
        }
        List<String> words = new ArrayList<String>();
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            String text = profile.section(section);
            if (!isValidSection(section, text)) {
                return CharacterValidationResult.failure(
                        CharacterErrorId.INVALID_PROFILE_TEXT);
            }
            words.add(text);
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            String value = profile.fact(fact);
            if (!isValidLine(value, CharacterProfile.MAX_FACT_LENGTH)) {
                return CharacterValidationResult.failure(
                        CharacterErrorId.INVALID_PROFILE_TEXT);
            }
            words.add(value);
        }
        if (profile.glances().size() > CharacterProfile.MAX_GLANCES) {
            return CharacterValidationResult.failure(
                    CharacterErrorId.INVALID_GLANCE);
        }
        for (CharacterProfile.Glance glance : profile.glances()) {
            if (!isValidGlance(glance)) {
                return CharacterValidationResult.failure(
                        CharacterErrorId.INVALID_GLANCE);
            }
            words.add(glance.getTitle());
            words.add(glance.getLine());
        }
        for (String text : words) {
            if (ChatProfanityFilter.hasListedWord(text,
                    ChatProfanityCatalog.effective())) {
                return CharacterValidationResult.failure(
                        CharacterErrorId.INVALID_PROFILE_TEXT_PROFANE);
            }
        }
        if (age < MIN_AGE || age > MAX_AGE) {
            return CharacterValidationResult.failure(CharacterErrorId.INVALID_AGE);
        }
        return CharacterValidationResult.success();
    }

    /** A glance as stored: its emoji by its name alone, its title and line each on one line. */
    public static CharacterProfile.Glance normalizeGlance(
            CharacterProfile.Glance glance) {
        return new CharacterProfile.Glance(glance.getEmoji().trim(),
                normalizeLine(glance.getTitle()),
                normalizeLine(glance.getLine()));
    }

    /** A glance as stored may be shown: a chat emoji by its name, a title, and both texts within bounds. */
    public static boolean isValidGlance(CharacterProfile.Glance glance) {
        return glance != null && ChatEmoji.fromName(glance.getEmoji()) != null
                && glance.getTitle().length() > 0
                && isValidLine(glance.getTitle(),
                        CharacterProfile.MAX_GLANCE_TITLE_LENGTH)
                && isValidLine(glance.getLine(),
                        CharacterProfile.MAX_GLANCE_LINE_LENGTH);
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

    /**
     * Whether a name has a character name's shape: 2-32 characters of the
     * allowed ones once tidied. What it may not be (a lore figure's, a
     * taken one, a listed word) only the full check says.
     */
    public static boolean isWellFormedName(String input) {
        String name = normalizeName(input);
        int length = name.codePointCount(0, name.length());
        return length >= MIN_NAME_LENGTH && length <= MAX_NAME_LENGTH
                && containsOnlyAllowedNameCharacters(name)
                && CharacterNames.inOneScript(name);
    }

    /** A name as stored: NFC, trimmed, one space between words. */
    public static String normalizeName(String input) {
        return normalizeWhitespace(input);
    }

    /**
     * A profile as stored: each About text as {@link #normalizeSection}
     * keeps it, each fact and each glance's title and line on one line as
     * a name is, and each glance's emoji by its name alone.
     */
    public static CharacterProfile normalizeProfile(CharacterProfile profile) {
        if (profile == null) {
            return CharacterProfile.EMPTY;
        }
        CharacterProfile normalized = CharacterProfile.EMPTY;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            normalized = normalized.withSection(section,
                    normalizeSection(profile.section(section)));
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            normalized = normalized.withFact(fact,
                    normalizeLine(profile.fact(fact)));
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>();
        for (CharacterProfile.Glance glance : profile.glances()) {
            glances.add(normalizeGlance(glance));
        }
        return normalized.withGlances(glances);
    }

    /**
     * A fact, or a glance's title or line, as stored: the rule a name
     * follows, its invisible marks dropped ({@link #isInvisibleMark}).
     */
    public static String normalizeLine(String input) {
        return normalizeWhitespace(withoutInvisibleMarks(input));
    }

    /**
     * An About text as stored: NFC, its invisible marks dropped
     * ({@link #isInvisibleMark}), each paragraph's whitespace folded as a
     * name's is, one empty line at most between paragraphs, and none
     * before the first or after the last.
     */
    public static String normalizeSection(String input) {
        if (input == null) {
            return "";
        }
        String text = withoutInvisibleMarks(input)
                .replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder output = new StringBuilder(text.length());
        boolean gap = false;
        for (String paragraph : text.split("\n", -1)) {
            String folded = normalizeWhitespace(paragraph);
            if (folded.length() == 0) {
                gap = output.length() > 0;
                continue;
            }
            if (output.length() > 0) {
                output.append(gap ? "\n\n" : "\n");
            }
            gap = false;
            output.append(folded);
        }
        return output.toString();
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

    /** An About text within its section's length, printable, its only breaks the ones between paragraphs. */
    public static boolean isValidSection(CharacterProfile.Section section,
                                         String value) {
        return isPrintable(value, section.getMaxLength(), true);
    }

    /** One line of printable text of at most {@code maxLength} characters. */
    public static boolean isValidLine(String value, int maxLength) {
        return isPrintable(value, maxLength, false);
    }

    private static boolean isPrintable(String value, int maxLength,
                                       boolean paragraphs) {
        if (value == null || value.codePointCount(0, value.length())
                > maxLength) {
            return false;
        }
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (paragraphs && codePoint == '\n') {
                continue;
            }
            if (codePoint == 0x00A7 || Character.isISOControl(codePoint)
                    || !Character.isDefined(codePoint)
                    || isInvisibleMark(codePoint)) {
                return false;
            }
        }
        return true;
    }

    /**
     * A mark profile text drops: one that turns the text after it around,
     * as the chat drops them ({@link ChatMessageValidator#isDirectionMark}),
     * a left-to-right or right-to-left mark (U+200E, U+200F, U+061C), or a
     * character of no width (U+200B-U+200D, U+2060-U+2064, U+FEFF). Each
     * could make a profile read as something other than what was typed, or
     * hide a word from the filter.
     */
    static boolean isInvisibleMark(int codePoint) {
        return codePoint <= Character.MAX_VALUE
                        && ChatMessageValidator.isDirectionMark((char)codePoint)
                || codePoint >= 0x200B && codePoint <= 0x200F
                || codePoint >= 0x2060 && codePoint <= 0x2064
                || codePoint == 0xFEFF || codePoint == 0x061C;
    }

    /** {@code input} without its invisible marks ({@link #isInvisibleMark}). */
    private static String withoutInvisibleMarks(String input) {
        if (input == null) {
            return "";
        }
        StringBuilder kept = new StringBuilder(input.length());
        for (int offset = 0; offset < input.length();) {
            int codePoint = input.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isInvisibleMark(codePoint)) {
                kept.appendCodePoint(codePoint);
            }
        }
        return kept.toString();
    }

    public static String normalizeStableIdentifier(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isValidIdentifierLength(String value) {
        return value != null && value.length() > 0 && value.length() <= MAX_IDENTIFIER_LENGTH;
    }

    /**
     * Whether every character of a name is one the game draws: a letter, a
     * digit, a space, {@code -}, {@code '} or {@code ’}, with at least one
     * letter. The game's font has no letter past U+FFFF and draws such a
     * letter as nothing, and the Hangul fillers are blank, so a name holding
     * one would read as another name or as none.
     */
    private static boolean containsOnlyAllowedNameCharacters(String value) {
        boolean containsLetter = false;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint > Character.MAX_VALUE || isBlankLetter(codePoint)) {
                return false;
            }
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

    /** The Hangul fillers: letters to Unicode, drawn blank. */
    private static boolean isBlankLetter(int codePoint) {
        return codePoint == 0x115F || codePoint == 0x1160
                || codePoint == 0x3164 || codePoint == 0xFFA0;
    }
}
