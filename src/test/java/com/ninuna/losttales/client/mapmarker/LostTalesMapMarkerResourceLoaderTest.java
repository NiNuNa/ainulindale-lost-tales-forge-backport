package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.data.IMetadataSection;
import net.minecraft.util.ResourceLocation;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A client reads the marker files through its resource packs with the
 * server's own reader and checks, so it keeps exactly the markers a server
 * keeps of them (MOD-17).
 */
public final class LostTalesMapMarkerResourceLoaderTest {

    /** A pack's file with anything wrong in it is left out whole; the other files load. */
    @Test
    public void aPackFileWithAWarningIsLeftOutWhole() {
        String towns = "{\"map_markers\": ["
                + marker("losttales:greenway_market", "red")
                + ", " + marker("losttales:greenway_market", "blue")
                + "]}";

        Set<String> client = new TreeSet<String>();
        for (LostTalesMapMarkerData marker : LostTalesMapMarkerResourceLoader
                .loadSharedMarkers(new Pack(Collections.singletonMap(
                        "map_markers/towns.json", towns)))) {
            client.add(marker.getId());
        }

        assertFalse(client.contains("losttales:greenway_market"));
        assertFalse(client.contains("losttales:south_road_hamlet"));
        assertTrue("the other files load", client.contains("losttales:mossy_cave"));
        assertTrue(client.contains("lotr:waypoint:hobbiton"));
    }

    @Test
    public void theBundledFilesReadAsTheServerReadsThem() {
        LostTalesMapMarkerCatalog.reloadFromClasspath();
        Set<String> client = new TreeSet<String>();
        for (LostTalesMapMarkerData marker : LostTalesMapMarkerResourceLoader
                .loadSharedMarkers(new Pack(Collections.<String, String>emptyMap()))) {
            client.add(marker.getId());
        }

        Set<String> bundled = new TreeSet<String>();
        for (com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition marker
                : LostTalesMapMarkerCatalog.getBundledMarkers()) {
            bundled.add(marker.getId());
        }
        assertEquals(bundled, client);
        assertEquals(289, client.size());
    }

    private static String marker(String id, String color) {
        return "{\"id\": \"" + id + "\", \"relevance\": \"medium\", "
                + "\"icon\": \"shack\", \"color\": \"" + color + "\", "
                + "\"dimension\": \"lotr:middle_earth\", \"x\": 1.0, \"z\": 2.0, "
                + "\"compassFadeInRadius\": 128.0, \"discoveryRadius\": 8.0, "
                + "\"isDiscoverable\": true, \"hiddenUntilDiscovered\": false, "
                + "\"requiresRegionUnlock\": true, \"hasFastTravel\": true, "
                + "\"hasWaystone\": true, "
                + "\"waystoneStructureType\": \"losttales:glowstone_house\"}";
    }

    /** The files given by their paths, and the mod's own off the classpath for every other. */
    private static final class Pack implements IResourceManager {
        private final Map<String, String> files;

        Pack(Map<String, String> files) {
            this.files = files;
        }

        @Override
        public Set getResourceDomains() {
            return Collections.singleton("losttales");
        }

        @Override
        public IResource getResource(ResourceLocation location) throws IOException {
            final InputStream stream;
            String text = this.files.get(location.getResourcePath());
            if (text == null) {
                stream = LostTalesMapMarkerResourceLoaderTest.class.getResourceAsStream(
                        "/assets/" + location.getResourceDomain() + "/"
                                + location.getResourcePath());
            } else {
                stream = new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
            }
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
            List<IResource> resources = new ArrayList<IResource>();
            resources.add(getResource(location));
            return resources;
        }
    }
}
