package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.model.CharacterProgression;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
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

    public static final int CURRENT_ROOT_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_ROSTERS = "Rosters";
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
    private static final String TAG_AGE = "Age";
    private static final String TAG_STARTING_FACTION_ID = "StartingFactionId";
    private static final String TAG_ROLEPLAY_LEVEL = "RoleplayLevel";
    private static final String TAG_CREATION_TIMESTAMP = "CreationTimestamp";
    private static final String TAG_EXPERIENCE_POINTS = "ExperiencePoints";

    private static final int MAX_REASONABLE_AGE = 100000;

    private CharacterNbtCodec() {}

    public static void write(NBTTagCompound output, Collection<CharacterRoster> rosters) {
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
    }

    public static ReadResult read(NBTTagCompound source) {
        CharacterDataMigrator.MigrationResult rootMigration =
                CharacterDataMigrator.migrate(source, CURRENT_ROOT_DATA_VERSION);

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
        boolean repaired = rootMigration.wasMigrated();
        if (!root.hasKey(TAG_ROSTERS, Constants.NBT.TAG_LIST)) {
            repaired = true;
            warn("Character data root is missing the roster list; repairing it as empty");
        }
        LinkedHashMap<UUID, CharacterRoster> rosters = new LinkedHashMap<UUID, CharacterRoster>();
        NBTTagList rosterList = root.getTagList(TAG_ROSTERS, Constants.NBT.TAG_COMPOUND);

        for (int i = 0; i < rosterList.tagCount(); i++) {
            RosterReadResult rosterResult = readRoster(rosterList.getCompoundTagAt(i), i);
            if (rosterResult.unsupportedVersion >= 0) {
                warn("Character data contains nested unsupported version %d; the whole store will remain read-only",
                        Integer.valueOf(rosterResult.unsupportedVersion));
                return ReadResult.unsupported(source, rosterResult.unsupportedVersion);
            }
            repaired |= rosterResult.repaired;
            CharacterRoster roster = rosterResult.roster;
            if (roster == null) {
                continue;
            }
            if (rosters.containsKey(roster.getOwnerId())) {
                repaired = true;
                warn("Skipping duplicate roster for owner %s at index %d",
                        roster.getOwnerId(), Integer.valueOf(i));
                continue;
            }
            rosters.put(roster.getOwnerId(), roster);
        }

        return ReadResult.success(rosters, repaired);
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
        tag.setInteger(TAG_AGE, character.getAge());
        tag.setString(TAG_STARTING_FACTION_ID, character.getStartingFactionId());
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
                CharacterDataMigrator.migrate(source, CharacterRoster.CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            warn("Skipping malformed roster at index %d", Integer.valueOf(rosterIndex));
            return RosterReadResult.failed(true);
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
            return RosterReadResult.failed(true);
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

        NBTTagList characterList = tag.getTagList(TAG_CHARACTERS, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < characterList.tagCount(); i++) {
            CharacterReadResult characterResult = readCharacter(
                    characterList.getCompoundTagAt(i), ownerId, i);
            if (characterResult.unsupportedVersion >= 0) {
                return RosterReadResult.unsupported(characterResult.unsupportedVersion);
            }
            repaired |= characterResult.repaired;
            RoleplayCharacter character = characterResult.character;
            if (character == null) {
                continue;
            }
            if (!roster.addCharacter(character)) {
                repaired = true;
                warn("Skipping duplicate character UUID or occupied slot for owner %s, character %s, slot %d",
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

        return RosterReadResult.success(roster, repaired);
    }

    private static CharacterReadResult readCharacter(NBTTagCompound source, UUID rosterOwnerId,
                                                       int characterIndex) {
        CharacterDataMigrator.MigrationResult migration =
                CharacterDataMigrator.migrate(source, RoleplayCharacter.CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            warn("Skipping malformed character at index %d for owner %s",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed(true);
        }
        if (!migration.isSupported()) {
            warn("Character at index %d for owner %s uses unsupported version %d",
                    Integer.valueOf(characterIndex), rosterOwnerId,
                    Integer.valueOf(migration.getVersion()));
            return CharacterReadResult.unsupported(migration.getVersion());
        }

        NBTTagCompound tag = migration.getTag();
        boolean repaired = migration.wasMigrated();
        UUID characterId = readUuid(tag, TAG_CHARACTER_UUID);
        if (characterId == null) {
            warn("Skipping character at index %d for owner %s because its UUID is missing or invalid",
                    Integer.valueOf(characterIndex), rosterOwnerId);
            return CharacterReadResult.failed(true);
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
            return CharacterReadResult.failed(true);
        }

        if (!tag.hasKey(TAG_SLOT_INDEX, Constants.NBT.TAG_INT)) {
            warn("Skipping character %s for owner %s because its slot index is missing",
                    characterId, rosterOwnerId);
            return CharacterReadResult.failed(true);
        }
        int slotIndex = tag.getInteger(TAG_SLOT_INDEX);
        if (!CharacterRoster.isValidSlotIndex(slotIndex)) {
            warn("Skipping character %s for owner %s because slot %d is invalid",
                    characterId, rosterOwnerId, Integer.valueOf(slotIndex));
            return CharacterReadResult.failed(true);
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
            return CharacterReadResult.failed(true);
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

        String skinId = CharacterSkinRegistry.normalizeIdentifier(
                tag.getString(TAG_SKIN_ID));
        if (!CharacterSkinRegistry.isCompatible(skinId, raceId, genderId)) {
            skinId = CharacterSkinRegistry.getDefaultSkinId(raceId, genderId, characterId);
            repaired = true;
            warn("Assigning a compatible LOTR skin to character %s owned by %s",
                    characterId, rosterOwnerId);
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

        ProgressionReadResult progressionResult = readProgression(
                tag.getCompoundTag(TAG_PROGRESSION), characterId, rosterOwnerId,
                tag.hasKey(TAG_PROGRESSION, Constants.NBT.TAG_COMPOUND));
        if (progressionResult.unsupportedVersion >= 0) {
            return CharacterReadResult.unsupported(progressionResult.unsupportedVersion);
        }
        repaired |= progressionResult.repaired;

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
                RoleplayCharacter.CURRENT_DATA_VERSION
        );
        return CharacterReadResult.success(character, repaired);
    }

    private static ProgressionReadResult readProgression(NBTTagCompound source, UUID characterId,
                                                          UUID ownerId, boolean wasPresent) {
        if (!wasPresent) {
            warn("Creating missing progression container for character %s owned by %s",
                    characterId, ownerId);
            return ProgressionReadResult.success(new CharacterProgression(), true);
        }

        CharacterDataMigrator.MigrationResult migration =
                CharacterDataMigrator.migrate(source, CharacterProgression.CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            warn("Replacing malformed progression data for character %s owned by %s",
                    characterId, ownerId);
            return ProgressionReadResult.success(new CharacterProgression(), true);
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

        boolean hasExtensionData = tag.hasKey(TAG_EXTENSION_DATA, Constants.NBT.TAG_COMPOUND);
        NBTTagCompound extensionData = hasExtensionData
                ? tag.getCompoundTag(TAG_EXTENSION_DATA)
                : new NBTTagCompound();
        if (!hasExtensionData) {
            repaired = true;
            warn("Repairing missing progression extension data for character %s owned by %s",
                    characterId, ownerId);
        }
        CharacterProgression progression = new CharacterProgression(
                CharacterProgression.CURRENT_DATA_VERSION,
                experiencePoints,
                extensionData
        );
        return ProgressionReadResult.success(progression, repaired);
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

    private static void warn(String message, Object... arguments) {
        Object[] allArguments = new Object[arguments.length + 1];
        allArguments[0] = LostTalesMetaData.MOD_ID;
        System.arraycopy(arguments, 0, allArguments, 1, arguments.length);
        FMLLog.warning("[%s] " + message, allArguments);
    }

    public static final class ReadResult {
        private final Map<UUID, CharacterRoster> rosters;
        private final boolean repaired;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, CharacterRoster> rosters, boolean repaired,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.rosters = rosters;
            this.repaired = repaired;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        public static ReadResult success(Map<UUID, CharacterRoster> rosters, boolean repaired) {
            return new ReadResult(rosters, repaired, false, -1, null);
        }

        public static ReadResult empty(boolean repaired) {
            return success(new LinkedHashMap<UUID, CharacterRoster>(), repaired);
        }

        public static ReadResult unsupported(NBTTagCompound originalData, int unsupportedVersion) {
            NBTTagCompound copy = originalData == null
                    ? new NBTTagCompound()
                    : (NBTTagCompound) originalData.copy();
            return new ReadResult(new LinkedHashMap<UUID, CharacterRoster>(), false,
                    true, unsupportedVersion, copy);
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
    }

    private static final class RosterReadResult {
        private final CharacterRoster roster;
        private final boolean repaired;
        private final int unsupportedVersion;

        private RosterReadResult(CharacterRoster roster, boolean repaired, int unsupportedVersion) {
            this.roster = roster;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static RosterReadResult success(CharacterRoster roster, boolean repaired) {
            return new RosterReadResult(roster, repaired, -1);
        }

        private static RosterReadResult failed(boolean repaired) {
            return new RosterReadResult(null, repaired, -1);
        }

        private static RosterReadResult unsupported(int version) {
            return new RosterReadResult(null, false, version);
        }
    }

    private static final class CharacterReadResult {
        private final RoleplayCharacter character;
        private final boolean repaired;
        private final int unsupportedVersion;

        private CharacterReadResult(RoleplayCharacter character, boolean repaired, int unsupportedVersion) {
            this.character = character;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static CharacterReadResult success(RoleplayCharacter character, boolean repaired) {
            return new CharacterReadResult(character, repaired, -1);
        }

        private static CharacterReadResult failed(boolean repaired) {
            return new CharacterReadResult(null, repaired, -1);
        }

        private static CharacterReadResult unsupported(int version) {
            return new CharacterReadResult(null, false, version);
        }
    }

    private static final class ProgressionReadResult {
        private final CharacterProgression progression;
        private final boolean repaired;
        private final int unsupportedVersion;

        private ProgressionReadResult(CharacterProgression progression, boolean repaired,
                                      int unsupportedVersion) {
            this.progression = progression;
            this.repaired = repaired;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static ProgressionReadResult success(CharacterProgression progression,
                                                     boolean repaired) {
            return new ProgressionReadResult(progression, repaired, -1);
        }

        private static ProgressionReadResult unsupported(int version) {
            return new ProgressionReadResult(null, false, version);
        }
    }
}
