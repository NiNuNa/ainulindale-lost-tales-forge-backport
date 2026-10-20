package com.ninuna.losttales.progression.skill;

import java.util.Locale;

/**
 * The acts a skill file may count (PROG-01), each named in the file by its
 * lower-case word. What an act is worth comes from the count's {@code per}
 * (by an amount the act measures) or its tables (by the thing it was done
 * to), PROG-02.
 */
public enum SkillAct {
    /** Damage dealt to a foe that fights back, with the weapons the count names; {@code per} a point of damage. */
    HIT(Measure.PER, 1.0D, true),
    /** A foe slain with the weapons the count names; {@code per} a point of the foe's health. */
    KILL(Measure.PER, 0.5D, true),
    /** Damage your armour and blocking took from a foe; {@code per} a point. */
    GUARD(Measure.PER, 1.0D, false),
    /** Distance ridden on a mount; {@code per} a block. */
    RIDE(Measure.PER, 0.1D, false),
    /** A mount fed while hurt; {@code per} a point of health it got back. */
    FEED(Measure.PER, 1.0D, false),
    /** An anvil repair; {@code per} a level it cost. */
    REPAIR(Measure.PER, 5.0D, false),
    /** A mug of drink brewed; {@code per} a mug. */
    BREW(Measure.PER, 2.0D, false),
    /** An animal tamed, by its kind. */
    TAME(Measure.TABLE, 0.0D, false),
    /** A young animal bred, by its kind. */
    BREED(Measure.TABLE, 0.0D, false),
    /** Each item taken from a furnace or oven, by the item. */
    SMELT(Measure.TABLE, 0.0D, false),
    /** A craft, by what it made and what it used. */
    CRAFT(Measure.TABLE, 0.0D, false),
    /** A ripe crop harvested, by its block. */
    HARVEST(Measure.TABLE, 0.0D, false),
    /** A block mined, by the block. */
    MINE(Measure.TABLE, 0.0D, false),
    /** A quest completed, by its size; {@code per} an objective it holds. */
    QUEST(Measure.PER, 10.0D, false),
    /** A region, waypoint or map marker found for the first time, by its kind. */
    DISCOVER(Measure.TABLE, 0.0D, false);

    /** How an act's worth is read. */
    public enum Measure {
        /** By an amount the act measures, times the count's {@code per}. */
        PER,
        /** By the thing it was done to, from the count's tables. */
        TABLE
    }

    private final Measure measure;
    private final double defaultPer;
    private final boolean weapons;

    SkillAct(Measure measure, double defaultPer, boolean weapons) {
        this.measure = measure;
        this.defaultPer = defaultPer;
        this.weapons = weapons;
    }

    public Measure getMeasure() {
        return this.measure;
    }

    /** What a {@code per} count is worth when its file names none. */
    public double getDefaultPer() {
        return this.defaultPer;
    }

    /** Whether the count names the weapons it is done with. */
    public boolean takesWeapons() {
        return this.weapons;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The act a file's word names; null for a word no act has. */
    public static SkillAct fromWord(String word) {
        if (word == null) {
            return null;
        }
        for (SkillAct act : values()) {
            if (act.word().equals(word)) {
                return act;
            }
        }
        return null;
    }
}
