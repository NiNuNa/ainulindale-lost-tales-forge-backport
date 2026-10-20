package com.ninuna.losttales.progression.perk;

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
 * or one whose skill or tree no longer has it does nothing.
 */
public final class PerkIndex {
    public static final PerkIndex EMPTY = new PerkIndex(
            new EnumMap<PerkEffect, List<TakenPerk>>(PerkEffect.class));

    private final Map<PerkEffect, List<TakenPerk>> byEffect;

    private PerkIndex(Map<PerkEffect, List<TakenPerk>> byEffect) {
        this.byEffect = byEffect;
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
     */
    public double total(PerkEffect effect, WeaponMatch weapon) {
        double total = 0.0D;
        for (TakenPerk perk : taken(effect)) {
            List<String> with = perk.getPerk().getWith().isEmpty()
                    ? perk.getSkill().weapons() : perk.getPerk().getWith();
            if (weapon != null && weapon.matches(with)) {
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
}
