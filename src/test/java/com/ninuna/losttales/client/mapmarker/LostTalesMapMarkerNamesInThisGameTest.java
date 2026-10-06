package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.util.LostTalesLangFile;
import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.util.StringTranslate;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The client draws a bundled marker by the name its game's language gives
 * it, keeps the words it was given beside that, and Find Location finds
 * the place by either and by its id.
 */
public final class LostTalesMapMarkerNamesInThisGameTest {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String KEY = "lotr.waypoint.LOSTTALES_MOSSY_CAVE";
    private static final List<String> KEYS = Arrays.asList(KEY, KEY + ".info");

    @Before
    public void readInAnotherLanguage() {
        inject(KEY + "=Moosige Höhle\n" + KEY + ".info=Unter Moos.\n");
    }

    @After
    public void readInEnglishAgain() {
        StringBuilder english = new StringBuilder();
        for (String key : KEYS) {
            english.append(key).append('=')
                    .append(LostTalesLangFile.english().get(key)).append('\n');
        }
        inject(english.toString());
    }

    @Test
    public void aBundledMarkerIsDrawnInTheGamesLanguage() {
        LostTalesMapMarkerData cave = cave(true);
        assertEquals("Moosige Höhle", cave.getName());
        assertEquals("Mossy Cave", cave.getGivenName());
        assertEquals("Unter Moos.", cave.getDescription());
    }

    /** A marker named by an operator keeps the words they wrote. */
    @Test
    public void anOperatorsWordsStay() {
        LostTalesMapMarkerData cave = cave(false);
        assertEquals("Mossy Cave", cave.getName());
        assertEquals("A cave.", cave.getDescription());
    }

    @Test
    public void findLocationFindsThePlaceByEitherNameAndItsId() {
        LostTalesMapMarkerData cave = cave(true);
        List<LostTalesMapSearchPrompt.Entry> entries =
                new ArrayList<LostTalesMapSearchPrompt.Entry>();
        entries.add(new LostTalesMapSearchPrompt.Entry(cave, cave.getName()));
        assertEquals(entries, LostTalesMapSearchPrompt.filter(entries, "moosige"));
        assertEquals(entries, LostTalesMapSearchPrompt.filter(entries, "mossy"));
        assertEquals(entries, LostTalesMapSearchPrompt.filter(entries, "mossy_cave"));
        assertTrue(LostTalesMapSearchPrompt.filter(entries, "bree").isEmpty());
    }

    private static LostTalesMapMarkerData cave(boolean bundled) {
        String id = "losttales:mossy_cave";
        return new LostTalesMapMarkerData(id, "Mossy Cave", "undiscovered",
                "gray", "", "A cave.", true, 100, -370.0D, 64.0D, 40.0D,
                220.0D, 9.0D, false, true, true, true, 0,
                LostTalesMapMarkerSource.CUSTOM_PRESET,
                bundled ? KEY : "", bundled ? KEY + ".info" : "");
    }

    private static void inject(String lines) {
        StringTranslate.inject(new ByteArrayInputStream(lines.getBytes(UTF_8)));
    }
}
