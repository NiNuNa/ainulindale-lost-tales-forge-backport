package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.util.StringTranslate;
import org.junit.BeforeClass;
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

    /** The mod's own words, as a game loads them from its lang file. */
    @BeforeClass
    public static void loadTheModsWords() {
        StringTranslate.inject(LostTalesQuestObjectiveTypeTest.class
                .getResourceAsStream("/assets/losttales/lang/en_US.lang"));
    }

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
                        "entity", "Nia,losttalesnia"), false, false)
                        .replace(" (0/1)", ""));
        assertEquals("Bring Nia 4 Stick.", LostTalesQuestObjectiveTextHelper
                .buildObjectiveLine(null, objective("deliver",
                        "entity", "Nia", "item", "minecraft:stick",
                        "count", "4"), false, false)
                        .replace(" (0/4)", ""));
        assertEquals("Deliver", LostTalesQuestObjectiveTextHelper
                .typeName("turn_in"));
        assertEquals("Speak", LostTalesQuestObjectiveTextHelper
                .typeName("visit"));
        assertEquals("Dance", LostTalesQuestObjectiveTextHelper
                .typeName("dance"));
    }

    @Test
    public void anObjectiveWithoutWordsIsDescribedByItsKindAndTarget() {
        assertEquals("Defeat 1 enemy.", LostTalesQuestObjectiveTextHelper
                .describe(objective("kill")));
        assertEquals("Defeat 3 enemies.", LostTalesQuestObjectiveTextHelper
                .describe(objective("kill", "count", "3")));
        assertEquals("Gather 2 items.", LostTalesQuestObjectiveTextHelper
                .describe(objective("gather", "count", "2")));
        assertEquals("Travel to the destination.",
                LostTalesQuestObjectiveTextHelper.describe(objective("goto")));
        assertEquals("Speak to the person named.",
                LostTalesQuestObjectiveTextHelper.describe(objective("talk")));
        assertEquals("Objective",
                LostTalesQuestObjectiveTextHelper.describe(null));
    }

    @Test
    public void anOptionalObjectiveSaysSo() {
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("count", "2");
        LostTalesQuestObjectiveDefinition optional =
                new LostTalesQuestObjectiveDefinition("objective", "gather",
                        "Pick the herbs.", true, params);
        assertEquals("Pick the herbs. (0/2) optional",
                LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        null, optional, false, false));
        assertEquals("Pick the herbs. (2/2) optional",
                LostTalesQuestObjectiveTextHelper.buildObjectiveLine(
                        null, optional, false, true));
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
