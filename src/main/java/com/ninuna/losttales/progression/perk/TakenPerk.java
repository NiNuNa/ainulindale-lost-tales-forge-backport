package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillDefinition;

/**
 * One perk a character took, with its skill and its rank, and how much of
 * it works with the armour worn: all of it for a perk of no armour skill,
 * a quarter for each piece of its weight for an armour skill's rank perk
 * (PROG-17).
 */
public final class TakenPerk {
    private final SkillDefinition skill;
    private final PerkDefinition perk;
    private final int rank;
    private final double share;

    public TakenPerk(SkillDefinition skill, PerkDefinition perk, int rank) {
        this(skill, perk, rank, 1.0D);
    }

    private TakenPerk(SkillDefinition skill, PerkDefinition perk, int rank, double share) {
        this.skill = skill;
        this.perk = perk;
        this.rank = rank;
        this.share = share;
    }

    /** The same perk working at {@code share} of its amount. */
    TakenPerk working(double share) {
        return new TakenPerk(this.skill, this.perk, this.rank, share);
    }

    /** How much of the perk works with the armour worn, 1 for all of it. */
    public double getShare() {
        return this.share;
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

    /** The perk's amount times its rank, as much of it as works. */
    public double amount() {
        return this.perk.getAmount() * this.rank * this.share;
    }

    public double param(String key, double fallback) {
        return this.perk.param(key, fallback);
    }
}
