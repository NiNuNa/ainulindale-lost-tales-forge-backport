package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatTabIds;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What the history is for: a reply may quote a message only if the
 * server actually sent that message to the player replying to it, a
 * message may be rewritten or taken back only by the account that wrote
 * it, and a joining player is shown only what they were entitled to
 * then and are still entitled to now.
 */
public final class ChatHistoryTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID PARTY = UUID.randomUUID();
    private static final String GONDOR = "GONDOR";
    private static final long SENT_AT = 1000000L;
    private static final List<ChatChannel> EVERY_CHANNEL =
            Arrays.asList(ChatChannel.values());

    @Before
    public void setUp() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
    }

    @After
    public void tearDown() {
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
    }

    /* ---- quoting, editing, removing ---- */

    @Test
    public void aQuoteIsOnlyAllowedBackIntoItsOwnConversation() {
        // Having seen a line is not enough: a quote carries its words to
        // everyone the reply reaches, so a whisper may not be answered
        // into Global.
        long whisper = record(ChatChannel.WHISPER, ALICE,
                "the vault code is 4417", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));

        assertTrue(ChatHistory.quoteFor(
                whisper, BOB, ChatChannel.WHISPER, "").exists());
        assertFalse("a whisper quoted into Global would reach everyone",
                ChatHistory.quoteFor(whisper, BOB, ChatChannel.GLOBAL, "").exists());
        assertFalse(ChatHistory.quoteFor(
                whisper, BOB, ChatChannel.OOC, "").exists());
    }

    @Test
    public void aQuoteIsOnlyAllowedBackIntoItsOwnScope() {
        // Two factions share the Faction channel; a line said to one is
        // not a line the other may be shown.
        long gondor = record(ChatChannel.FACTION, ALICE, "the gate holds",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone(),
                "gondor");

        assertTrue(ChatHistory.quoteFor(
                gondor, BOB, ChatChannel.FACTION, "gondor").exists());
        assertFalse(ChatHistory.quoteFor(
                gondor, BOB, ChatChannel.FACTION, "rohan").exists());
    }

    @Test
    public void aRecipientIsQuotedTheMessageTheyWereSent() {
        long id = record(ChatChannel.GLOBAL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatReplyReference quote = ChatHistory.quoteFor(id, BOB, ChatChannel.GLOBAL, "");
        assertTrue(quote.exists());
        assertEquals(id, quote.getMessageId());
        assertEquals("Aldric", quote.getAuthor());
        assertEquals("meet me at the gate", quote.getExcerpt());
    }

    /**
     * A line for everyone — a server broadcast — is quoted by a player
     * it was replayed to, not only by those online when it was sent; a
     * line with an audience of its own stays theirs.
     */
    @Test
    public void aLineForEveryoneIsQuotedByAnyone() {
        long open = record(ChatChannel.GLOBAL, ALICE, "Alice joined the game",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        assertTrue(ChatHistory.quoteFor(open, BOB, ChatChannel.GLOBAL, "").isAnchored());
        long closed = record(ChatChannel.GLOBAL, ALICE, "for Alice alone",
                Arrays.asList(ALICE), ChatHistory.Audience.accounts(
                        Arrays.asList(ALICE), false));
        assertFalse(ChatHistory.quoteFor(closed, BOB, ChatChannel.GLOBAL, "").isAnchored());
        assertTrue(ChatHistory.quoteFor(closed, ALICE, ChatChannel.GLOBAL, "").isAnchored());
    }

    @Test
    public void theDiscordChannelIsQuotedWithoutARecipient() {
        long id = record(ChatChannel.OOC, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatReplyReference quote = ChatHistory.quoteForDiscordChannel(id);
        assertTrue(quote.exists());
        assertEquals("Aldric", quote.getAuthor());
        assertFalse(ChatHistory.quoteForDiscordChannel(id + 1).exists());
        ChatHistory.remove(id, ALICE);
        assertFalse(ChatHistory.quoteForDiscordChannel(id).exists());
    }

    /**
     * Where a kept line was said, as the Discord bridge asks it: the
     * channel, and for Faction chat the faction, which is what decides
     * whether its Discord copies are still bound.
     */
    @Test
    public void aKeptLineSaysWhereItWasSaid() {
        long gondor = record(ChatChannel.FACTION, ALICE, "the gate holds",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone(),
                "lotr:gondor");
        long global = record(ChatChannel.GLOBAL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        assertEquals(ChatChannel.FACTION, ChatHistory.channelOf(gondor));
        assertEquals("lotr:gondor", ChatHistory.factionScopeOf(gondor));
        assertEquals(ChatChannel.GLOBAL, ChatHistory.channelOf(global));
        assertEquals("", ChatHistory.factionScopeOf(global));
        assertNull(ChatHistory.channelOf(global + 1000L));
        assertEquals("", ChatHistory.factionScopeOf(global + 1000L));
        ChatHistory.remove(gondor, ALICE);
        assertNull(ChatHistory.channelOf(gondor));
        assertEquals("", ChatHistory.factionScopeOf(gondor));
    }

    @Test
    public void someoneWhoWasNotSentItIsQuotedNothing() {
        long id = record(ChatChannel.WHISPER, ALICE, "the key is under the barrel",
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        assertFalse(ChatHistory.quoteFor(id, CAROL, ChatChannel.WHISPER, "").exists());
        assertFalse(ChatHistory.quoteFor(id, null, ChatChannel.WHISPER, "").exists());
        assertFalse(ChatHistory.quoteFor(1234L, ALICE, ChatChannel.WHISPER, "").exists());
        assertFalse(ChatHistory.quoteFor(
                ChatMessageIds.NONE, ALICE, ChatChannel.WHISPER, "").exists());
    }

    @Test
    public void unnamedOrUnsignedMessagesAreNotRecorded() {
        ChatHistory.record(ChatMessageIds.NONE, ALICE, "Aldric",
                null, line(ChatMessageIdAllocator.next(), ChatChannel.GLOBAL, ALICE, "hello"),
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "  ", null, line(id, ChatChannel.GLOBAL, ALICE, "hello"),
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        ChatHistory.record(ChatMessageIdAllocator.next(), ALICE, "Aldric", null, null,
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        assertEquals(0, ChatHistory.size());
    }

    @Test
    public void aLongMessageIsQuotedAsAnExcerpt() {
        StringBuilder long_ = new StringBuilder();
        while (long_.length() < ChatReplyReference.MAX_EXCERPT_CHARACTERS + 40) {
            long_.append('x');
        }
        long id = record(ChatChannel.GLOBAL, ALICE, long_.toString(),
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        String excerpt = ChatHistory.quoteFor(
                id, ALICE, ChatChannel.GLOBAL, "").getExcerpt();
        assertTrue(excerpt.length() < ChatReplyReference.MAX_EXCERPT_CHARACTERS + 5);
        assertTrue(excerpt.endsWith("..."));
    }

    @Test
    public void onlyTheAuthorMayChangeAMessage() {
        long id = record(ChatChannel.GLOBAL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        assertNull(ChatHistory.applyEdit(id, BOB, "meet me at the tower"));
        assertNull(ChatHistory.remove(id, BOB));
        assertNull(ChatHistory.applyEdit(id, null, "nobody at all"));
        assertEquals("meet me at the gate", ChatHistory.quoteFor(
                id, BOB, ChatChannel.GLOBAL, "").getExcerpt());
    }

    /** An edit reaches exactly who was sent the original, and the replay says the new words. */
    @Test
    public void anEditIsToldToEveryoneWhoWasSentTheMessageAndReplaysEdited() {
        long id = record(ChatChannel.GLOBAL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        Set<UUID> told = ChatHistory.applyEdit(id, ALICE, "meet me at the tower");
        assertNotNull(told);
        assertTrue(told.contains(ALICE));
        assertTrue(told.contains(BOB));
        assertFalse(told.contains(CAROL));
        assertEquals("meet me at the tower", ChatHistory.quoteFor(
                id, BOB, ChatChannel.GLOBAL, "").getExcerpt());
        List<LostTalesChatMessagePacket> replay = ChatHistory.replayFor(
                requester(CAROL), ChatMessageIds.NONE);
        assertEquals(1, replay.size());
        assertEquals("meet me at the tower", replay.get(0).getMessage());
    }

    @Test
    public void aRemovedMessageIsGoneForGood() {
        long id = record(ChatChannel.GLOBAL, ALICE, "forget I said that",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        Set<UUID> told = ChatHistory.remove(id, ALICE);
        assertNotNull(told);
        assertTrue(told.contains(BOB));
        assertFalse(ChatHistory.quoteFor(id, BOB, ChatChannel.GLOBAL, "").exists());
        assertNull(ChatHistory.applyEdit(id, ALICE, "or that"));
        assertTrue(ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).isEmpty());
        // A moderator's removal likewise, and it says whose the message was.
        long other = record(ChatChannel.GLOBAL, BOB, "and that", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.everyone());
        ChatHistory.Removal removal = ChatHistory.removeByOperator(other);
        assertEquals(BOB, removal.authorId);
        assertTrue(removal.recipients.contains(ALICE));
        assertNull(ChatHistory.removeByOperator(other));
        assertEquals(0, ChatHistory.size());
    }

    /* ---- bounds ---- */

    /** A channel keeps only so many; the oldest of that channel goes first. */
    @Test
    public void eachChannelReachesOnlySoFarBack() {
        long oldestGlobal = record(ChatChannel.GLOBAL, ALICE, "the first thing said",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        long oldestOoc = record(ChatChannel.OOC, ALICE, "and out of character",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        for (int index = 0; index < ChatHistory.MAX_PER_CHANNEL; index++) {
            record(ChatChannel.GLOBAL, ALICE, "and another",
                    Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        }
        assertFalse(ChatHistory.quoteFor(
                oldestGlobal, ALICE, ChatChannel.GLOBAL, "").exists());
        assertNull(ChatHistory.applyEdit(oldestGlobal, ALICE, "on reflection"));
        // The other channel's line was not the one to go.
        assertTrue(ChatHistory.quoteFor(
                oldestOoc, ALICE, ChatChannel.OOC, "").exists());
        assertEquals(ChatHistory.MAX_PER_CHANNEL + 1, ChatHistory.size());
        ChatHistory.clear();
        assertEquals(0, ChatHistory.size());
    }

    @Test
    public void theChannelCapacityIsTheServersConfigWithinTheBound() {
        int original = LostTalesConfig.chatHistoryPerChannel;
        try {
            LostTalesConfig.chatHistoryPerChannel = 3;
            assertEquals(3, ChatHistory.perChannelCapacity());
            long first = record(ChatChannel.GLOBAL, ALICE, "one",
                    Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
            for (int index = 0; index < 3; index++) {
                record(ChatChannel.GLOBAL, ALICE, "more",
                        Collections.singletonList(ALICE),
                        ChatHistory.Audience.everyone());
            }
            assertEquals(3, ChatHistory.size());
            assertFalse(ChatHistory.quoteFor(first, ALICE, ChatChannel.GLOBAL, "").exists());
            LostTalesConfig.chatHistoryPerChannel = ChatHistory.MAX_TOTAL * 2;
            assertEquals(ChatHistory.MAX_TOTAL, ChatHistory.perChannelCapacity());
        } finally {
            LostTalesConfig.chatHistoryPerChannel = original;
        }
    }

    @Test
    public void aRestoredHistoryKeepsItsOrderAndMovesTheAllocatorPast() {
        long first = record(ChatChannel.GLOBAL, ALICE, "one",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        long second = record(ChatChannel.GLOBAL, ALICE, "two",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        java.util.List<ChatHistory.Entry> snapshot = ChatHistory.snapshot();
        assertEquals(2, snapshot.size());
        ChatHistory.clear();
        ChatMessageIdAllocator.reset();
        assertEquals(0, ChatHistory.size());

        // Restored twice over: a line already held is not taken again.
        assertEquals(2, ChatHistory.restore(snapshot));
        assertEquals(0, ChatHistory.restore(snapshot));
        List<LostTalesChatMessagePacket> replay = ChatHistory.replayFor(
                requester(ALICE), ChatMessageIds.NONE);
        assertEquals(2, replay.size());
        assertEquals(first, replay.get(0).getMessageId());
        assertEquals(second, replay.get(1).getMessageId());
        // The next id said is newer than anything restored, whatever the
        // clock says, so a reply to it can never mean a kept line.
        assertTrue(ChatMessageIdAllocator.next() > second);
        // Nothing to restore leaves the allocator alone.
        ChatMessageIdAllocator.reset();
        assertEquals(0, ChatHistory.restore(null));
        assertEquals(0, ChatHistory.restore(Collections.<ChatHistory.Entry>emptyList()));
    }

    @Test
    public void thePageBeforeALineIsTheChannelsNewestOlderLinesNewestFirst() {
        long[] ids = new long[ChatHistory.MAX_OLDER_PER_REQUEST + 5];
        for (int index = 0; index < ids.length; index++) {
            ids[index] = record(ChatChannel.GLOBAL, ALICE, "line " + index,
                    Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        }
        long otherChannel = record(ChatChannel.OOC, ALICE, "elsewhere",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        long whisper = record(ChatChannel.WHISPER, ALICE, "psst",
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        List<LostTalesChatMessagePacket> page = ChatHistory.replayBefore(
                requester(ALICE), ChatChannel.GLOBAL, "", ids[ids.length - 1]);
        // At most a page, newest first, none of them the line asked from
        // or a line of another channel.
        assertEquals(ChatHistory.MAX_OLDER_PER_REQUEST, page.size());
        assertEquals(ids[ids.length - 2], page.get(0).getMessageId());
        assertEquals(ids[ids.length - 1 - ChatHistory.MAX_OLDER_PER_REQUEST],
                page.get(page.size() - 1).getMessageId());
        for (LostTalesChatMessagePacket line : page) {
            assertTrue(line.getMessageId() != otherChannel
                    && line.getMessageId() != whisper);
        }
        // The next page reaches the beginning and stops.
        List<LostTalesChatMessagePacket> rest = ChatHistory.replayBefore(
                requester(ALICE), ChatChannel.GLOBAL, "",
                page.get(page.size() - 1).getMessageId());
        assertEquals(ids.length - 1 - ChatHistory.MAX_OLDER_PER_REQUEST, rest.size());
        assertEquals(ids[0], rest.get(rest.size() - 1).getMessageId());
        assertTrue(ChatHistory.replayBefore(requester(ALICE), ChatChannel.GLOBAL, "",
                ids[0]).isEmpty());
        // A stranger the audience does not admit is shown nothing of a
        // gated channel; a bad request answers with nothing.
        assertTrue(ChatHistory.replayBefore(requester(ALICE), ChatChannel.GLOBAL, "",
                ChatMessageIds.NONE).isEmpty());
        assertTrue(ChatHistory.replayBefore(null, ChatChannel.GLOBAL, "",
                ids[3]).isEmpty());
    }

    @Test
    public void thePageOfAScopedChannelIsItsConversationsAlone() {
        long gondor = record(ChatChannel.FACTION, ALICE, "the gate holds",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone(), "gondor");
        long rohan = record(ChatChannel.FACTION, ALICE, "the horses are ready",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone(), "rohan");
        long newest = record(ChatChannel.FACTION, ALICE, "later",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone(), "gondor");
        List<LostTalesChatMessagePacket> page = ChatHistory.replayBefore(
                requester(ALICE), ChatChannel.FACTION, "gondor", newest);
        assertEquals(1, page.size());
        assertEquals(gondor, page.get(0).getMessageId());
        assertTrue(rohan != page.get(0).getMessageId());
    }

    /** A replay hands over the newest of a channel, and only so many. */
    @Test
    public void aReplayIsCappedPerChannelAndComesOldestFirst() {
        long first = record(ChatChannel.GLOBAL, ALICE, "one",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        for (int index = 0; index < ChatHistory.MAX_REPLAY_PER_CHANNEL; index++) {
            record(ChatChannel.GLOBAL, ALICE, "more",
                    Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        }
        long ooc = record(ChatChannel.OOC, ALICE, "aside",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        List<LostTalesChatMessagePacket> replay = ChatHistory.replayFor(
                requester(CAROL), ChatMessageIds.NONE);
        assertEquals(ChatHistory.MAX_REPLAY_PER_CHANNEL + 1, replay.size());
        assertTrue(replay.get(0).getMessageId() > first);
        for (int index = 1; index < replay.size(); index++) {
            assertTrue(replay.get(index - 1).getMessageId()
                    < replay.get(index).getMessageId());
        }
        assertEquals(ooc, replay.get(replay.size() - 1).getMessageId());
        // Only what is newer than what the asker already holds.
        long since = replay.get(replay.size() - 3).getMessageId();
        assertEquals(2, ChatHistory.replayFor(requester(CAROL), since).size());
    }

    /* ---- who is shown what ---- */

    @Test
    public void anOpenChannelReplaysToAnyoneAndTheSenderGetsTheirOwnCopy() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket shared = line(id, ChatChannel.GLOBAL, ALICE, "hail");
        LostTalesChatMessagePacket own = new LostTalesChatMessagePacket(ChatChannel.GLOBAL,
                ALICE, "Aldric", "alice", "", 0, 0, "hail", SENT_AT, "", null, "", "", 0,
                false, id, ChatReplyReference.NONE, "", 77L);
        ChatHistory.record(id, ALICE, "Aldric", own, shared,
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        assertEquals(77L, ChatHistory.replayFor(requester(ALICE), ChatMessageIds.NONE)
                .get(0).getEchoNonce());
        assertEquals(0L, ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE)
                .get(0).getEchoNonce());
    }

    /** A whisper is its two parties' and nobody else's, however long after. */
    @Test
    public void aWhisperReplaysToItsTwoPartiesOnly() {
        record(ChatChannel.WHISPER, ALICE, "between us", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        assertEquals(1, ChatHistory.replayFor(requester(ALICE), ChatMessageIds.NONE).size());
        assertEquals(1, ChatHistory.replayFor(requester(BOB), ChatMessageIds.NONE).size());
        assertTrue(ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).isEmpty());
    }

    /** A party line reaches the members it had, while they are still in that party. */
    @Test
    public void aPartyLineReplaysToThenMembersStillInTheParty() {
        record(ChatChannel.PARTY, ALICE, "form up", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.party(PARTY, Arrays.asList(ALICE, BOB)));
        assertEquals(1, ChatHistory.replayFor(
                new ChatHistory.Requester(BOB, "", 0L, PARTY, EVERY_CHANNEL),
                ChatMessageIds.NONE).size());
        // Bob has since left the party.
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(BOB, "", 0L, null, EVERY_CHANNEL),
                ChatMessageIds.NONE).isEmpty());
        // Carol joined it afterwards.
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, "", 0L, PARTY, EVERY_CHANNEL),
                ChatMessageIds.NONE).isEmpty());
    }

    /** A faction line reaches the faction's characters then and now. */
    @Test
    public void aFactionLineReplaysToCharactersOfTheFactionThenAndNow() {
        record(ChatChannel.FACTION, ALICE, "for Gondor", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.faction(GONDOR, false));
        // A Gondor character made before the line: shown it, online then or not.
        assertEquals(1, ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, GONDOR, SENT_AT - 1L, null, EVERY_CHANNEL),
                ChatMessageIds.NONE).size());
        // A Gondor character made after it: not in the faction when it was said.
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, GONDOR, SENT_AT + 1L, null, EVERY_CHANNEL),
                ChatMessageIds.NONE).isEmpty());
        // Another faction, or the account with none.
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, "MORDOR", 0L, null, EVERY_CHANNEL),
                ChatMessageIds.NONE).isEmpty());
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, "", 0L, null, EVERY_CHANNEL),
                ChatMessageIds.NONE).isEmpty());
    }

    /** A staff line reaches whoever may read the channel now, and nobody else. */
    @Test
    public void aGatedLineReachesWhoMayReadItNow() {
        record(ChatChannel.OPERATOR, ALICE, "staff only", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.readers());
        assertEquals(1, ChatHistory.replayFor(requester(BOB), ChatMessageIds.NONE).size());
        // Bob was an operator then but is not any more.
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(BOB, "", 0L, null,
                        Collections.singletonList(ChatChannel.GLOBAL)),
                ChatMessageIds.NONE).isEmpty());
        // Carol was not sent it but is an operator now: the channel's past is hers.
        assertEquals(1, ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).size());
        // A gated line written with an account list keeps to that list.
        record(ChatChannel.OPERATOR, ALICE, "for these two", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), true));
        assertEquals(1, ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).size());
        assertEquals(2, ChatHistory.replayFor(requester(BOB), ChatMessageIds.NONE).size());
    }

    /** Where a player stood cannot be asked again: proximity reaches who was near. */
    @Test
    public void aProximityLineReplaysOnlyToWhoWasNear() {
        record(ChatChannel.PROXIMITY, ALICE, "over here", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        assertEquals(1, ChatHistory.replayFor(requester(BOB), ChatMessageIds.NONE).size());
        assertTrue(ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).isEmpty());
        assertTrue(ChatHistory.replayFor(null, ChatMessageIds.NONE).isEmpty());
    }

    /**
     * An account with characters in two factions is replayed both
     * conversations, each from when its own character was made: it may
     * play either at will, so what it could read by switching is what it
     * may read. The two are still separate lines, filed under separate
     * tabs on the client.
     */
    @Test
    public void anAccountIsReplayedEveryFactionItHasACharacterIn() {
        long gondorLine = record(ChatChannel.FACTION, ALICE, "for Gondor",
                Arrays.asList(ALICE),
                ChatHistory.Audience.faction(GONDOR, false));
        long mordorLine = record(ChatChannel.FACTION, BOB, "for Mordor",
                Arrays.asList(BOB),
                ChatHistory.Audience.faction("MORDOR", false));

        java.util.Map<String, Long> both = new java.util.HashMap<String, Long>();
        both.put(GONDOR, Long.valueOf(SENT_AT - 1L));
        both.put("MORDOR", Long.valueOf(SENT_AT - 1L));
        List<LostTalesChatMessagePacket> lines = ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, both, null, EVERY_CHANNEL),
                ChatMessageIds.NONE);
        assertEquals(2, lines.size());

        // One of the two alone is replayed one of the two.
        java.util.Map<String, Long> gondorOnly = new java.util.HashMap<String, Long>();
        gondorOnly.put(GONDOR, Long.valueOf(SENT_AT - 1L));
        List<LostTalesChatMessagePacket> gondor = ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, gondorOnly, null, EVERY_CHANNEL),
                ChatMessageIds.NONE);
        assertEquals(1, gondor.size());
        assertEquals(gondorLine, gondor.get(0).getMessageId());

        // Each faction is asked about its own character's age, not the
        // account's: a Mordor character made after the Mordor line was
        // said is shown Gondor's and not Mordor's.
        java.util.Map<String, Long> laterMordor = new java.util.HashMap<String, Long>();
        laterMordor.put(GONDOR, Long.valueOf(SENT_AT - 1L));
        laterMordor.put("MORDOR", Long.valueOf(SENT_AT + 1L));
        List<LostTalesChatMessagePacket> mixed = ChatHistory.replayFor(
                new ChatHistory.Requester(CAROL, laterMordor, null, EVERY_CHANNEL),
                ChatMessageIds.NONE);
        assertEquals(1, mixed.size());
        assertEquals(gondorLine, mixed.get(0).getMessageId());
        assertTrue("the Mordor line stays unread", mordorLine != mixed.get(0).getMessageId());
    }

    /**
     * Asking for one conversation answers with that conversation alone,
     * only what is newer than the client holds, and nothing at all to an
     * account with no character in it. Sending only the newer lines is
     * what makes asking safe to repeat: the client appends what it is
     * sent without clearing.
     */
    @Test
    public void oneConversationIsReplayedOnItsOwnAndOnlyWhatIsNew() {
        long first = record(ChatChannel.FACTION, ALICE, "for Gondor",
                Arrays.asList(ALICE),
                ChatHistory.Audience.faction(GONDOR, false),
                GONDOR);
        long second = record(ChatChannel.FACTION, ALICE, "and again",
                Arrays.asList(ALICE),
                ChatHistory.Audience.faction(GONDOR, false),
                GONDOR);
        record(ChatChannel.FACTION, BOB, "for Mordor", Arrays.asList(BOB),
                ChatHistory.Audience.faction("MORDOR", false),
                "MORDOR");
        record(ChatChannel.GLOBAL, ALICE, "hello", Arrays.asList(ALICE),
                ChatHistory.Audience.everyone());

        java.util.Map<String, Long> gondor = new java.util.HashMap<String, Long>();
        gondor.put(GONDOR, Long.valueOf(SENT_AT - 1L));
        ChatHistory.Requester requester =
                new ChatHistory.Requester(CAROL, gondor, null, EVERY_CHANNEL);

        List<LostTalesChatMessagePacket> all = ChatHistory.replayForContext(
                requester, ChatChannel.FACTION, GONDOR, ChatMessageIds.NONE);
        assertEquals("Gondor's two lines and nothing else", 2, all.size());
        assertEquals(first, all.get(0).getMessageId());
        assertEquals(second, all.get(1).getMessageId());

        List<LostTalesChatMessagePacket> since = ChatHistory.replayForContext(
                requester, ChatChannel.FACTION, GONDOR, first);
        assertEquals(1, since.size());
        assertEquals(second, since.get(0).getMessageId());
        assertTrue("nothing is repeated once it is all held",
                ChatHistory.replayForContext(requester, ChatChannel.FACTION,
                        GONDOR, second).isEmpty());

        // A conversation the account has no character in answers nothing,
        // whatever it asks for.
        assertTrue(ChatHistory.replayForContext(requester, ChatChannel.FACTION,
                "MORDOR", ChatMessageIds.NONE).isEmpty());
        // And a conversation nobody named is nothing to ask about.
        assertTrue(ChatHistory.replayForContext(requester, ChatChannel.FACTION,
                "", ChatMessageIds.NONE).isEmpty());
        assertTrue(ChatHistory.replayForContext(null, ChatChannel.FACTION,
                GONDOR, ChatMessageIds.NONE).isEmpty());
    }

    /* ---- who spoke where ---- */

    @Test
    public void theOperatorChannelsVoicesAreTheAccountsThatSpokeInIt() {
        // Only an operator can post in the Operator channel, so its kept
        // lines name operators the world's own server does not list.
        record(ChatChannel.OPERATOR, ALICE, "restarting at nine",
                Arrays.asList(ALICE), ChatHistory.Audience.readers());
        record(ChatChannel.OPERATOR, ALICE, "back up",
                Arrays.asList(ALICE), ChatHistory.Audience.readers());
        record(ChatChannel.OOC, BOB, "lag?", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.everyone());

        Set<UUID> voices = ChatHistory.authorsIn(ChatChannel.OPERATOR);
        assertEquals(Collections.singleton(ALICE), voices);
        assertEquals(Collections.singleton(BOB),
                ChatHistory.authorsIn(ChatChannel.OOC));
        assertTrue(ChatHistory.authorsIn(ChatChannel.PARTY).isEmpty());
        assertTrue(ChatHistory.authorsIn(null).isEmpty());
    }

    /* ---- helpers ---- */

    private static ChatHistory.Requester requester(UUID account) {
        return new ChatHistory.Requester(account, "", 0L, null, EVERY_CHANNEL);
    }

    /** As below, with the conversation of a scoped channel stamped on the line. */
    /* ---- an edit that changes nothing ---- */

    @Test
    public void anEditToTheSameWordsIsNoEdit() {
        long id = record(ChatChannel.OOC, ALICE, "https://example.com/gif",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        assertNull("nobody is told of an edit that changes nothing",
                ChatHistory.applyEdit(id, ALICE, "https://example.com/gif"));
        assertNotNull(ChatHistory.applyEdit(id, ALICE, "a real edit"));
    }

    /* ---- reactions ---- */

    private static ChatHistory.Requester reader(UUID account) {
        return new ChatHistory.Requester(account, "", 0L, null, EVERY_CHANNEL);
    }

    @Test
    public void aReaderMayReactAndEveryReaderIsTold() {
        long id = record(ChatChannel.GLOBAL, ALICE, "hail",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatHistory.ReactionChange change = ChatHistory.react(id, reader(BOB),
                BOB, "Beren", "smile", true);
        assertNotNull(change);
        assertTrue(change.readers.contains(ALICE));
        assertFalse(change.before.players());
        assertTrue(change.after.players());
        assertNull("the same reaction twice changes nothing",
                ChatHistory.react(id, reader(BOB), BOB, "Beren", "smile", true));

        ChatReactionSummary forBob = ChatHistory.reactionsFor(id, BOB);
        assertTrue(forBob.find("smile").mine);
        assertFalse(ChatHistory.reactionsFor(id, ALICE).find("smile").mine);
    }

    @Test
    public void aReactionIsOnlyForThoseWhoMayReadTheLine() {
        long whisper = record(ChatChannel.WHISPER, ALICE, "the vault code",
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        assertNull(ChatHistory.react(whisper, reader(CAROL), CAROL, "Celeb",
                "smile", true));
        assertNotNull(ChatHistory.react(whisper, reader(BOB), BOB, "Beren",
                "smile", true));
        assertNull("a message not kept takes no reaction",
                ChatHistory.react(whisper + 99, reader(BOB), BOB, "Beren",
                        "smile", true));
    }

    @Test
    public void anEditKeepsTheReactions() {
        long id = record(ChatChannel.GLOBAL, ALICE, "hail",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatHistory.react(id, reader(BOB), BOB, "Beren", "smile", true);
        assertNotNull(ChatHistory.applyEdit(id, ALICE, "hail, friends"));
        assertEquals(1, ChatHistory.reactionsFor(id, BOB).find("smile").count);
    }

    @Test
    public void aReplayWearsTheReactionsAndMakesTheReplayedAReader() {
        long id = record(ChatChannel.GLOBAL, ALICE, "Alice joined the game",
                Arrays.asList(ALICE), ChatHistory.Audience.everyone());
        ChatHistory.react(id, reader(ALICE), ALICE, "Aldric", "smile", true);

        List<LostTalesChatMessagePacket> replay =
                ChatHistory.replayFor(reader(CAROL), 0L);
        assertEquals(1, replay.size());
        ChatReactionSummary.Reaction smile =
                replay.get(0).getReactions().find("smile");
        assertNotNull(smile);
        assertFalse(smile.mine);
        assertEquals("Aldric", smile.names.get(0));

        // Carol was handed the line, so a reaction made later reaches her.
        ChatHistory.ReactionChange change = ChatHistory.react(id, reader(BOB),
                BOB, "Beren", "smile", true);
        assertTrue(change.readers.contains(CAROL));
    }

    @Test
    public void aDiscordMemberReactsWithoutARequesterAndIsClearedAlone() {
        long id = record(ChatChannel.OOC, ALICE, "hail",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        UUID member = LostTalesChatMessagePacket.discordSenderId("42");
        ChatHistory.ReactionChange change = ChatHistory.react(id, null, member,
                "Nils", "smile", true);
        assertNotNull(change);
        assertFalse("a Discord member is no player", change.after.players());
        assertFalse(change.readers.contains(member));
        ChatHistory.react(id, reader(BOB), BOB, "Beren", "smile", true);
        assertNotNull(ChatHistory.clearDiscordReactions(id, null, "", "7"));
        assertEquals(1, ChatHistory.reactionsFor(id, BOB).find("smile").count);
        assertNull(ChatHistory.clearDiscordReactions(id, null, "", "7"));
    }

    @Test
    public void aPlayerJoinsAForeignEmojiButNeverBringsOne() {
        long id = record(ChatChannel.OOC, ALICE, "hail",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        String parrot = "partyparrot:556";
        assertNull("nobody on the message reacted with it yet",
                ChatHistory.react(id, reader(BOB), BOB, "Beren", parrot, true));
        assertNotNull(ChatHistory.react(id, null,
                LostTalesChatMessagePacket.discordSenderId("42"), "Nils",
                parrot, true));
        ChatHistory.ReactionChange change = ChatHistory.react(id, reader(BOB),
                BOB, "Beren", parrot, true);
        assertNotNull(change);
        assertFalse(change.before.players());
        assertTrue("the first player", change.after.players());
        assertNotNull(ChatHistory.clearDiscordReactions(id, parrot, "556", "7"));
        assertEquals(1, ChatHistory.reactionsFor(id, BOB).find(parrot).count);
        assertTrue(ChatHistory.reactionsFor(id, BOB).find(parrot).mine);
    }

    /** The custom emoji pepe:556 renamed on Discord to pepe_happy keeps its id. */
    @Test
    public void aDiscordReactionFollowsARenamedCustomEmojiByItsId() {
        long id = record(ChatChannel.OOC, ALICE, "hail",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        UUID first = LostTalesChatMessagePacket.discordSenderId("42");
        UUID second = LostTalesChatMessagePacket.discordSenderId("43");
        assertNotNull(ChatHistory.reactFromDiscord(id, first, "Nils",
                "pepe:556", "556", "7", true));

        assertNotNull("a reaction after the rename joins the chip",
                ChatHistory.reactFromDiscord(id, second, "Ana",
                        "pepe_happy:556", "556", "7", true));
        assertEquals(2, ChatHistory.reactionsFor(id, BOB).find("pepe:556").count);
        assertNull(ChatHistory.reactionsFor(id, BOB).find("pepe_happy:556"));

        assertNotNull("a removal after the rename finds it",
                ChatHistory.reactFromDiscord(id, first, "", "pepe_happy:556",
                        "556", "7", false));
        assertEquals(1, ChatHistory.reactionsFor(id, BOB).find("pepe:556").count);
        assertNull("a member with no reaction takes nothing back",
                ChatHistory.reactFromDiscord(id, first, "", null, "556", "7", false));

        assertNotNull("a removal without a name finds it by the id",
                ChatHistory.reactFromDiscord(id, second, "", null, "556", "7", false));
        assertNull(ChatHistory.reactionsFor(id, BOB).find("pepe:556"));
    }

    @Test
    public void aDiscordClearOfARenamedEmojiGoesByItsId() {
        long id = record(ChatChannel.OOC, ALICE, "hail",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatHistory.reactFromDiscord(id,
                LostTalesChatMessagePacket.discordSenderId("42"), "Nils",
                "pepe:556", "556", "7", true);
        ChatHistory.ReactionChange joined = ChatHistory.react(id, reader(BOB),
                BOB, "Beren", "pepe:556", true);
        assertNotNull("a player joins the key they were shown", joined);
        assertTrue(joined.after.players());

        assertNotNull(ChatHistory.clearDiscordReactions(id, "pepe_happy:556",
                "556", "7"));
        assertEquals(1, ChatHistory.reactionsFor(id, BOB).find("pepe:556").count);
        assertTrue(ChatHistory.reactionsFor(id, BOB).find("pepe:556").mine);
        assertNull("nothing of Discord's left to clear",
                ChatHistory.clearDiscordReactions(id, null, "556", "7"));
    }

    /**
     * Whisper history is kept per conversation: a busy pair cannot push
     * another pair's lines out of reach.
     */
    @Test
    public void whisperRetentionIsPerConversation() {
        int kept = LostTalesConfig.chatHistoryPerChannel;
        LostTalesConfig.chatHistoryPerChannel = 2;
        try {
            ChatHistory.Audience withBob = ChatHistory.Audience.accounts(
                    Arrays.asList(ALICE, BOB), false);
            long first = record(ChatChannel.WHISPER, ALICE, "one", Arrays.asList(ALICE, BOB), withBob);
            long aside = ChatMessageIdAllocator.next();
            ChatHistory.record(aside, ALICE, "Aldric", null,
                    line(aside, ChatChannel.WHISPER, ALICE, "aside").withPartner("carol", "carol"),
                    Arrays.asList(ALICE, CAROL),
                    ChatHistory.Audience.accounts(Arrays.asList(ALICE, CAROL), false));
            long second = record(ChatChannel.WHISPER, ALICE, "two", Arrays.asList(ALICE, BOB), withBob);
            long third = record(ChatChannel.WHISPER, ALICE, "three", Arrays.asList(ALICE, BOB), withBob);
            assertFalse("the pair's oldest line went",
                    ChatHistory.quoteFor(first, BOB, ChatChannel.WHISPER, "").exists());
            assertTrue(ChatHistory.quoteFor(second, BOB, ChatChannel.WHISPER, "").exists());
            assertTrue(ChatHistory.quoteFor(third, BOB, ChatChannel.WHISPER, "").exists());
            assertTrue("the other pair's line stays",
                    ChatHistory.quoteFor(aside, CAROL, ChatChannel.WHISPER, "").exists());
        } finally {
            LostTalesConfig.chatHistoryPerChannel = kept;
        }
    }

    /**
     * The Faction and Party channels hold several conversations, and each
     * faction and each party is charged its own budget: a busy faction
     * only ever pushes out its own lines.
     */
    @Test
    public void factionAndPartyRetentionIsPerFactionAndPerParty() {
        int kept = LostTalesConfig.chatHistoryPerChannel;
        LostTalesConfig.chatHistoryPerChannel = 2;
        try {
            ChatHistory.Audience gondor = ChatHistory.Audience.faction("gondor", false);
            long first = record(ChatChannel.FACTION, ALICE, "one",
                    Arrays.asList(ALICE), gondor, "gondor");
            long rohan = record(ChatChannel.FACTION, BOB, "horses",
                    Arrays.asList(BOB), ChatHistory.Audience.faction("rohan", false), "rohan");
            long second = record(ChatChannel.FACTION, ALICE, "two",
                    Arrays.asList(ALICE), gondor, "gondor");
            long third = record(ChatChannel.FACTION, ALICE, "three",
                    Arrays.asList(ALICE), gondor, "gondor");
            assertFalse("the faction's oldest line went",
                    ChatHistory.quoteFor(first, ALICE, ChatChannel.FACTION, "gondor").exists());
            assertTrue(ChatHistory.quoteFor(second, ALICE, ChatChannel.FACTION, "gondor").exists());
            assertTrue(ChatHistory.quoteFor(third, ALICE, ChatChannel.FACTION, "gondor").exists());
            assertTrue("the other faction's line stays",
                    ChatHistory.quoteFor(rohan, BOB, ChatChannel.FACTION, "rohan").exists());

            UUID otherParty = UUID.randomUUID();
            ChatHistory.Audience ours = ChatHistory.Audience.party(PARTY, Arrays.asList(ALICE, BOB));
            String ourScope = PARTY.toString();
            long formUp = record(ChatChannel.PARTY, ALICE, "form up",
                    Arrays.asList(ALICE, BOB), ours, ourScope);
            long theirs = record(ChatChannel.PARTY, CAROL, "we march",
                    Arrays.asList(CAROL), ChatHistory.Audience.party(otherParty, Arrays.asList(CAROL)),
                    otherParty.toString());
            record(ChatChannel.PARTY, ALICE, "hold", Arrays.asList(ALICE, BOB), ours, ourScope);
            record(ChatChannel.PARTY, ALICE, "charge", Arrays.asList(ALICE, BOB), ours, ourScope);
            assertFalse("the party's oldest line went",
                    ChatHistory.quoteFor(formUp, BOB, ChatChannel.PARTY, ourScope).exists());
            assertTrue("the other party's line stays",
                    ChatHistory.quoteFor(theirs, CAROL, ChatChannel.PARTY, otherParty.toString()).exists());
        } finally {
            LostTalesConfig.chatHistoryPerChannel = kept;
        }
    }

    /**
     * A whisper tab reaches back into the kept history like any other: the
     * page before a line, of the conversation the tab names, for one of
     * its two parties and nobody else.
     */
    @Test
    public void aWhisperConversationPagesItsOlderLinesForItsParties() {
        long older = record(ChatChannel.WHISPER, ALICE, "first", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        long newer = record(ChatChannel.WHISPER, ALICE, "second", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        String conversation = ChatTabIds.whisperConversationId("bob", "", "");
        List<LostTalesChatMessagePacket> page = ChatHistory.replayBefore(
                requester(ALICE), ChatChannel.WHISPER, conversation, newer);
        assertEquals(1, page.size());
        assertEquals(older, page.get(0).getMessageId());
        assertTrue("another conversation answers with nothing",
                ChatHistory.replayBefore(requester(ALICE), ChatChannel.WHISPER,
                        ChatTabIds.whisperConversationId("carol", "", ""), newer).isEmpty());
        assertTrue("someone who was not sent it is answered with nothing",
                ChatHistory.replayBefore(requester(CAROL), ChatChannel.WHISPER,
                        conversation, newer).isEmpty());
    }

    /**
     * A join line is recorded before the server lists the player it
     * announces, so it names nobody; at their login they are named on
     * it, afresh if need be, and a reader shown it later sees the
     * identity they were playing. A player's own line names nobody
     * this way.
     */
    @Test
    public void namingAPlayerOnAServerLineReachesLaterReaders() {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(
                ChatChannel.GLOBAL, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                "Server", "Server", "", 0, 0, "alice joined the game", SENT_AT,
                "", null, "", "", 0, true, id, ChatReplyReference.NONE, "")
                .withServerBody("{}", Collections.<ChatNamedPlayer>emptyList());
        ChatHistory.record(id, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                "Server", null, line, Collections.singletonList(BOB),
                ChatHistory.Audience.everyone());
        ChatNamedPlayer asAccount = new ChatNamedPlayer(ALICE, "alice", null,
                "alice", "");
        ChatHistory.namePlayer(id, asAccount);
        ChatHistory.namePlayer(id, new ChatNamedPlayer(ALICE, "alice",
                UUID.randomUUID(), "Aldric", "human/male/1"));
        List<LostTalesChatMessagePacket> replay = ChatHistory.replayFor(
                requester(CAROL), ChatMessageIds.NONE);
        assertEquals(1, replay.size());
        List<ChatNamedPlayer> named = replay.get(0).getNamedPlayers();
        assertEquals(1, named.size());
        assertEquals("Aldric", named.get(0).getIdentityName());
        assertEquals("human/male/1", named.get(0).getSkinId());
        long own = record(ChatChannel.GLOBAL, ALICE, "hello", Arrays.asList(BOB),
                ChatHistory.Audience.everyone());
        ChatHistory.namePlayer(own, asAccount);
        replay = ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE);
        assertEquals(2, replay.size());
        assertTrue(replay.get(1).getNamedPlayers().isEmpty());
    }

    /**
     * A reader may forward what they may read, in the words the server
     * holds, named by its link and author; a line they were never shown
     * and the Server's own are not theirs to carry on.
     */
    @Test
    public void aForwardCarriesWhatTheReaderMayRead() {
        long id = record(ChatChannel.OOC, ALICE, "meet at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatHistory.Forwardable forward = ChatHistory.forwardable(id,
                reader(BOB));
        assertNotNull(forward);
        assertEquals("meet at the gate", forward.text);
        assertTrue(forward.reference.isForward());
        assertEquals("#ooc/" + id, forward.reference.getForwardedFrom());
        assertEquals("Aldric", forward.reference.getAuthor());
        assertEquals("", forward.reference.getExcerpt());
        assertEquals(ALICE, forward.reference.getSenderId());

        long whisper = record(ChatChannel.WHISPER, ALICE, "the vault code",
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        assertNull(ChatHistory.forwardable(whisper, reader(CAROL)));
        assertNull("a message not kept is nothing to forward",
                ChatHistory.forwardable(id + 99, reader(BOB)));

        long server = record(ChatChannel.OOC,
                LostTalesChatMessagePacket.SERVER_SENDER_ID, "Server restarting",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        assertNull(ChatHistory.forwardable(server, reader(BOB)));
    }

    private static long record(ChatChannel channel, UUID author, String text,
                               List<UUID> sentTo, ChatHistory.Audience audience,
                               String scopeValue) {
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, author, "Aldric", null,
                line(id, channel, author, text).withScope(scopeValue),
                sentTo, audience);
        return id;
    }

    private static long record(ChatChannel channel, UUID author, String text,
                               List<UUID> sentTo, ChatHistory.Audience audience) {
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, author, "Aldric", null, line(id, channel, author, text),
                sentTo, audience);
        return id;
    }

    private static LostTalesChatMessagePacket line(long id, ChatChannel channel,
                                                   UUID author, String text) {
        return new LostTalesChatMessagePacket(channel, author, "Aldric", "alice", "",
                0, 0, text, SENT_AT, "", null, "",
                channel == ChatChannel.WHISPER ? "bob" : "", 0, false, id,
                ChatReplyReference.NONE, "");
    }
}
