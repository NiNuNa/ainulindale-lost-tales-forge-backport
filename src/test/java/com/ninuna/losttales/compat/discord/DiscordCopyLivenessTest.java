package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The one rule every action on a Discord copy asks: a copy is live
 * while the message's game channel is bound to the copy's Discord
 * channel, in the direction the action crosses. An unbound pair is left
 * alone both ways and its links are kept, so binding it again brings
 * everything back.
 */
public final class DiscordCopyLivenessTest {
    private static final String HOOK_A = "https://discord.com/api/webhooks/1/a";
    private static final String HOOK_B = "https://discord.com/api/webhooks/2/b";
    private static final long MESSAGE = 1000L;
    private static final long OTHER = 2000L;
    private static final DiscordCopyLiveness.Crossing TO =
            DiscordCopyLiveness.Crossing.TO_DISCORD;
    private static final DiscordCopyLiveness.Crossing FROM =
            DiscordCopyLiveness.Crossing.FROM_DISCORD;
    private static final DiscordCopyLiveness.Webhooks NOTHING_KNOWN =
            DiscordCopyLiveness.NO_WEBHOOKS;

    /** Where each test's messages were said, as the chat history would say it. */
    private static final class Said implements DiscordCopyLiveness.Places {
        private final Map<Long, ChatChannel> channels = new HashMap<Long, ChatChannel>();
        private final Map<Long, String> factions = new HashMap<Long, String>();

        Said put(long messageId, ChatChannel channel, String faction) {
            this.channels.put(Long.valueOf(messageId), channel);
            this.factions.put(Long.valueOf(messageId), faction);
            return this;
        }

        @Override
        public ChatChannel channelOf(long messageId) {
            return this.channels.get(Long.valueOf(messageId));
        }

        @Override
        public String factionScopeOf(long messageId) {
            String faction = this.factions.get(Long.valueOf(messageId));
            return faction == null ? "" : faction;
        }
    }

    /** What a worker would know of its webhooks. */
    private static final class Hooks implements DiscordCopyLiveness.Webhooks {
        private final Map<String, String> channels = new HashMap<String, String>();
        private final Set<String> refused = new HashSet<String>();

        Hooks posting(String webhookUrl, String channelId) {
            this.channels.put(webhookUrl, channelId);
            return this;
        }

        Hooks refusing(String webhookUrl) {
            this.refused.add(webhookUrl);
            return this;
        }

        @Override
        public String channelOf(String webhookUrl) {
            String channel = this.channels.get(webhookUrl);
            return channel == null ? "" : channel;
        }

        @Override
        public boolean refused(String webhookUrl) {
            return this.refused.contains(webhookUrl);
        }
    }

    private static DiscordChannelBindings bound(String... entries) {
        return DiscordChannelBindings.parse(entries, true, null);
    }

    /** A Discord member's own line, read from {@code channelId}. */
    private static DiscordMessageLinks.Copy memberLine(String discordId, String channelId) {
        return new DiscordMessageLinks.Copy(discordId, "channel:" + channelId, "", "");
    }

    private static boolean live(DiscordChannelBindings bindings, ChatChannel channel,
                                String faction, DiscordMessageLinks.Copy copy,
                                DiscordCopyLiveness.Crossing crossing,
                                DiscordCopyLiveness.Webhooks webhooks) {
        return DiscordCopyLiveness.isLive(bindings, channel, faction, copy, crossing,
                webhooks);
    }

    @Test
    public void aBoundPairIsLiveBothWays() {
        DiscordChannelBindings bindings = bound(
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A);
        DiscordMessageLinks.Copy line = memberLine("111", "5");
        assertTrue(live(bindings, ChatChannel.OOC, "", line, TO, NOTHING_KNOWN));
        assertTrue(live(bindings, ChatChannel.OOC, "", line, FROM, NOTHING_KNOWN));
        DiscordMessageLinks.Copy posted = new DiscordMessageLinks.Copy("222",
                "channel:5", HOOK_A, "", "ooc");
        assertTrue(live(bindings, ChatChannel.OOC, "", posted, TO,
                new Hooks().posting(HOOK_A, "5")));
    }

    @Test
    public void anUnboundPairIsLeftAloneBothWays() {
        Hooks hooks = new Hooks().posting(HOOK_A, "5").posting(HOOK_B, "6");
        DiscordMessageLinks.Copy line = memberLine("111", "5");
        DiscordMessageLinks.Copy posted = new DiscordMessageLinks.Copy("222",
                "channel:5", HOOK_A, "", "ooc");
        for (DiscordChannelBindings bindings : Arrays.asList(
                DiscordChannelBindings.EMPTY,
                bound("ooc=BIDIRECTIONAL;channel=6;webhook=" + HOOK_B),
                bound("ooc=DISABLED;channel=5;webhook=" + HOOK_A))) {
            assertFalse(live(bindings, ChatChannel.OOC, "", line, TO, hooks));
            assertFalse(live(bindings, ChatChannel.OOC, "", line, FROM, hooks));
            assertFalse(live(bindings, ChatChannel.OOC, "", posted, TO, hooks));
            assertEquals("", DiscordCopyLiveness.correctionWebhook(bindings,
                    ChatChannel.OOC, "", posted, hooks));
        }
    }

    /**
     * A Discord channel bound from one game channel to another reaches
     * the new one's messages only: the old ones are neither reacted to
     * nor corrected, and a reply there never quotes their words into the
     * new channel. The links stay, so the old pair bound again is whole.
     */
    @Test
    public void aDiscordChannelReboundToAnotherGameChannelLeavesTheOldMessagesAlone() {
        DiscordChannelBindings before = bound(
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A);
        DiscordChannelBindings after = bound(
                "all=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A);
        Hooks hooks = new Hooks().posting(HOOK_A, "5");
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(MESSAGE, "111", "", "channel:5", "");
        links.link(OTHER, "222", "", "channel:5", HOOK_A, "all");
        Said said = new Said().put(MESSAGE, ChatChannel.OOC, "")
                .put(OTHER, ChatChannel.ALL, "");

        assertEquals(MESSAGE, DiscordCopyLiveness.inboundTarget(links, before, said,
                "111", "5"));
        assertEquals(MESSAGE, DiscordCopyLiveness.quotedBy(links, before, said,
                "111", "5"));
        assertEquals(ChatMessageIds.NONE, DiscordCopyLiveness.inboundTarget(links,
                after, said, "111", "5"));
        assertEquals(ChatMessageIds.NONE, DiscordCopyLiveness.quotedBy(links, after,
                said, "111", "5"));
        assertFalse(live(after, ChatChannel.OOC, "", links.copiesOf(MESSAGE).get(0),
                TO, hooks));
        // The game channel it is bound to now has its own lines live there.
        assertEquals(OTHER, DiscordCopyLiveness.inboundTarget(links, after, said,
                "222", "5"));
        assertEquals(HOOK_A, DiscordCopyLiveness.correctionWebhook(after,
                ChatChannel.ALL, "", links.copiesOf(OTHER).get(0), hooks));

        // Nothing was dropped: the old pair bound again answers as before.
        assertEquals(MESSAGE, links.messageIdOf("111"));
        assertEquals(MESSAGE, DiscordCopyLiveness.inboundTarget(links, before, said,
                "111", "5"));
        assertTrue(live(before, ChatChannel.OOC, "", links.copiesOf(MESSAGE).get(0),
                TO, hooks));
    }

    @Test
    public void wordFromDiscordNeedsTheReaderOfTheChannelItCameFrom() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(MESSAGE, "111", "", "channel:5", "");
        Said said = new Said().put(MESSAGE, ChatChannel.OOC, "");
        DiscordChannelBindings bindings = bound(
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A,
                "ooc=BIDIRECTIONAL;channel=6;webhook=" + HOOK_B);
        assertEquals(MESSAGE, DiscordCopyLiveness.inboundTarget(links, bindings, said,
                "111", "5"));
        // The channel a Discord event names is Discord's own word; a reply
        // quotes only a message with a copy in the channel it was written in.
        assertEquals(MESSAGE, DiscordCopyLiveness.inboundTarget(links, bindings, said,
                "111", "6"));
        assertEquals(ChatMessageIds.NONE, DiscordCopyLiveness.quotedBy(links, bindings,
                said, "111", "6"));
        assertEquals(ChatMessageIds.NONE, DiscordCopyLiveness.inboundTarget(links,
                bindings, said, "999", "5"));
        assertEquals("a message the history no longer keeps", ChatMessageIds.NONE,
                DiscordCopyLiveness.inboundTarget(links, bindings, new Said(), "111", "5"));
    }

    @Test
    public void theDirectionDecidesWhichWayACopyIsLive() {
        DiscordMessageLinks.Copy line = memberLine("111", "5");
        DiscordChannelBindings posting = bound(
                "ooc=GAME_TO_DISCORD;channel=5;webhook=" + HOOK_A);
        assertTrue(live(posting, ChatChannel.OOC, "", line, TO, NOTHING_KNOWN));
        assertFalse(live(posting, ChatChannel.OOC, "", line, FROM, NOTHING_KNOWN));
        DiscordChannelBindings reading = bound(
                "ooc=DISCORD_TO_GAME;channel=5;webhook=" + HOOK_A);
        assertFalse("the game never writes into a channel it only reads, "
                        + "so the bot does not react there",
                live(reading, ChatChannel.OOC, "", line, TO, NOTHING_KNOWN));
        assertTrue(live(reading, ChatChannel.OOC, "", line, FROM, NOTHING_KNOWN));
        DiscordChannelBindings noWebhook = bound("ooc=BIDIRECTIONAL;channel=5");
        assertFalse(live(noWebhook, ChatChannel.OOC, "", line, TO, NOTHING_KNOWN));
        assertTrue(live(noWebhook, ChatChannel.OOC, "", line, FROM, NOTHING_KNOWN));
    }

    @Test
    public void aFactionCopyIsLiveForItsOwnFactionOnly() {
        DiscordChannelBindings bindings = bound(
                "faction:lotr:gondor=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A);
        DiscordMessageLinks.Copy line = memberLine("111", "5");
        assertTrue(live(bindings, ChatChannel.FACTION, "lotr:gondor", line, FROM,
                NOTHING_KNOWN));
        assertTrue(live(bindings, ChatChannel.FACTION, "lotr:gondor", line, TO,
                NOTHING_KNOWN));
        assertTrue("faction ids compare case-insensitively",
                live(bindings, ChatChannel.FACTION, "LOTR:Gondor", line, FROM,
                        NOTHING_KNOWN));
        assertFalse(live(bindings, ChatChannel.FACTION, "lotr:rohan", line, FROM,
                NOTHING_KNOWN));
        assertFalse(live(bindings, ChatChannel.FACTION, "lotr:rohan", line, TO,
                NOTHING_KNOWN));
        assertFalse(live(bindings, ChatChannel.FACTION, "", line, FROM,
                NOTHING_KNOWN));
        assertFalse("another game channel in the same Discord channel",
                live(bindings, ChatChannel.OOC, "", line, FROM, NOTHING_KNOWN));
    }

    /**
     * Reordering the entries moves the ordinals in the binding ids, so a
     * copy saved under {@code ooc} now names another entry; it is still
     * found by its game channel and its Discord channel.
     */
    @Test
    public void reorderingTheEntriesKeepsEveryPairLive() {
        String first = "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A;
        String second = "ooc=BIDIRECTIONAL;channel=6;webhook=" + HOOK_B;
        DiscordChannelBindings before = bound(first, second);
        DiscordChannelBindings after = bound(second, first);
        assertEquals("5", before.byId("ooc").getDiscordChannelId());
        assertEquals("6", after.byId("ooc").getDiscordChannelId());
        Hooks hooks = new Hooks().posting(HOOK_A, "5").posting(HOOK_B, "6");
        DiscordMessageLinks.Copy restored = new DiscordMessageLinks.Copy("111",
                "channel:5", "", "", "ooc");
        DiscordMessageLinks.Copy posted = new DiscordMessageLinks.Copy("222",
                "channel:5", HOOK_A, "", "ooc");
        for (DiscordChannelBindings bindings : Arrays.asList(before, after)) {
            assertTrue(live(bindings, ChatChannel.OOC, "", restored, TO, hooks));
            assertTrue(live(bindings, ChatChannel.OOC, "", restored, FROM, hooks));
            assertEquals(HOOK_A, DiscordCopyLiveness.correctionWebhook(bindings,
                    ChatChannel.OOC, "", restored, hooks));
            assertEquals(HOOK_A, DiscordCopyLiveness.correctionWebhook(bindings,
                    ChatChannel.OOC, "", posted, hooks));
        }
    }

    @Test
    public void aCopyKnownOnlyByItsWebhookGoesWithTheWebhook() {
        DiscordMessageLinks.Copy byWebhook = new DiscordMessageLinks.Copy("444",
                HOOK_A, HOOK_A, "", "all");
        DiscordChannelBindings bindings = bound("all=GAME_TO_DISCORD;webhook=" + HOOK_A);
        assertTrue(live(bindings, ChatChannel.ALL, "", byWebhook, TO, NOTHING_KNOWN));
        assertEquals(HOOK_A, DiscordCopyLiveness.correctionWebhook(bindings,
                ChatChannel.ALL, "", byWebhook, NOTHING_KNOWN));
        assertFalse("word from Discord always names its channel",
                live(bindings, ChatChannel.ALL, "", byWebhook, FROM, NOTHING_KNOWN));
        // The webhook given to another game channel takes the copy with it.
        DiscordChannelBindings moved = bound("ooc=GAME_TO_DISCORD;webhook=" + HOOK_A);
        assertFalse(live(moved, ChatChannel.ALL, "", byWebhook, TO, NOTHING_KNOWN));
        assertEquals("", DiscordCopyLiveness.correctionWebhook(moved,
                ChatChannel.ALL, "", byWebhook, NOTHING_KNOWN));
        // A copy the save kept under its binding alone is never live.
        Hooks hooks = new Hooks().posting(HOOK_A, "5");
        DiscordMessageLinks.Copy underBinding = new DiscordMessageLinks.Copy("555",
                "binding:all", "", "", "all");
        assertFalse(live(bindings, ChatChannel.ALL, "", underBinding, TO, hooks));
        assertEquals("", DiscordCopyLiveness.correctionWebhook(bindings,
                ChatChannel.ALL, "", underBinding, hooks));
    }

    @Test
    public void aWebhookPostingElsewhereOrRefusedPostsNothingThere() {
        DiscordChannelBindings bindings = bound("all=GAME_TO_DISCORD;webhook=" + HOOK_A);
        DiscordMessageLinks.Copy inFive = new DiscordMessageLinks.Copy("222",
                "channel:5", HOOK_A, "", "all");
        assertTrue(live(bindings, ChatChannel.ALL, "", inFive, TO,
                new Hooks().posting(HOOK_A, "5")));
        assertFalse("the webhook now posts into another channel",
                live(bindings, ChatChannel.ALL, "", inFive, TO,
                        new Hooks().posting(HOOK_A, "6")));
        assertTrue("where nothing says, the webhook it went through places it",
                live(bindings, ChatChannel.ALL, "", inFive, TO, NOTHING_KNOWN));
        assertFalse("a webhook Discord refused posts nowhere",
                live(bindings, ChatChannel.ALL, "", inFive, TO,
                        new Hooks().posting(HOOK_A, "5").refusing(HOOK_A)));
    }

    @Test
    public void anEditOrDeletionGoesOnlyThroughTheWebhookThatMadeTheCopy() {
        DiscordChannelBindings bindings = bound(
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A);
        Hooks hooks = new Hooks().posting(HOOK_A, "5");
        DiscordMessageLinks.Copy posted = new DiscordMessageLinks.Copy("222",
                "channel:5", HOOK_A, "", "ooc");
        assertEquals(HOOK_A, DiscordCopyLiveness.correctionWebhook(bindings,
                ChatChannel.OOC, "", posted, hooks));
        assertEquals("given another webhook", "",
                DiscordCopyLiveness.correctionWebhook(
                        bound("ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_B),
                        ChatChannel.OOC, "", posted, new Hooks().posting(HOOK_B, "5")));
        assertEquals("reading only", "", DiscordCopyLiveness.correctionWebhook(
                bound("ooc=DISCORD_TO_GAME;channel=5;webhook=" + HOOK_A),
                ChatChannel.OOC, "", posted, hooks));
        assertEquals("another game channel's message", "",
                DiscordCopyLiveness.correctionWebhook(bindings, ChatChannel.ALL, "",
                        posted, hooks));
        assertEquals("a Discord member's line was made by no webhook", "",
                DiscordCopyLiveness.correctionWebhook(bindings, ChatChannel.OOC, "",
                        memberLine("111", "5"), hooks));
        DiscordMessageLinks.Copy restored = new DiscordMessageLinks.Copy("333",
                "channel:5", "", "", "ooc");
        assertEquals(HOOK_A, DiscordCopyLiveness.correctionWebhook(bindings,
                ChatChannel.OOC, "", restored, hooks));
        assertEquals("Discord never said where the webhook posts", "",
                DiscordCopyLiveness.correctionWebhook(bindings, ChatChannel.OOC, "",
                        restored, NOTHING_KNOWN));
    }

    @Test
    public void aDiscordJumpLinkIsReadOnlyThroughTheMessagesOwnReader() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(MESSAGE, "111", "", "channel:5", HOOK_A, "ooc");
        Said said = new Said().put(MESSAGE, ChatChannel.OOC, "");
        DiscordChannelBindings bindings = bound(
                "ooc=BIDIRECTIONAL;channel=5;webhook=" + HOOK_A);
        assertEquals("#OOC&Discord/1000", DiscordCopyLiveness.gameLink(links,
                bindings, said, "5", "111"));
        assertEquals("posted there but not read", "", DiscordCopyLiveness.gameLink(
                links, bound("ooc=GAME_TO_DISCORD;channel=5;webhook=" + HOOK_A),
                said, "5", "111"));
        assertEquals("read into another game channel", "",
                DiscordCopyLiveness.gameLink(links, bound("all=DISCORD_TO_GAME;channel=5"),
                        said, "5", "111"));
        assertEquals("a message the history no longer keeps", "",
                DiscordCopyLiveness.gameLink(links, bindings, new Said(), "5", "111"));
        assertEquals("never carried", "", DiscordCopyLiveness.gameLink(links,
                bindings, said, "5", "999"));
        // A link's channel is typed by its author: one naming a read
        // channel for a copy that is somewhere else counts for nothing,
        // whether or not the message has another copy in that channel.
        DiscordMessageLinks elsewhere = new DiscordMessageLinks();
        elsewhere.link(MESSAGE, "222", "", "channel:6", HOOK_B, "ooc#2");
        assertEquals("the message has no copy in the channel the link names", "",
                DiscordCopyLiveness.gameLink(elsewhere, bindings, said, "5", "222"));
        links.link(MESSAGE, "222", "", "channel:6", HOOK_B, "ooc#2");
        assertEquals("the linked copy is not the one in that channel", "",
                DiscordCopyLiveness.gameLink(links, bindings, said, "5", "222"));
        assertEquals("#OOC&Discord/1000", DiscordCopyLiveness.gameLink(links,
                bindings, said, "5", "111"));
    }

    @Test
    public void aGameJumpLinkPointsAtALiveCopyOnly() {
        DiscordMessageLinks links = new DiscordMessageLinks();
        links.link(MESSAGE, "111", "", "channel:5", HOOK_A, "ooc");
        links.link(MESSAGE, "222", "", "channel:6", HOOK_B, "ooc#2");
        Said said = new Said().put(MESSAGE, ChatChannel.OOC, "");
        Hooks hooks = new Hooks().posting(HOOK_A, "5").posting(HOOK_B, "6");
        DiscordChannelBindings both = bound(
                "ooc=GAME_TO_DISCORD;webhook=" + HOOK_A,
                "ooc=GAME_TO_DISCORD;webhook=" + HOOK_B);
        assertEquals("222", DiscordCopyLiveness.jumpTarget(links, both, said, MESSAGE,
                "channel:6", hooks).discordId);
        assertEquals("111", DiscordCopyLiveness.jumpTarget(links, both, said, MESSAGE,
                "channel:7", hooks).discordId);
        DiscordChannelBindings onlySix = bound("ooc=GAME_TO_DISCORD;webhook=" + HOOK_B);
        assertEquals("222", DiscordCopyLiveness.jumpTarget(links, onlySix, said,
                MESSAGE, "channel:5", hooks).discordId);
        assertNull(DiscordCopyLiveness.jumpTarget(links, DiscordChannelBindings.EMPTY,
                said, MESSAGE, "channel:5", hooks));
        assertNull("a message the history no longer keeps",
                DiscordCopyLiveness.jumpTarget(links, both, new Said(), MESSAGE,
                        "channel:5", hooks));
        assertEquals(2, links.copiesOf(MESSAGE).size());
    }

    @Test
    public void onlyTheReadingBindingAnswersForADiscordChannel() {
        DiscordChannelBindings bindings = bound(
                "ooc=GAME_TO_DISCORD;channel=5;webhook=" + HOOK_A,
                "all=DISCORD_TO_GAME;channel=6");
        assertNull(bindings.readerOf("5"));
        assertEquals("all", bindings.readerOf("6").id());
        assertNull(bindings.readerOf(""));
        assertNull(bindings.readerOf(null));
    }
}
