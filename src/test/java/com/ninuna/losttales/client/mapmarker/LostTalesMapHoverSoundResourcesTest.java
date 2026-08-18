package com.ninuna.losttales.client.mapmarker;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public final class LostTalesMapHoverSoundResourcesTest {
    private static final String HOVER_SOUND =
            "map_marker/marker_hover";

    @Test
    public void hoverEventUsesTheDedicatedUiSound() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/sounds.json");
        assertNotNull(input);
        Reader reader = new InputStreamReader(input, StandardCharsets.UTF_8);
        try {
            JsonObject root = new JsonParser().parse(reader)
                    .getAsJsonObject();
            JsonObject event = root.getAsJsonObject("map_marker.hover");
            assertNotNull(event);
            assertEquals("master", event.get("category").getAsString());
            JsonArray sounds = event.getAsJsonArray("sounds");
            assertEquals(1, sounds.size());
            assertEquals(HOVER_SOUND, sounds.get(0).getAsString());
        } finally {
            reader.close();
        }
    }

    @Test
    public void hoverSoundIsBundledAsOggVorbis() throws Exception {
        InputStream input = getClass().getResourceAsStream(
                "/assets/losttales/sounds/" + HOVER_SOUND + ".ogg");
        assertNotNull("Missing map hover sound", input);
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
