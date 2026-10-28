package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelAccess;
import com.ninuna.losttales.chat.ChatChannelDescriptor;
import com.ninuna.losttales.chat.ChatChannelGates;
import com.ninuna.losttales.chat.ChatChannelScope;
import com.ninuna.losttales.chat.ChatCodeNames;
import com.ninuna.losttales.chat.ChatPresentationMode;
import com.ninuna.losttales.chat.ChatRecipientRule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class DiscordChannelLinksTest {

    /** A faction's chat is linked by the faction's code name. */
    @Before
    public void factions() {
        ChatCodeNames.installFactions(Arrays.asList(
                "lotr:gondor", "lotr:rohan", "lotr:mordor"));
    }

    @After
    public void forgetFactions() {
        ChatCodeNames.installFactions(Collections.<String>emptyList());
        ChatChannelGates.install(ChatChannelGates.defaults());
    }

    /** Gates with OOC closed to every reader, as a gate naming a role nothing knows is. */
    private static void closeOocToReading() {
        Map<ChatChannel, ChatChannelGates.Gate> gates =
                new HashMap<ChatChannel, ChatChannelGates.Gate>();
        gates.put(ChatChannel.OOC, new ChatChannelGates.Gate(
                Collections.<String>emptySet(), Collections.<String>emptySet(),
                true, false));
        ChatChannelGates.install(ChatChannelGates.of(gates));
    }

    /**
     * A channel nobody may read carries nothing, so an entry for it is
     * refused with a warning, and the other entries stand; the channels
     * the bridge would refuse are known by id, to restart it when a gate
     * opens or closes one.
     */
    @Test
    public void aChannelClosedToReadingIsRefused() {
        closeOocToReading();
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=123456789;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK + "-global",
        }, true, warnings);
        assertTrue(channelLinks.forGame(ChatChannel.OOC, "").isEmpty());
        assertEquals(1, channelLinks.forGame(ChatChannel.GLOBAL, "").size());
        assertEquals(1, warnings.messages.size());
        assertTrue(warnings.messages.get(0), warnings.messages.get(0).contains("gate"));
        // Proximity is never carried, so it stands among them whatever the gates say.
        assertEquals(new java.util.TreeSet<String>(Arrays.asList(
                        ChatChannel.OOC.getId(), ChatChannel.PROXIMITY.getId())),
                LostTalesDiscordBridge.closedChannels());

        ChatChannelGates.install(ChatChannelGates.defaults());
        assertEquals(Collections.singleton(ChatChannel.PROXIMITY.getId()),
                LostTalesDiscordBridge.closedChannels());
    }

    private static final String WEBHOOK = "https://discord.com/api/webhooks/1/abc";

    private static final class Collected implements DiscordChannelLinks.Warnings {
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
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=123456789;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK + "-global",
                "gondor=DISCORD_TO_GAME;channel=987654321;webhook=" + WEBHOOK + "-gondor",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertTrue(warnings.refusals.isEmpty());
        assertEquals(3, channelLinks.all().size());
        DiscordChannelLink ooc = channelLinks.byId("ooc");
        assertNotNull(ooc);
        assertEquals("ooc", ooc.key());
        assertEquals("ooc", ooc.id());
        assertEquals("123456789", ooc.getDiscordChannelId());
        assertEquals(WEBHOOK, ooc.getWebhookUrl());
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL, ooc.getDirection());
        assertTrue(ooc.sendsToDiscord());
        assertTrue(ooc.readsFromDiscord());
        assertEquals(Arrays.asList(ooc), channelLinks.forGame(ChatChannel.OOC, ""));
        DiscordChannelLink global = channelLinks.byId("global");
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD, global.getDirection());
        assertFalse(global.readsFromDiscord());
        DiscordChannelLink gondor = channelLinks.byId("gondor");
        assertEquals(ChatChannel.FACTION, gondor.getChannel());
        assertEquals("lotr:gondor", gondor.getFactionScope());
        assertTrue(gondor.readsFromDiscord());
        assertFalse(gondor.sendsToDiscord());
        assertEquals(Arrays.asList(ooc, gondor), channelLinks.reading());
        assertEquals(Arrays.asList(ooc, global), channelLinks.destinations());
        assertEquals(Arrays.asList("123456789", "222", "987654321"), channelLinks.channels());
        assertEquals("ooc", channelLinks.ownerOfChannel("123456789"));
        assertEquals("", channelLinks.ownerOfChannel("555"));
        assertEquals("", channelLinks.ownerOfChannel(null));
        assertTrue(channelLinks.readsAnything());
        assertTrue(channelLinks.sendsAnything());
    }

    /**
     * The list is written by the link commands alone, so an entry of any
     * other form is refused with a warning and nothing of it is read.
     */
    @Test
    public void formsTheLinkCommandsNeverWriteAreRefused() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=DISABLED;channel=1;webhook=" + WEBHOOK,
                "ooc=game_to_discord;channel=2;webhook=" + WEBHOOK,
                "ooc=Discord-To-Game;channel=3;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "ooc=DISCORD_TO_GAME;channel=4",
                "ooc=BIDIRECTIONAL",
                "ooc=DISABLED;channel=;webhook=",
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + ";channel=5",
                "ooc=GAME_TO_DISCORD; channel=6;webhook=" + WEBHOOK,
                " ooc=GAME_TO_DISCORD;channel=7;webhook=" + WEBHOOK,
                "OOC=GAME_TO_DISCORD;channel=8;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;channel=9;webhook="
                        + "https://discordapp.com/api/webhooks/1/abc",
                "# ooc=GAME_TO_DISCORD;channel=10;webhook=" + WEBHOOK,
                "",
                null,
        }, true, warnings);
        assertTrue(channelLinks.all().isEmpty());
        assertEquals(15, warnings.messages.size());
        assertTrue(warnings.refusals.isEmpty());
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
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;channel=333;webhook=" + WEBHOOK,
                "operator=GAME_TO_DISCORD;channel=444;webhook=" + WEBHOOK + "-own",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertTrue(warnings.refusals.get(0), warnings.refusals.get(0).contains(
                "'global' posts through the webhook 'ooc' posts through"));
        assertEquals(DiscordBridgeDirection.DISABLED,
                channelLinks.byId("global").getDirection());
        assertEquals(1, warnings.messages.size());
        assertTrue(warnings.messages.get(0).contains("names a webhook it names already"));
        // Every webhook posts for one game channel, once.
        assertEquals(Arrays.asList("ooc", "operator"), idsOf(channelLinks.destinations()));
        assertEquals(1, channelLinks.forGame(ChatChannel.OOC, "").size());
        assertNull(channelLinks.byId("ooc#2"));
    }

    /**
     * A game channel goes to as many Discord channels as it is bound
     * to, in any guild: each entry is one destination, told apart by
     * an ordinal on the shared key.
     */
    @Test
    public void oneGameChannelGoesToManyDiscordChannels() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK + "-main",
                "ooc=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK + "-mirror",
                "global=GAME_TO_DISCORD;channel=444;webhook=" + WEBHOOK + "-global",
                "ooc=GAME_TO_DISCORD;channel=333;webhook=" + WEBHOOK + "-other-guild",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertTrue(warnings.refusals.isEmpty());
        List<DiscordChannelLink> ooc = channelLinks.forGame(ChatChannel.OOC, "");
        assertEquals(3, ooc.size());
        assertEquals("ooc", ooc.get(0).id());
        assertEquals("ooc#2", ooc.get(1).id());
        assertEquals("ooc#3", ooc.get(2).id());
        assertSame(ooc.get(1), channelLinks.byId("ooc#2"));
        assertEquals(WEBHOOK + "-mirror", ooc.get(1).getWebhookUrl());
        assertEquals("ooc=BIDIRECTIONAL, ooc#2=GAME_TO_DISCORD, global=GAME_TO_DISCORD, "
                + "ooc#3=GAME_TO_DISCORD", channelLinks.describeForLog());
        // Every webhook once, the first link through each.
        assertEquals(Arrays.asList("ooc", "ooc#2", "global", "ooc#3"),
                idsOf(channelLinks.destinations()));
        assertEquals(Arrays.asList("111", "222", "444", "333"), channelLinks.channels());
        assertEquals(Arrays.asList(channelLinks.byId("global")),
                channelLinks.forGame(ChatChannel.GLOBAL, ""));
        assertTrue(channelLinks.forGame(ChatChannel.OPERATOR, "").isEmpty());
        assertTrue(channelLinks.forGame(null, "").isEmpty());
        assertNull(channelLinks.byId("ooc#4"));
        assertNull(channelLinks.byId(null));
    }

    /**
     * A Discord channel holds one conversation: a second game channel
     * naming it is refused outright, whether it would read it or post
     * into it.
     */
    @Test
    public void aDiscordChannelFeedsOneGameChannelOnly() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=DISCORD_TO_GAME;channel=111;webhook=" + WEBHOOK + "-global",
                "operator=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK + "-staff",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertEquals(2, warnings.refusals.size());
        assertTrue(warnings.refusals.get(0),
                warnings.refusals.get(0).contains("'global' names Discord channel 111")
                        && warnings.refusals.get(0).contains("'ooc' has already"));
        assertEquals(Arrays.asList(channelLinks.byId("ooc")), channelLinks.reading());
        assertEquals(DiscordBridgeDirection.DISABLED,
                channelLinks.byId("global").getDirection());
        assertEquals(DiscordBridgeDirection.DISABLED,
                channelLinks.byId("operator").getDirection());
        assertEquals(Arrays.asList("ooc"), idsOf(channelLinks.destinations()));
    }

    /**
     * A game channel that only posts into a Discord channel still has it:
     * another game channel may not read it afterwards, nor post into it
     * before.
     */
    @Test
    public void postingIntoADiscordChannelOwnsItToo() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK,
                "global=DISCORD_TO_GAME;channel=222;webhook=" + WEBHOOK + "-global",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertEquals(DiscordBridgeDirection.DISABLED,
                channelLinks.byId("global").getDirection());
        assertTrue(channelLinks.reading().isEmpty());
        assertEquals("ooc", channelLinks.ownerOfChannel("222"));
    }

    /**
     * The bridge sends the game's lines wherever a webhook points, so a
     * webhook must be a Discord webhook's address; an entry with any
     * other is refused with a warning that does not repeat it.
     */
    @Test
    public void aWebhookMustBeADiscordWebhook() {
        assertTrue(DiscordChannelLinks.isDiscordWebhook(WEBHOOK));
        assertTrue(DiscordChannelLinks.isDiscordWebhook(
                "https://canary.discord.com/api/v10/webhooks/123/abc"));
        assertFalse("Discord's old host is no webhook the bot makes",
                DiscordChannelLinks.isDiscordWebhook(
                        "https://discordapp.com/api/webhooks/123/a_B-c"));
        assertFalse(DiscordChannelLinks.isDiscordWebhook(
                "http://discord.com/api/webhooks/123/abc"));
        assertFalse(DiscordChannelLinks.isDiscordWebhook(
                "https://discord.com.example.org/api/webhooks/123/abc"));
        assertFalse(DiscordChannelLinks.isDiscordWebhook(
                "https://example.org/api/webhooks/123/abc"));
        assertFalse(DiscordChannelLinks.isDiscordWebhook(
                "https://discord.com/api/webhooks/123/abc?wait=true"));
        assertFalse(DiscordChannelLinks.isDiscordWebhook(
                "https://discord.com/api/webhooks/123"));
        assertFalse("an id longer than any Discord id",
                DiscordChannelLinks.isDiscordWebhook(
                        "https://discord.com/api/webhooks/123456789012345678901/abc"));
        assertFalse(DiscordChannelLinks.isDiscordWebhook(null));

        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=https://example.org/hook/secret",
        }, true, warnings);
        assertNull(channelLinks.byId("ooc"));
        assertEquals(1, warnings.messages.size());
        assertFalse(warnings.messages.get(0), warnings.messages.get(0).contains("secret"));
    }

    /** A Discord id is digits alone, as many as a 64-bit number has at most. */
    @Test
    public void aDiscordIdIsTwentyDigitsAtMost() {
        assertTrue(DiscordChannelLinks.isSnowflake("1"));
        assertTrue(DiscordChannelLinks.isSnowflake("18446744073709551615"));
        assertFalse(DiscordChannelLinks.isSnowflake("123456789012345678901"));
        assertFalse(DiscordChannelLinks.isSnowflake("12a"));
        assertFalse(DiscordChannelLinks.isSnowflake("-1"));
        assertFalse(DiscordChannelLinks.isSnowflake(""));
        assertFalse(DiscordChannelLinks.isSnowflake(null));
    }

    @Test
    public void anEntryGivenTwiceOverStandsOnce() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;channel=111;webhook=" + WEBHOOK,
                "ooc=DISCORD_TO_GAME;channel=111;webhook=" + WEBHOOK + "-other",
                "ooc=DISCORD_TO_GAME;channel=222;webhook=" + WEBHOOK,
        }, true, warnings);
        assertEquals(1, channelLinks.all().size());
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
    public void aFactionLineFindsOnlyItsOwnFactionsLinks() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "faction=GAME_TO_DISCORD;channel=1;webhook=" + WEBHOOK,
                "gondor=GAME_TO_DISCORD;channel=2;webhook=" + WEBHOOK + "2",
                "gondor=GAME_TO_DISCORD;channel=3;webhook=" + WEBHOOK + "3",
        }, false, warnings);
        assertEquals(1, warnings.messages.size());
        assertTrue(warnings.messages.get(0), warnings.messages.get(0).contains(
                "entry 1 names no channel the game knows"));
        assertNull(channelLinks.byId("faction"));
        assertEquals(Arrays.asList("gondor", "gondor#2"),
                idsOf(channelLinks.forGame(ChatChannel.FACTION, "LOTR:Gondor")));
        assertTrue(channelLinks.forGame(ChatChannel.FACTION, "lotr:rohan").isEmpty());
        assertTrue(channelLinks.forGame(ChatChannel.FACTION, "").isEmpty());
        assertTrue(channelLinks.forGame(ChatChannel.OOC, "").isEmpty());
        assertTrue(channelLinks.forGame(null, "").isEmpty());
    }

    @Test
    public void privateChannelsAreRefusedWhateverTheEntrySays() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "whisper=BIDIRECTIONAL;channel=123;webhook=" + WEBHOOK,
                "fellowship=GAME_TO_DISCORD;channel=124;webhook=" + WEBHOOK,
                "console=DISCORD_TO_GAME;channel=125;webhook=" + WEBHOOK,
        }, true, warnings);
        assertTrue(channelLinks.all().isEmpty());
        assertEquals(3, warnings.messages.size());
        for (String message : warnings.messages) {
            assertTrue(message, message.contains("private"));
        }
    }

    @Test
    public void faultsAreOneWarningEachAndNeverAnException() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "nonsense",
                "trade=BIDIRECTIONAL;channel=1;webhook=" + WEBHOOK,
                "ooc:scope=GAME_TO_DISCORD;channel=2;webhook=" + WEBHOOK,
                "global=SIDEWAYS;channel=3;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;channel=4;webhook=" + WEBHOOK + ";colour=red",
                "global=GAME_TO_DISCORD;channel=5;webhook=" + WEBHOOK,
                "ooc=BIDIRECTIONAL;channel=not-a-number;webhook=" + WEBHOOK + "-ooc",
                "",
                "# a comment",
                null,
        }, true, warnings);
        assertEquals(1, channelLinks.all().size());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                channelLinks.byId("global").getDirection());
        assertNull(channelLinks.byId("ooc"));
        assertEquals(9, warnings.messages.size());
        assertTrue(warnings.refusals.isEmpty());
    }

    /**
     * An entry that cannot be read is named by its place in the list and
     * never repeated: a token or a webhook's address pasted in the wrong
     * place must not reach the log, not even its first characters.
     */
    @Test
    public void anEntryThatCannotBeReadIsNamedByItsPlaceAlone() {
        Collected warnings = new Collected();
        DiscordChannelLinks.parse(new String[] {
                "ooc=GAME_TO_DISCORD;channel=1;webhook=" + WEBHOOK,
                "https://discord.com/api/webhooks/1/sEcReT-token",
                "MTIzNDU2Nzg5.sEcReT.bot-token=",
                "https://discord.com/api/webhooks/2/sEcReT?wait=true",
                "ooc=GAME_TO_DISCORD;channel=2;webhook=" + WEBHOOK
                        + "-b;https://discord.com/api/webhooks/3/sEcReT?x=1",
        }, true, warnings);
        assertEquals(4, warnings.messages.size());
        for (String message : warnings.messages) {
            assertFalse(message, message.contains("sEcReT"));
            assertFalse(message, message.contains("MTIz"));
        }
        assertTrue(warnings.messages.get(0), warnings.messages.get(0).contains("entry 2"));
        assertTrue(warnings.messages.get(1), warnings.messages.get(1).contains("entry 3"));
        assertTrue(warnings.messages.get(2), warnings.messages.get(2).contains("entry 4"));
        assertTrue(warnings.messages.get(3), warnings.messages.get(3).contains("entry 5"));
    }

    @Test
    public void whatALinkCannotDoIsTrimmedWithAWarning() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "faction=BIDIRECTIONAL;channel=333;webhook=" + WEBHOOK + "-f",
                "operator=DISCORD_TO_GAME;channel=333;webhook=" + WEBHOOK + "-o",
        }, true, warnings);
        assertNull("the faction channel is linked per faction only",
                channelLinks.byId("faction"));
        assertEquals("the refused faction entry owns nothing, so Operator may read 333",
                DiscordBridgeDirection.DISCORD_TO_GAME,
                channelLinks.byId("operator").getDirection());
        assertEquals(1, warnings.messages.size());
        assertTrue(warnings.refusals.isEmpty());
    }

    /**
     * A link saved for Proximity, or for a server's channel heard nearby,
     * is refused as the bridge starts, with a line saying why, and owns
     * nothing; the links beside it stand (DISC-03).
     */
    @Test
    public void aSavedLinkOfAChannelHeardNearbyIsRefused() {
        ChatChannel.installDefined(Collections.singletonList(new ChatChannelDescriptor(
                "tavern", "Tavern", ChatPresentationMode.IN_CHARACTER,
                ChatRecipientRule.PROXIMITY, ChatChannelAccess.NONE, 0xFFFFFF, true,
                ChatChannelScope.NONE)), null);
        try {
            Collected warnings = new Collected();
            DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                    "proximity=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK + "-p",
                    "tavern=BIDIRECTIONAL;channel=333;webhook=" + WEBHOOK + "-t",
                    "ooc=BIDIRECTIONAL;channel=222;webhook=" + WEBHOOK + "-o",
            }, true, warnings);
            assertNull(channelLinks.byId("proximity"));
            assertNull(channelLinks.byId("tavern"));
            assertEquals(Arrays.asList("ooc"), idsOf(channelLinks.all()));
            assertEquals("a refused entry owns no Discord channel", "ooc",
                    channelLinks.ownerOfChannel("222"));
            assertEquals(2, warnings.messages.size());
            assertTrue(warnings.messages.get(0), warnings.messages.get(0).contains(
                    "Proximity Chat channel refused: that channel is heard only nearby"));
            assertTrue(warnings.messages.get(1), warnings.messages.get(1).contains(
                    "Tavern channel refused: that channel is heard only nearby"));
            assertTrue(warnings.refusals.isEmpty());
        } finally {
            ChatChannel.resetToBuiltIn();
        }
    }

    @Test
    public void readingNeedsTheBotToken() {
        Collected warnings = new Collected();
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
        }, false, warnings);
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                channelLinks.byId("ooc").getDirection());
        assertFalse(channelLinks.readsAnything());
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
        DiscordChannelLinks kept = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=GAME_TO_DISCORD;channel=222;webhook=" + WEBHOOK,
        }, true, null);
        assertEquals(DiscordBridgeDirection.DISABLED, kept.byId("global").getDirection());
        assertEquals("", kept.ownerOfChannel("222"));

        Collected warnings = new Collected();
        DiscordChannelLinks refused = DiscordChannelLinks.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "global=DISCORD_TO_GAME;channel=111;webhook=" + WEBHOOK + "-global",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertEquals(DiscordBridgeDirection.DISABLED,
                refused.byId("global").getDirection());
        assertEquals("ooc", refused.ownerOfChannel("111"));
    }

    @Test
    public void destinationsArePostingWebhooksOnceEach() {
        DiscordChannelLinks channelLinks = DiscordChannelLinks.parse(new String[] {
                "global=GAME_TO_DISCORD;channel=4;webhook=" + WEBHOOK + "-a",
                "ooc=BIDIRECTIONAL;channel=1;webhook=" + WEBHOOK + "-b",
                "rohan=GAME_TO_DISCORD;channel=5;webhook=" + WEBHOOK + "-p",
                "gondor=DISCORD_TO_GAME;channel=2;webhook=" + WEBHOOK + "-g",
                "operator=BIDIRECTIONAL;channel=1;webhook=" + WEBHOOK + "-c",
        }, true, null);
        assertEquals(Arrays.asList("global", "ooc", "rohan"),
                idsOf(channelLinks.destinations()));
        assertEquals("every channel a link reads or posts, once each; "
                        + "a switched-off entry binds nothing",
                Arrays.asList("4", "1", "5", "2"), channelLinks.channels());
        assertTrue(DiscordChannelLinks.EMPTY.destinations().isEmpty());
        assertTrue(DiscordChannelLinks.EMPTY.channels().isEmpty());
        assertFalse(DiscordChannelLinks.EMPTY.sendsAnything());
        assertEquals("none", DiscordChannelLinks.EMPTY.describeForLog());
    }

    private static List<String> idsOf(List<DiscordChannelLink> channelLinks) {
        List<String> ids = new ArrayList<String>(channelLinks.size());
        for (DiscordChannelLink channelLink : channelLinks) {
            ids.add(channelLink.id());
        }
        return ids;
    }
}
