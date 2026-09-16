package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class DiscordChannelBindingsTest {

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
                "all=game_to_discord;webhook=" + WEBHOOK + "-all",
                "faction:lotr:gondor=Discord-To-Game;channel=987654321",
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
        DiscordChannelBinding global = bindings.byId("all");
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD, global.getDirection());
        assertFalse(global.readsFromDiscord());
        DiscordChannelBinding gondor = bindings.byId("faction:lotr:gondor");
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
                "all=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "proximity=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-own",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertTrue(warnings.refusals.get(0), warnings.refusals.get(0).contains(
                "'all' posts through the webhook 'ooc' posts through"));
        assertEquals(DiscordBridgeDirection.DISABLED,
                bindings.byId("all").getDirection());
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
                "all=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-all",
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
        assertEquals("ooc=BIDIRECTIONAL, ooc#2=GAME_TO_DISCORD, all=GAME_TO_DISCORD, "
                + "ooc#3=GAME_TO_DISCORD", bindings.describeForLog());
        // Every webhook once, the first binding through each.
        assertEquals(Arrays.asList("ooc", "ooc#2", "all", "ooc#3"),
                idsOf(bindings.destinations()));
        assertEquals(Arrays.asList("111", "333"), bindings.channels());
        assertEquals(Arrays.asList(bindings.byId("all")),
                bindings.forGame(ChatChannel.ALL, ""));
        assertTrue(bindings.forGame(ChatChannel.ADMIN, "").isEmpty());
        assertTrue(bindings.forGame(null, "").isEmpty());
        assertNull(bindings.byId("ooc#4"));
        assertNull(bindings.byId(null));
    }

    /** A Discord line belongs to one conversation: the second reader is refused. */
    @Test
    public void aDiscordChannelFeedsOneGameChannelOnly() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "all=DISCORD_TO_GAME;channel=111",
                "admin=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK + "-staff",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertEquals(2, warnings.refusals.size());
        assertTrue(warnings.refusals.get(0),
                warnings.refusals.get(0).contains("'all' reads Discord channel 111")
                        && warnings.refusals.get(0).contains("'ooc' reads already"));
        assertEquals(Arrays.asList(bindings.byId("ooc")), bindings.reading());
        assertEquals(DiscordBridgeDirection.DISABLED,
                bindings.byId("all").getDirection());
        assertEquals("the refused entry keeps posting",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("admin").getDirection());
    }

    @Test
    public void anEntryGivenTwiceOverStandsOnce() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + ";channel=111",
                "ooc=DISCORD_TO_GAME;channel=111",
                "all=DISABLED;webhook=",
                "all=DISABLED;channel=;webhook=",
        }, true, warnings);
        assertEquals(2, bindings.all().size());
        assertEquals(3, warnings.messages.size());
        for (String message : warnings.messages) {
            assertTrue(message, message.contains("the first stands"));
        }
        assertTrue(warnings.refusals.isEmpty());
    }

    @Test
    public void aFactionLineFindsItsOwnBindingsBeforeTheChannelsOwn() {
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "faction=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "faction:lotr:gondor=GAME_TO_DISCORD;webhook=" + WEBHOOK + "2",
                "faction:lotr:gondor=GAME_TO_DISCORD;webhook=" + WEBHOOK + "3",
        }, false, null);
        assertEquals(Arrays.asList("faction:lotr:gondor", "faction:lotr:gondor#2"),
                idsOf(bindings.forGame(ChatChannel.FACTION, "LOTR:Gondor")));
        assertEquals(Arrays.asList("faction"),
                idsOf(bindings.forGame(ChatChannel.FACTION, "lotr:rohan")));
        assertEquals(Arrays.asList("faction"),
                idsOf(bindings.forGame(ChatChannel.FACTION, "")));
        assertTrue(bindings.forGame(ChatChannel.OOC, "").isEmpty());
        assertTrue(bindings.forGame(null, "").isEmpty());
    }

    @Test
    public void privateChannelsAreRefusedWhateverTheEntrySays() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "whisper=BIDIRECTIONAL;channel=123;webhook=" + WEBHOOK,
                "party=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "console=DISCORD_TO_GAME;channel=123",
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
                "all=SIDEWAYS;webhook=" + WEBHOOK,
                "all=GAME_TO_DISCORD;webhook=" + WEBHOOK + ";colour=red",
                "all=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "ooc=BIDIRECTIONAL;channel=not-a-number;webhook=" + WEBHOOK + "-ooc",
                "",
                "# a comment",
                null,
        }, true, warnings);
        assertEquals(2, bindings.all().size());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("all").getDirection());
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
                "all=BIDIRECTIONAL;channel=111",
                "ooc=BIDIRECTIONAL;webhook=" + WEBHOOK,
                "proximity=BIDIRECTIONAL;channel=222;webhook=" + WEBHOOK + "-p",
                "faction=BIDIRECTIONAL;channel=333;webhook=" + WEBHOOK + "-f",
                "admin=DISCORD_TO_GAME;channel=333",
        }, true, warnings);
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byId("all").getDirection());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("ooc").getDirection());
        assertEquals("proximity has nowhere on Discord to read from",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("proximity").getDirection());
        assertEquals("the faction channel reads only per faction",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byId("faction").getDirection());
        assertEquals("the faction channel never read 333, so Operator may",
                DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byId("admin").getDirection());
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
                "all=DISABLED;channel=111;webhook=" + WEBHOOK + "-kept",
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-ooc",
        }, true, null);
        assertEquals("", kept.ownerOfChannel("111"));

        Collected warnings = new Collected();
        DiscordChannelBindings refused = DiscordChannelBindings.parse(new String[] {
                "ooc=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
                "all=DISCORD_TO_GAME;channel=111",
        }, true, warnings);
        assertEquals(1, warnings.refusals.size());
        assertEquals(DiscordBridgeDirection.DISABLED,
                refused.byId("all").getDirection());
        assertEquals("ooc", refused.ownerOfChannel("111"));
    }

    @Test
    public void destinationsArePostingWebhooksOnceEach() {
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "all=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-a",
                "ooc=BIDIRECTIONAL;channel=1;webhook=" + WEBHOOK + "-b",
                "proximity=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-p",
                "faction:lotr:gondor=DISCORD_TO_GAME;channel=2",
                "admin=DISABLED;channel=3;webhook=" + WEBHOOK + "-c",
        }, true, null);
        assertEquals(Arrays.asList("all", "ooc", "proximity"),
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
