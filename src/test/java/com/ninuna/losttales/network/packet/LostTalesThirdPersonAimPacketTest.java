package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesThirdPersonAimPacketTest {

    @Test
    public void activeAimRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesThirdPersonAimPacket(
                true, 0.0F, 0.0F, 1.0F).toBytes(buffer);
        LostTalesThirdPersonAimPacket decoded =
                new LostTalesThirdPersonAimPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformedForTest());
        assertTrue(decoded.isActiveForTest());
    }

    @Test
    public void inactiveAimRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesThirdPersonAimPacket(
                false, 0.0F, 0.0F, 0.0F).toBytes(buffer);
        LostTalesThirdPersonAimPacket decoded =
                new LostTalesThirdPersonAimPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformedForTest());
        assertFalse(decoded.isActiveForTest());
    }

    @Test
    public void nonFiniteActiveDirectionIsMalformed() {
        ByteBuf buffer = completeBuffer(1, Float.NaN, 0.0F, 1.0F);
        LostTalesThirdPersonAimPacket decoded =
                new LostTalesThirdPersonAimPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    @Test
    public void invalidActiveFlagIsMalformed() {
        ByteBuf buffer = completeBuffer(2, 0.0F, 0.0F, 1.0F);
        LostTalesThirdPersonAimPacket decoded =
                new LostTalesThirdPersonAimPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    @Test
    public void trailingDataIsMalformed() {
        ByteBuf buffer = completeBuffer(1, 0.0F, 0.0F, 1.0F);
        buffer.writeByte(0);
        LostTalesThirdPersonAimPacket decoded =
                new LostTalesThirdPersonAimPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    private static ByteBuf completeBuffer(
            int active, float x, float y, float z) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(active);
        buffer.writeFloat(x);
        buffer.writeFloat(y);
        buffer.writeFloat(z);
        return buffer;
    }
}
