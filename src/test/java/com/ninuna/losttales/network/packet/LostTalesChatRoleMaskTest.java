package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatRoleFixtures;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The role mask rides in the message layout as a whole int, so a config
 * role past the eighth bit travels too.
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
        LostTalesChatMessagePacket tagged = ChatPacketFixtures.line(
                ChatChannel.OOC, "Steve", "Steve", "hello").roles(roles).build();
        ByteBuf buffer = Unpooled.buffer();
        tagged.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(roles, decoded.getRoles());

        LostTalesChatMessagePacket plain = ChatPacketFixtures.line(
                ChatChannel.OOC, "Steve", "Steve", "hello").build();
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

    /** A role past the eighth bit travels too. */
    @Test
    public void aWideMaskTravelsWhole() {
        java.util.List<ChatAccountRole> custom = new java.util.ArrayList<ChatAccountRole>();
        for (int index = 0; index < 8; index++) {
            custom.add(ChatAccountRole.custom("role" + index, "Role " + index, "",
                    0, true, 20 + index, null));
        }
        ChatRoleCatalog.install(ChatRoleCatalog.of(custom, null, null));
        ChatAccountRole ninth = ChatAccountRole.byId("role7");
        assertEquals(1 << 8, ninth.bit());
        int roles = ChatAccountRole.maskOf(ninth, ChatRoleFixtures.OPERATOR);
        ByteBuf buffer = Unpooled.buffer();
        ChatPacketFixtures.line(ChatChannel.OOC, "Steve", "Steve", "hello")
                .roles(roles).build().toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(roles, decoded.getRoles());
    }

    /** A mask naming a role this build does not know is refused. */
    @Test(expected = IllegalArgumentException.class)
    public void unknownRoleBitsAreRefused() {
        ChatPacketFixtures.line(ChatChannel.OOC, "Steve", "Steve", "hello").roles(0x80).build();
    }

    /**
     * A client decodes a line before the server's roles reach it, so a
     * role bit it does not know yet is carried, not refused: the roles are
     * read where the line is shown, and an unknown bit is ignored there.
     */
    @Test
    public void aRoleThisSideDoesNotKnowYetStillDecodes() {
        int roles = ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR);
        LostTalesChatMessagePacket roled = ChatPacketFixtures.line(
                ChatChannel.OOC, "Steve", "Steve", "hello").roles(roles).build();
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        ChatRoleCatalog.resetToBuiltIn();
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(roles, decoded.getRoles());
    }

    /**
     * The login replay reaches a client that has only its built-in roles:
     * the server's arrive in the same moment and are put in place a tick
     * later. A batch holding an operator's line is read whole, not
     * dropped with every other line in it.
     */
    @Test
    public void aReplayWithAnOperatorsLineSurvivesAClientWithoutTheRolesYet() {
        java.util.List<LostTalesChatMessagePacket> lines =
                new java.util.ArrayList<LostTalesChatMessagePacket>();
        lines.add(ChatPacketFixtures.line(ChatChannel.OOC, "Steve", "Steve", "hello")
                .roles(ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR)).build());
        lines.add(ChatPacketFixtures.line(ChatChannel.GLOBAL, "Alex", "Alex", "hi").at(2L).build());
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatHistorySyncPacket(lines, 3L).toBytes(buffer);
        ChatRoleCatalog.resetToBuiltIn();
        LostTalesChatHistorySyncPacket decoded =
                new LostTalesChatHistorySyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getMessages().size());
    }

    @Test
    public void aPayloadEndingBeforeTheMaskIsMalformed() {
        LostTalesChatMessagePacket roled = ChatPacketFixtures.line(
                ChatChannel.OOC, "Steve", "Steve", "hello")
                .roles(ChatAccountRole.maskOf(ChatRoleFixtures.OPERATOR)).build();
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer.slice(0, buffer.readableBytes()
                - scopeTailBytes("") - 4
                - 3 * LostTalesChatMessagePacket.IDENTITY_ID_TAIL_BYTES));
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
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
