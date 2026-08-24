package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The chat access packet carries the player's own roles beside the two
 * channel flags, so the client can notice a mention addressed to one of
 * them. The roles sit at the end of the layout: a payload written before
 * they existed still reads, and names none.
 */
public final class LostTalesChatAccessPacketTest {

    @Test
    public void accessAndRolesRoundTrip() {
        int roles = ChatAccountRole.maskOf(ChatAccountRole.OPERATOR);
        LostTalesChatAccessPacket packet =
                new LostTalesChatAccessPacket(true, false, roles);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.hasAdminAccess());
        assertFalse(decoded.hasDiscordAccess());
        assertEquals(roles, decoded.getRoleMask());
    }

    @Test
    public void aPayloadWithoutRolesNamesNone() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeBoolean(false);
        buffer.writeBoolean(true);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.hasAdminAccess());
        assertTrue(decoded.hasDiscordAccess());
        assertEquals(0, decoded.getRoleMask());
    }

    /** A mask naming roles this build does not know is taken as none. */
    @Test
    public void anUnknownMaskIsDiscardedRatherThanShown() {
        LostTalesChatAccessPacket packet =
                new LostTalesChatAccessPacket(false, false, 0x40000000);
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(0, decoded.getRoleMask());
    }

    @Test
    public void anOversizedPayloadIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        for (int index = 0; index < 64; index++) {
            buffer.writeByte(1);
        }
        LostTalesChatAccessPacket decoded = new LostTalesChatAccessPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.hasAdminAccess());
        assertEquals(0, decoded.getRoleMask());
    }
}
