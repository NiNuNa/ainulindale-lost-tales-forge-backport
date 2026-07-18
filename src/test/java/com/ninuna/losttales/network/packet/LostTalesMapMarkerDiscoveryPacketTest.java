package com.ninuna.losttales.network.packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerDiscoveryPacketTest {

    @Test
    public void validDiscoveryRoundTrips() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMapMarkerDiscoveryPacket(
                "losttales:greenway_market", "Greenway Market")
                .toBytes(buffer);

        LostTalesMapMarkerDiscoveryPacket decoded =
                new LostTalesMapMarkerDiscoveryPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals("losttales:greenway_market", decoded.getMarkerId());
        assertEquals("Greenway Market", decoded.getMarkerName());
    }

    @Test
    public void trailingPayloadIsRejected() {
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMapMarkerDiscoveryPacket("losttales:camp", "Camp")
                .toBytes(buffer);
        buffer.writeByte(99);

        LostTalesMapMarkerDiscoveryPacket decoded =
                new LostTalesMapMarkerDiscoveryPacket();
        decoded.fromBytes(buffer);

        assertTrue(decoded.isMalformed());
        assertEquals("", decoded.getMarkerId());
    }

    @Test
    public void exactUtf8LimitsRoundTrip() {
        String markerId = repeat('i',
                LostTalesMapMarkerDiscoveryPacket.MAX_MARKER_ID_BYTES);
        String markerName = repeat('n',
                LostTalesMapMarkerDiscoveryPacket.MAX_MARKER_NAME_BYTES);
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMapMarkerDiscoveryPacket(markerId, markerName)
                .toBytes(buffer);

        LostTalesMapMarkerDiscoveryPacket decoded =
                new LostTalesMapMarkerDiscoveryPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(markerId, decoded.getMarkerId());
        assertEquals(markerName, decoded.getMarkerName());
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int i = 0; i < count; i++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
