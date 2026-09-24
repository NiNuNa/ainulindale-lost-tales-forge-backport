package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatCodeNames;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class DiscordChannelBindingsTest {

    /** A faction's chat is linked by the faction's code name. */
    @Before
    public void factions() {
        ChatCodeNames.installFactions(Arrays.asList(
                "lotr:gondor", "lotr:rohan", "lotr:mordor"));
    }

    @After
    public void forgetFactions() {
        ChatCodeNames.installFactions(Collections.<String>emptyList());
    }

    private static final String WEBHOOK = "https://discord.com/api/webhooks/1/abc";

    private static final class Collected implements DiscordChannelBindings.Warnings {
        final List<String> messages = new ArrayList<String>();
        final List<String> refusals = new ArrayList<String>();

        @Override
        public void warn(String message) {
            this.messages.add(message);
        }

        @Override
        public void refuse(String message) {
            this.refusals.add(message);
        }
    }

    @Test
    public void entriesNameTheChannelTheDirectionAndWhereItGoes() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=123456789;webhook=" + WEBHOOK,
                "global=game_to_discord;webhook=" + WEBHOOK + "-global",
                "gondor=Discord-To-Game;channel=987654321",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertTrue(warnings.refusals.isEmpty());
        assertEquals(3, bindings.all().size());
        DiscordChannelBinding ooc = bindings.byId("ooc");
        assertNotNull(ooc);
        assertEquals("ooc", ooc.key());
        assertEquals("ooc", ooc.id());
        assertEquals("123456789", ooc.getDiscordChannelId());
        assertEquals(WEBHOOK, ooc.getWebhookUrl());
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL, ooc.getDirection());
        assertTrue(ooc.sendsToDiscord());
        assertTrue(ooc.readsFromDiscord());
        assertEquals(Arrays.asList(ooc), bindings.forGame(ChatChannel.OOC, ""));
        DiscordChannelBinding global = bindings.byId("global");
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD, global.getDirection());
        assertFalse(global.readsFromDiscord());
        DiscordChannelBinding gondor = bindings.byId("gondor");
        assertEquals(ChatChannel.FACTION, gondor.getChannel());
        assertEquals("lotr:gondor", gondor.getFactionScope());
        assertTrue(gondor.readsFromDiscord());
        assertFalse(gondor.sendsToDiscord());
        assertEquals(Arrays.asList(ooc, gondor), bindings.reading());
        assertEquals(Arrays.asList(ooc, global), bindings.destinations());
        assertEquals(Arrays.asList("123456789", "987654321"), bindings.channels());
        assertEquals("ooc", bindings.ownerOfChannel("123456789"));
        assertEquals("", bindings.ownerOfChannel("555"));
        assertEquals("", bindings.ownerOfChannel(null));
        assertTrue(bindings.readsAnything());
        assertTrue(bindings.sendsAnything());
    }

    /**
     * A Discord channel belongs to one game channel whichever way lines
     * cross it: a second game channel naming the same webhook loses its
     * posting, and the same game channel naming one webhook twice posts
     * through it once.
     */
    @Test
    public void aDiscordChannelBelongsToOneGameChannelForPostingToo() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "proximity=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-own",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertTrue(warnings.refusals.get(0), warnings.refusals.get(0).contains(
                "'global' posts through the webhook 'ooc' posts through"));
        assertEquals(DiscordBridgeDirection.DISABLED,
                bindings.byId("global").getDirection());
        assertEquals(1, warnings.messages.size());
        assertTrue(warnings.messages.get(0).contains("names a webhook it names already"));
        // Every webhook posts for one game channel, once.
        assertEquals(Arrays.asList("ooc", "proximity"), idsOf(bindings.destinations()));
        assertEquals(1, bindings.forGame(ChatChannel.OOC, "").size());
        assertNull(bindings.byId("ooc#2"));
    }

    /**
     * A game channel goes to as many Discord channels as it is bound
     * to, in any guild: each entry is one destination, told apart by
     * an ordinal on the shared key.
     */
    @Test
    public void oneGameChannelGoesToManyDiscordChannels() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK + "-main",
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-mirror",
                "global=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-global",
                "ooc=GAME_TO_DISCORD;channel=333;webhook=" + WEBHOOK + "-other-guild",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertTrue(warnings.refusals.isEmpty());
        List<DiscordChannelBinding> ooc = bindings.forGame(ChatChannel.OOC, "");
        assertEquals(3, ooc.size());
        assertEquals("ooc", ooc.get(0).id());
        assertEquals("ooc#2", ooc.get(1).id());
        assertEquals("ooc#3", ooc.get(2).id());
        assertSame(ooc.get(1), bindings.byId("ooc#2"));
        assertEquals(WEBHOOK + "-mirror", ooc.get(1).getWebhookUrl());
        assertEquals("ooc=BIDIRECTIONAL, ooc#2=GAME_TO_DISCORD, global=GAME_TO_DISCORD, "
                + "ooc#3=GAME_TO_DISCORD", bindings.describeForLog());
        // Every webhook once, the first binding through each.
        assertEquals(Arrays.asList("ooc", "ooc#2", "global", "ooc#3"),
                idsOf(bindings.destinations()));
        assertEquals(Arrays.asList("111", "333"), bindings.channels());
        assertEquals(Arrays.asList(bindings.byId("global")),
                bindings.forGame(ChatChannel.GLOBAL, ""));
        assertTrue(bindings.forGame(ChatChannel.OPERATOR, "").isEmpty());
        assertTrue(bindings.forGame(null, "").isEmpty());
        assertNull(bindings.byId("ooc#4"));
        assertNull(bindings.byId(null));
    }

    /**
     * A Discord channel holds one conversation: a second game channel
     * naming it is refused outright, whether it would read it or post
     * into it.
     */
    @Test
    public void aDiscordChannelFeedsOneGameChannelOnly() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=DISCORD_TO_GAME;channel=111",
                "operator=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK + "-staff",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertEquals(2, warnings.refusals.size());
        assertTrue(warnings.refusals.get(0),
                warnings.refusals.get(0).contains("'global' names Discord channel 111")
                        && warnings.refusals.get(0).contains("'ooc' has already"));
        assertEquals(Arrays.asList(bindings.byId("ooc")), bindings.reading());
        assertEquals(DiscordBridgeDirection.DISABLED,
                bindings.byId("global").getDirection());
        assertEquals(DiscordBridgeDirection.DISABLED,
                bindings.byId("operator").getDirection());
        assertEquals(Arrays.asList("ooc"), idsOf(bindings.destinations()));
    }

    /**
     * A game channel that only posts into a Discord channel still has it:
     * another game channel may not read it afterwards, nor post into it
     * before.
     */
    @Test
    public void postingIntoADiscordChannelOwnsItToo() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK,
                "global=DISCORD_TO_GAME;channel=222",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertEquals(DiscordBridgeDirection.DISABLED,
                bindings.byId("global").getDirection());
        assertTrue(bindings.reading().isEmpty());
        assertEquals("ooc", bindings.ownerOfChannel("222"));
    }

    /**
     * The bridge sends the game's lines wherever a webhook points, so a
     * webhook must be a Discord webhook's address; any other is dropped
     * with a warning that does not repeat it.
     */
    @Test
    public void aWebhookMustBeADiscordWebhook() {
        assertTrue(DiscordChannelBindings.isDiscordWebhook(WEBHOOK));
        assertTrue(DiscordChannelBindings.isDiscordWebhook(
                "https://discordapp.com/api/webhooks/123/a_B-c"));
        assertTrue(DiscordChannelBindings.isDiscordWebhook(
                "https://canary.discord.com/api/v10/webhooks/123/abc"));
        assertFalse(DiscordChannelBindings.isDiscordWebhook(
                "http://discord.com/api/webhooks/123/abc"));
        assertFalse(DiscordChannelBindings.isDiscordWebhook(
                "https://discord.com.example.org/api/webhooks/123/abc"));
        assertFalse(DiscordChannelBindings.isDiscordWebhook(
                "https://example.org/api/webhooks/123/abc"));
        assertFalse(DiscordChannelBindings.isDiscordWebhook(
                "https://discord.com/api/webhooks/123/abc?wait=true"));
        assertFalse(DiscordChannelBindings.isDiscordWebhook(
                "https://discord.com/api/webhooks/123"));
        assertFalse(DiscordChannelBindings.isDiscordWebhook(null));

        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=https://example.org/hook/secret",
        }, true, warnings);
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byId("ooc").getDirection());
        assertEquals("", bindings.byId("ooc").getWebhookUrl());
        // The address dropped, and then posting with nowhere to post: two
        // faults, two warnings, neither repeating the address.
        assertEquals(2, warnings.messages.size());
        for (String message : warnings.messages) {
            assertFalse(message, message.contains("secret"));
        }
    }

    @Test
    public void anEntryGivenTwiceOverStandsOnce() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + ";channel=111",
                "ooc=DISCORD_TO_GAME;channel=111",
                "global=DISABLED;webhook=",
                "global=DISABLED;channel=;webhook=",
        }, true, warnings);
        assertEquals(2, bindings.all().size());
        assertEquals(3, warnings.messages.size());
        for (String message : warnings.messages) {
            assertTrue(message, message.contains("the first stands"));
        }
        assertTrue(warnings.refusals.isEmpty());
    }

    /**
     * Each faction's talk is a conversation of its own: a link names its
     * faction by the faction's code name, and the bare word faction, which
     * would put every faction into one Discord channel, names nothing.
     */
    @Test
    public void aFactionLineFindsOnlyItsOwnFactionsBindings() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "faction=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "gondor=GAME_TO_DISCORD;webhook=" + WEBHOOK + "2",
                "gondor=GAME_TO_DISCORD;webhook=" + WEBHOOK + "3",
        }, false, warnings);
        assertEquals(1, warnings.messages.size());
        assertTrue(warnings.messages.get(0), warnings.messages.get(0).contains(
                "unknown channel 'faction'"));
        assertNull(bindings.byId("faction"));
        assertEquals(Arrays.asList("gondor", "gondor#2"),
                idsOf(bindings.forGame(ChatChannel.FACTION, "LOTR:Gondor")));
        assertTrue(bindings.forGame(ChatChannel.FACTION, "lotr:rohan").isEmpty());
        assertTrue(bindings.forGame(ChatChannel.FACTION, "").isEmpty());
        assertTrue(bindings.forGame(ChatChannel.OOC, "").isEmpty());
        assertTrue(bindings.forGame(null, "").isEmpty());
    }

    @Test
    public void privateChannelsAreRefusedWhateverTheEntrySays() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "whisper=BIDIRECTIONAL;channel=123;webhook=" + WEBHOOK,
                "party=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "client_console=DISCORD_TO_GAME;channel=123",
        }, true, warnings);
        assertTrue(bindings.all().isEmpty());
        assertEquals(3, warnings.messages.size());
        for (String message : warnings.messages) {
            assertTrue(message, message.contains("private"));
        }
    }

    @Test
    public void faultsAreOneWarningEachAndNeverAnException() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "nonsense",
                "trade=BIDIRECTIONAL;channel=1;webhook=" + WEBHOOK,
                "ooc:scope=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "global=SIDEWAYS;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;webhook=" + WEBHOOK + ";colour=red",
                "global=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "ooc=BIDIRECTIONAL;channel=not-a-number;webhook=" + WEBHOOK + "-ooc",
                "",
                "# a comment",
                null,
        }, true, warnings);
        assertEquals(2, bindings.all().size());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("global").getDirection());
        // OOC asked to read a channel whose id is no number: the id is
        // dropped, and then reading has no channel — two faults, two
        // warnings.
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("ooc").getDirection());
        assertEquals(8, warnings.messages.size());
        assertTrue(warnings.refusals.isEmpty());
    }

    @Test
    public void whatABindingCannotDoIsTrimmedWithAWarning() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "global=BIDIRECTIONAL;channel=111",
                "ooc=BIDIRECTIONAL;webhook=" + WEBHOOK,
                "proximity=BIDIRECTIONAL;channel=222;webhook=" + WEBHOOK + "-p",
                "faction=BIDIRECTIONAL;channel=333;webhook=" + WEBHOOK + "-f",
                "operator=DISCORD_TO_GAME;channel=333",
        }, true, warnings);
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byId("global").getDirection());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("ooc").getDirection());
        assertEquals("proximity has nowhere on Discord to read from",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("proximity").getDirection());
        assertNull("the faction channel is linked per faction only",
                bindings.byId("faction"));
        assertEquals("the refused faction entry owns nothing, so Operator may read 333",
                DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byId("operator").getDirection());
        assertEquals(4, warnings.messages.size());
        assertTrue(warnings.refusals.isEmpty());
    }

    @Test
    public void readingNeedsTheBotToken() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
        }, false, warnings);
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("ooc").getDirection());
        assertFalse(bindings.readsAnything());
        assertEquals(1, warnings.messages.size());
    }

    /**
     * A switched-off entry binds its Discord channel to nothing, so a
     * webhook of another game channel found posting there is not refused
     * on its account; a reader refused for a conflict leaves the channel
     * with the reader that had it first.
     */
    @Test
    public void aSwitchedOffEntryOwnsNoDiscordChannel() {
        DiscordChannelBindings kept = DiscordChannelBindings.parse(new String[] {
                "global=DISABLED;channel=111;webhook=" + WEBHOOK + "-kept",
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-ooc",
        }, true, null);
        assertEquals("", kept.ownerOfChannel("111"));

        Collected warnings = new Collected();
        DiscordChannelBindings refused = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=DISCORD_TO_GAME;channel=111",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertEquals(DiscordBridgeDirection.DISABLED,
                refused.byId("global").getDirection());
        assertEquals("ooc", refused.ownerOfChannel("111"));
    }

    @Test
    public void destinationsArePostingWebhooksOnceEach() {
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "global=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-a",
                "ooc=BIDIRECTIONAL;channel=1;webhook=" + WEBHOOK + "-b",
                "proximity=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-p",
                "gondor=DISCORD_TO_GAME;channel=2",
                "operator=DISABLED;channel=3;webhook=" + WEBHOOK + "-c",
        }, true, null);
        assertEquals(Arrays.asList("global", "ooc", "proximity"),
                idsOf(bindings.destinations()));
        assertEquals("every channel a binding reads or posts, once each; "
                        + "a switched-off entry binds nothing",
                Arrays.asList("1", "2"), bindings.channels());
        assertTrue(DiscordChannelBindings.EMPTY.destinations().isEmpty());
        assertTrue(DiscordChannelBindings.EMPTY.channels().isEmpty());
        assertFalse(DiscordChannelBindings.EMPTY.sendsAnything());
        assertEquals("none", DiscordChannelBindings.EMPTY.describeForLog());
    }

    private static List<String> idsOf(List<DiscordChannelBinding> bindings) {
        List<String> ids = new ArrayList<String>(bindings.size());
        for (DiscordChannelBinding binding : bindings) {
            ids.add(binding.id());
        }
        return ids;
    }
}
