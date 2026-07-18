package com.ninuna.losttales.client.accessory;

import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class WraithWorldVisualEffectTest {

    @Test
    public void transitionApproachesTargetWithoutOvershooting() {
        assertEquals(0.25F,
                WraithWorldVisualEffect.approach(0.0F, 1.0F, 0.25F),
                0.0001F);
        assertEquals(1.0F,
                WraithWorldVisualEffect.approach(0.95F, 1.0F, 0.25F),
                0.0001F);
        assertEquals(0.0F,
                WraithWorldVisualEffect.approach(0.05F, 0.0F, 0.25F),
                0.0001F);
        assertEquals(0.50F,
                WraithWorldVisualEffect.approach(0.50F, 1.0F, -1.0F),
                0.0001F);
    }

    @Test
    public void shaderUsesWraithWorldAssetsAndTemporalVeils()
            throws IOException {
        String fragment = readResource(
                "/assets/losttales/shaders/wraith_world.frag");
        assertTrue(fragment.contains("uniform sampler2D previousScene"));
        assertTrue(fragment.contains("float broadVeil"));
        assertTrue(fragment.contains("float temporalMask"));
        assertTrue(fragment.contains("float edgeMist"));
        assertTrue(fragment.contains("float wispyVeil"));
        assertTrue(fragment.contains("vec3 secondEcho"));
        assertTrue(fragment.contains("float smokeSheet"));
        assertNull(WraithWorldVisualEffectTest.class.getResourceAsStream(
                "/assets/losttales/shaders/ring_world.frag"));
    }

    private static String readResource(String path) throws IOException {
        InputStream input = WraithWorldVisualEffectTest.class
                .getResourceAsStream(path);
        if (input == null) {
            throw new IOException("Missing shader resource " + path);
        }
        BufferedReader reader = new BufferedReader(new InputStreamReader(
                input, StandardCharsets.UTF_8));
        try {
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line).append('\n');
            }
            return result.toString();
        } finally {
            reader.close();
        }
    }
}
