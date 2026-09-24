package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Linking adds an entry for each Discord channel a game channel goes to;
 * unlinking takes entries away by game channel or by Discord channel, and
 * says which webhooks went with them.
 */
public final class DiscordBindingEntriesTest {

    private static final String HOOK_ALL = "https://discord.com/api/webhooks/1/abc";
    private static final String HOOK_OOC = "https://discord.com/api/webhooks/2/def";
    private static final String[] ENTRIES = {
            "# a comment naming channel=5",
            "ooc=DISABLED;channel=;webhook=",
            "global=GAME_TO_DISCORD;channel=7;webhook=" + HOOK_ALL,
    };

    /**
     * A link is one more entry; the empty placeholder of the same game
     * channel goes, and a comment stays where it was.
     */
    @Test
    public void aLinkAddsItsEntryInPlaceOfAPlaceholder() {
        List<String> linked = DiscordBindingEntries.link(ENTRIES, "ooc",
                DiscordBridgeDirection.BIDIRECTIONAL, "5", HOOK_OOC);
        assertEquals(Arrays.asList(ENTRIES[0], ENTRIES[2],
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_OOC), linked);
    }

    /** A game channel links to a second Discord channel beside its first. */
    @Test
    public void aGameChannelLinksToManyDiscordChannels() {
        List<String> linked = DiscordBindingEntries.link(ENTRIES, "global",
                DiscordBridgeDirection.GAME_TO_DISCORD, "8", HOOK_OOC);
        assertEquals(4, linked.size());
        assertEquals(ENTRIES[2], linked.get(2));
        assertEquals("global=GAME_TO_DISCORD;channel=8;webhook=" + HOOK_OOC, linked.get(3));
    }

    @Test
    public void anUnlinkTakesAGameChannelsOrADiscordChannelsEntriesAway() {
        List<String> byKey = DiscordBindingEntries.removeKey(ENTRIES, "ooc");
        assertEquals(Arrays.asList(ENTRIES[0], ENTRIES[2]), byKey);
        List<String> byChannel = DiscordBindingEntries.removeChannel(ENTRIES, "7");
        assertEquals(Arrays.asList(ENTRIES[0], ENTRIES[1]), byChannel);
        assertEquals("a comment is no link", Arrays.asList(ENTRIES),
                DiscordBindingEntries.removeChannel(ENTRIES, "5"));
        assertEquals(Arrays.asList(HOOK_ALL),
                DiscordBindingEntries.webhooksRemoved(ENTRIES, byChannel));
        assertTrue(DiscordBindingEntries.webhooksRemoved(ENTRIES, byKey).isEmpty());
        assertTrue(DiscordBindingEntries.contains(ENTRIES, "ooc"));
        assertFalse(DiscordBindingEntries.contains(ENTRIES, "party"));
    }

    @Test
    public void keysAndOptionsAreReadOffAnEntry() {
        assertEquals("global", DiscordBindingEntries.keyOf(ENTRIES[2]));
        assertEquals("", DiscordBindingEntries.keyOf(ENTRIES[0]));
        assertEquals(HOOK_ALL, DiscordBindingEntries.optionOf(ENTRIES[2], "webhook"));
        assertEquals("7", DiscordBindingEntries.optionOf(ENTRIES[2], "channel"));
        assertEquals("", DiscordBindingEntries.optionOf(ENTRIES[1], "channel"));
    }
}
