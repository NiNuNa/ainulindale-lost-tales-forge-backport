package com.ninuna.losttales.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.StringTranslate;

/**
 * The language a dedicated server writes in (LANG-08): the mod's lang
 * file that {@code chat.serverLanguage} names. Its lines are put over the
 * English ones Forge gives a dedicated server, so whatever the server
 * words itself reads in it: what the Discord bridge writes and relays on
 * ({@link LostTalesWords#LANG}), and the console's answers, which the
 * console reads from the same lines. Only the mod's own lines change;
 * vanilla's and LOTR's stay English, since a dedicated server holds no
 * other file of theirs. A single-player or LAN world writes in its host's
 * game language, which the game keeps, so the option is read there and
 * never applied. The logs are English written in the code and never pass
 * through the lang lines.
 */
public final class LostTalesServerLanguage {
    /** What a server writes in unless told otherwise, and what it falls back to. */
    public static final String ENGLISH = "en_US";
    /** Where the mod's lang files are: a language's is this, its code and {@code .lang}. */
    static final String FOLDER = "/assets/losttales/lang/";
    /** The longest a refused value is repeated in the log. */
    private static final int SHOWN_LENGTH = 32;
    /**
     * A language code spelled the game's way, {@code de_DE} or
     * {@code zh_CN}: letters before the underscore, letters or digits after
     * it. Never a path, so a value read from the file cannot reach any
     * resource but a lang file.
     */
    private static final Pattern CODE = Pattern.compile("[a-z]{2,3}_[A-Z0-9]{2,3}");

    /** Which lang files the mod has. */
    interface LangFiles {
        boolean has(String language);
    }

    /** The mod's own, among its resources: in the jar, or the resources folder in development. */
    static final LangFiles MOD_FILES = new LangFiles() {
        @Override
        public boolean has(String language) {
            return LostTalesServerLanguage.class.getResource(FOLDER + language + ".lang") != null;
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

    /**
     * The language whose lines are in force on this dedicated server; null
     * before the first, and always off one. The lines it put in belong to
     * the JVM and outlive a world, so it is not cleared with the server's
     * state: a dedicated server runs one world.
     */
    private static String applied;

    private LostTalesServerLanguage() {}

    /**
     * The language {@code configured} names, spelled the game's way
     * ({@code de_de} reads {@code de_DE}), when the mod has a lang file for
     * it; else English, with one warning naming the value. A blank value
     * is English without one.
     */
    public static String resolve(String configured) {
        return resolve(configured, MOD_FILES, LOG);
    }

    static String resolve(String configured, LangFiles files, Warnings warnings) {
        if (configured == null || configured.trim().length() == 0) {
            return ENGLISH;
        }
        String code = spelled(configured);
        if (code.length() > 0 && files.has(code)) {
            return code;
        }
        if (warnings != null) {
            warnings.warn("The server language '" + shown(configured)
                    + "' names no lang file of the mod; the server writes in " + ENGLISH);
        }
        return ENGLISH;
    }

    /**
     * {@code configured} as a language code spelled the game's way, a dash
     * read as the underscore; empty for anything not shaped like one.
     */
    static String spelled(String configured) {
        String text = configured == null ? "" : configured.trim().replace('-', '_');
        int split = text.indexOf('_');
        if (split <= 0 || split != text.lastIndexOf('_')) {
            return "";
        }
        String code = text.substring(0, split).toLowerCase(Locale.ROOT) + "_"
                + text.substring(split + 1).toUpperCase(Locale.ROOT);
        return CODE.matcher(code).matches() ? code : "";
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

    /**
     * The language put in force on this dedicated server, English before
     * the first. A world hosted in a game writes in its host's language
     * and leaves this at English.
     */
    public static synchronized String inForce() {
        return applied == null ? ENGLISH : applied;
    }

    /**
     * Puts {@code language}'s lines in force on a dedicated server: the
     * mod's English lines first, so a line its file lacks reads in
     * English, then that language's over them. Off a dedicated server, or
     * for the language already in force, nothing. Never throws: a file
     * that cannot be read leaves the lines as they were, with one warning.
     */
    public static synchronized void apply(String language) {
        if (!isDedicatedServer()) {
            return;
        }
        String wanted = language == null || language.length() == 0 ? ENGLISH : language;
        if (wanted.equals(applied)) {
            return;
        }
        try {
            inject(ENGLISH);
            if (!ENGLISH.equals(wanted)) {
                inject(wanted);
            }
            applied = wanted;
        } catch (IOException unreadable) {
            LostTalesLog.warning("Could not read the lang file of the server language %s"
                    + " (%s); the server writes as before", wanted, unreadable);
        } catch (RuntimeException fault) {
            LostTalesLog.warning("Could not put the server language %s in force (%s);"
                    + " the server writes as before", wanted, fault);
        } catch (LinkageError fault) {
            LostTalesLog.warning("Could not put the server language %s in force (%s);"
                    + " the server writes as before", wanted, fault);
        }
    }

    /** Puts one of the mod's lang files over the lines the server holds. */
    private static void inject(String language) throws IOException {
        InputStream in = LostTalesServerLanguage.class.getResourceAsStream(
                FOLDER + language + ".lang");
        if (in == null) {
            throw new FileNotFoundException(FOLDER + language + ".lang");
        }
        try {
            StringTranslate.inject(in);
        } finally {
            in.close();
        }
    }

    /**
     * Whether a dedicated server runs here, rather than a world hosted in
     * a game. Asked of the server rather than of Forge's side, which a
     * test has none of.
     */
    private static boolean isDedicatedServer() {
        MinecraftServer server = MinecraftServer.getServer();
        return server != null && server.isDedicatedServer();
    }
}
