package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class LostTalesDiscoverySoundResourcesTest {
    private static final String DISCOVERY_SOUND =
            "map_marker/map_marker_discovered";

    @Test
    public void discoveryEventUsesTheDedicatedDiscoverySound() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/sounds.json");
        assertNotNull(input);
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        try {
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            JsonObject event = root.getAsJsonObject("map_marker.discovery");
            assertNotNull(event);
            assertEquals("player", event.get("category").getAsString());

            JsonArray sounds = event.getAsJsonArray("sounds");
            assertEquals(1, sounds.size());
            assertEquals(DISCOVERY_SOUND, sounds.get(0).getAsString());
        } finally {
            reader.close();
        }
    }

    @Test
    public void discoverySoundIsBundledAsOggVorbis() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/sounds/" + DISCOVERY_SOUND + ".ogg");
        assertNotNull("Missing discovery sound: " + DISCOVERY_SOUND, input);
        try {
            assertEquals('O', input.read());
            assertEquals('g', input.read());
            assertEquals('g', input.read());
            assertEquals('S', input.read());
        } finally {
            input.close();
        }
    }
}
