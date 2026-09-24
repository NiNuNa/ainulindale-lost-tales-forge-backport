package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatReportReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** A report names a message the server gave an id, a reason and a short note; anything else is refused. */
public final class LostTalesChatReportPacketTest {
    private static final long MESSAGE = 1757522000000L;

    @Test
    public void aReportRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatReportPacket(MESSAGE, ChatReportReason.CHEATING,
                " flying about ").toBytes(buffer);
        LostTalesChatReportPacket decoded = new LostTalesChatReportPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(MESSAGE, decoded.getMessageId());
        assertEquals(ChatReportReason.CHEATING, decoded.getReason());
        assertEquals("flying about", decoded.getNote());
    }

    @Test
    public void aReportThatCannotBeFiledIsNeverBuilt() {
        assertRefused(0L, ChatReportReason.SPAM, "");
        assertRefused(-5L, ChatReportReason.SPAM, "");
        assertRefused(MESSAGE, null, "");
        assertRefused(MESSAGE, ChatReportReason.OTHER,
                "a note far longer than the sixty-four characters a report may carry");
    }

    @Test
    public void badBytesDecodeAsMalformed() {
        ByteBuf reason = Unpooled.buffer();
        reason.writeLong(MESSAGE);
        reason.writeByte(99);
        LostTalesPacketCodec.writeUtf8String(reason, "", 256);
        assertMalformed(reason);

        ByteBuf trailing = Unpooled.buffer();
        new LostTalesChatReportPacket(MESSAGE, ChatReportReason.SPAM, "")
                .toBytes(trailing);
        trailing.writeByte(1);
        assertMalformed(trailing);

        ByteBuf shortened = Unpooled.buffer();
        shortened.writeLong(MESSAGE);
        assertMalformed(shortened);
    }

    private static void assertRefused(long messageId, ChatReportReason reason,
                                      String note) {
        try {
            new LostTalesChatReportPacket(messageId, reason, note);
            fail("a report of " + messageId + " for " + reason + " was built");
        } catch (IllegalArgumentException expected) {
        }
    }

    private static void assertMalformed(ByteBuf buffer) {
        LostTalesChatReportPacket decoded = new LostTalesChatReportPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertNull(decoded.getReason());
    }
}
