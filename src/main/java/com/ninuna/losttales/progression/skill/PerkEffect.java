package com.ninuna.losttales.progression.skill;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Every effect a perk may have (PROG-06), named in a skill file by its
 * lower-case word. The code knows each one; a file picks one per perk and
 * sets its numbers: {@code amount} for each rank, in the effect's
 * {@link Unit}, and the named {@code params} the effect reads. A server
 * adds perks from these and nothing else. A perk of an armour skill
 * (PROG-17) works only in that skill's weight: one of ranks for each
 * piece of it worn, a quarter per piece; any other with all four pieces.
 */
public enum PerkEffect {
    // Weapons, every combat tree: the skill's own weapons unless the perk names others.
    WEAPON_DAMAGE(Unit.PERCENT),
    WEAR(Unit.PERCENT),
    // Archery.
    DRAW_SPEED(Unit.PERCENT),
    BOW_ZOOM(Unit.PERCENT),
    ARROW_RECOVERY(Unit.PERCENT),
    RELOAD_SPEED(Unit.PERCENT),
    QUICK_NOCK(Unit.PERCENT, "seconds"),
    PINNING_SHOT(Unit.SECONDS, "level"),
    ARROW_FLIGHT(Unit.PERCENT),
    // Blades.
    SPRINT_HUNGER(Unit.PERCENT),
    DAGGER_WORK(Unit.PERCENT, "poison"),
    RIPOSTE(Unit.PERCENT, "seconds"),
    KILL_SPEED(Unit.SECONDS, "level"),
    FALLING_STRIKE(Unit.PERCENT),
    // Heavy Arms.
    CHOP_SPEED(Unit.PERCENT),
    SUNDER(Unit.PERCENT),
    STAGGER(Unit.PERCENT),
    CLEAVE(Unit.PERCENT, "radius"),
    KILL_HEAL(Unit.HEALTH),
    // Polearms.
    THROW_FLIGHT(Unit.PERCENT),
    BRACE(Unit.PERCENT),
    THROW_RECOVERY(Unit.PERCENT),
    BEAST_BREAKER(Unit.PERCENT),
    PHALANX(Unit.PERCENT, "radius"),
    UNHORSE(Unit.PERCENT),
    // Light Armour and Heavy Armour (PROG-17); protection follows PROG-19.
    /** The skill's weight lets a share less of a blow through, per rank. */
    WEIGHT_PROTECTION(Unit.PERCENT),
    /** Your armour lets a share less of a blow through. */
    PROTECTION(Unit.PERCENT),
    /** One named set's four pieces let a share less of a blow through. */
    MATCHING_SET(Unit.PERCENT),
    /** While you sneak, foes notice you a share less far, and others hear your steps a share quieter. */
    SOFT_STEP(Unit.PERCENT, "volume"),
    /** A share more speed. */
    SPEED(Unit.PERCENT),
    /** A melee blow has a chance to do no harm. */
    MELEE_EVADE(Unit.PERCENT),
    /** An arrow, a bolt or a thrown weapon has a chance to miss you. */
    MISSILE_EVADE(Unit.PERCENT),
    /** Heavy armour slows you a share less per rank. */
    CONDITIONING(Unit.PERCENT),
    /** More chance that a blow does not push you. */
    KNOCKBACK(Unit.PERCENT),
    FALL_DAMAGE(Unit.PERCENT),
    /** While you sneak or stand still, nothing knocks you back and blows do a share less. */
    STANDING_FIRM(Unit.PERCENT),
    LAST_STAND(Unit.PERCENT, "health", "seconds", "cooldown"),
    // Riding.
    HORSE_SENSE(Unit.NONE),
    MOUNT_SPEED(Unit.PERCENT),
    TAME_EASE(Unit.COUNT),
    MOUNTED_AIM(Unit.PERCENT),
    FEED_HEAL(Unit.PERCENT),
    MOUNTED_FALL(Unit.PERCENT),
    CHARGE(Unit.PERCENT),
    MOUNT_JUMP(Unit.PERCENT),
    // Smithing.
    SMELT_NUGGET(Unit.PERCENT),
    FORGE_THRIFT(Unit.PERCENT),
    REPAIR_COST(Unit.PERCENT),
    TEMPERED(Unit.NONE),
    MASTERWORK(Unit.PERCENT),
    REPAIR_MORE(Unit.PERCENT),
    // Cooking.
    HEARTY_MEALS(Unit.PERCENT),
    BREWER(Unit.COUNT),
    SECOND_HELPING(Unit.PERCENT),
    ALE_WISE(Unit.PERCENT),
    FEAST(Unit.SECONDS),
    MASTER_COOK(Unit.PERCENT),
    // Husbandry.
    HARVEST_BONUS(Unit.PERCENT),
    BREED_COOLDOWN(Unit.PERCENT),
    SHEAR_BONUS(Unit.COUNT),
    HERBALIST(Unit.COUNT),
    BUTCHER(Unit.PERCENT),
    GROW_FASTER(Unit.PERCENT),
    // Mining.
    ORE_BONUS(Unit.PERCENT),
    STONE_SPEED(Unit.PERCENT),
    DEEP_SPEED(Unit.PERCENT, "below"),
    GEM_BONUS(Unit.PERCENT),
    MINE_SPEED(Unit.PERCENT),
    // Lore.
    TRAVEL_WAIT(Unit.PERCENT),
    DISCOVERY_RANGE(Unit.PERCENT),
    RUMOURS(Unit.COUNT),
    FAIR_DEALER(Unit.PERCENT),
    ALIGNMENT_GAIN(Unit.PERCENT),
    QUEST_SKILL_BONUS(Unit.PERCENT),
    PERK_POINT(Unit.COUNT);

    /** How an effect's number reads on the Skills page. */
    public enum Unit {
        /** A share, written 0.1 and read 10%. */
        PERCENT,
        /** Seconds. */
        SECONDS,
        /** Points of health; two are a heart. */
        HEALTH,
        /** A whole count. */
        COUNT,
        /** The effect has no number. */
        NONE
    }

    private final Unit unit;
    private final List<String> params;

    PerkEffect(Unit unit, String... params) {
        this.unit = unit;
        this.params = Collections.unmodifiableList(Arrays.asList(params));
    }

    public Unit getUnit() {
        return this.unit;
    }

    /**
     * The named numbers the effect reads besides its amount, in the order
     * a perk's description takes them after the amount.
     */
    public List<String> getParams() {
        return this.params;
    }

    /**
     * Whether the effect already works piece by piece, as Conditioning
     * takes its share off each heavy piece's slowdown, so its ranks count
     * whole for any piece of its weight rather than a quarter per piece.
     */
    public boolean worksPerPiece() {
        return this == CONDITIONING;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The effect a file's word names; null for a word no effect has. */
    public static PerkEffect fromWord(String word) {
        if (word == null) {
            return null;
        }
        for (PerkEffect effect : values()) {
            if (effect.word().equals(word)) {
                return effect;
            }
        }
        return null;
    }
}
