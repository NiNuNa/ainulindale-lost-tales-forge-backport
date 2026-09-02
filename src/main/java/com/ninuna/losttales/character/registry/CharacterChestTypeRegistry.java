package com.ninuna.losttales.character.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Stable chest type identifiers: the shape and size of the feminine chest a
 * character is drawn with, or none. Like the arm width it is a choice of
 * its own, stored next to sex; sex only picks the default.
 */
public final class CharacterChestTypeRegistry {

    /** The shapes a chest type can take; the client owns the geometry. */
    public enum Shape {
        /** No chest geometry. */
        NONE,
        /** LOTR Legacy's single flat cuboid. */
        CLASSIC,
        /** Two rounded boxes hung from the torso, textured from the shirt. */
        ROUNDED,
        /** One soft mound sloping down from the chest. */
        FULL
    }

    public static final String NONE = "losttales:none";
    public static final String CLASSIC = "losttales:classic";
    public static final String ROUNDED_SMALL = "losttales:rounded_small";
    public static final String ROUNDED_MEDIUM = "losttales:rounded_medium";
    public static final String ROUNDED_LARGE = "losttales:rounded_large";
    public static final String FULL_SMALL = "losttales:full_small";
    public static final String FULL_MEDIUM = "losttales:full_medium";
    public static final String FULL_LARGE = "losttales:full_large";

    private static final Map<String, CharacterChestTypeDefinition> DEFINITIONS;

    static {
        LinkedHashMap<String, CharacterChestTypeDefinition> definitions =
                new LinkedHashMap<String, CharacterChestTypeDefinition>();
        register(definitions, new CharacterChestTypeDefinition(NONE, Shape.NONE, 0.0F));
        register(definitions, new CharacterChestTypeDefinition(CLASSIC, Shape.CLASSIC, 1.0F));
        register(definitions, new CharacterChestTypeDefinition(ROUNDED_SMALL, Shape.ROUNDED, 0.6F));
        register(definitions, new CharacterChestTypeDefinition(ROUNDED_MEDIUM, Shape.ROUNDED, 0.85F));
        register(definitions, new CharacterChestTypeDefinition(ROUNDED_LARGE, Shape.ROUNDED, 1.1F));
        register(definitions, new CharacterChestTypeDefinition(FULL_SMALL, Shape.FULL, 0.35F));
        register(definitions, new CharacterChestTypeDefinition(FULL_MEDIUM, Shape.FULL, 0.65F));
        register(definitions, new CharacterChestTypeDefinition(FULL_LARGE, Shape.FULL, 1.0F));
        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    private CharacterChestTypeRegistry() {}

    public static CharacterChestTypeDefinition get(String id) {
        return DEFINITIONS.get(normalizeIdentifier(id));
    }

    public static boolean contains(String id) {
        return DEFINITIONS.containsKey(normalizeIdentifier(id));
    }

    public static Collection<CharacterChestTypeDefinition> getAll() {
        return DEFINITIONS.values();
    }

    /** The chest a creator pre-selects for a sex: rounded medium for female, none otherwise. */
    public static String defaultFor(String genderId) {
        return CharacterGenderRegistry.FEMALE.equals(
                CharacterGenderRegistry.normalizeIdentifier(genderId)) ? ROUNDED_MEDIUM : NONE;
    }

    /** A registered identifier, or none for anything unknown. */
    public static String normalizeOrNone(String id) {
        String normalized = normalizeIdentifier(id);
        return DEFINITIONS.containsKey(normalized) ? normalized : NONE;
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void register(Map<String, CharacterChestTypeDefinition> definitions,
                                 CharacterChestTypeDefinition definition) {
        if (definitions.put(definition.getId(), definition) != null) {
            throw new IllegalStateException("Duplicate chest type ID: " + definition.getId());
        }
    }
}
