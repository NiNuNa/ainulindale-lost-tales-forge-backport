package com.ninuna.losttales.config;

import com.ninuna.losttales.util.LostTalesLangFile;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.Locale;
import java.util.Map;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

/**
 * The words of every option, all lines of the lang file under its
 * category and key, as Forge names a language key and its tooltip: the
 * name at {@code losttales.config.<category>.<key>}, the tip at the same
 * key with {@code .tooltip}, and each word of an option of a few words at
 * the same key with the word in lower case. The game shows them in each
 * player's language: Settings and the Server Settings page name a setting
 * by its name line, the page and {@code /losttales config get} explain it
 * by its tip. The config files' comments are the English tips, read from
 * the mod's own English lang file ({@link LostTalesLangFile}), so the code
 * holds no option's words.
 */
public final class LostTalesConfigWords {
    /**
     * What an option is read with where Forge asks for a comment: nothing,
     * since its words are its lang lines. {@link #apply} puts the English
     * tip in its place.
     */
    public static final String TIP = "";

    private static final String PREFIX = "losttales.config.";
    private static final String TOOLTIP = ".tooltip";

    /** Whether a missing English line has been logged: once is enough to find the gap. */
    private static boolean missingLogged;

    private LostTalesConfigWords() {}

    /** The lang key of an option's name, its language key in Forge's terms. */
    public static String nameKey(String category, String key) {
        return PREFIX + category + "." + key;
    }

    /** The lang key of an option's tip: what it does, a sentence or more. */
    public static String tipKey(String category, String key) {
        return nameKey(category, key) + TOOLTIP;
    }

    /** The lang key of one of the few words an option takes, in lower case. */
    public static String wordKey(String category, String key, String word) {
        return nameKey(category, key) + "."
                + (word == null ? "" : word.toLowerCase(Locale.ROOT));
    }

    /** Whether the English lang file has a line under {@code langKey}. */
    public static boolean hasEnglish(String langKey) {
        return LostTalesLangFile.english().containsKey(langKey);
    }

    /**
     * The English line under {@code langKey}; the key itself where there is
     * none, logged the first time, so a gap never blanks or breaks a file.
     */
    public static String english(String langKey) {
        Map<String, String> lines = LostTalesLangFile.english();
        String line = lines.get(langKey);
        if (line != null) {
            return line;
        }
        logMissing(langKey, lines.isEmpty());
        return langKey;
    }

    private static synchronized void logMissing(String langKey, boolean noFile) {
        if (missingLogged || noFile) {
            // An unreadable file has been logged once already.
            return;
        }
        missingLogged = true;
        LostTalesLog.warning("The English lang file has no line %s; the config "
                + "files show the key in its place. Other missing lines are "
                + "not logged.", langKey);
    }

    /**
     * Gives every option of {@code definitions} its language key and, for
     * its comment, its English tip followed by whatever Forge wrote there
     * as it was read: its range and its default. Run once, on the
     * definitions, which every save and screen then dress the files in
     * ({@link LostTalesConfigDefinitions}).
     */
    public static void apply(Configuration definitions) {
        if (definitions == null) {
            return;
        }
        for (String name : definitions.getCategoryNames()) {
            ConfigCategory category = definitions.getCategory(name);
            for (Map.Entry<String, Property> entry : category.getValues().entrySet()) {
                Property property = entry.getValue();
                property.setLanguageKey(nameKey(name, entry.getKey()));
                String forge = property.comment == null ? "" : property.comment;
                property.comment = english(tipKey(name, entry.getKey())) + forge;
            }
        }
    }
}
