package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The tab a command was typed in crosses whole, and anything that is
 * not a tab id is refused rather than kept.
 */
public final class LostTalesChatCommandContextPacketTest {

    @Test
    public void aTabIdRoundTrips() {
        LostTalesChatCommandContextPacket packet =
                new LostTalesChatCommandContextPacket("faction|scope:gondor");
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatCommandContextPacket decoded =
                new LostTalesChatCommandContextPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("faction|scope:gondor", decoded.getTabId());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    public void anEmptyOrUnprintableIdIsRefused() {
        try {
            new LostTalesChatCommandContextPacket("  ");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // nothing named
        }
        try {
            new LostTalesChatCommandContextPacket("all\nwhisper:Steve");
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // a line break is not part of any tab id
        }
        StringBuilder long_ = new StringBuilder();
        for (int index = 0;
             index <= LostTalesChatCommandContextPacket.MAX_TAB_ID_CHARACTERS;
             index++) {
            long_.append('x');
        }
        try {
            new LostTalesChatCommandContextPacket(long_.toString());
            assertTrue(false);
        } catch (IllegalArgumentException expected) {
            // longer than any tab id
        }
    }

    @Test
    public void aBadPayloadIsMalformedAndEmpty() {
        // Trailing bytes after the id.
        ByteBuf trailing = Unpooled.buffer();
        new LostTalesChatCommandContextPacket("all").toBytes(trailing);
        trailing.writeByte(7);
        LostTalesChatCommandContextPacket decoded =
                new LostTalesChatCommandContextPacket();
        decoded.fromBytes(trailing);
        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getTabId());
        assertEquals(0, trailing.readableBytes());

        // A control character inside the id.
        ByteBuf control = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(control, "al\u0001l", 64);
        decoded = new LostTalesChatCommandContextPacket();
        decoded.fromBytes(control);
        assertTrue(decoded.isMalformed());

        // Nothing at all.
        decoded = new LostTalesChatCommandContextPacket();
        decoded.fromBytes(Unpooled.buffer());
        assertTrue(decoded.isMalformed());
    }
}
