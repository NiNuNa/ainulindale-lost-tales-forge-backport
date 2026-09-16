package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public final class LostTalesChatTypingPacketTest {

    @Test
    public void requestRoundTripsAndRejectsTrailingData() {
        LostTalesChatTypingPacket original = new LostTalesChatTypingPacket(
                ChatChannel.PARTY, "", true);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatTypingPacket decoded = new LostTalesChatTypingPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.PARTY, decoded.getChannel());
        assertEquals("", decoded.getTarget());
        assertTrue(decoded.isTyping());

        LostTalesChatTypingPacket whisper = new LostTalesChatTypingPacket(
                ChatChannel.WHISPER, " Bilbo ", false);
        buffer = Unpooled.buffer();
        whisper.toBytes(buffer);
        decoded = new LostTalesChatTypingPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.WHISPER, decoded.getChannel());
        assertEquals("Bilbo", decoded.getTarget());
        assertFalse(decoded.isTyping());

        ByteBuf trailing = Unpooled.buffer();
        original.toBytes(trailing);
        trailing.writeByte(1);
        LostTalesChatTypingPacket rejected = new LostTalesChatTypingPacket();
        rejected.fromBytes(trailing);
        assertTrue(rejected.isMalformed());
    }

    @Test
    public void theStatedIdentityRoundTripsAndAnIncompletePayloadIsRejected() {
        java.util.UUID character =
                java.util.UUID.fromString("00000000-0000-0000-0000-0000000000c1");
        LostTalesChatTypingPacket original = new LostTalesChatTypingPacket(
                ChatChannel.ALL, "", true,
                LostTalesChatSendPacket.IDENTITY_CHARACTER, character);
        ByteBuf buffer = Unpooled.buffer();
        try {
            original.toBytes(buffer);
            LostTalesChatTypingPacket decoded = new LostTalesChatTypingPacket();
            decoded.fromBytes(buffer);
            assertFalse(decoded.isMalformed());
            assertEquals(LostTalesChatSendPacket.IDENTITY_CHARACTER,
                    decoded.getIdentityKind());
            assertEquals(character, decoded.getIdentityCharacterId());
        } finally {
            buffer.release();
        }

        // Every field is mandatory; no older wire layout is supported.
        ByteBuf older = Unpooled.buffer();
        try {
            LostTalesPacketCodec.writeUtf8String(older, "all", 16);
            LostTalesPacketCodec.writeUtf8String(older, "", 64);
            older.writeBoolean(true);
            LostTalesChatTypingPacket decoded = new LostTalesChatTypingPacket();
            decoded.fromBytes(older);
            assertTrue(decoded.isMalformed());
            assertEquals(LostTalesChatSendPacket.IDENTITY_DEFAULT,
                    decoded.getIdentityKind());
            assertNull(decoded.getIdentityCharacterId());
        } finally {
            older.release();
        }
    }

    @Test
    public void requestRefusesAWhisperWithoutATargetAndATargetElsewhere() {
        try {
            new LostTalesChatTypingPacket(ChatChannel.WHISPER, "", true);
            fail("a whisper needs its partner");
        } catch (IllegalArgumentException expected) {
            // The packet is never built.
        }
        try {
            new LostTalesChatTypingPacket(ChatChannel.ALL, "Bilbo", true);
            fail("only a whisper names a partner");
        } catch (IllegalArgumentException expected) {
            // The packet is never built.
        }
        // Decoding applies the same rule.
        ByteBuf buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(buffer, "whisper", 16);
        LostTalesPacketCodec.writeUtf8String(buffer, "", 64);
        buffer.writeBoolean(true);
        LostTalesChatTypingPacket decoded = new LostTalesChatTypingPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertFalse(decoded.isTyping());
    }

    @Test
    public void syncRoundTripsAndRejectsAnEmptyNameOrUnknownChannel() {
        LostTalesChatTypingSyncPacket original =
                new LostTalesChatTypingSyncPacket(ChatChannel.WHISPER,
                        "Frodo", "Bilbo Baggins", true);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesChatTypingSyncPacket decoded =
                new LostTalesChatTypingSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.WHISPER, decoded.getChannel());
        assertEquals("Frodo", decoded.getPartner());
        assertEquals("Bilbo Baggins", decoded.getIdentityName());
        assertTrue(decoded.isTyping());

        try {
            new LostTalesChatTypingSyncPacket(ChatChannel.ALL, "", " ", true);
            fail("a typist has a name");
        } catch (IllegalArgumentException expected) {
            // The packet is never built.
        }
        buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(buffer, "trade", 16);
        LostTalesPacketCodec.writeUtf8String(buffer, "", 96);
        LostTalesPacketCodec.writeUtf8String(buffer, "Bilbo", 96);
        buffer.writeBoolean(true);
        decoded = new LostTalesChatTypingSyncPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getIdentityName());
    }

    @Test
    public void typingCarriesItsConversationAndRejectsEveryIncompletePayload() {
        String identity = new java.util.UUID(1L, 2L).toString();
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatTypingSyncPacket(ChatChannel.FACTION, "", "Aldric",
                true, "lotr:gondor", identity).toBytes(wire);
        LostTalesChatTypingSyncPacket decoded = new LostTalesChatTypingSyncPacket();
        decoded.fromBytes(wire.copy());
        assertFalse(decoded.isMalformed());
        assertEquals("lotr:gondor", decoded.getScopeValue());
        assertEquals(identity, decoded.getRecipientIdentity());
        for (int length = 0; length < wire.readableBytes(); length++) {
            decoded = new LostTalesChatTypingSyncPacket();
            decoded.fromBytes(wire.copy(0, length));
            assertTrue(decoded.isMalformed());
        }
        decoded = new LostTalesChatTypingSyncPacket();
        decoded.fromBytes(wire.copy().writeByte(0));
        assertTrue(decoded.isMalformed());
    }

    @Test
    public void whisperTypingAddressesACharacterAndRejectsPartialRequests() {
        java.util.UUID character = new java.util.UUID(1L, 2L);
        ByteBuf wire = Unpooled.buffer();
        new LostTalesChatTypingPacket(ChatChannel.WHISPER, "Steve", true,
                LostTalesChatSendPacket.IDENTITY_ACCOUNT, null, "Aldric", character).toBytes(wire);
        LostTalesChatTypingPacket decoded = new LostTalesChatTypingPacket();
        decoded.fromBytes(wire.copy());
        assertFalse(decoded.isMalformed());
        assertEquals("Aldric", decoded.getTargetIdentity());
        assertEquals(character, decoded.getTargetCharacterId());
        for (int length = 0; length < wire.readableBytes(); length++) {
            decoded = new LostTalesChatTypingPacket();
            decoded.fromBytes(wire.copy(0, length));
            assertTrue(decoded.isMalformed());
        }
        decoded = new LostTalesChatTypingPacket();
        decoded.fromBytes(wire.copy().setByte(wire.readableBytes() - 17, 2));
        assertTrue(decoded.isMalformed());
    }
}
