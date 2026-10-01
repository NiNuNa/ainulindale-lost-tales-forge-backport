package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.chat.ChatSettingsSections;
import com.ninuna.losttales.client.settings.ClientSettingsSections;
import com.ninuna.losttales.config.DefinedClientOptions;
import com.ninuna.losttales.config.client.DefinedCameraOptions;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Every client option is set in Settings, or kept in the client file
 * alone on purpose and named so in {@link #FILE_ONLY_CLIENT_KEYS}; every
 * option of the camera's file is set in Settings. A new option with
 * neither fails here rather than going missing from every screen. The
 * sections stand in their order.
 */
public final class SettingsCoverageTest {
    /**
     * The client options Settings does not show, kept in the client file
     * alone: each is state another screen writes as it is used, not a
     * choice made in a list.
     */
    private static final Set<String> FILE_ONLY_CLIENT_KEYS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(
                    // The map's legend writes the categories it hides.
                    "hiddenMapLegendCategories",
                    // The map's waypoint editor writes a custom
                    // waypoint's colour and its note.
                    "customWaypointColors",
                    "customWaypointNotes")));

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
        for (String key : DefinedClientOptions.keys()) {
            if (!shown.contains(key)
                    && !FILE_ONLY_CLIENT_KEYS.contains(key)) {
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
                DefinedCameraOptions.keys());
        orphaned.removeAll(shown);
        assertEquals("camera options not in Settings",
                new TreeSet<String>(), orphaned);
    }

    @Test
    public void everyRowNamesAnOptionItsFileDefines() {
        Settings settings = everySection();
        Set<String> strays = new TreeSet<String>(keysKeptIn(settings,
                Settings.Store.CLIENT_FILE));
        strays.removeAll(DefinedClientOptions.keys());
        Set<String> cameraStrays = new TreeSet<String>(keysKeptIn(settings,
                Settings.Store.CAMERA_FILE));
        cameraStrays.removeAll(DefinedCameraOptions.keys());
        strays.addAll(cameraStrays);
        assertEquals("rows naming no option of their file",
                new TreeSet<String>(), strays);
    }

    @Test
    public void noOptionIsBothInSettingsAndKeptInTheFileAlone() {
        Set<String> both = new TreeSet<String>(keysKeptIn(everySection(),
                Settings.Store.CLIENT_FILE));
        both.retainAll(FILE_ONLY_CLIENT_KEYS);
        assertEquals(new TreeSet<String>(), both);
        assertTrue("each key kept in the file alone is an option",
                DefinedClientOptions.keys().containsAll(
                        FILE_ONLY_CLIENT_KEYS));
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
    public void eachSectionStandsInItsPlaceInItsOrder() {
        Settings settings = everySection();
        assertEquals(Arrays.asList(
                "gui.losttales.window.settings.section.windows"),
                settings.sectionTitleKeys(Settings.Place.WINDOWS));
        assertEquals(Arrays.asList(
                "gui.losttales.chat.settings.section.look",
                "gui.losttales.chat.settings.section.messages",
                "gui.losttales.chat.settings.section.mentions",
                "gui.losttales.chat.settings.section.typing",
                "gui.losttales.chat.settings.section.feed",
                "gui.losttales.chat.settings.section.ignored"),
                settings.sectionTitleKeys(Settings.Place.CHAT));
        assertEquals(Arrays.asList("gui.losttales.settings.section.quests"),
                settings.sectionTitleKeys(Settings.Place.QUESTS));
        assertEquals(Arrays.asList("gui.losttales.settings.section.map"),
                settings.sectionTitleKeys(Settings.Place.MAP));
        assertEquals(Arrays.asList("gui.losttales.settings.section.motion"),
                settings.sectionTitleKeys(Settings.Place.MOTION));
        assertEquals(Arrays.asList(
                "gui.losttales.window.settings.section.shortcuts",
                "gui.losttales.settings.section.hud",
                "gui.losttales.settings.section.compass",
                "gui.losttales.settings.section.camera",
                "gui.losttales.settings.section.screens",
                "gui.losttales.settings.section.appearance",
                "gui.losttales.settings.section.developer"),
                settings.sectionTitleKeys(Settings.Place.CLIENT));
    }

    @Test
    public void aSystemsSectionsAreAddedOnce() {
        Settings settings = everySection();
        int before = settings.allSettings().size();
        ClientSettingsSections.SECTIONS.addTo(settings);
        assertEquals(before, settings.allSettings().size());
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
