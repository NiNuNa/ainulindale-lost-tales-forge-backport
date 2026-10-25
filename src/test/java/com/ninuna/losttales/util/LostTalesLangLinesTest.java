package com.ninuna.losttales.util;

import com.ninuna.losttales.client.cache.LostTalesClientMobAggroCache;
import com.ninuna.losttales.client.cache.LostTalesClientQuickLootCache;
import com.ninuna.losttales.client.camera.CameraPresetId;
import com.ninuna.losttales.item.ELostTalesItem;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.item.EnumRarity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The words the code names by key have their English line, so no screen,
 * tooltip or notice ever shows a bare key: item tooltips and kinds, the
 * HUD's and the map's fixed words, the fallbacks drawn where the server
 * sends no name, and the lines content names by its id.
 */
public final class LostTalesLangLinesTest {
    private static final String SOURCE_ROOT = "src/main/java";
    /** A whole key: {@code gui.losttales.…}, {@code chat.losttales.…}, {@code losttales.…}. */
    private static final Pattern KEY = Pattern.compile(
            "(?:[A-Za-z]+\\.losttales|losttales)\\.[A-Za-z0-9_.\\-]*[A-Za-z0-9_]");
    /** The coremod's flags, system properties named like keys. */
    private static final Pattern FLAG = Pattern.compile("losttales\\.[A-Za-z]+\\.active");
    /** Literals shaped like keys that are not lines. */
    private static final List<String> NOT_LINES = Arrays.asList(
            // The prefix PageHelp.paragraphs numbers: .1, .2 and on.
            "gui.losttales.help.chat",
            // The permission node of LostTalesPermissions.
            "losttales.permission",
            // A system property of the map markers.
            "losttales.mapmarker.groupingDebug");
    private static final List<String> NAMED = Arrays.asList(
            "item.losttales.tooltip.hold.lore",
            "item.losttales.tooltip.hold.details",
            "item.losttales.tooltip.hold.set",
            "item.losttales.tooltip.key.shift",
            "item.losttales.tooltip.key.ctrl",
            "item.losttales.tooltip.faction",
            "item.losttales.tooltip.repair",
            "item.losttales.tooltip.type",
            "item.losttales.tooltip.created_by",
            "item.losttales.tooltip.rarity",
            "item.losttales.tooltip.set.heavy",
            "item.losttales.tooltip.set.light",
            "item.losttales.tooltip.set.empty",
            "item.losttales.tooltip.vessel.item",
            "item.losttales.tooltip.vessel.more",
            LostTalesClientQuickLootCache.CONTAINER_KEY,
            LostTalesClientQuickLootCache.SEALED_KEY,
            LostTalesClientMobAggroCache.ENEMY_KEY,
            "map.losttales.marker.unnamed",
            "gui.losttales.hud.location_discovered",
            "gui.losttales.compass.tracked",
            "gui.losttales.compass.distance",
            "gui.losttales.chat.hover.invalid_item",
            "gui.losttales.chat.hover.invalid_statistic",
            "gui.losttales.fellowship.unknown",
            "gui.losttales.map.go_here.description",
            "gui.losttales.character.attribute.value.hearts",
            "gui.losttales.character.attribute.value.percent",
            "gui.losttales.character.attribute.value.size",
            "gui.losttales.character.error.wait",
            "disconnect.losttales.character_recovery",
            "gui.losttales.character.lore.eomer.description",
            "gui.losttales.character.lore.frodo.description",
            "gui.losttales.character.lore.gandalf.description",
            "gui.losttales.character.lore.gollum.description",
            "gui.losttales.character.lore.sauron.description",
            "entity.losttales.BlueGoblinSoldier.name",
            "entity.losttales.Nia.name",
            "lotr.achievement.enterMoonElfBiome.title",
            "lotr.achievement.enterMoonElfBiome.desc",
            "lotr.waypoint.SUN_ELVES",
            "lotr.waypoint.SUN_ELVES.info",
            "lotr.waypoint.MOON_ELVES_2",
            "lotr.waypoint.MOON_ELVES_2.info");

    @Test
    public void everyWordTheCodeNamesHasAnEnglishLine() {
        for (String key : NAMED) {
            assertTrue(key, EnglishWords.INSTANCE.has(key));
        }
        for (ELostTalesItem.Type type : ELostTalesItem.Type.values()) {
            assertTrue(type.name(), EnglishWords.INSTANCE.has(type.getNameKey()));
        }
        for (EnumRarity rarity : EnumRarity.values()) {
            assertTrue(rarity.name(), EnglishWords.INSTANCE.has(
                    "item.losttales.tooltip.rarity." + rarity.name()));
        }
        for (CameraPresetId preset : CameraPresetId.values()) {
            assertTrue(preset.name(), EnglishWords.INSTANCE.has(
                    "losttales.config.third_person_camera.cameraPreset."
                            + preset.getConfigValue()));
        }
    }

    /**
     * Every key the code spells out whole in a string literal has its
     * English line, so a key mistyped or a line renamed fails here and not
     * on a screen. A literal followed by {@code +} is the start of a key
     * built as the game runs; {@link #NOT_LINES} and the coremod's flags
     * are shaped like keys but are not lines.
     */
    @Test
    public void everyKeyTheCodeSpellsOutHasAnEnglishLine() throws IOException {
        List<String> missing = new ArrayList<String>();
        int checked = 0;
        for (File file : javaFiles(sourceRoot())) {
            String source = read(file);
            for (int[] literal : literals(source)) {
                String key = source.substring(literal[0], literal[1]);
                if (!KEY.matcher(key).matches() || FLAG.matcher(key).matches()
                        || NOT_LINES.contains(key) || builtOn(source, literal[1] + 1)) {
                    continue;
                }
                checked++;
                if (!EnglishWords.INSTANCE.has(key)) {
                    missing.add(key + " (" + file.getName() + ")");
                }
            }
        }
        assertTrue("the code's keys were found", checked > 500);
        assertEquals("keys in the code with no line in en_US.lang",
                new ArrayList<String>(), missing);
    }

    /** The English lines read as the screens show them. */
    @Test
    public void englishReadsAsTheScreensShowIt() {
        assertEquals("Heavy Armor", EnglishWords.INSTANCE.format(
                ELostTalesItem.Type.ARMOR_HEAVY.getNameKey()));
        assertEquals("Urn (Sealed)", EnglishWords.INSTANCE.format(
                LostTalesClientQuickLootCache.SEALED_KEY, "Urn"));
        assertEquals("20 (10 hearts)", EnglishWords.INSTANCE.format(
                "gui.losttales.character.attribute.value.hearts", "20", "10"));
        assertEquals("110%", EnglishWords.INSTANCE.format(
                "gui.losttales.character.attribute.value.percent", "110"));
        assertEquals("12m", EnglishWords.INSTANCE.format(
                "gui.losttales.compass.distance", Long.valueOf(12L)));
    }

    /** Whether the code goes on with {@code +} after {@code index}: a key built as it runs. */
    private static boolean builtOn(String source, int index) {
        int cursor = index;
        while (cursor < source.length() && Character.isWhitespace(source.charAt(cursor))) {
            cursor++;
        }
        return cursor < source.length() && source.charAt(cursor) == '+';
    }

    /**
     * Where each string literal of a Java source starts and ends, its
     * quotes left out; comments and character literals are passed over.
     */
    private static List<int[]> literals(String source) {
        List<int[]> found = new ArrayList<int[]>();
        int length = source.length();
        int index = 0;
        while (index < length) {
            char c = source.charAt(index);
            char next = index + 1 < length ? source.charAt(index + 1) : '\0';
            if (c == '/' && next == '/') {
                int end = source.indexOf('\n', index);
                index = end < 0 ? length : end + 1;
            } else if (c == '/' && next == '*') {
                int end = source.indexOf("*/", index + 2);
                index = end < 0 ? length : end + 2;
            } else if (c == '\'' || c == '"') {
                int start = index + 1;
                index = start;
                while (index < length && source.charAt(index) != c) {
                    index += source.charAt(index) == '\\' ? 2 : 1;
                }
                if (c == '"') {
                    found.add(new int[] {start, Math.min(index, length)});
                }
                index++;
            } else {
                index++;
            }
        }
        return found;
    }

    /**
     * {@code src/main/java} under the working directory, as Gradle runs
     * tests, or under the nearest folder above it that has one.
     */
    private static File sourceRoot() throws IOException {
        File directory = new File(System.getProperty("user.dir")).getAbsoluteFile();
        while (directory != null) {
            File candidate = new File(directory, SOURCE_ROOT);
            if (new File(candidate, "com/ninuna/losttales").isDirectory()) {
                return candidate;
            }
            directory = directory.getParentFile();
        }
        throw new IOException("No " + SOURCE_ROOT + " at or above the working "
                + "directory; run this test from the repository");
    }

    private static List<File> javaFiles(File directory) {
        List<File> files = new ArrayList<File>();
        collect(directory, files);
        return files;
    }

    private static void collect(File directory, List<File> files) {
        File[] entries = directory.listFiles();
        if (entries == null) {
            return;
        }
        for (File entry : entries) {
            if (entry.isDirectory()) {
                collect(entry, files);
            } else if (entry.getName().endsWith(".java")) {
                files.add(entry);
            }
        }
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
