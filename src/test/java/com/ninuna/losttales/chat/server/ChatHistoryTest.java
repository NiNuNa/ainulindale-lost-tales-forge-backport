package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
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
    public void aRecipientIsQuotedTheMessageTheyWereSent() {
        long id = record(ChatChannel.ALL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        ChatReplyReference quote = ChatHistory.quoteFor(id, BOB);
        assertTrue(quote.exists());
        assertEquals(id, quote.getMessageId());
        assertEquals("Aldric", quote.getAuthor());
        assertEquals("meet me at the gate", quote.getExcerpt());
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

    @Test
    public void someoneWhoWasNotSentItIsQuotedNothing() {
        long id = record(ChatChannel.WHISPER, ALICE, "the key is under the barrel",
                Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), false));
        assertFalse(ChatHistory.quoteFor(id, CAROL).exists());
        assertFalse(ChatHistory.quoteFor(id, null).exists());
        assertFalse(ChatHistory.quoteFor(1234L, ALICE).exists());
        assertFalse(ChatHistory.quoteFor(ChatMessageIds.NONE, ALICE).exists());
    }

    @Test
    public void unnamedOrUnsignedMessagesAreNotRecorded() {
        ChatHistory.record(ChatMessageIds.NONE, ALICE, "Aldric",
                null, line(ChatMessageIdAllocator.next(), ChatChannel.ALL, ALICE, "hello"),
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        long id = ChatMessageIdAllocator.next();
        ChatHistory.record(id, ALICE, "  ", null, line(id, ChatChannel.ALL, ALICE, "hello"),
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
        long id = record(ChatChannel.ALL, ALICE, long_.toString(),
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        String excerpt = ChatHistory.quoteFor(id, ALICE).getExcerpt();
        assertTrue(excerpt.length() < ChatReplyReference.MAX_EXCERPT_CHARACTERS + 5);
        assertTrue(excerpt.endsWith("..."));
    }

    @Test
    public void onlyTheAuthorMayChangeAMessage() {
        long id = record(ChatChannel.ALL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        assertNull(ChatHistory.applyEdit(id, BOB, "meet me at the tower"));
        assertNull(ChatHistory.remove(id, BOB));
        assertNull(ChatHistory.applyEdit(id, null, "nobody at all"));
        assertEquals("meet me at the gate", ChatHistory.quoteFor(id, BOB).getExcerpt());
    }

    /** An edit reaches exactly who was sent the original, and the replay says the new words. */
    @Test
    public void anEditIsToldToEveryoneWhoWasSentTheMessageAndReplaysEdited() {
        long id = record(ChatChannel.ALL, ALICE, "meet me at the gate",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        Set<UUID> told = ChatHistory.applyEdit(id, ALICE, "meet me at the tower");
        assertNotNull(told);
        assertTrue(told.contains(ALICE));
        assertTrue(told.contains(BOB));
        assertFalse(told.contains(CAROL));
        assertEquals("meet me at the tower", ChatHistory.quoteFor(id, BOB).getExcerpt());
        List<LostTalesChatMessagePacket> replay = ChatHistory.replayFor(
                requester(CAROL), ChatMessageIds.NONE);
        assertEquals(1, replay.size());
        assertEquals("meet me at the tower", replay.get(0).getMessage());
    }

    @Test
    public void aRemovedMessageIsGoneForGood() {
        long id = record(ChatChannel.ALL, ALICE, "forget I said that",
                Arrays.asList(ALICE, BOB), ChatHistory.Audience.everyone());
        Set<UUID> told = ChatHistory.remove(id, ALICE);
        assertNotNull(told);
        assertTrue(told.contains(BOB));
        assertFalse(ChatHistory.quoteFor(id, BOB).exists());
        assertNull(ChatHistory.applyEdit(id, ALICE, "or that"));
        assertTrue(ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).isEmpty());
        // A moderator's removal likewise, and it says whose the message was.
        long other = record(ChatChannel.ALL, BOB, "and that", Arrays.asList(ALICE, BOB),
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
        long oldestGlobal = record(ChatChannel.ALL, ALICE, "the first thing said",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        long oldestOoc = record(ChatChannel.OOC, ALICE, "and out of character",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        for (int index = 0; index < ChatHistory.MAX_PER_CHANNEL; index++) {
            record(ChatChannel.ALL, ALICE, "and another",
                    Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        }
        assertFalse(ChatHistory.quoteFor(oldestGlobal, ALICE).exists());
        assertNull(ChatHistory.applyEdit(oldestGlobal, ALICE, "on reflection"));
        // The other channel's line was not the one to go.
        assertTrue(ChatHistory.quoteFor(oldestOoc, ALICE).exists());
        assertEquals(ChatHistory.MAX_PER_CHANNEL + 1, ChatHistory.size());
        ChatHistory.clear();
        assertEquals(0, ChatHistory.size());
    }

    /** A replay hands over the newest of a channel, and only so many. */
    @Test
    public void aReplayIsCappedPerChannelAndComesOldestFirst() {
        long first = record(ChatChannel.ALL, ALICE, "one",
                Collections.singletonList(ALICE), ChatHistory.Audience.everyone());
        for (int index = 0; index < ChatHistory.MAX_REPLAY_PER_CHANNEL; index++) {
            record(ChatChannel.ALL, ALICE, "more",
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
        LostTalesChatMessagePacket shared = line(id, ChatChannel.ALL, ALICE, "hail");
        LostTalesChatMessagePacket own = new LostTalesChatMessagePacket(ChatChannel.ALL,
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
                ChatHistory.Audience.faction(GONDOR, Arrays.asList(ALICE, BOB), false));
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

    /** A staff line reaches who was sent it and may still read the channel. */
    @Test
    public void aGatedLineNeedsThenAndNow() {
        record(ChatChannel.ADMIN, ALICE, "staff only", Arrays.asList(ALICE, BOB),
                ChatHistory.Audience.accounts(Arrays.asList(ALICE, BOB), true));
        assertEquals(1, ChatHistory.replayFor(requester(BOB), ChatMessageIds.NONE).size());
        // Bob was an operator then but is not any more.
        assertTrue(ChatHistory.replayFor(
                new ChatHistory.Requester(BOB, "", 0L, null,
                        Collections.singletonList(ChatChannel.ALL)),
                ChatMessageIds.NONE).isEmpty());
        // Carol is an operator now but was not sent it.
        assertTrue(ChatHistory.replayFor(requester(CAROL), ChatMessageIds.NONE).isEmpty());
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

    /* ---- helpers ---- */

    private static ChatHistory.Requester requester(UUID account) {
        return new ChatHistory.Requester(account, "", 0L, null, EVERY_CHANNEL);
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
