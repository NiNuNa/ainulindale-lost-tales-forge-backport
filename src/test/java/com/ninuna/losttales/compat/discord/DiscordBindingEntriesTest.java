package com.ninuna.losttales.compat.discord;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Linking adds an entry for each Discord channel a game channel goes to;
 * unlinking takes entries away by game channel or by Discord channel, and
 * says which webhooks went with them.
 */
public final class DiscordBindingEntriesTest {

    private static final String HOOK_ALL = "https://discord.com/api/webhooks/1/abc";
    private static final String HOOK_OOC = "https://discord.com/api/webhooks/2/def";
    private static final String HOOK_OLD = "https://discord.com/api/webhooks/3/ghi";
    private static final String[] ENTRIES = {
            "ooc=GAME_TO_DISCORD;channel=5;webhook=" + HOOK_OLD,
            "global=GAME_TO_DISCORD;channel=7;webhook=" + HOOK_ALL,
    };

    /** A link is one more entry, in place of any entry naming the same Discord channel. */
    @Test
    public void aLinkAddsItsEntryInPlaceOfTheDiscordChannelsOldOne() {
        List<String> linked = DiscordBindingEntries.link(ENTRIES, "ooc",
                DiscordBridgeDirection.BIDIRECTIONAL, "5", HOOK_OOC);
        assertEquals(Arrays.asList(ENTRIES[1],
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_OOC), linked);
        assertEquals(Arrays.asList(HOOK_OLD),
                DiscordBindingEntries.webhooksRemoved(ENTRIES, linked));
    }

    /** A game channel links to a second Discord channel beside its first. */
    @Test
    public void aGameChannelLinksToManyDiscordChannels() {
        List<String> linked = DiscordBindingEntries.link(ENTRIES, "global",
                DiscordBridgeDirection.GAME_TO_DISCORD, "8", HOOK_OOC);
        assertEquals(3, linked.size());
        assertEquals(ENTRIES[1], linked.get(1));
        assertEquals("global=GAME_TO_DISCORD;channel=8;webhook=" + HOOK_OOC, linked.get(2));
    }

    @Test
    public void anUnlinkTakesAGameChannelsOrADiscordChannelsEntriesAway() {
        List<String> byKey = DiscordBindingEntries.removeKey(ENTRIES, "ooc");
        assertEquals(Arrays.asList(ENTRIES[1]), byKey);
        List<String> byChannel = DiscordBindingEntries.removeChannel(ENTRIES, "7");
        assertEquals(Arrays.asList(ENTRIES[0]), byChannel);
        assertEquals(Arrays.asList(HOOK_ALL),
                DiscordBindingEntries.webhooksRemoved(ENTRIES, byChannel));
        assertEquals(Arrays.asList(HOOK_OLD),
                DiscordBindingEntries.webhooksRemoved(ENTRIES, byKey));
        assertTrue(DiscordBindingEntries.webhooksRemoved(ENTRIES,
                Arrays.asList(ENTRIES)).isEmpty());
        assertEquals("a key no entry names leaves the list whole", Arrays.asList(ENTRIES),
                DiscordBindingEntries.removeKey(ENTRIES, "fellowship"));
    }

    @Test
    public void keysAndOptionsAreReadOffAnEntry() {
        assertEquals("global", DiscordBindingEntries.keyOf(ENTRIES[1]));
        assertEquals(HOOK_ALL, DiscordBindingEntries.optionOf(ENTRIES[1], "webhook"));
        assertEquals("7", DiscordBindingEntries.optionOf(ENTRIES[1], "channel"));
        assertEquals("", DiscordBindingEntries.optionOf(ENTRIES[1], "colour"));
    }

    /** What the link commands write is what the bridge reads. */
    @Test
    public void anEntryAsWrittenIsAnEntryTheBridgeReads() {
        String entry = DiscordBindingEntries.format("OOC",
                DiscordBridgeDirection.BIDIRECTIONAL, "5", HOOK_OOC);
        assertEquals("ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_OOC, entry);
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(
                new String[] {entry}, true, null);
        assertEquals(1, bindings.all().size());
        assertEquals("5", bindings.byId("ooc").getDiscordChannelId());
        assertEquals(HOOK_OOC, bindings.byId("ooc").getWebhookUrl());
    }
}
