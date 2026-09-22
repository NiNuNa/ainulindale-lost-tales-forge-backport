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

/**
 * A quote of a line no server named says whose line it was, as far as
 * the sender can tell: the Server's or the Client's, the sender's own,
 * or anybody else's. The claim travels after the quote's words and only
 * with them.
 */
public final class LostTalesChatSendPacketQuoteSourceTest {

    private static LostTalesChatSendPacket quoting(int source) {
        return new LostTalesChatSendPacket(ChatChannel.ALL, "which one?",
                null, "", LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "", 7L, null, "Server",
                "Unknown command", source);
    }

    @Test
    public void theSourceRoundTripsWithTheQuote() {
        for (int source = LostTalesChatSendPacket.QUOTE_OTHER;
             source <= LostTalesChatSendPacket.QUOTE_SYSTEM; source++) {
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
        quoting(LostTalesChatSendPacket.QUOTE_SYSTEM).toBytes(buffer);
        LostTalesChatSendPacket shortened = new LostTalesChatSendPacket();
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - 1));
        assertTrue(shortened.isMalformed());
        assertEquals("", shortened.getQuoteExcerpt());
    }

    @Test
    public void anUnknownSourceIsRefused() {
        ByteBuf buffer = Unpooled.buffer();
        quoting(LostTalesChatSendPacket.QUOTE_OWN).toBytes(buffer);
        buffer.setByte(buffer.writerIndex() - 1, 9);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
    }

    @Test
    public void aSourceWithoutAQuoteIsDropped() {
        LostTalesChatSendPacket plain = new LostTalesChatSendPacket(
                ChatChannel.ALL, "hello", null, "",
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null,
                ChatMessageIds.NONE, "", 7L, null, "", "",
                LostTalesChatSendPacket.QUOTE_SYSTEM);
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                plain.getQuoteSource());
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
        assertEquals(LostTalesChatSendPacket.QUOTE_SYSTEM,
                LostTalesChatSendPacket.quoteSourceOf(quote.withHead(
                        LostTalesChatMessagePacket.SERVER_SENDER_ID, true, ""),
                        self));
        // An NPC's portrait means nothing to anyone else.
        assertEquals(LostTalesChatSendPacket.QUOTE_OTHER,
                LostTalesChatSendPacket.quoteSourceOf(
                        quote.withNpcHead(self, "lotr:npc.png"), self));
    }
}
