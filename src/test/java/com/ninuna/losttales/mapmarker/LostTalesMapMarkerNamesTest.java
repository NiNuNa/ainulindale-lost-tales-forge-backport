package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.util.EnglishWords;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A bundled map marker is named in each player's language by the lang
 * line its id names: LOTR's line for a LOTR waypoint, the mod's line for
 * a Lost Tales marker. Words an operator gave a marker stay as written,
 * and a marker with no line keeps the words it was given.
 */
public final class LostTalesMapMarkerNamesTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String MOSSY_CAVE = "losttales:mossy_cave";
    private static final String MOSSY_CAVE_KEY = "lotr.waypoint.LOSTTALES_MOSSY_CAVE";
    private static final String HIMLING = "lotr:waypoint:himling";
    private static final String HIMLING_KEY = "lotr.waypoint.HIMLING";
    private static final List<String> KEYS = Arrays.asList(
            MOSSY_CAVE_KEY, MOSSY_CAVE_KEY + ".info");

    @Before
    public void readInAnotherLanguage() {
        inject(MOSSY_CAVE_KEY + "=Moosige Höhle\n"
                + MOSSY_CAVE_KEY + ".info=Ein feuchter Höhlenmund unter Moos.\n"
                + HIMLING_KEY + "=Himling-Insel\n");
    }

    /** The mod's English lines back, and LOTR's line, which no test file holds, empty again. */
    @After
    public void readInEnglishAgain() {
        StringBuilder english = new StringBuilder();
        for (String key : KEYS) {
            english.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        english.append(HIMLING_KEY).append("=Himling\n");
        inject(english.toString());
    }

    @Test
    public void anIdNamesItsLangKeys() {
        assertEquals(MOSSY_CAVE_KEY, LostTalesMapMarkerNames.nameKey(MOSSY_CAVE));
        assertEquals(MOSSY_CAVE_KEY + ".info",
                LostTalesMapMarkerNames.descriptionKey(MOSSY_CAVE));
        assertEquals("LOSTTALES_MOSSY_CAVE",
                LostTalesMapMarkerNames.waypointCode(MOSSY_CAVE));
        assertEquals(HIMLING_KEY, LostTalesMapMarkerNames.nameKey(HIMLING));
        // LOTR describes its own waypoints.
        assertEquals("", LostTalesMapMarkerNames.descriptionKey(HIMLING));
        // A player's waystone and anything else are named by their words alone.
        assertEquals("", LostTalesMapMarkerNames.nameKey(
                "losttales:player/0123456789abcdef0123456789abcdef"));
        assertEquals("", LostTalesMapMarkerNames.nameKey("fellowship_mark:abc"));
    }

    @Test
    public void aBundledMarkerReadsInTheGamesLanguage() {
        assertEquals("Moosige Höhle",
                LostTalesMapMarkerNames.shownName(MOSSY_CAVE, "Mossy Cave"));
        assertEquals("Moosige Höhle",
                LostTalesMapMarkerNames.shownName(MOSSY_CAVE, ""));
        assertEquals("Himling-Insel",
                LostTalesMapMarkerNames.shownName(HIMLING, "Himling"));
        assertTrue(LostTalesMapMarkerNames.isNamedByLang(MOSSY_CAVE, "Mossy Cave"));
    }

    /** What an operator wrote in place of the bundled name is theirs. */
    @Test
    public void anOperatorsNameIsNeverTranslated() {
        assertEquals("The Old Hole",
                LostTalesMapMarkerNames.shownName(MOSSY_CAVE, "The Old Hole"));
        assertFalse(LostTalesMapMarkerNames.isNamedByLang(MOSSY_CAVE, "The Old Hole"));
        assertEquals("Somewhere", LostTalesMapMarkerNames.shownName(
                "losttales:player/0123456789abcdef0123456789abcdef", "Somewhere"));
    }

    /** The marker files leave a Lost Tales marker's words to the lang file. */
    @Test
    public void theCatalogTakesItsEnglishWordsFromTheLangFile() {
        LostTalesMapMarkerDefinition cave = LostTalesMapMarkerCatalog.getMarker(MOSSY_CAVE);
        assertEquals("Mossy Cave", cave.getName());
        assertEquals(EnglishWords.INSTANCE.format(MOSSY_CAVE_KEY + ".info"),
                cave.getDescription());
        assertTrue(LostTalesMapMarkerNames.isBundledName(MOSSY_CAVE, "Mossy Cave"));
        assertTrue(LostTalesMapMarkerNames.isBundledDescription(
                MOSSY_CAVE, cave.getDescription()));
    }

    /**
     * Every Lost Tales marker has its name line, and every one that
     * registers a LOTR waypoint its description, which LOTR's map reads.
     */
    @Test
    public void everyLostTalesMarkerHasItsLines() {
        int lostTales = 0;
        for (LostTalesMapMarkerDefinition marker : LostTalesMapMarkerCatalog.getMarkers()) {
            if (!marker.getId().startsWith("losttales:")) {
                continue;
            }
            lostTales++;
            String key = LostTalesMapMarkerNames.nameKey(marker.getId());
            assertTrue(marker.getId(), EnglishWords.INSTANCE.has(key));
            if (marker.hasFastTravel()) {
                assertTrue(marker.getId() + " is described",
                        EnglishWords.INSTANCE.has(key + ".info"));
            }
        }
        assertTrue(lostTales > 0);
        assertFalse("no doubled keys are left",
                EnglishWords.INSTANCE.has("lotr.waypoint.LOSTTALES_LOSTTALES_MOSSY_CAVE"));
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
