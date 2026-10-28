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
public final class DiscordLinkEntriesTest {

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
        List<String> linked = DiscordLinkEntries.link(ENTRIES, "ooc",
                DiscordBridgeDirection.BIDIRECTIONAL, "5", HOOK_OOC, "");
        assertEquals(Arrays.asList(ENTRIES[1],
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_OOC), linked);
        assertEquals(Arrays.asList(HOOK_OLD),
                DiscordLinkEntries.webhooksRemoved(ENTRIES, linked));
    }

    /** A game channel links to a second Discord channel beside its first. */
    @Test
    public void aGameChannelLinksToManyDiscordChannels() {
        List<String> linked = DiscordLinkEntries.link(ENTRIES, "global",
                DiscordBridgeDirection.GAME_TO_DISCORD, "8", HOOK_OOC, "");
        assertEquals(3, linked.size());
        assertEquals(ENTRIES[1], linked.get(1));
        assertEquals("global=GAME_TO_DISCORD;channel=8;webhook=" + HOOK_OOC, linked.get(2));
    }

    @Test
    public void anUnlinkTakesAGameChannelsOrADiscordChannelsEntriesAway() {
        List<String> byKey = DiscordLinkEntries.removeKey(ENTRIES, "ooc");
        assertEquals(Arrays.asList(ENTRIES[1]), byKey);
        List<String> byChannel = DiscordLinkEntries.removeChannel(ENTRIES, "7");
        assertEquals(Arrays.asList(ENTRIES[0]), byChannel);
        assertEquals(Arrays.asList(HOOK_ALL),
                DiscordLinkEntries.webhooksRemoved(ENTRIES, byChannel));
        assertEquals(Arrays.asList(HOOK_OLD),
                DiscordLinkEntries.webhooksRemoved(ENTRIES, byKey));
        assertTrue(DiscordLinkEntries.webhooksRemoved(ENTRIES,
                Arrays.asList(ENTRIES)).isEmpty());
        assertEquals("a key no entry names leaves the list whole", Arrays.asList(ENTRIES),
                DiscordLinkEntries.removeKey(ENTRIES, "fellowship"));
    }

    @Test
    public void keysAndOptionsAreReadOffAnEntry() {
        assertEquals("global", DiscordLinkEntries.keyOf(ENTRIES[1]));
        assertEquals(HOOK_ALL, DiscordLinkEntries.optionOf(ENTRIES[1], "webhook"));
        assertEquals("7", DiscordLinkEntries.optionOf(ENTRIES[1], "channel"));
        assertEquals("", DiscordLinkEntries.optionOf(ENTRIES[1], "colour"));
    }

    /** What the link commands write is what the bridge reads. */
    @Test
    public void anEntryAsWrittenIsAnEntryTheBridgeReads() {
        String entry = DiscordLinkEntries.format("OOC",
                DiscordBridgeDirection.BIDIRECTIONAL, "5", HOOK_OOC, "");
        assertEquals("ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_OOC, entry);
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(
                new String[] {entry}, true, null);
        assertEquals(1, channelLinks.all().size());
        assertEquals("5", channelLinks.byId("ooc").getDiscordChannelId());
        assertEquals(HOOK_OOC, channelLinks.byId("ooc").getWebhookUrl());
        assertEquals("no language chosen: the server's default", "",
                channelLinks.byId("ooc").getLanguage());
    }

    /** A link whose language was chosen carries it as one more part (DISC-30). */
    @Test
    public void aChosenLanguageIsOneMorePart() {
        String entry = DiscordLinkEntries.format("ooc",
                DiscordBridgeDirection.BIDIRECTIONAL, "5", HOOK_OOC, "en_US");
        assertEquals("ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_OOC
                + ";language=en_US", entry);
        DiscordChannelLink channelLink = DiscordChannelLinks.parse(
                new String[] {entry}, true, null).byId("ooc");
        assertEquals("en_US", channelLink.getLanguage());
        assertEquals("en_US", channelLink.languageOr("de_DE"));
        assertEquals("de_DE", DiscordChannelLinks.parse(new String[] {ENTRIES[0]},
                true, null).byId("ooc").languageOr("de_DE"));
    }

    /** The language of the entries naming a Discord channel changes; every other stays. */
    @Test
    public void aLinksLanguageChangesInPlace() {
        List<String> german = DiscordLinkEntries.withLanguage(ENTRIES, "5", "de_DE");
        assertEquals(Arrays.asList(ENTRIES[0] + ";language=de_DE", ENTRIES[1]), german);
        List<String> again = DiscordLinkEntries.withLanguage(
                german.toArray(new String[0]), "5", "en_US");
        assertEquals(ENTRIES[0] + ";language=en_US", again.get(0));
        assertEquals("back to the server's default", Arrays.asList(ENTRIES),
                DiscordLinkEntries.withLanguage(german.toArray(new String[0]), "5", ""));
        assertEquals("a channel no entry names leaves the list whole", Arrays.asList(ENTRIES),
                DiscordLinkEntries.withLanguage(ENTRIES, "99", "de_DE"));
    }
}
