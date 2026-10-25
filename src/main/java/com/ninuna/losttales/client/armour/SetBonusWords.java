package com.ninuna.losttales.client.armour;

import com.ninuna.losttales.armour.ArmourWeight;
import com.ninuna.losttales.armour.ArmourWords;
import com.ninuna.losttales.armour.SetBonus;
import com.ninuna.losttales.armour.SetBonusesInForce;
import com.ninuna.losttales.armour.SetEffect;
import com.ninuna.losttales.client.progression.ClientProgression;
import com.ninuna.losttales.progression.PerkWords;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.resources.I18n;

/**
 * A set bonus in the reader's language: the set's name
 * ({@code Heavy Armor Set}, {@code Gondorian Set}) and what each of its
 * effects does ({@code 5% more speed}). The armour tooltip and the
 * inventory's set bonus column both say it in these words.
 */
public final class SetBonusWords {
    private static final String TOOLTIP = "item.losttales.tooltip.";

    private SetBonusWords() {}

    /** The bonus's set, by name. */
    public static String name(SetBonusesInForce.Entry entry) {
        if (entry.isWeightSet()) {
            return weightSetName(entry.getWeight());
        }
        return ArmourWords.setName(entry.getSet());
    }

    /** What the bonus does, a line for each effect. */
    public static List<String> effects(SetBonusesInForce.Entry entry) {
        List<String> lines = new ArrayList<String>();
        if (entry.isWeightSet()) {
            lines.add(weightBonus(entry.getWeight(), entry.getWeightAmount()));
            return lines;
        }
        for (SetBonus bonus : entry.getSet().getBonuses()) {
            lines.add(effect(bonus));
        }
        return lines;
    }

    /** A weight's full set, by name: the Heavy or the Light Armor Set. */
    public static String weightSetName(ArmourWeight weight) {
        return word(weight == ArmourWeight.HEAVY ? "set.heavy" : "set.light");
    }

    /** What a weight's full set does, {@code amount} written in as a share. */
    public static String weightBonus(ArmourWeight weight, double amount) {
        return word(weight == ArmourWeight.HEAVY ? "set.bonus.knockback" : "set.bonus.speed",
                PerkWords.written(PerkEffect.Unit.PERCENT, amount));
    }

    /** What one effect of a named set's bonus does, its number written in. */
    public static String effect(SetBonus bonus) {
        String share = PerkWords.written(PerkEffect.Unit.PERCENT, bonus.getAmount());
        if (bonus.getEffect() == SetEffect.EXPERIENCE) {
            SkillDefinition skill = ClientProgression.skill(bonus.getSkill());
            return word("set.bonus.experience", share,
                    skill == null ? bonus.getSkill() : PerkWords.skillName(skill));
        }
        return word("set.bonus." + bonus.getEffect().word(), share);
    }

    private static String word(String key, Object... arguments) {
        return I18n.format(TOOLTIP + key, arguments);
    }
}
