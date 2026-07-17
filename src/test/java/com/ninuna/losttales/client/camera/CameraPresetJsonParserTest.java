package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public final class CameraPresetJsonParserTest {
    private static final double EPSILON = 0.0000001D;
    private static final String RESOURCE_ROOT =
            "assets/losttales/camera_presets/";

    @Test
    public void bundledJsonExactlyMatchesCompiledFallbacks()
            throws Exception {
        assertBundledPresetMatches(
                "modern_action_rpg.json",
                CameraPresetId.MODERN_ACTION_RPG,
                "Modern Action RPG");
    }

    @Test
    public void invalidPresetReportsUnknownFields() throws Exception {
        String json = readResource("modern_action_rpg.json")
                .replace("\"name\": \"Modern Action RPG\",",
                        "\"name\": \"Modern Action RPG\",\n"
                                + "  \"unexpected\": true,");

        CameraPresetJsonParser.ParseResult result =
                CameraPresetJsonParser.parseDefinition(
                        new StringReader(json), "unknown-field.json");

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains("unknown field"));
    }

    @Test
    public void invalidPresetReportsOutOfBoundsValues() throws Exception {
        String json = readResource("modern_action_rpg.json")
                .replaceFirst("\"distance\": 2\\.65",
                        "\"distance\": 120.0");

        CameraPresetJsonParser.ParseResult result =
                CameraPresetJsonParser.parseDefinition(
                        new StringReader(json), "out-of-bounds.json");

        assertFalse(result.isValid());
        assertTrue(result.getErrors().get(0).contains(
                "profiles.standing.distance"));
    }

    @Test
    public void oldCustomPresetWithoutMotionRemainsCompatible()
            throws Exception {
        String json = readResource("modern_action_rpg.json")
                .replace("\"id\": \"modern_action_rpg\"",
                        "\"id\": \"legacy_custom\"")
                .replaceAll(
                        ",?\\s*\"verticalPositionRate\"\\s*:\\s*[0-9.]+",
                        "")
                .replaceAll(
                        ",\\s*\"motion\"\\s*:\\s*\\{[^}]+\\}",
                        "");

        CameraPresetJsonParser.ParseResult result =
                CameraPresetJsonParser.parseDefinition(
                        new StringReader(json), "legacy-custom.json");

        assertTrue(result.getErrors().toString(), result.isValid());
        CameraProfile standing = result.getDefinition()
                .getPreset().get(CameraProfileId.STANDING);
        assertEquals(standing.getSmoothing().getPositionRate(),
                standing.getSmoothing().getVerticalPositionRate(), EPSILON);
        assertEquals(0.0D,
                standing.getMotion().getHorizontalFollowLimit(), EPSILON);
        assertEquals(0.0D,
                standing.getMotion().getSideSway(), EPSILON);
    }

    @Test
    public void oldBuiltInPresetInheritsNewMotionWithoutOverwritingFraming()
            throws Exception {
        String json = readResource("modern_action_rpg.json")
                .replaceFirst("\"distance\": 2\\.65",
                        "\"distance\": 3.55")
                .replaceAll(
                        ",?\\s*\"verticalPositionRate\"\\s*:\\s*[0-9.]+",
                        "")
                .replaceAll(
                        ",\\s*\"motion\"\\s*:\\s*\\{[^}]+\\}",
                        "");

        CameraPresetJsonParser.ParseResult result =
                CameraPresetJsonParser.parseDefinition(
                        new StringReader(json), "old-modern.json");

        assertTrue(result.getErrors().toString(), result.isValid());
        CameraProfile standing = result.getDefinition()
                .getPreset().get(CameraProfileId.STANDING);
        assertEquals(3.55D, standing.getDistance(), EPSILON);
        assertEquals(6.5D,
                standing.getSmoothing().getVerticalPositionRate(), EPSILON);
        assertEquals(0.35D,
                standing.getMotion().getHorizontalFollowLimit(), EPSILON);
        assertEquals(0.085D,
                standing.getMotion().getTurnSway(), EPSILON);
        assertEquals(0.045D,
                standing.getMotion().getLookPitchSway(), EPSILON);
        assertEquals(0.055D,
                standing.getMotion().getLookForwardSway(), EPSILON);
        assertEquals(0.035D,
                standing.getMotion().getIdleSideSway(), EPSILON);
    }

    @Test
    public void copiedModernPresetWithoutIdleFieldsInheritsAmbientMotion()
            throws Exception {
        String json = readResource("modern_action_rpg.json")
                .replaceAll(
                        ",\\s*\"idle(?:Side|Vertical|Forward)Sway\"\\s*:\\s*[0-9.]+",
                        "")
                .replaceAll(
                        ",\\s*\"idleCyclesPerSecond\"\\s*:\\s*[0-9.]+",
                        "");

        CameraPresetJsonParser.ParseResult result =
                CameraPresetJsonParser.parseDefinition(
                        new StringReader(json), "copied-modern.json");

        assertTrue(result.getErrors().toString(), result.isValid());
        CameraMotionProfile standing = result.getDefinition().getPreset()
                .get(CameraProfileId.STANDING).getMotion();
        assertEquals(0.035D, standing.getIdleSideSway(), EPSILON);
        assertEquals(0.022D, standing.getIdleVerticalSway(), EPSILON);
        assertEquals(0.014D, standing.getIdleForwardSway(), EPSILON);
        assertEquals(0.110D,
                standing.getIdleCyclesPerSecond(), EPSILON);
    }

    @Test
    public void oldModernPresetInheritsNewLookMotionFields()
            throws Exception {
        String json = readResource("modern_action_rpg.json")
                .replaceAll(
                        ",\\s*\"look(?:Pitch|Forward)Sway\"\\s*:\\s*[0-9.]+",
                        "")
                .replaceAll(
                        ",\\s*\"look(?:ResponseRate|ReferenceSpeed)\"\\s*:\\s*[0-9.]+",
                        "");

        CameraPresetJsonParser.ParseResult result =
                CameraPresetJsonParser.parseDefinition(
                        new StringReader(json), "old-modern-look.json");

        assertTrue(result.getErrors().toString(), result.isValid());
        CameraMotionProfile standing = result.getDefinition().getPreset()
                .get(CameraProfileId.STANDING).getMotion();
        assertEquals(0.045D, standing.getLookPitchSway(), EPSILON);
        assertEquals(0.055D, standing.getLookForwardSway(), EPSILON);
        assertEquals(8.0D, standing.getLookResponseRate(), EPSILON);
        assertEquals(220.0D,
                standing.getLookReferenceSpeed(), EPSILON);
    }

    @Test
    public void customIdsAreStableAndCaseInsensitive() {
        assertEquals("community_camera",
                CameraPresetFileStore.normalizeId(
                        " Community_Camera "));
        assertEquals("modern_action_rpg",
                CameraPresetFileStore.normalizeId(
                        "invalid/path"));
    }

    private static void assertBundledPresetMatches(
            String fileName, CameraPresetId expectedId,
            String expectedName) throws Exception {
        Reader reader = resource(fileName);
        CameraPresetJsonParser.ParseResult result;
        try {
            result = CameraPresetJsonParser.parseDefinition(
                    reader, fileName);
        } finally {
            reader.close();
        }
        assertTrue(result.getErrors().toString(), result.isValid());
        CameraPresetDefinition definition = result.getDefinition();
        assertNotNull(definition);
        assertEquals(expectedId.getConfigValue(), definition.getId());
        assertEquals(expectedName, definition.getName());

        CameraPreset expected = CameraPreset.forId(expectedId);
        CameraPreset actual = definition.getPreset();
        for (CameraProfileId profileId : CameraProfileId.values()) {
            assertProfileEquals(
                    expected.get(profileId), actual.get(profileId));
        }
    }

    private static void assertProfileEquals(
            CameraProfile expected, CameraProfile actual) {
        assertNotNull(actual);
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getDistance(), actual.getDistance(), EPSILON);
        assertEquals(expected.getShoulderOffset(),
                actual.getShoulderOffset(), EPSILON);
        assertEquals(expected.getVerticalOffset(),
                actual.getVerticalOffset(), EPSILON);
        assertEquals(expected.getFovOffset(),
                actual.getFovOffset(), EPSILON);

        CameraSmoothing expectedSmoothing = expected.getSmoothing();
        CameraSmoothing actualSmoothing = actual.getSmoothing();
        assertEquals(expectedSmoothing.getPositionRate(),
                actualSmoothing.getPositionRate(), EPSILON);
        assertEquals(expectedSmoothing.getVerticalPositionRate(),
                actualSmoothing.getVerticalPositionRate(), EPSILON);
        assertEquals(expectedSmoothing.getRotationRate(),
                actualSmoothing.getRotationRate(), EPSILON);
        assertEquals(expectedSmoothing.getZoomRate(),
                actualSmoothing.getZoomRate(), EPSILON);
        assertEquals(expectedSmoothing.getShoulderRate(),
                actualSmoothing.getShoulderRate(), EPSILON);
        assertEquals(expectedSmoothing.getVerticalRate(),
                actualSmoothing.getVerticalRate(), EPSILON);
        assertEquals(expectedSmoothing.getFovRate(),
                actualSmoothing.getFovRate(), EPSILON);

        CameraMotionProfile expectedMotion = expected.getMotion();
        CameraMotionProfile actualMotion = actual.getMotion();
        assertEquals(expectedMotion.getHorizontalFollowLimit(),
                actualMotion.getHorizontalFollowLimit(), EPSILON);
        assertEquals(expectedMotion.getVerticalFollowLimit(),
                actualMotion.getVerticalFollowLimit(), EPSILON);
        assertEquals(expectedMotion.getSideSway(),
                actualMotion.getSideSway(), EPSILON);
        assertEquals(expectedMotion.getVerticalSway(),
                actualMotion.getVerticalSway(), EPSILON);
        assertEquals(expectedMotion.getForwardSway(),
                actualMotion.getForwardSway(), EPSILON);
        assertEquals(expectedMotion.getTurnSway(),
                actualMotion.getTurnSway(), EPSILON);
        assertEquals(expectedMotion.getLookPitchSway(),
                actualMotion.getLookPitchSway(), EPSILON);
        assertEquals(expectedMotion.getLookForwardSway(),
                actualMotion.getLookForwardSway(), EPSILON);
        assertEquals(expectedMotion.getLookResponseRate(),
                actualMotion.getLookResponseRate(), EPSILON);
        assertEquals(expectedMotion.getLookReferenceSpeed(),
                actualMotion.getLookReferenceSpeed(), EPSILON);
        assertEquals(expectedMotion.getSwayCyclesPerBlock(),
                actualMotion.getSwayCyclesPerBlock(), EPSILON);
        assertEquals(expectedMotion.getResponseRate(),
                actualMotion.getResponseRate(), EPSILON);
        assertEquals(expectedMotion.getIdleSideSway(),
                actualMotion.getIdleSideSway(), EPSILON);
        assertEquals(expectedMotion.getIdleVerticalSway(),
                actualMotion.getIdleVerticalSway(), EPSILON);
        assertEquals(expectedMotion.getIdleForwardSway(),
                actualMotion.getIdleForwardSway(), EPSILON);
        assertEquals(expectedMotion.getIdleCyclesPerSecond(),
                actualMotion.getIdleCyclesPerSecond(), EPSILON);
    }

    private static String readResource(String fileName) throws IOException {
        Reader reader = resource(fileName);
        try {
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (read > 0) {
                    value.append(buffer, 0, read);
                }
            }
            return value.toString();
        } finally {
            reader.close();
        }
    }

    private static Reader resource(String fileName) throws IOException {
        InputStream stream = CameraPresetJsonParserTest.class
                .getClassLoader().getResourceAsStream(
                        RESOURCE_ROOT + fileName);
        if (stream == null) {
            throw new IOException("Missing test resource " + fileName);
        }
        return new InputStreamReader(stream, StandardCharsets.UTF_8);
    }
}
