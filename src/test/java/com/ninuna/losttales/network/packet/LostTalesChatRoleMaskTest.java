package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The role mask rides in the message layout twice: one byte where it
 * always was, and the whole int appended at the tail, so a config role
 * past the eighth bit travels too.
 */
public final class LostTalesChatRoleMaskTest {

    @Before
    public void setUp() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void roleMaskRoundTripsAndDefaultsToNone() {
        int roles = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR,
                ChatAccountRole.TEAM);
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

        // A payload cut short is malformed, never half-read.
        buffer = Unpooled.buffer();
        tagged.toBytes(buffer);
        ByteBuf truncated = buffer.readSlice(buffer.readableBytes() - 1);
        decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(truncated);
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
    }

    /** A role past the eighth bit travels in the appended int. */
    @Test
    public void aWideMaskTravelsWhole() {
        java.util.List<ChatAccountRole> custom = new java.util.ArrayList<ChatAccountRole>();
        for (int index = 0; index < 8; index++) {
            custom.add(ChatAccountRole.custom("role" + index, "Role " + index, "", "",
                    0, true, 20 + index, null));
        }
        ChatRoleCatalog.install(ChatRoleCatalog.of(custom, null, null));
        ChatAccountRole ninth = ChatAccountRole.byId("role7");
        assertEquals(1 << 9, ninth.bit());
        int roles = ChatAccountRole.maskOf(ninth, ChatAccountRole.OPERATOR);
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "", roles).toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(roles, decoded.getRoles());
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
        LostTalesChatMessagePacket roled = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "",
                ChatAccountRole.maskOf(ChatAccountRole.OPERATOR));
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        // The whole mask is the last int of the layout: a bit no role
        // occupies, planted there, is refused.
        buffer.setInt(buffer.writerIndex() - 4, 0x40000000 | ChatAccountRole.OPERATOR.bit());
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer.copy());
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
        // And the two copies of the mask must agree.
        buffer.setInt(buffer.writerIndex() - 4, ChatAccountRole.TEAM.bit());
        LostTalesChatMessagePacket disagreeing = new LostTalesChatMessagePacket();
        disagreeing.fromBytes(buffer);
        assertTrue(disagreeing.isMalformed());
    }

    @Test
    public void aLayoutWithoutTheTailReadsTheByte() {
        LostTalesChatMessagePacket roled = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "",
                ChatAccountRole.maskOf(ChatAccountRole.OPERATOR));
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer.slice(0, buffer.readableBytes() - 4));
        assertFalse(decoded.isMalformed());
        assertEquals(ChatAccountRole.OPERATOR.bit(), decoded.getRoles());
        assertEquals(Collections.singletonList(ChatAccountRole.OPERATOR),
                ChatAccountRole.fromMask(decoded.getRoles()));
    }
}
