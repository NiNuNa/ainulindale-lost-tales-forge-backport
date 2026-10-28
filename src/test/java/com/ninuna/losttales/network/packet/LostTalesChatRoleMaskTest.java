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
    /** An optional id at the payload's tail: a presence flag and a UUID, written whole either way. */
    private static final int IDENTITY_ID_TAIL_BYTES = 1 + 16;

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
        int roles = ChatRoleFixtures.maskOf(ChatRoleFixtures.OPERATOR,
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
                buffer.readableBytes() - LostTalesChatPacketTest.scopeTailBytes("") - 1);
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
                    0, true, 20 + index, null, null, null));
        }
        ChatRoleCatalog.install(ChatRoleCatalog.of(custom, null, null));
        ChatAccountRole ninth = ChatAccountRole.byId("role7");
        assertEquals(1 << 8, ninth.bit());
        int roles = ChatRoleFixtures.maskOf(ninth, ChatRoleFixtures.OPERATOR);
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
        int roles = ChatRoleFixtures.maskOf(ChatRoleFixtures.OPERATOR);
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
                .roles(ChatRoleFixtures.maskOf(ChatRoleFixtures.OPERATOR)).build());
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
                .roles(ChatRoleFixtures.maskOf(ChatRoleFixtures.OPERATOR)).build();
        ByteBuf buffer = Unpooled.buffer();
        roled.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer.slice(0, buffer.readableBytes()
                - LostTalesChatPacketTest.scopeTailBytes("") - 4
                - 3 * IDENTITY_ID_TAIL_BYTES));
        assertTrue(decoded.isMalformed());
        assertEquals(0, decoded.getRoles());
    }
}
