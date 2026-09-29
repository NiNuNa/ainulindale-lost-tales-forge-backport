package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.server.ChatMessageIdAllocator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A quote of a line no server named says whose line it was, as far as
 * the sender can tell: the sender's own, or anybody else's. The claim
 * travels after the quote's words, and the message a forward carries on
 * after it; a forward has no words of its own. Quote words holding a
 * section sign or a control character are refused.
 */
public final class LostTalesChatSendPacketQuoteSourceTest {

    private static LostTalesChatSendPacket quoting(int source) {
        return new LostTalesChatSendPacket(ChatChannel.GLOBAL, "which one?",
                null, "", LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "", 7L, null, "Server",
                "Unknown command", source);
    }

    @Test
    public void theSourceRoundTripsWithTheQuote() {
        for (int source = LostTalesChatSendPacket.QUOTE_OTHER;
             source <= LostTalesChatSendPacket.QUOTE_OWN; source++) {
            ByteBuf buffer = Unpooled.buffer();
            quoting(source).toBytes(buffer);
            LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertEquals(source, decoded.getQuoteSource());
            assertEquals("Server", decoded.getQuoteAuthor());
            assertEquals("Unknown command", decoded.getQuoteExcerpt());
        }
    }

    @Test
    public void aQuoteWithoutASourceIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        quoting(LostTalesChatSendPacket.QUOTE_OWN).toBytes(buffer);
        LostTalesChatSendPacket shortened = new LostTalesChatSendPacket();
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - 1));
        assertTrue(shortened.isMalformed());
        assertEquals("", shortened.getQuoteExcerpt());
    }

    @Test
    public void anUnknownSourceIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        quoting(LostTalesChatSendPacket.QUOTE_OWN).toBytes(buffer);
        // The source, before the eight bytes of the message a forward names.
        buffer.setByte(buffer.writerIndex() - 9, 9);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
    }

    @Test
    public void quoteWordsWithACodeOrAControlCharacterAreRefused() throws Exception {
        String[] forged = { "\u00a7\u00a7aGold", "line\nbreak", "be\u0007ll" };
        for (String words : forged) {
            byte[] wanted = words.getBytes("UTF-8");
            char[] filler = new char[wanted.length];
            java.util.Arrays.fill(filler, 'q');
            String placeholder = new String(filler);
            ByteBuf buffer = Unpooled.buffer();
            new LostTalesChatSendPacket(ChatChannel.GLOBAL, "which one?",
                    null, "", LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                    ChatMessageIds.NONE, "", 7L, null, "Server", placeholder,
                    LostTalesChatSendPacket.QUOTE_OTHER).toBytes(buffer);
            // A client that skips its own checks writes the words in place.
            byte[] bytes = new byte[buffer.readableBytes()];
            buffer.getBytes(0, bytes);
            int at = new String(bytes, "ISO-8859-1").indexOf(placeholder);
            System.arraycopy(wanted, 0, bytes, at, wanted.length);
            LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
            decoded.fromBytes(Unpooled.wrappedBuffer(bytes));
            assertTrue(words, decoded.isMalformed());
        }
    }

    @Test
    public void aSourceWithoutAQuoteIsDropped() {
        LostTalesChatSendPacket plain = new LostTalesChatSendPacket(
                ChatChannel.GLOBAL, "hello", null, "",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "", 7L, null, "", "",
                LostTalesChatSendPacket.QUOTE_OWN);
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                plain.getQuoteSource());
    }

    @Test
    public void aForwardNamesItsMessageAndSaysNothingOfItsOwn() {
        long original = ChatMessageIdAllocator.next();
        LostTalesChatSendPacket forward = LostTalesChatSendPacket.forward(
                ChatChannel.WHISPER, "Beren",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null, "", null,
                original);
        ByteBuf buffer = Unpooled.buffer();
        forward.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(original, decoded.getForwardOf());
        assertEquals("", decoded.getMessage());
        assertEquals("Beren", decoded.getTarget());

        LostTalesChatSendPacket plain = quoting(
                LostTalesChatSendPacket.QUOTE_OTHER);
        assertEquals(ChatMessageIds.NONE, plain.getForwardOf());
        // Words beside a forward are refused off the wire.
        buffer = Unpooled.buffer();
        plain.toBytes(buffer);
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

    @Test
    public void theSourceIsReadOffTheHeadTheQuoteWears() {
        UUID self = UUID.randomUUID();
        ChatReplyReference quote = ChatReplyReference.unanchored("Aldric",
                "/warp home", ChatReplyReference.NO_COLOR);
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                LostTalesChatSendPacket.quoteSourceOf(quote, self));
        assertEquals(LostTalesChatSendPacket.QUOTE_OWN,
                LostTalesChatSendPacket.quoteSourceOf(
                        quote.withHead(self, false, "skin"), self));
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                LostTalesChatSendPacket.quoteSourceOf(
                        quote.withHead(UUID.randomUUID(), true, ""), self));
        // The Server's mark is the server's own record to give, never a claim.
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                LostTalesChatSendPacket.quoteSourceOf(quote.withHead(
                        LostTalesChatMessagePacket.SERVER_SENDER_ID, true, ""),
                        self));
        // An NPC's portrait means nothing to anyone else.
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                LostTalesChatSendPacket.quoteSourceOf(
                        quote.withNpcHead(self, "lotr:npc.png"), self));
    }
}
