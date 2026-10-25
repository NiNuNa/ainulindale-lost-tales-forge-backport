package com.ninuna.losttales.character.lore;

import com.ninuna.losttales.character.registry.CharacterFactionCategory;
import com.ninuna.losttales.character.registry.CharacterFactionDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import lotr.common.fac.LOTRFaction;

/**
 * The game a lore file is checked against in a test: the bundled races and
 * their skins, a server's races besides, and every faction a running game
 * has, LOTR v36.15's from its own enum and the mod's own as
 * {@code ELostTalesFaction} adds them, each with the categories
 * {@code LotrCharacterAdapter} gives it.
 */
public class LoreTestGame implements LoreCharacterFiles.Game {
    /** The mod's own factions and their LOTR types, as {@code ELostTalesFaction} writes them. */
    private static final String[][] OWN_FACTIONS = {
            {"LOSSOTH", "HUMAN"},
            {"MORIA_GOBLINS", "ORC"},
            {"OROCARNI", "DWARF"},
            {"THARBAD", "HUMAN"},
            {"MOON_ELVES", "ELF"},
            {"SUN_ELVES", "ELF"},
            {"ODANE", "HUMAN"},
            {"BLUE_GOBLINS", "ORC"}};

    private final Map<String, CharacterRaceDefinition> serverRaces =
            new LinkedHashMap<String, CharacterRaceDefinition>();
    private final Map<String, String> serverSkins = new LinkedHashMap<String, String>();
    private final Map<String, CharacterFactionDefinition> factions;
    private boolean knowsFactions = true;

    public LoreTestGame() {
        this.factions = everyFaction();
    }

    /** A server's own race beside the bundled six, with the skins it offers every body type. */
    public LoreTestGame withRace(CharacterRaceDefinition race, String... skinIds) {
        this.serverRaces.put(race.getId(), race);
        for (String skinId : skinIds) {
            this.serverSkins.put(skinId, race.getId());
        }
        return this;
    }

    /** LOTR out of reach, as when its API cannot be read. */
    public LoreTestGame withoutFactions() {
        this.knowsFactions = false;
        return this;
    }

    public Set<String> factionIds() {
        return this.factions.keySet();
    }

    @Override
    public CharacterRaceDefinition race(String raceId) {
        CharacterRaceDefinition race = this.serverRaces.get(raceId);
        return race != null ? race : CharacterRaceRegistry.get(raceId);
    }

    @Override
    public boolean skinFits(String skinId, String raceId, String bodyTypeId) {
        if (this.serverRaces.containsKey(raceId)) {
            return raceId.equals(this.serverSkins.get(skinId));
        }
        return CharacterSkinRegistry.isCompatible(skinId, raceId, bodyTypeId);
    }

    @Override
    public boolean knowsFactions() {
        return this.knowsFactions;
    }

    @Override
    public CharacterFactionDefinition faction(String factionId) {
        return this.factions.get(factionId);
    }

    private static Map<String, CharacterFactionDefinition> everyFaction() {
        Map<String, CharacterFactionDefinition> all =
                new LinkedHashMap<String, CharacterFactionDefinition>();
        for (LOTRFaction faction : LOTRFaction.values()) {
            String id = "lotr:" + faction.codeName().toLowerCase(java.util.Locale.ROOT);
            all.put(id, new CharacterFactionDefinition(id,
                    faction.isPlayableAlignmentFaction(), categoriesOf(faction)));
        }
        Set<String> declared = ownFactionNames();
        for (String[] own : OWN_FACTIONS) {
            if (!declared.contains(own[0])) {
                throw new IllegalStateException("ELostTalesFaction has no " + own[0]);
            }
            String id = "lotr:" + own[0].toLowerCase(java.util.Locale.ROOT);
            all.put(id, new CharacterFactionDefinition(id, true,
                    EnumSet.of(CharacterFactionCategory.valueOf(own[1]))));
        }
        return all;
    }

    private static Set<CharacterFactionCategory> categoriesOf(LOTRFaction faction) {
        Set<CharacterFactionCategory> categories = EnumSet.noneOf(CharacterFactionCategory.class);
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_MAN)) {
            categories.add(CharacterFactionCategory.HUMAN);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_ELF)) {
            categories.add(CharacterFactionCategory.ELF);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_DWARF)) {
            categories.add(CharacterFactionCategory.DWARF);
        }
        if (faction.isOfType(LOTRFaction.FactionType.TYPE_ORC)) {
            categories.add(CharacterFactionCategory.ORC);
        }
        return categories;
    }

    /**
     * The constants {@code ELostTalesFaction} declares, read without
     * starting it: started, it would add its factions to LOTR's enum.
     */
    private static Set<String> ownFactionNames() {
        Set<String> names = new LinkedHashSet<String>();
        try {
            Class<?> type = Class.forName("com.ninuna.losttales.faction.ELostTalesFaction",
                    false, LoreTestGame.class.getClassLoader());
            for (Field field : type.getDeclaredFields()) {
                if (field.isEnumConstant()) {
                    names.add(field.getName());
                }
            }
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException(missing);
        }
        return names;
    }
}
