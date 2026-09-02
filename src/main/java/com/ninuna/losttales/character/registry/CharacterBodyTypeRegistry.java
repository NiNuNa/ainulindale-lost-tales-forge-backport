package com.ninuna.losttales.character.registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Stable body type identifiers: the arm width of a body model that supports
 * both. Body type is a choice of its own, stored next to sex rather than
 * derived from it; sex only picks the default.
 */
public final class CharacterBodyTypeRegistry {

    /** Four-pixel arms, Minecraft's classic proportions. */
    public static final String WIDE = "losttales:wide";
    /** Three-pixel arms. */
    public static final String SLIM = "losttales:slim";

    private static final Set<String> IDENTIFIERS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(WIDE, SLIM)));

    private CharacterBodyTypeRegistry() {}

    public static boolean contains(String id) {
        return IDENTIFIERS.contains(normalizeIdentifier(id));
    }

    public static Collection<String> getAll() {
        return IDENTIFIERS;
    }

    /** The body type a creator pre-selects for a sex: slim for female, wide otherwise. */
    public static String defaultFor(String genderId) {
        return CharacterGenderRegistry.FEMALE.equals(
                CharacterGenderRegistry.normalizeIdentifier(genderId)) ? SLIM : WIDE;
    }

    /** A registered identifier, or wide for anything unknown. */
    public static String normalizeOrWide(String id) {
        String normalized = normalizeIdentifier(id);
        return IDENTIFIERS.contains(normalized) ? normalized : WIDE;
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
