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

    private static LostTalesChatMessagePacket line(long id, String text) {
        return new LostTalesChatMessagePacket(ChatChannel.OOC, UUID.randomUUID(),
                "Aldric", "alice", "", 0, 0, text, 5000L, "", null, "", "", 0, true,
                id, ChatReplyReference.NONE, "");
    }
}
