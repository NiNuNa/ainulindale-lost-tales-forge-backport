package com.ninuna.losttales.achievement;

import com.ninuna.losttales.faction.ELostTalesFaction;
import com.ninuna.losttales.util.LostTalesUtil;
import lotr.common.LOTRAchievement;
import net.minecraft.init.Blocks;

public enum ELostTalesAchievement {
    ENTER_MOON_ELF_BIOME(new LOTRAchievement(Category.MOON_ELVES.getCategory(), 0, Blocks.packed_ice, "enterMoonElfBiome").setBiomeAchievement());

    private final LOTRAchievement achievement;

    ELostTalesAchievement(LOTRAchievement achievement) {
        this.achievement = achievement;
    }

    public static void initAndRegisterAchievements() {
        for (ELostTalesAchievement a : ELostTalesAchievement.values()) {}
    }

    public LOTRAchievement getAchievement() {
        return achievement;
    }

    public enum Category {
        MOON_ELVES(LostTalesUtil.addAchievementCategory(ELostTalesFaction.MOON_ELVES.getFaction().codeName(), ELostTalesFaction.MOON_ELVES.getFaction())),
        SUN_ELVES(LostTalesUtil.addAchievementCategory(ELostTalesFaction.SUN_ELVES.getFaction().codeName(), ELostTalesFaction.SUN_ELVES.getFaction())),
        LOSSOTH(LostTalesUtil.addAchievementCategory(ELostTalesFaction.LOSSOTH.getFaction().codeName(), ELostTalesFaction.LOSSOTH.getFaction())),
        MORIA_GOBLINS(LostTalesUtil.addAchievementCategory(ELostTalesFaction.MORIA_GOBLINS.getFaction().codeName(), ELostTalesFaction.MORIA_GOBLINS.getFaction())),
        THARBAD(LostTalesUtil.addAchievementCategory(ELostTalesFaction.THARBAD.getFaction().codeName(), ELostTalesFaction.THARBAD.getFaction())),
        BLUE_GOBLINS(LostTalesUtil.addAchievementCategory(ELostTalesFaction.BLUE_GOBLINS.getFaction().codeName(), ELostTalesFaction.BLUE_GOBLINS.getFaction()));

        private final LOTRAchievement.Category category;

        Category(LOTRAchievement.Category category) {
            this.category = category;
        }

        public LOTRAchievement.Category getCategory() {
            return category;
        }
    }
}