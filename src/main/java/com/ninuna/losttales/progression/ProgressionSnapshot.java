package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the played character's client is told of its growth: the state
 * itself, with the numbers only the server's settings give, what each
 * skill and the character need for their next level and the perk points
 * left to spend.
 */
public final class ProgressionSnapshot {
    private final ProgressionState state;
    private final Map<String, Double> skillNeeded;
    private final long characterNeeded;
    private final int availablePoints;

    public ProgressionSnapshot(ProgressionState state, Map<String, Double> skillNeeded,
                               long characterNeeded, int availablePoints) {
        this.state = state == null ? new ProgressionState() : state.copy();
        this.skillNeeded = Collections.unmodifiableMap(skillNeeded == null
                ? new LinkedHashMap<String, Double>()
                : new LinkedHashMap<String, Double>(skillNeeded));
        this.characterNeeded = characterNeeded;
        this.availablePoints = availablePoints;
    }

    /** What the server tells a client of one character, by its settings and the skills it knows. */
    public static ProgressionSnapshot of(ProgressionState state, SkillCurve curve,
                                         CharacterLevelRule rule, int pointsPerLevel,
                                         Map<String, SkillDefinition> skills) {
        Map<String, Double> needed = new LinkedHashMap<String, Double>();
        for (String skillId : skills.keySet()) {
            needed.put(skillId, Double.valueOf(curve.needed(state.skill(skillId).getLevel())));
        }
        return new ProgressionSnapshot(state, needed,
                rule.needed(state.getCharacterLevel()),
                ProgressionRules.availablePoints(state, pointsPerLevel, skills));
    }

    public ProgressionState getState() {
        return this.state.copy();
    }

    /** The state, read without copying. */
    public ProgressionState view() {
        return this.state;
    }

    /** What each skill needs for its next level; 0 at the top. */
    public Map<String, Double> getSkillNeeded() {
        return this.skillNeeded;
    }

    public double skillNeeded(String skillId) {
        Double needed = this.skillNeeded.get(skillId);
        return needed == null ? 0.0D : needed.doubleValue();
    }

    public long getCharacterNeeded() {
        return this.characterNeeded;
    }

    public int getAvailablePoints() {
        return this.availablePoints;
    }
}
