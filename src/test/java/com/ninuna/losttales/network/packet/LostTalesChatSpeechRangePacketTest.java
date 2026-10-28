package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatSpeechRange;
import com.ninuna.losttales.chat.ChatUnseenVoice;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Speech ranges on the wire (CHAT-37) and the typist's ids (CHAT-61,
 * CHAT-84): a line heard nearby carries its range and its reader's size
 * step, every other line talk at the usual size; requests name a range
 * only for a conversation heard nearby; a typing notice names its typist
 * by ids, or nobody for an unseen voice.
 */
public final class LostTalesChatSpeechRangePacketTest {
    private static final UUID STEVE = new UUID(7L, 1L);
    private static final UUID ALDRIC = new UUID(7L, 2L);

    @Test
    public void aLineHeardNearbyCarriesItsRangeAndItsReadersSize() {
        LostTalesChatMessagePacket line = ChatPacketFixtures.line(
                ChatChannel.PROXIMITY, "Aldric", "Steve", "Turn back.").build()
                .withSpeechRange(ChatSpeechRange.SHOUT)
                .withDistanceStep(ChatSpeechRange.FAR_STEP);
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatSpeechRange.SHOUT, decoded.getSpeechRange());
        assertEquals(ChatSpeechRange.FAR_STEP, decoded.getDistanceStep());
        // Every copy made from it keeps both.
        assertEquals(ChatSpeechRange.SHOUT,
                decoded.withMessage("Turn back!").getSpeechRange());
        assertEquals(ChatSpeechRange.FAR_STEP,
                decoded.withoutEcho().getDistanceStep());
    }

    @Test
    public void aLineOfAnyOtherChannelIsTalkAtTheUsualSize() {
        LostTalesChatMessagePacket line = ChatPacketFixtures.line(
                ChatChannel.GLOBAL, "Aldric", "Steve", "Hello.").build()
                .withSpeechRange(ChatSpeechRange.WHISPER)
                .withDistanceStep(ChatSpeechRange.NEAR_STEP);
        assertEquals(ChatSpeechRange.TALK, line.getSpeechRange());
        assertEquals(ChatSpeechRange.USUAL_STEP, line.getDistanceStep());
    }

    @Test
    public void aStepOrARangeNoBuildKnowsIsMalformed() {
        LostTalesChatMessagePacket line = ChatPacketFixtures.line(
                ChatChannel.PROXIMITY, "Aldric", "Steve", "Hush.").build();
        ByteBuf buffer = Unpooled.buffer();
        line.toBytes(buffer);
        // The last two bytes are the range and the step stored one up.
        buffer.setByte(buffer.writerIndex() - 1, 7);
        LostTalesChatMessagePacket decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        buffer = Unpooled.buffer();
        line.toBytes(buffer);
        buffer.setByte(buffer.writerIndex() - 2, 9);
        decoded = new LostTalesChatMessagePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
    }

    @Test
    public void aSendNamesARangeOnlyWhereItIsHeardNearby() {
        LostTalesChatSendPacket send = ChatPacketFixtures.send(ChatChannel.PROXIMITY,
                "psst").build().range(ChatSpeechRange.WHISPER);
        ByteBuf buffer = Unpooled.buffer();
        send.toBytes(buffer);
        LostTalesChatSendPacket decoded = new LostTalesChatSendPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(ChatSpeechRange.WHISPER, decoded.getRange());
        try {
            ChatPacketFixtures.send(ChatChannel.GLOBAL, "psst").build()
                    .range(ChatSpeechRange.SHOUT);
            fail("a range on a channel not heard nearby");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void typingAndAMemberListAskNameTheirRange() {
        LostTalesChatTypingPacket typing = ChatPacketFixtures.typing(
                ChatChannel.PROXIMITY, "", true).range(ChatSpeechRange.SHOUT);
        ByteBuf buffer = Unpooled.buffer();
        typing.toBytes(buffer);
        LostTalesChatTypingPacket decodedTyping = new LostTalesChatTypingPacket();
        decodedTyping.fromBytes(buffer);
        assertFalse(decodedTyping.isMalformed());
        assertEquals(ChatSpeechRange.SHOUT, decodedTyping.getRange());

        LostTalesChatMembersRequestPacket ask = new LostTalesChatMembersRequestPacket(
                ChatChannel.PROXIMITY, "proximity|range:whisper", "", "", null,
                null, 0L).range(ChatSpeechRange.WHISPER);
        buffer = Unpooled.buffer();
        ask.toBytes(buffer);
        LostTalesChatMembersRequestPacket decodedAsk =
                new LostTalesChatMembersRequestPacket();
        decodedAsk.fromBytes(buffer);
        assertFalse(decodedAsk.isMalformed());
        assertEquals(ChatSpeechRange.WHISPER, decodedAsk.getRange());
        try {
            new LostTalesChatMembersRequestPacket(ChatChannel.OOC, "ooc", "",
                    "", null, null, 0L).range(ChatSpeechRange.WHISPER);
            fail("a range on a channel not heard nearby");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }

    @Test
    public void typingNamesItsTypistByIdsAndAnUnseenVoiceNobody() {
        LostTalesChatTypingSyncPacket named = new LostTalesChatTypingSyncPacket(
                ChatChannel.PROXIMITY, "", "Aldric", false, true, "", "")
                .from(STEVE, ALDRIC);
        ByteBuf buffer = Unpooled.buffer();
        named.toBytes(buffer);
        LostTalesChatTypingSyncPacket decoded = new LostTalesChatTypingSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(STEVE, decoded.getAccountId());
        assertEquals(ALDRIC, decoded.getCharacterId());
        assertFalse(decoded.isUnseen());

        LostTalesChatTypingSyncPacket unseen = LostTalesChatTypingSyncPacket
                .unseenVoice(ChatChannel.PROXIMITY, true, "");
        buffer = Unpooled.buffer();
        unseen.toBytes(buffer);
        decoded = new LostTalesChatTypingSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isUnseen());
        assertEquals(ChatUnseenVoice.NAME, decoded.getIdentityName());
        assertNull(decoded.getAccountId());
        try {
            LostTalesChatTypingSyncPacket.unseenVoice(ChatChannel.PROXIMITY,
                    true, "").from(STEVE, null);
            fail("an unseen voice names nobody");
        } catch (IllegalArgumentException expected) {
            // refused
        }
        try {
            new LostTalesChatTypingSyncPacket(ChatChannel.PROXIMITY, "",
                    "Narrator", true, true, "", "").from(STEVE, null);
            fail("the Narrator names nobody");
        } catch (IllegalArgumentException expected) {
            // refused
        }
    }
}
