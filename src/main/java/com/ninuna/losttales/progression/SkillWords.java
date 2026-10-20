package com.ninuna.losttales.progression;

import com.ninuna.losttales.progression.skill.PerkDefinition;
import com.ninuna.losttales.progression.skill.SkillDefinition;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.StatCollector;

/**
 * A skill's and a perk's words: a bundled skill's are lang lines its id
 * names ({@code skill.losttales.archery.name}, {@code
 * skill.losttales.archery.perk.steady_draw.name}), read in each player's
 * language; a server's own file writes them itself, and its words stand
 * wherever it wrote some.
 */
public final class SkillWords {
    private SkillWords() {}

    /** {@code skill.losttales.archery}: the start of every line a skill's id names. */
    public static String keyOf(String skillId) {
        return "skill." + skillId.replace(':', '.');
    }

    public static String nameKey(String skillId) {
        return keyOf(skillId) + ".name";
    }

    public static String descriptionKey(String skillId) {
        return keyOf(skillId) + ".description";
    }

    public static String perkNameKey(String skillId, String perkId) {
        return keyOf(skillId) + ".perk." + perkId + ".name";
    }

    public static String perkDescriptionKey(String skillId, String perkId) {
        return keyOf(skillId) + ".perk." + perkId + ".description";
    }

    /**
     * A skill's name in the language of the side that asks, by its id
     * alone: its lang line, else the last part of its id.
     */
    public static String localName(String skillId) {
        String key = nameKey(skillId);
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocal(key);
        }
        int colon = skillId.indexOf(':');
        String path = colon >= 0 ? skillId.substring(colon + 1) : skillId;
        return path.replace('_', ' ');
    }

    /** The skill's name as a chat line carries it: the file's own words, else its lang line. */
    public static IChatComponent name(SkillDefinition skill) {
        if (skill.getName().length() > 0) {
            return new ChatComponentText(skill.getName());
        }
        return new ChatComponentTranslation(nameKey(skill.getId()));
    }

    /** The perk's name as a chat line carries it. */
    public static IChatComponent perkName(SkillDefinition skill, PerkDefinition perk) {
        if (perk.getName().length() > 0) {
            return new ChatComponentText(perk.getName());
        }
        return new ChatComponentTranslation(perkNameKey(skill.getId(), perk.getId()));
    }
}
