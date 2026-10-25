package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.util.LostTalesIdentifiers;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable body model identifiers; a race's default model is its body's
 * ({@link CharacterBody}).
 *
 * Skins name the model that draws them, so the model a player renders with
 * follows the chosen skin rather than the race directly. The LOTR identifiers
 * name the bodies; every body behind them is Lost Tales geometry that reads
 * LOTR's layouts.
 */
public final class CharacterBodyModelRegistry {

    public static final String LOTR_HUMAN = "lotr:human";
    public static final String LOTR_ELF = "lotr:elf";
    public static final String LOTR_DWARF = "lotr:dwarf";
    public static final String LOTR_HOBBIT = "lotr:hobbit";
    public static final String LOTR_ORC = "lotr:orc";
    public static final String LOTR_URUK = "lotr:uruk";

    /**
     * The plain player body: a human without race geometry, drawn with the
     * account skin. Players without a character use it.
     */
    public static final String LOSTTALES_PLAYER = "losttales:player";

    private static final Map<String, CharacterBodyModelDefinition> DEFINITIONS;

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
        register(definitions, new CharacterBodyModelDefinition(
                LOSTTALES_PLAYER, CharacterSkinLayout.MINECRAFT_64X64, true, true, true));
        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    private CharacterBodyModelRegistry() {}

    public static CharacterBodyModelDefinition get(String id) {
        return DEFINITIONS.get(LostTalesIdentifiers.normalize(id));
    }

    /** The model a race's catalogue skins draw with, its body's; empty for an unknown race. */
    public static String getDefaultModelId(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        return race == null ? "" : race.getBody().getModelId();
    }

    /**
     * True when the model is one a character of that race may be drawn
     * with: its body's, or the plain player body on the human body.
     */
    public static boolean isCompatible(String raceId, String modelId) {
        String normalized = LostTalesIdentifiers.normalize(modelId);
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (normalized.length() == 0 || race == null) {
            return false;
        }
        return normalized.equals(race.getBody().getModelId())
                || normalized.equals(race.getBody().getAccountSkinModelId());
    }

    private static void register(Map<String, CharacterBodyModelDefinition> definitions,
                                 CharacterBodyModelDefinition definition) {
        String id = definition.getId();
        if (!id.equals(LostTalesIdentifiers.normalize(id))) {
            throw new IllegalArgumentException("Body model ID is not canonical: " + id);
        }
        if (definitions.containsKey(id)) {
            throw new IllegalStateException("Duplicate body model ID: " + id);
        }
        definitions.put(id, definition);
    }
}
