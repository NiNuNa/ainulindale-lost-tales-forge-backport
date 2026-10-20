package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.progression.deed.DeedCategory;
import com.ninuna.losttales.progression.deed.DeedDefinition;
import com.ninuna.losttales.progression.deed.DeedWords;
import com.ninuna.losttales.util.LostTalesUtil;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.GameData;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lotr.common.LOTRAchievement;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRTitle;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The bundled deeds as LOTR achievements (PROG-11): each in a Lost Tales
 * category of LOTR's achievement screen, announced as LOTR announces its
 * own, and, where its file asks, unlocking a LOTR title picked in LOTR's
 * title menu (Nils, 2026-10-19). Made once as the mod loads, on both
 * sides in the bundled files' order, so client and server agree on every
 * id; a server's own deeds are none of this.
 */
public final class LotrDeedAchievements {
    private static final Map<DeedCategory, LOTRAchievement.Category> CATEGORIES =
            new EnumMap<DeedCategory, LOTRAchievement.Category>(DeedCategory.class);
    private static final Map<String, LOTRAchievement> BY_DEED =
            new LinkedHashMap<String, LOTRAchievement>();
    private static boolean made;

    private LotrDeedAchievements() {}

    /** Makes the categories, achievements and titles of the bundled deeds; once. */
    public static synchronized void make(List<DeedDefinition> bundled) {
        if (made) {
            return;
        }
        made = true;
        for (DeedCategory category : DeedCategory.values()) {
            CATEGORIES.put(category, LostTalesUtil.addAchievementCategory(
                    category.lotrName(), category.colour()));
        }
        Map<DeedCategory, Integer> nextId = new EnumMap<DeedCategory, Integer>(DeedCategory.class);
        for (DeedDefinition deed : bundled) {
            DeedCategory category = DeedCategory.fromWord(deed.getCategory());
            if (category == null) {
                continue;
            }
            Integer id = nextId.get(category);
            int achievementId = id == null ? 1 : id.intValue();
            nextId.put(category, Integer.valueOf(achievementId + 1));
            try {
                LOTRAchievement achievement = new LOTRAchievement(CATEGORIES.get(category),
                        achievementId, icon(deed.getIcon()), DeedWords.achievementName(deed.getId()));
                BY_DEED.put(deed.getId(), achievement);
                if (deed.hasTitle()) {
                    new LOTRTitle(DeedWords.achievementName(deed.getId()), achievement);
                }
            } catch (RuntimeException clash) {
                FMLLog.warning("[%s] The deed %s could not become a LOTR achievement: %s",
                        LostTalesMetaData.MOD_ID, deed.getId(), clash.toString());
            }
        }
    }

    /** Whether the deed is a LOTR achievement: a bundled one. */
    public static synchronized boolean isAchievement(String deedId) {
        return BY_DEED.containsKey(deedId);
    }

    public static synchronized Map<String, LOTRAchievement> achievements() {
        return Collections.unmodifiableMap(BY_DEED);
    }

    /** Gives the player the deed's LOTR achievement, which LOTR announces as its own. */
    public static void award(EntityPlayerMP player, String deedId) {
        LOTRAchievement achievement;
        synchronized (LotrDeedAchievements.class) {
            achievement = BY_DEED.get(deedId);
        }
        if (achievement == null) {
            return;
        }
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data != null) {
            data.addAchievement(achievement);
        }
    }

    private static ItemStack icon(String name) {
        Object item = name.length() == 0 ? null : GameData.getItemRegistry().getObject(name);
        if (item instanceof Item) {
            return new ItemStack((Item) item);
        }
        Object block = name.length() == 0 ? null : GameData.getBlockRegistry().getObject(name);
        if (block instanceof Block && Item.getItemFromBlock((Block) block) != null) {
            return new ItemStack((Block) block);
        }
        return new ItemStack(Items.book);
    }
}
