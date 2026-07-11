package com.ninuna.losttales.character.registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Stable roleplay-gender identifiers accepted by character creation. */
public final class CharacterGenderRegistry {

    public static final String FEMALE = "losttales:female";
    public static final String MALE = "losttales:male";
    public static final String NON_BINARY = "losttales:non_binary";

    /** Legacy save identifier removed from character creation in data version 3. */
    public static final String LEGACY_UNSPECIFIED = "losttales:unspecified";

    private static final Set<String> IDENTIFIERS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    FEMALE,
                    MALE,
                    NON_BINARY
            )));

    private CharacterGenderRegistry() {}

    public static boolean contains(String id) {
        return IDENTIFIERS.contains(normalizeIdentifier(id));
    }

    public static Collection<String> getAll() {
        return IDENTIFIERS;
    }

    /**
     * Maps a roleplay gender to LOTR Legacy's two visual catalogues. Races with
     * one unisex body use the masculine implementation internally, while still
     * storing and displaying the roleplay value as non-binary.
     */
    public static String appearanceGender(String id) {
        return FEMALE.equals(normalizeIdentifier(id)) ? FEMALE : MALE;
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
