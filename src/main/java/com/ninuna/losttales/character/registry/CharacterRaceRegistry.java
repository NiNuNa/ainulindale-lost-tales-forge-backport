package com.ninuna.losttales.character.registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

/**
 * Stable built-in race identifiers for character creation.
 *
 * Faction compatibility is intentionally conservative. The registry can be
 * expanded later without changing saved character records.
 */
public final class CharacterRaceRegistry {

    public static final String HUMAN = "losttales:human";
    public static final String ELF = "losttales:elf";
    public static final String DWARF = "losttales:dwarf";
    public static final String HOBBIT = "losttales:hobbit";
    public static final String ORC = "losttales:orc";
    public static final String TROLL = "losttales:troll";

    private static final Map<String, CharacterRaceDefinition> DEFINITIONS;

    static {
        LinkedHashMap<String, CharacterRaceDefinition> definitions =
                new LinkedHashMap<String, CharacterRaceDefinition>();
        register(definitions, new CharacterRaceDefinition(
                HUMAN,
                EnumSet.of(CharacterFactionCategory.HUMAN),
                Collections.<String>emptySet()));
        register(definitions, new CharacterRaceDefinition(
                ELF,
                EnumSet.of(CharacterFactionCategory.ELF),
                Collections.<String>emptySet()));
        register(definitions, new CharacterRaceDefinition(
                DWARF,
                EnumSet.of(CharacterFactionCategory.DWARF),
                Collections.<String>emptySet()));
        register(definitions, new CharacterRaceDefinition(
                HOBBIT,
                Collections.<CharacterFactionCategory>emptySet(),
                new LinkedHashSet<String>(Arrays.asList("lotr:hobbit", "lotr:bree"))));
        register(definitions, new CharacterRaceDefinition(
                ORC,
                EnumSet.of(CharacterFactionCategory.ORC),
                Collections.<String>emptySet()));
        register(definitions, new CharacterRaceDefinition(
                TROLL,
                EnumSet.of(CharacterFactionCategory.TROLL),
                Collections.<String>emptySet()));
        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    private CharacterRaceRegistry() {}

    public static CharacterRaceDefinition get(String id) {
        return id == null ? null : DEFINITIONS.get(normalizeIdentifier(id));
    }

    public static Collection<CharacterRaceDefinition> getAll() {
        return DEFINITIONS.values();
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void register(Map<String, CharacterRaceDefinition> definitions,
                                 CharacterRaceDefinition definition) {
        definitions.put(definition.getId(), definition);
    }
}
