package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
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
        assertEquals(220.0D,
                decoded.getSettings().getCompassFadeInRadius(), 0.0D);
        assertEquals(32.0D, decoded.getSettings().getDiscoveryRadius(), 0.0D);
        assertEquals(LostTalesMapMarkerRelevance.HIGH.getRank(),
                decoded.getSettings().getPriority());
    }

    /** Destinations carries only the waystone it is taken at, and nothing runs on past it. */
    @Test
    public void destinationsRoundTripsWithNothingMore() {
        LostTalesWaystoneSettingsRequestPacket original =
                LostTalesWaystoneSettingsRequestPacket.destinations(
                        12, 70, -8, "losttales:player/test", 4L);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneSettingsRequestPacket decoded =
                new LostTalesWaystoneSettingsRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(com.ninuna.losttales.mapmarker
                        .LostTalesWaystoneSettingsOperation.DESTINATIONS,
                decoded.getOperation());
        assertEquals("losttales:player/test", decoded.getMarkerId());
        assertEquals("", decoded.getTargetName());

        ByteBuf longer = Unpooled.buffer();
        original.toBytes(longer);
        longer.writeByte(0);
        LostTalesWaystoneSettingsRequestPacket trailing =
                new LostTalesWaystoneSettingsRequestPacket();
        trailing.fromBytes(longer);
        assertTrue(trailing.isMalformed());
    }

    /** One settings check for both packets: a radius past what a marker keeps is refused before it is sent. */
    @Test(expected = IllegalArgumentException.class)
    public void aSaveWithARadiusPastTheBoundIsNeverSent() {
        LostTalesWaystoneSettingsRequestPacket.save(12, 70, -8,
                "losttales:player/test", 4L,
                new LostTalesMapMarkerEditableSettings(
                        "Bree Gate", "camp", "white", "", "", true,
                        LostTalesMapMarkerRecord.MAX_RADIUS + 1.0D, 8.0D,
                        false, true, false, 0,
                        LostTalesMapMarkerVisibility.PRIVATE));
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
                decoded.getTargetName());
    }

    @Test
    public void statePacketRoundTripsAuthorityFlags() {
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/state", "Aldric",
                        UUID.randomUUID(), 0, 4, 65, 9,
                        UUID.randomUUID()).toBuilder()
                        .sharedCharacterIds(Collections.singleton(
                                UUID.randomUUID()))
                        .sharedFellowshipIds(Collections.singleton(
                                UUID.randomUUID()))
                        .priority(73)
                        .build();
        LostTalesWaystoneStatePacket original =
                new LostTalesWaystoneStatePacket(
                        0, 4, 65, 9, record, "Aldric",
                        true, false, LostTalesWaystoneStateReason.OPENED);
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneStatePacket decoded =
                new LostTalesWaystoneStatePacket();
        decoded.fromBytes(buffer);

        assertFalse(decoded.isMalformed());
        assertEquals(record.getId(), decoded.getMarkerId());
        assertEquals("an unnamed waystone travels unnamed", "",
                decoded.getName());
        assertEquals("player:Aldric", decoded.getNamedAfter());
        assertTrue(decoded.isPlayerPlaced());
        assertEquals(record.getRevision(), decoded.getRevision());
        assertTrue(decoded.canEdit());
        assertFalse(decoded.managesMarkers());
        assertEquals("the owning character's name travels", "Aldric",
                decoded.getOwnerName());
        assertEquals(record.getDescription(),
                decoded.getSettings().getDescription());
        assertEquals(0, decoded.getDimensionId());
        assertEquals(1, decoded.getSharedCharacterCount());
        assertEquals(1, decoded.getSharedFellowshipCount());
        assertEquals(73, decoded.getSettings().getPriority());
        assertTrue("using the waystone opens its page", decoded.isOpening());
    }

    /** A waystone called after a lore figure of 64 three-byte letters still reaches its page. */
    @Test
    public void aLongNameInManyByteLettersIsCalledAfter() {
        StringBuilder placer = new StringBuilder();
        for (int index = 0; index < 64; index++) {
            placer.append('მ');
        }
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/lore", placer.toString(),
                        UUID.randomUUID(), 0, 4, 65, 9, UUID.randomUUID());

        LostTalesWaystoneStatePacket decoded = roundTrip(
                new LostTalesWaystoneStatePacket(0, 4, 65, 9, record, "",
                        true, false, LostTalesWaystoneStateReason.OPENED));

        assertFalse(decoded.isMalformed());
        assertEquals("player:" + placer, decoded.getNamedAfter());
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
        // Sixty-five letters of two bytes each: 130 bytes, past the
        // wire's 128, though far fewer characters.
        StringBuilder name = new StringBuilder();
        for (int index = 0; index < 65; index++) {
            name.append((char)0xE9);
        }
        LostTalesWaystoneSettingsRequestPacket.share(false, 12, 70, -8,
                "losttales:player/test", 4L, name.toString());
    }

    @Test
    public void handOverRoundTripsTheCharacterNamed() {
        LostTalesWaystoneSettingsRequestPacket original =
                LostTalesWaystoneSettingsRequestPacket.handOver(
                        12, 70, -8, "losttales:player/test", 4L, "Faramir");
        ByteBuf buffer = Unpooled.buffer();
        original.toBytes(buffer);
        LostTalesWaystoneSettingsRequestPacket decoded =
                new LostTalesWaystoneSettingsRequestPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(com.ninuna.losttales.mapmarker
                        .LostTalesWaystoneSettingsOperation.HAND_OVER,
                decoded.getOperation());
        assertEquals("Faramir", decoded.getTargetName());
    }

    @Test
    public void aStateWithAnOverlongOwnerNameIsMalformed() {
        StringBuilder owner = new StringBuilder();
        for (int index = 0; index
                <= LostTalesWaystoneStatePacket.MAX_OWNER_NAME_BYTES; index++) {
            owner.append('a');
        }
        LostTalesWaystoneStatePacket fine = state(
                LostTalesWaystoneStateReason.SAVED);
        ByteBuf buffer = Unpooled.buffer();
        fine.toBytes(buffer);
        // The owner name sits after the two share counts: rewrite the
        // packet by hand with one byte too many.
        ByteBuf forged = Unpooled.buffer();
        byte[] whole = new byte[buffer.readableBytes()];
        buffer.readBytes(whole);
        int tail = 1 + 1 + 1 + 1; // empty owner name, canEdit, manages, reason
        forged.writeBytes(whole, 0, whole.length - tail);
        LostTalesPacketCodec.writeUtf8String(forged, owner.toString(),
                owner.length());
        forged.writeBytes(whole, whole.length - tail + 1, tail - 1);
        LostTalesWaystoneStatePacket decoded =
                new LostTalesWaystoneStatePacket();
        decoded.fromBytes(forged);
        assertTrue(decoded.isMalformed());
    }

    private static LostTalesWaystoneStatePacket state(
            LostTalesWaystoneStateReason reason) {
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/reason", "Aldric",
                        UUID.randomUUID(), 0, 4, 65, 9,
                        UUID.randomUUID());
        return new LostTalesWaystoneStatePacket(0, 4, 65, 9, record,
                "", true, false, reason);
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
                true, 220.0D, 32.0D,
                true, true, true,
                LostTalesMapMarkerRelevance.HIGH.getRank(),
                LostTalesMapMarkerVisibility.SHARED);
    }
}
