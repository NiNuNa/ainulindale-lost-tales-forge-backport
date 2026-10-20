package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * How a character grows, as pure rules over its {@link ProgressionState}:
 * experience raising a skill (PROG-03), each skill level reached raising
 * the character level (PROG-04), the perk points those give and what a
 * perk needs before it can be taken (PROG-06).
 */
public final class ProgressionRules {
    /** The perk effect that gives perk points of its own (Lore's Sage). */
    public static final String PERK_POINT_EFFECT = "perk_point";

    private ProgressionRules() {}

    /** A new character: its race's starting skills, which give no character level (PROG-05). */
    public static ProgressionState startingState(Map<String, Integer> startingSkills) {
        ProgressionState state = new ProgressionState();
        if (startingSkills != null) {
            for (Map.Entry<String, Integer> skill : startingSkills.entrySet()) {
                if (ProgressionState.fitsId(skill.getKey()) && skill.getValue() != null
                        && state.skills().size() < ProgressionState.MAX_SKILLS) {
                    state.setSkill(skill.getKey(),
                            SkillStanding.start(skill.getValue().intValue()));
                }
            }
        }
        return state;
    }

    /**
     * Adds {@code amount} experience to one skill; each level it reaches
     * adds its own number to the character's progress. Answers what
     * changed.
     */
    public static Gain gain(ProgressionState state, String skillId, double amount,
                            SkillCurve curve, CharacterLevelRule rule) {
        if (state == null || !ProgressionState.fitsId(skillId)
                || !(amount > 0.0D) || Double.isInfinite(amount)) {
            return Gain.NONE;
        }
        SkillStanding before = state.skill(skillId);
        if (before.getLevel() >= SkillCurve.TOP_LEVEL) {
            return Gain.NONE;
        }
        if (!state.skills().containsKey(skillId)
                && state.skills().size() >= ProgressionState.MAX_SKILLS) {
            return Gain.NONE;
        }
        SkillCurve.Standing after = curve.add(before.getLevel(), before.getInto(), amount);
        state.setSkill(skillId, new SkillStanding(after.level, after.into));
        List<Integer> reached = new ArrayList<Integer>();
        long progress = 0L;
        for (int level = before.getLevel() + 1; level <= after.level; level++) {
            reached.add(Integer.valueOf(level));
            progress += CharacterLevelRule.progressFor(level);
        }
        int characterBefore = state.getCharacterLevel();
        if (progress > 0L) {
            CharacterLevelRule.Standing character = rule.add(characterBefore,
                    state.getCharacterInto(), progress);
            state.setCharacter(character.level, character.into);
        }
        return new Gain(skillId, reached, state.getCharacterLevel() - characterBefore);
    }

    /**
     * The perk points a character has to spend: {@code pointsPerLevel}
     * for each character level past the first, plus what its perks give,
     * less every rank it took.
     */
    public static int availablePoints(ProgressionState state, int pointsPerLevel,
                                      Map<String, SkillDefinition> skills) {
        long earned = (long) Math.max(0, state.getCharacterLevel() - 1)
                * Math.max(0, pointsPerLevel);
        earned += bonusPoints(state, skills);
        long available = earned - state.spentPoints();
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, available));
    }

    /** The points the character's perks give of their own (Sage). */
    public static int bonusPoints(ProgressionState state, Map<String, SkillDefinition> skills) {
        int bonus = 0;
        for (Map.Entry<String, Integer> taken : state.perks().entrySet()) {
            String key = taken.getKey();
            int slash = key.indexOf('/');
            SkillDefinition skill = skills == null ? null : skills.get(key.substring(0, slash));
            PerkDefinition perk = skill == null ? null : skill.perk(key.substring(slash + 1));
            if (perk != null && perk.isEnabled()
                    && PERK_POINT_EFFECT.equals(perk.getEffect())) {
                bonus += (int) Math.round(perk.getAmount()) * taken.getValue().intValue();
            }
        }
        return bonus;
    }

    /** Why a perk's next rank cannot be taken, or {@link Refusal#NONE}. */
    public static Refusal canTake(ProgressionState state, SkillDefinition skill,
                                  PerkDefinition perk, int availablePoints) {
        if (skill == null || perk == null) {
            return Refusal.UNKNOWN;
        }
        if (!perk.isEnabled()) {
            return Refusal.SWITCHED_OFF;
        }
        int rank = state.perkRank(skill.getId(), perk.getId());
        if (rank >= perk.getRanks()) {
            return Refusal.ALL_TAKEN;
        }
        if (state.skill(skill.getId()).getLevel() < perk.levelFor(rank + 1)) {
            return Refusal.SKILL_TOO_LOW;
        }
        if (perk.getNeedsPerk().length() > 0 && state.perkRank(skill.getId(),
                perk.getNeedsPerk()) < perk.getNeedsRank()) {
            return Refusal.NEEDS_PERK;
        }
        if (availablePoints < 1) {
            return Refusal.NO_POINTS;
        }
        return Refusal.NONE;
    }

    /** Why a perk cannot be taken; each has a line the page shows. */
    public enum Refusal {
        NONE,
        UNKNOWN,
        SWITCHED_OFF,
        ALL_TAKEN,
        SKILL_TOO_LOW,
        NEEDS_PERK,
        NO_POINTS
    }

    /** What one gain changed: the skill levels reached and the character levels. */
    public static final class Gain {
        public static final Gain NONE = new Gain("", Collections.<Integer>emptyList(), 0);

        public final String skillId;
        public final List<Integer> levelsReached;
        public final int characterLevels;

        Gain(String skillId, List<Integer> levelsReached, int characterLevels) {
            this.skillId = skillId;
            this.levelsReached = Collections.unmodifiableList(levelsReached);
            this.characterLevels = characterLevels;
        }

        public boolean changedLevels() {
            return !this.levelsReached.isEmpty();
        }
    }
}
