package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Regression coverage for character markers synchronized without a fellowship. */
public final class FellowshipTrackingSyncPacketTest {

    @Test
    public void soloGoHereMarkerRoundTrips() {
        UUID ownerId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        UUID characterId = UUID.fromString(
                "20000000-0000-0000-0000-000000000002");
        FellowshipGoHereMarkerSnapshot marker = new FellowshipGoHereMarkerSnapshot(
                characterId, "Borin", FellowshipColor.GREEN,
                100, -22000.25D, 71.0D, -7278.5D, 1234L);
        FellowshipTrackingSnapshot snapshot = FellowshipTrackingSnapshot.noFellowship(
                ownerId, 7L, characterId,
                Collections.singletonList(marker));

        ByteBuf buffer = Unpooled.buffer();
        try {
            new FellowshipTrackingSyncPacket(snapshot).toBytes(buffer);
            FellowshipTrackingSyncPacket decoded =
                    new FellowshipTrackingSyncPacket();
            decoded.fromBytes(buffer);

            assertFalse(decoded.isMalformed());
            assertFalse(decoded.getSnapshot().hasFellowship());
            assertEquals(characterId,
                    decoded.getSnapshot().getActiveIdentityId());
            assertEquals(Collections.singletonList(marker),
                    decoded.getSnapshot().getGoHereMarkers());
        } finally {
            buffer.release();
        }
    }
}
