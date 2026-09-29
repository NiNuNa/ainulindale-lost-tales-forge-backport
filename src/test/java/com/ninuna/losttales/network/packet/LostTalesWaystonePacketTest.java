package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerHeightResolver;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRelevance;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneStateReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.Collections;
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
        assertEquals(LostTalesMapMarkerRelevance.HIGH.getRank(),
                decoded.getSettings().getPriority());
    }

    @Test
    public void fellowshipSharingRequestRoundTripsTargetAndOperation() {
        LostTalesWaystoneSettingsRequestPacket original =
                LostTalesWaystoneSettingsRequestPacket
                        .shareFellowship(
                                false, 12, 70, -8,
                                "losttales:player/test", 4L,
                                "The Prancing Ponies");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneSettingsRequestPacket decoded =
                new LostTalesWaystoneSettingsRequestPacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(
                com.ninuna.losttales.mapmarker
                        .LostTalesWaystoneSettingsOperation
                        .SHARE_FELLOWSHIP,
                decoded.getOperation());
        assertEquals("The Prancing Ponies",
                decoded.getTargetPlayerName());
    }

    @Test
    public void statePacketRoundTripsAuthorityFlags() {
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/state", "State Waystone",
                        UUID.randomUUID(), 0, 4, 65, 9,
                        UUID.randomUUID()).toBuilder()
                        .sharedFellowshipIds(Collections.singleton(
                                UUID.randomUUID()))
                        .priority(73)
                        .build();
        LostTalesWaystoneStatePacket original =
                new LostTalesWaystoneStatePacket(
                        0, 4, 65, 9, record,
                        true, false, LostTalesWaystoneStateReason.OPENED);
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
        assertEquals(record.getDescription(),
                decoded.getSettings().getDescription());
        assertEquals(record.getDimensionId(),
                decoded.getSettings().getDimensionId());
        assertEquals(1, decoded.getSharedFellowshipCount());
        assertEquals(73, decoded.getSettings().getPriority());
        assertTrue("using the waystone opens its page", decoded.isOpening());
    }

    @Test
    public void stateCarriesWhyItWasSentAndOnlyAnOpeningOpens() {
        for (LostTalesWaystoneStateReason reason
                : LostTalesWaystoneStateReason.values()) {
            LostTalesWaystoneStatePacket decoded = roundTrip(state(reason));
            assertFalse(reason.name(), decoded.isMalformed());
            assertEquals(reason, decoded.getReason());
            assertEquals(reason == LostTalesWaystoneStateReason.OPENED,
                    decoded.isOpening());
            assertEquals(reason.name(), reason,
                    LostTalesWaystoneStateReason.fromNetworkId(
                            reason.getNetworkId()));
        }
        assertFalse(LostTalesWaystoneStateReason.SAVED.isRefusal());
        assertTrue(LostTalesWaystoneStateReason.STALE.isRefusal());
    }

    @Test
    public void stateWithAnUnknownReasonIsMalformed() {
        ByteBuf buffer = Unpooled.buffer();
        state(LostTalesWaystoneStateReason.SAVED).toBytes(buffer);
        buffer.setByte(buffer.writerIndex() - 1, 200);
        LostTalesWaystoneStatePacket decoded =
                new LostTalesWaystoneStatePacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertEquals("nothing is left to read", 0, buffer.readableBytes());
    }

    @Test
    public void stateCutShortOrRunningOnIsMalformed() {
        ByteBuf whole = Unpooled.buffer();
        state(LostTalesWaystoneStateReason.OPENED).toBytes(whole);
        ByteBuf cut = whole.copy(0, whole.writerIndex() - 1);
        LostTalesWaystoneStatePacket truncated =
                new LostTalesWaystoneStatePacket();
        truncated.fromBytes(cut);
        assertTrue("the reason missing", truncated.isMalformed());
        assertFalse(truncated.isOpening());

        ByteBuf longer = whole.copy();
        longer.writeByte(0);
        LostTalesWaystoneStatePacket trailing =
                new LostTalesWaystoneStatePacket();
        trailing.fromBytes(longer);
        assertTrue("a byte past the reason", trailing.isMalformed());

        LostTalesWaystoneStatePacket empty =
                new LostTalesWaystoneStatePacket();
        empty.fromBytes(Unpooled.buffer());
        assertTrue(empty.isMalformed());
    }

    @Test(expected = IllegalArgumentException.class)
    public void stateNeedsAReason() {
        state(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void aShareNameTooLongForTheWireIsRefusedBeforeItIsSent() {
        // Thirty-three letters of two bytes each: 66 bytes, past the
        // wire's 64, though far fewer characters.
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 33; index++) {
            name.append((char)0xE9);
        }
        LostTalesWaystoneSettingsRequestPacket.share(false, 12, 70, -8,
                "losttales:player/test", 4L, name.toString());
    }

    private static LostTalesWaystoneStatePacket state(
            LostTalesWaystoneStateReason reason) {
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/reason", "Reason Waystone",
                        UUID.randomUUID(), 0, 4, 65, 9,
                        UUID.randomUUID());
        return new LostTalesWaystoneStatePacket(0, 4, 65, 9, record, true,
                false, reason);
    }

    private static LostTalesWaystoneStatePacket roundTrip(
            LostTalesWaystoneStatePacket original) {
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneStatePacket decoded =
                new LostTalesWaystoneStatePacket();
        decoded.fromBytes(buffer);
        return decoded;
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
                true, 100, 12.5D,
                LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                -8.5D, 220.0D, 32.0D,
                true, true, true,
                true, "losttales:glowstone_house",
                LostTalesMapMarkerRelevance.HIGH,
                LostTalesMapMarkerVisibility.SHARED);
    }
}
