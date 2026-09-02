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
 * Server-valid catalogue of player-compatible skins.
 *
 * Most skins are supplied by LOTR Legacy: Lost Tales stores only stable
 * identifiers and LOTR resource locations, and the textures stay inside the
 * required LOTR installation. Skins drawn for Lost Tales itself ship under
 * {@code assets/losttales/textures/skins/} and register through the same
 * catalogue, so the creation screen cycles both kinds in one list and the
 * server validates both the same way.
 */
public final class CharacterSkinRegistry {

    /** Prefix of every texture location that points into LOTR Legacy. */
    public static final String LOTR_TEXTURE_ROOT = "lotr:";
    /** Prefix of every texture location bundled with Lost Tales. */
    public static final String BUNDLED_TEXTURE_ROOT = "losttales:textures/skins/";
    /**
     * The player's own Minecraft account skin, drawn on the Lost Tales player
     * body. Every client fetches the texture for itself; the placeholder
     * location below is never loaded.
     */
    public static final String ACCOUNT_SKIN_ID = "losttales:account_skin";
    public static final String ACCOUNT_SKIN_GROUP = "account";
    private static final String ACCOUNT_TEXTURE_PLACEHOLDER = "losttales:account_skin";

    private static final Map<String, CharacterSkinDefinition> DEFINITIONS;
    private static final Map<String, List<CharacterSkinDefinition>> BY_RACE;

    static {
        LinkedHashMap<String, CharacterSkinDefinition> definitions =
                new LinkedHashMap<String, CharacterSkinDefinition>();

        // The account skin comes first in every race's list so a new
        // character lands on it. The half-troll has none: its body has no
        // sensible mapping onto a Minecraft skin.
        registerAccountSkin(definitions, CharacterRaceRegistry.HUMAN);
        registerAccountSkin(definitions, CharacterRaceRegistry.ELF);
        registerAccountSkin(definitions, CharacterRaceRegistry.DWARF);
        registerAccountSkin(definitions, CharacterRaceRegistry.HOBBIT);
        registerAccountSkin(definitions, CharacterRaceRegistry.ORC);
        registerAccountSkin(definitions, CharacterRaceRegistry.URUK);

        // Adult human body skins, painted for LOTR's human body.
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
        // Drawn for Lost Tales: a 64x64 skin in the LOTR layout, rendered
        // through the same dwarf model as the LOTR skins above.
        registerBundledGroup(definitions, CharacterRaceRegistry.DWARF,
                CharacterGenderRegistry.MALE, "dwarf_exiled", "dwarf/exiled_male", 1);

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
     * different appearance each time its record is loaded. The account skin
     * is never chosen for a record that did not ask for it.
     */
    public static String getDefaultSkinId(String raceId, String genderId, UUID seed) {
        ArrayList<CharacterSkinDefinition> skins = new ArrayList<CharacterSkinDefinition>();
        for (CharacterSkinDefinition definition : getCompatibleSkins(raceId, genderId)) {
            if (!definition.isAccountSkin()) {
                skins.add(definition);
            }
        }
        if (skins.isEmpty()) {
            return "";
        }
        int hash = seed == null ? 0 : seed.hashCode();
        int index = (hash & Integer.MAX_VALUE) % skins.size();
        return skins.get(index).getId();
    }

    /** True when the identifier names the player's own account skin. */
    public static boolean isAccountSkin(String skinId) {
        CharacterSkinDefinition definition = get(skinId);
        return definition != null && definition.isAccountSkin();
    }

    public static String normalizeIdentifier(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private static void registerGenderedGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String displayGroupId, String resourceBase,
            int maleCount, int femaleCount) {
        registerGroup(definitions, raceId, CharacterGenderRegistry.MALE,
                displayGroupId, LOTR_TEXTURE_ROOT + resourceBase + "_male", maleCount);
        registerGroup(definitions, raceId, CharacterGenderRegistry.FEMALE,
                displayGroupId, LOTR_TEXTURE_ROOT + resourceBase + "_female", femaleCount);
    }

    private static void registerUnisexGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String displayGroupId, String resourceBase,
            int count) {
        registerGroup(definitions, raceId, "", displayGroupId,
                LOTR_TEXTURE_ROOT + resourceBase, count);
    }

    /**
     * Registers skins bundled with Lost Tales. {@code resourceBase} is the
     * folder under {@code assets/losttales/textures/skins/} that holds
     * {@code 0.png}, {@code 1.png}, ... for the group.
     */
    private static void registerBundledGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String genderId, String displayGroupId,
            String resourceBase, int count) {
        registerGroup(definitions, raceId, genderId, displayGroupId,
                BUNDLED_TEXTURE_ROOT + resourceBase, count);
    }

    /**
     * Registers the account skin for a race: unisex, in Minecraft's layout,
     * drawn by the race's own body (the plain player body for humans) with
     * its race geometry sampled from the skin.
     */
    private static void registerAccountSkin(
            Map<String, CharacterSkinDefinition> definitions, String raceId) {
        boolean human = raceId.equals(CharacterRaceRegistry.HUMAN);
        String id = human
                ? ACCOUNT_SKIN_ID
                : ACCOUNT_SKIN_ID + "_" + stripNamespace(raceId);
        CharacterSkinDefinition definition = new CharacterSkinDefinition(
                id, raceId, "", ACCOUNT_SKIN_GROUP, 0,
                ACCOUNT_TEXTURE_PLACEHOLDER,
                human ? CharacterBodyModelRegistry.LOSTTALES_PLAYER
                        : CharacterBodyModelRegistry.getDefaultModelId(raceId),
                CharacterSkinLayout.MINECRAFT_64X64, true);
        if (definitions.put(definition.getId(), definition) != null) {
            throw new IllegalStateException("duplicate character skin id " + id);
        }
    }

    private static void registerGroup(
            Map<String, CharacterSkinDefinition> definitions,
            String raceId, String genderId, String displayGroupId,
            String textureBase, int count) {
        String genderSuffix = genderId.length() == 0
                ? "" : "_" + stripNamespace(genderId);
        // Catalogue skins are painted for the race's default body, in the
        // layout that body reads.
        String modelId = CharacterBodyModelRegistry.getDefaultModelId(raceId);
        CharacterBodyModelDefinition model = CharacterBodyModelRegistry.get(modelId);
        if (model == null) {
            throw new IllegalStateException("no body model for race " + raceId);
        }
        for (int index = 0; index < count; index++) {
            String id = "losttales:" + displayGroupId + genderSuffix + "_" + index;
            CharacterSkinDefinition definition = new CharacterSkinDefinition(
                    id,
                    raceId,
                    genderId,
                    displayGroupId,
                    index,
                    textureBase + "/" + index + ".png",
                    modelId,
                    model.getLayout()
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
