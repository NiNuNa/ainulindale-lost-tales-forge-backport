package com.ninuna.losttales.progression;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.character.registry.CharacterRaceDefinition;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.compat.lotr.LotrDeedAchievements;
import com.ninuna.losttales.progression.deed.DeedDefinition;
import com.ninuna.losttales.progression.deed.DeedFiles;
import com.ninuna.losttales.data.DataFiles;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every skill and deed the logical server knows: the bundled files, with a
 * server's own in place of those of the same id and its new ones after
 * (PROG-01, PROG-11). Read as the server starts and again on
 * {@code /losttales skills reload}; cleared as it stops. The races' starts
 * are in their race files (PROG-05, CHAR-38).
 */
public final class ProgressionCatalog {
    private static Map<String, SkillDefinition> skills =
            Collections.<String, SkillDefinition>emptyMap();
    private static List<SkillDefinition> ordered = Collections.<SkillDefinition>emptyList();
    private static List<DeedDefinition> deeds = Collections.<DeedDefinition>emptyList();
    private static Map<String, DeedDefinition> deedsById =
            Collections.<String, DeedDefinition>emptyMap();
    private static int revision;

    private ProgressionCatalog() {}

    /**
     * Makes the bundled deeds LOTR achievements, with their categories and
     * titles, as the mod loads on either side (PROG-11).
     */
    public static void makeDeedAchievements() {
        DataFiles.Result<DeedDefinition> bundled = DataFiles.readBundled(
                ProgressionFiles.CLASSPATH, DeedFiles.INDEX, "deeds", DeedFiles.KIND);
        note(new ArrayList<String>(), "Bundled deed", bundled.problems);
        LotrDeedAchievements.make(bundled.values);
    }

    /** Reads every file again; answers why each file left out was left out. */
    public static synchronized List<String> load() {
        return load(ProgressionFiles.CLASSPATH);
    }

    static synchronized List<String> load(DataFiles.Source bundledSource) {
        List<String> problems = new ArrayList<String>();
        DataFiles.Result<SkillDefinition> bundledSkills = DataFiles.readBundled(
                bundledSource, ProgressionFiles.SKILLS_INDEX, "skills",
                ProgressionFiles.SKILLS);
        DataFiles.Result<SkillDefinition> serverSkills = DataFiles.readFolder(
                ProgressionFiles.folder(ProgressionFiles.SKILLS_FOLDER),
                ProgressionFiles.SKILLS);
        note(problems, "Bundled skill", bundledSkills.problems);
        note(problems, "Server skill", serverSkills.problems);
        List<SkillDefinition> mergedSkills = DataFiles.merge(bundledSkills.values,
                serverSkills.values, ProgressionFiles.SKILLS);
        Map<String, SkillDefinition> byId = new LinkedHashMap<String, SkillDefinition>();
        for (SkillDefinition skill : mergedSkills) {
            byId.put(skill.getId(), skill);
        }

        DataFiles.Result<DeedDefinition> bundledDeeds = DataFiles.readBundled(
                bundledSource, DeedFiles.INDEX, "deeds", DeedFiles.KIND);
        DataFiles.Result<DeedDefinition> serverDeeds = DataFiles.readFolder(
                ProgressionFiles.folder(DeedFiles.FOLDER), DeedFiles.KIND);
        note(problems, "Bundled deed", bundledDeeds.problems);
        note(problems, "Server deed", serverDeeds.problems);
        List<DeedDefinition> ownDeeds = new ArrayList<DeedDefinition>();
        List<String> titleless = new ArrayList<String>();
        for (DeedDefinition deed : serverDeeds.values) {
            if (deed.hasTitle() && !LotrDeedAchievements.isAchievement(deed.getId())) {
                titleless.add(deed.getId() + ": a server's own deed brings no title;"
                        + " LOTR knows titles only from the bundled deeds");
            } else {
                ownDeeds.add(deed);
            }
        }
        note(problems, "Server deed", titleless);
        set(byId, DataFiles.merge(bundledDeeds.values, ownDeeds, DeedFiles.KIND));
        return problems;
    }

    /** Puts a catalog in place, as a load does; for tests and the reader alike. */
    static synchronized void set(Map<String, SkillDefinition> skillsById,
                                 List<DeedDefinition> deedList) {
        skills = Collections.unmodifiableMap(new LinkedHashMap<String, SkillDefinition>(skillsById));
        List<SkillDefinition> sorted = new ArrayList<SkillDefinition>(skills.values());
        Collections.sort(sorted, ORDER);
        ordered = Collections.unmodifiableList(sorted);
        deeds = Collections.unmodifiableList(new ArrayList<DeedDefinition>(deedList));
        Map<String, DeedDefinition> deedMap = new LinkedHashMap<String, DeedDefinition>();
        for (DeedDefinition deed : deedList) {
            deedMap.put(deed.getId(), deed);
        }
        deedsById = Collections.unmodifiableMap(deedMap);
        revision++;
    }

    public static synchronized void clear() {
        skills = Collections.<String, SkillDefinition>emptyMap();
        ordered = Collections.<SkillDefinition>emptyList();
        deeds = Collections.<DeedDefinition>emptyList();
        deedsById = Collections.<String, DeedDefinition>emptyMap();
        revision++;
    }

    /** The skills by their page order, then by id. */
    public static synchronized List<SkillDefinition> skills() {
        return ordered;
    }

    public static synchronized Map<String, SkillDefinition> skillsById() {
        return skills;
    }

    public static synchronized SkillDefinition skill(String id) {
        return skills.get(id);
    }

    /**
     * Each skill a race starts higher in, as its race file writes it, and
     * its level; empty for a race that names none, and without a skill
     * that did not load.
     */
    public static synchronized Map<String, Integer> startingSkills(String raceId) {
        CharacterRaceDefinition race = CharacterRaceRegistry.get(raceId);
        if (race == null) {
            return Collections.<String, Integer>emptyMap();
        }
        Map<String, Integer> known = new LinkedHashMap<String, Integer>();
        for (Map.Entry<String, Integer> skill : race.getStartingSkills().entrySet()) {
            if (skills.containsKey(skill.getKey())) {
                known.put(skill.getKey(), skill.getValue());
            }
        }
        return known;
    }

    /** Every deed, the bundled ones in their index's order and a server's own after. */
    public static synchronized List<DeedDefinition> deeds() {
        return deeds;
    }

    public static synchronized DeedDefinition deed(String id) {
        return deedsById.get(id);
    }

    /** Counts every load and clear, so a client can tell its copy is old. */
    public static synchronized int revision() {
        return revision;
    }

    /** Skills by their page order, then by id. */
    public static final Comparator<SkillDefinition> ORDER = new Comparator<SkillDefinition>() {
        @Override
        public int compare(SkillDefinition left, SkillDefinition right) {
            if (left.getOrder() != right.getOrder()) {
                return left.getOrder() < right.getOrder() ? -1 : 1;
            }
            return left.getId().compareTo(right.getId());
        }
    };

    private static void note(List<String> problems, String what, List<String> found) {
        for (String problem : found) {
            problems.add(problem);
            FMLLog.warning("[%s] %s left out: %s", LostTalesMetaData.MOD_ID, what, problem);
        }
    }
}
