package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesThirdPersonEntityActionPacketTest {

    @Test
    public void validActionRoundTrips() {
        LostTalesThirdPersonEntityActionPacket original =
                new LostTalesThirdPersonEntityActionPacket(
                        LostTalesThirdPersonEntityActionPacket.Action.ATTACK,
                        42, 1.25D, 64.5D, -8.0D);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        LostTalesThirdPersonEntityActionPacket decoded =
                new LostTalesThirdPersonEntityActionPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformedForTest());
        assertEquals(
                LostTalesThirdPersonEntityActionPacket.Action.ATTACK,
                decoded.getActionForTest());
        assertEquals(42, decoded.getEntityIdForTest());
        assertFalse(decoded.shouldUseItemIfInteractionDeclinesForTest());
    }

    @Test
    public void interactionFallbackFlagRoundTrips() {
        LostTalesThirdPersonEntityActionPacket original =
                new LostTalesThirdPersonEntityActionPacket(
                        LostTalesThirdPersonEntityActionPacket.Action.INTERACT,
                        7, 2.0D, 65.0D, 4.0D, true);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);

        LostTalesThirdPersonEntityActionPacket decoded =
                new LostTalesThirdPersonEntityActionPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformedForTest());
        assertEquals(
                LostTalesThirdPersonEntityActionPacket.Action.INTERACT,
                decoded.getActionForTest());
        assertTrue(decoded.shouldUseItemIfInteractionDeclinesForTest());
    }

    @Test
    public void unknownActionIsMalformed() {
        ByteBuf buffer = completeBuffer(99, 42, 1.0D, 2.0D, 3.0D);
        LostTalesThirdPersonEntityActionPacket decoded =
                new LostTalesThirdPersonEntityActionPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    @Test
    public void nonFiniteHitIsMalformed() {
        ByteBuf buffer = completeBuffer(1, 42,
                Double.NaN, 2.0D, 3.0D);
        LostTalesThirdPersonEntityActionPacket decoded =
                new LostTalesThirdPersonEntityActionPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    @Test
    public void trailingDataIsMalformed() {
        ByteBuf buffer = completeBuffer(1, 42, 1.0D, 2.0D, 3.0D);
        buffer.writeByte(1);
        LostTalesThirdPersonEntityActionPacket decoded =
                new LostTalesThirdPersonEntityActionPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformedForTest());
    }

    private static ByteBuf completeBuffer(
            int action, int entityId,
            double x, double y, double z) {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeByte(action);
        buffer.writeInt(entityId);
        buffer.writeDouble(x);
        buffer.writeDouble(y);
        buffer.writeDouble(z);
        buffer.writeBoolean(false);
        return buffer;
    }
}
