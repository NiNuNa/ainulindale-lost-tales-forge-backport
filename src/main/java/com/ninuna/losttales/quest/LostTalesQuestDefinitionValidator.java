package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.Collection;
import java.util.Locale;
import java.util.Map;
/** Logs lightweight quest data warnings without preventing older worlds from loading. */
public final class LostTalesQuestDefinitionValidator {
    private LostTalesQuestDefinitionValidator() {}

    public static void logWarnings(Collection<LostTalesQuestDefinition> quests) {
        if (quests == null) {
            return;
        }
        for (LostTalesQuestDefinition quest : quests) {
            validateQuest(quest);
        }
    }

    private static void validateQuest(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return;
        }
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage == null) {
                continue;
            }
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                validateObjective(quest, stage, objective);
            }
        }
    }

    private static void validateObjective(LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return;
        }
        String type = objective.getType() == null ? "" : objective.getType().trim().toLowerCase(Locale.ROOT);
        Map<String, String> params = objective.getParams();

        if ("gather".equals(type) || "gather_item".equals(type) || "pickup".equals(type) || "pickup_item".equals(type) || "craft".equals(type)) {
            if (!hasAny(params, "item", "itemId", "items", "tag", "ore", "oreDict", "oredict")) {
                warn(quest, stage, objective, "missing 'item' or OreDictionary-style 'tag' parameter for type '%s'", type);
            }
            validateInteger(quest, stage, objective, params.get("count"), "count");
            return;
        }

        if ("kill".equals(type)) {
            if (!hasAny(params, "entity", "entityId", "type", "tag", "group")) {
                warn(quest, stage, objective, "has no entity selector; it will count any killed entity");
            }
            validateInteger(quest, stage, objective, params.get("count"), "count");
            validateNumber(quest, stage, objective, params.get("radius"), "radius");
            return;
        }

        if ("goto".equals(type)) {
            boolean hasMarker = hasAny(params, "marker", "markerId", "mapMarker");
            boolean hasAnyCoordinate = hasAny(params, "x", "y", "z");
            if (!hasMarker && !hasAnyCoordinate) {
                warn(quest, stage, objective, "missing marker or x/y/z coordinates for type 'goto'");
            } else if (!hasMarker && (!hasAny(params, "x") || !hasAny(params, "y") || !hasAny(params, "z"))) {
                warn(quest, stage, objective, "has incomplete coordinates for type 'goto'; expected x, y, and z or a marker id");
            }
            validateNumber(quest, stage, objective, params.get("x"), "x");
            validateNumber(quest, stage, objective, params.get("y"), "y");
            validateNumber(quest, stage, objective, params.get("z"), "z");
            validateNumber(quest, stage, objective, params.get("radius"), "radius");
            return;
        }

        warn(quest, stage, objective, "uses unknown objective type '%s'", objective.getType());
    }

    private static boolean hasAny(Map<String, String> params, String... keys) {
        if (params == null || keys == null) {
            return false;
        }
        for (String key : keys) {
            String value = params.get(key);
            if (value != null && value.trim().length() > 0) {
                return true;
            }
        }
        return false;
    }

    private static void validateInteger(LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String value, String key) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        try {
            Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            warn(quest, stage, objective, "has non-integer '%s': %s", key, value);
        }
    }

    private static void validateNumber(LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String value, String key) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        try {
            Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            warn(quest, stage, objective, "has non-numeric '%s': %s", key, value);
        }
    }

    private static void warn(LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String message, Object... args) {
        String formatted;
        try {
            formatted = String.format(Locale.ROOT, message, args);
        } catch (Exception ignored) {
            formatted = message;
        }
        FMLLog.warning("[%s] Quest objective warning: quest=%s stage=%s objective=%s %s",
                LostTalesMetaData.MOD_ID,
                quest == null ? "<unknown>" : quest.getId(),
                stage == null ? "<unknown>" : stage.getId(),
                objective == null ? "<unknown>" : objective.getId(),
                formatted);
    }
}
