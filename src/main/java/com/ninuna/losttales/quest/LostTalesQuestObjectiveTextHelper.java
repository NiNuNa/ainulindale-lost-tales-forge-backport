package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import net.minecraft.util.StatCollector;

/**
 * An objective's words, shared by the journal, the HUD tracker, quest
 * conversations, quest cards in the chat and missive letters. An
 * objective written without a description gets words made from its
 * kind and target. Works on both sides: a dedicated server words a
 * shared quest in its own language.
 */
public final class LostTalesQuestObjectiveTextHelper {
    private LostTalesQuestObjectiveTextHelper() {}

    /** An objective's words with how far along it is, {@code (2/5)}, and whether it is optional. */
    public static String buildObjectiveLine(LostTalesQuestProgress progress,
            LostTalesQuestObjectiveDefinition objective, boolean currentStage,
            boolean questCompleted) {
        if (objective == null) {
            return describe(null);
        }
        String line = describe(objective) + " ("
                + getObjectiveProgress(progress, objective, currentStage, questCompleted)
                + "/" + getObjectiveTargetCount(objective) + ")";
        return objective.isOptional() ? line + " "
                + StatCollector.translateToLocal("gui.losttales.quest.objective.optional")
                : line;
    }

    /** An objective's own description, or words made from its kind and target. */
    public static String describe(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return StatCollector.translateToLocal("gui.losttales.quest.objective.generic");
        }
        String description = objective.getDescription();
        if (description != null && description.length() > 0) {
            return description;
        }
        LostTalesQuestObjectiveType type = LostTalesQuestObjectiveType.of(objective);
        String count = String.valueOf(getObjectiveTargetCount(objective));
        switch (type) {
            case GOTO:
                return translate("goto");
            case GATHER:
            case CRAFT:
                return translate(type.canonicalName(), count, itemName(objective));
            case KILL:
                return translate("kill", count, targetName(objective,
                        getObjectiveTargetCount(objective) == 1 ? "enemy" : "enemies"));
            case TALK: {
                String who = targetName(objective, "");
                return who.length() == 0 ? translate("talk.anyone") : translate("talk", who);
            }
            case DELIVER: {
                String who = targetName(objective, "");
                return who.length() == 0 ? translate("deliver.anyone", count, itemName(objective))
                        : translate("deliver", who, count, itemName(objective));
            }
            default:
                return typeName(objective.getType());
        }
    }

    /** An objective kind's name, {@code Defeat} for {@code kill}; the word itself for a kind this mod does not know. */
    public static String typeName(String type) {
        LostTalesQuestObjectiveType known = LostTalesQuestObjectiveType.of(type);
        if (known != LostTalesQuestObjectiveType.UNKNOWN) {
            return translate("type." + known.canonicalName());
        }
        String word = type == null ? "" : type.trim();
        return word.length() == 0 ? describe(null)
                : Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }

    public static int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return 1;
        }
        if (LostTalesQuestObjectiveType.of(objective).countsToOne()) {
            return 1;
        }
        return Math.max(1, LostTalesQuestParams.parseInt(
                objective.getParam("count", "1"), 1));
    }

    public static int getObjectiveProgress(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        int target = getObjectiveTargetCount(objective);
        int current = questCompleted ? target : currentStage && progress != null && objective != null ? progress.getObjectiveProgress(objective.getId()) : 0;
        return Math.min(current, target);
    }

    /** The item a gathering, crafting or delivery asks for, by its name; "item" or "items" for none named. */
    private static String itemName(LostTalesQuestObjectiveDefinition objective) {
        String item = objective.getParam("item", "").trim();
        if (item.length() > 0) {
            return LostTalesQuestRewardText.itemPhrase(item);
        }
        return translate(getObjectiveTargetCount(objective) == 1 ? "noun.item" : "noun.items");
    }

    /**
     * Who the objective names, for a reader: the first creature kind its
     * {@code entity} lists, by the game's name for it where the game has
     * one, else the kind made readable; else the first group its
     * {@code group} lists. {@code noun} stands for nobody named.
     */
    private static String targetName(LostTalesQuestObjectiveDefinition objective,
            String noun) {
        String target = LostTalesQuestParams.value(objective.getParams(), "entity");
        if (target.length() == 0) {
            target = LostTalesQuestParams.value(objective.getParams(), "group");
        }
        int comma = target.indexOf(',');
        if (comma >= 0) {
            target = target.substring(0, comma).trim();
        }
        if (target.length() == 0) {
            return noun.length() == 0 ? "" : translate("noun." + noun);
        }
        String key = "entity." + target + ".name";
        if (StatCollector.canTranslate(key)) {
            return StatCollector.translateToLocal(key);
        }
        // A mod's creature is registered as mod.Kind; the kind is what reads.
        int dot = target.lastIndexOf('.');
        if (dot >= 0 && dot + 1 < target.length()) {
            target = target.substring(dot + 1);
        }
        return target.replace('-', ' ').replace('_', ' ');
    }

    private static String translate(String key, Object... args) {
        return StatCollector.translateToLocalFormatted("gui.losttales.quest.objective." + key, args);
    }
}
