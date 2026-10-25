package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stable body type identifiers accepted by character creation (CHAR-08). A
 * body type picks a body and its skins and says nothing of who the character
 * is.
 */
public final class CharacterBodyTypeRegistry {

    public static final String FEMININE = "losttales:feminine";
    public static final String MASCULINE = "losttales:masculine";
    public static final String NEUTRAL = "losttales:neutral";

    private static final Set<String> IDENTIFIERS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    FEMININE,
                    MASCULINE,
                    NEUTRAL
            )));

    private CharacterBodyTypeRegistry() {}

    public static boolean contains(String id) {
        return IDENTIFIERS.contains(LostTalesIdentifiers.normalize(id));
    }

    /**
     * Maps a body type to LOTR Legacy's two catalogues of pictures. A race
     * with one body for all draws it from the masculine catalogue, while the
     * character keeps and shows neutral.
     */
    public static String appearanceBodyType(String id) {
        return FEMININE.equals(LostTalesIdentifiers.normalize(id)) ? FEMININE : MASCULINE;
    }
}
