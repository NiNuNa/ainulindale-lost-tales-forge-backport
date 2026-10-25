package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.PerkEffect;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.util.StatCollector;

/**
 * A skill's and a perk's words in the language of the side that asks: a
 * bundled one's lang lines, a server's own file's words where it wrote
 * some. A perk's description takes its numbers from the skill file, its
 * amount for each rank first, then the numbers its effect reads, each
 * written in its unit ({@code 10%}, {@code 3 seconds}, {@code 1 heart}).
 */
public final class PerkWords {
    private static final String[] ROMAN = { "", "I", "II", "III", "IV", "V", "VI", "VII",
            "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI" };

    private PerkWords() {}

    public static String skillName(SkillDefinition skill) {
        return skill.getName().length() > 0 ? skill.getName()
                : StatCollector.translateToLocal(SkillWords.nameKey(skill.getId()));
    }

    public static String skillDescription(SkillDefinition skill) {
        return skill.getDescription().length() > 0 ? skill.getDescription()
                : StatCollector.translateToLocal(SkillWords.descriptionKey(skill.getId()));
    }

    public static String perkName(SkillDefinition skill, PerkDefinition perk) {
        return perk.getName().length() > 0 ? perk.getName() : StatCollector.translateToLocal(
                SkillWords.perkNameKey(skill.getId(), perk.getId()));
    }

    /** The perk's name with the rank, {@code Steady Draw II}, where it has more than one. */
    public static String perkName(SkillDefinition skill, PerkDefinition perk, int rank) {
        String name = perkName(skill, perk);
        return perk.getRanks() > 1 && rank >= 1 && rank < ROMAN.length
                ? name + " " + ROMAN[rank] : name;
    }

    /** What the perk does, its numbers written in. */
    public static String perkDescription(SkillDefinition skill, PerkDefinition perk) {
        String pattern = perk.getDescription().length() > 0 ? perk.getDescription()
                : StatCollector.translateToLocal(
                        SkillWords.perkDescriptionKey(skill.getId(), perk.getId()));
        PerkEffect effect = PerkEffect.fromWord(perk.getEffect());
        List<Object> numbers = new ArrayList<Object>();
        numbers.add(written(effect == null ? PerkEffect.Unit.COUNT : effect.getUnit(),
                perk.getAmount()));
        if (effect != null) {
            for (String param : effect.getParams()) {
                numbers.add(written(unitOf(param), perk.param(param, 0.0D)));
            }
        }
        try {
            return String.format(pattern, numbers.toArray());
        } catch (RuntimeException unformattable) {
            // A server's own words that do not fit the numbers read as written.
            return pattern;
        }
    }

    /** The unit a named number of an effect is read in. */
    static PerkEffect.Unit unitOf(String param) {
        if ("seconds".equals(param) || "cooldown".equals(param)) {
            return PerkEffect.Unit.SECONDS;
        }
        if ("health".equals(param) || "poison".equals(param) || "volume".equals(param)) {
            return PerkEffect.Unit.PERCENT;
        }
        if ("radius".equals(param)) {
            return null;
        }
        return PerkEffect.Unit.COUNT;
    }

    /** A number in its unit, in the side's language; null reads as blocks. */
    public static String written(PerkEffect.Unit unit, double value) {
        if (unit == null) {
            return plural("gui.losttales.skill.unit.block", "gui.losttales.skill.unit.blocks", value);
        }
        switch (unit) {
            case PERCENT:
                return StatCollector.translateToLocalFormatted("gui.losttales.skill.unit.percent",
                        plain(value * 100.0D));
            case SECONDS:
                return plural("gui.losttales.skill.unit.second", "gui.losttales.skill.unit.seconds",
                        value);
            case HEALTH:
                return plural("gui.losttales.skill.unit.heart", "gui.losttales.skill.unit.hearts",
                        value / 2.0D);
            default:
                return plain(value);
        }
    }

    private static String plural(String one, String many, double value) {
        return StatCollector.translateToLocalFormatted(value == 1.0D ? one : many, plain(value));
    }

    /** A number with no needless places: {@code 10}, {@code 1.5}. */
    static String plain(double value) {
        double rounded = Math.round(value * 100.0D) / 100.0D;
        if (rounded == Math.rint(rounded)) {
            return String.valueOf((long) rounded);
        }
        String text = String.format(Locale.ROOT, "%.2f", rounded);
        while (text.endsWith("0")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
