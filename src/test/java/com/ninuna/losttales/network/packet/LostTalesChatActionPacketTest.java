package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * An action ({@code /me}) travels as a flag beside the words: on the
 * request, on the line the server records and hands out, and on a quote
 * of such a line. Every copy of a line keeps what it is, and a payload
 * that claims an action where none can be is malformed.
 */
public final class LostTalesChatActionPacketTest {
    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-00000000a1d0");

    @Test
    public void anActionRequestRoundTrips() {
        LostTalesChatSendPacket decoded = roundTrip(ChatPacketFixtures
                .send(ChatChannel.GLOBAL, "draws his sword.").action()
                .build());
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isAction());
        assertEquals("draws his sword.", decoded.getMessage());
        LostTalesChatSendPacket said = roundTrip(ChatPacketFixtures
                .send(ChatChannel.GLOBAL, "draws his sword.").build());
        assertFalse(said.isMalformed());
        assertFalse(said.isAction());
    }

    /** An action with no words is no action: it cannot be built, and arriving it is malformed. */
    @Test
    public void anEmptyActionIsRefused() {
        try {
            ChatPacketFixtures.send(ChatChannel.GLOBAL, "").action().build();
            fail("an empty action was built");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        // The same request with its one word taken out of the payload.
        ByteBuf full = Unpooled.buffer();
        ChatPacketFixtures.send(ChatChannel.GLOBAL, "x").action().build()
                .toBytes(full);
        ByteBuf channel = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(channel,
                ChatChannel.GLOBAL.getId(), 16);
        int wordsAt = channel.readableBytes();
        ByteBuf empty = Unpooled.buffer();
        empty.writeBytes(full, 0, wordsAt);
        empty.writeByte(0);
        // Past the one word's length and its one byte.
        empty.writeBytes(full, wordsAt + 2, full.readableBytes() - wordsAt - 2);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(empty);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.isAction());
    }

    @Test
    public void truncatedAndTrailingActionRequestsAreMalformed() {
        ByteBuf full = Unpooled.buffer();
        ChatPacketFixtures.send(ChatChannel.PROXIMITY, "sits down.").action()
                .build().toBytes(full);
        ByteBuf trailing = full.copy();
        trailing.writeByte(1);
        LostTalesChatSendPacket longer = new LostTalesChatSendPacket();
        longer.fromBytes(trailing);
        assertTrue(longer.isMalformed());
        ByteBuf truncated = full.copy(0, full.readableBytes() - 1);
        LostTalesChatSendPacket shorter = new LostTalesChatSendPacket();
        shorter.fromBytes(truncated);
        assertTrue(shorter.isMalformed());
        assertFalse(shorter.isAction());
    }

    /** A forward carries on the server's record; it asks for no action of its own. */
    @Test
    public void aForwardAsksForNoAction() {
        LostTalesChatSendPacket forward = LostTalesChatSendPacket.forward(
                ChatChannel.GLOBAL, "", LostTalesChatSendPacket.IDENTITY_DEFAULT,
                null, "", null, 42L);
        ByteBuf plain = Unpooled.buffer();
        forward.toBytes(plain);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(plain.copy());
        assertFalse(decoded.isMalformed());
        assertFalse(decoded.isAction());
        // The flag follows the (empty) words: the channel, then one byte.
        ByteBuf channel = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(channel,
                ChatChannel.GLOBAL.getId(), 16);
        int flagAt = channel.readableBytes() + 1;
        assertEquals(0, plain.getByte(flagAt));
        plain.setByte(flagAt, 1);
        LostTalesChatSendPacket forged = new LostTalesChatSendPacket();
        forged.fromBytes(plain);
        assertTrue(forged.isMalformed());
    }

    @Test
    public void anActionLineRoundTripsAndEveryCopyKeepsIt() {
        LostTalesChatMessagePacket line = ChatPacketFixtures
                .line(ChatChannel.GLOBAL, "Aldric", "Steve", "draws his sword.")
                .sender(PLAYER).messageId(7L).build().withAction(true);
        assertTrue(line.isAction());
        LostTalesChatMessagePacket decoded = roundTrip(line);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isAction());
        assertEquals("draws his sword.", decoded.getMessage());
        assertTrue(decoded.withMessage("sheathes it.").isAction());
        assertTrue(decoded.withScope("").isAction());
        assertTrue(decoded.withNameColor(0x123456).isAction());
        assertTrue(decoded.withoutEcho().isAction());
        assertTrue(decoded.withReply(ChatReplyReference.NONE).isAction());
        assertFalse(decoded.withAction(false).isAction());
        assertFalse(roundTrip(ChatPacketFixtures.line(ChatChannel.GLOBAL,
                "Aldric", "Steve", "Hello.").sender(PLAYER).build())
                .isAction());
    }

    /** The Server and the Client say things; they do none. */
    @Test
    public void aSystemLineIsNeverAnAction() {
        LostTalesChatMessagePacket server = ChatPacketFixtures
                .line(ChatChannel.GLOBAL, "Server", "Server", "draws his sword.")
                .sender(LostTalesChatMessagePacket.SERVER_SENDER_ID).build();
        assertFalse(server.withAction(true).isAction());
        // The same line from a player differs in the flag's byte alone
        // once it is an action: that byte forged on the Server's line is
        // malformed.
        ByteBuf said = encode(ChatPacketFixtures.line(ChatChannel.GLOBAL,
                "Server", "Server", "draws his sword.").sender(PLAYER).build());
        ByteBuf done = encode(ChatPacketFixtures.line(ChatChannel.GLOBAL,
                "Server", "Server", "draws his sword.").sender(PLAYER).build()
                .withAction(true));
        int flagAt = onlyDifference(said, done);
        ByteBuf forged = encode(server);
        forged.setByte(flagAt, 1);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(forged);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.isAction());
    }

    @Test
    public void aQuoteOfAnActionSaysSo() {
        ChatReplyReference quote = ChatReplyReference.of(5L, "Aldric",
                "draws his sword.", 0x5A7A3A).withHead(PLAYER, false, "")
                .asAction(true);
        assertTrue(quote.isAction());
        assertTrue(quote.withHead(PLAYER, false, "skin").isAction());
        LostTalesChatMessagePacket reply = ChatPacketFixtures
                .line(ChatChannel.GLOBAL, "Beren", "Alex", "Not here.")
                .messageId(8L).build().withReply(quote);
        LostTalesChatMessagePacket decoded = roundTrip(reply);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.getReply().isAction());
        assertEquals("Aldric", decoded.getReply().getAuthor());
        assertEquals("draws his sword.", decoded.getReply().getExcerpt());
        assertFalse(decoded.isAction());
        // A quote of nothing and a forward are never an action's.
        assertFalse(ChatReplyReference.NONE.asAction(true).isAction());
        assertFalse(ChatReplyReference.forward(5L, "Aldric", 0x5A7A3A,
                "#global/5").asAction(true).isAction());
    }

    /**
     * A line that quotes no named message cannot say its quote is an
     * action: neither a line with no quote nor one quoting a line no
     * longer kept, which tells nothing of that line. A client's own action
     * quote of a line nobody named travels as no longer kept, plainly.
     */
    @Test
    public void anActionQuoteOfNothingIsMalformed() {
        // A quote of a line no longer kept sits where a line with no quote
        // says it has none; the two differ in that one flag.
        ByteBuf plain = encode(ChatPacketFixtures.line(ChatChannel.GLOBAL,
                "Beren", "Alex", "Not here.").sender(PLAYER).build());
        ByteBuf unkept = encode(ChatPacketFixtures.line(ChatChannel.GLOBAL,
                "Beren", "Alex", "Not here.").sender(PLAYER).build()
                .withReply(ChatReplyReference.unanchored("A", "",
                        ChatReplyReference.NO_COLOR).asAction(true)));
        int unkeptAt = onlyDifference(plain, unkept);
        LostTalesChatMessagePacket decodedQuote =
                new LostTalesChatMessagePacket();
        decodedQuote.fromBytes(unkept.copy());
        assertFalse(decodedQuote.isMalformed());
        assertTrue(decodedQuote.getReply().isUnkept());
        assertFalse(decodedQuote.getReply().isAction());
        // The action flag follows the quote's head: an absent id, the
        // account flag and an empty skin.
        int flagAt = unkeptAt + 1 + 17 + 1 + 1;
        for (ByteBuf forged : new ByteBuf[] {plain, unkept}) {
            assertEquals(0, forged.getByte(flagAt));
            forged.setByte(flagAt, 1);
            LostTalesChatMessagePacket read = new LostTalesChatMessagePacket();
            read.fromBytes(forged);
            assertTrue(read.isMalformed());
            assertEquals(ChatMessageIds.NONE, read.getMessageId());
        }
    }

    private static LostTalesChatSendPacket roundTrip(
            LostTalesChatSendPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    private static LostTalesChatMessagePacket roundTrip(
            LostTalesChatMessagePacket packet) {
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(encode(packet));
        return decoded;
    }

    private static ByteBuf encode(LostTalesChatMessagePacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        return buffer;
    }

    /** The one place two payloads of the same length differ. */
    private static int onlyDifference(ByteBuf a, ByteBuf b) {
        assertEquals(a.readableBytes(), b.readableBytes());
        int found = -1;
        for (int index = 0; index < a.readableBytes(); index++) {
            if (a.getByte(index) != b.getByte(index)) {
                assertEquals("more than one byte differs", -1, found);
                found = index;
            }
        }
        assertTrue("no byte differs", found >= 0);
        return found;
    }
}
