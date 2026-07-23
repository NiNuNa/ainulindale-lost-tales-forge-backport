package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import java.util.Arrays;
import lotr.common.LOTRDimension;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class LostTalesClientMapMarkerLookupTest {

    @Test
    public void indexedWaypointLookupSupportsCodePositionAndName() {
        LostTalesMapMarkerDefinition nativeMarker = marker(
                "losttales:native", "Native", "HOBBITON",
                704.0D, -320.0D);
        LostTalesMapMarkerDefinition generatedMarker = marker(
                "losttales:generated", "Test Place", "",
                123.0D, 456.0D);
        LostTalesClientMapMarkerStore.setServerMarkers(
                Arrays.asList(nativeMarker, generatedMarker));

        assertEquals(nativeMarker.getId(),
                lookup("hobbiton", "", 0, 0).getId());
        assertEquals(generatedMarker.getId(),
                lookup("", "", 123, 456).getId());
        assertEquals(generatedMarker.getId(),
                lookup("test_place", "", 0, 0).getId());
    }

    private static LostTalesMapMarkerData lookup(
            String code, String display, int x, int z) {
        LostTalesMapMarkerData marker =
                LostTalesClientMapMarkerStore
                        .findMappedFastTravelMarker(
                                code, display, x, z);
        assertNotNull(marker);
        return marker;
    }

    private static LostTalesMapMarkerDefinition marker(
            String id, String name, String code,
            double x, double z) {
        return new LostTalesMapMarkerDefinition(
                id, name, "fort", "white",
                "City", "", true, code,
                LOTRDimension.MIDDLE_EARTH.dimensionID,
                x, LostTalesMapMarkerDefinition.AUTOMATIC_Y, z,
                128.0D, 8.0D,
                false, false, false);
    }
}
