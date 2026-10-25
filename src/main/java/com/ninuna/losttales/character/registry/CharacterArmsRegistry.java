package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Stable arms identifiers: the arm width of a body model that offers both.
 * The arms are a choice of their own, stored next to the body type; the body
 * type only picks the first one.
 */
public final class CharacterArmsRegistry {

    /** Four-pixel arms, Minecraft's classic proportions. */
    public static final String BROAD = "losttales:broad";
    /** Three-pixel arms. */
    public static final String SLENDER = "losttales:slender";

    private static final Set<String> IDENTIFIERS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(BROAD, SLENDER)));

    private CharacterArmsRegistry() {}

    public static boolean contains(String id) {
        return IDENTIFIERS.contains(LostTalesIdentifiers.normalize(id));
    }

    public static Collection<String> getAll() {
        return IDENTIFIERS;
    }

    /** The arms a creator picks first for a body type: slender for feminine, broad otherwise. */
    public static String defaultFor(String bodyTypeId) {
        return CharacterBodyTypeRegistry.FEMININE.equals(
                LostTalesIdentifiers.normalize(bodyTypeId)) ? SLENDER : BROAD;
    }

    /** A registered identifier, or broad for anything unknown. */
    public static String normalizeOrBroad(String id) {
        String normalized = LostTalesIdentifiers.normalize(id);
        return IDENTIFIERS.contains(normalized) ? normalized : BROAD;
    }
}
