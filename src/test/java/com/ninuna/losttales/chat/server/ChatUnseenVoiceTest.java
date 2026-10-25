package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatNames;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatUnseenVoice;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import com.ninuna.losttales.util.EnglishWords;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * A wearer of the One Ring is an unseen voice (CHAT-84): its lines heard
 * nearby read as the voice, with no head, no name, no title and no
 * character, for every reader but the wearer and staff holding
 * {@code chat.moderate}, live, in replays, in quotes and in forwards.
 */
public final class ChatUnseenVoiceTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();
    private static final UUID CAROL = UUID.randomUUID();
    private static final UUID ALDRIC = UUID.randomUUID();
    private static final long SENT_AT = 1000000L;

    @Before
    @After
    public void clear() {
        ChatHistory.clear();
    }

    @Test
    public void onlyALineHeardNearbyFromAConcealedSpeakerIsUnseen() {
        assertTrue(ChatUnseenVoice.applies(ChatChannel.PROXIMITY, true, false));
        assertFalse("not concealed", ChatUnseenVoice.applies(ChatChannel.PROXIMITY, false, false));
        assertFalse("the Narrator names nobody already",
                ChatUnseenVoice.applies(ChatChannel.PROXIMITY, true, true));
        assertFalse("Global is heard by everyone online",
                ChatUnseenVoice.applies(ChatChannel.GLOBAL, true, false));
        assertFalse(ChatUnseenVoice.applies(ChatChannel.WHISPER, true, false));
        assertFalse(ChatUnseenVoice.applies(null, true, false));
    }

    @Test
    public void everyoneButTheSpeakerAndStaffReadsTheVoice() {
        LostTalesChatMessagePacket said = line(ALICE, "who goes there", ChatReplyReference.NONE)
                .withUnseen(true);
        LostTalesChatMessagePacket toBob = ChatReaderView.forReader(said, BOB, false, true);
        assertTrue(toBob.isUnseenVoice());
        assertEquals(ChatUnseenVoice.SENDER_ID, toBob.getSenderId());
        assertEquals(ChatUnseenVoice.NAME, toBob.getIdentityName());
        assertEquals(ChatUnseenVoice.NAME, toBob.getAccountName());
        assertEquals(ChatUnseenVoice.SKIN_ID, toBob.getSkinId());
        assertEquals("", toBob.getTitle());
        assertEquals("", toBob.getFactionId());
        assertNull(toBob.getIdentityCharacterId());
        assertEquals("who goes there", toBob.getMessage());
        assertTrue(toBob.isWellFormed());

        assertSame("the speaker reads their own line", said,
                ChatReaderView.forReader(said, ALICE, false, true));
        assertSame("staff read it as usual", said,
                ChatReaderView.forReader(said, CAROL, true, true));
        LostTalesChatMessagePacket plain = line(ALICE, "hello", ChatReplyReference.NONE);
        assertSame("a line nobody hid stays as it is", plain,
                ChatReaderView.forReader(plain, BOB, false, true));
    }

    @Test
    public void theMaskedLineTravelsAndNamesTheVoiceInEachGame() {
        LostTalesChatMessagePacket masked = line(ALICE, "who goes there",
                ChatReplyReference.NONE).withUnseen(true).unseenForOthers();
        LostTalesChatMessagePacket read = roundTrip(masked);
        assertFalse(read.isMalformed());
        assertTrue(read.isUnseen());
        assertTrue(read.isUnseenVoice());
        assertArrayEquals(bytesOf(masked), bytesOf(read));
        assertEquals("An unseen voice", ChatNames.sender(EnglishWords.INSTANCE,
                read.getSenderId(), read.getSkinId(), read.getIdentityName()));
        // The named copy travels too, marked, for the speaker and staff.
        LostTalesChatMessagePacket named = roundTrip(line(ALICE, "who goes there",
                ChatReplyReference.NONE).withUnseen(true));
        assertTrue(named.isUnseen());
        assertFalse(named.isUnseenVoice());
        assertEquals("Aldric", named.getIdentityName());
    }

    @Test
    public void noServerOrNarratorLineIsEverUnseen() {
        LostTalesChatMessagePacket server = new LostTalesChatMessagePacket(
                ChatChannel.PROXIMITY, LostTalesChatMessagePacket.SERVER_SENDER_ID,
                "Server", "Server", "", 0, 0, "the gates close", SENT_AT, "", null,
                "", "", 0, true, 7L, ChatReplyReference.NONE, "");
        assertFalse(server.withUnseen(true).isUnseen());
        assertFalse(server.withUnseen(true).unseenForOthers().isUnseenVoice());
    }

    @Test
    public void theHistoryKeepsTheSpeakerAndReplaysTheVoice() {
        long id = record(line(ALICE, "who goes there", ChatReplyReference.NONE)
                .withUnseen(true), Arrays.asList(ALICE, BOB, CAROL));
        assertTrue(replayed(ChatHistoryRequesters.reader(BOB), id).isUnseenVoice());
        assertFalse(replayed(ChatHistoryRequesters.reader(ALICE), id).isUnseenVoice());
        assertEquals("Aldric", replayed(ChatHistoryRequesters.reader(CAROL)
                .moderating(true), id).getIdentityName());
    }

    @Test
    public void aQuoteOfTheVoiceNamesTheVoiceAsTheLineDoes() {
        long id = record(line(ALICE, "who goes there", ChatReplyReference.NONE)
                .withUnseen(true), Arrays.asList(ALICE, BOB, CAROL));
        ChatReplyReference quote = ChatHistory.quoteFor(id,
                ChatHistoryRequesters.reader(BOB), ChatChannel.PROXIMITY, "");
        assertTrue(quote.isUnseen());
        assertEquals("the server keeps who spoke", "Aldric", quote.getAuthor());
        long reply = record(line(BOB, "show yourself", quote),
                Arrays.asList(ALICE, BOB, CAROL));

        ChatReplyReference toBob = replayed(ChatHistoryRequesters.reader(BOB), reply)
                .getReply();
        assertTrue(toBob.isUnseenVoice());
        assertEquals(ChatUnseenVoice.NAME, toBob.getAuthor());
        assertEquals(ChatUnseenVoice.SENDER_ID, toBob.getSenderId());
        assertEquals(ChatUnseenVoice.SKIN_ID, toBob.getSkinId());
        assertEquals("who goes there", toBob.getExcerpt());
        assertEquals("Aldric", replayed(ChatHistoryRequesters.reader(ALICE), reply)
                .getReply().getAuthor());
        assertEquals("Aldric", replayed(ChatHistoryRequesters.reader(CAROL)
                .moderating(true), reply).getReply().getAuthor());
        // The masked quote travels as it is.
        LostTalesChatMessagePacket travelled = roundTrip(
                replayed(ChatHistoryRequesters.reader(BOB), reply));
        assertFalse(travelled.isMalformed());
        assertTrue(travelled.getReply().isUnseenVoice());
    }

    @Test
    public void aForwardOfTheVoiceNamesTheVoice() {
        long id = record(line(ALICE, "who goes there", ChatReplyReference.NONE)
                .withUnseen(true), Arrays.asList(ALICE, BOB, CAROL));
        ChatHistory.Forwardable forward = ChatHistory.forwardable(id,
                ChatHistoryRequesters.reader(BOB));
        assertTrue(forward.reference.isUnseen());
        LostTalesChatMessagePacket carried = new LostTalesChatMessagePacket(
                ChatChannel.WHISPER, BOB, "Beren", "bob", "", 0, 0, forward.text,
                SENT_AT, "", null, "", "carol", 0, false, 9L, forward.reference, "");
        assertTrue(ChatReaderView.forReader(carried, CAROL, false, true)
                .getReply().isUnseenVoice());
        assertEquals("Aldric", ChatReaderView.forReader(carried, ALICE, false, true)
                .getReply().getAuthor());
    }

    @Test
    public void theMarkIsKeptWithTheSave() {
        LostTalesChatMessagePacket said = line(ALICE, "who goes there",
                ChatReplyReference.of(5L, "Aldric", "hm").withHead(ALICE, false, "")
                        .asUnseen(true)).withUnseen(true);
        LostTalesChatMessagePacket back = ChatLineNbtCodec.read(ChatLineNbtCodec.write(said));
        assertTrue(back.isUnseen());
        assertTrue(back.getReply().isUnseen());
        assertArrayEquals(bytesOf(said), bytesOf(back));
    }

    private static long record(LostTalesChatMessagePacket line, List<UUID> sentTo) {
        ChatHistory.record(line.getMessageId(), line.getSenderId(),
                line.getIdentityName(), null, line, sentTo,
                ChatHistory.Audience.accounts(sentTo, false));
        return line.getMessageId();
    }

    private static LostTalesChatMessagePacket replayed(ChatHistory.Requester reader,
                                                       long messageId) {
        for (LostTalesChatMessagePacket line : ChatHistory.replayFor(reader,
                ChatMessageIds.NONE)) {
            if (line.getMessageId() == messageId) {
                return line;
            }
        }
        throw new AssertionError("not replayed");
    }

    private static LostTalesChatMessagePacket line(UUID sender, String text,
                                                   ChatReplyReference reply) {
        long id = ChatMessageIdAllocator.next();
        return new LostTalesChatMessagePacket(ChatChannel.PROXIMITY, sender,
                sender.equals(ALICE) ? "Aldric" : "Beren",
                sender.equals(ALICE) ? "alice" : "bob", "lotr.title.ranger",
                0, 0, text, SENT_AT, "skin", null, "lotr:gondor", "", 0, false,
                id, reply, "", 0L, sender.equals(ALICE) ? ALDRIC : null);
    }

    private static LostTalesChatMessagePacket roundTrip(LostTalesChatMessagePacket line) {
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        LostTalesChatMessagePacket read = new LostTalesChatMessagePacket();
        read.fromBytes(buffer);
        return read;
    }

    private static byte[] bytesOf(LostTalesChatMessagePacket line) {
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.readBytes(bytes);
        return bytes;
    }
}
