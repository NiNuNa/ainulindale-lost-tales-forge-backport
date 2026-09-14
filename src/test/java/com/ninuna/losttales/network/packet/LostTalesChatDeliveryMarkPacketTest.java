package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.DedicatedServerIsolation;
import com.ninuna.losttales.chat.ChatDeliveryMark;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.IOException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A delivery mark is ten bytes — a message id, a state and a reason —
 * and a payload that is not exactly that is never applied.
 */
public final class LostTalesChatDeliveryMarkPacketTest {

    private static LostTalesChatDeliveryMarkPacket decode(ByteBuf buffer) {
        try {
            LostTalesChatDeliveryMarkPacket decoded =
                    new LostTalesChatDeliveryMarkPacket();
            decoded.fromBytes(buffer);
            assertFalse("the decoder leaves nothing behind", buffer.isReadable());
            return decoded;
        } finally {
            buffer.release();
        }
    }

    private static ByteBuf raw(long messageId, int state, int reason) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeLong(messageId);
        buffer.writeByte(state);
        buffer.writeByte(reason);
        return buffer;
    }

    @Test
    public void everyMarkRoundTripsInTenBytes() {
        for (ChatDeliveryMark.State state : ChatDeliveryMark.State.values()) {
            for (ChatDeliveryMark.Reason reason : ChatDeliveryMark.Reason.values()) {
                if (reason == ChatDeliveryMark.Reason.UNKNOWN) {
                    continue;
                }
                ByteBuf buffer = Unpooled.buffer();
                new LostTalesChatDeliveryMarkPacket(1234L, state, reason)
                        .toBytes(buffer);
                assertEquals(10, buffer.readableBytes());
                LostTalesChatDeliveryMarkPacket decoded = decode(buffer);
                assertFalse(decoded.isMalformed());
                assertEquals(1234L, decoded.getMessageId());
                assertEquals(state, decoded.getState());
                assertEquals(reason, decoded.getReason());
            }
        }
    }

    /** The codes are wire surface: each keeps the number it shipped with. */
    @Test
    public void theCodesKeepTheirNumbers() {
        assertEquals(0, ChatDeliveryMark.State.NONE.code());
        assertEquals(1, ChatDeliveryMark.State.RETRYING.code());
        assertEquals(2, ChatDeliveryMark.State.FAILED.code());
        ChatDeliveryMark.Reason[] inOrder = {
                ChatDeliveryMark.Reason.NONE,
                ChatDeliveryMark.Reason.WAITING,
                ChatDeliveryMark.Reason.LIMITED,
                ChatDeliveryMark.Reason.FAILING,
                ChatDeliveryMark.Reason.REFUSED,
                ChatDeliveryMark.Reason.GAVE_UP,
                ChatDeliveryMark.Reason.WEBHOOK_OFF,
                ChatDeliveryMark.Reason.QUEUE_FULL,
                ChatDeliveryMark.Reason.STOPPED
        };
        for (int code = 0; code < inOrder.length; code++) {
            assertEquals(code, inOrder[code].code());
            assertEquals(inOrder[code], ChatDeliveryMark.Reason.fromCode(code));
        }
        assertEquals("", ChatDeliveryMark.State.NONE.langKey());
        assertEquals("", ChatDeliveryMark.Reason.NONE.langKey());
    }

    @Test
    public void aPayloadThatIsNotTenBytesIsMalformed() {
        ByteBuf nine = Unpooled.buffer();
        nine.writeLong(1234L);
        nine.writeByte(1);
        LostTalesChatDeliveryMarkPacket decoded = decode(nine);
        assertTrue(decoded.isMalformed());
        assertEquals(ChatDeliveryMark.State.NONE, decoded.getState());

        ByteBuf eleven = raw(1234L, 1, 1);
        eleven.writeByte(0);
        assertTrue(decode(eleven).isMalformed());
    }

    @Test
    public void anIdTheServerNeverHandsOutIsMalformed() {
        assertTrue(decode(raw(0L, 2, 4)).isMalformed());
        assertTrue(decode(raw(-7L, 2, 4)).isMalformed());
    }

    @Test
    public void anUnknownStateIsMalformedAndAnUnknownReasonReadsAsUnknown() {
        assertTrue(decode(raw(1234L, 3, 4)).isMalformed());
        assertTrue(decode(raw(1234L, -1, 4)).isMalformed());
        LostTalesChatDeliveryMarkPacket newer = decode(raw(1234L, 2, 200));
        assertFalse(newer.isMalformed());
        assertEquals(ChatDeliveryMark.State.FAILED, newer.getState());
        assertEquals(ChatDeliveryMark.Reason.UNKNOWN, newer.getReason());
    }

    @Test
    public void aMarkThatCouldNotBeSentIsNeverBuilt() {
        refuse(0L, ChatDeliveryMark.State.RETRYING, ChatDeliveryMark.Reason.WAITING);
        refuse(-1L, ChatDeliveryMark.State.RETRYING, ChatDeliveryMark.Reason.WAITING);
        refuse(1L, null, ChatDeliveryMark.Reason.WAITING);
        refuse(1L, ChatDeliveryMark.State.FAILED, null);
        refuse(1L, ChatDeliveryMark.State.FAILED, ChatDeliveryMark.Reason.UNKNOWN);
    }

    private static void refuse(long messageId, ChatDeliveryMark.State state,
                               ChatDeliveryMark.Reason reason) {
        try {
            new LostTalesChatDeliveryMarkPacket(messageId, state, reason);
            fail("the mark is never built");
        } catch (IllegalArgumentException expected) {
            // Nothing that could be sent.
        }
    }

    @Test
    public void aDedicatedServerCanLoadTheMark() throws IOException {
        DedicatedServerIsolation.assertServerSafe(
                LostTalesChatDeliveryMarkPacket.class,
                LostTalesChatDeliveryMarkPacket.Handler.class,
                ChatDeliveryMark.class,
                ChatDeliveryMark.State.class,
                ChatDeliveryMark.Reason.class);
    }
}
