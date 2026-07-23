package com.ninuna.losttales.mapmarker;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerEditableSettingsTest {

    @Test
    public void allJsonSettingsChangeWithoutBreakingStableLinkIdentity() {
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord original =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/editor", "Old",
                        UUID.randomUUID(), 100,
                        4, 70, 8, token);
        LostTalesMapMarkerEditableSettings settings =
                new LostTalesMapMarkerEditableSettings(
                        "New", "tavern", "orange", "City",
                        "Updated description", true, "bree",
                        100, 512.0D,
                        LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                        -384.0D, 320.0D, 18.0D,
                        true, true, false,
                        true, "losttales:glowstone_house",
                        LostTalesMapMarkerVisibility.SHARED);

        LostTalesMapMarkerRecord updated =
                original.withEditableSettings(settings);

        assertEquals(original.getId(), updated.getId());
        assertEquals(original.getLinkToken(), updated.getLinkToken());
        assertEquals(original.getLinkedX(), updated.getLinkedX());
        assertEquals("tavern", updated.getIconName());
        assertEquals("City", updated.getCategoryName());
        assertEquals("Updated description",
                updated.getDescription());
        assertEquals("bree",
                updated.getFastTravelWaypointCode());
        assertEquals(512.0D, updated.getX(), 0.0D);
        assertEquals(LostTalesMapMarkerHeightResolver.AUTOMATIC_Y,
                updated.getY(), 0.0D);
        assertEquals(320.0D,
                updated.getCompassFadeInRadius(), 0.0D);
        assertTrue(updated.isHiddenUntilDiscovered());
        assertTrue(updated.hasWaystone());
        assertEquals("losttales:glowstone_house",
                updated.getWaystoneStructureType());
        assertEquals(original.getRevision() + 1L,
                updated.getRevision());
    }
}
