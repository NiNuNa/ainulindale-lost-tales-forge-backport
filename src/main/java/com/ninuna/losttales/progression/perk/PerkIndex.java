package com.ninuna.losttales.progression.perk;

import com.ninuna.losttales.armour.ArmourSlot;
import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.armour.WornArmour;
import com.ninuna.losttales.progression.ProgressionState;
import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The perks one character took, by their effect (PROG-06): each one on
 * and known to the skills in force, with its rank. A switched-off perk
 * or one whose skill or tree no longer has it does nothing. An armour
 * skill's perks work only as far as the armour worn lets them
 * ({@link #armoured}, PROG-17).
 */
public final class PerkIndex {
    public static final PerkIndex EMPTY = new PerkIndex(
            new EnumMap<PerkEffect, List<TakenPerk>>(PerkEffect.class));

    private final Map<PerkEffect, List<TakenPerk>> byEffect;
    private final boolean armourPerks;
    /** The index as the armour last asked about lets it work; never saved. */
    private volatile Armoured armoured;

    private PerkIndex(Map<PerkEffect, List<TakenPerk>> byEffect) {
        this.byEffect = byEffect;
        boolean armour = false;
        for (List<TakenPerk> list : byEffect.values()) {
            for (TakenPerk perk : list) {
                armour |= perk.getSkill().getWeight() != null;
            }
        }
        this.armourPerks = armour;
    }

    public static PerkIndex of(ProgressionState state, Map<String, SkillDefinition> skills) {
        if (state == null || skills == null || state.perks().isEmpty()) {
            return EMPTY;
        }
        Map<PerkEffect, List<TakenPerk>> byEffect =
                new EnumMap<PerkEffect, List<TakenPerk>>(PerkEffect.class);
        for (Map.Entry<String, Integer> taken : state.perks().entrySet()) {
            String key = taken.getKey();
            int slash = key.indexOf('/');
            SkillDefinition skill = skills.get(key.substring(0, slash));
            PerkDefinition perk = skill == null ? null : skill.perk(key.substring(slash + 1));
            PerkEffect effect = perk == null ? null : PerkEffect.fromWord(perk.getEffect());
            if (effect == null || !perk.isEnabled()) {
                continue;
            }
            int rank = Math.min(taken.getValue().intValue(), perk.getRanks());
            List<TakenPerk> list = byEffect.get(effect);
            if (list == null) {
                list = new ArrayList<TakenPerk>();
                byEffect.put(effect, list);
            }
            list.add(new TakenPerk(skill, perk, rank));
        }
        return new PerkIndex(byEffect);
    }

    /**
     * The perks as the armour worn lets them work (PROG-17): an armour
     * skill's rank perk a quarter for each piece of its weight, Matching
     * Set with the four pieces of a named set of its weight, any other of
     * its perks with all four pieces of its weight; every other skill's
     * perks whole. Kept until the armour changes.
     */
    public PerkIndex armoured(WornArmour worn) {
        if (worn == null) {
            return this;
        }
        if (!this.armourPerks) {
            return this;
        }
        Armoured cached = this.armoured;
        if (cached != null && cached.key.equals(worn.key())) {
            return cached.index;
        }
        Map<PerkEffect, List<TakenPerk>> working =
                new EnumMap<PerkEffect, List<TakenPerk>>(PerkEffect.class);
        for (Map.Entry<PerkEffect, List<TakenPerk>> entry : this.byEffect.entrySet()) {
            List<TakenPerk> list = new ArrayList<TakenPerk>();
            for (TakenPerk perk : entry.getValue()) {
                double share = share(perk, entry.getKey(), worn);
                if (share > 0.0D) {
                    list.add(share == 1.0D ? perk : perk.working(share));
                }
            }
            if (!list.isEmpty()) {
                working.put(entry.getKey(), list);
            }
        }
        PerkIndex index = new PerkIndex(working, false);
        this.armoured = new Armoured(worn.key(), index);
        return index;
    }

    private PerkIndex(Map<PerkEffect, List<TakenPerk>> byEffect, boolean armourPerks) {
        this.byEffect = byEffect;
        this.armourPerks = armourPerks;
    }

    /** Whether any perk taken is an armour skill's, so the armour worn matters. */
    public boolean hasArmourPerks() {
        return this.armourPerks;
    }

    /** How much of a taken perk the armour worn lets work, 0 for none. */
    static double share(TakenPerk perk, PerkEffect effect, WornArmour worn) {
        ArmourWeight weight = perk.getSkill().getWeight();
        if (weight == null) {
            return 1.0D;
        }
        int pieces = worn.count(weight);
        if (effect == PerkEffect.MATCHING_SET) {
            return worn.weightSet() == weight && worn.hasCompleteSet() ? 1.0D : 0.0D;
        }
        if (effect.worksPerPiece()) {
            return pieces > 0 ? 1.0D : 0.0D;
        }
        if (perk.getPerk().getRanks() > 1) {
            return pieces / (double) ArmourSlot.values().length;
        }
        return worn.weightSet() == weight ? 1.0D : 0.0D;
    }

    /** Every taken perk with the effect. */
    public List<TakenPerk> taken(PerkEffect effect) {
        List<TakenPerk> list = this.byEffect.get(effect);
        return list == null ? Collections.<TakenPerk>emptyList() : list;
    }

    public boolean has(PerkEffect effect) {
        return !taken(effect).isEmpty();
    }

    /** The effect's amount over every taken perk with it, each times its rank. */
    public double total(PerkEffect effect) {
        double total = 0.0D;
        for (TakenPerk perk : taken(effect)) {
            total += perk.amount();
        }
        return total;
    }

    /**
     * The effect's amount over the taken perks that work with a weapon or
     * tool: those whose own list names it, else whose skill's weapons do.
     * A perk of a skill with no weapons that names none works with any.
     */
    public double total(PerkEffect effect, WeaponMatch weapon) {
        double total = 0.0D;
        for (TakenPerk perk : taken(effect)) {
            List<String> with = perk.getPerk().getWith().isEmpty()
                    ? perk.getSkill().weapons() : perk.getPerk().getWith();
            if (with.isEmpty() || weapon != null && weapon.matches(with)) {
                total += perk.amount();
            }
        }
        return total;
    }

    /** The first taken perk with the effect, or null; for effects one tree holds. */
    public TakenPerk first(PerkEffect effect) {
        List<TakenPerk> list = taken(effect);
        return list.isEmpty() ? null : list.get(0);
    }

    /** Whether a weapon or tool is one a list names, by its kind or its item name. */
    public interface WeaponMatch {
        boolean matches(List<String> names);
    }

    /** An index as one set of worn pieces lets it work. */
    private static final class Armoured {
        final String key;
        final PerkIndex index;

        Armoured(String key, PerkIndex index) {
            this.key = key;
            this.index = index;
        }
    }
}
