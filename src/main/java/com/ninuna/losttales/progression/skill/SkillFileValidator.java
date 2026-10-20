package com.ninuna.losttales.progression.skill;

import com.ninuna.losttales.progression.SkillCurve;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Says what is missing or wrong in a skill as its file wrote it (PROG-01):
 * a file with any warning is left out whole, so a skill never counts or
 * gives something its writer did not mean.
 */
public final class SkillFileValidator {
    public static final int MAX_ID_LENGTH = 64;
    public static final int MAX_PERKS = 32;
    public static final int MAX_RANKS = 16;
    public static final int MAX_COUNTS = 32;
    public static final int MAX_TABLE_ENTRIES = 512;
    public static final int MAX_WITH = 64;
    /** The most weapons one skill's hits and kills name between them. */
    public static final int MAX_WEAPONS = 32;
    public static final int MAX_NAME_LENGTH = 64;
    public static final int MAX_DESCRIPTION_LENGTH = 512;
    /** A skill's id: its namespace, a colon, and a path of letters, digits and _ or . */
    private static final Pattern SKILL_ID = Pattern.compile("[a-z0-9_]+:[a-z0-9_.]+");
    private static final Pattern PERK_ID = Pattern.compile("[a-z0-9_]+");

    private SkillFileValidator() {}

    public static List<String> warningsOf(SkillDefinition skill) {
        List<String> warnings = new ArrayList<String>();
        String id = skill.getId();
        if (id.length() > MAX_ID_LENGTH || !SKILL_ID.matcher(id).matches()) {
            warnings.add("the id '" + id + "' is not a namespace and a path of"
                    + " lower-case letters, digits, _ and . within "
                    + MAX_ID_LENGTH + " characters");
        }
        if (skill.getIcon().length() > 0 && !WeaponKind.isNamed(skill.getIcon())) {
            warnings.add("the icon '" + skill.getIcon() + "' is no item name");
        }
        words(warnings, "the skill", skill.getName(), skill.getDescription());
        if (skill.weapons().size() > MAX_WEAPONS) {
            warnings.add("its counts name more than " + MAX_WEAPONS + " weapons");
        }
        if (skill.getCounts().size() > MAX_COUNTS) {
            warnings.add("it counts more than " + MAX_COUNTS + " acts");
        }
        int index = 0;
        for (SkillCount count : skill.getCounts()) {
            index++;
            count(warnings, "count " + index + " (" + count.getAct().word() + ")", count);
        }
        if (skill.getPerks().size() > MAX_PERKS) {
            warnings.add("its tree holds more than " + MAX_PERKS + " perks");
        }
        Set<String> perkIds = new HashSet<String>();
        for (PerkDefinition perk : skill.getPerks()) {
            if (!PERK_ID.matcher(perk.getId()).matches()
                    || perk.getId().length() > MAX_ID_LENGTH) {
                warnings.add("a perk's id '" + perk.getId() + "' is not lower-case"
                        + " letters, digits and _ within " + MAX_ID_LENGTH + " characters");
            } else if (!perkIds.add(perk.getId())) {
                warnings.add("the perk id '" + perk.getId() + "' is written twice");
            }
        }
        for (PerkDefinition perk : skill.getPerks()) {
            perk(warnings, skill, perk);
        }
        return warnings;
    }

    private static void count(List<String> warnings, String where, SkillCount count) {
        SkillAct act = count.getAct();
        if (count.getPer() < 0.0D) {
            warnings.add(where + " is worth less than nothing");
        }
        if (act.takesWeapons()) {
            if (count.getWeapons().isEmpty()) {
                warnings.add(where + " names no weapons");
            }
            for (String weapon : count.getWeapons()) {
                if (!WeaponKind.isNamed(weapon)) {
                    warnings.add(where + " names '" + weapon
                            + "', which is no kind of weapon nor an item name");
                }
            }
        } else if (!count.getWeapons().isEmpty()) {
            warnings.add(where + " names weapons, which it does not read");
        }
        if (act.getMeasure() == SkillAct.Measure.PER) {
            if (!count.getWorth().isEmpty() || !count.getIngredients().isEmpty()) {
                warnings.add(where + " writes a table, which it does not read; it counts by 'per'");
            }
        } else {
            if (act != SkillAct.CRAFT && !count.getIngredients().isEmpty()) {
                warnings.add(where + " writes 'ingredients', which only a craft reads");
            }
            if (count.getWorth().isEmpty() && count.getIngredients().isEmpty()) {
                warnings.add(where + " names nothing it is worth, so it counts nothing");
            }
            table(warnings, where + " worth", count.getWorth());
            table(warnings, where + " ingredients", count.getIngredients());
        }
    }

    private static void table(List<String> warnings, String where, WorthTable table) {
        if (table.entries().size() > MAX_TABLE_ENTRIES) {
            warnings.add(where + " holds more than " + MAX_TABLE_ENTRIES + " entries");
        }
        for (Map.Entry<String, Double> entry : table.entries().entrySet()) {
            if (entry.getKey().trim().length() == 0) {
                warnings.add(where + " names nothing for a worth");
            }
            if (entry.getValue().doubleValue() < 0.0D) {
                warnings.add(where + " makes " + entry.getKey() + " worth less than nothing");
            }
        }
    }

    private static void perk(List<String> warnings, SkillDefinition skill,
                             PerkDefinition perk) {
        String where = "the perk " + perk.getId();
        int[] levels = perk.getLevels();
        if (levels.length == 0 || levels.length > MAX_RANKS) {
            warnings.add(where + " needs between 1 and " + MAX_RANKS + " levels, one for each rank");
        }
        int previous = SkillCurve.FIRST_LEVEL;
        for (int level : levels) {
            if (level < SkillCurve.FIRST_LEVEL || level > SkillCurve.TOP_LEVEL) {
                warnings.add(where + " needs level " + level + ", outside "
                        + SkillCurve.FIRST_LEVEL + " to " + SkillCurve.TOP_LEVEL);
            } else if (level < previous) {
                warnings.add(where + " needs a lower level for a later rank");
            }
            previous = Math.max(previous, level);
        }
        if (perk.getNeedsPerk().length() > 0) {
            PerkDefinition before = skill.perk(perk.getNeedsPerk());
            if (before == null || before == perk) {
                warnings.add(where + " needs '" + perk.getNeedsPerk()
                        + "', which is no other perk of this tree");
            } else if (perk.getNeedsRank() < 1 || perk.getNeedsRank() > before.getRanks()) {
                warnings.add(where + " needs rank " + perk.getNeedsRank() + " of "
                        + before.getId() + ", which has " + before.getRanks());
            }
        }
        PerkEffect effect = PerkEffect.fromWord(perk.getEffect());
        if (effect == null) {
            warnings.add(where + " has the effect '" + perk.getEffect() + "', which no perk has");
        } else {
            for (String param : perk.getParams().keySet()) {
                if (!effect.getParams().contains(param)) {
                    warnings.add(where + " writes the number '" + param
                            + "', which " + effect.word() + " does not read");
                }
            }
        }
        if (perk.getAmount() < 0.0D) {
            warnings.add(where + " has an amount below 0");
        }
        for (Double value : perk.getParams().values()) {
            if (value.doubleValue() < 0.0D) {
                warnings.add(where + " has a number below 0");
            }
        }
        if (perk.getWith().size() > MAX_WITH) {
            warnings.add(where + " names more than " + MAX_WITH + " things it works with");
        }
        for (String thing : perk.getWith()) {
            if (thing.trim().length() == 0) {
                warnings.add(where + " names an empty thing it works with");
            }
        }
        words(warnings, where, perk.getName(), perk.getDescription());
    }

    private static void words(List<String> warnings, String where, String name,
                              String description) {
        if (name.length() > MAX_NAME_LENGTH) {
            warnings.add(where + "'s name is longer than " + MAX_NAME_LENGTH + " characters");
        }
        if (description.length() > MAX_DESCRIPTION_LENGTH) {
            warnings.add(where + "'s description is longer than "
                    + MAX_DESCRIPTION_LENGTH + " characters");
        }
    }
}
