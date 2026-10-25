package com.ninuna.losttales.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The language a server writes in (LANG-08): one the mod has a lang file
 * for, spelled the game's way; anything else is English, with one line in
 * the log. Off a dedicated server nothing is put in force.
 */
public final class LostTalesServerLanguageTest {

    /** A mod that ships English and German. */
    private static final LostTalesServerLanguage.LangFiles ENGLISH_AND_GERMAN =
            new LostTalesServerLanguage.LangFiles() {
                private final Set<String> files =
                        new HashSet<String>(Arrays.asList("en_US", "de_DE"));

                @Override
                public boolean has(String language) {
                    return this.files.contains(language);
                }
            };

    private static final class Collected implements LostTalesServerLanguage.Warnings {
        final List<String> lines = new ArrayList<String>();

        @Override
        public void warn(String message) {
            this.lines.add(message);
        }
    }

    @Test
    public void aLanguageTheModHasIsTakenInTheGamesSpelling() {
        Collected log = new Collected();
        assertEquals("de_DE", LostTalesServerLanguage.resolve("de_DE", ENGLISH_AND_GERMAN, log));
        assertEquals("de_DE", LostTalesServerLanguage.resolve(" de_de ", ENGLISH_AND_GERMAN, log));
        assertEquals("de_DE", LostTalesServerLanguage.resolve("DE-de", ENGLISH_AND_GERMAN, log));
        assertEquals("en_US", LostTalesServerLanguage.resolve("en_US", ENGLISH_AND_GERMAN, log));
        assertTrue(log.lines.isEmpty());
    }

    @Test
    public void anUnknownLanguageIsEnglishWithOneLine() {
        Collected log = new Collected();
        assertEquals("en_US", LostTalesServerLanguage.resolve("fr_FR", ENGLISH_AND_GERMAN, log));
        assertEquals(1, log.lines.size());
        assertTrue(log.lines.get(0), log.lines.get(0).contains("'fr_FR'"));
        assertTrue(log.lines.get(0), log.lines.get(0).endsWith("writes in en_US"));
    }

    /** A blank value names no language, so it is English without a word. */
    @Test
    public void aBlankValueIsEnglishQuietly() {
        Collected log = new Collected();
        assertEquals("en_US", LostTalesServerLanguage.resolve("", ENGLISH_AND_GERMAN, log));
        assertEquals("en_US", LostTalesServerLanguage.resolve("  ", ENGLISH_AND_GERMAN, log));
        assertEquals("en_US", LostTalesServerLanguage.resolve(null, ENGLISH_AND_GERMAN, log));
        assertTrue(log.lines.isEmpty());
    }

    /** Only a code reaches the resources: a path never does, and the log repeats it cut short. */
    @Test
    public void nothingButALanguageCodeIsLookedFor() {
        final List<String> asked = new ArrayList<String>();
        LostTalesServerLanguage.LangFiles any = new LostTalesServerLanguage.LangFiles() {
            @Override
            public boolean has(String language) {
                asked.add(language);
                return true;
            }
        };
        Collected log = new Collected();
        for (String value : new String[] {"../../server", "en_US/../x", "de_DE.lang",
                "de", "d_DE", "de_DE_x", "_DE", "de_", "deutsch_DEUTSCH", "de§_DE"}) {
            assertEquals(value, "en_US", LostTalesServerLanguage.resolve(value, any, log));
        }
        assertTrue(asked.toString(), asked.isEmpty());
        String longValue = "x_" + new String(new char[200]).replace('\0', 'y');
        LostTalesServerLanguage.resolve(longValue, any, log);
        String last = log.lines.get(log.lines.size() - 1);
        assertFalse(last, last.contains(longValue));
        assertTrue(last, last.contains("..."));
        assertEquals("zh_CN", LostTalesServerLanguage.spelled("ZH_cn"));
        assertEquals("", LostTalesServerLanguage.spelled("en US"));
    }

    /** The mod's own files: English ships, a language it has no file for is not there. */
    @Test
    public void theModsOwnFilesAreAsked() {
        assertTrue(LostTalesServerLanguage.MOD_FILES.has("en_US"));
        assertFalse(LostTalesServerLanguage.MOD_FILES.has("xx_XX"));
        assertEquals("en_US", LostTalesServerLanguage.resolve("en_us"));
    }

    /** No dedicated server runs in a test, so nothing is put in force and English stands. */
    @Test
    public void offADedicatedServerNothingIsPutInForce() {
        LostTalesServerLanguage.apply("de_DE");
        assertEquals("en_US", LostTalesServerLanguage.inForce());
    }
}
