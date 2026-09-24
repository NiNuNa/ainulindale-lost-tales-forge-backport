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

    @Test
    public void anActionThatNeedsAQuestIsRefusedWithoutOne() {
        assertTrue(roundTrip(LostTalesQuestActionPacket.ACTION_CLEAR, "")
                .isMalformed());
        assertTrue(roundTrip(LostTalesQuestActionPacket.ACTION_ABANDON, "")
                .isMalformed());
        // Stopping all tracking names no quest.
        assertFalse(roundTrip(LostTalesQuestActionPacket.ACTION_UNPIN, "")
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
