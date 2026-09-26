package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProfile;
import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.CharacterKind;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
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
 * Versioned NBT serialization for all roleplaying character data.
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
    private static final String TAG_PROGRESSION = "Progression";
    private static final String TAG_EXTENSION_DATA = "ExtensionData";

    private static final String TAG_OWNER_UUID = "OwnerUUID";
    private static final String TAG_CHARACTER_UUID = "CharacterUUID";
    private static final String TAG_KIND = "Kind";
    private static final String TAG_ACTIVE_CHARACTER_UUID = "ActiveCharacterUUID";

    private static final String TAG_SLOT_INDEX = "SlotIndex";
    private static final String TAG_UNLOCKED_SLOT_COUNT = "UnlockedSlotCount";
    private static final String TAG_REVISION = "Revision";
    private static final String TAG_ACCOUNT_SHOW_MINECRAFT_CAPE = "AccountShowMinecraftCape";
    private static final String TAG_ACCOUNT_COSMETIC_CAPE_ID = "AccountCosmeticCapeId";
    private static final String TAG_TEMPLATE_TAKEN = "TemplateTaken";
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
    private static final String TAG_COSMETIC_CAPE_ID = "CosmeticCapeId";
    private static final String TAG_AGE = "Age";
    private static final String TAG_STARTING_FACTION_ID = "StartingFactionId";
    private static final String TAG_STARTING_WAYPOINT_ID = "StartingWaypointId";
    private static final String TAG_UNCONVENTIONAL_SETTINGS = "UnconventionalSettings";
    private static final String TAG_ROLEPLAY_LEVEL = "RoleplayLevel";
    private static final String TAG_CREATION_TIMESTAMP = "CreationTimestamp";
    private static final String TAG_EXPERIENCE_POINTS = "ExperiencePoints";

    private static final int MAX_REASONABLE_AGE = 100000;
    private static final int MAX_STABLE_IDENTIFIER_LENGTH = 64;

    private CharacterNbtCodec() {}

    public static void write(NBTTagCompound output, Collection<CharacterRoster> rosters) {
        write(output, rosters, Collections.<NBTTagCompound>emptyList());
    }

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
                source, expectedOwnerId, -1, -1);
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
        NbtQuarantine.Read quarantineResult = NbtQuarantine.readCurrentVersionOnly(root);
        if (!quarantineResult.isSupported()) {
            LostTalesLog.warning("Character quarantine data is malformed or uses unsupported version %d; "
                            + "the whole store will remain read-only",
                    Integer.valueOf(quarantineResult.getUnsupportedVersion()));
            return ReadResult.unsupported(source, quarantineResult.getUnsupportedVersion());
        }

        boolean repaired = quarantineResult.isRepaired();
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantineResult.getEntries());
        if (!root.hasKey(TAG_ROSTERS, Constants.NBT.TAG_LIST)) {
            repaired = true;
            LostTalesLog.warning("Character data root is missing the roster list; repairing it as empty");
        }
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
        tag.setBoolean(TAG_ACCOUNT_SHOW_MINECRAFT_CAPE, roster.isAccountMinecraftCapeVisible());
        tag.setInteger(TAG_ACCOUNT_COSMETIC_CAPE_ID, roster.getAccountCosmeticCapeId());
        tag.setBoolean(TAG_TEMPLATE_TAKEN, roster.isTemplateTaken());

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
        tag.setString(TAG_KIND, character.getKind().getId());
        tag.setInteger(TAG_SLOT_INDEX, character.getSlotIndex());
        tag.setString(TAG_NAME, character.getName());
        tag.setString(TAG_RACE_ID, character.getRaceId());
        tag.setString(TAG_GENDER_ID, character.getGenderId());
        tag.setString(TAG_SKIN_ID, character.getSkinId());
        tag.setString(TAG_BODY_TYPE_ID, character.getBodyTypeId());
        tag.setString(TAG_CHEST_TYPE_ID, character.getChestTypeId());
        tag.setTag(TAG_PROFILE, writeProfile(character.getProfile()));
        tag.setBoolean(TAG_SHOW_MINECRAFT_CAPE, character.isMinecraftCapeVisible());
        tag.setInteger(TAG_COSMETIC_CAPE_ID, character.getCosmeticCapeId());
        tag.setInteger(TAG_AGE, character.getAge());
        tag.setString(TAG_STARTING_FACTION_ID, character.getStartingFactionId());
        tag.setString(TAG_STARTING_WAYPOINT_ID, character.getStartingWaypointId());
        tag.setBoolean(TAG_UNCONVENTIONAL_SETTINGS,
                character.hasUnconventionalSettings());
        tag.setInteger(TAG_ROLEPLAY_LEVEL, character.getRoleplayLevel());
        tag.setLong(TAG_CREATION_TIMESTAMP, character.getCreationTimestamp());
        tag.setTag(TAG_PROGRESSION, writeProgression(character.getProgression()));
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

    /** A profile read back, and whether any part of it had to be repaired. */
    private static final class ProfileReadResult {
        final CharacterProfile profile;
        final boolean repaired;

        ProfileReadResult(CharacterProfile profile, boolean repaired) {
            this.profile = profile;
            this.repaired = repaired;
        }
    }

    /**
     * The profile a character record holds. A part that is missing, or no
     * longer valid as stored, is read as empty, a glance that no longer is
     * one is left out, and so is one past the fifth; the record is then
     * repaired, so it is written back as read. The profanity list is not
     * asked here: it may change after the words were accepted.
     */
    private static ProfileReadResult readProfile(NBTTagCompound tag,
                                                 UUID characterId,
                                                 UUID ownerId) {
        if (!tag.hasKey(TAG_PROFILE, Constants.NBT.TAG_COMPOUND)) {
            LostTalesLog.warning("Assigning an empty profile to character %s owned by %s",
                    characterId, ownerId);
            return new ProfileReadResult(CharacterProfile.EMPTY, true);
        }
        NBTTagCompound stored = tag.getCompoundTag(TAG_PROFILE);
        boolean repaired = false;
        CharacterProfile profile = CharacterProfile.EMPTY;
        for (CharacterProfile.Section section : CharacterProfile.Section.values()) {
            String key = sectionKey(section);
            String text = readBoundedString(stored, key,
                    CharacterProfile.MAX_SECTION_LENGTH);
            String kept = CharacterValidator.normalizeSection(text);
            if (!CharacterValidator.isValidSection(kept)) {
                kept = "";
            }
            if (!stored.hasKey(key, Constants.NBT.TAG_STRING)
                    || !kept.equals(text)) {
                repaired = true;
            }
            profile = profile.withSection(section, kept);
        }
        NBTTagCompound facts = stored.getCompoundTag(TAG_PROFILE_FACTS);
        if (!stored.hasKey(TAG_PROFILE_FACTS, Constants.NBT.TAG_COMPOUND)) {
            repaired = true;
        }
        for (CharacterProfile.Fact fact : CharacterProfile.Fact.values()) {
            String key = factKey(fact);
            String value = readBoundedString(facts, key,
                    CharacterProfile.MAX_FACT_LENGTH);
            String kept = CharacterValidator.normalizeLine(value);
            if (!CharacterValidator.isValidLine(kept,
                    CharacterProfile.MAX_FACT_LENGTH)) {
                kept = "";
            }
            if (!facts.hasKey(key, Constants.NBT.TAG_STRING)
                    || !kept.equals(value)) {
                repaired = true;
            }
            profile = profile.withFact(fact, kept);
        }
        List<CharacterProfile.Glance> glances =
                new ArrayList<CharacterProfile.Glance>();
        if (!stored.hasKey(TAG_PROFILE_GLANCES, Constants.NBT.TAG_LIST)) {
            repaired = true;
        }
        NBTTagList storedGlances = stored.getTagList(TAG_PROFILE_GLANCES,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < storedGlances.tagCount(); index++) {
            NBTTagCompound entry = storedGlances.getCompoundTagAt(index);
            CharacterProfile.Glance read = new CharacterProfile.Glance(
                    readBoundedString(entry, TAG_GLANCE_EMOJI,
                            MAX_STABLE_IDENTIFIER_LENGTH),
                    readBoundedString(entry, TAG_GLANCE_TITLE,
                            CharacterProfile.MAX_GLANCE_TITLE_LENGTH),
                    readBoundedString(entry, TAG_GLANCE_LINE,
                            CharacterProfile.MAX_GLANCE_LINE_LENGTH));
            CharacterProfile.Glance glance =
                    CharacterValidator.normalizeGlance(read);
            if (glances.size() >= CharacterProfile.MAX_GLANCES
                    || !CharacterValidator.isValidGlance(glance)) {
                repaired = true;
                continue;
            }
            if (!glance.equals(read)) {
                repaired = true;
            }
            glances.add(glance);
        }
        if (repaired) {
            LostTalesLog.warning("Repairing the profile of character %s owned by %s",
                    characterId, ownerId);
        }
        return new ProfileReadResult(profile.withGlances(glances), repaired);
    }

    /**
     * A string as stored, or empty for one missing or so long that no
     * normalising could bring it within {@code limit} characters.
     */
    private static String readBoundedString(NBTTagCompound tag, String key,
                                            int limit) {
        String value = tag.hasKey(key, Constants.NBT.TAG_STRING)
                ? tag.getString(key) : "";
        return value.length() > limit * 2 ? "" : value;
    }

    private static NBTTagCompound writeProgression(CharacterProgression progression) {
        CharacterProgression safeProgression = progression == null
                ? new CharacterProgression()
                : progression;
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterProgression.CURRENT_DATA_VERSION);
        tag.setLong(TAG_EXPERIENCE_POINTS, safeProgression.getExperiencePoints());
        tag.setTag(TAG_EXTENSION_DATA, safeProgression.getExtensionDataCopy());
        return tag;
    }

    private static RosterReadResult readRoster(NBTTagCompound source, int rosterIndex) {
        if (source == null) {
            LostTalesLog.warning("Skipping malformed roster at index %d", Integer.valueOf(rosterIndex));
            return RosterReadResult.failed(true, "malformed_roster");
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
            return RosterReadResult.failed(true, "missing_or_invalid_owner_uuid");
        }

        boolean hasUnlockedSlotCount = tag.hasKey(TAG_UNLOCKED_SLOT_COUNT, Constants.NBT.TAG_INT);
        int unlockedSlotCount = hasUnlockedSlotCount
                ? tag.getInteger(TAG_UNLOCKED_SLOT_COUNT)
                : CharacterRoster.INITIAL_UNLOCKED_SLOTS;
        if (!hasUnlockedSlotCount) {
            repaired = true;
            LostTalesLog.warning("Repairing missing unlocked slot count for owner %s", ownerId);
        }
        if (unlockedSlotCount < CharacterRoster.INITIAL_UNLOCKED_SLOTS
                || unlockedSlotCount > CharacterRoster.MAX_SLOTS) {
            repaired = true;
            LostTalesLog.warning("Repairing unlocked slot count %d for owner %s",
                    Integer.valueOf(unlockedSlotCount), ownerId);
        }

        boolean hasRevision = tag.hasKey(TAG_REVISION, Constants.NBT.TAG_LONG);
        long revision = hasRevision ? tag.getLong(TAG_REVISION) : 0L;
        if (!hasRevision) {
            repaired = true;
            LostTalesLog.warning("Repairing missing roster revision for owner %s", ownerId);
        }
        if (revision < 0L) {
            revision = 0L;
            repaired = true;
            LostTalesLog.warning("Repairing negative roster revision for owner %s", ownerId);
        }

        UUID activeCharacterId = NbtTags.readUuid(tag, TAG_ACTIVE_CHARACTER_UUID);
        CharacterRoster roster = new CharacterRoster(
                ownerId,
                unlockedSlotCount,
                activeCharacterId,
                revision,
                CharacterRoster.CURRENT_DATA_VERSION
        );
        // The account cape keys are newer than the roster layout: a roster
        // without them wears the defaults, and an unknown cape id is
        // repaired to none rather than refused.
        boolean showAccountCape = tag.hasKey(TAG_ACCOUNT_SHOW_MINECRAFT_CAPE, Constants.NBT.TAG_BYTE)
                ? tag.getBoolean(TAG_ACCOUNT_SHOW_MINECRAFT_CAPE)
                : RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE;
        int accountCapeId = tag.hasKey(TAG_ACCOUNT_COSMETIC_CAPE_ID, Constants.NBT.TAG_INT)
                ? tag.getInteger(TAG_ACCOUNT_COSMETIC_CAPE_ID)
                : RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID;
        if (!CharacterCapeCatalog.isValidSelection(accountCapeId)) {
            LostTalesLog.warning("Repairing unknown account cape %d for owner %s",
                    Integer.valueOf(accountCapeId), ownerId);
            accountCapeId = RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID;
            repaired = true;
        }
        roster.setAccountCapeSettings(showAccountCape, accountCapeId);
        // A roster without the key has not read one: such a world takes
        // the template on the player's next login instead of never.
        if (tag.getBoolean(TAG_TEMPLATE_TAKEN)) {
            roster.markTemplateTaken();
        }

        ArrayList<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
        NBTTagList characterList = tag.getTagList(TAG_CHARACTERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < characterList.tagCount(); i++) {
            NBTTagCompound rawCharacter = characterList.getCompoundTagAt(i);
            CharacterReadResult characterResult = readCharacter(rawCharacter, ownerId, rosterIndex, i);
            if (characterResult.unsupportedVersion >= 0) {
                return RosterReadResult.unsupported(characterResult.unsupportedVersion);
            }
            repaired |= characterResult.repaired;
            quarantinedEntries.addAll(characterResult.quarantinedEntries);
            RoleplayCharacter character = characterResult.character;
            if (character == null) {
                quarantinedEntries.add(createQuarantineEntry(
                        "character", characterResult.failureReason, rosterIndex, i,
                        ownerId, null, rawCharacter));
                repaired = true;
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

        int beforeNormalizedUnlockedCount = roster.getUnlockedSlotCount();
        roster.setUnlockedSlotCount(unlockedSlotCount);
        if (roster.getUnlockedSlotCount() != beforeNormalizedUnlockedCount
                || roster.getUnlockedSlotCount() != clampUnlockedSlotCount(unlockedSlotCount)) {
            repaired = true;
            LostTalesLog.warning("Expanded unlocked slots for owner %s to preserve occupied slots", ownerId);
        }

        if (roster.getActiveCharacterId() != null && roster.getActiveCharacter() == null) {
            repaired = true;
            LostTalesLog.warning("Clearing invalid active character %s for owner %s",
                    roster.getActiveCharacterId(), ownerId);
            roster.clearInvalidActiveCharacter();
        }

        return RosterReadResult.success(roster, repaired, quarantinedEntries);
    }

    private static CharacterReadResult readCharacter(NBTTagCompound source, UUID rosterOwnerId,
                                                       int rosterIndex, int characterIndex) {
        if (source == null) {
            LostTalesLog.warning("Skipping malformed character at index %d for owner %s",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed(true, "malformed_character");
        }
        int version = versionOf(source);
        if (version != RoleplayCharacter.CURRENT_DATA_VERSION) {
            LostTalesLog.warning("Character at index %d for owner %s uses unsupported version %d",
                    Integer.valueOf(characterIndex), rosterOwnerId,
                    Integer.valueOf(version));
            return CharacterReadResult.unsupported(version);
        }

        NBTTagCompound tag = (NBTTagCompound) source.copy();
        boolean repaired = false;
        ArrayList<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
        UUID characterId = NbtTags.readUuid(tag, TAG_CHARACTER_UUID);
        if (characterId == null) {
            LostTalesLog.warning("Skipping character at index %d for owner %s because its UUID is missing or invalid",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed(true, "missing_or_invalid_character_uuid");
        }

        UUID characterOwnerId = NbtTags.readUuid(tag, TAG_OWNER_UUID);
        if (characterOwnerId == null) {
            characterOwnerId = rosterOwnerId;
            repaired = true;
            LostTalesLog.warning("Repairing missing owner UUID for character %s using roster owner %s",
                    characterId, rosterOwnerId);
        } else if (!rosterOwnerId.equals(characterOwnerId)) {
            LostTalesLog.warning("Skipping character %s because owner %s does not match roster owner %s",
                    characterId, characterOwnerId, rosterOwnerId);
            return CharacterReadResult.failed(true, "owner_uuid_mismatch");
        }

        if (!tag.hasKey(TAG_SLOT_INDEX, Constants.NBT.TAG_INT)) {
            LostTalesLog.warning("Skipping character %s for owner %s because its slot index is missing",
                    characterId, rosterOwnerId);
            return CharacterReadResult.failed(true, "missing_slot_index");
        }
        int slotIndex = tag.getInteger(TAG_SLOT_INDEX);
        if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
            LostTalesLog.warning("Skipping character %s for owner %s because slot %d is invalid",
                    characterId, rosterOwnerId, Integer.valueOf(slotIndex));
            return CharacterReadResult.failed(true, "invalid_slot_index");
        }

        String name = tag.getString(TAG_NAME);
        String storedRaceId = tag.getString(TAG_RACE_ID);
        String raceId = LostTalesIdentifiers.normalize(storedRaceId);
        if (CharacterRaceRegistry.get(raceId) == null) {
            raceId = CharacterRaceRegistry.HUMAN;
            repaired = true;
            LostTalesLog.warning("Repairing unknown race %s to safe fallback %s for character %s owned by %s",
                    storedRaceId, raceId, characterId, rosterOwnerId);
        }
        String storedGenderId = LostTalesIdentifiers.normalize(
                tag.getString(TAG_GENDER_ID));
        String genderId = CharacterRaceRegistry.normalizeGenderForRace(
                raceId, storedGenderId);
        String startingFactionId = tag.getString(TAG_STARTING_FACTION_ID);
        // A record naming no kind, or one this build does not know, is a
        // roleplay character.
        CharacterKind kind = CharacterKind.fromId(tag.getString(TAG_KIND));
        if ((slotIndex == CharacterRoster.DEFAULT_SLOT_INDEX)
                != (kind == CharacterKind.DEFAULT)) {
            // The slot and the kind are two spellings of the same fact,
            // and everything that reads the roster picks one of them.
            // A record where they disagree would be the account's own
            // identity to one reader and a deletable character to
            // another, so it is quarantined rather than guessed at.
            LostTalesLog.warning("Skipping character %s for owner %s because slot %d and kind %s disagree",
                    characterId, rosterOwnerId, Integer.valueOf(slotIndex),
                    kind.getId());
            return CharacterReadResult.failed(true, "slot_kind_mismatch");
        }
        if (isBlank(name) || isBlank(raceId) || isBlank(genderId)
                || isBlank(startingFactionId)) {
            LostTalesLog.warning("Skipping character %s for owner %s because a required "
                            + "text field is empty or unsupported",
                    characterId, rosterOwnerId);
            return CharacterReadResult.failed(true, "missing_required_text_field");
        }
        if (!raceId.equals(storedRaceId)) {
            repaired = true;
            LostTalesLog.warning("Repairing race id %s to %s for character %s owned by %s",
                    storedRaceId, raceId, characterId, rosterOwnerId);
        }
        if (!genderId.equals(storedGenderId)) {
            repaired = true;
            LostTalesLog.warning("Repairing gender %s to %s for race %s on character %s owned by %s",
                    storedGenderId, genderId, raceId, characterId, rosterOwnerId);
        }

        boolean hasStartingWaypoint = tag.hasKey(
                TAG_STARTING_WAYPOINT_ID, Constants.NBT.TAG_STRING);
        String startingWaypointId = hasStartingWaypoint
                ? tag.getString(TAG_STARTING_WAYPOINT_ID) : "";
        String normalizedStartingWaypointId =
                LotrCharacterAdapter.normalizeWaypointId(startingWaypointId);
        if (startingWaypointId.length() > 0
                && (!startingWaypointId.equals(normalizedStartingWaypointId)
                || normalizedStartingWaypointId.length()
                > MAX_STABLE_IDENTIFIER_LENGTH)) {
            startingWaypointId = "";
            repaired = true;
            LostTalesLog.warning("Clearing malformed starting waypoint for character %s owned by %s",
                    characterId, rosterOwnerId);
        } else {
            startingWaypointId = normalizedStartingWaypointId;
        }
        if (!hasStartingWaypoint) {
            repaired = true;
        }
        boolean hasUnconventionalSettings = tag.hasKey(
                TAG_UNCONVENTIONAL_SETTINGS, Constants.NBT.TAG_BYTE);
        boolean unconventionalSettings = hasUnconventionalSettings
                && tag.getBoolean(TAG_UNCONVENTIONAL_SETTINGS);
        if (!hasUnconventionalSettings) {
            repaired = true;
        }

        String skinId = LostTalesIdentifiers.normalize(
                tag.getString(TAG_SKIN_ID));
        if (!CharacterSkinRegistry.isCompatible(skinId, raceId, genderId)) {
            skinId = CharacterSkinRegistry.getDefaultSkinId(raceId, genderId, characterId);
            repaired = true;
            LostTalesLog.warning("Assigning a compatible LOTR skin to character %s owned by %s",
                    characterId, rosterOwnerId);
        }

        // A record without a body type takes the sex's, which is what the
        // creator would have pre-selected.
        boolean hasBodyType = tag.hasKey(TAG_BODY_TYPE_ID, Constants.NBT.TAG_STRING);
        String storedBodyTypeId = hasBodyType
                ? LostTalesIdentifiers.normalize(tag.getString(TAG_BODY_TYPE_ID))
                : "";
        String bodyTypeId = CharacterBodyTypeRegistry.contains(storedBodyTypeId)
                ? storedBodyTypeId
                : CharacterBodyTypeRegistry.defaultFor(genderId);
        if (!bodyTypeId.equals(storedBodyTypeId)) {
            repaired = true;
            if (hasBodyType) {
                LostTalesLog.warning("Repairing unknown body type %s to %s for character %s owned by %s",
                        storedBodyTypeId, bodyTypeId, characterId, rosterOwnerId);
            }
        }

        // A record without a chest type takes the sex's, as the creator
        // would have.
        boolean hasChestType = tag.hasKey(TAG_CHEST_TYPE_ID, Constants.NBT.TAG_STRING);
        String storedChestTypeId = hasChestType
                ? LostTalesIdentifiers.normalize(tag.getString(TAG_CHEST_TYPE_ID))
                : "";
        String chestTypeId = CharacterChestTypeRegistry.contains(storedChestTypeId)
                ? storedChestTypeId
                : CharacterChestTypeRegistry.defaultFor(genderId);
        if (!chestTypeId.equals(storedChestTypeId)) {
            repaired = true;
            if (hasChestType) {
                LostTalesLog.warning("Repairing unknown chest type %s to %s for character %s owned by %s",
                        storedChestTypeId, chestTypeId, characterId, rosterOwnerId);
            }
        }

        ProfileReadResult profileResult = readProfile(tag, characterId,
                rosterOwnerId);
        if (profileResult.repaired) {
            repaired = true;
        }

        boolean hasShowMinecraftCape = tag.hasKey(
                TAG_SHOW_MINECRAFT_CAPE, Constants.NBT.TAG_BYTE);
        boolean showMinecraftCape = hasShowMinecraftCape
                ? tag.getBoolean(TAG_SHOW_MINECRAFT_CAPE)
                : RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE;
        if (!hasShowMinecraftCape) {
            repaired = true;
            LostTalesLog.warning("Assigning the default normal-cape visibility to character %s owned by %s",
                    characterId, rosterOwnerId);
        }

        boolean hasCosmeticCapeId = tag.hasKey(
                TAG_COSMETIC_CAPE_ID, Constants.NBT.TAG_INT);
        int storedCosmeticCapeId = hasCosmeticCapeId
                ? tag.getInteger(TAG_COSMETIC_CAPE_ID)
                : RoleplayCharacter.DEFAULT_COSMETIC_CAPE_ID;
        int cosmeticCapeId = CharacterCapeCatalog.normalizeSelection(storedCosmeticCapeId);
        if (!hasCosmeticCapeId || cosmeticCapeId != storedCosmeticCapeId) {
            repaired = true;
            if (hasCosmeticCapeId) {
                LostTalesLog.warning("Clearing invalid cosmetic cape ID %d for character %s owned by %s",
                        Integer.valueOf(storedCosmeticCapeId), characterId, rosterOwnerId);
            } else {
                LostTalesLog.warning("Assigning no cosmetic cape to character %s owned by %s",
                        characterId, rosterOwnerId);
            }
        }

        boolean hasAge = tag.hasKey(TAG_AGE, Constants.NBT.TAG_INT);
        int age = hasAge ? tag.getInteger(TAG_AGE) : 1;
        if (!hasAge) {
            repaired = true;
            LostTalesLog.warning("Repairing missing age for character %s owned by %s", characterId, rosterOwnerId);
        } else if (age < 1) {
            age = 1;
            repaired = true;
            LostTalesLog.warning("Repairing non-positive age for character %s owned by %s", characterId, rosterOwnerId);
        } else if (age > MAX_REASONABLE_AGE) {
            age = MAX_REASONABLE_AGE;
            repaired = true;
            LostTalesLog.warning("Clamping unreasonable age for character %s owned by %s", characterId, rosterOwnerId);
        }

        boolean hasRoleplayLevel = tag.hasKey(TAG_ROLEPLAY_LEVEL, Constants.NBT.TAG_INT);
        int roleplayLevel = hasRoleplayLevel
                ? tag.getInteger(TAG_ROLEPLAY_LEVEL)
                : RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL;
        if (!hasRoleplayLevel) {
            repaired = true;
            LostTalesLog.warning("Repairing missing roleplay level for character %s owned by %s",
                    characterId, rosterOwnerId);
        } else if (roleplayLevel < RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL) {
            roleplayLevel = RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL;
            repaired = true;
            LostTalesLog.warning("Repairing invalid roleplay level for character %s owned by %s",
                    characterId, rosterOwnerId);
        }

        boolean hasCreationTimestamp = tag.hasKey(TAG_CREATION_TIMESTAMP, Constants.NBT.TAG_LONG);
        long creationTimestamp = hasCreationTimestamp ? tag.getLong(TAG_CREATION_TIMESTAMP) : 0L;
        if (!hasCreationTimestamp) {
            repaired = true;
            LostTalesLog.warning("Repairing missing creation timestamp for character %s owned by %s",
                    characterId, rosterOwnerId);
        }
        if (creationTimestamp < 0L) {
            creationTimestamp = 0L;
            repaired = true;
            LostTalesLog.warning("Repairing negative creation timestamp for character %s owned by %s",
                    characterId, rosterOwnerId);
        }

        boolean progressionTagPresent = tag.hasKey(TAG_PROGRESSION);
        boolean progressionCompoundPresent = tag.hasKey(TAG_PROGRESSION, Constants.NBT.TAG_COMPOUND);
        ProgressionReadResult progressionResult = readProgression(
                tag.getCompoundTag(TAG_PROGRESSION), characterId, rosterOwnerId,
                progressionCompoundPresent);
        if (progressionResult.unsupportedVersion >= 0) {
            return CharacterReadResult.unsupported(progressionResult.unsupportedVersion);
        }
        repaired |= progressionResult.repaired;
        if ((progressionTagPresent && !progressionCompoundPresent)
                || progressionResult.replacedMalformedData) {
            quarantinedEntries.add(createQuarantineEntry(
                    "character", "malformed_progression_replaced", rosterIndex, characterIndex,
                    rosterOwnerId, characterId, source));
        }

        RoleplayCharacter character = RoleplayCharacter
                .builder(characterId, characterOwnerId)
                .slot(slotIndex).name(name).race(raceId).gender(genderId)
                .skin(skinId).age(age).startingFaction(startingFactionId)
                .roleplayLevel(roleplayLevel)
                .progression(progressionResult.progression)
                .createdAt(creationTimestamp)
                .dataVersion(RoleplayCharacter.CURRENT_DATA_VERSION)
                .minecraftCapeVisible(showMinecraftCape)
                .cosmeticCape(cosmeticCapeId)
                .startingWaypoint(startingWaypointId)
                .unconventionalSettings(unconventionalSettings)
                .profile(profileResult.profile)
                .bodyType(bodyTypeId)
                .chestType(chestTypeId)
                .kind(kind)
                .build();
        return CharacterReadResult.success(character, repaired, quarantinedEntries);
    }

    private static ProgressionReadResult readProgression(NBTTagCompound source, UUID characterId,
                                                          UUID ownerId, boolean wasPresent) {
        if (!wasPresent) {
            LostTalesLog.warning("Creating missing progression container for character %s owned by %s",
                    characterId, ownerId);
            return ProgressionReadResult.success(new CharacterProgression(), true, false);
        }

        int version = versionOf(source);
        if (version != CharacterProgression.CURRENT_DATA_VERSION) {
            LostTalesLog.warning("Progression data for character %s owned by %s uses unsupported version %d",
                    characterId, ownerId, Integer.valueOf(version));
            return ProgressionReadResult.unsupported(version);
        }

        NBTTagCompound tag = (NBTTagCompound) source.copy();
        boolean repaired = false;
        long experiencePoints = tag.hasKey(TAG_EXPERIENCE_POINTS, Constants.NBT.TAG_LONG)
                ? tag.getLong(TAG_EXPERIENCE_POINTS)
                : 0L;
        if (experiencePoints < 0L) {
            experiencePoints = 0L;
            repaired = true;
            LostTalesLog.warning("Repairing negative experience for character %s owned by %s", characterId, ownerId);
        }

        boolean extensionTagPresent = tag.hasKey(TAG_EXTENSION_DATA);
        boolean hasExtensionData = tag.hasKey(TAG_EXTENSION_DATA, Constants.NBT.TAG_COMPOUND);
        boolean replacedMalformedData = extensionTagPresent && !hasExtensionData;
        NBTTagCompound extensionData = hasExtensionData
                ? tag.getCompoundTag(TAG_EXTENSION_DATA)
                : new NBTTagCompound();
        if (!hasExtensionData) {
            repaired = true;
            LostTalesLog.warning("Repairing missing or malformed progression extension "
                            + "data for character %s owned by %s",
                    characterId, ownerId);
        }
        CharacterProgression progression = new CharacterProgression(
                CharacterProgression.CURRENT_DATA_VERSION,
                experiencePoints,
                extensionData
        );
        return ProgressionReadResult.success(progression, repaired, replacedMalformedData);
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

    private static int clampUnlockedSlotCount(int count) {
        if (count < CharacterRoster.INITIAL_UNLOCKED_SLOTS) {
            return CharacterRoster.INITIAL_UNLOCKED_SLOTS;
        }
        return Math.min(CharacterRoster.MAX_SLOTS, count);
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

        public static ReadResult success(Map<UUID, CharacterRoster> rosters, boolean repaired) {
            return success(rosters, repaired, Collections.<NBTTagCompound>emptyList());
        }

        public static ReadResult empty(boolean repaired) {
            return success(new LinkedHashMap<UUID, CharacterRoster>(), repaired);
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

        private static RosterReadResult failed(boolean repaired, String reason) {
            return new RosterReadResult(null, repaired, -1, reason,
                    Collections.<NBTTagCompound>emptyList());
        }

        private static RosterReadResult unsupported(int version) {
            return new RosterReadResult(null, false, version, null,
                    Collections.<NBTTagCompound>emptyList());
        }
    }

    private static final class CharacterReadResult {
        private final RoleplayCharacter character;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final String failureReason;
        private final List<NBTTagCompound> quarantinedEntries;

        private CharacterReadResult(RoleplayCharacter character, boolean repaired,
                                    int unsupportedVersion, String failureReason,
                                    Collection<NBTTagCompound> quarantinedEntries) {
            this.character = character;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.failureReason = failureReason;
            this.quarantinedEntries = copyQuarantineEntries(quarantinedEntries);
        }

        private static CharacterReadResult success(RoleplayCharacter character, boolean repaired,
                                                   Collection<NBTTagCompound> quarantinedEntries) {
            return new CharacterReadResult(character, repaired, -1, null, quarantinedEntries);
        }

        private static CharacterReadResult failed(boolean repaired, String reason) {
            return new CharacterReadResult(null, repaired, -1, reason,
                    Collections.<NBTTagCompound>emptyList());
        }

        private static CharacterReadResult unsupported(int version) {
            return new CharacterReadResult(null, false, version, null,
                    Collections.<NBTTagCompound>emptyList());
        }
    }

    private static final class ProgressionReadResult {
        private final CharacterProgression progression;
        private final boolean repaired;
        private final int unsupportedVersion;
        private final boolean replacedMalformedData;

        private ProgressionReadResult(CharacterProgression progression, boolean repaired,
                                      int unsupportedVersion, boolean replacedMalformedData) {
            this.progression = progression;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
            this.replacedMalformedData = replacedMalformedData;
        }

        private static ProgressionReadResult success(CharacterProgression progression,
                                                     boolean repaired,
                                                     boolean replacedMalformedData) {
            return new ProgressionReadResult(progression, repaired, -1,
                    replacedMalformedData);
        }

        private static ProgressionReadResult unsupported(int version) {
            return new ProgressionReadResult(null, false, version, false);
        }
    }
}
