package com.ninuna.losttales.client.progression;

import com.ninuna.losttales.progression.ProgressionSnapshot;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import com.ninuna.losttales.progression.perk.PerkIndex;
import com.ninuna.losttales.progression.deed.DeedSummary;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What this client was told of the progression: the server's skills and
 * the played character's growth. Cleared as the client leaves a server.
 */
public final class ClientProgression {
    private static List<SkillDefinition> skills = Collections.<SkillDefinition>emptyList();
    private static Map<String, SkillDefinition> skillsById =
            Collections.<String, SkillDefinition>emptyMap();
    private static ProgressionSnapshot snapshot;
    private static int changes;
    private static PerkIndex perkIndex;
    private static List<DeedSummary> deeds = Collections.<DeedSummary>emptyList();

    private ClientProgression() {}

    public static void setDeeds(List<DeedSummary> sent) {
        deeds = Collections.unmodifiableList(new ArrayList<DeedSummary>(sent));
        changes++;
    }

    /** The server's deeds, bundled first. */
    public static List<DeedSummary> deeds() {
        return deeds;
    }

    public static void setSkills(List<SkillDefinition> sent) {
        List<SkillDefinition> copy = new ArrayList<SkillDefinition>(sent);
        Map<String, SkillDefinition> byId = new LinkedHashMap<String, SkillDefinition>();
        for (SkillDefinition skill : copy) {
            byId.put(skill.getId(), skill);
        }
        skills = Collections.unmodifiableList(copy);
        skillsById = Collections.unmodifiableMap(byId);
        perkIndex = null;
        changes++;
    }

    public static void setSnapshot(ProgressionSnapshot sent) {
        snapshot = sent;
        perkIndex = null;
        changes++;
    }

    /** The server's skills by their page order. */
    public static List<SkillDefinition> skills() {
        return skills;
    }

    public static SkillDefinition skill(String id) {
        return skillsById.get(id);
    }

    /** The played character's growth, or null before the server sent it. */
    public static ProgressionSnapshot snapshot() {
        return snapshot;
    }

    /** The played character's state; an empty one before the server sent it. */
    public static ProgressionState state() {
        return snapshot == null ? new ProgressionState() : snapshot.view();
    }

    /** The perks the played character took, by effect. */
    public static PerkIndex perkIndex() {
        if (perkIndex == null) {
            perkIndex = PerkIndex.of(state(), skillsById);
        }
        return perkIndex;
    }

    /** Counts every change, so a page knows to lay itself out again. */
    public static int changes() {
        return changes;
    }

    public static void clear() {
        skills = Collections.<SkillDefinition>emptyList();
        skillsById = Collections.<String, SkillDefinition>emptyMap();
        deeds = Collections.<DeedSummary>emptyList();
        snapshot = null;
        perkIndex = null;
        changes++;
    }
}
