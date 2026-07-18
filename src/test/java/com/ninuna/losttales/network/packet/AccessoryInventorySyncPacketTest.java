package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class AccessoryInventorySyncPacketTest {

    @Test
    public void emptyAuthoritativeSnapshotRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new AccessoryInventorySyncPacket(17L, null, true).toBytes(buffer);

        AccessoryInventorySyncPacket decoded =
                new AccessoryInventorySyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(17L, decoded.getRevision());
        assertNull(decoded.getEquipped());
        assertTrue(decoded.hasRejectedEntry());
    }

    @Test
    public void trailingPayloadIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        new AccessoryInventorySyncPacket(3L, null, false).toBytes(buffer);
        buffer.writeByte(99);

        AccessoryInventorySyncPacket decoded =
                new AccessoryInventorySyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertEquals(-1L, decoded.getRevision());
    }
}
