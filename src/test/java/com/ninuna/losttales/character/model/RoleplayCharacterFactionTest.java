package com.ninuna.losttales.character.model;

import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import com.ninuna.losttales.character.sync.CharacterSummary;
import com.ninuna.losttales.chat.server.ChatChannelPolicy;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A character's faction is its LOTR pledge while it has one, else its
 * starting faction (R3 a). The record keeps the pledge, so a character not
 * played answers too, and the chat, the roster and the appearance all read
 * the one rule.
 */
public final class RoleplayCharacterFactionTest {

    private static final UUID OWNER =
            UUID.fromString("e0000000-0000-0000-0000-00000000000e");
    private static final UUID CHARACTER =
            UUID.fromString("e1000000-0000-0000-0000-00000000001e");

    @Test
    public void withoutAPledgeTheStartingFactionStands() {
        RoleplayCharacter character = character("lotr:gondor", "");
        assertEquals("", character.getPledgedFactionId());
        assertEquals("lotr:gondor", character.getFactionId());
    }

    @Test
    public void aPledgeWinsOverTheStartingFaction() {
        RoleplayCharacter character = character("lotr:gondor", "lotr:rohan");
        assertEquals("lotr:rohan", character.getFactionId());
        assertEquals("lotr:gondor", character.getStartingFactionId());
    }

    @Test
    public void theAccountCharacterJoinsAFactionByPledging() {
        RoleplayCharacter account = character(
                LotrCharacterAdapter.UNALIGNED_FACTION_ID, "");
        assertEquals(LotrCharacterAdapter.UNALIGNED_FACTION_ID,
                ChatChannelPolicy.factionOf(account));
        assertTrue(account.setPledgedFactionId("lotr:dale", 1000L));
        assertEquals("lotr:dale", ChatChannelPolicy.factionOf(account));
    }

    @Test
    public void aPledgeIsKeptNormalisedAndReportsWhetherItChanged() {
        RoleplayCharacter character = character("lotr:gondor", "");
        assertTrue(character.setPledgedFactionId(" LOTR:Rohan ", 1000L));
        assertEquals("lotr:rohan", character.getPledgedFactionId());
        assertFalse("the same pledge again changes nothing",
                character.setPledgedFactionId("lotr:rohan", 1000L));
        assertTrue("a broken pledge falls back to the start",
                character.setPledgedFactionId("", 1000L));
        assertEquals("lotr:gondor", character.getFactionId());
        assertFalse(character.setPledgedFactionId(null, 1000L));
    }

    /** A faction's history opens from the moment the character joined it. */
    @Test
    public void aPledgeStartsTheFactionsHistoryAnew() {
        RoleplayCharacter character = character("lotr:gondor", "");
        assertEquals(character.getCreationTimestamp(), character.getFactionSince());
        assertTrue(character.setPledgedFactionId("lotr:rohan", 5000L));
        assertEquals(5000L, character.getFactionSince());
        assertTrue("breaking the pledge starts the old faction anew",
                character.setPledgedFactionId("", 6000L));
        assertEquals(6000L, character.getFactionSince());
        assertTrue(character.setPledgedFactionId("lotr:gondor", 7000L));
        assertEquals("the faction did not change", 6000L, character.getFactionSince());
        assertEquals(6000L, RoleplayCharacter.builder(character).build().getFactionSince());
    }

    @Test
    public void aCopyKeepsThePledge() {
        RoleplayCharacter copy = RoleplayCharacter.builder(
                character("lotr:gondor", "lotr:rohan")).build();
        assertEquals("lotr:rohan", copy.getPledgedFactionId());
    }

    @Test
    public void theRosterAndTheAppearanceCarryTheFactionTheRuleGives() {
        RoleplayCharacter character = character("lotr:gondor", "lotr:rohan");
        assertEquals("lotr:rohan",
                CharacterSummary.fromCharacter(character).getFactionId());
        CharacterRoster roster = new CharacterRoster(OWNER);
        assertTrue(roster.addCharacter(character));
        roster.setActiveCharacterId(CHARACTER);
        assertEquals("lotr:rohan", CharacterAppearance.fromRoster(OWNER,
                "Steve", roster, CharacterBodyTypeRegistry.WIDE).getFactionId());
    }

    @Test
    public void theStaticRuleNeedsNoRecord() {
        assertEquals("lotr:gondor",
                RoleplayCharacter.factionOf("", "lotr:gondor"));
        assertEquals("lotr:mordor",
                RoleplayCharacter.factionOf("lotr:mordor", "lotr:gondor"));
        assertEquals("lotr:gondor",
                RoleplayCharacter.factionOf(null, "lotr:gondor"));
    }

    private static RoleplayCharacter character(String startingFactionId,
                                               String pledgedFactionId) {
        return RoleplayCharacter.builder(CHARACTER, OWNER)
                .slot(0).name("Aldric").race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_gondor_male_0").age(30)
                .startingFaction(startingFactionId)
                .pledgedFaction(pledgedFactionId)
                .createdAt(1L)
                .build();
    }
}
