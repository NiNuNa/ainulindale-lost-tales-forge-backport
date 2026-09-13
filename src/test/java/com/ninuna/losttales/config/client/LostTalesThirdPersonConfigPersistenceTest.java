package com.ninuna.losttales.config.client;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import cpw.mods.fml.relauncher.FMLInjectionData;
import com.ninuna.losttales.config.LostTalesConfigFiles;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class LostTalesThirdPersonConfigPersistenceTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void guiConfigurationIsSavedBeforeStaticValuesReload()
            throws Exception {
        File directory = temporaryFolder.newFolder("config");
        initializeForgeHome(directory.getParentFile());
        LostTalesThirdPersonConfig.load(directory);

        Configuration guiConfiguration =
                LostTalesThirdPersonConfig.createConfiguration();
        guiConfiguration.load();
        guiConfiguration.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "enabled", false).set(true);
        guiConfiguration.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "distanceMultiplier", 1.0D).set(1.73D);
        guiConfiguration.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "projectileTrajectorySamplesPerTick", 6).set(9);

        LostTalesThirdPersonConfig.savePendingGuiConfiguration();

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

        LostTalesThirdPersonConfig.reload();
        assertEquals(1.73D,
                LostTalesThirdPersonConfig.distanceMultiplier, 0.0D);
        assertEquals(9, LostTalesThirdPersonConfig
                .projectileTrajectorySamplesPerTick);

        guiConfiguration.get(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "enabled", false).set(false);
        LostTalesThirdPersonConfig.savePendingGuiConfiguration();

        Configuration persistedAgain = new Configuration(
                LostTalesConfigFiles.clientFile(directory, LostTalesConfigFiles.CAMERA_OPTIONS));
        persistedAgain.load();
        assertFalse(persistedAgain.getBoolean(
                "enabled",
                LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                true, ""));
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
     * The screen reads the file afresh, and the file holds only the value
     * saved; dressed in the camera's definitions it restores the shipped
     * default and keeps the value.
     */
    @Test
    public void aSavedOptionStillRestoresTheShippedDefault() throws Exception {
        File directory = temporaryFolder.newFolder("defaults-config");
        initializeForgeHome(directory.getParentFile());
        LostTalesThirdPersonConfig.load(directory);
        Configuration screen = LostTalesThirdPersonConfig.createConfiguration();
        screen.load();
        screen.getCategory(LostTalesThirdPersonConfig.CATEGORY_CAMERA)
                .get("distanceMultiplier").set(1.73D);
        LostTalesThirdPersonConfig.savePendingGuiConfiguration();
        LostTalesThirdPersonConfig.reload();

        Configuration reopened = LostTalesThirdPersonConfig.createConfiguration();
        reopened.load();
        Property distance = reopened.getCategory(
                LostTalesThirdPersonConfig.CATEGORY_CAMERA).get("distanceMultiplier");
        assertEquals("1.73", distance.getDefault());
        LostTalesThirdPersonConfig.applyShippedDefinitions(reopened);
        assertEquals(1.73D, distance.getDouble(), 0.0D);
        assertEquals("1.0", distance.getDefault());
        assertTrue(distance.comment.contains("camera distance"));
    }

    private static void initializeForgeHome(File directory) throws Exception {
        Field minecraftHome = FMLInjectionData.class
                .getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, directory);
    }
}
