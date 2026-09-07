package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterChestTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The world store for character rosters fails closed. Data it cannot read
 * is handed back exactly as it was found, an entry it must reject is kept
 * in the quarantine with its original tag, and a field it can repair is
 * repaired to a deterministic value rather than costing the character.
 */
public final class CharacterWorldDataTest {

    private static final UUID OWNER_A =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID OWNER_B =
            UUID.fromString("20000000-0000-0000-0000-000000000002");
    private static final UUID CHARACTER_A =
            UUID.fromString("30000000-0000-0000-0000-000000000003");
    private static final UUID CHARACTER_B =
            UUID.fromString("40000000-0000-0000-0000-000000000004");
    private static final UUID CHARACTER_C =
            UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID STRANGER =
            UUID.fromString("60000000-0000-0000-0000-000000000006");

    @Test
    public void aStoreWrittenAndReadBackNeedsNoRepair() {
        NBTTagCompound saved = savedRosters();

        CharacterWorldData loaded = load(saved);

        assertEquals(CharacterNbtCodec.CURRENT_ROOT_DATA_VERSION,
                saved.getInteger("DataVersion"));
        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertFalse(loaded.isDirty());
        assertEquals(2, loaded.getRosterCount());
        assertEquals(0, loaded.getQuarantinedEntryCount());
        assertNotNull(loaded.findCharacter(CHARACTER_A));
        assertNotNull(loaded.findCharacter(CHARACTER_B));
    }

    @Test
    public void aFileFromANewerBuildIsWrittenBackUnchanged() {
        NBTTagCompound saved = savedRosters();
        saved.setInteger("DataVersion",
                CharacterNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        NBTTagCompound expected = (NBTTagCompound) saved.copy();

        CharacterWorldData loaded = load(saved);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertEquals(CharacterNbtCodec.CURRENT_ROOT_DATA_VERSION + 1,
                loaded.getUnsupportedDataVersion());
        assertEquals(0, loaded.getRosterCount());
        assertEquals(expected, written);
    }

    @Test
    public void aRootWithANegativeVersionIsWrittenBackUnchanged() {
        NBTTagCompound saved = savedRosters();
        saved.setInteger("DataVersion", -1);
        NBTTagCompound expected = (NBTTagCompound) saved.copy();

        CharacterWorldData loaded = load(saved);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertEquals(0, loaded.getRosterCount());
        assertEquals(expected, written);
    }

    /** A newer quarantine holds records this build cannot read either. */
    @Test
    public void aNewerQuarantineHoldsTheWholeStoreReadOnly() {
        NBTTagCompound saved = savedRosters();
        saved.getCompoundTag("Quarantine").setInteger("DataVersion",
                CharacterNbtCodec.CURRENT_QUARANTINE_DATA_VERSION + 1);
        NBTTagCompound expected = (NBTTagCompound) saved.copy();

        CharacterWorldData loaded = load(saved);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertEquals(CharacterNbtCodec.CURRENT_QUARANTINE_DATA_VERSION + 1,
                loaded.getUnsupportedDataVersion());
        assertEquals(0, loaded.getRosterCount());
        assertEquals(expected, written);
    }

    /** One character from a newer build protects every roster beside it. */
    @Test
    public void aCharacterFromANewerBuildHoldsTheWholeStoreReadOnly() {
        NBTTagCompound saved = savedRosters();
        firstCharacterOf(saved).setInteger("DataVersion",
                RoleplayCharacter.CURRENT_DATA_VERSION + 1);
        NBTTagCompound expected = (NBTTagCompound) saved.copy();

        CharacterWorldData loaded = load(saved);
        NBTTagCompound written = new NBTTagCompound();
        loaded.writeToNBT(written);

        assertTrue(loaded.isReadOnlyForNewerVersion());
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION + 1,
                loaded.getUnsupportedDataVersion());
        assertEquals(0, loaded.getRosterCount());
        assertEquals(expected, written);
    }

    @Test
    public void everyMutatorRefusesAStoreHeldReadOnly() {
        NBTTagCompound saved = savedRosters();
        saved.setInteger("DataVersion",
                CharacterNbtCodec.CURRENT_ROOT_DATA_VERSION + 1);
        CharacterWorldData loaded = load(saved);

        try {
            loaded.getOrCreateRoster(OWNER_A);
            fail("getOrCreateRoster must refuse a read-only store");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("read-only"));
        }
        try {
            loaded.saveRoster(new CharacterRoster(OWNER_A));
            fail("saveRoster must refuse a read-only store");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("read-only"));
        }
        assertEquals(0, loaded.getRosterCount());
    }

    @Test
    public void aRosterWithoutAnOwnerIsQuarantinedWithItsOriginalTag() {
        NBTTagCompound saved = savedRosters();
        NBTTagCompound broken = rostersOf(saved).getCompoundTagAt(0);
        assertEquals(OWNER_A.getMostSignificantBits(),
                broken.getLong("OwnerUUIDMost"));
        broken.removeTag("OwnerUUIDLeast");
        NBTTagCompound original = (NBTTagCompound) broken.copy();

        CharacterWorldData loaded = load(saved);

        assertFalse(loaded.isReadOnlyForNewerVersion());
        assertTrue(loaded.isDirty());
        assertEquals(1, loaded.getRosterCount());
        assertNull(loaded.getRoster(OWNER_A));
        assertNotNull(loaded.getRoster(OWNER_B));
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound entry = loaded.getQuarantinedEntriesCopy().get(0);
        assertEquals("roster", entry.getString("EntryType"));
        assertEquals("missing_or_invalid_owner_uuid", entry.getString("Reason"));
        assertEquals(0, entry.getInteger("RosterIndex"));
        assertEquals(original, entry.getCompoundTag("OriginalData"));
    }

    @Test
    public void aDuplicateRosterIsQuarantinedAndTheFirstOneKept() {
        NBTTagCompound saved = savedRosters();
        NBTTagList rosters = rostersOf(saved);
        rosters.appendTag(rosters.getCompoundTagAt(0).copy());

        CharacterWorldData loaded = load(saved);

        assertEquals(2, loaded.getRosterCount());
        assertNotNull(loaded.getRoster(OWNER_A));
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound entry = loaded.getQuarantinedEntriesCopy().get(0);
        assertEquals("roster", entry.getString("EntryType"));
        assertEquals("duplicate_roster_owner", entry.getString("Reason"));
        assertEquals(2, entry.getInteger("RosterIndex"));
        assertEquals(OWNER_A.getMostSignificantBits(),
                entry.getLong("OwnerUUIDMost"));
        assertEquals(OWNER_A.getLeastSignificantBits(),
                entry.getLong("OwnerUUIDLeast"));
    }

    /** A character may only be read into the roster whose owner it names. */
    @Test
    public void aCharacterOwnedByAnotherAccountIsQuarantined() {
        NBTTagCompound saved = savedRosters();
        NBTTagCompound characterTag = firstCharacterOf(saved);
        characterTag.setLong("OwnerUUIDMost", STRANGER.getMostSignificantBits());
        NBTTagCompound original = (NBTTagCompound) characterTag.copy();

        CharacterWorldData loaded = load(saved);

        assertEquals(2, loaded.getRosterCount());
        assertNotNull(loaded.getRoster(OWNER_A));
        assertEquals(0, loaded.getRoster(OWNER_A).getCharacterCount());
        assertNull(loaded.findCharacter(CHARACTER_A));
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound entry = loaded.getQuarantinedEntriesCopy().get(0);
        assertEquals("character", entry.getString("EntryType"));
        assertEquals("owner_uuid_mismatch", entry.getString("Reason"));
        assertEquals(0, entry.getInteger("RosterIndex"));
        assertEquals(0, entry.getInteger("CharacterIndex"));
        assertEquals(OWNER_A.getMostSignificantBits(),
                entry.getLong("OwnerUUIDMost"));
        assertFalse(entry.hasKey("CharacterUUIDMost"));
        assertEquals(original, entry.getCompoundTag("OriginalData"));
    }

    @Test
    public void aDuplicateCharacterIsQuarantinedAndTheFirstOneKept() {
        NBTTagCompound saved = savedRosters();
        NBTTagList characters = charactersOf(rostersOf(saved).getCompoundTagAt(0));
        characters.appendTag(characters.getCompoundTagAt(0).copy());

        CharacterWorldData loaded = load(saved);

        assertNotNull(loaded.findCharacter(CHARACTER_A));
        assertEquals(1, loaded.getRoster(OWNER_A).getCharacterCount());
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound entry = loaded.getQuarantinedEntriesCopy().get(0);
        assertEquals("character", entry.getString("EntryType"));
        assertEquals("duplicate_character_uuid_or_occupied_slot",
                entry.getString("Reason"));
        assertEquals(0, entry.getInteger("RosterIndex"));
        assertEquals(1, entry.getInteger("CharacterIndex"));
        assertEquals(CHARACTER_A.getMostSignificantBits(),
                entry.getLong("CharacterUUIDMost"));
    }

    /** A quarantined record stays in the file the next time it is saved. */
    @Test
    public void quarantinedEntriesSurviveTheNextSave() {
        NBTTagCompound saved = savedRosters();
        firstCharacterOf(saved).removeTag("SlotIndex");

        CharacterWorldData loaded = load(saved);
        assertEquals(1, loaded.getQuarantinedEntryCount());
        NBTTagCompound rewritten = new NBTTagCompound();
        loaded.writeToNBT(rewritten);
        CharacterWorldData reloaded = load(rewritten);

        assertEquals(1, reloaded.getQuarantinedEntryCount());
        NBTTagCompound entry = reloaded.getQuarantinedEntriesCopy().get(0);
        assertEquals("missing_slot_index", entry.getString("Reason"));
        assertEquals(loaded.getQuarantinedEntriesCopy().get(0), entry);
        assertEquals(2, reloaded.getRosterCount());
    }

    @Test
    public void anUnknownRaceIsRepairedRatherThanRejected() {
        NBTTagCompound saved = savedRosters();
        NBTTagCompound characterTag = firstCharacterOf(saved);
        String storedSkinId = characterTag.getString("SkinId");
        characterTag.setString("RaceId", "losttales:balrog");

        CharacterWorldData loaded = load(saved);

        RoleplayCharacter repaired = loaded.findCharacter(CHARACTER_A);
        assertNotNull(repaired);
        assertEquals(CharacterRaceRegistry.HUMAN, repaired.getRaceId());
        assertEquals(storedSkinId, repaired.getSkinId());
        assertEquals(0, loaded.getQuarantinedEntryCount());
        assertTrue(loaded.isDirty());
    }

    @Test
    public void anIncompatibleSkinIsRepairedToTheDeterministicDefault() {
        String elfSkinId = CharacterSkinRegistry.getDefaultSkinId(
                CharacterRaceRegistry.ELF, CharacterGenderRegistry.MALE, CHARACTER_A);
        NBTTagCompound saved = savedRosters();
        firstCharacterOf(saved).setString("SkinId", elfSkinId);

        CharacterWorldData loaded = load(saved);

        RoleplayCharacter repaired = loaded.findCharacter(CHARACTER_A);
        assertNotNull(repaired);
        assertFalse(elfSkinId.equals(repaired.getSkinId()));
        assertTrue(CharacterSkinRegistry.isCompatible(repaired.getSkinId(),
                CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.MALE));
        assertEquals(CharacterSkinRegistry.getDefaultSkinId(
                        CharacterRaceRegistry.HUMAN, CharacterGenderRegistry.MALE,
                        CHARACTER_A),
                repaired.getSkinId());
        assertEquals(0, loaded.getQuarantinedEntryCount());
    }

    /** A record written before the body and chest keys existed takes the sex's defaults. */
    @Test
    public void aMissingBodyAndChestTypeFollowTheSex() {
        CharacterWorldData data = new CharacterWorldData();
        CharacterRoster roster = data.getOrCreateRoster(OWNER_A);
        roster.addCharacter(character(OWNER_A, CHARACTER_A, 0, "Bregil",
                CharacterBodyTypeRegistry.SLIM,
                CharacterChestTypeRegistry.ROUNDED_MEDIUM));
        data.saveRoster(roster);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        NBTTagCompound characterTag = firstCharacterOf(saved);
        assertEquals(CharacterBodyTypeRegistry.SLIM,
                characterTag.getString("BodyTypeId"));
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM,
                characterTag.getString("ChestTypeId"));
        characterTag.removeTag("BodyTypeId");
        characterTag.removeTag("ChestTypeId");

        CharacterWorldData loaded = load(saved);

        RoleplayCharacter repaired = loaded.findCharacter(CHARACTER_A);
        assertNotNull(repaired);
        assertEquals(CharacterBodyTypeRegistry.WIDE, repaired.getBodyTypeId());
        assertEquals(CharacterChestTypeRegistry.NONE, repaired.getChestTypeId());
        assertEquals(0, loaded.getQuarantinedEntryCount());
        assertTrue(loaded.isDirty());
    }

    @Test
    public void anActiveCharacterTheRosterDoesNotHoldIsCleared() {
        CharacterWorldData data = new CharacterWorldData();
        CharacterRoster roster = data.getOrCreateRoster(OWNER_A);
        roster.addCharacter(character(OWNER_A, CHARACTER_A, 0, "Bregil"));
        roster.setActiveCharacterId(CHARACTER_A);
        data.saveRoster(roster);
        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        NBTTagCompound rosterTag = rostersOf(saved).getCompoundTagAt(0);
        rosterTag.setLong("ActiveCharacterUUIDMost",
                STRANGER.getMostSignificantBits());
        rosterTag.setLong("ActiveCharacterUUIDLeast",
                STRANGER.getLeastSignificantBits());

        CharacterWorldData loaded = load(saved);

        CharacterRoster restored = loaded.getRoster(OWNER_A);
        assertNotNull(restored);
        assertNull(restored.getActiveCharacterId());
        assertEquals(1, restored.getCharacterCount());
        assertTrue(loaded.isDirty());
    }

    /** The id index is dropped when a roster is written back, not before. */
    @Test
    public void theCharacterIndexIsRebuiltWhenARosterIsSaved() {
        CharacterWorldData data = load(savedRosters());
        assertNotNull(data.findCharacter(CHARACTER_A));
        assertNull(data.findCharacter(CHARACTER_C));

        CharacterRoster roster = data.getRoster(OWNER_A);
        assertTrue(roster.addCharacter(character(OWNER_A, CHARACTER_C, 1, "Erling")));
        assertNull(data.findCharacter(CHARACTER_C));
        data.saveRoster(roster);

        assertNotNull(data.findCharacter(CHARACTER_C));
        assertTrue(data.containsCharacter(CHARACTER_C));
        assertEquals(CHARACTER_C,
                data.findCharacter(CHARACTER_C).getCharacterId());
    }

    /** Two rosters, one character each, written exactly as the world saves them. */
    private static NBTTagCompound savedRosters() {
        CharacterWorldData data = new CharacterWorldData();
        CharacterRoster rosterA = data.getOrCreateRoster(OWNER_A);
        rosterA.addCharacter(character(OWNER_A, CHARACTER_A, 0, "Bregil"));
        data.saveRoster(rosterA);
        CharacterRoster rosterB = data.getOrCreateRoster(OWNER_B);
        rosterB.addCharacter(character(OWNER_B, CHARACTER_B, 0, "Halbarad"));
        data.saveRoster(rosterB);

        NBTTagCompound saved = new NBTTagCompound();
        data.writeToNBT(saved);
        return saved;
    }

    private static CharacterWorldData load(NBTTagCompound saved) {
        CharacterWorldData data = new CharacterWorldData();
        data.readFromNBT(saved);
        return data;
    }

    private static RoleplayCharacter character(UUID ownerId, UUID characterId,
                                               int slotIndex, String name) {
        return character(ownerId, characterId, slotIndex, name,
                CharacterBodyTypeRegistry.WIDE, CharacterChestTypeRegistry.NONE);
    }

    private static RoleplayCharacter character(UUID ownerId, UUID characterId,
                                               int slotIndex, String name,
                                               String bodyTypeId, String chestTypeId) {
        return RoleplayCharacter.builder(characterId, ownerId)
                .slot(slotIndex).name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.getDefaultSkinId(
                        CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, characterId))
                .age(30).startingFaction("lotr:bree").createdAt(1000L)
                .bodyType(bodyTypeId).chestType(chestTypeId)
                .build();
    }

    /** The rosters sort by owner UUID, so owner A is always the first. */
    private static NBTTagList rostersOf(NBTTagCompound root) {
        return root.getTagList("Rosters", Constants.NBT.TAG_COMPOUND);
    }

    private static NBTTagList charactersOf(NBTTagCompound roster) {
        return roster.getTagList("Characters", Constants.NBT.TAG_COMPOUND);
    }

    private static NBTTagCompound firstCharacterOf(NBTTagCompound root) {
        return charactersOf(rostersOf(root).getCompoundTagAt(0)).getCompoundTagAt(0);
    }
}
