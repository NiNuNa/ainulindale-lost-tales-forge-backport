package com.ninuna.losttales.character.model;

import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import org.junit.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The account's own identity in the roster. It sits outside the nine
 * slots a player fills, is shown before them, and is counted against
 * nothing — a player with a full roster still has it, and a player with
 * only it can still make nine.
 */
public final class CharacterRosterDefaultCharacterTest {

    private static final UUID OWNER = UUID.fromString(
            "b0000000-0000-0000-0000-00000000000b");

    @Test
    public void aFreshRosterHasNoDefaultCharacterYet() {
        assertNull(new CharacterRoster(OWNER).getDefaultCharacter());
    }

    /**
     * The default character carries the account's own id. That is what
     * lets a world that already existed gain one without moving anything:
     * party membership, markers and saved player state are all filed
     * under the gameplay id, which for the account was this same value.
     */
    @Test
    public void theDefaultCharacterIsFoundByItsOwnAccountId() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        RoleplayCharacter account = defaultCharacter();
        assertTrue(roster.addCharacter(account));

        assertSame(account, roster.getDefaultCharacter());
        assertSame("and by the account's id, which is its own",
                account, roster.getCharacter(OWNER));
    }

    /** It is shown before every character the player made. */
    @Test
    public void theDefaultCharacterComesFirst() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        roster.addCharacter(roleplay(0, "Aldric"));
        roster.addCharacter(roleplay(1, "Beren"));
        roster.addCharacter(defaultCharacter());

        List<RoleplayCharacter> characters = roster.getCharacters();
        assertEquals(3, characters.size());
        assertTrue("the account's own identity is first",
                characters.get(0).isDefault());
        assertEquals("Aldric", characters.get(1).getName());
        assertEquals("Beren", characters.get(2).getName());
    }

    /** It is not one of the nine, so it costs the player nothing. */
    @Test
    public void theDefaultCharacterDoesNotSpendOneOfTheNineSlots() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertTrue(roster.addCharacter(defaultCharacter()));
        assertEquals(0, roster.roleplayCharacterCount());

        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            assertTrue("slot " + slot + " is still there",
                    roster.addCharacter(roleplay(slot, "Character" + slot)));
        }

        assertEquals(CharacterRoster.MAX_SLOTS, roster.roleplayCharacterCount());
        assertEquals("the account's own identity rides beside them",
                CharacterRoster.MAX_SLOTS + 1, roster.getCharacterCount());
    }

    /** The nine are still nine once the default character is there. */
    @Test
    public void aFullRosterStillRefusesATenthCharacter() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        roster.addCharacter(defaultCharacter());
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS; slot++) {
            roster.addCharacter(roleplay(slot, "Character" + slot));
        }

        assertFalse("there is no tenth slot to fill",
                roster.addCharacter(RoleplayCharacter.builder(
                                UUID.randomUUID(), OWNER)
                        .slot(0).name("Overflow")
                        .race(CharacterRaceRegistry.HUMAN)
                        .gender(CharacterGenderRegistry.MALE)
                        .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                        .startingFaction("lotr:gondor").build()));
    }

    /** Adding it does not unlock a slot the player has not earned. */
    @Test
    public void theDefaultCharacterUnlocksNothing() {
        CharacterRoster roster = new CharacterRoster(OWNER);
        int unlockedBefore = roster.getUnlockedSlotCount();

        roster.addCharacter(defaultCharacter());

        assertEquals(unlockedBefore, roster.getUnlockedSlotCount());
    }

    private static RoleplayCharacter defaultCharacter() {
        return RoleplayCharacter.builder(OWNER, OWNER)
                .kind(CharacterKind.DEFAULT)
                .slot(CharacterRoster.DEFAULT_SLOT_INDEX)
                .name("Steve")
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("")
                .build();
    }

    private static RoleplayCharacter roleplay(int slot, String name) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(slot)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin(CharacterSkinRegistry.ACCOUNT_SKIN_ID)
                .startingFaction("lotr:gondor")
                .build();
    }
}
