package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;

import java.util.Map;

/**
 * Shared readable objective text used by both the journal and the HUD tracker.
 *
 * Keeping this tiny helper in the common quest package avoids duplicating the
 * same legacy-friendly formatting rules across multiple 1.7.10 GUI classes.
 */
public final class LostTalesQuestObjectiveTextHelper {
    private LostTalesQuestObjectiveTextHelper() {}

    public static String buildObjectiveLine(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted, boolean includeCheckbox, boolean includeDetails) {
        if (objective == null) {
            return includeCheckbox ? "- [ ] Objective" : "Objective";
        }

        int target = getObjectiveTargetCount(objective);
        int current = questCompleted ? target : currentStage && progress != null ? progress.getObjectiveProgress(objective.getId()) : 0;
        if (current > target) {
            current = target;
        }

        StringBuilder line = new StringBuilder();
        if (includeCheckbox) {
            line.append(current >= target ? "- [x] " : "- [ ] ");
        }
        line.append(getReadableObjectiveType(objective.getType())).append(": ");
        String description = objective.getDescription();
        line.append(description == null || description.length() == 0 ? objective.getId() : description);
        line.append(" (").append(current).append('/').append(target).append(')');

        if (includeDetails) {
            String details = buildObjectiveParamSummary(objective);
            if (details.length() > 0) {
                line.append(" - ").append(details);
            }
        }
        if (objective.isOptional()) {
            line.append(" optional");
        }
        return line.toString();
    }

    public static String getReadableObjectiveType(String type) {
        if (type == null || type.length() == 0) {
            return "Objective";
        }
        if ("goto".equalsIgnoreCase(type)) {
            return "Travel";
        }
        if ("gather".equalsIgnoreCase(type) || "gather_item".equalsIgnoreCase(type) || "pickup".equalsIgnoreCase(type) || "pickup_item".equalsIgnoreCase(type) || "collect".equalsIgnoreCase(type)) {
            return "Gather";
        }
        if ("craft".equalsIgnoreCase(type)) {
            return "Craft";
        }
        if ("kill".equalsIgnoreCase(type)) {
            return "Defeat";
        }
        return Character.toUpperCase(type.charAt(0)) + type.substring(1);
    }

    public static String buildObjectiveParamSummary(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null || objective.getParams().isEmpty()) {
            return "";
        }

        String type = objective.getType() == null ? "" : objective.getType();
        if ("goto".equalsIgnoreCase(type)) {
            String x = objective.getParam("x", "?");
            String y = objective.getParam("y", "?");
            String z = objective.getParam("z", "?");
            String radius = objective.getParam("radius", "");
            String dimension = objective.getParam("dimension", "");
            String marker = firstNonEmpty(objective.getParam("marker", ""), objective.getParam("markerId", ""), objective.getParam("mapMarker", ""));

            StringBuilder text = new StringBuilder();
            if (marker.length() > 0) {
                text.append("Marker ").append(marker);
            }
            if (!"?".equals(x) || !"?".equals(y) || !"?".equals(z)) {
                if (text.length() > 0) {
                    text.append(" @ ");
                } else {
                    text.append("Location ");
                }
                text.append(x).append(", ").append(y).append(", ").append(z);
            }
            if (dimension.length() > 0) {
                text.append(" in ").append(dimension);
            }
            if (radius.length() > 0) {
                text.append(" within ").append(radius).append(" blocks");
            }
            return text.toString();
        }

        if ("gather".equalsIgnoreCase(type) || "gather_item".equalsIgnoreCase(type) || "pickup".equalsIgnoreCase(type) || "pickup_item".equalsIgnoreCase(type) || "collect".equalsIgnoreCase(type) || "craft".equalsIgnoreCase(type)) {
            String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
            if (item.length() > 0) {
                return "Item " + item;
            }
            String tag = firstNonEmpty(objective.getParam("tag", ""), objective.getParam("ore", ""), objective.getParam("oreDict", ""), objective.getParam("oredict", ""));
            return tag.length() == 0 ? "" : "Tag " + tag;
        }

        if ("kill".equalsIgnoreCase(type)) {
            String entity = firstNonEmpty(objective.getParam("entity", ""), objective.getParam("entityId", ""), objective.getParam("target", ""));
            String group = firstNonEmpty(objective.getParam("tag", ""), objective.getParam("group", ""));
            String radius = objective.getParam("radius", "");
            String text = entity.length() > 0 ? "Target " + entity : group.length() > 0 ? "Group " + group : "";
            if (radius.length() > 0) {
                text += (text.length() == 0 ? "" : ", ") + "within " + radius + " blocks";
            }
            return text;
        }

        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : objective.getParams().entrySet()) {
            if ("count".equalsIgnoreCase(entry.getKey())) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    public static int getObjectiveTargetCount(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return 1;
        }
        if ("goto".equalsIgnoreCase(objective.getType())) {
            return 1;
        }
        try {
            return Math.max(1, Integer.parseInt(objective.getParam("count", "1")));
        } catch (Exception ignored) {
            return 1;
        }
    }

    public static int getObjectiveProgress(LostTalesQuestProgress progress, LostTalesQuestObjectiveDefinition objective, boolean currentStage, boolean questCompleted) {
        int target = getObjectiveTargetCount(objective);
        int current = questCompleted ? target : currentStage && progress != null && objective != null ? progress.getObjectiveProgress(objective.getId()) : 0;
        return Math.min(current, target);
    }

    private static String firstNonEmpty(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }
}
