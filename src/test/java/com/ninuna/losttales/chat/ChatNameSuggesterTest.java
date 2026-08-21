package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatNameSuggesterTest {
    /** Steve123 plays Aragorn; shaped for an OOC (account) channel. */
    private static final List<ChatMentionCandidate> OOC = Arrays.asList(
            new ChatMentionCandidate("uuid-1", "Steve123",
                    Arrays.asList("Steve123", "Aragorn")),
            new ChatMentionCandidate("uuid-2", "Grey_Wanderer",
                    Arrays.asList("Grey_Wanderer", "Gandalf")),
            ChatMentionCandidate.single("account:padda", "Padda"),
            null);
    /** The same players shaped for a role-play (character) channel. */
    private static final List<ChatMentionCandidate> ROLEPLAY = Arrays.asList(
            new ChatMentionCandidate("uuid-1", "Aragorn",
                    Arrays.asList("Steve123", "Aragorn")),
            new ChatMentionCandidate("uuid-2", "Gandalf",
                    Arrays.asList("Grey_Wanderer", "Gandalf")),
            ChatMentionCandidate.single("account:padda", "Padda"));

    @Test
    public void plainTextCommandsAndAddressesProduceNoQuery() {
        assertNull(ChatNameSuggester.findQuery(null, 0));
        assertNull(ChatNameSuggester.findQuery("hello", 5));
        assertNull(ChatNameSuggester.findQuery("/msg @Pl", 8));
        assertNull(ChatNameSuggester.findQuery("mail@Bob", 8));
    }

    @Test
    public void bareAtOpensAnEmptyQueryListingEveryone() {
        ChatNameSuggester.Query bare = ChatNameSuggester.findQuery("@", 1);
        assertNotNull(bare);
        assertEquals(0, bare.atIndex);
        assertEquals("", bare.prefix);
        assertEquals(3, ChatNameSuggester.matches(
                bare.prefix, OOC, 8).size());
    }

    @Test
    public void queryTracksTheAtSignAndTypedPrefix() {
        ChatNameSuggester.Query query =
                ChatNameSuggester.findQuery("hi @Pl", 6);
        assertNotNull(query);
        assertEquals(3, query.atIndex);
        assertEquals("pl", query.prefix);

        ChatNameSuggester.Query spaced =
                ChatNameSuggester.findQuery("@Grey Wan", 9);
        assertNotNull(spaced);
        assertEquals("grey wan", spaced.prefix);
    }

    @Test
    public void overlongPrefixesStopSuggesting() {
        StringBuilder text = new StringBuilder("@");
        for (int index = 0; index < 33; index++) {
            text.append('a');
        }
        assertNull(ChatNameSuggester.findQuery(
                text.toString(), text.length()));
    }

    @Test
    public void eitherAliasFindsThePlayerButOnlyTheChannelIdentityIsShown() {
        List<ChatMentionCandidate> byAccount =
                ChatNameSuggester.matches("ste", OOC, 8);
        assertEquals(1, byAccount.size());
        assertEquals("Steve123", byAccount.get(0).getDisplayName());

        List<ChatMentionCandidate> byCharacter =
                ChatNameSuggester.matches("ARAG", OOC, 8);
        assertEquals(1, byCharacter.size());
        assertEquals("Steve123", byCharacter.get(0).getDisplayName());

        List<ChatMentionCandidate> roleplayByAccount =
                ChatNameSuggester.matches("steve123", ROLEPLAY, 8);
        assertEquals(1, roleplayByAccount.size());
        assertEquals("Aragorn", roleplayByAccount.get(0).getDisplayName());
        assertEquals("Aragorn", ChatNameSuggester.matches(
                "ara", ROLEPLAY, 8).get(0).getDisplayName());
    }

    @Test
    public void matchesDeduplicateByKeyAndHonourTheLimit() {
        List<ChatMentionCandidate> duplicated = Arrays.asList(
                new ChatMentionCandidate("uuid-1", "Aragorn",
                        Arrays.asList("Steve123")),
                new ChatMentionCandidate("uuid-1", "Aragorn",
                        Arrays.asList("Steve123")),
                new ChatMentionCandidate("uuid-9", "Aragorn",
                        Arrays.asList("Imposter")));
        List<ChatMentionCandidate> matched =
                ChatNameSuggester.matches("ara", duplicated, 8);
        // Same player once; a different player sharing the name stays.
        assertEquals(2, matched.size());
        assertEquals("uuid-1", matched.get(0).getKey());
        assertEquals("uuid-9", matched.get(1).getKey());

        assertEquals(1, ChatNameSuggester.matches("", OOC, 1).size());
        assertTrue(ChatNameSuggester.matches("zz", OOC, 8).isEmpty());
        assertTrue(ChatNameSuggester.matches("p", null, 8).isEmpty());
        assertTrue(ChatNameSuggester.matches(null, OOC, 8).isEmpty());
    }

    @Test
    public void candidateNormalizesAliasesAndUsability() {
        ChatMentionCandidate candidate = new ChatMentionCandidate(
                " key ", " Aragorn ", Arrays.asList("Steve123", "", null,
                        "aragorn"));
        assertEquals("key", candidate.getKey());
        assertEquals("Aragorn", candidate.getDisplayName());
        assertEquals(Arrays.asList("aragorn", "steve123"),
                candidate.getAliases());
        assertTrue(candidate.matches("STE".toLowerCase()));
        assertFalse(candidate.matches("gimli"));
        assertFalse(ChatMentionCandidate.single("k", "  ").isUsable());
    }
}
