package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lotr.common.world.map.LOTRCustomWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import org.junit.Test;

public final class LostTalesMapLegendRegistryTest {
    @Test
    public void registryContainsOnlySupportedMapFilterCategories() {
        Set<String> ids = new HashSet<String>();
        for (LostTalesMapLegendCategory category
                : LostTalesMapLegendRegistry.getCategories()) {
            ids.add(category.getId());
        }

        assertEquals(7, ids.size());
        assertTrue(ids.contains(LostTalesMapLegendRegistry.LOCATIONS));
        assertTrue(ids.contains(
                LostTalesMapLegendRegistry.PERSONAL_WAYPOINTS));
        assertTrue(ids.contains(
                LostTalesMapLegendRegistry.SHARED_WAYPOINTS));
        assertTrue(ids.contains(
                LostTalesMapLegendRegistry.PLAYER_WAYSTONES));
        assertTrue(ids.contains(LostTalesMapLegendRegistry.QUESTS));
        assertTrue(ids.contains(LostTalesMapLegendRegistry.PARTY));
        assertTrue(ids.contains(LostTalesMapLegendRegistry.LABELS));
        for (String id : ids) {
            assertFalse(id.contains("hostile"));
            assertFalse(id.contains("enemy"));
            assertFalse(id.contains("cardinal"));
            assertFalse(id.contains("direction"));
        }
    }

    @Test
    public void markerClassificationUsesStableSourceWithPartyPrecedence() {
        assertEquals(LostTalesMapLegendRegistry.LOCATIONS,
                LostTalesMapLegendRegistry.categoryFor(marker(
                        "location", "Point of Interest",
                        LostTalesMapMarkerSource.LOTR_ADAPTER,
                        true, true)));
        assertEquals(LostTalesMapLegendRegistry.PLAYER_WAYSTONES,
                LostTalesMapLegendRegistry.categoryFor(marker(
                        "waystone", "Point of Interest",
                        LostTalesMapMarkerSource.PLAYER_CREATED,
                        false, false)));
        assertEquals(LostTalesMapLegendRegistry.QUESTS,
                LostTalesMapLegendRegistry.categoryFor(marker(
                        "quest", "Quest",
                        LostTalesMapMarkerSource.QUEST_DYNAMIC,
                        false, false)));
        assertEquals(LostTalesMapLegendRegistry.PARTY,
                LostTalesMapLegendRegistry.categoryFor(marker(
                        "party_go_here:character", "Go Here",
                        LostTalesMapMarkerSource.PLAYER_CREATED,
                        false, false)));
    }

    @Test
    public void classificationDoesNotAlterDiscoveryRules() {
        LostTalesMapMarkerData undiscovered = marker(
                "undiscovered", "Point of Interest",
                LostTalesMapMarkerSource.CUSTOM_PRESET,
                true, true);

        assertTrue(undiscovered.isDiscoverable());
        assertTrue(undiscovered.isHiddenUntilDiscovered());
        assertEquals(LostTalesMapLegendRegistry.LOCATIONS,
                LostTalesMapLegendRegistry.categoryFor(undiscovered));
    }

    @Test
    public void lotrPersonalSharedAndPublicWaypointsStayDistinct() {
        LOTRCustomWaypoint personal = new LOTRCustomWaypoint(
                "Personal", 0.0D, 0.0D, 0, 0, 0, 1);
        LOTRCustomWaypoint shared = new LOTRCustomWaypoint(
                "Shared", 0.0D, 0.0D, 0, 0, 0, 2);
        shared.setSharingPlayerID(UUID.randomUUID());

        assertEquals(LostTalesMapLegendRegistry.PERSONAL_WAYPOINTS,
                LostTalesMapLegendRegistry.categoryFor(personal));
        assertEquals(LostTalesMapLegendRegistry.SHARED_WAYPOINTS,
                LostTalesMapLegendRegistry.categoryFor(shared));
        assertEquals(LostTalesMapLegendRegistry.LOCATIONS,
                LostTalesMapLegendRegistry.categoryFor(LOTRWaypoint.HOBBITON));
    }

    @Test
    public void preferenceTogglesAreClientOnlyAndImmediate() {
        String[] previous = LostTalesConfig.hiddenMapLegendCategories;
        try {
            LostTalesConfig.hiddenMapLegendCategories = new String[0];
            assertTrue(LostTalesMapLegendRegistry.isCategoryEnabled(
                    LostTalesMapLegendRegistry.QUESTS));
            assertFalse(LostTalesMapLegendRegistry.toggleCategory(
                    LostTalesMapLegendRegistry.QUESTS));
            assertFalse(LostTalesMapLegendRegistry.isCategoryEnabled(
                    LostTalesMapLegendRegistry.QUESTS));
            assertTrue(LostTalesMapLegendRegistry.toggleCategory(
                    LostTalesMapLegendRegistry.QUESTS));
            assertTrue(LostTalesMapLegendRegistry.isCategoryEnabled(
                    LostTalesMapLegendRegistry.QUESTS));
        } finally {
            LostTalesConfig.hiddenMapLegendCategories = previous;
        }
    }

    @Test
    public void hidingAMapCategoryDoesNotChangeBaseVisibilityPolicy() {
        String[] previous = LostTalesConfig.hiddenMapLegendCategories;
        try {
            LostTalesConfig.hiddenMapLegendCategories = new String[] {
                    LostTalesMapLegendRegistry.LOCATIONS
            };
            LostTalesMapMarkerData location = marker(
                    "compass-location", "Point of Interest",
                    LostTalesMapMarkerSource.CUSTOM_PRESET,
                    false, false);

            assertTrue(LostTalesClientMapMarkerVisibility
                    .isMapVisible(location));
            assertTrue(LostTalesClientMapMarkerVisibility
                    .isNonDiscoverableVisible(location));
            assertFalse(LostTalesMapLegendRegistry
                    .isMarkerVisible(location));
        } finally {
            LostTalesConfig.hiddenMapLegendCategories = previous;
        }
    }

    @Test
    public void malformedPreferenceIdsAreIgnored() {
        assertEquals("locations",
                LostTalesMapLegendPreferences.normalize(" Locations "));
        assertEquals("",
                LostTalesMapLegendPreferences.normalize("enemy marker!"));
        assertEquals("",
                LostTalesMapLegendPreferences.normalize(null));
    }

    private static LostTalesMapMarkerData marker(
            String id, String category,
            LostTalesMapMarkerSource source,
            boolean hiddenUntilDiscovered, boolean discoverable) {
        return new LostTalesMapMarkerData(
                id, id, "town", "white", category, "",
                false, 100, 0.0D, 64.0D, 0.0D,
                128.0D, 8.0D,
                hiddenUntilDiscovered, discoverable,
                false, false, 0, source);
    }
}
