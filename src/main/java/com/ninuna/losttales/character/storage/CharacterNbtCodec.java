package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBody;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesIdentifiers;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned NBT serialization for all roleplaying character data. A store,
 * a roster and a character are read only at the version this build writes,
 * and only as it writes them: a roster or a character of any other shape
 * is kept whole in the quarantine and left out, never filled in or put
 * right; data of another version keeps the whole store read-only.
 */
public final class CharacterNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 2;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ROSTERS = "Rosters";
    private static final String TAG_ENTRY_TYPE = "EntryType";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_ROSTER_INDEX = "RosterIndex";
    private static final String TAG_CHARACTER_INDEX = "CharacterIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";
    private static final String TAG_CHARACTERS = "Characters";

    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_CHARACTER_UUID = "CharacterUUID";
    private static final String TAG_ACTIVE_CHARACTER_UUID = "ActiveCharacterUUID";

    private static final String TAG_SLOT_INDEX = "SlotIndex";
    private static final String TAG_UNLOCKED_SLOT_COUNT = "UnlockedSlotCount";
    private static final String TAG_REVISION = "Revision";
    private static final String TAG_NAME = "Name";
    private static final String TAG_RACE_ID = "RaceId";
    private static final String TAG_GENDER_ID = "GenderId";
    private static final String TAG_SKIN_ID = "SkinId";
    private static final String TAG_BODY_TYPE_ID = "BodyTypeId";
    private static final String TAG_CHEST_TYPE_ID = "ChestTypeId";
    private static final String TAG_PROFILE = "Profile";
    private static final String TAG_PROFILE_APPEARANCE = "Appearance";
    private static final String TAG_PROFILE_PERSONALITY = "Personality";
    private static final String TAG_PROFILE_HISTORY = "History";
    private static final String TAG_PROFILE_FACTS = "Facts";
    private static final String TAG_FACT_HEIGHT = "Height";
    private static final String TAG_FACT_BUILD = "Build";
    private static final String TAG_FACT_EYES = "Eyes";
    private static final String TAG_FACT_HAIR = "Hair";
    private static final String TAG_FACT_BIRTHPLACE = "Birthplace";
    private static final String TAG_FACT_HOME = "Home";
    private static final String TAG_PROFILE_GLANCES = "Glances";
    private static final String TAG_GLANCE_EMOJI = "Emoji";
    private static final String TAG_GLANCE_TITLE = "Title";
    private static final String TAG_GLANCE_LINE = "Line";
    private static final String TAG_SHOW_MINECRAFT_CAPE = "ShowMinecraftCape";
    private static final String TAG_COSMETIC_CAPE = "CosmeticCape";
    private static final String TAG_BODY = "Body";
    private static final String TAG_AGE = "Age";
    private static final String TAG_STARTING_FACTION_ID = "StartingFactionId";
    private static final String TAG_STARTING_WAYPOINT_ID = "StartingWaypointId";
    private static final String TAG_UNCONVENTIONAL_SETTINGS = "UnconventionalSettings";
    private static final String TAG_PLEDGED_FACTION_ID = "PledgedFactionId";
    private static final String TAG_CREATION_TIMESTAMP = "CreationTimestamp";
    private static final String TAG_FACTION_SINCE = "FactionSince";
    private static final String TAG_LOTR_TITLE = "LotrTitle";

    private static final int MAX_STABLE_IDENTIFIER_LENGTH = 64;

    private CharacterNbtCodec() {}

    /**
     * Encodes one detached character record for recovery-oriented stores.
     * The returned tag is independent from the live roster object.
     */
    public static NBTTagCompound writeCharacterRecord(RoleplayCharacter character) {
        if (character == null) {
            throw new IllegalArgumentException("character must not be null");
        }
        return writeCharacter(character);
    }

    /**
     * Decodes and validates a detached character record for the expected owner.
     * Malformed or unsupported records fail closed instead of producing a
     * partially repaired recovery entry.
     */
    public static RoleplayCharacter readCharacterRecord(
            NBTTagCompound source, UUID expectedOwnerId) {
        if (source == null || expectedOwnerId == null) {
            throw new IllegalArgumentException(
                    "character data and expected owner must not be null");
        }
        CharacterReadResult result = readCharacter(
                source, expectedOwnerId, -1);
        if (result.unsupportedVersion >= 0) {
            throw new IllegalArgumentException(
                    "unsupported character data version "
                            + result.unsupportedVersion);
        }
        if (result.character == null) {
            throw new IllegalArgumentException(
                    "malformed detached character record: "
                            + result.failureReason);
        }
        return result.character;
    }

    public static void write(NBTTagCompound output, Collection<CharacterRoster> rosters,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);

        NBTTagList rosterList = new NBTTagList();
        ArrayList<CharacterRoster> sortedRosters = new ArrayList<CharacterRoster>();
        if (rosters != null) {
            sortedRosters.addAll(rosters);
        }
        Collections.sort(sortedRosters, new Comparator<CharacterRoster>() {
            @Override
            public int compare(CharacterRoster left, CharacterRoster right) {
                return left.getOwnerId().toString().compareTo(right.getOwnerId().toString());
            }
        });

        for (CharacterRoster roster : sortedRosters) {
            if (roster != null) {
                rosterList.appendTag(writeRoster(roster));
            }
        }
        output.setTag(TAG_ROSTERS, rosterList);
        NbtQuarantine.write(output, quarantinedEntries);
    }

    /**
     * The data version a record carries, zero for a record that names
     * none. A record is read at exactly the version this build writes;
     * any other stays as it is and the store goes read-only.
     */
    private static int versionOf(NBTTagCompound source) {
        return source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
    }

    public static ReadResult read(NBTTagCompound source) {
        if (source == null) {
            LostTalesLog.warning("Character data root is malformed; data will remain "
                    + "read-only to avoid overwriting it");
            return ReadResult.unsupported(source, -1);
        }
        int rootVersion = versionOf(source);
        if (rootVersion != CURRENT_ROOT_DATA_VERSION) {
            LostTalesLog.warning("Character data root uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(rootVersion));
            return ReadResult.unsupported(source, rootVersion);
        }

        NBTTagCompound root = (NBTTagCompound) source.copy();
        NbtQuarantine.Read quarantineResult = NbtQuarantine.read(root);
        if (!quarantineResult.isSupported()) {
            LostTalesLog.warning("Character quarantine data is malformed or uses unsupported version %d; "
                            + "the whole store will remain read-only",
                    Integer.valueOf(quarantineResult.getUnsupportedVersion()));
            return ReadResult.unsupported(source, quarantineResult.getUnsupportedVersion());
        }

        if (!root.hasKey(TAG_ROSTERS, Constants.NBT.TAG_LIST)) {
            LostTalesLog.warning("Character data root is missing the roster list; "
                    + "the whole store will remain read-only");
            return ReadResult.unsupported(source, -1);
        }
        boolean repaired = false;
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantineResult.getEntries());
        LinkedHashMap<UUID, CharacterRoster> rosters = new LinkedHashMap<UUID, CharacterRoster>();
        NBTTagList rosterList = root.getTagList(TAG_ROSTERS, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < rosterList.tagCount(); i++) {
            NBTTagCompound rawRoster = rosterList.getCompoundTagAt(i);
            RosterReadResult rosterResult = readRoster(rawRoster, i);
            if (rosterResult.unsupportedVersion >= 0) {
                LostTalesLog.warning("Character data contains nested unsupported version %d; "
                                + "the whole store will remain read-only",
                        Integer.valueOf(rosterResult.unsupportedVersion));
                return ReadResult.unsupported(source, rosterResult.unsupportedVersion);
            }
            repaired |= rosterResult.repaired;
            quarantinedEntries.addAll(rosterResult.quarantinedEntries);
            CharacterRoster roster = rosterResult.roster;
            if (roster == null) {
                quarantinedEntries.add(createQuarantineEntry(
                        "roster", rosterResult.failureReason, i, -1, null, null, rawRoster));
                repaired = true;
                continue;
            }
            if (rosters.containsKey(roster.getOwnerId())) {
                repaired = true;
                quarantinedEntries.add(createQuarantineEntry(
                        "roster", "duplicate_roster_owner", i, -1,
                        roster.getOwnerId(), null, rawRoster));
                LostTalesLog.warning("Quarantining duplicate roster for owner %s at index %d",
                        roster.getOwnerId(), Integer.valueOf(i));
                continue;
            }
            rosters.put(roster.getOwnerId(), roster);
        }

        if (quarantinedEntries.size() > quarantineResult.getEntries().size()) {
            LostTalesLog.warning("Preserved %d newly rejected character record(s) in the character-data quarantine",
                    Integer.valueOf(quarantinedEntries.size() - quarantineResult.getEntries().size()));
        }
        return ReadResult.success(rosters, repaired, quarantinedEntries);
    }

    private static NBTTagCompound writeRoster(CharacterRoster roster) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterRoster.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_OWNER_UUID, roster.getOwnerId());
        tag.setInteger(TAG_UNLOCKED_SLOT_COUNT, roster.getUnlockedSlotCount());
        tag.setLong(TAG_REVISION, roster.getRevision());
        if (roster.getActiveCharacterId() != null) {
            NbtTags.writeUuid(tag, TAG_ACTIVE_CHARACTER_UUID, roster.getActiveCharacterId());
        }

        NBTTagList characterList = new NBTTagList();
        for (RoleplayCharacter character : roster.getCharacters()) {
            characterList.appendTag(writeCharacter(character));
        }
        tag.setTag(TAG_CHARACTERS, characterList);
        return tag;
    }

    private static NBTTagCompound writeCharacter(RoleplayCharacter character) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, RoleplayCharacter.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_CHARACTER_UUID, character.getCharacterId());
        NbtTags.writeUuid(tag, TAG_OWNER_UUID, character.getOwnerId());
        tag.setInteger(TAG_SLOT_INDEX, character.getSlotIndex());
        tag.setString(TAG_NAME, character.getName());
        tag.setString(TAG_RACE_ID, character.getRaceId());
        tag.setString(TAG_BODY, character.getBody().getWord());
        tag.setString(TAG_GENDER_ID, character.getGenderId());
        tag.setString(TAG_SKIN_ID, character.getSkinId());
        tag.setString(TAG_BODY_TYPE_ID, character.getBodyTypeId());
        tag.setString(TAG_CHEST_TYPE_ID, character.getChestTypeId());
        tag.setTag(TAG_PROFILE, writeProfile(character.getProfile()));
        tag.setBoolean(TAG_SHOW_MINECRAFT_CAPE, character.isMinecraftCapeVisible());
        tag.setString(TAG_COSMETIC_CAPE, character.getCosmeticCapeId());
        tag.setInteger(TAG_AGE, character.getAge());
        tag.setString(TAG_STARTING_FACTION_ID, character.getStartingFactionId());
        tag.setString(TAG_STARTING_WAYPOINT_ID, character.getStartingWaypointId());
        tag.setBoolean(TAG_UNCONVENTIONAL_SETTINGS,
                character.hasUnconventionalSettings());
        tag.setString(TAG_PLEDGED_FACTION_ID, character.getPledgedFactionId());
        tag.setLong(TAG_CREATION_TIMESTAMP, character.getCreationTimestamp());
        tag.setLong(TAG_FACTION_SINCE, character.getFactionSince());
        tag.setString(TAG_LOTR_TITLE, character.getLotrTitle());
        return tag;
    }

    /** A profile: its About texts, its facts, and its glances in order. */
    private static NBTTagCompound writeProfile(CharacterProfile profile) {
        NBTTagCompound tag = new NBTTagCompound();
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            tag.setString(sectionKey(section), profile.section(section));
        }
        NBTTagCompound facts = new NBTTagCompound();
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            facts.setString(factKey(fact), profile.fact(fact));
        }
        tag.setTag(TAG_PROFILE_FACTS, facts);
        NBTTagList glances = new NBTTagList();
        for (CharacterProfile.Glance glance : profile.glances()) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TAG_GLANCE_EMOJI, glance.getEmoji());
            entry.setString(TAG_GLANCE_TITLE, glance.getTitle());
            entry.setString(TAG_GLANCE_LINE, glance.getLine());
            glances.appendTag(entry);
        }
        tag.setTag(TAG_PROFILE_GLANCES, glances);
        return tag;
    }

    private static String sectionKey(CharacterProfile.Section section) {
        switch (section) {
            case APPEARANCE:
                return TAG_PROFILE_APPEARANCE;
            case PERSONALITY:
                return TAG_PROFILE_PERSONALITY;
            default:
                return TAG_PROFILE_HISTORY;
        }
    }

    private static String factKey(CharacterProfile.Fact fact) {
        switch (fact) {
            case HEIGHT:
                return TAG_FACT_HEIGHT;
            case BUILD:
                return TAG_FACT_BUILD;
            case EYES:
                return TAG_FACT_EYES;
            case HAIR:
                return TAG_FACT_HAIR;
            case BIRTHPLACE:
                return TAG_FACT_BIRTHPLACE;
            default:
                return TAG_FACT_HOME;
        }
    }

    /**
     * The profile a character record holds, every part as it is written:
     * each text in the form it is kept in and within its bounds, at most
     * five glances. Anything else leaves the whole record malformed, kept
     * in the quarantine as it was. The profanity list is not asked here:
     * it may change after the words were accepted.
     */
    private static CharacterProfile readProfile(NBTTagCompound tag)
            throws MalformedRecord {
        if (!tag.hasKey(TAG_PROFILE, Constants.NBT.TAG_COMPOUND)) {
            throw new MalformedRecord("missing_profile");
        }
        NBTTagCompound stored = tag.getCompoundTag(TAG_PROFILE);
        CharacterProfile profile = CharacterProfile.EMPTY;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            String text = requireString(stored, sectionKey(section),
                    "invalid_profile");
            if (!text.equals(CharacterValidator.normalizeSection(text))
                    || !CharacterValidator.isValidSection(section, text)) {
                throw new MalformedRecord("invalid_profile");
            }
            profile = profile.withSection(section, text);
        }
        if (!stored.hasKey(TAG_PROFILE_FACTS, Constants.NBT.TAG_COMPOUND)) {
            throw new MalformedRecord("invalid_profile");
        }
        NBTTagCompound facts = stored.getCompoundTag(TAG_PROFILE_FACTS);
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            String value = requireString(facts, factKey(fact),
                    "invalid_profile");
            if (!value.equals(CharacterValidator.normalizeLine(value))
                    || !CharacterValidator.isValidLine(value,
                            CharacterProfile.MAX_FACT_LENGTH)) {
                throw new MalformedRecord("invalid_profile");
            }
            profile = profile.withFact(fact, value);
        }
        if (!stored.hasKey(TAG_PROFILE_GLANCES, Constants.NBT.TAG_LIST)) {
            throw new MalformedRecord("invalid_profile");
        }
        NBTTagList storedGlances = stored.getTagList(TAG_PROFILE_GLANCES,
                Constants.NBT.TAG_COMPOUND);
        if (storedGlances.tagCount() > CharacterProfile.MAX_GLANCES) {
            throw new MalformedRecord("invalid_profile");
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>();
        for (int index = 0; index < storedGlances.tagCount(); index++) {
            NBTTagCompound entry = storedGlances.getCompoundTagAt(index);
            CharacterProfile.Glance glance = new CharacterProfile.Glance(
                    requireString(entry, TAG_GLANCE_EMOJI, "invalid_profile"),
                    requireString(entry, TAG_GLANCE_TITLE, "invalid_profile"),
                    requireString(entry, TAG_GLANCE_LINE, "invalid_profile"));
            if (!glance.equals(CharacterValidator.normalizeGlance(glance))
                    || !CharacterValidator.isValidGlance(glance)) {
                throw new MalformedRecord("invalid_profile");
            }
            glances.add(glance);
        }
        return profile.withGlances(glances);
    }

    /** A field this build writes and a record does not hold as written: the record is quarantined with the reason. */
    private static final class MalformedRecord extends Exception {
        private static final long serialVersionUID = 1L;

        final String reason;

        MalformedRecord(String reason) {
            super(reason, null, false, false);
            this.reason = reason;
        }
    }

    private static String requireString(NBTTagCompound tag, String key,
                                        String reason) throws MalformedRecord {
        if (!tag.hasKey(key, Constants.NBT.TAG_STRING)) {
            throw new MalformedRecord(reason);
        }
        return tag.getString(key);
    }

    private static int requireInt(NBTTagCompound tag, String key,
                                  String reason) throws MalformedRecord {
        if (!tag.hasKey(key, Constants.NBT.TAG_INT)) {
            throw new MalformedRecord(reason);
        }
        return tag.getInteger(key);
    }

    private static long requireLong(NBTTagCompound tag, String key,
                                    String reason) throws MalformedRecord {
        if (!tag.hasKey(key, Constants.NBT.TAG_LONG)) {
            throw new MalformedRecord(reason);
        }
        return tag.getLong(key);
    }

    private static boolean requireBoolean(NBTTagCompound tag, String key,
                                          String reason) throws MalformedRecord {
        if (!tag.hasKey(key, Constants.NBT.TAG_BYTE)) {
            throw new MalformedRecord(reason);
        }
        return tag.getBoolean(key);
    }

    /** An identifier as this build writes it: in its normalized form. */
    private static boolean isCanonical(String identifier) {
        return identifier.equals(LostTalesIdentifiers.normalize(identifier));
    }

    private static RosterReadResult readRoster(NBTTagCompound source, int rosterIndex) {
        if (source == null) {
            LostTalesLog.warning("Skipping malformed roster at index %d", Integer.valueOf(rosterIndex));
            return RosterReadResult.failed("malformed_roster");
        }
        int version = versionOf(source);
        if (version != CharacterRoster.CURRENT_DATA_VERSION) {
            LostTalesLog.warning("Roster at index %d uses unsupported version %d",
                    Integer.valueOf(rosterIndex), Integer.valueOf(version));
            return RosterReadResult.unsupported(version);
        }

        NBTTagCompound tag = (NBTTagCompound) source.copy();
        boolean repaired = false;
        UUID ownerId = NbtTags.readUuid(tag, TAG_OWNER_UUID);
        if (ownerId == null) {
            LostTalesLog.warning("Skipping roster at index %d because its owner UUID is missing or invalid",
                    Integer.valueOf(rosterIndex));
            return RosterReadResult.failed("missing_or_invalid_owner_uuid");
        }
        if (!tag.hasKey(TAG_UNLOCKED_SLOT_COUNT, Constants.NBT.TAG_INT)
                || tag.getInteger(TAG_UNLOCKED_SLOT_COUNT)
                        < CharacterRoster.INITIAL_UNLOCKED_SLOTS
                || tag.getInteger(TAG_UNLOCKED_SLOT_COUNT)
                        > CharacterRoster.MAX_SLOTS) {
            LostTalesLog.warning("Skipping roster of owner %s because its open slot count "
                    + "is missing or out of range", ownerId);
            return RosterReadResult.failed("invalid_unlocked_slot_count");
        }
        int unlockedSlotCount = tag.getInteger(TAG_UNLOCKED_SLOT_COUNT);
        if (!tag.hasKey(TAG_REVISION, Constants.NBT.TAG_LONG)
                || tag.getLong(TAG_REVISION) < 0L) {
            LostTalesLog.warning("Skipping roster of owner %s because its revision "
                    + "is missing or negative", ownerId);
            return RosterReadResult.failed("invalid_revision");
        }
        if (!tag.hasKey(TAG_CHARACTERS, Constants.NBT.TAG_LIST)) {
            LostTalesLog.warning("Skipping roster of owner %s because its character list "
                    + "is missing", ownerId);
            return RosterReadResult.failed("missing_characters");
        }
        long revision = tag.getLong(TAG_REVISION);

        UUID activeCharacterId = NbtTags.readUuid(tag, TAG_ACTIVE_CHARACTER_UUID);
        CharacterRoster roster = new CharacterRoster(
                ownerId,
                unlockedSlotCount,
                activeCharacterId,
                revision
        );

        ArrayList<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
        NBTTagList characterList = tag.getTagList(TAG_CHARACTERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < characterList.tagCount(); i++) {
            NBTTagCompound rawCharacter = characterList.getCompoundTagAt(i);
            CharacterReadResult characterResult = readCharacter(rawCharacter, ownerId, i);
            if (characterResult.unsupportedVersion >= 0) {
                return RosterReadResult.unsupported(characterResult.unsupportedVersion);
            }
            RoleplayCharacter character = characterResult.character;
            if (character == null) {
                quarantinedEntries.add(createQuarantineEntry(
                        "character", characterResult.failureReason, rosterIndex, i,
                        ownerId, null, rawCharacter));
                repaired = true;
                continue;
            }
            // A character stands only in an open slot, and alone in it.
            if (character.getSlotIndex() >= unlockedSlotCount) {
                repaired = true;
                quarantinedEntries.add(createQuarantineEntry(
                        "character", "slot_not_open",
                        rosterIndex, i, ownerId, character.getCharacterId(), rawCharacter));
                LostTalesLog.warning("Quarantining character %s of owner %s in slot %d, "
                                + "which is not open",
                        character.getCharacterId(), ownerId,
                        Integer.valueOf(character.getSlotIndex()));
                continue;
            }
            if (!roster.addCharacter(character)) {
                repaired = true;
                quarantinedEntries.add(createQuarantineEntry(
                        "character", "duplicate_character_uuid_or_occupied_slot",
                        rosterIndex, i, ownerId, character.getCharacterId(), rawCharacter));
                LostTalesLog.warning("Quarantining duplicate character UUID or occupied slot "
                                + "for owner %s, character %s, slot %d",
                        ownerId, character.getCharacterId(), Integer.valueOf(character.getSlotIndex()));
            }
        }

        // An active character the roster does not hold, one set aside
        // above among them, is played by nobody: its owner is on a first
        // visit until a character is played.
        if (roster.getActiveCharacterId() != null && roster.getActiveCharacter() == null) {
            repaired = true;
            LostTalesLog.warning("Clearing invalid active character %s for owner %s",
                    roster.getActiveCharacterId(), ownerId);
            roster.clearInvalidActiveCharacter();
        }

        return RosterReadResult.success(roster, repaired, quarantinedEntries);
    }

    private static CharacterReadResult readCharacter(NBTTagCompound source, UUID rosterOwnerId,
                                                       int characterIndex) {
        if (source == null) {
            LostTalesLog.warning("Skipping malformed character at index %d for owner %s",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed("malformed_character");
        }
        int version = versionOf(source);
        if (version != RoleplayCharacter.CURRENT_DATA_VERSION) {
            LostTalesLog.warning("Character at index %d for owner %s uses unsupported version %d",
                    Integer.valueOf(characterIndex), rosterOwnerId,
                    Integer.valueOf(version));
            return CharacterReadResult.unsupported(version);
        }
        try {
            return CharacterReadResult.success(decodeCharacter(
                    (NBTTagCompound) source.copy(), rosterOwnerId));
        } catch (MalformedRecord malformed) {
            LostTalesLog.warning("Quarantining the character at index %d for owner %s: %s",
                    Integer.valueOf(characterIndex), rosterOwnerId,
                    malformed.reason);
            return CharacterReadResult.failed(malformed.reason);
        }
    }

    /**
     * A character record exactly as this build writes it: every field
     * present, of its type, and in the form the character holds it: names
     * of the registries in their normalized form, a skin, an arm width and
     * a chest the race and sex may have, an age within its bounds, the
     * faction's start at or after the character's making. Nothing is
     * filled in or put right; a record of any other shape is malformed.
     */
    private static RoleplayCharacter decodeCharacter(NBTTagCompound tag,
                                                     UUID rosterOwnerId)
            throws MalformedRecord {
        UUID characterId = NbtTags.readUuid(tag, TAG_CHARACTER_UUID);
        if (characterId == null) {
            throw new MalformedRecord("missing_or_invalid_character_uuid");
        }
        UUID characterOwnerId = NbtTags.readUuid(tag, TAG_OWNER_UUID);
        if (characterOwnerId == null) {
            throw new MalformedRecord("missing_or_invalid_owner_uuid");
        }
        if (!rosterOwnerId.equals(characterOwnerId)) {
            throw new MalformedRecord("owner_uuid_mismatch");
        }
        int slotIndex = requireInt(tag, TAG_SLOT_INDEX, "missing_slot_index");
        if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
            throw new MalformedRecord("invalid_slot_index");
        }

        String name = requireString(tag, TAG_NAME, "missing_name");
        if (isBlank(name)) {
            throw new MalformedRecord("missing_name");
        }
        // The race is kept as it was saved, and the sex and skin with it:
        // races are data a server may change or take away, and a race gone
        // plays as the body it stood on (CHAR-38). What a race offers is
        // asked where a character is made or changed.
        String raceId = requireString(tag, TAG_RACE_ID, "missing_race");
        if (isBlank(raceId) || !isCanonical(raceId)
                || raceId.length() > MAX_STABLE_IDENTIFIER_LENGTH) {
            throw new MalformedRecord("malformed_race");
        }
        CharacterBody body = CharacterBody.fromWord(
                requireString(tag, TAG_BODY, "missing_body"));
        if (body == null) {
            throw new MalformedRecord("unknown_body");
        }
        String genderId = requireString(tag, TAG_GENDER_ID, "missing_sex");
        if (!isCanonical(genderId) || !CharacterGenderRegistry.contains(genderId)) {
            throw new MalformedRecord("unknown_sex");
        }
        String startingFactionId = requireString(tag, TAG_STARTING_FACTION_ID,
                "missing_starting_faction");
        if (isBlank(startingFactionId)) {
            throw new MalformedRecord("missing_starting_faction");
        }
        String startingWaypointId = requireString(tag, TAG_STARTING_WAYPOINT_ID,
                "missing_starting_waypoint");
        if (startingWaypointId.length() > 0
                && (!startingWaypointId.equals(LotrCharacterAdapter
                        .normalizeWaypointId(startingWaypointId))
                || startingWaypointId.length() > MAX_STABLE_IDENTIFIER_LENGTH)) {
            throw new MalformedRecord("malformed_starting_waypoint");
        }
        boolean unconventionalSettings = requireBoolean(tag,
                TAG_UNCONVENTIONAL_SETTINGS, "missing_unconventional_settings");

        String skinId = requireString(tag, TAG_SKIN_ID, "missing_skin");
        if (isBlank(skinId) || !isCanonical(skinId)
                || skinId.length() > MAX_STABLE_IDENTIFIER_LENGTH) {
            throw new MalformedRecord("malformed_skin");
        }
        String bodyTypeId = requireString(tag, TAG_BODY_TYPE_ID,
                "missing_body_type");
        if (!isCanonical(bodyTypeId)
                || !CharacterBodyTypeRegistry.contains(bodyTypeId)) {
            throw new MalformedRecord("unknown_body_type");
        }
        String chestTypeId = requireString(tag, TAG_CHEST_TYPE_ID,
                "missing_chest_type");
        if (!isCanonical(chestTypeId)
                || !CharacterChestTypeRegistry.contains(chestTypeId)) {
            throw new MalformedRecord("unknown_chest_type");
        }
        CharacterProfile profile = readProfile(tag);

        boolean showMinecraftCape = requireBoolean(tag, TAG_SHOW_MINECRAFT_CAPE,
                "missing_minecraft_cape");
        // A cape is kept as it was chosen, its file there or not (CHAR-39).
        String cosmeticCapeId = requireString(tag, TAG_COSMETIC_CAPE,
                "missing_cosmetic_cape");
        if (!isCanonical(cosmeticCapeId)
                || cosmeticCapeId.length() > CharacterCapeCatalog.MAX_ID) {
            throw new MalformedRecord("malformed_cosmetic_cape");
        }
        int age = requireInt(tag, TAG_AGE, "missing_age");
        if (age < CharacterValidator.MIN_AGE || age > CharacterValidator.MAX_AGE) {
            throw new MalformedRecord("age_out_of_range");
        }
        // The pledge LOTR last reported for the character; empty for none.
        String pledgedFactionId = requireString(tag, TAG_PLEDGED_FACTION_ID,
                "missing_pledged_faction");
        if (!pledgedFactionId.equals(LotrCharacterAdapter
                        .normalizeFactionId(pledgedFactionId))
                || pledgedFactionId.length() > MAX_STABLE_IDENTIFIER_LENGTH) {
            throw new MalformedRecord("malformed_pledged_faction");
        }
        long creationTimestamp = requireLong(tag, TAG_CREATION_TIMESTAMP,
                "missing_creation_timestamp");
        if (creationTimestamp < 0L) {
            throw new MalformedRecord("negative_creation_timestamp");
        }
        long factionSince = requireLong(tag, TAG_FACTION_SINCE,
                "missing_faction_since");
        if (factionSince < creationTimestamp) {
            throw new MalformedRecord("faction_before_making");
        }
        // The LOTR title the character last wore, as words alone.
        String lotrTitle = requireString(tag, TAG_LOTR_TITLE, "missing_lotr_title");
        if (!lotrTitle.equals(RoleplayCharacter.normalizeTitle(lotrTitle))) {
            throw new MalformedRecord("malformed_lotr_title");
        }

        return RoleplayCharacter
                .builder(characterId, characterOwnerId)
                .slot(slotIndex).name(name).race(raceId).body(body).gender(genderId)
                .skin(skinId).age(age).startingFaction(startingFactionId)
                .pledgedFaction(pledgedFactionId)
                .factionSince(factionSince)
                .lotrTitle(lotrTitle)
                .createdAt(creationTimestamp)
                .minecraftCapeVisible(showMinecraftCape)
                .cosmeticCape(cosmeticCapeId)
                .startingWaypoint(startingWaypointId)
                .unconventionalSettings(unconventionalSettings)
                .profile(profile)
                .bodyType(bodyTypeId)
                .chestType(chestTypeId)
                .build();
    }

    private static NBTTagCompound createQuarantineEntry(String entryType, String reason,
                                                         int rosterIndex, int characterIndex,
                                                         UUID ownerId, UUID characterId,
                                                         NBTTagCompound originalData) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_ENTRY_TYPE, isBlank(entryType) ? "unknown" : entryType);
        entry.setString(TAG_REASON, isBlank(reason) ? "unspecified" : reason);
        if (rosterIndex >= 0) {
            entry.setInteger(TAG_ROSTER_INDEX, rosterIndex);
        }
        if (characterIndex >= 0) {
            entry.setInteger(TAG_CHARACTER_INDEX, characterIndex);
        }
        if (ownerId != null) {
            NbtTags.writeUuid(entry, TAG_OWNER_UUID, ownerId);
        }
        if (characterId != null) {
            NbtTags.writeUuid(entry, TAG_CHARACTER_UUID, characterId);
        }
        entry.setTag(TAG_ORIGINAL_DATA, originalData == null
                ? new NBTTagCompound()
                : originalData.copy());
        return entry;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().length() == 0;
    }

    private static List<NBTTagCompound> copyQuarantineEntries(
            Collection<NBTTagCompound> entries) {
        ArrayList<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    copies.add((NBTTagCompound) entry.copy());
                }
            }
        }
        return copies;
    }

    public static final class ReadResult {
        private final Map<UUID, CharacterRoster> rosters;
        private final boolean repaired;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;
        private final List<NBTTagCompound> quarantinedEntries;

        private ReadResult(Map<UUID, CharacterRoster> rosters, boolean repaired,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData,
                           Collection<NBTTagCompound> quarantinedEntries) {
            this.rosters = rosters;
            this.repaired = repaired;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
            this.quarantinedEntries = copyQuarantineEntries(quarantinedEntries);
        }

        public static ReadResult success(Map<UUID, CharacterRoster> rosters, boolean repaired,
                                         Collection<NBTTagCompound> quarantinedEntries) {
            return new ReadResult(rosters, repaired, false, -1, null, quarantinedEntries);
        }

        public static ReadResult unsupported(NBTTagCompound originalData, int unsupportedVersion) {
            NBTTagCompound copy = originalData == null
                    ? new NBTTagCompound()
                    : (NBTTagCompound) originalData.copy();
            return new ReadResult(new LinkedHashMap<UUID, CharacterRoster>(), false,
                    true, unsupportedVersion, copy,
                    Collections.<NBTTagCompound>emptyList());
        }

        public Map<UUID, CharacterRoster> getRosters() {
            return this.rosters;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        public int getUnsupportedVersion() {
            return this.unsupportedVersion;
        }

        public NBTTagCompound getOriginalDataCopy() {
            return this.originalData == null
                    ? null
                    : (NBTTagCompound) this.originalData.copy();
        }

        public List<NBTTagCompound> getQuarantinedEntriesCopy() {
            return copyQuarantineEntries(this.quarantinedEntries);
        }
    }

    private static final class RosterReadResult {
        private final CharacterRoster roster;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;
        private final List<NBTTagCompound> quarantinedEntries;

        private RosterReadResult(CharacterRoster roster, boolean repaired, int unsupportedVersion,
                                 String failureReason,
                                 Collection<NBTTagCompound> quarantinedEntries) {
            this.roster = roster;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
            this.quarantinedEntries = copyQuarantineEntries(quarantinedEntries);
        }

        private static RosterReadResult success(CharacterRoster roster, boolean repaired,
                                                Collection<NBTTagCompound> quarantinedEntries) {
            return new RosterReadResult(roster, repaired, -1, null, quarantinedEntries);
        }

        private static RosterReadResult failed(String reason) {
            return new RosterReadResult(null, false, -1, reason,
                    Collections.<NBTTagCompound>emptyList());
        }

        private static RosterReadResult unsupported(int version) {
            return new RosterReadResult(null, false, version, null,
                    Collections.<NBTTagCompound>emptyList());
        }
    }

    private static final class CharacterReadResult {
        private final RoleplayCharacter character;
        private final int unsupportedVersion;
        private final String failureReason;

        private CharacterReadResult(RoleplayCharacter character,
                                    int unsupportedVersion, String failureReason) {
            this.character = character;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
        }

        private static CharacterReadResult success(RoleplayCharacter character) {
            return new CharacterReadResult(character, -1, null);
        }

        private static CharacterReadResult failed(String reason) {
            return new CharacterReadResult(null, -1, reason);
        }

        private static CharacterReadResult unsupported(int version) {
            return new CharacterReadResult(null, version, null);
        }
    }
}
