package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The bind and unbind commands edit the bindings list one key at a time. */
public final class DiscordBindingEntriesTest {

    private static final String[] ENTRIES = {
            "# a comment",
            "ooc=DISABLED;channel=;webhook=",
            "all=GAME_TO_DISCORD;webhook=https://discord.com/api/webhooks/1/abc",
    };

    @Test
    public void aBindReplacesTheKeysEntryAndKeepsWhatItLeftBlank() {
        List<String> edited = DiscordBindingEntries.upsert(ENTRIES, "ALL",
                DiscordBridgeDirection.BIDIRECTIONAL, "123456789012345678", "");
        assertEquals(3, edited.size());
        assertEquals("# a comment", edited.get(0));
        assertEquals("ooc=DISABLED;channel=;webhook=", edited.get(1));
        assertEquals("all=BIDIRECTIONAL;channel=123456789012345678"
                + ";webhook=https://discord.com/api/webhooks/1/abc", edited.get(2));
    }

    @Test
    public void aBindOfANewKeyAppendsAndAnUnbindRemoves() {
        List<String> added = DiscordBindingEntries.upsert(ENTRIES, "faction:lotr.gondor",
                DiscordBridgeDirection.DISCORD_TO_GAME, "5", "https://x");
        assertEquals(4, added.size());
        assertEquals("faction:lotr.gondor=DISCORD_TO_GAME;channel=5;webhook=https://x",
                added.get(3));
        assertTrue(DiscordBindingEntries.contains(ENTRIES, "ooc"));
        assertFalse(DiscordBindingEntries.contains(ENTRIES, "party"));
        List<String> removed = DiscordBindingEntries.remove(ENTRIES, "ooc");
        assertEquals(Arrays.asList(ENTRIES[0], ENTRIES[2]), removed);
    }

    @Test
    public void keysAndOptionsAreReadOffAnEntry() {
        assertEquals("all", DiscordBindingEntries.keyOf(ENTRIES[2]));
        assertEquals("", DiscordBindingEntries.keyOf(ENTRIES[0]));
        assertEquals("https://discord.com/api/webhooks/1/abc",
                DiscordBindingEntries.optionOf(ENTRIES[2], "webhook"));
        assertEquals("", DiscordBindingEntries.optionOf(ENTRIES[2], "channel"));
        assertEquals("(set)", redactedWebhook(ENTRIES[2]));
    }

    private static String redactedWebhook(String entry) {
        String redacted = com.ninuna.losttales.command.LostTalesCommandDiscord.redact(entry);
        return DiscordBindingEntries.optionOf(redacted, "webhook");
    }
}
