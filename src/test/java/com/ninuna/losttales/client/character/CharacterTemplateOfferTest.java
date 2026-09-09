package com.ninuna.losttales.client.character;

import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.server.CharacterTemplateAdoption;
import com.ninuna.losttales.character.sync.CharacterOperationFeedback;
import com.ninuna.losttales.character.sync.CharacterOperationType;
import com.ninuna.losttales.character.validation.CharacterErrorId;
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
        // The half-troll is registered but nobody may choose it. A template
        // naming it is not sent, so the world spends its one reading cleanly
        // instead of on a refusal; the template editor names the missing
        // choice the next time it is opened.
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

    @Test
    public void onlyTheAnswerToTheOfferStillWaitingCounts() {
        CharacterOperationFeedback refused = new CharacterOperationFeedback(7,
                CharacterOperationType.CREATE, false, false,
                CharacterErrorId.INTERNAL_ERROR, 2L, 0L, true);
        assertTrue(CharacterTemplateOffer.isAnswerTo(refused, 7));
        // Another request's answer, an offer already answered, no offer.
        assertFalse(CharacterTemplateOffer.isAnswerTo(refused, 8));
        assertFalse(CharacterTemplateOffer.isAnswerTo(refused, 0));
        assertFalse(CharacterTemplateOffer.isAnswerTo(null, 7));
    }
}
