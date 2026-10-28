package com.ninuna.losttales.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The mod's lang files by language (DISC-30): a language is one the mod
 * ships, named the game's way; its words are its file's lines over the
 * English ones; nothing but a code ever reaches the resources.
 */
public final class LostTalesLanguagesTest {

    /** A mod that ships English and German, the German file holding one line. */
    private static class Files implements LostTalesLanguages.LangFiles {
        final List<String> asked = new ArrayList<String>();
        final List<String> read = new ArrayList<String>();
        final List<String> names = new ArrayList<String>(
                Arrays.asList("en_US", "de_DE", "README", "de_de", "../x"));

        @Override
        public boolean has(String language) {
            this.asked.add(language);
            return "en_US".equals(language) || "de_DE".equals(language);
        }

        @Override
        public Map<String, String> read(String language) {
            this.read.add(language);
            Map<String, String> lines = new HashMap<String, String>();
            lines.put("chat.losttales.discord.topic.online", "Server online, auf Deutsch");
            lines.put("chat.losttales.discord.notice.joined", "%s ist da");
            return lines;
        }

        @Override
        public List<String> listed() {
            return this.names;
        }
    }

    private static final class Collected implements LostTalesLanguages.Warnings {
        final List<String> lines = new ArrayList<String>();

        @Override
        public void warn(String message) {
            this.lines.add(message);
        }
    }

    @After
    public void forget() {
        LostTalesLanguages.forget();
    }

    @Test
    public void aLanguageIsSpelledTheGamesWay() {
        assertEquals("de_DE", LostTalesLanguages.spelled(" de_de "));
        assertEquals("de_DE", LostTalesLanguages.spelled("DE-de"));
        assertEquals("zh_CN", LostTalesLanguages.spelled("ZH_cn"));
        assertEquals("qya_AA", LostTalesLanguages.spelled("qya_aa"));
        for (String value : new String[] {"", "de", "en US", "../../server",
                "en_US/../x", "de_DE.lang", "d_DE", "de_DE_x", "_DE", "de_",
                "deutsch_DEUTSCH", "de§_DE"}) {
            assertEquals(value, "", LostTalesLanguages.spelled(value));
        }
        assertEquals("", LostTalesLanguages.spelled(null));
    }

    /** Only a well-formed code is ever looked for among the files. */
    @Test
    public void onlyACodeReachesTheFiles() {
        Files files = new Files();
        assertEquals("de_DE", LostTalesLanguages.find("de-de", files));
        assertEquals("", LostTalesLanguages.find("fr_FR", files));
        assertEquals("", LostTalesLanguages.find("../de_DE", files));
        assertEquals("", LostTalesLanguages.find("de_DE.lang", files));
        assertEquals(Arrays.asList("de_DE", "fr_FR"), files.asked);
        assertFalse(LostTalesLanguages.has("de_de", files));
        assertFalse(LostTalesLanguages.has(null, files));
    }

    @Test
    public void anOptionNamingNoShippedLanguageIsEnglishWithOneLine() {
        Files files = new Files();
        Collected log = new Collected();
        assertEquals("de_DE", LostTalesLanguages.resolve("de_de", "discord.language",
                files, log));
        assertEquals("en_US", LostTalesLanguages.resolve(" ", "discord.language",
                files, log));
        assertEquals("en_US", LostTalesLanguages.resolve(null, "discord.language",
                files, log));
        assertTrue(log.lines.isEmpty());
        assertEquals("en_US", LostTalesLanguages.resolve("fr_FR", "discord.language",
                files, log));
        assertEquals(1, log.lines.size());
        assertTrue(log.lines.get(0), log.lines.get(0).startsWith(
                "discord.language names 'fr_FR'"));
        String longValue = "x_" + new String(new char[200]).replace('\0', 'y');
        LostTalesLanguages.resolve(longValue, "discord.language", files, log);
        String last = log.lines.get(log.lines.size() - 1);
        assertFalse(last, last.contains(longValue));
        assertTrue(last, last.contains("..."));
    }

    /** A language's words are its lines over English; a key neither has reads as itself. */
    @Test
    public void aLanguagesWordsStandOverTheEnglishOnes() {
        Files files = new Files();
        LostTalesWords german = LostTalesLanguages.words("de_DE", files);
        assertEquals("Server online, auf Deutsch",
                german.format("chat.losttales.discord.topic.online"));
        assertEquals("Mira ist da", german.format(
                "chat.losttales.discord.notice.joined", "Mira"));
        assertEquals("the line its file lacks reads in English",
                EnglishWords.INSTANCE.format("chat.losttales.discord.topic.offline"),
                german.format("chat.losttales.discord.topic.offline"));
        assertEquals("no.such.key", german.format("no.such.key"));
        assertSame("read once", german, LostTalesLanguages.words("de_DE", files));
        assertEquals(Collections.singletonList("de_DE"), files.read);
    }

    @Test
    public void englishAndALanguageNotShippedReadTheEnglishFile() {
        Files files = new Files();
        assertSame(LostTalesLangFile.ENGLISH_WORDS, LostTalesLanguages.words("en_US", files));
        assertSame(LostTalesLangFile.ENGLISH_WORDS, LostTalesLanguages.words("fr_FR", files));
        assertSame(LostTalesLangFile.ENGLISH_WORDS, LostTalesLanguages.words("../x", files));
        assertSame(LostTalesLangFile.ENGLISH_WORDS, LostTalesLanguages.words(null, files));
        assertTrue(files.read.isEmpty());
    }

    /** A file that cannot be read leaves its readers in English. */
    @Test
    public void anUnreadableFileReadsInEnglish() {
        LostTalesLanguages.LangFiles unreadable = new LostTalesLanguages.LangFiles() {
            @Override
            public boolean has(String language) {
                return true;
            }

            @Override
            public Map<String, String> read(String language) throws IOException {
                throw new IOException("unreadable");
            }

            @Override
            public List<String> listed() {
                return Collections.emptyList();
            }
        };
        assertEquals(EnglishWords.INSTANCE.format("chat.losttales.discord.topic.offline"),
                LostTalesLanguages.words("de_DE", unreadable)
                        .format("chat.losttales.discord.topic.offline"));
    }

    /** The shipped languages: English first, then each well-named file the mod has. */
    @Test
    public void theShippedLanguagesAreTheWellNamedFiles() {
        assertEquals(Arrays.asList("en_US", "de_DE"), LostTalesLanguages.shipped(new Files()));
        LostTalesLanguages.LangFiles unlisted = new Files() {
            @Override
            public List<String> listed() throws RuntimeException {
                throw new IllegalStateException("cannot list");
            }
        };
        assertEquals(Collections.singletonList("en_US"),
                LostTalesLanguages.shipped(unlisted));
    }

    /** The mod's own files: English ships and is listed; a language it lacks is not there. */
    @Test
    public void theModsOwnFilesAreAsked() {
        assertTrue(LostTalesLanguages.has("en_US"));
        assertFalse(LostTalesLanguages.has("xx_XX"));
        assertEquals("en_US", LostTalesLanguages.find("en_us"));
        assertEquals("en_US", LostTalesLanguages.shipped().get(0));
        assertTrue(LostTalesLanguages.shipped().contains("en_US"));
    }
}
