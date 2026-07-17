package com.ninuna.losttales.config.client;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;

import cpw.mods.fml.relauncher.FMLInjectionData;
import java.io.File;
import java.lang.reflect.Field;
import net.minecraftforge.common.config.Configuration;
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
                new File(directory, "losttales-third-person.cfg"));
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
                new File(directory, "losttales-third-person.cfg"));
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
        File configFile = new File(directory,
                "losttales-third-person.cfg");
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
    public void previousCorrectedHeadDefaultMigratesToCurrentLimit()
            throws Exception {
        File directory = temporaryFolder.newFolder("previous-head-config");
        initializeForgeHome(directory.getParentFile());
        File configFile = new File(directory,
                "losttales-third-person.cfg");
        Configuration previous = new Configuration(configFile);
        previous.load();
        previous.get(LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 65.0D).set(65.0D);
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

    @Test
    public void recentSeventyDegreeDefaultMigratesToCurrentLimit()
            throws Exception {
        File directory = temporaryFolder.newFolder("recent-head-config");
        initializeForgeHome(directory.getParentFile());
        File configFile = new File(directory,
                "losttales-third-person.cfg");
        Configuration recent = new Configuration(configFile);
        recent.load();
        recent.get(LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 70.0D).set(70.0D);
        recent.save();

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
    public void formerEightyDegreeDefaultMigratesToCurrentLimit()
            throws Exception {
        File directory = temporaryFolder.newFolder("last-head-config");
        initializeForgeHome(directory.getParentFile());
        File configFile = new File(directory,
                "losttales-third-person.cfg");
        Configuration previous = new Configuration(configFile);
        previous.load();
        previous.get(LostTalesThirdPersonConfig.CATEGORY_CAMERA,
                "headTrackingAngle", 80.0D).set(80.0D);
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

    @Test
    public void formerEightyFiveDegreeDefaultMigratesToCurrentLimit()
            throws Exception {
        File directory = temporaryFolder.newFolder("former-head-config");
        initializeForgeHome(directory.getParentFile());
        File configFile = new File(directory,
                "losttales-third-person.cfg");
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

    private static void initializeForgeHome(File directory) throws Exception {
        Field minecraftHome = FMLInjectionData.class
                .getDeclaredField("minecraftHome");
        minecraftHome.setAccessible(true);
        minecraftHome.set(null, directory);
    }
}
