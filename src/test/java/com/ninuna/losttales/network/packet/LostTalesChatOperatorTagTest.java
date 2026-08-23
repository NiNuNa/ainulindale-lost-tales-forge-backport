package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The operator flag rides at the end of the message wire layout. */
public final class LostTalesChatOperatorTagTest {

    @Test
    public void operatorFlagRoundTripsAndDefaultsToFalse() {
        LostTalesChatMessagePacket tagged = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", true);
        ByteBuf buffer = Unpooled.buffer();
        tagged.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isOperator());

        LostTalesChatMessagePacket plain = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
        buffer = Unpooled.buffer();
        plain.toBytes(buffer);
        decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.isOperator());

        // A payload cut before the flag is malformed, never half-read.
        buffer = Unpooled.buffer();
        tagged.toBytes(buffer);
        ByteBuf truncated = buffer.readSlice(buffer.readableBytes() - 1);
        decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(truncated);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.isOperator());
    }
}
