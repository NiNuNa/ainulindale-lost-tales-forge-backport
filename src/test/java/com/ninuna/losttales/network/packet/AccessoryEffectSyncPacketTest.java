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

    /**
     * The accessory named is looked up as it is used, in the list the
     * server sent ahead of it (ACC-01), so a name the client does not hold
     * yet still decodes.
     */
    @Test
    public void aDefinitionIsLookedUpAsItIsUsed() {
        AccessoryEffectSyncPacket decoded = decode("myserver:signet");

        assertFalse(decoded.isMalformed());
        assertEquals("myserver:signet", decoded.getDefinitionId());
    }

    @Test
    public void anOverlongDefinitionIsRejected() {
        StringBuilder name = new StringBuilder("a:");
        while (name.length() <= AccessoryEffectSyncPacket.MAX_DEFINITION_ID_BYTES) {
            name.append('x');
        }
        assertTrue(decode(name.toString()).isMalformed());
    }

    private static AccessoryEffectSyncPacket decode(String definitionId) {
        UUID playerId = UUID.randomUUID();
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(1);
        buffer.writeLong(playerId.getMostSignificantBits());
        buffer.writeLong(playerId.getLeastSignificantBits());
        buffer.writeInt(42);
        buffer.writeLong(1L);
        ByteBufUtils.writeUTF8String(buffer, definitionId);

        AccessoryEffectSyncPacket decoded =
                new AccessoryEffectSyncPacket();
        decoded.fromBytes(buffer);
        return decoded;
    }
}
