package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import java.lang.Character.UnicodeScript;
import java.text.Normalizer;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * When two names are the same name, for every check that asks: letters and
 * digits alone, lower case, accents taken off, and each letter read as the
 * letters it looks like (CHAR-11). So *Aldric*, *aldric* and *Al-dric* are
 * one name, so are *Éomer* and *Eomer*, and so are *Server* and *Sеrver*
 * with a Cyrillic *е*. A name uses the letters of one script. A few names
 * belong to the chat's own voices and no character may take them.
 */
public final class CharacterNames {
    private static final String[] VOICES = { "Server", "Client", "Narrator", "Discord" };
    /** The scripts a name may mix, as Unicode's highly restrictive level has them. */
    private static final Set<UnicodeScript> JAPANESE =
            EnumSet.of(UnicodeScript.HAN, UnicodeScript.HIRAGANA, UnicodeScript.KATAKANA);
    private static final Set<UnicodeScript> CHINESE =
            EnumSet.of(UnicodeScript.HAN, UnicodeScript.BOPOMOFO);
    private static final Set<UnicodeScript> KOREAN =
            EnumSet.of(UnicodeScript.HAN, UnicodeScript.HANGUL);

    private CharacterNames() {}

    /** The name as names are compared; empty for a name with no letter or digit. */
    public static String key(String name) {
        if (name == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFKD);
        StringBuilder key = new StringBuilder(decomposed.length());
        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!Character.isLetterOrDigit(codePoint)) {
                continue;
            }
            int lower = Character.toLowerCase(codePoint);
            String looksLike = NameLookAlikes.of(lower);
            if (looksLike == null) {
                key.appendCodePoint(lower);
            } else {
                key.append(looksLike);
            }
        }
        return key.toString();
    }

    /**
     * Whether the name's letters are of one script (CHAR-11). Digits,
     * spaces, hyphens and apostrophes go with any script, and Han with
     * Hiragana and Katakana, with Bopomofo, or with Hangul, as each
     * language writes them together.
     */
    public static boolean inOneScript(String name) {
        if (name == null) {
            return true;
        }
        Set<UnicodeScript> scripts = EnumSet.noneOf(UnicodeScript.class);
        for (int offset = 0; offset < name.length();) {
            int codePoint = name.codePointAt(offset);
            offset += Character.charCount(codePoint);
            UnicodeScript script = UnicodeScript.of(codePoint);
            if (script != UnicodeScript.COMMON && script != UnicodeScript.INHERITED
                    && script != UnicodeScript.UNKNOWN) {
                scripts.add(script);
            }
        }
        return scripts.size() <= 1 || JAPANESE.containsAll(scripts)
                || CHINESE.containsAll(scripts) || KOREAN.containsAll(scripts);
    }

    /** Whether the two are one name; two names with nothing to compare never are. */
    public static boolean same(String first, String second) {
        String key = key(first);
        return key.length() > 0 && key.equals(key(second));
    }

    /**
     * The one character in {@code rosters} that goes by the name, as names
     * are compared ({@link #same}); null for none, for a name two
     * characters share, and for one whose roster names another owner.
     */
    public static RoleplayCharacter named(Collection<CharacterRoster> rosters, String name) {
        if (rosters == null || key(name).length() == 0) {
            return null;
        }
        RoleplayCharacter found = null;
        for (CharacterRoster roster : rosters) {
            if (roster == null) {
                continue;
            }
            for (RoleplayCharacter character : roster.getCharacters()) {
                if (character == null || !same(name, character.getName())) {
                    continue;
                }
                if (found != null || !character.getOwnerId().equals(roster.getOwnerId())) {
                    return null;
                }
                found = character;
            }
        }
        return found;
    }

    /** Whether the name is one of the chat's own voices: Server, Client, Narrator, Discord. */
    public static boolean isVoice(String name) {
        for (String voice : VOICES) {
            if (same(voice, name)) {
                return true;
            }
        }
        return false;
    }
}
