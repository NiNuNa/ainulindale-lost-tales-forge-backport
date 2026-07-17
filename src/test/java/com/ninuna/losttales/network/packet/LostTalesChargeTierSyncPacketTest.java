package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesChargeTierSyncPacketTest {

    @Test
    public void validTierTransitionRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChargeTierSyncPacket(
                42, true, false, 2, 1.09D).toBytes(buffer);

        LostTalesChargeTierSyncPacket decoded =
                new LostTalesChargeTierSyncPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(42, decoded.getEntityId());
        assertTrue(decoded.isActive());
        assertFalse(decoded.isReleased());
        assertEquals(2, decoded.getTier());
        assertEquals(1.09D,
                decoded.getVelocityMultiplier(), 0.000001D);
    }

    @Test
    public void activeReleaseCombinationIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(42);
        buffer.writeBoolean(true);
        buffer.writeBoolean(true);
        buffer.writeByte(3);
        buffer.writeFloat(1.16F);

        LostTalesChargeTierSyncPacket decoded =
                new LostTalesChargeTierSyncPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertFalse(decoded.isActive());
        assertEquals(0, decoded.getTier());
    }
}
