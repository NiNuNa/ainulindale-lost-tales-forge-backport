package com.ninuna.losttales.network.packet;

import cpw.mods.fml.common.network.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AccessoryEffectSyncPacketTest {

    @Test
    public void removalSnapshotRoundTrips() {
        UUID playerId = UUID.randomUUID();
        ByteBuf buffer = Unpooled.buffer();
        new AccessoryEffectSyncPacket(
                playerId, 42, 9L, "").toBytes(buffer);

        AccessoryEffectSyncPacket decoded =
                new AccessoryEffectSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(playerId, decoded.getPlayerId());
        assertEquals(42, decoded.getEntityId());
        assertEquals(9L, decoded.getSequence());
        assertEquals("", decoded.getDefinitionId());
    }

    @Test
    public void unknownDefinitionIsRejected() {
        UUID playerId = UUID.randomUUID();
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeLong(playerId.getMostSignificantBits());
        buffer.writeLong(playerId.getLeastSignificantBits());
        buffer.writeInt(42);
        buffer.writeLong(1L);
        ByteBufUtils.writeUTF8String(buffer, "unknown:ring");

        AccessoryEffectSyncPacket decoded =
                new AccessoryEffectSyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
    }
}
