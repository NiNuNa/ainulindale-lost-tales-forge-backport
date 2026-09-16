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
 * Asking for the page of a channel before the oldest line held. The
 * request names the channel, the conversation exactly when the channel
 * has more than one, and a line the server named; anything else is
 * refused before it reaches the server's own entitlement check.
 */
public final class LostTalesChatOlderHistoryPacketTest {

    private static final String GONDOR = "lotr:gondor";

    private static LostTalesChatOlderHistoryPacket roundTrip(
            LostTalesChatOlderHistoryPacket packet) {
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatOlderHistoryPacket decoded = new LostTalesChatOlderHistoryPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }

    @Test
    public void aRequestNamesTheChannelAndTheLineToReachBackFrom() {
        LostTalesChatOlderHistoryPacket decoded = roundTrip(
                new LostTalesChatOlderHistoryPacket(ChatChannel.ALL, "", 42L));
        assertFalse(decoded.isMalformed());
        assertEquals(ChatChannel.ALL, decoded.getChannel());
        assertEquals("", decoded.getScopeValue());
        assertEquals(42L, decoded.getBeforeMessageId());
        LostTalesChatOlderHistoryPacket scoped = roundTrip(
                new LostTalesChatOlderHistoryPacket(ChatChannel.FACTION, GONDOR, 7L));
        assertFalse(scoped.isMalformed());
        assertEquals(GONDOR, scoped.getScopeValue());
    }

    @Test
    public void aScopedChannelNeedsItsConversationAndAPlainOneRefusesOne() {
        assertRefused(ChatChannel.FACTION, "", 42L);
        assertRefused(ChatChannel.ALL, GONDOR, 42L);
    }

    @Test
    public void aWhisperNamesItsConversationAndAnUnnamedLineIsRefused() {
        assertRefused(ChatChannel.WHISPER, "", 42L);
        LostTalesChatOlderHistoryPacket whisper = roundTrip(
                new LostTalesChatOlderHistoryPacket(ChatChannel.WHISPER,
                        "whisper:Steve|Aldric|own:abc", 42L));
        assertFalse(whisper.isMalformed());
        assertEquals("whisper:Steve|Aldric|own:abc", whisper.getScopeValue());
        assertRefused(ChatChannel.ALL, "", ChatMessageIds.NONE);
        assertRefused(ChatChannel.ALL, "", -3L);
        assertRefused(null, "", 42L);
    }

    @Test
    public void bytesThatBreakTheRuleDecodeAsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        LostTalesPacketCodec.writeUtf8String(buffer, "all", 64);
        LostTalesPacketCodec.writeUtf8String(buffer, "", 128);
        buffer.writeLong(0L);
        LostTalesChatOlderHistoryPacket decoded = new LostTalesChatOlderHistoryPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals(ChatMessageIds.NONE, decoded.getBeforeMessageId());
        // Trailing bytes are a malformed payload too.
        ByteBuf trailing = Unpooled.buffer();
        new LostTalesChatOlderHistoryPacket(ChatChannel.ALL, "", 42L).toBytes(trailing);
        trailing.writeByte(1);
        LostTalesChatOlderHistoryPacket extra = new LostTalesChatOlderHistoryPacket();
        extra.fromBytes(trailing);
        assertTrue(extra.isMalformed());
    }

    private static void assertRefused(ChatChannel channel, String scope, long before) {
        try {
            new LostTalesChatOlderHistoryPacket(channel, scope, before);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("a request for " + channel + "/" + scope + "/"
                + before + " was accepted");
    }
}
