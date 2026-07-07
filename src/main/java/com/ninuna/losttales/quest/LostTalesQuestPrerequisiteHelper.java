package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import java.util.Map;
import net.minecraft.entity.player.EntityPlayer;
/** Small server-side evaluator for optional quest JSON prerequisites. */
public final class LostTalesQuestPrerequisiteHelper {

    private LostTalesQuestPrerequisiteHelper() {}

    public static boolean areMet(LostTalesQuestDefinition quest, EntityPlayer player, LostTalesQuestPlayerData data) {
        return getFailureReason(quest, player, data) == null;
    }

    public static String getFailureReason(LostTalesQuestDefinition quest, EntityPlayer player, LostTalesQuestPlayerData data) {
        if (quest == null) {
            return "Unknown quest.";
        }
        if (data == null) {
            return "Quest data is not available.";
        }

        Map<String, String> prerequisites = quest.getPrerequisites();
        if (prerequisites.isEmpty()) {
            return null;
        }

        String completed = firstNonEmpty(prerequisites.get("completed"), prerequisites.get("completedQuests"), prerequisites.get("requiresCompleted"));
        if (!hasAllCompleted(data, completed)) {
            return "You have not completed the required quest yet.";
        }

        String active = firstNonEmpty(prerequisites.get("active"), prerequisites.get("activeQuests"), prerequisites.get("requiresActive"));
        if (!hasAllActive(data, active)) {
            return "A required quest is not active.";
        }

        String notCompleted = firstNonEmpty(prerequisites.get("notCompleted"), prerequisites.get("not_completed"), prerequisites.get("forbidCompleted"));
        if (hasAnyCompleted(data, notCompleted)) {
            return "This quest is no longer available.";
        }

        String notActive = firstNonEmpty(prerequisites.get("notActive"), prerequisites.get("not_active"), prerequisites.get("forbidActive"));
        if (hasAnyActive(data, notActive)) {
            return "Another conflicting quest is already active.";
        }

        String levelValue = firstNonEmpty(prerequisites.get("minLevel"), prerequisites.get("minExperienceLevel"), prerequisites.get("level"));
        int requiredLevel = parseInt(levelValue, -1);
        if (requiredLevel >= 0 && player != null && player.experienceLevel < requiredLevel) {
            return "You need level " + requiredLevel + " to start this quest.";
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

    private static String firstNonEmpty(String a, String b, String c) {
        if (a != null && a.trim().length() > 0) return a;
        if (b != null && b.trim().length() > 0) return b;
        if (c != null && c.trim().length() > 0) return c;
        return "";
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }
}
