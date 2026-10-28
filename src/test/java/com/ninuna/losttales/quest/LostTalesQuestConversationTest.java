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
 * What a quest's {@code conversation} block means. A quest that writes an
 * offer is talked about; one that writes nothing keeps starting the way
 * it always did, which is what stops a conversation appearing where
 * nobody wrote one.
 */
public final class LostTalesQuestConversationTest {

    @Test
    public void aQuestWithNoBlockIsNotTalkedAbout() {
        LostTalesQuestConversation conversation = LostTalesQuestConversation.of(
                (Map<String, String>)null);
        assertFalse(conversation.exists());
        assertFalse(conversation.isOffered());
        assertFalse(conversation.isHandedIn());
        assertEquals("", conversation.line(LostTalesQuestConversation.OFFER));
        assertEquals("fallback",
                conversation.lineOr(LostTalesQuestConversation.OFFER, "fallback"));
        assertFalse(LostTalesQuestConversation.of(
                (LostTalesQuestDefinition)null).exists());
    }

    @Test
    public void anOfferIsWhatMakesAQuestOffered() {
        LostTalesQuestConversation conversation = LostTalesQuestConversation.of(
                block("more", "Any dry stick will do."));
        assertTrue("it says something", conversation.exists());
        assertFalse("but nobody offers anything", conversation.isOffered());

        conversation = LostTalesQuestConversation.of(block("offer", "Four sticks?"));
        assertTrue(conversation.isOffered());
        assertFalse(conversation.isHandedIn());

        conversation = LostTalesQuestConversation.of(block("handIn", "Bless you."));
        assertTrue(conversation.isHandedIn());
        assertFalse(conversation.isOffered());
    }

    @Test
    public void aNameIsReadAsWrittenAndBlankLinesAreNotLines() {
        Map<String, String> block = new LinkedHashMap<String, String>();
        block.put(" offer ", "  Four sticks?  ");
        block.put("OFFER", "Another spelling");
        block.put("more", "   ");
        block.put("handIn", null);
        LostTalesQuestConversation conversation = LostTalesQuestConversation.of(block);

        assertEquals("Four sticks?", conversation.line("offer"));
        assertEquals("one spelling per key", "", conversation.line("Offer"));
        assertFalse("whitespace is not a line", conversation.isHandedIn());
        assertEquals("", conversation.line(LostTalesQuestConversation.MORE));
        assertEquals("", conversation.line(null));
    }

    @Test
    public void aLineIsCutToItsBound() {
        StringBuilder long_ = new StringBuilder();
        for (int index = 0; index < LostTalesQuestConversation.MAX_LINE + 50; index++) {
            long_.append('a');
        }
        assertEquals(LostTalesQuestConversation.MAX_LINE,
                LostTalesQuestConversation.of(block("offer", long_.toString()))
                        .line("offer").length());
    }

    /**
     * The validator says so rather than letting a misspelt entry sit in
     * a file saying nothing.
     */
    @Test
    public void theValidatorNamesAnEntryNothingSays() {
        Map<String, String> conversation = new LinkedHashMap<String, String>();
        conversation.put("offer", "Four sticks?");
        conversation.put("greeting", "Well met.");
        List<String> warnings = LostTalesQuestDefinitionValidator
                .describeWarnings(Collections.singletonList(
                        quest(conversation, "Nia")));
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

    private static LostTalesQuestDefinition quest(Map<String, String> conversation,
                                                  String giver) {
        Map<String, String> interaction = new LinkedHashMap<String, String>();
        if (giver.length() > 0) {
            interaction.put("entity", giver);
        }
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("entity", "losttales.Nia");
        LostTalesQuestStageDefinition stage = new LostTalesQuestStageDefinition(
                "10", Collections.singletonList(new LostTalesQuestObjectiveDefinition(
                        "greet", "talk", "Greet Nia.", false, params)));
        return new LostTalesQuestDefinition("losttales:test", "Test", "",
                false, false, LostTalesQuestDefinition.START_MODE_INTERACTION,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), interaction,
                Collections.<String, String>emptyMap(),
                Collections.<String, String>emptyMap(), conversation, null,
                Collections.singletonList(stage), false, null, null);
    }
}
