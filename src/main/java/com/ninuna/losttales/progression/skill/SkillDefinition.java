package com.ninuna.losttales.progression.skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A skill as its data file writes it (PROG-01): what counts toward it,
 * its perk tree (PROG-06), the item its page shows it by and where it
 * stands among the others. A bundled skill's words are lang lines named
 * by its id; a server's own file may write them itself.
 */
public final class SkillDefinition {
    private final String id;
    private final String icon;
    private final int order;
    private final String name;
    private final String description;
    private final List<SkillCount> counts;
    private final List<PerkDefinition> perks;

    public SkillDefinition(String id, String icon, int order, String name,
                           String description, List<SkillCount> counts,
                           List<PerkDefinition> perks) {
        this.id = id == null ? "" : id;
        this.icon = icon == null ? "" : icon;
        this.order = order;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.counts = Collections.unmodifiableList(counts == null
                ? new ArrayList<SkillCount>() : new ArrayList<SkillCount>(counts));
        this.perks = Collections.unmodifiableList(perks == null
                ? new ArrayList<PerkDefinition>() : new ArrayList<PerkDefinition>(perks));
    }

    public String getId() {
        return this.id;
    }

    public String getIcon() {
        return this.icon;
    }

    public int getOrder() {
        return this.order;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public List<SkillCount> getCounts() {
        return this.counts;
    }

    public List<PerkDefinition> getPerks() {
        return this.perks;
    }

    public PerkDefinition perk(String perkId) {
        for (PerkDefinition perk : this.perks) {
            if (perk.getId().equals(perkId)) {
                return perk;
            }
        }
        return null;
    }

    /** The weapons the skill's hits are done with, which its perks work with unless they name others. */
    public List<String> weapons() {
        List<String> weapons = new ArrayList<String>();
        for (SkillCount count : this.counts) {
            if (count.getAct().takesWeapons()) {
                for (String weapon : count.getWeapons()) {
                    if (!weapons.contains(weapon)) {
                        weapons.add(weapon);
                    }
                }
            }
        }
        return weapons;
    }

    /** The same skill with words in place of the file's own. */
    public SkillDefinition worded(String name, String description,
                                  List<PerkDefinition> wordedPerks) {
        return new SkillDefinition(this.id, this.icon, this.order, name,
                description, this.counts, wordedPerks);
    }
}
