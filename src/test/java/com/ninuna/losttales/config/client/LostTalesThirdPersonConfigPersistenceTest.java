package com.ninuna.losttales.config.client;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import cpw.mods.fml.relauncher.FMLInjectionData;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.TreeSet;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Settings sets a camera option's field and saves the camera's file at
 * once; the file then holds the value in the option's shipped definition,
 * and the next load reads it back.
 */
public final class LostTalesThirdPersonConfigPersistenceTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private final boolean enabled = LostTalesThirdPersonConfig.enabled;
    private final double distance = LostTalesThirdPersonConfig.distanceMultiplier;
    private final int samples =
            LostTalesThirdPersonConfig.projectileTrajectorySamplesPerTick;

    @After
    public void putTheFieldsBack() {
        LostTalesThirdPersonConfig.enabled = this.enabled;
        LostTalesThirdPersonConfig.distanceMultiplier = this.distance;
        LostTalesThirdPersonConfig.projectileTrajectorySamplesPerTick =
                this.samples;
    }

    @Test
    public void aSavedOptionIsWrittenAndReadBack() throws Exception {
        File directory = temporaryFolder.newFolder("config");
        initializeForgeHome(directory.getParentFile());
        LostTalesThirdPersonConfig.load(directory);

        LostTalesThirdPersonConfig.enabled = true;
        LostTalesThirdPersonConfig.distanceMultiplier = 1.73D;
        LostTalesThirdPersonConfig.projectileTrajectorySamplesPerTick = 9;
        LostTalesThirdPersonConfig.save();

        Configuration persisted = new Configuration(
                LostTalesConfigFiles.clientFile(directory, LostTalesConfigFiles.CAMERA_OPTIONS));
        persisted.load();
        assertTrue(persisted.getBoolean(
                "enabled",
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                false, ""));
        assertEquals(1.73D, persisted.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "distanceMultiplier", 0.0D).getDouble(), 0.0D);
        assertEquals(9, persisted.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "projectileTrajectorySamplesPerTick", 0).getInt());

        LostTalesThirdPersonConfig.distanceMultiplier = 1.0D;
        LostTalesThirdPersonConfig.projectileTrajectorySamplesPerTick = 6;
        LostTalesThirdPersonConfig.reload();
        assertEquals(1.73D,
                LostTalesThirdPersonConfig.distanceMultiplier, 0.0D);
        assertEquals(9, LostTalesThirdPersonConfig
                .projectileTrajectorySamplesPerTick);
    }

    @Test
    public void legacyHeadTrackingDefaultMigratesToCorrectedLimit()
            throws Exception {
        File directory = temporaryFolder.newFolder("legacy-head-config");
        initializeForgeHome(directory.getParentFile());
        File configFile = LostTalesConfigFiles.clientFile(directory, LostTalesConfigFiles.CAMERA_OPTIONS);
        Configuration legacy = new Configuration(configFile);
        legacy.load();
        legacy.get(LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 35.0D).set(35.0D);
        legacy.save();

        LostTalesThirdPersonConfig.load(directory);

        assertEquals(100.0D,
                LostTalesThirdPersonConfig.headTrackingAngle, 0.0D);
        Configuration migrated = new Configuration(configFile);
        migrated.load();
        assertEquals(100.0D, migrated.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 0.0D).getDouble(0.0D), 0.0D);
    }

    @Test
    public void formerEightyFiveDegreeDefaultMigratesToCurrentLimit()
            throws Exception {
        File directory = temporaryFolder.newFolder("former-head-config");
        initializeForgeHome(directory.getParentFile());
        File configFile = LostTalesConfigFiles.clientFile(directory, LostTalesConfigFiles.CAMERA_OPTIONS);
        Configuration previous = new Configuration(configFile);
        previous.load();
        previous.get(LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 85.0D).set(85.0D);
        previous.save();

        LostTalesThirdPersonConfig.load(directory);

        assertEquals(100.0D,
                LostTalesThirdPersonConfig.headTrackingAngle, 0.0D);
        Configuration migrated = new Configuration(configFile);
        migrated.load();
        assertEquals(100.0D, migrated.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 0.0D).getDouble(0.0D), 0.0D);
    }

    /**
     * A saved option is written in its shipped definition: the default it
     * restores, its bounds and its comment stay the mod's, whatever it is
     * set to, and Settings reads them from the definitions.
     */
    @Test
    public void aSavedOptionKeepsItsShippedDefinition() throws Exception {
        File directory = temporaryFolder.newFolder("defaults-config");
        initializeForgeHome(directory.getParentFile());
        LostTalesThirdPersonConfig.load(directory);
        LostTalesThirdPersonConfig.distanceMultiplier = 1.73D;
        LostTalesThirdPersonConfig.save();

        File file = LostTalesConfigFiles.clientFile(directory,
                LostTalesConfigFiles.CAMERA_OPTIONS);
        Configuration persisted = new Configuration(file);
        persisted.load();
        Property distance = persisted.getCategory(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA).get("distanceMultiplier");
        assertEquals(1.73D, distance.getDouble(), 0.0D);
        // A file read back holds no comments; the one written holds the
        // option's own.
        String written = new String(Files.readAllBytes(file.toPath()),
                StandardCharsets.UTF_8);
        assertTrue(written.contains("camera distance"));
        assertEquals("1.0",
                LostTalesThirdPersonConfig.shippedValue("distanceMultiplier"));
        assertArrayEquals(new double[] {0.5D, 2.0D},
                LostTalesThirdPersonConfig.shippedBounds("distanceMultiplier"),
                0.0D);
    }

    /** Every camera option is written back on a save; one left out would be lost. */
    @Test
    public void everyCameraOptionIsWrittenBack() {
        Configuration written = new Configuration();
        LostTalesThirdPersonConfig.writeCurrentValues(written);
        assertEquals(LostTalesThirdPersonConfig.optionKeys(),
                new TreeSet<String>(written.getCategory(
                        LostTalesThirdPersonConfig.CATEGORY_CAMERA).keySet()));
    }

    private static void initializeForgeHome(File directory) throws Exception {
        Field minecraftHome = FMLInjectionData.class
                .getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, directory);
    }
}
