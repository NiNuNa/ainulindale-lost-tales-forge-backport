package com.ninuna.losttales.util;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;

/**
 * The mod's own lang files in any language it ships, read from its
 * resources: the words of a reader with no game of its own, a linked
 * Discord channel (DISC-30). Players never read these, since each game
 * reads the mod's words in its own language (LANG-08). A language is
 * named the game's way, {@code de_DE}, and only such a code ever reaches
 * the resources, never a path. A language's words are its file's lines
 * over the English ones: a line its file lacks reads in English, and a
 * key neither has reads as itself.
 */
public final class LostTalesLanguages {
    /** The language the mod always ships, and what stands in for one it does not. */
    public static final String ENGLISH = "en_US";
    /** Where the mod's lang files are: a language's is this, its code and {@code .lang}. */
    static final String FOLDER = "/assets/losttales/lang/";
    private static final String SUFFIX = ".lang";
    /**
     * A language code spelled the game's way, {@code de_DE}, {@code zh_CN}
     * or {@code qya_AA}: letters before the underscore, letters or digits
     * after it.
     */
    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}_[A-Z0-9]{2,3}");
    /** The most languages listed or held at once: far more than the mod ships. */
    static final int MAX_LANGUAGES = 64;
    /** The most bytes one lang file is read to; the English file is a few hundred kilobytes. */
    static final int MAX_FILE_BYTES = 4 * 1024 * 1024;
    /** The longest a refused value is repeated in the log. */
    private static final int SHOWN_LENGTH = 32;

    /** Which lang files the mod has, and their lines. */
    interface LangFiles {
        boolean has(String language);

        /** The file's lines by key. */
        Map<String, String> read(String language) throws IOException;

        /** The codes of the files there, as their names give them, unchecked. */
        List<String> listed() throws IOException;
    }

    /** The mod's own files: in the jar, or the resources folder in development. */
    static final LangFiles MOD_FILES = new LangFiles() {
        @Override
        public boolean has(String language) {
            return LostTalesLanguages.class.getResource(FOLDER + language + SUFFIX) != null;
        }

        @Override
        public Map<String, String> read(String language) throws IOException {
            InputStream in = LostTalesLanguages.class.getResourceAsStream(
                    FOLDER + language + SUFFIX);
            if (in == null) {
                throw new IOException("no lang file " + language + SUFFIX);
            }
            return LostTalesLangFile.read(new Capped(in, MAX_FILE_BYTES));
        }

        @Override
        public List<String> listed() throws IOException {
            return filesBeside(LostTalesLanguages.class.getResource(
                    FOLDER + ENGLISH + SUFFIX));
        }
    };

    /** Where a refused value is told: the server log in the game, a list in tests. */
    interface Warnings {
        void warn(String message);
    }

    private static final Warnings LOG = new Warnings() {
        @Override
        public void warn(String message) {
            LostTalesLog.warning("%s", message);
        }
    };

    /** The words of each language read so far; a bundled file does not change. */
    private static final Map<String, LostTalesWords> HELD =
            new HashMap<String, LostTalesWords>();
    /** The languages the mod ships, listed once; null until asked. */
    private static List<String> shipped;

    private LostTalesLanguages() {}

    /**
     * {@code typed} as a language code spelled the game's way, a dash read
     * as the underscore ({@code de-de} is {@code de_DE}); empty for
     * anything not shaped like one.
     */
    public static String spelled(String typed) {
        String text = typed == null ? "" : typed.trim().replace('-', '_');
        int split = text.indexOf('_');
        if (split <= 0 || split != text.lastIndexOf('_')) {
            return "";
        }
        String code = text.substring(0, split).toLowerCase(Locale.ROOT) + "_"
                + text.substring(split + 1).toUpperCase(Locale.ROOT);
        return CODE.matcher(code).matches() ? code : "";
    }

    /** Whether the mod ships a lang file for {@code language}, a code spelled the game's way. */
    public static boolean has(String language) {
        return has(language, MOD_FILES);
    }

    static boolean has(String language, LangFiles files) {
        return language != null && CODE.matcher(language).matches()
                && files.has(language);
    }

    /**
     * The language {@code typed} names, spelled the game's way, when the
     * mod ships its lang file; empty otherwise.
     */
    public static String find(String typed) {
        return find(typed, MOD_FILES);
    }

    static String find(String typed, LangFiles files) {
        String code = spelled(typed);
        return code.length() > 0 && has(code, files) ? code : "";
    }

    /**
     * The language an option names, as {@link #find} reads it; English
     * when it names none the mod ships, with one warning naming the value
     * and the option. A blank value is English without one.
     */
    public static String resolve(String configured, String option) {
        return resolve(configured, option, MOD_FILES, LOG);
    }

    static String resolve(String configured, String option, LangFiles files,
                          Warnings warnings) {
        if (configured == null || configured.trim().length() == 0) {
            return ENGLISH;
        }
        String code = find(configured, files);
        if (code.length() > 0) {
            return code;
        }
        if (warnings != null) {
            warnings.warn(option + " names '" + shown(configured)
                    + "', a language the mod ships no lang file for; " + ENGLISH
                    + " stands in");
        }
        return ENGLISH;
    }

    /**
     * The mod's words in {@code language}: its file's lines over the
     * English ones. English for a language the mod does not ship, and for
     * one whose file cannot be read, which is said once in the log.
     */
    public static LostTalesWords words(String language) {
        return words(language, MOD_FILES);
    }

    static synchronized LostTalesWords words(String language, LangFiles files) {
        if (language == null || ENGLISH.equals(language) || !has(language, files)) {
            return LostTalesLangFile.ENGLISH_WORDS;
        }
        LostTalesWords held = HELD.get(language);
        if (held != null) {
            return held;
        }
        Map<String, String> lines;
        try {
            lines = files.read(language);
        } catch (IOException unreadable) {
            LostTalesLog.warning("Could not read the mod's lang file %s%s (%s);"
                    + " its readers read English", language, SUFFIX, unreadable);
            lines = Collections.emptyMap();
        }
        LostTalesWords words = new Over(lines);
        if (HELD.size() < MAX_LANGUAGES) {
            HELD.put(language, words);
        }
        return words;
    }

    /**
     * The languages the mod ships a lang file for, English first and the
     * rest by code, at most {@link #MAX_LANGUAGES}; listed once. English
     * alone when the files cannot be listed.
     */
    public static synchronized List<String> shipped() {
        if (shipped == null) {
            shipped = shipped(MOD_FILES);
        }
        return shipped;
    }

    static List<String> shipped(LangFiles files) {
        Set<String> found = new TreeSet<String>();
        try {
            for (String name : files.listed()) {
                String code = spelled(name);
                if (code.equals(name) && found.size() < MAX_LANGUAGES
                        && has(code, files)) {
                    found.add(code);
                }
            }
        } catch (IOException unlisted) {
            LostTalesLog.warning("Could not list the mod's lang files (%s);"
                    + " only %s is offered", unlisted, ENGLISH);
        } catch (RuntimeException unlisted) {
            LostTalesLog.warning("Could not list the mod's lang files (%s);"
                    + " only %s is offered", unlisted, ENGLISH);
        }
        Set<String> ordered = new LinkedHashSet<String>();
        ordered.add(ENGLISH);
        ordered.addAll(found);
        List<String> listed = new ArrayList<String>(ordered);
        return Collections.unmodifiableList(listed.size() > MAX_LANGUAGES
                ? listed.subList(0, MAX_LANGUAGES) : listed);
    }

    /**
     * The names, without {@code .lang}, of the files in the folder the
     * English one is in: a jar's entries, or a folder's files. Anything
     * else lists nothing.
     */
    private static List<String> filesBeside(URL english) throws IOException {
        List<String> names = new ArrayList<String>();
        if (english == null) {
            return names;
        }
        if ("jar".equals(english.getProtocol())) {
            URLConnection connection = english.openConnection();
            if (!(connection instanceof JarURLConnection)) {
                return names;
            }
            // A jar of its own, closed here: the cached one is the class loader's.
            connection.setUseCaches(false);
            JarFile jar = ((JarURLConnection)connection).getJarFile();
            try {
                String folder = FOLDER.substring(1);
                Enumeration<JarEntry> entries = jar.entries();
                while (entries.hasMoreElements()) {
                    String name = entries.nextElement().getName();
                    if (name.startsWith(folder) && name.endsWith(SUFFIX)
                            && name.indexOf('/', folder.length()) < 0) {
                        names.add(name.substring(folder.length(),
                                name.length() - SUFFIX.length()));
                    }
                }
            } finally {
                jar.close();
            }
        } else if ("file".equals(english.getProtocol())) {
            File[] files;
            try {
                files = new File(english.toURI()).getParentFile().listFiles();
            } catch (URISyntaxException unreadable) {
                throw new IOException(unreadable);
            }
            for (File file : files == null ? new File[0] : files) {
                String name = file.getName();
                if (file.isFile() && name.endsWith(SUFFIX)) {
                    names.add(name.substring(0, name.length() - SUFFIX.length()));
                }
            }
        }
        return names;
    }

    /** A refused value as the log repeats it: cut short, nothing unprintable. */
    private static String shown(String configured) {
        String text = configured.trim();
        StringBuilder shown = new StringBuilder(Math.min(text.length(), SHOWN_LENGTH));
        for (int index = 0; index < text.length() && index < SHOWN_LENGTH; index++) {
            char character = text.charAt(index);
            shown.append(character < ' ' || character == 127 || character == '§'
                    ? '?' : character);
        }
        return text.length() > SHOWN_LENGTH ? shown.append("...").toString()
                : shown.toString();
    }

    /** Forgets the words read and the languages listed; for tests. */
    static synchronized void forget() {
        HELD.clear();
        shipped = null;
    }

    /** A language's lines over the English ones. */
    private static final class Over implements LostTalesWords {
        private final Map<String, String> lines;

        Over(Map<String, String> lines) {
            this.lines = lines;
        }

        @Override
        public String format(String key, Object... arguments) {
            String line = this.lines.get(key);
            if (line == null) {
                line = LostTalesLangFile.english().get(key);
            }
            if (line == null) {
                return key;
            }
            if (arguments == null || arguments.length == 0) {
                return line;
            }
            try {
                return String.format(line, arguments);
            } catch (IllegalFormatException wrong) {
                return line;
            }
        }
    }

    /** A stream that refuses to be read past a number of bytes. */
    private static final class Capped extends FilterInputStream {
        private long left;

        Capped(InputStream in, long most) {
            super(in);
            this.left = most;
        }

        @Override
        public int read() throws IOException {
            int read = super.read();
            if (read >= 0) {
                spend(1);
            }
            return read;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int read = super.read(buffer, offset, length);
            if (read > 0) {
                spend(read);
            }
            return read;
        }

        private void spend(int bytes) throws IOException {
            this.left -= bytes;
            if (this.left < 0L) {
                throw new IOException("the lang file is longer than "
                        + MAX_FILE_BYTES + " bytes");
            }
        }
    }
}
