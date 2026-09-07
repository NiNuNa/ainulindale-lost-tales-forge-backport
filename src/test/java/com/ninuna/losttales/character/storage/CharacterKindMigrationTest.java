package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterKind;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Character data version 9 records which kind of identity a character is.
 * Every record written before it is a roleplay character, which is what
 * they all were, and the account's own identity reads back as the default
 * one it was written as.
 */
public final class CharacterKindMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "a0000000-0000-0000-0000-00000000000a");
    private static final UUID CHARACTER = UUID.fromString(
            "a1000000-0000-0000-0000-00000000001a");

    /** A record from before kinds existed is a roleplay character. */
    @Test
    public void versionEightRecordsAreRoleplayCharacters() {
        NBTTagCompound legacy = CharacterNbtCodec.writeCharacterRecord(roleplay());
        legacy.setInteger("DataVersion", 8);
        legacy.removeTag("Kind");

        RoleplayCharacter migrated = CharacterNbtCodec.readCharacterRecord(legacy, OWNER);

        assertNotNull(migrated);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION, migrated.getDataVersion());
        assertEquals(CharacterKind.ROLEPLAY, migrated.getKind());
        assertFalse(migrated.isDefault());
    }

    /** A kind this build does not know reads as a roleplay character. */
    @Test
    public void anUnknownKindReadsAsARoleplayCharacter() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(roleplay());
        record.setString("Kind", "losttales:something_later");

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(CharacterKind.ROLEPLAY, loaded.getKind());
    }

    /** The account's own identity round-trips as the default one. */
    @Test
    public void theDefaultCharacterRoundTripsAsItsOwnKind() {
        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(defaultCharacter()), OWNER);

        assertNotNull(loaded);
        assertEquals(CharacterKind.DEFAULT, loaded.getKind());
        assertTrue(loaded.isDefault());
        assertEquals(CharacterRoster.DEFAULT_SLOT_INDEX, loaded.getSlotIndex());
        assertEquals("its id is the account's own", OWNER, loaded.getCharacterId());
    }

    /**
     * The account belongs to no faction, exactly as it did before it was
     * a character, so its record carries none and is still read back. A
     * roleplay character with no faction is a record missing a field it
     * must have, and is skipped as it always was.
     */
    @Test
    public void onlyTheDefaultCharacterMayBelongToNoFaction() {
        NBTTagCompound withoutFaction = CharacterNbtCodec.writeCharacterRecord(
                defaultCharacter());
        assertEquals("", withoutFaction.getString("StartingFactionId"));
        assertNotNull("the account's own identity is kept",
                CharacterNbtCodec.readCharacterRecord(withoutFaction, OWNER));

        NBTTagCompound roleplayWithoutFaction =
                CharacterNbtCodec.writeCharacterRecord(roleplay());
        roleplayWithoutFaction.setString("StartingFactionId", "");
        try {
            CharacterNbtCodec.readCharacterRecord(roleplayWithoutFaction, OWNER);
            fail("a roleplay character with no faction was read back");
        } catch (IllegalArgumentException expected) {
            assertTrue("refused for the missing field, not something else",
                    expected.getMessage() != null && expected.getMessage()
                            .contains("missing_required_text_field"));
        }
    }

    /** The slot the default character sits in survives the round trip. */
    @Test
    public void theDefaultSlotIsAValidSlotAndSurvivesTheRoundTrip() {
        assertTrue(CharacterRoster.isValidSlotIndex(
                CharacterRoster.DEFAULT_SLOT_INDEX));
        assertFalse("and nothing below it is",
                CharacterRoster.isValidSlotIndex(
                        CharacterRoster.DEFAULT_SLOT_INDEX - 1));

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(
                CharacterNbtCodec.writeCharacterRecord(defaultCharacter()), OWNER);
        assertNotNull(loaded);
        assertEquals(CharacterRoster.DEFAULT_SLOT_INDEX, loaded.getSlotIndex());
    }

    /**
     * The whole store, not one detached record: a roster holding the
     * account's own identity beside a character the player made survives
     * a write and a read with both kinds, both slots and both ids intact.
     * This is the path a world actually takes on every save.
     */
    @Test
    public void aRosterHoldingBothKindsSurvivesTheStore() {
        CharacterWorldData written = new CharacterWorldData();
        CharacterRoster roster = written.getOrCreateRoster(OWNER);
        assertTrue(roster.addCharacter(defaultCharacter()));
        assertTrue(roster.addCharacter(roleplay()));
        roster.setActiveCharacterId(OWNER);
        written.saveRoster(roster);

        NBTTagCompound saved = new NBTTagCompound();
        written.writeToNBT(saved);
        CharacterWorldData reloaded = new CharacterWorldData();
        reloaded.readFromNBT(saved);

        CharacterRoster loaded = reloaded.getRoster(OWNER);
        assertNotNull(loaded);
        assertEquals(2, loaded.getCharacterCount());
        assertEquals("only one of them spends a slot",
                1, loaded.roleplayCharacterCount());

        RoleplayCharacter account = loaded.getDefaultCharacter();
        assertNotNull("the account's own identity is still there", account);
        assertEquals(CharacterKind.DEFAULT, account.getKind());
        assertEquals(OWNER, account.getCharacterId());
        assertEquals(CharacterRoster.DEFAULT_SLOT_INDEX, account.getSlotIndex());
        assertEquals("", account.getStartingFactionId());

        RoleplayCharacter made = loaded.getCharacter(CHARACTER);
        assertNotNull(made);
        assertEquals(CharacterKind.ROLEPLAY, made.getKind());
        assertEquals(0, made.getSlotIndex());

        assertEquals("and it is still the identity being played",
                OWNER, loaded.getActiveCharacterId());
    }

    private static RoleplayCharacter roleplay() {
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .slot(0)
                .name("Aldric")
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.getDefaultSkinId(
                        CharacterRaceRegistry.HUMAN,
                        CharacterGenderRegistry.MALE, CHARACTER))
                .age(30)
                .startingFaction("lotr:gondor")
                .createdAt(1000L)
                .build();
    }

    private static RoleplayCharacter defaultCharacter() {
        return RoleplayCharacter.builder(OWNER, OWNER)
                .kind(CharacterKind.DEFAULT)
                .slot(CharacterRoster.DEFAULT_SLOT_INDEX)
                .name("Steve")
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .age(1)
                .startingFaction("")
                .createdAt(1000L)
                .build();
    }
}
