package com.ninuna.losttales.quest;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * What a quest's {@code dialogue} block means. A quest that writes an
 * offer is talked about; one that writes nothing keeps starting the way
 * it always did, which is what stops a conversation appearing where
 * nobody wrote one.
 */
public final class LostTalesQuestDialogueTest {

    @Test
    public void aQuestWithNoBlockIsNotTalkedAbout() {
        LostTalesQuestDialogue dialogue = LostTalesQuestDialogue.of(
                (Map<String, String>)null);
        assertFalse(dialogue.exists());
        assertFalse(dialogue.isOffered());
        assertFalse(dialogue.isHandedIn());
        assertEquals("", dialogue.line(LostTalesQuestDialogue.OFFER));
        assertEquals("fallback",
                dialogue.lineOr(LostTalesQuestDialogue.OFFER, "fallback"));
        assertFalse(LostTalesQuestDialogue.of(
                (LostTalesQuestDefinition)null).exists());
    }

    @Test
    public void anOfferIsWhatMakesAQuestOffered() {
        LostTalesQuestDialogue dialogue = LostTalesQuestDialogue.of(
                block("more", "Any dry stick will do."));
        assertTrue("it says something", dialogue.exists());
        assertFalse("but nobody offers anything", dialogue.isOffered());

        dialogue = LostTalesQuestDialogue.of(block("offer", "Four sticks?"));
        assertTrue(dialogue.isOffered());
        assertFalse(dialogue.isHandedIn());

        dialogue = LostTalesQuestDialogue.of(block("handIn", "Bless you."));
        assertTrue(dialogue.isHandedIn());
        assertFalse(dialogue.isOffered());
    }

    @Test
    public void namesAreReadWhateverTheirCaseAndBlankLinesAreNotLines() {
        Map<String, String> block = new LinkedHashMap<String, String>();
        block.put("OFFER", "  Four sticks?  ");
        block.put("more", "   ");
        block.put("handIn", null);
        LostTalesQuestDialogue dialogue = LostTalesQuestDialogue.of(block);

        assertEquals("Four sticks?", dialogue.line("offer"));
        assertEquals("Four sticks?", dialogue.line(" Offer "));
        assertFalse("whitespace is not a line", dialogue.isHandedIn());
        assertEquals("", dialogue.line(LostTalesQuestDialogue.MORE));
        assertEquals("", dialogue.line(null));
    }

    @Test
    public void aLineIsCutToItsBound() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < LostTalesQuestDialogue.MAX_LINE + 50; index++) {
            long_.append('a');
        }
        assertEquals(LostTalesQuestDialogue.MAX_LINE,
                LostTalesQuestDialogue.of(block("offer", long_.toString()))
                        .line("offer").length());
    }

    /**
     * The validator says so rather than letting a misspelt entry sit in
     * a file saying nothing.
     */
    @Test
    public void theValidatorNamesAnEntryNothingSays() {
        Map<String, String> dialogue = new LinkedHashMap<String, String>();
        dialogue.put("offer", "Four sticks?");
        dialogue.put("greeting", "Well met.");
        List<String> warnings = LostTalesQuestDefinitionValidator
                .describeWarnings(Collections.singletonList(
                        quest(dialogue, "Nia")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("greeting"));
    }

    /** A quest offered in conversation needs somebody to have it with. */
    @Test
    public void theValidatorNamesAnOfferWithNoGiver() {
        List<String> warnings = LostTalesQuestDefinitionValidator
                .describeWarnings(Collections.singletonList(
                        quest(block("offer", "Four sticks?"), "")));
        assertEquals(1, warnings.size());
        assertTrue(warnings.get(0), warnings.get(0).contains("nobody"));
    }

    @Test
    public void aQuestThatSaysNothingRaisesNothing() {
        assertEquals(Collections.<String>emptyList(),
                LostTalesQuestDefinitionValidator.describeWarnings(
                        Collections.singletonList(quest(
                                Collections.<String, String>emptyMap(), "Nia"))));
    }

    private static Map<String, String> block(String key, String value) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        map.put(key, value);
        return map;
    }

    private static LostTalesQuestDefinition quest(Map<String, String> dialogue,
                                                  String giver) {
        Map<String, String> interaction = new LinkedHashMap<String, String>();
        if (giver.length() > 0) {
            interaction.put("entity", giver);
        }
        return new LostTalesQuestDefinition("losttales:test", "Test", "",
                false, false, LostTalesQuestDefinition.START_MODE_INTERACTION,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), interaction,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), dialogue,
                Collections.<LostTalesQuestStageDefinition>emptyList());
    }
}
