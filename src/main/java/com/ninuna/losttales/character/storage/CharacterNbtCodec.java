package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.validation.CharacterValidator;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import cpw.mods.fml.common.FMLLog;
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
    public static final int CURRENT_QUARANTINE_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ROSTERS = "Rosters";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_QUARANTINE_ENTRIES = "Entries";
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
    private static final String TAG_ACTIVE_CHARACTER_UUID = "ActiveCharacterUUID";

    private static final String TAG_SLOT_INDEX = "SlotIndex";
    private static final String TAG_UNLOCKED_SLOT_COUNT = "UnlockedSlotCount";
    private static final String TAG_REVISION = "Revision";
    private static final String TAG_NAME = "Name";
    private static final String TAG_RACE_ID = "RaceId";
    private static final String TAG_GENDER_ID = "GenderId";
    private static final String TAG_SKIN_ID = "SkinId";
    private static final String TAG_DESCRIPTION = "Description";
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
        output.setTag(TAG_QUARANTINE, writeQuarantine(quarantinedEntries));
    }

    public static ReadResult read(NBTTagCompound source) {
        CharacterDataMigrator.MigrationResult rootMigration =
                CharacterDataMigrator.migrateRoot(source, CURRENT_ROOT_DATA_VERSION);

        if (!rootMigration.isValid()) {
            warn("Character data root is malformed; data will remain read-only to avoid overwriting it");
            return ReadResult.unsupported(source, -1);
        }
        if (!rootMigration.isSupported()) {
            warn("Character data root uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(rootMigration.getVersion()));
            return ReadResult.unsupported(source, rootMigration.getVersion());
        }

        NBTTagCompound root = rootMigration.getTag();
        QuarantineReadResult quarantineResult = readQuarantine(root);
        if (!quarantineResult.supported) {
            warn("Character quarantine data is malformed or uses unsupported version %d; "
                            + "the whole store will remain read-only",
                    Integer.valueOf(quarantineResult.unsupportedVersion));
            return ReadResult.unsupported(source, quarantineResult.unsupportedVersion);
        }

        boolean repaired = rootMigration.wasMigrated() || quarantineResult.repaired;
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantineResult.entries);
        if (!root.hasKey(TAG_ROSTERS, Constants.NBT.TAG_LIST)) {
            repaired = true;
            warn("Character data root is missing the roster list; repairing it as empty");
        }
        LinkedHashMap<UUID, CharacterRoster> rosters = new LinkedHashMap<UUID, CharacterRoster>();
        NBTTagList rosterList = root.getTagList(TAG_ROSTERS, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < rosterList.tagCount(); i++) {
            NBTTagCompound rawRoster = rosterList.getCompoundTagAt(i);
            RosterReadResult rosterResult = readRoster(rawRoster, i);
            if (rosterResult.unsupportedVersion >= 0) {
                warn("Character data contains nested unsupported version %d; the whole store will remain read-only",
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
                warn("Quarantining duplicate roster for owner %s at index %d",
                        roster.getOwnerId(), Integer.valueOf(i));
                continue;
            }
            rosters.put(roster.getOwnerId(), roster);
        }

        if (quarantinedEntries.size() > quarantineResult.entries.size()) {
            warn("Preserved %d newly rejected character record(s) in the character-data quarantine",
                    Integer.valueOf(quarantinedEntries.size() - quarantineResult.entries.size()));
        }
        return ReadResult.success(rosters, repaired, quarantinedEntries);
    }

    private static NBTTagCompound writeQuarantine(Collection<NBTTagCompound> entries) {
        NBTTagCompound quarantine = new NBTTagCompound();
        quarantine.setInteger(TAG_DATA_VERSION, CURRENT_QUARANTINE_DATA_VERSION);
        NBTTagList entryList = new NBTTagList();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    entryList.appendTag(entry.copy());
                }
            }
        }
        quarantine.setTag(TAG_QUARANTINE_ENTRIES, entryList);
        return quarantine;
    }

    private static QuarantineReadResult readQuarantine(NBTTagCompound root) {
        if (!root.hasKey(TAG_QUARANTINE)) {
            return QuarantineReadResult.success(Collections.<NBTTagCompound>emptyList(), false);
        }
        if (!root.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND)) {
            return QuarantineReadResult.unsupported(-1);
        }

        NBTTagCompound source = root.getCompoundTag(TAG_QUARANTINE);
        NBTTagCompound quarantine = (NBTTagCompound) source.copy();
        int version = quarantine.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? quarantine.getInteger(TAG_DATA_VERSION)
                : 0;
        if (version < 0 || version > CURRENT_QUARANTINE_DATA_VERSION) {
            return QuarantineReadResult.unsupported(version);
        }

        boolean repaired = false;
        if (version == 0) {
            version = 1;
            quarantine.setInteger(TAG_DATA_VERSION, version);
            repaired = true;
        }
        if (version != CURRENT_QUARANTINE_DATA_VERSION) {
            return QuarantineReadResult.unsupported(version);
        }
        if (quarantine.hasKey(TAG_QUARANTINE_ENTRIES)
                && !quarantine.hasKey(TAG_QUARANTINE_ENTRIES, Constants.NBT.TAG_LIST)) {
            return QuarantineReadResult.unsupported(-1);
        }

        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        NBTTagList entryList = quarantine.getTagList(
                TAG_QUARANTINE_ENTRIES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < entryList.tagCount(); i++) {
            entries.add((NBTTagCompound) entryList.getCompoundTagAt(i).copy());
        }
        return QuarantineReadResult.success(entries, repaired);
    }

    private static NBTTagCompound writeRoster(CharacterRoster roster) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, CharacterRoster.CURRENT_DATA_VERSION);
        writeUuid(tag, TAG_OWNER_UUID, roster.getOwnerId());
        tag.setInteger(TAG_UNLOCKED_SLOT_COUNT, roster.getUnlockedSlotCount());
        tag.setLong(TAG_REVISION, roster.getRevision());
        if (roster.getActiveCharacterId() != null) {
            writeUuid(tag, TAG_ACTIVE_CHARACTER_UUID, roster.getActiveCharacterId());
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
        writeUuid(tag, TAG_CHARACTER_UUID, character.getCharacterId());
        writeUuid(tag, TAG_OWNER_UUID, character.getOwnerId());
        tag.setInteger(TAG_SLOT_INDEX, character.getSlotIndex());
        tag.setString(TAG_NAME, character.getName());
        tag.setString(TAG_RACE_ID, character.getRaceId());
        tag.setString(TAG_GENDER_ID, character.getGenderId());
        tag.setString(TAG_SKIN_ID, character.getSkinId());
        tag.setString(TAG_DESCRIPTION, character.getDescription());
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
        CharacterDataMigrator.MigrationResult migration =
                CharacterDataMigrator.migrateRoster(source, CharacterRoster.CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            warn("Skipping malformed roster at index %d", Integer.valueOf(rosterIndex));
            return RosterReadResult.failed(true, "malformed_roster");
        }
        if (!migration.isSupported()) {
            warn("Roster at index %d uses unsupported version %d",
                    Integer.valueOf(rosterIndex), Integer.valueOf(migration.getVersion()));
            return RosterReadResult.unsupported(migration.getVersion());
        }

        NBTTagCompound tag = migration.getTag();
        boolean repaired = migration.wasMigrated();
        UUID ownerId = readUuid(tag, TAG_OWNER_UUID);
        if (ownerId == null) {
            warn("Skipping roster at index %d because its owner UUID is missing or invalid",
                    Integer.valueOf(rosterIndex));
            return RosterReadResult.failed(true, "missing_or_invalid_owner_uuid");
        }

        boolean hasUnlockedSlotCount = tag.hasKey(TAG_UNLOCKED_SLOT_COUNT, Constants.NBT.TAG_INT);
        int unlockedSlotCount = hasUnlockedSlotCount
                ? tag.getInteger(TAG_UNLOCKED_SLOT_COUNT)
                : CharacterRoster.INITIAL_UNLOCKED_SLOTS;
        if (!hasUnlockedSlotCount) {
            repaired = true;
            warn("Repairing missing unlocked slot count for owner %s", ownerId);
        }
        if (unlockedSlotCount < CharacterRoster.INITIAL_UNLOCKED_SLOTS
                || unlockedSlotCount > CharacterRoster.MAX_SLOTS) {
            repaired = true;
            warn("Repairing unlocked slot count %d for owner %s",
                    Integer.valueOf(unlockedSlotCount), ownerId);
        }

        boolean hasRevision = tag.hasKey(TAG_REVISION, Constants.NBT.TAG_LONG);
        long revision = hasRevision ? tag.getLong(TAG_REVISION) : 0L;
        if (!hasRevision) {
            repaired = true;
            warn("Repairing missing roster revision for owner %s", ownerId);
        }
        if (revision < 0L) {
            revision = 0L;
            repaired = true;
            warn("Repairing negative roster revision for owner %s", ownerId);
        }

        UUID activeCharacterId = readUuid(tag, TAG_ACTIVE_CHARACTER_UUID);
        CharacterRoster roster = new CharacterRoster(
                ownerId,
                unlockedSlotCount,
                activeCharacterId,
                revision,
                CharacterRoster.CURRENT_DATA_VERSION
        );

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
                warn("Quarantining duplicate character UUID or occupied slot for owner %s, character %s, slot %d",
                        ownerId, character.getCharacterId(), Integer.valueOf(character.getSlotIndex()));
            }
        }

        int beforeNormalizedUnlockedCount = roster.getUnlockedSlotCount();
        roster.setUnlockedSlotCount(unlockedSlotCount);
        if (roster.getUnlockedSlotCount() != beforeNormalizedUnlockedCount
                || roster.getUnlockedSlotCount() != clampUnlockedSlotCount(unlockedSlotCount)) {
            repaired = true;
            warn("Expanded unlocked slots for owner %s to preserve occupied slots", ownerId);
        }

        if (roster.getActiveCharacterId() != null && roster.getActiveCharacter() == null) {
            repaired = true;
            warn("Clearing invalid active character %s for owner %s",
                    roster.getActiveCharacterId(), ownerId);
            roster.clearInvalidActiveCharacter();
        }

        return RosterReadResult.success(roster, repaired, quarantinedEntries);
    }

    private static CharacterReadResult readCharacter(NBTTagCompound source, UUID rosterOwnerId,
                                                       int rosterIndex, int characterIndex) {
        CharacterDataMigrator.MigrationResult migration =
                CharacterDataMigrator.migrateCharacter(source, RoleplayCharacter.CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            warn("Skipping malformed character at index %d for owner %s",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed(true, "malformed_character");
        }
        if (!migration.isSupported()) {
            warn("Character at index %d for owner %s uses unsupported version %d",
                    Integer.valueOf(characterIndex), rosterOwnerId,
                    Integer.valueOf(migration.getVersion()));
            return CharacterReadResult.unsupported(migration.getVersion());
        }

        NBTTagCompound tag = migration.getTag();
        boolean repaired = migration.wasMigrated();
        ArrayList<NBTTagCompound> quarantinedEntries = new ArrayList<NBTTagCompound>();
        UUID characterId = readUuid(tag, TAG_CHARACTER_UUID);
        if (characterId == null) {
            warn("Skipping character at index %d for owner %s because its UUID is missing or invalid",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed(true, "missing_or_invalid_character_uuid");
        }

        UUID characterOwnerId = readUuid(tag, TAG_OWNER_UUID);
        if (characterOwnerId == null) {
            characterOwnerId = rosterOwnerId;
            repaired = true;
            warn("Repairing missing owner UUID for character %s using roster owner %s",
                    characterId, rosterOwnerId);
        } else if (!rosterOwnerId.equals(characterOwnerId)) {
            warn("Skipping character %s because owner %s does not match roster owner %s",
                    characterId, characterOwnerId, rosterOwnerId);
            return CharacterReadResult.failed(true, "owner_uuid_mismatch");
        }

        if (!tag.hasKey(TAG_SLOT_INDEX, Constants.NBT.TAG_INT)) {
            warn("Skipping character %s for owner %s because its slot index is missing",
                    characterId, rosterOwnerId);
            return CharacterReadResult.failed(true, "missing_slot_index");
        }
        int slotIndex = tag.getInteger(TAG_SLOT_INDEX);
        if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
            warn("Skipping character %s for owner %s because slot %d is invalid",
                    characterId, rosterOwnerId, Integer.valueOf(slotIndex));
            return CharacterReadResult.failed(true, "invalid_slot_index");
        }

        String name = tag.getString(TAG_NAME);
        String storedRaceId = tag.getString(TAG_RACE_ID);
        String raceId = CharacterRaceRegistry.canonicalizeIdentifier(storedRaceId);
        if (CharacterRaceRegistry.get(raceId) == null) {
            raceId = CharacterRaceRegistry.HUMAN;
            repaired = true;
            warn("Repairing unknown race %s to safe fallback %s for character %s owned by %s",
                    storedRaceId, raceId, characterId, rosterOwnerId);
        }
        String storedGenderId = CharacterGenderRegistry.normalizeIdentifier(
                tag.getString(TAG_GENDER_ID));
        String genderId = CharacterRaceRegistry.normalizeGenderForRace(
                raceId, storedGenderId);
        String startingFactionId = tag.getString(TAG_STARTING_FACTION_ID);
        if (isBlank(name) || isBlank(raceId) || isBlank(genderId) || isBlank(startingFactionId)) {
            warn("Skipping character %s for owner %s because a required text field is empty or unsupported",
                    characterId, rosterOwnerId);
            return CharacterReadResult.failed(true, "missing_required_text_field");
        }
        if (!raceId.equals(storedRaceId)) {
            repaired = true;
            warn("Migrating legacy race %s to %s for character %s owned by %s",
                    storedRaceId, raceId, characterId, rosterOwnerId);
        }
        if (!genderId.equals(storedGenderId)) {
            repaired = true;
            warn("Repairing gender %s to %s for race %s on character %s owned by %s",
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
            warn("Clearing malformed starting waypoint for character %s owned by %s",
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

        String skinId = CharacterSkinRegistry.normalizeIdentifier(
                tag.getString(TAG_SKIN_ID));
        if (!CharacterSkinRegistry.isCompatible(skinId, raceId, genderId)) {
            skinId = CharacterSkinRegistry.getDefaultSkinId(raceId, genderId, characterId);
            repaired = true;
            warn("Assigning a compatible LOTR skin to character %s owned by %s",
                    characterId, rosterOwnerId);
        }

        boolean hasDescription = tag.hasKey(
                TAG_DESCRIPTION, Constants.NBT.TAG_STRING);
        String storedDescription = hasDescription
                ? tag.getString(TAG_DESCRIPTION) : "";
        String description = storedDescription.length()
                > CharacterValidator.MAX_DESCRIPTION_LENGTH * 2
                ? "" : CharacterValidator.normalizeDescription(
                        storedDescription);
        if (!CharacterValidator.isValidDescription(description)) {
            description = "";
        }
        if (!hasDescription || !description.equals(storedDescription)) {
            repaired = true;
            warn("Repairing character description for %s owned by %s",
                    characterId, rosterOwnerId);
        }

        boolean hasShowMinecraftCape = tag.hasKey(
                TAG_SHOW_MINECRAFT_CAPE, Constants.NBT.TAG_BYTE);
        boolean showMinecraftCape = hasShowMinecraftCape
                ? tag.getBoolean(TAG_SHOW_MINECRAFT_CAPE)
                : RoleplayCharacter.DEFAULT_SHOW_MINECRAFT_CAPE;
        if (!hasShowMinecraftCape) {
            repaired = true;
            warn("Assigning the default normal-cape visibility to character %s owned by %s",
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
                warn("Clearing invalid cosmetic cape ID %d for character %s owned by %s",
                        Integer.valueOf(storedCosmeticCapeId), characterId, rosterOwnerId);
            } else {
                warn("Assigning no cosmetic cape to character %s owned by %s",
                        characterId, rosterOwnerId);
            }
        }

        boolean hasAge = tag.hasKey(TAG_AGE, Constants.NBT.TAG_INT);
        int age = hasAge ? tag.getInteger(TAG_AGE) : 1;
        if (!hasAge) {
            repaired = true;
            warn("Repairing missing age for character %s owned by %s", characterId, rosterOwnerId);
        } else if (age < 1) {
            age = 1;
            repaired = true;
            warn("Repairing non-positive age for character %s owned by %s", characterId, rosterOwnerId);
        } else if (age > MAX_REASONABLE_AGE) {
            age = MAX_REASONABLE_AGE;
            repaired = true;
            warn("Clamping unreasonable age for character %s owned by %s", characterId, rosterOwnerId);
        }

        boolean hasRoleplayLevel = tag.hasKey(TAG_ROLEPLAY_LEVEL, Constants.NBT.TAG_INT);
        int roleplayLevel = hasRoleplayLevel
                ? tag.getInteger(TAG_ROLEPLAY_LEVEL)
                : RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL;
        if (!hasRoleplayLevel) {
            repaired = true;
            warn("Repairing missing roleplay level for character %s owned by %s",
                    characterId, rosterOwnerId);
        } else if (roleplayLevel < RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL) {
            roleplayLevel = RoleplayCharacter.INITIAL_ROLEPLAY_LEVEL;
            repaired = true;
            warn("Repairing invalid roleplay level for character %s owned by %s",
                    characterId, rosterOwnerId);
        }

        boolean hasCreationTimestamp = tag.hasKey(TAG_CREATION_TIMESTAMP, Constants.NBT.TAG_LONG);
        long creationTimestamp = hasCreationTimestamp ? tag.getLong(TAG_CREATION_TIMESTAMP) : 0L;
        if (!hasCreationTimestamp) {
            repaired = true;
            warn("Repairing missing creation timestamp for character %s owned by %s",
                    characterId, rosterOwnerId);
        }
        if (creationTimestamp < 0L) {
            creationTimestamp = 0L;
            repaired = true;
            warn("Repairing negative creation timestamp for character %s owned by %s",
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

        RoleplayCharacter character = new RoleplayCharacter(
                characterId,
                characterOwnerId,
                slotIndex,
                name,
                raceId,
                genderId,
                skinId,
                age,
                startingFactionId,
                roleplayLevel,
                progressionResult.progression,
                creationTimestamp,
                RoleplayCharacter.CURRENT_DATA_VERSION,
                showMinecraftCape,
                cosmeticCapeId,
                startingWaypointId,
                unconventionalSettings,
                description
        );
        return CharacterReadResult.success(character, repaired, quarantinedEntries);
    }

    private static ProgressionReadResult readProgression(NBTTagCompound source, UUID characterId,
                                                          UUID ownerId, boolean wasPresent) {
        if (!wasPresent) {
            warn("Creating missing progression container for character %s owned by %s",
                    characterId, ownerId);
            return ProgressionReadResult.success(new CharacterProgression(), true, false);
        }

        CharacterDataMigrator.MigrationResult migration =
                CharacterDataMigrator.migrateProgression(source, CharacterProgression.CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            warn("Replacing malformed progression data for character %s owned by %s",
                    characterId, ownerId);
            return ProgressionReadResult.success(new CharacterProgression(), true, true);
        }
        if (!migration.isSupported()) {
            warn("Progression data for character %s owned by %s uses unsupported version %d",
                    characterId, ownerId, Integer.valueOf(migration.getVersion()));
            return ProgressionReadResult.unsupported(migration.getVersion());
        }

        NBTTagCompound tag = migration.getTag();
        boolean repaired = migration.wasMigrated();
        long experiencePoints = tag.hasKey(TAG_EXPERIENCE_POINTS, Constants.NBT.TAG_LONG)
                ? tag.getLong(TAG_EXPERIENCE_POINTS)
                : 0L;
        if (experiencePoints < 0L) {
            experiencePoints = 0L;
            repaired = true;
            warn("Repairing negative experience for character %s owned by %s", characterId, ownerId);
        }

        boolean extensionTagPresent = tag.hasKey(TAG_EXTENSION_DATA);
        boolean hasExtensionData = tag.hasKey(TAG_EXTENSION_DATA, Constants.NBT.TAG_COMPOUND);
        boolean replacedMalformedData = extensionTagPresent && !hasExtensionData;
        NBTTagCompound extensionData = hasExtensionData
                ? tag.getCompoundTag(TAG_EXTENSION_DATA)
                : new NBTTagCompound();
        if (!hasExtensionData) {
            repaired = true;
            warn("Repairing missing or malformed progression extension data for character %s owned by %s",
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
            writeUuid(entry, TAG_OWNER_UUID, ownerId);
        }
        if (characterId != null) {
            writeUuid(entry, TAG_CHARACTER_UUID, characterId);
        }
        entry.setTag(TAG_ORIGINAL_DATA, originalData == null
                ? new NBTTagCompound()
                : originalData.copy());
        return entry;
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID uuid) {
        tag.setLong(key + "Most", uuid.getMostSignificantBits());
        tag.setLong(key + "Least", uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        String mostKey = key + "Most";
        String leastKey = key + "Least";
        if (!tag.hasKey(mostKey, Constants.NBT.TAG_LONG)
                || !tag.hasKey(leastKey, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(mostKey), tag.getLong(leastKey));
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

    private static void warn(String message, Object... arguments) {
        Object[] allArguments = new Object[arguments.length + 1];
        allArguments[0] = LostTalesMetaData.MOD_ID;
        System.arraycopy(arguments, 0, allArguments, 1, arguments.length);
        try {
            FMLLog.warning("[%s] " + message, allArguments);
        } catch (Throwable ignored) {
            // Persistence validation must never fail merely because Forge's
            // logger has not yet been bootstrapped (for example in standalone
            // recovery tools and the dependency-free validation harness).
        }
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

    private static final class QuarantineReadResult {
        private final List<NBTTagCompound> entries;
        private final boolean repaired;
        private final boolean supported;
        private final int unsupportedVersion;

        private QuarantineReadResult(Collection<NBTTagCompound> entries, boolean repaired,
                                     boolean supported, int unsupportedVersion) {
            this.entries = copyQuarantineEntries(entries);
            this.repaired = repaired;
            this.supported = supported;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static QuarantineReadResult success(Collection<NBTTagCompound> entries,
                                                    boolean repaired) {
            return new QuarantineReadResult(entries, repaired, true, -1);
        }

        private static QuarantineReadResult unsupported(int version) {
            return new QuarantineReadResult(Collections.<NBTTagCompound>emptyList(),
                    false, false, version);
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
