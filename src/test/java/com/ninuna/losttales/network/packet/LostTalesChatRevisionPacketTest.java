package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * The wire for changing a message after it was said: the two requests a
 * client may make, and what the server tells everyone who saw it.
 */
public final class LostTalesChatRevisionPacketTest {
    /** Any id the server would have handed out. */
    private static final long SERVER_ID = 4096L;

    @Test
    public void editRequestRoundTrips() {
        LostTalesChatEditPacket original = new LostTalesChatEditPacket(
                SERVER_ID, "meet me at the tower");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatEditPacket decoded = new LostTalesChatEditPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(SERVER_ID, decoded.getMessageId());
        assertEquals("meet me at the tower", decoded.getMessage());
    }

    @Test
    public void deleteRequestRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatDeletePacket(SERVER_ID).toBytes(buffer);
        LostTalesChatDeletePacket decoded = new LostTalesChatDeletePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(SERVER_ID, decoded.getMessageId());
    }

    @Test
    public void updateRoundTripsBothWaysRound() {
        ByteBuf edited = Unpooled.buffer();
        LostTalesChatUpdatePacket.edited(SERVER_ID, "on reflection")
                .toBytes(edited);
        LostTalesChatUpdatePacket decodedEdit = new LostTalesChatUpdatePacket();
        decodedEdit.fromBytes(edited);
        assertFalse(decodedEdit.isMalformed());
        assertFalse(decodedEdit.isRemoved());
        assertEquals("on reflection", decodedEdit.getMessage());

        ByteBuf removed = Unpooled.buffer();
        LostTalesChatUpdatePacket.removed(SERVER_ID).toBytes(removed);
        LostTalesChatUpdatePacket decodedRemoval =
                new LostTalesChatUpdatePacket();
        decodedRemoval.fromBytes(removed);
        assertFalse(decodedRemoval.isMalformed());
        assertTrue(decodedRemoval.isRemoved());
        assertEquals(SERVER_ID, decodedRemoval.getMessageId());
        assertEquals("", decodedRemoval.getMessage());
    }

    /**
     * Only a message the server named can be changed. A client-local id
     * belongs to a line no server ever saw — this client's own half of
     * an NPC conversation — and {@code NONE} names nothing at all.
     */
    @Test
    public void onlyServerNamedMessagesMayBeChanged() {
        assertRefused(ChatMessageIds.NONE);
        assertRefused(-1L);
        for (long id : new long[] { ChatMessageIds.NONE, -1L }) {
            ByteBuf buffer = Unpooled.buffer();
            buffer.writeLong(id);
            LostTalesChatDeletePacket decoded =
                    new LostTalesChatDeletePacket();
            decoded.fromBytes(buffer);
            assertTrue(decoded.isMalformed());
            assertEquals(ChatMessageIds.NONE, decoded.getMessageId());
        }
    }

    /** An edit is bounded exactly like the message it replaces. */
    @Test
    public void anOversizedEditIsMalformed() {
        StringBuilder tooLong = new StringBuilder();
        while (tooLong.length() <= ChatMessageValidator.MAX_UTF8_BYTES) {
            tooLong.append('x');
        }
        assertFalse(ChatMessageValidator.isValid(tooLong.toString()));
        try {
            new LostTalesChatEditPacket(SERVER_ID, tooLong.toString());
            fail("an edit longer than a message may be was accepted");
        } catch (IllegalArgumentException expected) {
            // The constructor validates, so it can never be sent either.
        }
    }

    @Test
    public void trailingDataIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatEditPacket(SERVER_ID, "hello").toBytes(buffer);
        buffer.writeByte(7);
        LostTalesChatEditPacket decoded = new LostTalesChatEditPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getMessage());
    }

    /** A removal says nothing; anything else on it is not a removal. */
    @Test
    public void aRemovalCarryingTextIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeLong(SERVER_ID);
        buffer.writeBoolean(true);
        LostTalesPacketCodec.writeUtf8String(buffer, "smuggled",
                ChatMessageValidator.MAX_UTF8_BYTES);
        LostTalesChatUpdatePacket decoded = new LostTalesChatUpdatePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
    }

    private static void assertRefused(long messageId) {
        try {
            new LostTalesChatEditPacket(messageId, "hello");
            fail("a message the server never named was accepted");
        } catch (IllegalArgumentException expected) {
            // Refused before it could be sent.
        }
    }
}
