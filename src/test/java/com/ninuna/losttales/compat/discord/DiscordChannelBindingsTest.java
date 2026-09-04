package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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

        @Override
        public void warn(String message) {
            this.messages.add(message);
        }
    }

    @Test
    public void entriesNameTheChannelTheDirectionAndWhereItGoes() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "discord=BIDIRECTIONAL;channel=123456789;webhook=" + WEBHOOK,
                "all=game_to_discord;webhook=" + WEBHOOK,
                "faction:lotr:gondor=Discord-To-Game;channel=987654321",
        }, true, warnings);
        assertTrue(warnings.messages.toString(), warnings.messages.isEmpty());
        assertEquals(3, bindings.all().size());
        DiscordChannelBinding discord = bindings.discordChannel();
        assertNotNull(discord);
        assertEquals("discord", discord.key());
        assertEquals("123456789", discord.getDiscordChannelId());
        assertEquals(WEBHOOK, discord.getWebhookUrl());
        assertEquals(DiscordBridgeDirection.BIDIRECTIONAL, discord.getDirection());
        assertTrue(discord.sendsToDiscord());
        assertTrue(discord.readsFromDiscord());
        DiscordChannelBinding global = bindings.byKey("all");
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD, global.getDirection());
        assertFalse(global.readsFromDiscord());
        DiscordChannelBinding gondor = bindings.byKey("faction:lotr:gondor");
        assertEquals(ChatChannel.FACTION, gondor.getChannel());
        assertEquals("lotr:gondor", gondor.getFactionScope());
        assertTrue(gondor.readsFromDiscord());
        assertFalse(gondor.sendsToDiscord());
        assertSame(gondor, bindings.forDiscordChannel("987654321"));
        assertTrue(bindings.readsAnything());
        assertTrue(bindings.sendsAnything());
    }

    @Test
    public void aFactionLineFindsItsOwnBindingBeforeTheChannelsOwn() {
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "faction=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "faction:lotr:gondor=GAME_TO_DISCORD;webhook=" + WEBHOOK + "2",
        }, false, null);
        assertEquals("faction:lotr:gondor",
                bindings.forGame(ChatChannel.FACTION, "LOTR:Gondor").key());
        assertEquals("faction",
                bindings.forGame(ChatChannel.FACTION, "lotr:rohan").key());
        assertEquals("faction", bindings.forGame(ChatChannel.FACTION, "").key());
        assertNull(bindings.forGame(ChatChannel.OOC, ""));
        assertNull(bindings.forGame(null, ""));
    }

    @Test
    public void privateChannelsAreRefusedWhateverTheEntrySays() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "whisper=BIDIRECTIONAL;channel=123;webhook=" + WEBHOOK,
                "party=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "console=DISCORD_TO_GAME;channel=123",
        }, true, warnings);
        assertTrue(bindings.isEmpty());
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
                "ooc=BIDIRECTIONAL;channel=not-a-number;webhook=" + WEBHOOK,
                "",
                "# a comment",
                null,
        }, true, warnings);
        assertEquals(2, bindings.all().size());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byKey("all").getDirection());
        // OOC asked to read a channel whose id is no number: the id is
        // dropped, and then reading has no channel — two faults, two
        // warnings.
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byKey("ooc").getDirection());
        assertEquals(8, warnings.messages.size());
    }

    @Test
    public void whatABindingCannotDoIsTrimmedWithAWarning() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "discord=BIDIRECTIONAL;channel=111",
                "ooc=BIDIRECTIONAL;webhook=" + WEBHOOK,
                "proximity=BIDIRECTIONAL;channel=222;webhook=" + WEBHOOK,
                "faction=BIDIRECTIONAL;channel=333;webhook=" + WEBHOOK,
                "admin=DISCORD_TO_GAME;channel=333",
        }, true, warnings);
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byKey("discord").getDirection());
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byKey("ooc").getDirection());
        assertEquals("proximity has nowhere on Discord to read from",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byKey("proximity").getDirection());
        assertEquals("the faction channel reads only per faction",
                DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.byKey("faction").getDirection());
        assertEquals("one reader per Discord channel; faction asked first but lost its reads",
                DiscordBridgeDirection.DISCORD_TO_GAME,
                bindings.byKey("admin").getDirection());
        assertEquals(4, warnings.messages.size());
    }

    @Test
    public void readingNeedsTheBotToken() {
        Collected warnings = new Collected();
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "discord=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK,
        }, false, warnings);
        assertEquals(DiscordBridgeDirection.GAME_TO_DISCORD,
                bindings.discordChannel().getDirection());
        assertFalse(bindings.readsAnything());
        assertEquals(1, warnings.messages.size());
    }

    @Test
    public void theLegacyKeysBecomeEntriesThatParseToTheSameBindings() {
        String[] entries = DiscordChannelBindings.legacyEntries(
                "111", WEBHOOK, true, true, true, false, "");
        assertEquals(2, entries.length);
        assertEquals("discord=BIDIRECTIONAL;channel=111;webhook=" + WEBHOOK, entries[0]);
        assertEquals("all=GAME_TO_DISCORD;webhook=" + WEBHOOK, entries[1]);
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(entries, true, null);
        assertEquals("discord=BIDIRECTIONAL, all=GAME_TO_DISCORD",
                bindings.describeForLog());
        assertNull(bindings.byKey("ooc"));

        String[] readOnly = DiscordChannelBindings.legacyEntries(
                "111", WEBHOOK, false, true, true, true, WEBHOOK + "-ro");
        DiscordChannelBindings parsed = DiscordChannelBindings.parse(readOnly, true, null);
        assertEquals(DiscordBridgeDirection.DISCORD_TO_GAME,
                parsed.discordChannel().getDirection());
        assertEquals(WEBHOOK + "-ro", parsed.byKey("ooc").getWebhookUrl());
        assertEquals(WEBHOOK, parsed.noticeWebhookUrl());

        assertEquals(0, DiscordChannelBindings.legacyEntries(
                "", "", true, true, true, true, "").length);
    }

    @Test
    public void tagsAreForChannelsThatShareADiscordChannel() {
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "discord=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "all=GAME_TO_DISCORD;webhook=" + WEBHOOK,
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-own",
                "faction:lotr:gondor=DISCORD_TO_GAME;channel=555",
                "admin=DISCORD_TO_GAME;channel=555",
        }, true, null);
        assertTrue("Global shares the Discord channel's webhook",
                bindings.sharesDiscordChannel(bindings.byKey("all")));
        assertTrue(bindings.sharesDiscordChannel(bindings.byKey("discord")));
        assertFalse("OOC has a channel of its own",
                bindings.sharesDiscordChannel(bindings.byKey("ooc")));
        assertTrue("two readers of one channel share it, whichever kept its reads",
                bindings.sharesDiscordChannel(bindings.byKey("admin")));
        assertFalse(bindings.sharesDiscordChannel(null));
    }

    @Test
    public void noticesGoToTheDiscordChannelsWebhookElseTheFirstThatPosts() {
        DiscordChannelBindings bindings = DiscordChannelBindings.parse(new String[] {
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-ooc",
                "discord=GAME_TO_DISCORD;webhook=" + WEBHOOK,
        }, false, null);
        assertEquals(WEBHOOK, bindings.noticeWebhookUrl());
        DiscordChannelBindings without = DiscordChannelBindings.parse(new String[] {
                "ooc=GAME_TO_DISCORD;webhook=" + WEBHOOK + "-ooc",
        }, false, null);
        assertEquals(WEBHOOK + "-ooc", without.noticeWebhookUrl());
        assertEquals("", DiscordChannelBindings.EMPTY.noticeWebhookUrl());
    }
}
