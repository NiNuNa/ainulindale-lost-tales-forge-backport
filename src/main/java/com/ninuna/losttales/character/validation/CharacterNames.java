package com.ninuna.losttales.character.validation;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import java.text.Normalizer;
import java.util.Collection;

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
