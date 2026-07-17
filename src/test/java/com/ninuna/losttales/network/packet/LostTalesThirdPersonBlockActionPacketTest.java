package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesThirdPersonBlockActionPacketTest {

    @Test
    public void validActionRoundTripsWithVanillaQuantization() {
        LostTalesThirdPersonBlockActionPacket original =
                new LostTalesThirdPersonBlockActionPacket(
                        12, 64, -8, 3,
                        0.51F, 1.0F, 0.249F);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        LostTalesThirdPersonBlockActionPacket decoded =
                new LostTalesThirdPersonBlockActionPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformedForTest());
        assertEquals(12, decoded.getXForTest());
        assertEquals(64, decoded.getYForTest());
        assertEquals(-8, decoded.getZForTest());
        assertEquals(3, decoded.getSideForTest());
        assertEquals(0.5F, decoded.getHitOffsetXForTest(), 0.0F);
        assertEquals(1.0F, decoded.getHitOffsetYForTest(), 0.0F);
        assertEquals(0.1875F, decoded.getHitOffsetZForTest(), 0.0F);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsOutOfBoundsOffsets() {
        new LostTalesThirdPersonBlockActionPacket(
                0, 64, 0, 1,
                0.5F, 1.01F, 0.5F);
    }

    @Test
    public void invalidSideIsMalformed() {
        ByteBuf buffer = completeBuffer(0, 64, 0, 6, 8, 8, 8);
        LostTalesThirdPersonBlockActionPacket decoded =
                new LostTalesThirdPersonBlockActionPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    @Test
    public void invalidOffsetUnitIsMalformed() {
        ByteBuf buffer = completeBuffer(0, 64, 0, 1, 8, 17, 8);
        LostTalesThirdPersonBlockActionPacket decoded =
                new LostTalesThirdPersonBlockActionPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    @Test
    public void trailingDataIsMalformed() {
        ByteBuf buffer = completeBuffer(0, 64, 0, 1, 8, 16, 8);
        buffer.writeByte(1);
        LostTalesThirdPersonBlockActionPacket decoded =
                new LostTalesThirdPersonBlockActionPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    private static ByteBuf completeBuffer(
            int x, int y, int z, int side,
            int offsetX, int offsetY, int offsetZ) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(x);
        buffer.writeByte(y);
        buffer.writeInt(z);
        buffer.writeByte(side);
        buffer.writeByte(offsetX);
        buffer.writeByte(offsetY);
        buffer.writeByte(offsetZ);
        return buffer;
    }
}
