package com.ninuna.losttales.character.registry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Server-valid catalogue of player-compatible skins supplied by LOTR Legacy.
 *
 * Lost Tales stores only stable identifiers and LOTR resource locations. The
 * actual textures remain inside the required LOTR Legacy installation and are
 * never copied into this add-on.
 */
public final class CharacterSkinRegistry {

    private static final Map<String, CharacterSkinDefinition> DEFINITIONS;
    private static final Map<String, List<CharacterSkinDefinition>> BY_RACE;

    static {
        LinkedHashMap<String, CharacterSkinDefinition> definitions =
                new LinkedHashMap<String, CharacterSkinDefinition>();

        // Adult human body skins. These all use LOTRModelHuman.
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_bree", "mob/bree/bree", 30, 9);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_dale", "mob/dale/dale", 3, 2);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_dorwinion", "mob/dorwinion/dorwinion", 4, 4);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_dunlending", "mob/dunland/dunlending", 4, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_gondor", "mob/gondor/gondor", 10, 14);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_hillman", "mob/hillman/hillman", 3, 4);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_moredain", "mob/moredain/moredain", 5, 4);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_haradrim", "mob/nearHarad/haradrim", 5, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_harnedor", "mob/nearHarad/harnedor", 5, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_nomad", "mob/nearHarad/nomad", 5, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_nurn_slave", "mob/nurn/slave", 4, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_ranger", "mob/ranger/ranger", 5, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_easterling", "mob/rhun/easterling", 5, 5);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_rohan", "mob/rohan/rohan", 6, 7);
        registerGenderedGroup(definitions, CharacterRaceRegistry.HUMAN,
                "human_tauredain", "mob/tauredain/tauredain", 4, 3);

        registerGenderedGroup(definitions, CharacterRaceRegistry.ELF,
                "elf_high", "mob/elf/highElf", 18, 11);
        registerGenderedGroup(definitions, CharacterRaceRegistry.ELF,
                "elf_galadhrim", "mob/elf/galadhrim", 4, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.ELF,
                "elf_wood", "mob/elf/woodElf", 4, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.ELF,
                "elf_dorwinion", "mob/elf/dorwinion", 3, 3);

        registerGenderedGroup(definitions, CharacterRaceRegistry.DWARF,
                "dwarf_erebor", "mob/dwarf/dwarf", 3, 3);
        registerGenderedGroup(definitions, CharacterRaceRegistry.DWARF,
                "dwarf_blue_mountains", "mob/dwarf/blueMountains", 3, 3);

        registerGenderedGroup(definitions, CharacterRaceRegistry.HOBBIT,
                "hobbit_shire", "mob/hobbit/hobbit", 13, 13);

        // LOTR Legacy supplies one body catalogue for these races. Lost Tales
        // exposes Non-binary as their sole roleplay option and uses this unisex
        // skin collection.
        registerUnisexGroup(definitions, CharacterRaceRegistry.ORC,
                "orc", "mob/orc/orc", 8);
        registerUnisexGroup(definitions, CharacterRaceRegistry.URUK,
                "uruk_hai", "mob/orc/urukHai", 3);
        registerUnisexGroup(definitions, CharacterRaceRegistry.URUK,
                "black_uruk", "mob/orc/blackUruk", 3);
        registerUnisexGroup(definitions, CharacterRaceRegistry.HALF_TROLL,
                "half_troll", "mob/halfTroll/halfTroll", 3);

        DEFINITIONS = Collections.unmodifiableMap(definitions);

        LinkedHashMap<String, List<CharacterSkinDefinition>> byRace =
                new LinkedHashMap<String, List<CharacterSkinDefinition>>();
        for (CharacterSkinDefinition definition : definitions.values()) {
            List<CharacterSkinDefinition> raceSkins = byRace.get(definition.getRaceId());
            if (raceSkins == null) {
                raceSkins = new ArrayList<CharacterSkinDefinition>();
                byRace.put(definition.getRaceId(), raceSkins);
            }
            raceSkins.add(definition);
        }
        for (Map.Entry<String, List<CharacterSkinDefinition>> entry : byRace.entrySet()) {
            entry.setValue(Collections.unmodifiableList(entry.getValue()));
        }
        BY_RACE = Collections.unmodifiableMap(byRace);
    }

    private CharacterSkinRegistry() {}

    public static CharacterSkinDefinition get(String id) {
        return id == null ? null : DEFINITIONS.get(normalizeIdentifier(id));
    }

    public static Collection<CharacterSkinDefinition> getAll() {
        return DEFINITIONS.values();
    }

    public static List<CharacterSkinDefinition> getCompatibleSkins(
            String raceId, String genderId) {
        String canonicalRaceId = CharacterRaceRegistry.canonicalizeIdentifier(raceId);
        List<CharacterSkinDefinition> raceSkins = BY_RACE.get(canonicalRaceId);
        if (raceSkins == null || raceSkins.isEmpty()) {
            return Collections.emptyList();
        }
        ArrayList<CharacterSkinDefinition> compatible =
                new ArrayList<CharacterSkinDefinition>();
        for (CharacterSkinDefinition definition : raceSkins) {
            if (definition.isCompatibleWith(canonicalRaceId, genderId)) {
                compatible.add(definition);
            }
        }
        return Collections.unmodifiableList(compatible);
    }

    public static boolean isCompatible(String skinId, String raceId, String genderId) {
        CharacterSkinDefinition definition = get(skinId);
        return definition != null && definition.isCompatibleWith(raceId, genderId);
    }

    /**
     * Returns a deterministic fallback so an old character does not receive a
     * different appearance each time its record is loaded.
     */
    public static String getDefaultSkinId(String raceId, String genderId, UUID seed) {
        List<CharacterSkinDefinition> skins = getCompatibleSkins(raceId, genderId);
        if (skins.isEmpty()) {
            return "";
        }
        int hash = seed == null ? 0 : seed.hashCode();
        int index = (hash & Integer.MAX_VALUE) % skins.size();
        return skins.get(index).getId();
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void registerGenderedGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String displayGroupId, String resourceBase,
            int maleCount, int femaleCount) {
        registerGroup(definitions, raceId, CharacterGenderRegistry.MALE,
                displayGroupId, resourceBase + "_male", maleCount);
        registerGroup(definitions, raceId, CharacterGenderRegistry.FEMALE,
                displayGroupId, resourceBase + "_female", femaleCount);
    }

    private static void registerUnisexGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String displayGroupId, String resourceBase,
            int count) {
        registerGroup(definitions, raceId, "", displayGroupId, resourceBase, count);
    }

    private static void registerGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String genderId, String displayGroupId,
            String resourceBase, int count) {
        String genderSuffix = genderId.length() == 0
                ? "" : "_" + stripNamespace(genderId);
        for (int index = 0; index < count; index++) {
            String id = "losttales:" + displayGroupId + genderSuffix + "_" + index;
            CharacterSkinDefinition definition = new CharacterSkinDefinition(
                    id,
                    raceId,
                    genderId,
                    displayGroupId,
                    index,
                    "lotr:" + resourceBase + "/" + index + ".png"
            );
            CharacterSkinDefinition previous = definitions.put(definition.getId(), definition);
            if (previous != null) {
                throw new IllegalStateException(
                        "duplicate character skin id " + definition.getId());
            }
        }
    }

    private static String stripNamespace(String identifier) {
        int colon = identifier.indexOf(':');
        return colon < 0 ? identifier : identifier.substring(colon + 1);
    }
}
