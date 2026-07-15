package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyGoHereMarkerSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackingSnapshot;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/** Regression coverage for character markers synchronized without a party. */
public final class PartyTrackingSyncPacketTest {

    @Test
    public void soloGoHereMarkerRoundTrips() {
        UUID ownerId = UUID.fromString(
                "10000000-0000-0000-0000-000000000001");
        UUID characterId = UUID.fromString(
                "20000000-0000-0000-0000-000000000002");
        PartyGoHereMarkerSnapshot marker = new PartyGoHereMarkerSnapshot(
                characterId, "Borin", PartyColor.GREEN,
                100, -22000.25D, 71.0D, -7278.5D, 1234L);
        PartyTrackingSnapshot snapshot = PartyTrackingSnapshot.noParty(
                ownerId, 7L, characterId,
                Collections.singletonList(marker));

        ByteBuf buffer = Unpooled.buffer();
        try {
            new PartyTrackingSyncPacket(snapshot).toBytes(buffer);
            PartyTrackingSyncPacket decoded =
                    new PartyTrackingSyncPacket();
            decoded.fromBytes(buffer);

            assertFalse(decoded.isMalformed());
            assertFalse(decoded.getSnapshot().hasParty());
            assertEquals(characterId,
                    decoded.getSnapshot().getActiveCharacterId());
            assertEquals(Collections.singletonList(marker),
                    decoded.getSnapshot().getGoHereMarkers());
        } finally {
            buffer.release();
        }
    }
}
