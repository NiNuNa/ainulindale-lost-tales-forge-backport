package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.server.CharacterTemplateAdoption;
import com.ninuna.losttales.character.validation.CharacterValidator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a client offers a world that has not taken a template yet. An
 * offer a server would only refuse is not made, and the reading is spent
 * either way.
 */
public final class CharacterTemplateOfferTest {

    @Test
    public void aTemplateIsOfferedAsItWasWritten() {
        CharacterTemplateAdoption adoption = CharacterTemplateOffer.adoption(11L,
                new CharacterTemplate("Beren", CharacterRaceRegistry.HUMAN, "male",
                        "human_male_1", "slim", "flat", "gondor",
                        "A ranger of the north.", 34, false));
        assertTrue(adoption.isOffered());
        assertEquals(11L, adoption.getExpectedRosterRevision());
        assertEquals("Beren", adoption.getName());
        assertEquals(CharacterRaceRegistry.HUMAN, adoption.getRaceId());
        assertEquals("male", adoption.getGenderId());
        assertEquals("human_male_1", adoption.getSkinId());
        assertEquals("slim", adoption.getBodyTypeId());
        assertEquals("flat", adoption.getChestTypeId());
        assertEquals("A ranger of the north.", adoption.getDescription());
        assertEquals(34, adoption.getAge());
    }

    @Test
    public void anEmptyTemplateStillSpendsTheReading() {
        CharacterTemplateAdoption adoption =
                CharacterTemplateOffer.adoption(3L, CharacterTemplate.EMPTY);
        assertFalse(adoption.isOffered());
        assertEquals(3L, adoption.getExpectedRosterRevision());
    }

    @Test
    public void aTemplateWithNoUsableNameIsNotOffered() {
        // A name too short is one no server would accept, and offering it
        // would spend the reading on a refusal the player never asked for.
        CharacterTemplateAdoption adoption = CharacterTemplateOffer.adoption(3L,
                new CharacterTemplate("A", CharacterRaceRegistry.HUMAN, "male", "human_male_1",
                        "", "", "", "", 30, false));
        assertFalse(adoption.isOffered());
    }

    @Test
    public void aTemplateNamingARaceNobodyMayChooseIsNotOffered() {
        // Half-trolls are no longer anyone's to be. A template written
        // before that is not sent, so the world spends its one reading
        // cleanly instead of being refused on every login; the template
        // editor names the missing choice the next time it is opened.
        CharacterTemplateAdoption adoption = CharacterTemplateOffer.adoption(3L,
                new CharacterTemplate("Bogdal", CharacterRaceRegistry.HALF_TROLL,
                        "non_binary", "", "", "", "", "", 30, false));
        assertFalse(adoption.isOffered());
    }

    @Test
    public void aTemplateThatNamedNoAgeTakesTheLowestOne() {
        // Zero is "never chosen", and no server accepts it as an age.
        CharacterTemplateAdoption adoption = CharacterTemplateOffer.adoption(3L,
                new CharacterTemplate("Beren", CharacterRaceRegistry.HUMAN, "male",
                        "human_male_1", "", "", "", "", 0, false));
        assertTrue(adoption.isOffered());
        assertEquals(CharacterValidator.MIN_AGE, adoption.getAge());
    }
}
