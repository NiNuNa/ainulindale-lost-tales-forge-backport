package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A journal or conversation request names one known action and, where the action needs one, a quest. */
public final class LostTalesQuestActionPacketTest {
    private static final String LOTR_QUEST =
            "lotr:miniquest/3f2a6c1e-8d4b-4e7a-9c1f-2b5d7e9a0c3d";

    @Test
    public void aClearRoundTrips() {
        LostTalesQuestActionPacket decoded = roundTrip(
                LostTalesQuestActionPacket.ACTION_CLEAR, LOTR_QUEST);
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesQuestActionPacket.ACTION_CLEAR,
                decoded.getAction());
        assertEquals(LOTR_QUEST, decoded.getQuestId());
    }

    /** A hand-in names the item taken from its pick; a choice past a pick's is malformed (QUEST-33). */
    @Test
    public void aHandInCarriesItsChoice() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_HAND_IN,
                "losttales:test/q", 2).toBytes(buffer);
        LostTalesQuestActionPacket decoded = new LostTalesQuestActionPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getChoice());
        assertEquals(-1, roundTrip(LostTalesQuestActionPacket.ACTION_HAND_IN,
                "losttales:test/q").getChoice());

        ByteBuf forged = Unpooled.buffer();
        new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_HAND_IN,
                "losttales:test/q").toBytes(forged);
        forged.setByte(forged.writerIndex() - 1, 9);
        LostTalesQuestActionPacket refused = new LostTalesQuestActionPacket();
        refused.fromBytes(forged);
        assertTrue(refused.isMalformed());
    }

    /** Collect names the quest whose waiting rewards it asks for (QUEST-44); without one it is malformed. */
    @Test
    public void aCollectNamesItsQuest() {
        LostTalesQuestActionPacket decoded = roundTrip(
                LostTalesQuestActionPacket.ACTION_COLLECT,
                "losttales:tutorial/cheese_cache");
        assertFalse(decoded.isMalformed());
        assertEquals(LostTalesQuestActionPacket.ACTION_COLLECT, decoded.getAction());
        assertEquals("losttales:tutorial/cheese_cache", decoded.getQuestId());
        assertTrue(roundTrip(LostTalesQuestActionPacket.ACTION_COLLECT, "")
                .isMalformed());
    }

    @Test
    public void anActionThatNeedsAQuestIsRefusedWithoutOne() {
        assertTrue(roundTrip(LostTalesQuestActionPacket.ACTION_CLEAR, "")
                .isMalformed());
        assertTrue(roundTrip(LostTalesQuestActionPacket.ACTION_ABANDON, "")
                .isMalformed());
        // Stopping all tracking names no quest.
        assertFalse(roundTrip(LostTalesQuestActionPacket.ACTION_UNTRACK, "")
                .isMalformed());
    }

    @Test
    public void anUnknownActionOrTrailingBytesAreMalformed() {
        assertTrue(roundTrip("forget", LOTR_QUEST).isMalformed());

        ByteBuf trailing = Unpooled.buffer();
        new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_CLEAR,
                LOTR_QUEST).toBytes(trailing);
        trailing.writeByte(1);
        LostTalesQuestActionPacket decoded = new LostTalesQuestActionPacket();
        decoded.fromBytes(trailing);
        assertTrue(decoded.isMalformed());
        assertEquals("a malformed payload is read to its end", 0,
                trailing.readableBytes());
    }

    @Test
    public void aCutPayloadIsMalformedAndReadToItsEnd() {
        ByteBuf whole = Unpooled.buffer();
        new LostTalesQuestActionPacket(LostTalesQuestActionPacket.ACTION_CLEAR,
                LOTR_QUEST).toBytes(whole);
        ByteBuf cut = whole.copy(0, whole.readableBytes() - 4);
        LostTalesQuestActionPacket decoded = new LostTalesQuestActionPacket();
        decoded.fromBytes(cut);
        assertTrue(decoded.isMalformed());
        assertEquals(0, cut.readableBytes());
    }

    private static LostTalesQuestActionPacket roundTrip(String action,
                                                        String questId) {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestActionPacket(action, questId).toBytes(buffer);
        LostTalesQuestActionPacket decoded = new LostTalesQuestActionPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }
}
