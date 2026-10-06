package com.ninuna.losttales.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The mod's English lang file, read from its own resources: each line
 * {@code key=words}, a line starting with {@code #} a comment. What needs
 * words in English whatever the game's language, before the game has
 * loaded its lang files: the config files' comments, written at the first
 * load. The game shows a player every other line in their own language.
 */
public final class LostTalesLangFile {
    /** Where the English lines are, among the mod's resources. */
    public static final String ENGLISH = "/assets/losttales/lang/en_US.lang";

    /** The English lines, read once; the file is part of the mod and does not change. */
    private static Map<String, String> english;

    private LostTalesLangFile() {}

    /**
     * Every English line by its key. Empty, and logged once, when the file
     * cannot be read, so a caller falls back rather than fails.
     */
    public static synchronized Map<String, String> english() {
        if (english == null) {
            Map<String, String> lines = Collections.emptyMap();
            InputStream in = LostTalesLangFile.class.getResourceAsStream(ENGLISH);
            if (in == null) {
                LostTalesLog.warning("The English lang file %s is missing from "
                        + "the mod; the config files show keys in place of words.",
                        ENGLISH);
            } else {
                try {
                    lines = read(in);
                } catch (IOException unreadable) {
                    LostTalesLog.warning("Could not read the English lang file "
                            + "%s (%s); the config files show keys in place of "
                            + "words.", ENGLISH, unreadable);
                }
            }
            english = Collections.unmodifiableMap(lines);
        }
        return english;
    }

    /**
     * The lines of a lang file in UTF-8, by key, as the game reads them:
     * an empty line or one starting with {@code #} is skipped, and a line
     * is split at its first {@code =}. Closes {@code in}.
     */
    public static Map<String, String> read(InputStream in) throws IOException {
        Map<String, String> lines = new HashMap<String, String>();
        BufferedReader reader = new BufferedReader(new InputStreamReader(in, "UTF-8"));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                int equals = line.indexOf('=');
                if (equals > 0 && !line.startsWith("#")) {
                    lines.put(line.substring(0, equals), line.substring(equals + 1));
                }
            }
        } finally {
            reader.close();
        }
        return lines;
    }
}
