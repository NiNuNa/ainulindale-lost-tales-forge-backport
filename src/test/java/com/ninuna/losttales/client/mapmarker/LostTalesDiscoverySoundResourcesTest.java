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
    private static final String[] DISCOVERY_SOUNDS = {
            "map_marker/waystone_discovery_chime_1",
            "map_marker/waystone_discovery_chime_2"
    };

    @Test
    public void discoveryEventRandomizesBetweenTwoDedicatedSounds()
            throws Exception {
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
            assertEquals(DISCOVERY_SOUNDS.length, sounds.size());
            for (int i = 0; i < DISCOVERY_SOUNDS.length; i++) {
                assertEquals(DISCOVERY_SOUNDS[i],
                        sounds.get(i).getAsString());
            }
        } finally {
            reader.close();
        }
    }

    @Test
    public void discoverySoundsAreBundledAsOggVorbis() throws Exception {
        for (String sound : DISCOVERY_SOUNDS) {
            InputStream input = getClass().getResourceAsStream(
                    "/assets/losttales/sounds/" + sound + ".ogg");
            assertNotNull("Missing discovery sound: " + sound, input);
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
}
