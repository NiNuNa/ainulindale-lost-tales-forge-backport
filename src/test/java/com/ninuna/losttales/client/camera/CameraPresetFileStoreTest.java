package com.ninuna.losttales.client.camera;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class CameraPresetFileStoreTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void initializeCreatesEditableDefaultsInConfigFolder()
            throws Exception {
        File configDirectory = temporaryFolder.newFolder("config");

        CameraPresetFileStore.initialize(configDirectory);

        File presetDirectory = new File(
                configDirectory, "losttales/camera_presets");
        assertEquals(presetDirectory.getCanonicalFile(),
                CameraPresetFileStore.getPresetDirectory()
                        .getCanonicalFile());
        assertTrue(new File(presetDirectory,
                "modern_action_rpg.json").isFile());
        assertFalse(new File(presetDirectory,
                "wide_exploration.json").exists());
        assertFalse(new File(presetDirectory,
                "vanilla_plus.json").exists());
        assertArrayEquals(new String[] {
                        "modern_action_rpg"
                }, CameraPresetFileStore.getConfigValues());
    }

    @Test
    public void reloadDiscoversPlayerUploadedJsonWithoutAnIndex()
            throws Exception {
        File configDirectory = temporaryFolder.newFolder("custom-config");
        CameraPresetFileStore.initialize(configDirectory);
        File presetDirectory = CameraPresetFileStore.getPresetDirectory();
        String customJson = readBundled("modern_action_rpg.json")
                .replace("\"id\": \"modern_action_rpg\"",
                        "\"id\": \"my_camera\"")
                .replace("\"name\": \"Modern Action RPG\"",
                        "\"name\": \"My Camera\"");
        Files.write(new File(presetDirectory, "my_camera.json").toPath(),
                customJson.getBytes(StandardCharsets.UTF_8));

        CameraPresetFileStore.reload();

        CameraPresetDefinition definition =
                CameraPresetFileStore.getDefinition("my_camera");
        assertNotNull(definition);
        assertEquals("My Camera", definition.getName());
        assertEquals(2.65D, definition.getPreset()
                .get(CameraProfileId.STANDING).getDistance(), 0.000001D);
    }

    @Test
    public void initializeNeverOverwritesPlayerEditsToBundledPreset()
            throws Exception {
        File configDirectory = temporaryFolder.newFolder("edited-config");
        CameraPresetFileStore.initialize(configDirectory);
        File modernFile = new File(
                CameraPresetFileStore.getPresetDirectory(),
                "modern_action_rpg.json");
        String editedJson = new String(
                Files.readAllBytes(modernFile.toPath()),
                StandardCharsets.UTF_8).replaceFirst(
                        "\"distance\": 2\\.65",
                        "\"distance\": 4.75");
        Files.write(modernFile.toPath(),
                editedJson.getBytes(StandardCharsets.UTF_8));

        CameraPresetFileStore.initialize(configDirectory);

        assertEquals(4.75D, CameraPresetFileStore
                .getPreset("modern_action_rpg")
                .get(CameraProfileId.STANDING).getDistance(),
                0.000001D);
        assertTrue(new String(
                Files.readAllBytes(modernFile.toPath()),
                StandardCharsets.UTF_8).contains(
                        "\"distance\": 4.75"));
    }

    @Test
    public void retiredBundledIdsAreIgnoredWithoutDeletingFiles()
            throws Exception {
        File configDirectory = temporaryFolder.newFolder("retired-config");
        CameraPresetFileStore.initialize(configDirectory);
        File retired = new File(CameraPresetFileStore.getPresetDirectory(),
                "wide_exploration.json");
        String json = readBundled("modern_action_rpg.json")
                .replace("\"id\": \"modern_action_rpg\"",
                        "\"id\": \"wide_exploration\"")
                .replace("\"name\": \"Modern Action RPG\"",
                        "\"name\": \"Wide Exploration\"");
        Files.write(retired.toPath(),
                json.getBytes(StandardCharsets.UTF_8));

        CameraPresetFileStore.reload();

        assertTrue(retired.isFile());
        assertNull(CameraPresetFileStore.getDefinition(
                "wide_exploration"));
        assertArrayEquals(new String[] {"modern_action_rpg"},
                CameraPresetFileStore.getConfigValues());
    }

    private static String readBundled(String fileName) throws IOException {
        InputStream input = CameraPresetFileStoreTest.class
                .getResourceAsStream(
                        "/assets/losttales/camera_presets/" + fileName);
        if (input == null) {
            throw new IOException("Missing bundled preset " + fileName);
        }
        try {
            byte[] buffer = new byte[4096];
            StringBuilder result = new StringBuilder();
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    result.append(new String(
                            buffer, 0, read, StandardCharsets.UTF_8));
                }
            }
            return result.toString();
        } finally {
            input.close();
        }
    }
}
