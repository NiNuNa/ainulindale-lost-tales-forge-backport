package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public final class LostTalesMapMarkerSnapshotPacketTest {
    @Test
    public void priorityRoundTripsFromServerSnapshot() {
        LostTalesMapMarkerDefinition marker =
                new LostTalesMapMarkerDefinition(
                        "losttales:test", "Test", "fort", "white",
                        "Place", "", true, 100,
                        12.5D, 64.0D, -4.5D, 128.0D, 8.0D,
                        false, true, true,
                        LostTalesMapMarkerSource.CUSTOM_PRESET,
                        false, "", 99);
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesMapMarkerSnapshotPacket(
                Collections.singleton(marker)).toBytes(buffer);

        LostTalesMapMarkerSnapshotPacket decoded =
                new LostTalesMapMarkerSnapshotPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(99, decoded.getMarkers().get(0).getPriority());
    }
}
