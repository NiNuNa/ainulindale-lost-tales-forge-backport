package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.entity.combat.LostTalesCombatEngagement;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A combat contact travels as its creature's kind and, where a player named
 * it, that name as written (MAP-16); a kind that is no entity id, a name
 * past its bound or bytes left over make the snapshot malformed and empty.
 */
public final class LostTalesMobAggroSyncPacketTest {

    private static LostTalesMobAggroSyncPacket.Entry orc() {
        return new LostTalesMobAggroSyncPacket.Entry(17, LostTalesCombatEngagement.ATTACKING,
                false, "lotr.MordorOrc", "", 10.5D, 64.0D, -20.0D);
    }

    private static LostTalesMobAggroSyncPacket.Entry namedWolf() {
        return new LostTalesMobAggroSyncPacket.Entry(18, LostTalesCombatEngagement.TARGETING,
                true, "Wolf", "Grip", 11.0D, 65.5D, -21.0D);
    }

    @Test
    public void aContactRoundTripsWithItsKindAndItsOwnName() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMobAggroSyncPacket(0, 5, 32, Arrays.asList(orc(), namedWolf()))
                .toBytes(buffer);

        LostTalesMobAggroSyncPacket decoded = new LostTalesMobAggroSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(5, decoded.getSequence());
        assertEquals(32, decoded.getTrackingRadius());
        List<LostTalesMobAggroSyncPacket.Entry> entries = decoded.getEntries();
        assertEquals(Arrays.asList(orc(), namedWolf()), entries);
        assertEquals("lotr.MordorOrc", entries.get(0).getKind());
        assertEquals("", entries.get(0).getOwnName());
        assertEquals("Grip", entries.get(1).getOwnName());
        assertTrue(entries.get(1).isSharedFromFellowship());
    }

    @Test
    public void aKindThatIsNoEntityIdMakesTheSnapshotMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(0);
        buffer.writeInt(5);
        buffer.writeInt(32);
        buffer.writeInt(1);
        buffer.writeInt(17);
        buffer.writeByte(LostTalesCombatEngagement.ATTACKING.getNetworkId());
        buffer.writeBoolean(false);
        buffer.writeDouble(1.0D);
        buffer.writeDouble(64.0D);
        buffer.writeDouble(1.0D);
        LostTalesPacketCodec.writeUtf8String(buffer, "Orc of Mordor§c",
                LostTalesMobAggroSyncPacket.MAX_KIND_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, "",
                LostTalesMobAggroSyncPacket.MAX_ENTITY_NAME_BYTES);

        LostTalesMobAggroSyncPacket decoded = new LostTalesMobAggroSyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getEntries().isEmpty());
        assertEquals(0, buffer.readableBytes());
    }

    @Test
    public void aTruncatedOrOverlongSnapshotIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMobAggroSyncPacket(0, 5, 32, Arrays.asList(orc())).toBytes(buffer);
        buffer.writeByte(1);
        LostTalesMobAggroSyncPacket trailing = new LostTalesMobAggroSyncPacket();
        trailing.fromBytes(buffer);
        assertTrue(trailing.isMalformed());

        ByteBuf whole = Unpooled.buffer();
        new LostTalesMobAggroSyncPacket(0, 5, 32, Arrays.asList(orc())).toBytes(whole);
        ByteBuf cut = whole.copy(0, whole.readableBytes() - 3);
        LostTalesMobAggroSyncPacket truncated = new LostTalesMobAggroSyncPacket();
        truncated.fromBytes(cut);
        assertTrue(truncated.isMalformed());
        assertTrue(truncated.getEntries().isEmpty());
    }

    @Test
    public void onlyAnEntityIdTravelsAsAKind() {
        assertEquals("lotr.MordorOrc", LostTalesMobAggroSyncPacket.kindOf(" lotr.MordorOrc "));
        assertEquals("Zombie", LostTalesMobAggroSyncPacket.kindOf("Zombie"));
        assertEquals("", LostTalesMobAggroSyncPacket.kindOf(null));
        assertEquals("", LostTalesMobAggroSyncPacket.kindOf("Orc of Mordor"));
        assertEquals("", LostTalesMobAggroSyncPacket.kindOf("entity§c"));
        StringBuilder tooLong = new StringBuilder();
        while (tooLong.length() <= LostTalesMobAggroSyncPacket.MAX_KIND_BYTES) {
            tooLong.append('a');
        }
        assertEquals("", LostTalesMobAggroSyncPacket.kindOf(tooLong.toString()));
        assertTrue(LostTalesMobAggroSyncPacket.isKind(""));
    }

    @Test
    public void anOwnNameIsCutToItsBound() {
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 100; index++) {
            name.append('é');
        }
        String bounded = LostTalesMobAggroSyncPacket.boundedName("  " + name + "  ");
        assertEquals(LostTalesMobAggroSyncPacket.MAX_ENTITY_NAME_BYTES,
                bounded.getBytes(java.nio.charset.StandardCharsets.UTF_8).length);
        assertEquals("", LostTalesMobAggroSyncPacket.boundedName(null));
    }
}
