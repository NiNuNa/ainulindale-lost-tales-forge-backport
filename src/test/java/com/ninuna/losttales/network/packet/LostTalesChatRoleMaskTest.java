package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
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
        ChatRoleCatalog.install(ChatRoleFixtures.catalogue());
    }

    @After
    public void tearDown() {
        ChatRoleCatalog.resetToBuiltIn();
    }

    @Test
    public void roleMaskRoundTripsAndDefaultsToNone() {
        int roles = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR,
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
        ByteBuf truncated = buffer.readSlice(
                buffer.readableBytes() - scopeTailBytes("") - 1);
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
        assertEquals(1 << 8, ninth.bit());
        int roles = ChatAccountRole.maskOf(ninth, ChatRoleFixtures.OPERATOR);
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
                ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR));
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        // The whole mask is the int ahead of the three id tails: a bit no role
        // occupies, planted there, is refused.
        buffer.setInt(buffer.writerIndex() - scopeTailBytes("") - 4
                - 3 * LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES,
                0x40000000 | ChatRoleFixtures.OPERATOR.bit());
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer.copy());
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
        // And the two copies of the mask must agree.
        buffer.setInt(buffer.writerIndex() - scopeTailBytes("") - 4
                - 3 * LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES,
                ChatAccountRole.TEAM.bit());
        LostTalesChatMessagePacket disagreeing = new LostTalesChatMessagePacket();
        disagreeing.fromBytes(buffer);
        assertTrue(disagreeing.isMalformed());
    }

    @Test
    public void aLayoutWithoutTheTailReadsTheByte() {
        LostTalesChatMessagePacket roled = new LostTalesChatMessagePacket(
                ChatChannel.OOC, UUID.randomUUID(), "Steve", "Steve", "",
                0xFFFFFF, 0xFFFFFF, "hello", 1L, "", null, "", "",
                ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR));
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer.slice(0, buffer.readableBytes()
                - scopeTailBytes("") - 4
                - 3 * LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES));
        assertFalse(decoded.isMalformed());
        assertEquals(ChatRoleFixtures.OPERATOR.bit(), decoded.getRoles());
        assertEquals(Collections.singletonList(ChatRoleFixtures.OPERATOR),
                ChatAccountRole.fromMask(decoded.getRoles()));
    }

    /**
     * How many bytes the conversation a line belongs to takes at the end
     * of the payload. Measured rather than assumed, so a test that walks
     * back from the end of a packet keeps saying what it means when
     * another field is appended after this one.
     */
    private static int scopeTailBytes(String scopeValue) {
        ByteBuf probe = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(probe, scopeValue, 128);
        // Behind the scope, for a line quoting nothing: the empty quote
        // of a line nobody named (author, words, colour), then the
        // quote's head, a server line's component and its named
        // players, every one of them empty.
        LostTalesPacketCodec.writeUtf8String(probe, "", 256);
        LostTalesPacketCodec.writeUtf8String(probe, "", 297);
        probe.writeInt(0);
        probe.writeBoolean(false);
        probe.writeLong(0L);
        probe.writeLong(0L);
        probe.writeBoolean(false);
        LostTalesPacketCodec.writeUtf8String(probe, "", 128);
        LostTalesPacketCodec.writeUtf8String(probe, "", 8192);
        probe.writeInt(0);
        // And the reactions, none, and the tab a command's answer is
        // filed under, none.
        probe.writeInt(0);
        LostTalesPacketCodec.writeUtf8String(probe, "", 384);
        return probe.readableBytes();
    }
}
