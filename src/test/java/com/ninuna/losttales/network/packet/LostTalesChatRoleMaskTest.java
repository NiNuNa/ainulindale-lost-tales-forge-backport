package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The role mask rides at the end of the message wire layout. */
public final class LostTalesChatRoleMaskTest {

    @Test
    public void roleMaskRoundTripsAndDefaultsToNone() {
        int roles = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR,
                ChatAccountRole.DEVELOPER);
        LostTalesChatMessagePacket tagged = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", roles);
        ByteBuf buffer = Unpooled.buffer();
        tagged.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(roles, decoded.getRoles());

        LostTalesChatMessagePacket plain = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
        buffer = Unpooled.buffer();
        plain.toBytes(buffer);
        decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());

        // A payload cut before the mask is malformed, never half-read.
        buffer = Unpooled.buffer();
        tagged.toBytes(buffer);
        ByteBuf truncated = buffer.readSlice(buffer.readableBytes() - 1);
        decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(truncated);
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
    }

    /** A mask naming a role this build does not know is refused. */
    @Test(expected = IllegalArgumentException.class)
    public void unknownRoleBitsAreRefused() {
        new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", 0x80);
    }

    @Test
    public void unknownRoleBitsOnTheWireAreMalformed() {
        LostTalesChatMessagePacket plain = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "");
        ByteBuf buffer = Unpooled.buffer();
        plain.toBytes(buffer);
        // The roles byte sits before the trailing account-line flag.
        buffer.setByte(buffer.writerIndex() - 2, 0x80);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
    }
}
