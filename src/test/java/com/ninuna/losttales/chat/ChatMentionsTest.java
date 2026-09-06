package com.ninuna.losttales.chat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class ChatMentionsTest {
    private static final List<String> NAMES =
            Arrays.asList("Player812", "Grey Wanderer");

    @Test
    public void findsMentionsAnywhereInTheMessage() {
        assertTrue(ChatMentions.mentionsAny("@Player812 hello", NAMES));
        assertTrue(ChatMentions.mentionsAny("hello @Player812", NAMES));
        assertTrue(ChatMentions.mentionsAny(
                "hi @Player812, well met", NAMES));
        assertTrue(ChatMentions.mentionsAny("hey @player812!", NAMES));
        assertTrue(ChatMentions.mentionsAny(
                "greetings @Grey Wanderer over there", NAMES));
    }

    @Test
    public void requiresWordBoundariesAroundTheMention() {
        assertFalse(ChatMentions.mentionsAny("@Player8123", NAMES));
        assertFalse(ChatMentions.mentionsAny("mail@Player812", NAMES));
        assertFalse(ChatMentions.mentionsAny(
                "@Player812_alt hello", NAMES));
        assertTrue(ChatMentions.mentionsAny("(@Player812)", NAMES));
    }

    @Test
    public void ignoresPlainTextAndForeignNames() {
        assertFalse(ChatMentions.mentionsAny("Player812 hello", NAMES));
        assertFalse(ChatMentions.mentionsAny("@Someone else", NAMES));
        assertFalse(ChatMentions.mentionsAny("no at sign", NAMES));
        assertFalse(ChatMentions.mentionsAny("@", NAMES));
        assertFalse(ChatMentions.mentionsAny(null, NAMES));
        assertFalse(ChatMentions.mentionsAny("@Player812",
                Collections.<String>emptyList()));
        assertFalse(ChatMentions.mentionsAny("@Player812",
                Arrays.asList((String)null, "  ")));
    }

    @Test
    public void mentionNamesTagsBareNamesAtWordBoundaries() {
        java.util.List<String> names = java.util.Arrays.asList(
                "Player500", "Aragorn");
        assertEquals("Good day to you, @Player500!",
                ChatMentions.mentionNames(
                        "Good day to you, Player500!", names));
        // Already-tagged names keep their one @; case is preserved.
        assertEquals("hello @Player500 and @aragorn",
                ChatMentions.mentionNames(
                        "hello @Player500 and aragorn", names));
        // Word boundaries: no name inside a longer word.
        assertEquals("Player5000 and xPlayer500",
                ChatMentions.mentionNames(
                        "Player5000 and xPlayer500", names));
        assertEquals("unchanged", ChatMentions.mentionNames(
                "unchanged", names));
        assertEquals("", ChatMentions.mentionNames(null, names));
        assertEquals("text", ChatMentions.mentionNames("text", null));
    }
}
