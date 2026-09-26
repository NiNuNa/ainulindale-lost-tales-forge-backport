package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatSettingsSections;
import com.ninuna.losttales.client.settings.ClientSettingsSections;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every client option is set in Settings, or kept in the client file
 * alone on purpose and named so in {@link LostTalesConfig#FILE_ONLY_CLIENT_KEYS};
 * every option of the camera's file is set in Settings (Nils, 2026-09-26,
 * Q2 a). A new option with neither fails here rather than going missing
 * from every screen. The sections stand in the order Nils set them.
 */
public final class SettingsCoverageTest {
    /** Settings as every screen makes it: the windows', the chat's, then every client option's. */
    private static Settings everySection() {
        Settings settings = new Settings(new WindowMenus(new SubWindows()));
        ChatSettingsSections.SECTIONS.addTo(settings);
        ClientSettingsSections.SECTIONS.addTo(settings);
        return settings;
    }

    private static Set<String> keysKeptIn(Settings settings,
                                          Settings.Store store) {
        Set<String> keys = new TreeSet<String>();
        for (Settings.Setting setting : settings.allSettings()) {
            if (setting.store() == store) {
                keys.add(setting.key);
            }
        }
        return keys;
    }

    @Test
    public void everyClientOptionIsInSettingsOrKeptInTheFileAlone() {
        Set<String> shown = keysKeptIn(everySection(),
                Settings.Store.CLIENT_FILE);
        Set<String> orphaned = new TreeSet<String>();
        for (String key : LostTalesConfig.clientOptionKeys()) {
            if (!shown.contains(key)
                    && !LostTalesConfig.FILE_ONLY_CLIENT_KEYS.contains(key)) {
                orphaned.add(key);
            }
        }
        assertEquals("client options neither in Settings nor kept in the "
                + "file alone", new TreeSet<String>(), orphaned);
    }

    @Test
    public void everyCameraOptionIsInSettings() {
        Set<String> shown = keysKeptIn(everySection(),
                Settings.Store.CAMERA_FILE);
        Set<String> orphaned = new TreeSet<String>(
                LostTalesThirdPersonConfig.optionKeys());
        orphaned.removeAll(shown);
        assertEquals("camera options not in Settings",
                new TreeSet<String>(), orphaned);
    }

    @Test
    public void everyRowNamesAnOptionItsFileDefines() {
        Settings settings = everySection();
        Set<String> strays = new TreeSet<String>(keysKeptIn(settings,
                Settings.Store.CLIENT_FILE));
        strays.removeAll(LostTalesConfig.clientOptionKeys());
        Set<String> cameraStrays = new TreeSet<String>(keysKeptIn(settings,
                Settings.Store.CAMERA_FILE));
        cameraStrays.removeAll(LostTalesThirdPersonConfig.optionKeys());
        strays.addAll(cameraStrays);
        assertEquals("rows naming no option of their file",
                new TreeSet<String>(), strays);
    }

    @Test
    public void noOptionIsBothInSettingsAndKeptInTheFileAlone() {
        Set<String> both = new TreeSet<String>(keysKeptIn(everySection(),
                Settings.Store.CLIENT_FILE));
        both.retainAll(LostTalesConfig.FILE_ONLY_CLIENT_KEYS);
        assertEquals(new TreeSet<String>(), both);
        assertTrue("each key kept in the file alone is an option",
                LostTalesConfig.clientOptionKeys().containsAll(
                        LostTalesConfig.FILE_ONLY_CLIENT_KEYS));
    }

    @Test
    public void noOptionHasTwoRows() {
        Set<String> seen = new HashSet<String>();
        Set<String> twice = new TreeSet<String>();
        for (Settings.Setting setting : everySection().allSettings()) {
            if (!seen.add(setting.key)) {
                twice.add(setting.key);
            }
        }
        assertEquals(new TreeSet<String>(), twice);
    }

    @Test
    public void theSectionsStandInTheirOrder() {
        List<String> expected = Arrays.asList(
                "gui.losttales.window.settings.section.windows",
                "gui.losttales.chat.settings.section.look",
                "gui.losttales.chat.settings.section.messages",
                "gui.losttales.chat.settings.section.mentions",
                "gui.losttales.chat.settings.section.typing",
                "gui.losttales.chat.settings.section.feed",
                "gui.losttales.chat.settings.section.channels",
                "gui.losttales.chat.settings.section.ignored",
                "gui.losttales.chat.settings.section.shortcuts",
                "gui.losttales.settings.section.hud",
                "gui.losttales.settings.section.compass",
                "gui.losttales.settings.section.quests",
                "gui.losttales.settings.section.map",
                "gui.losttales.settings.section.camera",
                "gui.losttales.settings.section.motion",
                "gui.losttales.settings.section.screens",
                "gui.losttales.settings.section.appearance",
                "gui.losttales.settings.section.developer");
        assertEquals(expected, everySection().sectionTitleKeys());
    }

    @Test
    public void aSystemsSectionsAreAddedOnce() {
        Settings settings = everySection();
        int before = settings.sectionTitleKeys().size();
        ClientSettingsSections.SECTIONS.addTo(settings);
        assertEquals(before, settings.sectionTitleKeys().size());
    }

    @Test
    public void theHistoryLengthIsANumberInTheChatsMessages() {
        for (Settings.Setting setting : everySection().allSettings()) {
            if ("chatHistoryLines".equals(setting.key)) {
                assertTrue(setting instanceof Settings.Numeric);
                return;
            }
        }
        throw new AssertionError("chatHistoryLines has no row");
    }
}
