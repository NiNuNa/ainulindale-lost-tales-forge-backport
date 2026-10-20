package com.ninuna.losttales.mapmarker;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class LostTalesMapMarkerEditableSettingsTest {

    /** The page's settings change; the waystone's place, structure and link are the record's own. */
    @Test
    public void everyPageSettingChangesAndThePlaceAndLinkStay() {
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord original =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/editor", "Aldric",
                        UUID.randomUUID(), 100,
                        4, 70, 8, token);
        LostTalesMapMarkerEditableSettings settings =
                new LostTalesMapMarkerEditableSettings(
                        "New", "tavern", "orange", "City",
                        "Updated description", true,
                        320.0D, 18.0D,
                        true, true, false,
                        LostTalesMapMarkerRelevance.VERY_HIGH.getRank(),
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
        assertEquals(100, updated.getDimensionId());
        assertEquals(4.0D, updated.getX(), 0.0D);
        assertEquals(70.0D, updated.getY(), 0.0D);
        assertEquals(8.0D, updated.getZ(), 0.0D);
        assertEquals(320.0D,
                updated.getCompassFadeInRadius(), 0.0D);
        assertTrue(updated.isHiddenUntilDiscovered());
        assertTrue(updated.hasWaystone());
        assertEquals("losttales:player_placed",
                updated.getWaystoneStructureType());
        assertEquals(LostTalesMapMarkerRelevance.VERY_HIGH.getRank(),
                updated.getPriority());
        assertEquals(original.getRevision() + 1L,
                updated.getRevision());
    }

    /** A placed waystone's icon is the shack, one of the icons its page offers. */
    @Test
    public void aPlacedWaystoneWearsTheShack() {
        assertEquals("shack", LostTalesMapMarkerRecord.createPlayerMarker(
                "losttales:player/icon", "Aldric", UUID.randomUUID(), 100,
                4, 70, 8, UUID.randomUUID()).getIconName());
    }

    /** The link check every use of the block passes: the world, the block and the token. */
    @Test
    public void aRecordIsLinkedOnlyToItsBlockByItsToken() {
        UUID token = UUID.randomUUID();
        LostTalesMapMarkerRecord record =
                LostTalesMapMarkerRecord.createPlayerMarker(
                        "losttales:player/link", "Aldric", UUID.randomUUID(),
                        100, 4, 70, 8, token);
        assertTrue(record.isLinkedTo(100, 4, 70, 8, token));
        assertEquals(false, record.isLinkedTo(100, 4, 70, 8,
                UUID.randomUUID()));
        assertEquals(false, record.isLinkedTo(0, 4, 70, 8, token));
        assertEquals(false, record.isLinkedTo(100, 5, 70, 8, token));
        assertEquals(false, record.isLinkedTo(100, 4, 70, 8, null));
    }
}
