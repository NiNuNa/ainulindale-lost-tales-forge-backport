package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A request answering a line no server holds a record of says only that
 * it does: one flag, before the message a forward carries on, and
 * nothing of the line's author or words. A forward says nothing of its
 * own, that flag included.
 */
public final class LostTalesChatSendPacketUnkeptQuoteTest {

    private static LostTalesChatSendPacket request(boolean unkept) {
        return new LostTalesChatSendPacket(ChatChannel.GLOBAL, "which one?",
                null, "", LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "", 7L, null, unkept);
    }

    private static ByteBuf encode(LostTalesChatSendPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }

    @Test
    public void theFlagRoundTripsAndCarriesNothingOfTheLine() {
        ByteBuf unkept = encode(request(true));
        ByteBuf plain = encode(request(false));
        // The same size, one byte apart: no author, no words, no head.
        assertEquals(plain.readableBytes(), unkept.readableBytes());
        int differing = 0;
        for (int index = 0; index < plain.readableBytes(); index++) {
            if (plain.getByte(index) != unkept.getByte(index)) {
                differing++;
            }
        }
        assertEquals(1, differing);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(unkept);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.quotesUnkept());
        LostTalesChatSendPacket decodedPlain = new LostTalesChatSendPacket();
        decodedPlain.fromBytes(plain);
        assertFalse(decodedPlain.isMalformed());
        assertFalse(decodedPlain.quotesUnkept());
    }

    @Test
    public void aShortenedRequestIsMalformed() {
        ByteBuf buffer = encode(request(true));
        LostTalesChatSendPacket shortened = new LostTalesChatSendPacket();
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - 1));
        assertTrue(shortened.isMalformed());
        assertFalse(shortened.quotesUnkept());
    }

    @Test(expected = IllegalArgumentException.class)
    public void theFlagBesideAMessageIdIsRefused() {
        new LostTalesChatSendPacket(ChatChannel.GLOBAL, "which one?",
                null, "", LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIdAllocator.next(), "", 7L, null, true);
    }

    @Test
    public void theFlagBesideAMessageIdIsMalformedOffTheWire() {
        ByteBuf buffer = encode(new LostTalesChatSendPacket(ChatChannel.GLOBAL,
                "which one?", null, "", LostTalesChatSendPacket.IDENTITY_DEFAULT,
                null, ChatMessageIdAllocator.next(), "", 7L, null, false));
        // The flag, before the eight bytes of the message a forward names.
        buffer.setByte(buffer.writerIndex() - 9, 1);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
    }

    @Test
    public void aForwardNamesItsMessageAndSaysNothingOfItsOwn() {
        long original = ChatMessageIdAllocator.next();
        LostTalesChatSendPacket forward = LostTalesChatSendPacket.forward(
                ChatChannel.WHISPER, "Beren",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null, "", null,
                original);
        ByteBuf buffer = encode(forward);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer.copy());
        assertFalse(decoded.isMalformed());
        assertEquals(original, decoded.getForwardOf());
        assertEquals("", decoded.getMessage());
        assertEquals("Beren", decoded.getTarget());
        // A forward that says it answers a line no longer kept is refused.
        buffer.setByte(buffer.writerIndex() - 9, 1);
        decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());

        LostTalesChatSendPacket plain = request(true);
        assertEquals(ChatMessageIds.NONE, plain.getForwardOf());
        // Words beside a forward are refused off the wire.
        buffer = encode(plain);
        buffer.setLong(buffer.writerIndex() - 8, original);
        decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals(ChatMessageIds.NONE, decoded.getForwardOf());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aForwardOfNoServerMessageIsRefused() {
        LostTalesChatSendPacket.forward(ChatChannel.GLOBAL, "",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null, "", null,
                ChatMessageIds.NONE - 1);
    }
}
