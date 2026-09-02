package com.ninuna.losttales.character.registry;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Stable body model identifiers and the default model of every race.
 *
 * Skins name the model that draws them, so the model a player renders with
 * follows the chosen skin rather than the race directly. The LOTR identifiers
 * are the ones lore character definitions already use for {@code modelId};
 * every body behind them is Lost Tales geometry that reads LOTR's layouts.
 */
public final class CharacterBodyModelRegistry {

    public static final String LOTR_HUMAN = "lotr:human";
    public static final String LOTR_ELF = "lotr:elf";
    public static final String LOTR_DWARF = "lotr:dwarf";
    public static final String LOTR_HOBBIT = "lotr:hobbit";
    public static final String LOTR_ORC = "lotr:orc";
    public static final String LOTR_URUK = "lotr:uruk";
    public static final String LOTR_HALF_TROLL = "lotr:half_troll";

    /**
     * The plain player body: a human without race geometry, drawn with the
     * account skin. Players without a character use it.
     */
    public static final String LOSTTALES_PLAYER = "losttales:player";

    private static final Map<String, CharacterBodyModelDefinition> DEFINITIONS;
    private static final Map<String, String> DEFAULT_MODEL_BY_RACE;
    /** Models a race may use besides its default. */
    private static final Map<String, Set<String>> EXTRA_MODELS_BY_RACE;

    static {
        LinkedHashMap<String, CharacterBodyModelDefinition> definitions =
                new LinkedHashMap<String, CharacterBodyModelDefinition>();
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_HUMAN, CharacterSkinLayout.LOTR_64X64, true, true, true));
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_ELF, CharacterSkinLayout.LOTR_64X64, true, true, true));
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_DWARF, CharacterSkinLayout.LOTR_64X64, true, true, true));
        // The hobbit's arms hang lower than vanilla's.
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_HOBBIT, CharacterSkinLayout.LOTR_64X64, true, true, false));
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_ORC, CharacterSkinLayout.LOTR_64X32, false, true, true));
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_URUK, CharacterSkinLayout.LOTR_64X32, false, true, true));
        // The half-troll's shoulders sit far outside vanilla's.
        register(definitions, new CharacterBodyModelDefinition(
                LOTR_HALF_TROLL, CharacterSkinLayout.LOTR_64X64, false, true, false));
        register(definitions, new CharacterBodyModelDefinition(
                LOSTTALES_PLAYER, CharacterSkinLayout.MINECRAFT_64X64, true, true, true));
        DEFINITIONS = Collections.unmodifiableMap(definitions);

        LinkedHashMap<String, String> defaults = new LinkedHashMap<String, String>();
        defaults.put(CharacterRaceRegistry.HUMAN, LOTR_HUMAN);
        defaults.put(CharacterRaceRegistry.ELF, LOTR_ELF);
        defaults.put(CharacterRaceRegistry.DWARF, LOTR_DWARF);
        defaults.put(CharacterRaceRegistry.HOBBIT, LOTR_HOBBIT);
        defaults.put(CharacterRaceRegistry.ORC, LOTR_ORC);
        defaults.put(CharacterRaceRegistry.URUK, LOTR_URUK);
        defaults.put(CharacterRaceRegistry.HALF_TROLL, LOTR_HALF_TROLL);
        DEFAULT_MODEL_BY_RACE = Collections.unmodifiableMap(defaults);

        LinkedHashMap<String, Set<String>> extras = new LinkedHashMap<String, Set<String>>();
        extras.put(CharacterRaceRegistry.HUMAN, Collections.singleton(LOSTTALES_PLAYER));
        EXTRA_MODELS_BY_RACE = Collections.unmodifiableMap(extras);
    }

    private CharacterBodyModelRegistry() {}

    public static CharacterBodyModelDefinition get(String id) {
        return DEFINITIONS.get(normalizeIdentifier(id));
    }

    public static Collection<CharacterBodyModelDefinition> getAll() {
        return DEFINITIONS.values();
    }

    /** The model a race's catalogue skins draw with; empty for an unknown race. */
    public static String getDefaultModelId(String raceId) {
        String modelId = DEFAULT_MODEL_BY_RACE.get(
                CharacterRaceRegistry.canonicalizeIdentifier(raceId));
        return modelId == null ? "" : modelId;
    }

    /** True when the model is one a character of that race may be drawn with. */
    public static boolean isCompatible(String raceId, String modelId) {
        String normalized = normalizeIdentifier(modelId);
        if (normalized.length() == 0) {
            return false;
        }
        if (normalized.equals(getDefaultModelId(raceId))) {
            return true;
        }
        Set<String> extras = EXTRA_MODELS_BY_RACE.get(
                CharacterRaceRegistry.canonicalizeIdentifier(raceId));
        return extras != null && extras.contains(normalized);
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void register(Map<String, CharacterBodyModelDefinition> definitions,
                                 CharacterBodyModelDefinition definition) {
        String id = definition.getId();
        if (!id.equals(normalizeIdentifier(id))) {
            throw new IllegalArgumentException("Body model ID is not canonical: " + id);
        }
        if (definitions.containsKey(id)) {
            throw new IllegalStateException("Duplicate body model ID: " + id);
        }
        definitions.put(id, definition);
    }
}
