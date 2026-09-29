package com.ninuna.losttales.character.validation;

import java.text.Normalizer;

/**
 * When two names are the same name, for every check that asks: letters and
 * digits alone, lower case, accents taken off. So *Aldric*, *aldric* and
 * *Al-dric* are one name, and so are *Éomer* and *Eomer*. A few names belong
 * to the chat's own voices and no character may take them.
 */
public final class CharacterNames {
    private static final String[] VOICES = { "Server", "Client", "Narrator", "Discord" };

    private CharacterNames() {}

    /** The name as names are compared; empty for a name with no letter or digit. */
    public static String key(String name) {
        if (name == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(name.trim(), Normalizer.Form.NFKD);
        StringBuilder key = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length(); index++) {
            char character = decomposed.charAt(index);
            if (Character.isLetterOrDigit(character)) {
                key.append(Character.toLowerCase(character));
            }
        }
        return key.toString();
    }

    /** Whether the two are one name; two names with nothing to compare never are. */
    public static boolean same(String first, String second) {
        String key = key(first);
        return key.length() > 0 && key.equals(key(second));
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
