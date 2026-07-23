package com.ninuna.losttales.mapmarker;

import java.util.Collections;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerVisibilityPolicyTest {

    @Test
    public void ownerSharedPublicAndOperatorAccessAreDistinct() {
        UUID owner = UUID.randomUUID();
        UUID guest = UUID.randomUUID();
        UUID stranger = UUID.randomUUID();
        LostTalesMapMarkerRecord privateRecord = playerRecord(owner);

        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(
                privateRecord, owner, false));
        assertFalse(LostTalesMapMarkerVisibilityPolicy.canView(
                privateRecord, guest, false));
        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(
                privateRecord, stranger, true));

        LostTalesMapMarkerRecord shared =
                privateRecord.withSharedPlayers(
                        Collections.singleton(guest),
                        LostTalesMapMarkerVisibility.SHARED);
        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(
                shared, guest, false));
        assertFalse(LostTalesMapMarkerVisibilityPolicy.canView(
                shared, stranger, false));

        LostTalesMapMarkerRecord publicRecord =
                shared.withSettings(
                        shared.getName(), shared.getColorName(),
                        shared.hasFastTravel(),
                        shared.getDiscoveryRadius(),
                        LostTalesMapMarkerVisibility.PUBLIC);
        assertTrue(LostTalesMapMarkerVisibilityPolicy.canView(
                publicRecord, stranger, false));
    }

    private static LostTalesMapMarkerRecord playerRecord(UUID owner) {
        return LostTalesMapMarkerRecord.createPlayerMarker(
                "losttales:player/test", "Test Waystone",
                owner, 0, 1, 64, 2, UUID.randomUUID());
    }
}
