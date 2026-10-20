package com.ninuna.losttales.progression.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One thing a skill counts (PROG-01): the act, and what each one is worth
 * (PROG-02): {@code per} times the amount the act measures, or the worth
 * its tables give what it was done to. Hits and kills name the weapons
 * they are done with: kinds ({@link WeaponKind}) or item ids.
 */
public final class SkillCount {
    private final SkillAct act;
    private final double per;
    private final List<String> weapons;
    private final WorthTable worth;
    private final WorthTable ingredients;

    public SkillCount(SkillAct act, double per, List<String> weapons,
                      WorthTable worth, WorthTable ingredients) {
        this.act = act;
        this.per = per;
        this.weapons = Collections.unmodifiableList(weapons == null
                ? new ArrayList<String>() : new ArrayList<String>(weapons));
        this.worth = worth == null ? WorthTable.EMPTY : worth;
        this.ingredients = ingredients == null ? WorthTable.EMPTY : ingredients;
    }

    public SkillAct getAct() {
        return this.act;
    }

    public double getPer() {
        return this.per;
    }

    public List<String> getWeapons() {
        return this.weapons;
    }

    /** What the thing an act was done to is worth (for a craft: what it made). */
    public WorthTable getWorth() {
        return this.worth;
    }

    /** For a craft, what each thing it used up is worth. */
    public WorthTable getIngredients() {
        return this.ingredients;
    }
}
