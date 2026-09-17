package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a JSON {@code type} names. Every spelling of a kind reads as that
 * kind, an unknown word reads as nothing, and the questions the rest of
 * the quest system asks — is this counted up, does it send the player to
 * somebody — are answered here and nowhere else.
 */
public final class LostTalesQuestObjectiveTypeTest {

    @Test
    public void everySpellingOfAKindNamesThatKind() {
        assertEquals(LostTalesQuestObjectiveType.GATHER,
                LostTalesQuestObjectiveType.of("gather"));
        assertEquals(LostTalesQuestObjectiveType.GATHER,
                LostTalesQuestObjectiveType.of("PICKUP_ITEM"));
        // Read by the journal before it was read by the server; one
        // catalogue means both answer the same.
        assertEquals(LostTalesQuestObjectiveType.GATHER,
                LostTalesQuestObjectiveType.of(" Collect "));
        assertEquals(LostTalesQuestObjectiveType.GOTO,
                LostTalesQuestObjectiveType.of("travel"));
        assertEquals(LostTalesQuestObjectiveType.TALK,
                LostTalesQuestObjectiveType.of("speak_to"));
        assertEquals(LostTalesQuestObjectiveType.DELIVER,
                LostTalesQuestObjectiveType.of("hand_in"));
        assertEquals(LostTalesQuestObjectiveType.KILL,
                LostTalesQuestObjectiveType.of("kill"));
        assertEquals(LostTalesQuestObjectiveType.CRAFT,
                LostTalesQuestObjectiveType.of("craft"));
    }

    @Test
    public void aWordNothingKnowsNamesNothing() {
        assertEquals(LostTalesQuestObjectiveType.UNKNOWN,
                LostTalesQuestObjectiveType.of("escort"));
        assertEquals(LostTalesQuestObjectiveType.UNKNOWN,
                LostTalesQuestObjectiveType.of(""));
        assertEquals(LostTalesQuestObjectiveType.UNKNOWN,
                LostTalesQuestObjectiveType.of((String)null));
        assertEquals(LostTalesQuestObjectiveType.UNKNOWN,
                LostTalesQuestObjectiveType.of(
                        (LostTalesQuestObjectiveDefinition)null));
        assertEquals("", LostTalesQuestObjectiveType.UNKNOWN.canonicalName());
    }

    @Test
    public void goingSomewhereAndSpeakingCountToOneWhileDeliveringCountsItems() {
        assertTrue(LostTalesQuestObjectiveType.GOTO.countsToOne());
        assertTrue(LostTalesQuestObjectiveType.TALK.countsToOne());
        assertFalse("a delivery counts what changes hands",
                LostTalesQuestObjectiveType.DELIVER.countsToOne());
        assertFalse(LostTalesQuestObjectiveType.GATHER.countsToOne());
        assertTrue(LostTalesQuestObjectiveType.TALK.isNpcVisit());
        assertTrue(LostTalesQuestObjectiveType.DELIVER.isNpcVisit());
        assertFalse(LostTalesQuestObjectiveType.GOTO.isNpcVisit());
    }

    @Test
    public void anObjectiveAnswersWithItsOwnKind() {
        LostTalesQuestObjectiveDefinition deliver = objective("deliver",
                "entity", "Nia", "item", "minecraft:stick", "count", "4");
        assertTrue(LostTalesQuestObjectiveType.DELIVER.is(deliver));
        assertFalse(LostTalesQuestObjectiveType.TALK.is(deliver));
        assertEquals("deliver",
                LostTalesQuestObjectiveType.DELIVER.canonicalName());
        // The target of a delivery is what it asks to change hands.
        assertEquals(4, LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(deliver));
        assertEquals(1, LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(objective("talk", "entity", "Nia")));
    }

    @Test
    public void theJournalDescribesATalkAndADeliveryWithoutAuthoredText() {
        assertEquals("Speak to Nia.", LostTalesQuestObjectiveTextHelper
                .buildObjectiveLine(null, objective("talk",
                        "entity", "Nia,losttalesnia"), false, false, false,
                        false).replace(" (0/1)", ""));
        assertEquals("Bring Nia 4 stick.", LostTalesQuestObjectiveTextHelper
                .buildObjectiveLine(null, objective("deliver",
                        "entity", "Nia", "item", "minecraft:stick",
                        "count", "4"), false, false, false, false)
                        .replace(" (0/4)", ""));
        assertEquals("Deliver", LostTalesQuestObjectiveTextHelper
                .getReadableObjectiveType("turn_in"));
        assertEquals("Speak", LostTalesQuestObjectiveTextHelper
                .getReadableObjectiveType("visit"));
    }

    private static LostTalesQuestObjectiveDefinition objective(String type,
            String... params) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        for (int index = 0; index + 1 < params.length; index += 2) {
            values.put(params[index], params[index + 1]);
        }
        return new LostTalesQuestObjectiveDefinition("objective", type,
                "", false, values.isEmpty()
                        ? Collections.<String, String>emptyMap() : values);
    }
}
