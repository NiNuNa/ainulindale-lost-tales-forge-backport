package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.sync.CharacterAppearance;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * When a template is a character the account can play as, which is what
 * opens the main menu's play controls and the room menu's.
 */
public final class CharacterTemplateTest {

    private static final UUID ACCOUNT =
            UUID.fromString("c0000000-0000-0000-0000-00000000000c");

    @Test
    public void aSavedCharacterIsSetUp() {
        assertTrue(new CharacterTemplate("Beren", CharacterRaceRegistry.HUMAN, "male",
                "human_male_1", "", "", "", "", 34, false).isSetUp());
    }

    @Test
    public void nothingChosenIsNotSetUp() {
        assertFalse(CharacterTemplate.EMPTY.isSetUp());
    }

    @Test
    public void aNameAloneIsNotSetUp() {
        assertFalse(new CharacterTemplate("Beren", "", "", "", "", "", "", "",
                34, false).isSetUp());
    }

    @Test
    public void aNameTooShortIsNotSetUp() {
        assertFalse(new CharacterTemplate("B", CharacterRaceRegistry.HUMAN, "male",
                "human_male_1", "", "", "", "", 34, false).isSetUp());
    }

    @Test
    public void appearanceCarriesTheChoicesMade() {
        CharacterAppearance appearance = new CharacterTemplate("Beren",
                CharacterRaceRegistry.HUMAN, "male", "human_male_1",
                CharacterBodyTypeRegistry.SLIM, "none",
                "", "", 34, false).toAppearance(ACCOUNT);
        assertEquals(ACCOUNT, appearance.getPlayerId());
        assertEquals(CharacterRaceRegistry.HUMAN, appearance.getRaceId());
        assertEquals("male", appearance.getGenderId());
        assertEquals("human_male_1", appearance.getSkinId());
        assertEquals(CharacterBodyTypeRegistry.SLIM, appearance.getBodyTypeId());
        assertTrue(appearance.isPresent());
    }

    @Test
    public void anEmptyTemplateResolvesToAHumanOfTheAccountsOwnLook() {
        CharacterAppearance appearance = CharacterTemplate.EMPTY.toAppearance(ACCOUNT);
        assertEquals(CharacterRaceRegistry.HUMAN, appearance.getRaceId());
        assertTrue(appearance.getGenderId().length() > 0);
        assertTrue(appearance.getSkinId().length() > 0);
    }

    @Test
    public void aRaceNobodyMayChooseIsNotSetUp() {
        CharacterTemplate template = new CharacterTemplate("Bogdal",
                CharacterRaceRegistry.HALF_TROLL, "male", "half_troll_male_1",
                "", "", "", "", 30, false);
        assertTrue(template.hasUsableName());
        assertFalse(template.hasSelectableRace());
        assertFalse(template.isSetUp());
    }

    @Test
    public void aCapeOrSettingsChoiceAloneIsNotEmpty() {
        assertFalse(new CharacterTemplate("", "", "", "", "", "", "", "", 0,
                false, false, 0).isEmpty());
        assertFalse(new CharacterTemplate("", "", "", "", "", "", "", "", 0,
                true).isEmpty());
        assertTrue(CharacterTemplate.EMPTY.isEmpty());
    }
}
