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
    /** A Discord member's words with a sticker's mark each game translates. */
    private static final String BODY = "{\"text\":\"\",\"extra\":[{\"text\":\"look \"},"
            + "{\"translate\":\"chat.losttales.words.discord_sticker\",\"with\":[\"Wave\"]}]}";

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
        com.ninuna.losttales.chat.ChatNamedPlayer bob =
                new com.ninuna.losttales.chat.ChatNamedPlayer(
                        java.util.UUID.randomUUID(), "bob", null, "Beren", "");
        LostTalesChatUpdatePacket.edited(SERVER_ID, "on reflection, @Beren", "",
                java.util.Collections.singletonList(bob)).toBytes(edited);
        LostTalesChatUpdatePacket decodedEdit = new LostTalesChatUpdatePacket();
        decodedEdit.fromBytes(edited);
        assertFalse(decodedEdit.isMalformed());
        assertFalse(decodedEdit.isRemoved());
        assertEquals("on reflection, @Beren", decodedEdit.getMessage());
        assertEquals("", decodedEdit.getBodyJson());
        assertEquals("an edit names whom its new words name",
                "Beren", decodedEdit.getNamedPlayers().get(0).getIdentityName());

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

    /** A Discord member's edit carries its new words' component. */
    @Test
    public void anEditCarriesItsWordsToTranslate() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesChatUpdatePacket.edited(SERVER_ID, "look *[Sticker: Wave]*", BODY,
                null).toBytes(buffer);
        LostTalesChatUpdatePacket decoded = new LostTalesChatUpdatePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("look *[Sticker: Wave]*", decoded.getMessage());
        assertEquals(BODY, decoded.getBodyJson());
    }

    /**
     * A component is bounded as a line's is: one past the bound is never
     * built and never read, and nothing may follow it.
     */
    @Test
    public void anOversizedOrTrailingComponentIsMalformed() {
        StringBuilder huge = new StringBuilder();
        while (huge.length() <= LostTalesChatMessagePacket.MAX_BODY_BYTES) {
            huge.append('x');
        }
        try {
            LostTalesChatUpdatePacket.edited(SERVER_ID, "hello", huge.toString(), null);
            fail("a component past the bound was accepted");
        } catch (IllegalArgumentException expected) {
            // The factory validates, so it can never be sent either.
        }
        ByteBuf oversized = Unpooled.buffer();
        oversized.writeLong(SERVER_ID);
        oversized.writeBoolean(false);
        LostTalesPacketCodec.writeUtf8String(oversized, "hello",
                ChatMessageValidator.MAX_UTF8_BYTES);
        LostTalesPacketCodec.writeUtf8String(oversized, huge.toString(),
                huge.length());
        LostTalesChatNamedPlayerCodec.write(oversized,
                java.util.Collections.<com.ninuna.losttales.chat.ChatNamedPlayer>emptyList());
        LostTalesChatUpdatePacket decoded = new LostTalesChatUpdatePacket();
        decoded.fromBytes(oversized);
        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getBodyJson());

        ByteBuf trailing = Unpooled.buffer();
        LostTalesChatUpdatePacket.edited(SERVER_ID, "hello", BODY, null).toBytes(trailing);
        trailing.writeByte(7);
        LostTalesChatUpdatePacket withTail = new LostTalesChatUpdatePacket();
        withTail.fromBytes(trailing);
        assertTrue(withTail.isMalformed());
        assertEquals("", withTail.getBodyJson());
    }

    /** A removal carries no component either. */
    @Test
    public void aRemovalCarryingAComponentIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeLong(SERVER_ID);
        buffer.writeBoolean(true);
        LostTalesPacketCodec.writeUtf8String(buffer, "",
                ChatMessageValidator.MAX_UTF8_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, BODY,
                LostTalesChatMessagePacket.MAX_BODY_BYTES);
        LostTalesChatNamedPlayerCodec.write(buffer,
                java.util.Collections.<com.ninuna.losttales.chat.ChatNamedPlayer>emptyList());
        LostTalesChatUpdatePacket decoded = new LostTalesChatUpdatePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
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
