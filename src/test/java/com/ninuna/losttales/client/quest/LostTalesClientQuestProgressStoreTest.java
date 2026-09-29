package com.ninuna.losttales.client.quest;

import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LostTalesClientQuestProgressStoreTest {
    @After
    public void clearStore() {
        LostTalesClientQuestProgressStore.clear();
    }

    @Test
    public void syncedDiscoveryAndPinUseLogicalLotrIdentity() {
        LostTalesClientQuestProgressStore.update(
                Collections.emptyList(), Collections.emptySet(),
                Collections.emptySet(),
                Arrays.asList(
                        "lotr:waypoint:HOBBITON",
                        "LOTR:WAYPOINT:hobbiton"),
                "LOTR:WAYPOINT:hobbiton");

        assertTrue(LostTalesClientQuestProgressStore
                .isMarkerDiscovered("lotr:waypoint:hobbiton"));
        assertTrue(LostTalesClientQuestProgressStore
                .isMapMarkerPinned("LOTR:WAYPOINT:hobbiton"));
        assertTrue(LostTalesClientQuestProgressStore
                .isMapMarkerPinned("lotr:waypoint:HOBBITON"));
    }

    @Test
    public void customMarkerIdsRemainCaseSensitive() {
        LostTalesClientQuestProgressStore.update(
                Collections.emptyList(), Collections.emptySet(),
                Collections.emptySet(),
                Arrays.asList("losttales:Town", "losttales:town"),
                "losttales:Town");

        assertTrue(LostTalesClientQuestProgressStore
                .isMarkerDiscovered("losttales:Town"));
        assertTrue(LostTalesClientQuestProgressStore
                .isMarkerDiscovered("losttales:town"));
        assertTrue(LostTalesClientQuestProgressStore
                .isMapMarkerPinned("losttales:Town"));
        assertFalse(LostTalesClientQuestProgressStore
                .isMapMarkerPinned("losttales:town"));
    }

    @Test
    public void activeQuestLabelLookupUsesLogicalLotrIdentity() {
        assertEquals("Tracked Quest",
                LostTalesClientQuestMarkerHelper
                        .getActiveQuestMarkerLabel(
                                Collections.singletonMap(
                                        "lotr:waypoint:hobbiton",
                                        "Tracked Quest"),
                                "LOTR:WAYPOINT:HOBBITON"));
    }
}
