package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Asking for one conversation of a channel that has more than one. The
 * request names the channel, the conversation and what the client
 * already holds; anything else is refused before it reaches the server's
 * own check, which decides entitlement for itself either way.
 */
public final class LostTalesChatContextHistoryPacketTest {

    private static final String GONDOR = "lotr:gondor";

    private static LostTalesChatContextHistoryPacket roundTrip(
            LostTalesChatContextHistoryPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatContextHistoryPacket decoded =
                new LostTalesChatContextHistoryPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    @Test
    public void aRequestNamesTheConversationAndWhatIsAlreadyHeld() {
        LostTalesChatContextHistoryPacket decoded = roundTrip(
                new LostTalesChatContextHistoryPacket(ChatChannel.FACTION, GONDOR, 42L));
        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.FACTION, decoded.getChannel());
        assertEquals(GONDOR, decoded.getScopeValue());
        assertEquals(42L, decoded.getSinceMessageId());
    }

    /** Holding nothing yet asks for everything the server will give. */
    @Test
    public void holdingNothingAsksFromTheBeginning() {
        LostTalesChatContextHistoryPacket decoded = roundTrip(
                new LostTalesChatContextHistoryPacket(ChatChannel.FACTION, GONDOR,
                        ChatMessageIds.NONE));
        assertFalse(decoded.isMalformed());
        assertEquals(ChatMessageIds.NONE, decoded.getSinceMessageId());
    }

    /**
     * A channel that is only ever one conversation has nothing to ask
     * about: the login replay answered it already.
     */
    @Test
    public void anUnscopedChannelIsRefused() {
        try {
            new LostTalesChatContextHistoryPacket(ChatChannel.GLOBAL, GONDOR, 0L);
            throw new AssertionError("an unscoped channel is not a conversation");
        } catch (IllegalArgumentException refused) {
            assertTrue(refused.getMessage().contains("context history"));
        }
    }

    /** A scoped channel naming no conversation is refused as well. */
    @Test
    public void aScopedChannelWithoutAConversationIsRefused() {
        try {
            new LostTalesChatContextHistoryPacket(ChatChannel.FACTION, "", 0L);
            throw new AssertionError("a conversation has to be named");
        } catch (IllegalArgumentException refused) {
            assertTrue(refused.getMessage().contains("context history"));
        }
    }

    /** A payload naming no channel is malformed, never half-read. */
    @Test
    public void aPayloadNamingNoChannelIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(buffer, "nowhere", 64);
        LostTalesPacketCodec.writeUtf8String(buffer, GONDOR, 128);
        buffer.writeLong(0L);
        LostTalesChatContextHistoryPacket decoded =
                new LostTalesChatContextHistoryPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getScopeValue());
        assertEquals(ChatMessageIds.NONE, decoded.getSinceMessageId());
    }

    /** A payload cut short is malformed too. */
    @Test
    public void aTruncatedPayloadIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatContextHistoryPacket(ChatChannel.FACTION, GONDOR, 7L)
                .toBytes(buffer);
        LostTalesChatContextHistoryPacket decoded =
                new LostTalesChatContextHistoryPacket();
        decoded.fromBytes(buffer.slice(0, buffer.readableBytes() - 3));
        assertTrue(decoded.isMalformed());
    }
}
