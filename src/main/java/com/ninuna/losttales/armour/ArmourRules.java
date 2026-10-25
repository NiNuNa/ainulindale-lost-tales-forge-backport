package com.ninuna.losttales.armour;

import com.ninuna.losttales.progression.perk.PerkIndex;
import com.ninuna.losttales.progression.perk.TakenPerk;
import com.ninuna.losttales.progression.skill.PerkEffect;

/**
 * The numbers the armour worn comes to, from its pieces, the named sets
 * complete and the perks the armour lets work: speed, footing and what
 * is left of a blow. Plain arithmetic, so the rules are tested without a
 * game; {@link ArmourEvents} puts them in place.
 */
public final class ArmourRules {
    private ArmourRules() {}

    /** What a blow is, as the protections tell blows apart. */
    public static final class Harm {
        final boolean armourApplies;
        final boolean fire;
        final boolean fall;
        final boolean missile;

        /**
         * {@code armourApplies}: the armour takes its share of it, as of a
         * blow; {@code fire}, {@code fall}, {@code missile}: it burns, is a
         * fall, or is an arrow, a bolt or a thrown weapon.
         */
        public Harm(boolean armourApplies, boolean fire, boolean fall, boolean missile) {
            this.armourApplies = armourApplies;
            this.fire = fire;
            this.fall = fall;
            this.missile = missile;
        }
    }

    /**
     * The share more or less speed (CONTENT-06, PROG-18): four light pieces'
     * weight set and Unhindered, the complete sets' speed, less each heavy
     * piece's slowdown, which Conditioning takes its share off.
     */
    public static double speed(WornArmour worn, PerkIndex perks, ArmourSettings settings) {
        double speed = worn.weightSet() == ArmourWeight.LIGHT ? settings.getLightSpeed() : 0.0D;
        speed += perks.total(PerkEffect.SPEED);
        speed += worn.bonus(SetEffect.SPEED);
        double kept = 1.0D - Math.min(1.0D, perks.total(PerkEffect.CONDITIONING));
        return speed - worn.slowdown(settings) * kept;
    }

    /**
     * The chance a blow does not push you, at most 1 (PROG-18): four heavy
     * pieces' weight set, Tower of Strength and the complete sets' footing.
     */
    public static double footing(WornArmour worn, PerkIndex perks, ArmourSettings settings) {
        double footing = worn.weightSet() == ArmourWeight.HEAVY
                ? settings.getHeavyKnockback() : 0.0D;
        footing += perks.total(PerkEffect.KNOCKBACK);
        footing += worn.bonus(SetEffect.KNOCKBACK);
        return Math.max(0.0D, Math.min(1.0D, footing));
    }

    /**
     * What is left of a blow before the armour, so of what the armour lets
     * through (PROG-19): each protecting perk and each complete set's
     * protection takes its share off what the one before left, for a blow
     * the armour takes its share of; fire, a fall and a missile lose each
     * complete set's share of their own; Standing Firm, while it holds,
     * takes its share off a blow.
     */
    public static double left(WornArmour worn, PerkIndex perks, Harm harm, boolean standingFirm) {
        double left = 1.0D;
        if (harm.armourApplies) {
            for (PerkEffect effect : new PerkEffect[] { PerkEffect.WEIGHT_PROTECTION,
                    PerkEffect.PROTECTION, PerkEffect.MATCHING_SET }) {
                left = lessen(left, perks, effect);
            }
            left *= worn.left(SetEffect.PROTECTION);
            if (standingFirm) {
                left = lessen(left, perks, PerkEffect.STANDING_FIRM);
            }
        }
        if (harm.fire) {
            left *= worn.left(SetEffect.FIRE);
        }
        if (harm.fall) {
            left *= worn.left(SetEffect.FALLS);
        }
        if (harm.missile) {
            left *= worn.left(SetEffect.ARROWS);
        }
        return left;
    }

    private static double lessen(double left, PerkIndex perks, PerkEffect effect) {
        for (TakenPerk perk : perks.taken(effect)) {
            left *= 1.0D - Math.min(1.0D, perk.amount());
        }
        return left;
    }
}
