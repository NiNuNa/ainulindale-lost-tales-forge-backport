package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillDefinition;

/** One perk a character took, with its skill and its rank. */
public final class TakenPerk {
    private final SkillDefinition skill;
    private final PerkDefinition perk;
    private final int rank;

    public TakenPerk(SkillDefinition skill, PerkDefinition perk, int rank) {
        this.skill = skill;
        this.perk = perk;
        this.rank = rank;
    }

    public SkillDefinition getSkill() {
        return this.skill;
    }

    public PerkDefinition getPerk() {
        return this.perk;
    }

    public int getRank() {
        return this.rank;
    }

    /** The perk's amount times its rank. */
    public double amount() {
        return this.perk.getAmount() * this.rank;
    }

    public double param(String key, double fallback) {
        return this.perk.param(key, fallback);
    }
}
