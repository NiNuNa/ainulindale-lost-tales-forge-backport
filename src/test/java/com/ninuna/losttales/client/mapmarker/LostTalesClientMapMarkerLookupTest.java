package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import lotr.common.LOTRDimension;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class LostTalesClientMapMarkerLookupTest {

    @Test
    public void indexedWaypointLookupSupportsCodePositionAndName() {
        LostTalesMapMarkerDefinition nativeMarker = marker(
                "lotr:waypoint:hobbiton", "Native",
                704.0D, -320.0D);
        LostTalesMapMarkerDefinition generatedMarker = marker(
                "losttales:generated", "Test Place",
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

    @Test
    public void disabledFastTravelMarkerStillOwnsItsNativeMapIcon() {
        LostTalesMapMarkerDefinition marker =
                new LostTalesMapMarkerDefinition(
                        "lotr:waypoint:bree", "Disabled", "fort",
                        "white", "City", "", false,
                        LOTRDimension.MIDDLE_EARTH.dimensionID,
                        1.0D,
                        LostTalesMapMarkerDefinition.AUTOMATIC_Y,
                        2.0D, 128.0D, 8.0D,
                        false, false, false, LostTalesMapMarkerSource.QUEST_DYNAMIC, false, "", 0);
        LostTalesClientMapMarkerStore.setServerMarkers(
                Arrays.asList(marker));

        assertEquals(marker.getId(),
                lookup("BREE", "", 0, 0).getId());
    }

    /**
     * The bundled Town at 15, 15, read from the marker files, still
     * claims its waypoint's icon once the server has deleted the marker.
     */
    @Test
    public void deletedMarkerKeepsOnlyItsDecorativeSuppressionMapping() {
        LostTalesClientMapMarkerStore.reloadFromResources(new BundledFiles(true));
        try {
            LostTalesClientMapMarkerStore.setServerMarkers(
                    Collections
                            .<LostTalesMapMarkerDefinition>emptyList());

            assertNull(LostTalesClientMapMarkerStore
                    .findMappedWaypointMarker(
                            "", "", 15, 15));
            assertTrue(LostTalesClientMapMarkerStore
                    .hasDecorativeWaypointMapping(
                            "", "", 15, 15));
        } finally {
            LostTalesClientMapMarkerStore.reloadFromResources(new BundledFiles(false));
        }
    }

    /** The mod's own files off the test classpath, or none at all. */
    private static final class BundledFiles implements IResourceManager {
        private final boolean present;

        BundledFiles(boolean present) {
            this.present = present;
        }

        @Override
        public Set getResourceDomains() {
            return Collections.singleton("losttales");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            final InputStream stream = this.present
                    ? LostTalesClientMapMarkerLookupTest.class.getResourceAsStream(
                            "/assets/" + location.getResourceDomain() + "/"
                                    + location.getResourcePath())
                    : null;
            if (stream == null) {
                throw new FileNotFoundException(location.toString());
            }
            return new IResource() {
                @Override
                public InputStream getInputStream() {
                    return stream;
                }

                @Override
                public boolean hasMetadata() {
                    return false;
                }

                @Override
                public IMetadataSection getMetadata(String section) {
                    return null;
                }
            };
        }

        @Override
        public List getAllResources(ResourceLocation location) throws IOException {
            return Collections.singletonList(getResource(location));
        }
    }

    @Test
    public void loadingScreenSepiaTintUsesLotrPalette() {
        float[] white = LostTalesLotrMapMarkerIconOverlay
                .toLotrSepiaTint(1.0F, 1.0F, 1.0F);
        float[] red = LostTalesLotrMapMarkerIconOverlay
                .toLotrSepiaTint(1.0F, 0.0F, 0.0F);

        assertEquals(1.0F, white[0], 0.0001F);
        assertEquals(1.0F, white[1], 0.0001F);
        assertEquals(0.76F, white[2], 0.0001F);
        assertEquals(0.79F, red[0], 0.0001F);
        assertEquals(0.52F, red[1], 0.0001F);
        assertEquals(0.35F, red[2], 0.0001F);
    }

    private static LostTalesMapMarkerData lookup(
            String code, String display, int x, int z) {
        LostTalesMapMarkerData marker =
                LostTalesClientMapMarkerStore
                        .findMappedWaypointMarker(
                                code, display, x, z);
        assertNotNull(marker);
        return marker;
    }

    private static LostTalesMapMarkerDefinition marker(
            String id, String name,
            double x, double z) {
        return new LostTalesMapMarkerDefinition(
                id, name, "fort", "white",
                "City", "", true,
                LOTRDimension.MIDDLE_EARTH.dimensionID,
                x, LostTalesMapMarkerDefinition.AUTOMATIC_Y, z,
                128.0D, 8.0D,
                false, false, false, LostTalesMapMarkerSource.QUEST_DYNAMIC, false, "", 0);
    }
}
