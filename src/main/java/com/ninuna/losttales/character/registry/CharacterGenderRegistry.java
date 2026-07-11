package com.ninuna.losttales.character.registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Stable identifiers accepted for the initial character gender field. */
public final class CharacterGenderRegistry {

    public static final String FEMALE = "losttales:female";
    public static final String MALE = "losttales:male";
    public static final String NON_BINARY = "losttales:non_binary";
    public static final String UNSPECIFIED = "losttales:unspecified";

    private static final Set<String> IDENTIFIERS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    FEMALE,
                    MALE,
                    NON_BINARY,
                    UNSPECIFIED
            )));

    private CharacterGenderRegistry() {}

    public static boolean contains(String id) {
        return IDENTIFIERS.contains(normalizeIdentifier(id));
    }

    public static Collection<String> getAll() {
        return IDENTIFIERS;
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
