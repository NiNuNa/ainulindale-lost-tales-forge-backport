package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A reply to a line heard nearby (CHAT-43): it may be answered where it
 * was said, and its quote reaches only the readers who heard that line;
 * everyone else reads the reply alone, live and in every replay. A
 * forward of such a line still goes only into a whisper (CHAT-44).
 */
public final class ChatNearbyQuoteTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final long SENT_AT = 1000000L;

    @Before
    @After
    public void clear() {
        ChatHistory.clear();
    }

    @Test
    public void aLineHeardNearbyIsAnsweredWhereItWasSaid() {
        long near = record(ChatChannel.PROXIMITY, ALICE, "over here",
                Arrays.asList(ALICE, BOB), ChatReplyReference.NONE, null);
        assertTrue("Bob heard it", ChatHistory.mayQuoteIn(near,
                ChatChannel.PROXIMITY, "", ChatHistoryRequesters.reader(BOB), null));
        assertFalse("Carol did not", ChatHistory.mayQuoteIn(near,
                ChatChannel.PROXIMITY, "", ChatHistoryRequesters.reader(CAROL), null));
        assertFalse("never into another channel", ChatHistory.mayQuoteIn(near,
                ChatChannel.GLOBAL, "", ChatHistoryRequesters.reader(BOB), null));
        assertFalse("a forward still goes only into a whisper", ChatHistory.mayShowTo(
                near, ChatChannel.PROXIMITY, "", ChatHistoryRequesters.reader(BOB), null));
        ChatReplyReference quote = ChatHistory.quoteFor(near,
                ChatHistoryRequesters.reader(BOB), ChatChannel.PROXIMITY, "");
        assertEquals("over here", quote.getExcerpt());
    }

    @Test
    public void onlyWhoHeardTheLineIsShownItsQuote() {
        long near = record(ChatChannel.PROXIMITY, ALICE, "over here",
                Arrays.asList(ALICE, BOB), ChatReplyReference.NONE, null);
        ChatReplyReference quote = ChatHistory.quoteFor(near,
                ChatHistoryRequesters.reader(BOB), ChatChannel.PROXIMITY, "");
        assertTrue(ChatHistory.quotesNearby(quote));
        assertTrue(ChatHistory.showsQuoteTo(quote, ChatHistoryRequesters.reader(BOB)));
        assertFalse(ChatHistory.showsQuoteTo(quote, ChatHistoryRequesters.reader(CAROL)));

        long open = record(ChatChannel.GLOBAL, ALICE, "hail all",
                Arrays.asList(ALICE, BOB), ChatReplyReference.NONE, null);
        ChatReplyReference everyone = ChatHistory.quoteFor(open,
                ChatHistoryRequesters.reader(BOB), ChatChannel.GLOBAL, "");
        assertFalse(ChatHistory.quotesNearby(everyone));
        assertTrue(ChatHistory.showsQuoteTo(everyone,
                ChatHistoryRequesters.reader(CAROL)));
        assertTrue("a line of nobody's names no kept line",
                ChatHistory.showsQuoteTo(ChatReplyReference.UNKEPT,
                        ChatHistoryRequesters.reader(CAROL)));
    }

    @Test
    public void aQuoteOfALineNoLongerKeptIsShownToNobody() {
        ChatReplyReference gone = ChatReplyReference.of(
                ChatMessageIdAllocator.next(), "Aldric", "over here");
        assertFalse(ChatHistory.quotesNearby(gone));
        assertFalse(ChatHistory.showsQuoteTo(gone, ChatHistoryRequesters.reader(BOB)));
    }

    @Test
    public void theReplayLeavesTheQuoteOffForWhoNeverHeardIt() {
        long near = record(ChatChannel.PROXIMITY, ALICE, "over here",
                Arrays.asList(ALICE, BOB), ChatReplyReference.NONE, null);
        ChatReplyReference quote = ChatHistory.quoteFor(near,
                ChatHistoryRequesters.reader(BOB), ChatChannel.PROXIMITY, "");
        // Bob answers with Carol beside him, who never heard Alice.
        long reply = record(ChatChannel.PROXIMITY, BOB, "coming",
                Arrays.asList(BOB, CAROL), quote, Arrays.asList(BOB));

        LostTalesChatMessagePacket toCarol = replayed(CAROL, reply);
        assertFalse("Carol reads the reply alone", toCarol.getReply().exists());
        assertEquals("coming", toCarol.getMessage());
        LostTalesChatMessagePacket toBob = replayed(BOB, reply);
        assertEquals("over here", toBob.getReply().getExcerpt());
    }

    @Test
    public void aReplyWhoseQuoteEveryoneReadsKeepsItInTheReplay() {
        long open = record(ChatChannel.GLOBAL, ALICE, "hail all",
                Arrays.asList(ALICE, BOB), ChatReplyReference.NONE, null);
        ChatReplyReference quote = ChatHistory.quoteFor(open,
                ChatHistoryRequesters.reader(BOB), ChatChannel.GLOBAL, "");
        long reply = record(ChatChannel.GLOBAL, BOB, "well met",
                Arrays.asList(BOB, CAROL), quote, null);
        assertEquals("hail all", replayed(CAROL, reply).getReply().getExcerpt());
    }

    @Test
    public void theReadersOfAQuoteAreKeptWithTheSave() {
        long near = record(ChatChannel.PROXIMITY, ALICE, "over here",
                Arrays.asList(ALICE, BOB), ChatReplyReference.NONE, null);
        ChatReplyReference quote = ChatHistory.quoteFor(near,
                ChatHistoryRequesters.reader(BOB), ChatChannel.PROXIMITY, "");
        long reply = record(ChatChannel.PROXIMITY, BOB, "coming",
                Arrays.asList(BOB, CAROL), quote, Arrays.asList(BOB));
        NBTTagCompound written = new NBTTagCompound();
        ChatHistoryNbtCodec.write(written, ChatHistory.snapshot(),
                ChatConsoleStream.snapshot(), ChatConsoleStream.reactionsSnapshot(),
                Collections.<NBTTagCompound>emptyList());
        ChatHistory.clear();
        ChatHistoryNbtCodec.ReadResult read = ChatHistoryNbtCodec.read(written);
        assertFalse(read.wasRepaired());
        assertEquals(2, ChatHistory.restore(read.getEntries()));
        assertFalse(replayed(CAROL, reply).getReply().exists());
        assertTrue(replayed(BOB, reply).getReply().exists());
    }

    private static LostTalesChatMessagePacket replayed(UUID reader, long messageId) {
        for (LostTalesChatMessagePacket line : ChatHistory.replayFor(
                ChatHistoryRequesters.reader(reader), ChatMessageIds.NONE)) {
            if (line.getMessageId() == messageId) {
                return line;
            }
        }
        throw new AssertionError("not replayed to " + reader);
    }

    private static long record(ChatChannel channel, UUID author, String text,
                               List<UUID> sentTo, ChatReplyReference reply,
                               List<UUID> quoteReaders) {
        long id = ChatMessageIdAllocator.next();
        LostTalesChatMessagePacket line = new LostTalesChatMessagePacket(channel,
                author, "Aldric", "alice", "", 0, 0, text, SENT_AT, "", null, "",
                "", 0, false, id, reply, "");
        ChatHistory.record(id, author, "Aldric", null, line, sentTo,
                channel == ChatChannel.GLOBAL ? ChatHistory.Audience.everyone()
                        : ChatHistory.Audience.accounts(sentTo, false),
                quoteReaders);
        return id;
    }
}
