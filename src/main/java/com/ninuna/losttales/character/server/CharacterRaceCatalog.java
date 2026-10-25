package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterFactionResolver;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceFiles;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.registry.CharacterSkinRegistry;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.compat.lotr.LotrCharacterAdapter;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.ProgressionCatalog;
import com.ninuna.losttales.progression.ProgressionFiles;
import com.ninuna.losttales.util.LostTalesLog;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.World;

/**
 * The races and capes a server plays with (CHAR-38, CHAR-39), read as it
 * starts, after the skills, and again on {@code /losttales reload races}
 * (both) and {@code reload capes} (the capes alone, DATA-11): the bundled
 * files with the server's own in {@code config/losttales/races/} and
 * {@code capes/} in place of those of the same id and its new ones after.
 * A race a character still names but no file has any more stands in as its
 * body's own race, as the character remembers the body. Every player's game
 * is sent the lists as it joins ({@link CharacterRaceCatalogSync}).
 */
public final class CharacterRaceCatalog {

    private CharacterRaceCatalog() {}

    /**
     * Reads both lists again and puts them in place together, once both
     * are read: a read that fails leaves both as they were. Answers why
     * each file left out was left out.
     */
    public static synchronized List<String> load(World world) {
        List<String> problems = new ArrayList<String>();
        CharacterFactionResolver factions = LotrCharacterAdapter.getInstance();
        List<CharacterRaceDefinition> races = readRaces(
                ProgressionCatalog.skillsById().keySet(),
                ProgressionFiles.folder(CharacterRaceFiles.FOLDER), factions, problems);
        races.addAll(standIns(world, races, problems));
        List<CharacterCapeDefinition> capes = readCapes(races,
                ProgressionFiles.folder(CharacterCapeCatalog.FOLDER), factions, problems);
        CharacterRaceRegistry.set(races);
        CharacterCapeCatalog.set(capes);
        return problems;
    }

    /**
     * Reads the cape files alone again, against the races in place, and
     * puts them in place once read; answers why each file left out was
     * left out.
     */
    public static synchronized List<String> loadCapes() {
        List<String> problems = new ArrayList<String>();
        List<CharacterCapeDefinition> capes = readCapes(
                new ArrayList<CharacterRaceDefinition>(CharacterRaceRegistry.getAll()),
                ProgressionFiles.folder(CharacterCapeCatalog.FOLDER),
                LotrCharacterAdapter.getInstance(), problems);
        CharacterCapeCatalog.set(capes);
        return problems;
    }

    /** The bundled six as their files write them, again, as the server stops. */
    public static synchronized void clear() {
        CharacterRaceRegistry.resetToBundled();
        CharacterCapeCatalog.resetToBundled();
    }

    /**
     * The races of the bundled files and {@code folder}: each checked as
     * every file is, its factions against {@code factions} where LOTR
     * answers, at most {@link CharacterRaceRegistry#MAX_RACES} of them, and
     * none whose skin takes another race's skin's id.
     */
    static List<CharacterRaceDefinition> readRaces(Collection<String> skillIds, File folder,
                                                   CharacterFactionResolver factions,
                                                   List<String> problems) {
        DataFiles.Kind<CharacterRaceDefinition> kind = CharacterRaceFiles.kind(skillIds);
        DataFiles.Result<CharacterRaceDefinition> bundled = DataFiles.readBundled(
                CharacterRaceRegistry.CLASSPATH, CharacterRaceFiles.INDEX,
                CharacterRaceFiles.LIST_KEY, kind);
        DataFiles.Result<CharacterRaceDefinition> own = DataFiles.readFolder(folder, kind);
        note(problems, "Bundled race", bundled.problems);
        note(problems, "Server race", own.problems);

        List<String> unfit = new ArrayList<String>();
        List<CharacterRaceDefinition> fitting = new ArrayList<CharacterRaceDefinition>();
        for (CharacterRaceDefinition race : own.values) {
            String unknown = unknownFaction(race.getAllowedFactionIds(), factions);
            if (unknown != null) {
                unfit.add(race.getId() + ": names the faction " + unknown
                        + ", which LOTR does not have");
            } else {
                fitting.add(race);
            }
        }
        List<CharacterRaceDefinition> merged = DataFiles.merge(bundled.values, fitting, kind);
        List<CharacterRaceDefinition> kept = new ArrayList<CharacterRaceDefinition>();
        for (CharacterRaceDefinition race : merged) {
            if (kept.size() < CharacterRaceRegistry.MAX_RACES) {
                kept.add(race);
            } else {
                unfit.add(race.getId() + ": the server holds its "
                        + CharacterRaceRegistry.MAX_RACES + " races already");
            }
        }
        List<String> clashing = CharacterSkinRegistry.clashes(kept);
        List<CharacterRaceDefinition> races = new ArrayList<CharacterRaceDefinition>();
        for (CharacterRaceDefinition race : kept) {
            if (clashing.contains(race.getId())) {
                unfit.add(race.getId() + ": a skin of it takes the id of an earlier race's skin;"
                        + " give its groups names of their own");
            } else {
                races.add(race);
            }
        }
        note(problems, "Server race", unfit);
        return races;
    }

    /**
     * A stand-in for every race a character names that {@code races} does
     * not hold, as the body the character remembers; a character whose race
     * is held remembers that race's body now.
     */
    static List<CharacterRaceDefinition> standIns(World world,
                                                  List<CharacterRaceDefinition> races,
                                                  List<String> problems) {
        CharacterWorldData data;
        try {
            data = CharacterStorage.get(world);
        } catch (RuntimeException exception) {
            note(problems, "Race stand-in", listOf("the characters cannot be read: " + exception));
            return new ArrayList<CharacterRaceDefinition>();
        }
        if (data.isReadOnlyForNewerVersion()) {
            return new ArrayList<CharacterRaceDefinition>();
        }
        List<CharacterRoster> changed = new ArrayList<CharacterRoster>();
        List<CharacterRoster> rosters = new ArrayList<CharacterRoster>(data.getRosters());
        List<CharacterRaceDefinition> standIns = standInsFor(rosters, races, changed);
        for (CharacterRoster roster : changed) {
            data.saveRoster(roster);
        }
        for (CharacterRaceDefinition standIn : standIns) {
            int count = 0;
            for (CharacterRoster roster : rosters) {
                for (RoleplayCharacter character : roster.getCharacters()) {
                    if (standIn.getId().equals(character.getRaceId())) {
                        count++;
                    }
                }
            }
            LostTalesLog.warning("The race %s is in no race file any more; its %d character(s)"
                            + " play as %s, the body it stood on, until a file brings it back",
                    standIn.getId(), Integer.valueOf(count), standIn.getStandInFor());
        }
        return standIns;
    }

    /**
     * The stand-ins for {@code rosters}' characters whose race {@code races}
     * does not hold, each as its character's remembered body's own race
     * (Human's when that is missing too), at most
     * {@link CharacterRaceRegistry#MAX_STAND_INS}; a character whose race is
     * held remembers its body, and its roster is added to {@code changed}.
     */
    static List<CharacterRaceDefinition> standInsFor(Collection<CharacterRoster> rosters,
                                                     List<CharacterRaceDefinition> races,
                                                     List<CharacterRoster> changed) {
        Map<String, CharacterRaceDefinition> byId =
                new LinkedHashMap<String, CharacterRaceDefinition>();
        for (CharacterRaceDefinition race : races) {
            byId.put(race.getId(), race);
        }
        List<CharacterRaceDefinition> standIns = new ArrayList<CharacterRaceDefinition>();
        for (CharacterRoster roster : rosters) {
            boolean remembered = false;
            for (RoleplayCharacter character : roster.getCharacters()) {
                CharacterRaceDefinition race = byId.get(character.getRaceId());
                if (race != null) {
                    remembered |= !race.isStandIn() && character.rememberBody(race.getBody());
                    continue;
                }
                CharacterRaceDefinition own = byId.get(character.getBody().getOwnRaceId());
                if (own == null || own.isStandIn()) {
                    own = byId.get(CharacterRaceRegistry.HUMAN);
                }
                if (own == null || standIns.size() >= CharacterRaceRegistry.MAX_STAND_INS) {
                    continue;
                }
                CharacterRaceDefinition standIn =
                        CharacterRaceDefinition.standIn(character.getRaceId(), own);
                byId.put(standIn.getId(), standIn);
                standIns.add(standIn);
            }
            if (remembered) {
                changed.add(roster);
            }
        }
        return standIns;
    }

    /**
     * The capes of the bundled files and {@code folder}: each checked as
     * every file is, the races it names among {@code races} and its factions
     * among LOTR's where LOTR answers.
     */
    static List<CharacterCapeDefinition> readCapes(List<CharacterRaceDefinition> races,
                                                   File folder,
                                                   CharacterFactionResolver factions,
                                                   List<String> problems) {
        DataFiles.Result<CharacterCapeDefinition> bundled = DataFiles.readBundled(
                CharacterCapeCatalog.CLASSPATH, CharacterCapeCatalog.INDEX,
                CharacterCapeCatalog.LIST_KEY, CharacterCapeCatalog.KIND);
        DataFiles.Result<CharacterCapeDefinition> own =
                DataFiles.readFolder(folder, CharacterCapeCatalog.KIND);
        note(problems, "Bundled cape", bundled.problems);
        note(problems, "Server cape", own.problems);
        Set<String> raceIds = new LinkedHashSet<String>();
        for (CharacterRaceDefinition race : races) {
            if (!race.isStandIn()) {
                raceIds.add(race.getId());
            }
        }
        List<String> unfit = new ArrayList<String>();
        List<CharacterCapeDefinition> capes = new ArrayList<CharacterCapeDefinition>();
        for (CharacterCapeDefinition cape : DataFiles.merge(bundled.values, own.values,
                CharacterCapeCatalog.KIND)) {
            String unknownRace = null;
            for (String race : cape.getRaces()) {
                if (!raceIds.contains(race)) {
                    unknownRace = race;
                }
            }
            String unknownFaction = unknownFaction(cape.getFactions(), factions);
            if (unknownRace != null) {
                unfit.add(cape.getId() + ": names the race " + unknownRace
                        + ", which the server does not have");
            } else if (unknownFaction != null) {
                unfit.add(cape.getId() + ": names the faction " + unknownFaction
                        + ", which LOTR does not have");
            } else if (capes.size() >= CharacterCapeCatalog.MAX_CAPES) {
                unfit.add(cape.getId() + ": the server holds its "
                        + CharacterCapeCatalog.MAX_CAPES + " capes already");
            } else {
                capes.add(cape);
            }
        }
        note(problems, "Server cape", unfit);
        return capes;
    }

    /** The first of {@code ids} LOTR has no faction for; null when it has every one, or cannot be asked. */
    private static String unknownFaction(Collection<String> ids, CharacterFactionResolver factions) {
        if (factions == null || !factions.isAvailable()) {
            return null;
        }
        for (String id : ids) {
            if (factions.resolve(id) == null) {
                return id;
            }
        }
        return null;
    }

    private static List<String> listOf(String line) {
        List<String> lines = new ArrayList<String>();
        lines.add(line);
        return lines;
    }

    private static void note(List<String> problems, String what, List<String> found) {
        for (String problem : found) {
            problems.add(problem);
            LostTalesLog.warning("%s left out: %s", what, problem);
        }
    }
}
