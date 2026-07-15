package com.ninuna.losttales.character.registry;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Stable built-in race identifiers and common metadata. */
public final class CharacterRaceRegistry {

    /**
     * Eyes are measured from the feet and track the race's physical height.
     * Keeping the ratio and crouch drop centralized prevents a future model
     * size change from silently leaving the camera at another race's height.
     */
    private static final float STANDING_EYE_HEIGHT_RATIO = 0.85F;
    private static final float SNEAKING_EYE_HEIGHT_DROP = 0.08F;

    public static final String HUMAN = "losttales:human";
    public static final String ELF = "losttales:elf";
    public static final String DWARF = "losttales:dwarf";
    public static final String HOBBIT = "losttales:hobbit";
    public static final String ORC = "losttales:orc";
    public static final String URUK = "losttales:uruk";
    public static final String HALF_TROLL = "losttales:half_troll";

    /** Legacy save identifier. Full trolls are no longer playable. */
    public static final String LEGACY_TROLL = "losttales:troll";

    private static final Set<String> MALE_AND_FEMALE = identifiers(
            CharacterGenderRegistry.MALE,
            CharacterGenderRegistry.FEMALE);
    private static final Set<String> NON_BINARY_ONLY = identifiers(
            CharacterGenderRegistry.NON_BINARY);
    private static final Set<String> NO_FACTIONS = Collections.emptySet();

    private static final Map<String, CharacterRaceDefinition> DEFINITIONS;

    static {
        LinkedHashMap<String, CharacterRaceDefinition> definitions =
                new LinkedHashMap<String, CharacterRaceDefinition>();

        register(definitions, definition(
                HUMAN, "lotr:bree_man",
                EnumSet.of(CharacterFactionCategory.HUMAN),
                NO_FACTIONS, NO_FACTIONS, MALE_AND_FEMALE,
                0.60F, 1.80F, standingEyeHeight(1.80F), sneakingEyeHeight(1.80F),
                20.0D, 1.0D, 2.0D,
                1.0F, 1.0F, 0));
        register(definitions, definition(
                ELF, "lotr:high_elf",
                EnumSet.of(CharacterFactionCategory.ELF),
                NO_FACTIONS, NO_FACTIONS, MALE_AND_FEMALE,
                0.60F, 1.80F, standingEyeHeight(1.80F), sneakingEyeHeight(1.80F),
                20.0D, 1.0D, 2.0D,
                1.0F, 0.96F, 0));
        register(definitions, definition(
                DWARF, "lotr:dwarf",
                EnumSet.of(CharacterFactionCategory.DWARF),
                NO_FACTIONS, NO_FACTIONS, MALE_AND_FEMALE,
                0.50F, 1.50F, standingEyeHeight(1.50F), sneakingEyeHeight(1.50F),
                20.0D, 0.9D, 3.0D,
                0.8125F, 1.12F, -2));
        register(definitions, definition(
                HOBBIT, "lotr:hobbit",
                Collections.<CharacterFactionCategory>emptySet(),
                identifiers("lotr:hobbit", "lotr:bree"),
                NO_FACTIONS, MALE_AND_FEMALE,
                0.45F, 1.20F, standingEyeHeight(1.20F), sneakingEyeHeight(1.20F),
                16.0D, 1.0D, 2.0D,
                0.75F, 1.34F, -5));
        register(definitions, definition(
                ORC, "lotr:mordor_orc",
                EnumSet.of(CharacterFactionCategory.ORC),
                NO_FACTIONS, NO_FACTIONS, NON_BINARY_ONLY,
                0.50F, 1.55F, standingEyeHeight(1.55F), sneakingEyeHeight(1.55F),
                20.0D, 1.0D, 3.0D,
                0.85F, 1.08F, -2));
        register(definitions, definition(
                URUK, "lotr:uruk_hai",
                EnumSet.of(CharacterFactionCategory.ORC),
                NO_FACTIONS, NO_FACTIONS, NON_BINARY_ONLY,
                0.60F, 1.80F, standingEyeHeight(1.80F), sneakingEyeHeight(1.80F),
                24.0D, 1.05D, 4.0D,
                1.0F, 1.05F, -2));
        register(definitions, definition(
                HALF_TROLL, "lotr:half_troll",
                EnumSet.of(CharacterFactionCategory.TROLL),
                NO_FACTIONS, NO_FACTIONS, NON_BINARY_ONLY,
                1.00F, 2.40F, standingEyeHeight(2.40F), sneakingEyeHeight(2.40F),
                40.0D, 0.9D, 6.0D,
                1.0F, 0.72F, 5));

        DEFINITIONS = Collections.unmodifiableMap(definitions);
    }

    private CharacterRaceRegistry() {}

    public static CharacterRaceDefinition get(String id) {
        return DEFINITIONS.get(canonicalizeIdentifier(id));
    }

    public static Collection<CharacterRaceDefinition> getAll() {
        return DEFINITIONS.values();
    }

    public static boolean supportsGenderedModels(String raceId) {
        CharacterRaceDefinition definition = get(raceId);
        return definition != null && definition.hasGenderedModels();
    }

    /**
     * Repairs legacy or incompatible gender values deterministically. Gendered
     * races fall back to male; unisex races always use non-binary.
     */
    public static String normalizeGenderForRace(String raceId, String genderId) {
        CharacterRaceDefinition definition = get(raceId);
        if (definition == null) {
            return "";
        }
        String normalizedGender = CharacterGenderRegistry.normalizeIdentifier(genderId);
        if (definition.isGenderAllowed(normalizedGender)) {
            return normalizedGender;
        }
        return definition.hasGenderedModels()
                ? CharacterGenderRegistry.MALE
                : CharacterGenderRegistry.NON_BINARY;
    }

    public static String canonicalizeIdentifier(String id) {
        String normalized = normalizeIdentifier(id);
        return LEGACY_TROLL.equals(normalized) ? HALF_TROLL : normalized;
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static CharacterRaceDefinition definition(
            String id,
            String lotrRaceAssociation,
            Set<CharacterFactionCategory> categories,
            Set<String> allowedFactions,
            Set<String> deniedFactions,
            Set<String> genders,
            float width,
            float height,
            float standingEyeHeight,
            float sneakingEyeHeight,
            double maxHealth,
            double movementSpeedMultiplier,
            double attackDamage,
            float rendererScale,
            float previewScale,
            int previewVerticalOffset) {
        return new CharacterRaceDefinition(
                id,
                lotrRaceAssociation,
                categories,
                allowedFactions,
                deniedFactions,
                genders,
                width,
                height,
                standingEyeHeight,
                sneakingEyeHeight,
                maxHealth,
                movementSpeedMultiplier,
                attackDamage,
                rendererScale,
                previewScale,
                previewVerticalOffset);
    }

    private static Set<String> identifiers(String... values) {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(Arrays.asList(values)));
    }

    private static float standingEyeHeight(float bodyHeight) {
        return bodyHeight * STANDING_EYE_HEIGHT_RATIO;
    }

    private static float sneakingEyeHeight(float bodyHeight) {
        return standingEyeHeight(bodyHeight) - SNEAKING_EYE_HEIGHT_DROP;
    }

    private static void register(Map<String, CharacterRaceDefinition> definitions,
                                 CharacterRaceDefinition definition) {
        String id = canonicalizeIdentifier(definition.getId());
        if (!id.equals(definition.getId())) {
            throw new IllegalArgumentException("Race ID is not canonical: " + definition.getId());
        }
        if (definitions.containsKey(id)) {
            throw new IllegalStateException("Duplicate race ID: " + id);
        }
        definitions.put(id, definition);
    }
}
