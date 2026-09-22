package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * A link to a message crosses the bridge spelled the way the other side
 * reads it: {@code #Channel/<id>} becomes a Discord jump link when the
 * bridge holds the message's Discord copy, a jump URL becomes
 * {@code #Channel/<id>} when it names a message the bridge carried, and
 * a link the bridge cannot place is left exactly as typed.
 */
public final class DiscordMessageLinkRewriterTest {

    private static final class Known implements DiscordMessageLinkRewriter.Resolver {
        ChatChannel askedChannel;
        long askedId;

        @Override
        public String jumpUrl(ChatChannel channel, long messageId) {
            this.askedChannel = channel;
            this.askedId = messageId;
            return messageId == 1757522000000L
                    ? DiscordMessageLinkRewriter.jumpUrl("9", "8", "30") : "";
        }

        @Override
        public String gameLink(String guildId, String channelId,
                               String discordMessageId) {
            return "30".equals(discordMessageId) && "8".equals(channelId)
                    ? "#Global/1757522000000" : "";
        }
    }

    @Test
    public void aKnownGameLinkBecomesABareJumpUrl() {
        Known known = new Known();
        // Bare, so Discord draws it as its own link to a message; a
        // masked link would read as plain text.
        assertEquals("see https://discord.com/channels/9/8/30 now",
                DiscordMessageLinkRewriter.outbound(
                        "see #Global/1757522000000 now", known));
        assertSame(ChatChannel.ALL, known.askedChannel);
        assertEquals(1757522000000L, known.askedId);
        assertEquals("https://discord.com/channels/9/8/30",
                DiscordMessageLinkRewriter.outbound(
                        "#OOC/1757522000000", known));
    }

    @Test
    public void anUnknownOrMalformedGameLinkIsLeftAsTyped() {
        Known known = new Known();
        // A message the bridge never posted keeps its game-side spelling.
        assertEquals("#Global/12 stays", DiscordMessageLinkRewriter.outbound(
                "#Global/12 stays", known));
        // A channel alone is not a link to a message, nor is a word.
        assertEquals("#Global alone", DiscordMessageLinkRewriter.outbound(
                "#Global alone", known));
        assertEquals("item#3/1757522000000", DiscordMessageLinkRewriter.outbound(
                "item#3/1757522000000", known));
        assertEquals("#nowhere/1757522000000", DiscordMessageLinkRewriter.outbound(
                "#nowhere/1757522000000", known));
        assertEquals("", DiscordMessageLinkRewriter.outbound(null, known));
        assertEquals("plain", DiscordMessageLinkRewriter.outbound("plain", null));
    }

    @Test
    public void aKnownJumpUrlBecomesAGameLinkOnEveryDiscordHost() {
        Known known = new Known();
        assertEquals("look #Global/1757522000000 here",
                DiscordMessageLinkRewriter.inbound(
                        "look https://discord.com/channels/9/8/30 here", known));
        assertEquals("#Global/1757522000000", DiscordMessageLinkRewriter.inbound(
                "https://ptb.discord.com/channels/9/8/30", known));
        assertEquals("#Global/1757522000000", DiscordMessageLinkRewriter.inbound(
                "https://canary.discordapp.com/channels/9/8/30", known));
        assertEquals("#Global/1757522000000", DiscordMessageLinkRewriter.inbound(
                "http://discordapp.com/channels/9/8/30", known));
    }

    @Test
    public void anUnknownJumpUrlStaysAUrl() {
        Known known = new Known();
        assertEquals("https://discord.com/channels/9/8/31",
                DiscordMessageLinkRewriter.inbound(
                        "https://discord.com/channels/9/8/31", known));
        // Another channel's message of the same id is not this one.
        assertEquals("https://discord.com/channels/9/7/30",
                DiscordMessageLinkRewriter.inbound(
                        "https://discord.com/channels/9/7/30", known));
        // A channel link, not a message link, is left alone.
        assertEquals("https://discord.com/channels/9/8",
                DiscordMessageLinkRewriter.inbound(
                        "https://discord.com/channels/9/8", known));
        assertEquals("", DiscordMessageLinkRewriter.inbound(null, known));
    }
}
