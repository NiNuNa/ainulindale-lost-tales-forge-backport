package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesWaystonePacketTest {

    @Test
    public void settingsRequestRoundTripsExplicitFields() {
        LostTalesWaystoneSettingsRequestPacket original =
                LostTalesWaystoneSettingsRequestPacket.save(
                        12, 70, -8, "losttales:player/test", 4L,
                        settings());
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneSettingsRequestPacket decoded =
                new LostTalesWaystoneSettingsRequestPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals("Bree Gate",
                decoded.getSettings().getName());
        assertEquals("tavern",
                decoded.getSettings().getIconName());
        assertEquals("#aabbcc",
                decoded.getSettings().getColorName());
        assertEquals(4L, decoded.getExpectedRevision());
        assertEquals(LostTalesMapMarkerVisibility.SHARED,
                decoded.getSettings().getVisibility());
        assertEquals(LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                decoded.getSettings().getY(), 0.0D);
        assertEquals("losttales:glowstone_house",
                decoded.getSettings().getWaystoneStructureType());
    }

    @Test
    public void statePacketRoundTripsAuthorityFlags() {
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/state", "State Waystone",
                        UUID.randomUUID(), 0, 4, 65, 9,
                        UUID.randomUUID());
        LostTalesWaystoneStatePacket original =
                new LostTalesWaystoneStatePacket(
                        0, 4, 65, 9, record,
                        true, false, true);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneStatePacket decoded =
                new LostTalesWaystoneStatePacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(record.getId(), decoded.getMarkerId());
        assertEquals(record.getRevision(), decoded.getRevision());
        assertTrue(decoded.canEdit());
        assertFalse(decoded.canMakePublic());
        assertTrue(decoded.canEditAdvanced());
        assertEquals(record.getDescription(),
                decoded.getDescription());
        assertEquals(record.getDimensionId(),
                decoded.getMarkerDimensionId());
    }

    @Test
    public void travelRequestRoundTripsAndRejectsTrailingData() {
        LostTalesWaystoneTravelRequestPacket original =
                new LostTalesWaystoneTravelRequestPacket(
                        1, 64, 2, "losttales:source",
                        "losttales:destination");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneTravelRequestPacket decoded =
                new LostTalesWaystoneTravelRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals("losttales:destination",
                decoded.getDestinationMarkerId());

        ByteBuf malformed = Unpooled.buffer();
        original.toBytes(malformed);
        malformed.writeByte(1);
        LostTalesWaystoneTravelRequestPacket rejected =
                new LostTalesWaystoneTravelRequestPacket();
        rejected.fromBytes(malformed);
        assertTrue(rejected.isMalformed());
    }

    private static LostTalesMapMarkerEditableSettings settings() {
        return new LostTalesMapMarkerEditableSettings(
                "Bree Gate", "tavern", "#aabbcc",
                "Town", "The west gate of Bree.",
                true, "bree",
                100, 12.5D,
                LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                -8.5D, 220.0D, 32.0D,
                true, true, true,
                true, "losttales:glowstone_house",
                LostTalesMapMarkerVisibility.SHARED);
    }
}
