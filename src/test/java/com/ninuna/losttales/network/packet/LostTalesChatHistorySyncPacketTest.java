package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatReplyReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A history batch carries whole message packets, oldest first, and is
 * refused whole when any line in it is not one.
 */
public final class LostTalesChatHistorySyncPacketTest {

    @Test
    public void aBatchRoundTripsInOrder() {
        LostTalesChatHistorySyncPacket packet = new LostTalesChatHistorySyncPacket(
                Arrays.asList(line(10L, "first"), line(11L, "second")));
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatHistorySyncPacket decoded = new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getMessages().size());
        assertEquals(10L, decoded.getMessages().get(0).getMessageId());
        assertEquals("first", decoded.getMessages().get(0).getMessage());
        assertEquals("second", decoded.getMessages().get(1).getMessage());
        assertEquals(ChatChannel.OOC, decoded.getMessages().get(1).getChannel());
        assertEquals("Aldric", decoded.getMessages().get(1).getIdentityName());
    }

    @Test
    public void anEmptyBatchIsAllowedAndMoreThanTheCapIsCut() {
        LostTalesChatHistorySyncPacket empty = new LostTalesChatHistorySyncPacket(
                Collections.<LostTalesChatMessagePacket>emptyList());
        ByteBuf buffer = Unpooled.buffer();
        empty.toBytes(buffer);
        LostTalesChatHistorySyncPacket decoded = new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getMessages().isEmpty());

        List<LostTalesChatMessagePacket> many = new ArrayList<LostTalesChatMessagePacket>();
        for (int index = 0; index < LostTalesChatHistorySyncPacket.MAX_MESSAGES + 5; index++) {
            many.add(line(100L + index, "line " + index));
        }
        assertEquals(LostTalesChatHistorySyncPacket.MAX_MESSAGES,
                new LostTalesChatHistorySyncPacket(many).getMessages().size());
    }

    @Test
    public void aBadLineTooManyLinesOrTrailingDataRefusesTheBatch() {
        // A count past the cap.
        ByteBuf tooMany = Unpooled.buffer();
        tooMany.writeInt(LostTalesChatHistorySyncPacket.MAX_MESSAGES + 1);
        LostTalesChatHistorySyncPacket decoded = new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(tooMany);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getMessages().isEmpty());

        // A line that is not a message.
        ByteBuf badLine = Unpooled.buffer();
        badLine.writeInt(1);
        badLine.writeByte(3);
        badLine.writeBytes(new byte[] {1, 2, 3});
        decoded = new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(badLine);
        assertTrue(decoded.isMalformed());

        // Trailing bytes after a good batch.
        LostTalesChatHistorySyncPacket packet = new LostTalesChatHistorySyncPacket(
                Collections.singletonList(line(10L, "first")));
        ByteBuf trailing = Unpooled.buffer();
        packet.toBytes(trailing);
        trailing.writeByte(0);
        decoded = new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(trailing);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getMessages().isEmpty());
    }

    /**
     * The login replay says where its reader arrived, as the id of their
     * own join line: a line with a smaller id is history to them, that
     * line and every later one were said as they arrived — whatever the
     * lines' clocks say. Any other batch is all history.
     */
    @Test
    public void aReplaySaysWhereItsReaderArrived() {
        LostTalesChatHistorySyncPacket packet = new LostTalesChatHistorySyncPacket(
                Arrays.asList(line(10L, "first", 9000L),
                        line(11L, "joined", 1000L)), 11L);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatHistorySyncPacket decoded = new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(11L, decoded.getArrivalId());
        assertTrue(decoded.saidBeforeArrival(decoded.getMessages().get(0)));
        assertFalse(decoded.saidBeforeArrival(decoded.getMessages().get(1)));

        LostTalesChatHistorySyncPacket page = new LostTalesChatHistorySyncPacket(
                Collections.singletonList(line(Long.MAX_VALUE - 1L, "older")));
        assertEquals(Long.MAX_VALUE, page.getArrivalId());
        assertTrue(page.saidBeforeArrival(page.getMessages().get(0)));

        // A batch written without it is history, as it was then.
        buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatHistorySyncPacket older = new LostTalesChatHistorySyncPacket();
        older.fromBytes(buffer.slice(0, buffer.readableBytes() - 8));
        assertFalse(older.isMalformed());
        assertEquals(Long.MAX_VALUE, older.getArrivalId());
        assertTrue(older.saidBeforeArrival(older.getMessages().get(1)));
    }

    private static LostTalesChatMessagePacket line(long id, String text) {
        return line(id, text, 5000L);
    }

    private static LostTalesChatMessagePacket line(long id, String text,
                                                   long timestampMillis) {
        return new LostTalesChatMessagePacket(ChatChannel.OOC, UUID.randomUUID(),
                "Aldric", "alice", "", 0, 0, text, timestampMillis, "", null, "", "",
                0, true, id, ChatReplyReference.NONE, "");
    }
}
