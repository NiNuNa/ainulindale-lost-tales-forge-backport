package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterKind;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A character record says which kind of identity it is: the account's
 * own default character, stored as Unaligned, or a roleplay character.
 * Both kinds round-trip through the codec and the whole store, and a
 * record that names no kind is a roleplay character.
 */
public final class CharacterKindMigrationTest {

    private static final UUID OWNER = UUID.fromString(
            "a0000000-0000-0000-0000-00000000000a");
    private static final UUID CHARACTER = UUID.fromString(
            "a1000000-0000-0000-0000-00000000001a");

    /** A record naming no kind is a roleplay character. */
    @Test
    public void aRecordWithoutAKindIsARoleplayCharacter() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(roleplay());
        record.removeTag("Kind");

        RoleplayCharacter loaded = CharacterNbtCodec.readCharacterRecord(record, OWNER);

        assertNotNull(loaded);
        assertEquals(RoleplayCharacter.CURRENT_DATA_VERSION, loaded.getDataVersion());
        assertEquals(CharacterKind.ROLEPLAY, loaded.getKind());
        assertFalse(loaded.isDefault());
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

    /** A record of another data version is not read; the store stays read-only. */
    @Test
    public void aRecordOfAnotherVersionIsRefused() {
        NBTTagCompound record = CharacterNbtCodec.writeCharacterRecord(roleplay());
        record.setInteger("DataVersion", RoleplayCharacter.CURRENT_DATA_VERSION - 1);
        try {
            CharacterNbtCodec.readCharacterRecord(record, OWNER);
            fail("a record of another version was read");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("unsupported"));
        }
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
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                loaded.getStartingFactionId());
    }

    /** Every record needs a faction; the default character's is Unaligned. */
    @Test
    public void aRecordWithoutAFactionIsSkipped() {
        NBTTagCompound withoutFaction = CharacterNbtCodec.writeCharacterRecord(
                defaultCharacter());
        withoutFaction.setString("StartingFactionId", "");
        try {
            CharacterNbtCodec.readCharacterRecord(withoutFaction, OWNER);
            fail("a character with no faction was read back");
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
        assertEquals("the default character is Unaligned",
                LotrCharacterAdapter.UNALIGNED_FACTION_ID, account.getStartingFactionId());

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
                .startingFaction(LotrCharacterAdapter.UNALIGNED_FACTION_ID)
                .createdAt(1000L)
                .build();
    }
}
