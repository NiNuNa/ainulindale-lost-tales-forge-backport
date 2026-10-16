package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** A giver's offer on the wire: the speaker and the quest, and nothing of any other shape. */
public final class LostTalesQuestOfferPacketTest {

    @Test
    public void anOfferRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestOfferPacket(4711, "losttales:tutorial/meet_nia")
                .toBytes(buffer);
        LostTalesQuestOfferPacket decoded = new LostTalesQuestOfferPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(4711, decoded.getEntityId());
        assertEquals("losttales:tutorial/meet_nia", decoded.getQuestId());
    }

    @Test
    public void theLongestQuestIdRoundTrips() {
        StringBuilder id = new StringBuilder();
        while (id.length() < LostTalesQuestIds.MAX_BYTES) {
            id.append('q');
        }
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesQuestOfferPacket(-3, id.toString()).toBytes(buffer);
        LostTalesQuestOfferPacket decoded = new LostTalesQuestOfferPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(id.toString(), decoded.getQuestId());
    }

    @Test
    public void aMalformedOfferIsFlaggedAndNeverThrows() {
        assertMalformed(Unpooled.buffer());
        assertMalformed(Unpooled.buffer().writeInt(7));

        ByteBuf trailing = Unpooled.buffer();
        new LostTalesQuestOfferPacket(7, "losttales:quest").toBytes(trailing);
        trailing.writeByte(1);
        assertMalformed(trailing);

        ByteBuf empty = Unpooled.buffer();
        empty.writeInt(7);
        LostTalesPacketCodec.writeUtf8String(empty, "", 16);
        assertMalformed(empty);

        ByteBuf tooLong = Unpooled.buffer();
        tooLong.writeInt(7);
        byte[] bytes = new byte[LostTalesQuestIds.MAX_BYTES + 1];
        Arrays.fill(bytes, (byte)'q');
        LostTalesPacketCodec.writeUtf8String(tooLong,
                new String(bytes, Charset.forName("UTF-8")), bytes.length);
        assertMalformed(tooLong);

        ByteBuf huge = Unpooled.buffer();
        huge.writeZero(4096);
        assertMalformed(huge);
    }

    @Test(expected = IllegalArgumentException.class)
    public void anOfferOfNoQuestIsNeverMade() {
        new LostTalesQuestOfferPacket(1, "");
    }

    private static void assertMalformed(ByteBuf buffer) {
        LostTalesQuestOfferPacket decoded = new LostTalesQuestOfferPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getQuestId());
        assertEquals(0, buffer.readableBytes());
    }
}
