package com.ninuna.losttales.armour;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The set bonuses in force while a character wears {@code worn}, as the
 * inventory's effect column shows them (CONTENT-12): the weight set's
 * first (PROG-18), where all four pieces are of one weight and the
 * server gives that weight something, then every complete named set
 * with a bonus (CONTENT-07, CONTENT-11), in the order the worn pieces
 * name them. Plain data, so it is tested without a game.
 */
public final class SetBonusesInForce {
    private SetBonusesInForce() {}

    /** One bonus in force: a weight set's or a named set's. */
    public static final class Entry {
        private final ArmourWeight weight;
        private final double weightAmount;
        private final NamedSet set;

        private Entry(ArmourWeight weight, double weightAmount, NamedSet set) {
            this.weight = weight;
            this.weightAmount = weightAmount;
            this.set = set;
        }

        /** Whether it is the weight set's bonus rather than a named set's. */
        public boolean isWeightSet() {
            return this.set == null;
        }

        /** The weight of a weight set; null for a named set. */
        public ArmourWeight getWeight() {
            return this.weight;
        }

        /** What a weight set gives: light's speed or heavy's footing; 0 for a named set. */
        public double getWeightAmount() {
            return this.weightAmount;
        }

        /** The named set; null for a weight set. */
        public NamedSet getSet() {
            return this.set;
        }

        /** The lines saying what it does: one for a weight set, one per effect of a named set. */
        public int effectLines() {
            return this.set == null ? 1 : this.set.getBonuses().size();
        }

        /** The same for the same bonus from one frame to the next. */
        public String key() {
            return this.set == null ? "weight:" + this.weight.name() : "set:" + this.set.getId();
        }
    }

    /** Every bonus in force, the weight set's first; empty for none. */
    public static List<Entry> of(WornArmour worn, ArmourSettings settings) {
        if (worn == null || settings == null) {
            return Collections.<Entry>emptyList();
        }
        List<Entry> entries = new ArrayList<Entry>();
        ArmourWeight weight = worn.weightSet();
        double amount = weight == ArmourWeight.LIGHT ? settings.getLightSpeed()
                : weight == ArmourWeight.HEAVY ? settings.getHeavyKnockback() : 0.0D;
        if (weight != null && amount > 0.0D) {
            entries.add(new Entry(weight, amount, null));
        }
        for (NamedSet set : worn.completeSets()) {
            if (!set.getBonuses().isEmpty()) {
                entries.add(new Entry(null, 0.0D, set));
            }
        }
        return entries;
    }

    /** Whether any bonus is in force. */
    public static boolean any(WornArmour worn, ArmourSettings settings) {
        return !of(worn, settings).isEmpty();
    }
}
