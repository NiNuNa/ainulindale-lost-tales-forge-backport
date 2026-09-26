package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
/** Small server-side evaluator for optional quest JSON prerequisites. */
public final class LostTalesQuestPrerequisiteHelper {

    private LostTalesQuestPrerequisiteHelper() {}

    /**
     * Why the player may not start {@code quest} yet, as the Server says
     * it, or null when every prerequisite holds.
     */
    public static IChatComponent refusalOf(LostTalesQuestDefinition quest, EntityPlayer player, LostTalesQuestPlayerData data) {
        if (quest == null) {
            return new ChatComponentTranslation("chat.losttales.quest.no_such");
        }
        if (data == null) {
            return new ChatComponentTranslation("chat.losttales.quest.no_data");
        }

        Map<String, String> prerequisites = quest.getPrerequisites();
        if (prerequisites.isEmpty()) {
            return null;
        }

        String completed = LostTalesQuestParams.value(prerequisites, "completed");
        if (!hasAllCompleted(data, completed)) {
            return new ChatComponentTranslation("chat.losttales.quest.requires.completed");
        }

        String active = LostTalesQuestParams.value(prerequisites, "active");
        if (!hasAllActive(data, active)) {
            return new ChatComponentTranslation("chat.losttales.quest.requires.active");
        }

        String notCompleted = LostTalesQuestParams.value(prerequisites, "notCompleted");
        if (hasAnyCompleted(data, notCompleted)) {
            return new ChatComponentTranslation("chat.losttales.quest.requires.not_completed");
        }

        String notActive = LostTalesQuestParams.value(prerequisites, "notActive");
        if (hasAnyActive(data, notActive)) {
            return new ChatComponentTranslation("chat.losttales.quest.requires.not_active");
        }

        String levelValue = LostTalesQuestParams.value(prerequisites, "minLevel");
        int requiredLevel = LostTalesQuestParams.parseInt(levelValue, -1);
        if (requiredLevel >= 0 && player != null && player.experienceLevel < requiredLevel) {
            return new ChatComponentTranslation("chat.losttales.quest.requires.level",
                    Integer.valueOf(requiredLevel));
        }

        return null;
    }

    private static boolean hasAllCompleted(LostTalesQuestPlayerData data, String questList) {
        for (String questId : splitList(questList)) {
            if (!data.isQuestCompleted(questId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAllActive(LostTalesQuestPlayerData data, String questList) {
        for (String questId : splitList(questList)) {
            if (!data.isQuestActive(questId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasAnyCompleted(LostTalesQuestPlayerData data, String questList) {
        for (String questId : splitList(questList)) {
            if (data.isQuestCompleted(questId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyActive(LostTalesQuestPlayerData data, String questList) {
        for (String questId : splitList(questList)) {
            if (data.isQuestActive(questId)) {
                return true;
            }
        }
        return false;
    }

    private static String[] splitList(String value) {
        if (value == null || value.trim().length() == 0) {
            return new String[0];
        }
        String[] raw = value.replace(';', ',').split(",");
        java.util.List<String> cleaned = new java.util.ArrayList<String>();
        for (String entry : raw) {
            if (entry != null && entry.trim().length() > 0) {
                cleaned.add(entry.trim());
            }
        }
        return cleaned.toArray(new String[cleaned.size()]);
    }
}
