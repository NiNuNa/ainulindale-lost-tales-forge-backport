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
        String description = objective.getDescription();
        line.append(description == null || description.length() == 0 ? buildFallbackObjectiveText(objective) : description);
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

    private static String buildFallbackObjectiveText(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null) {
            return "Objective";
        }
        int target = getObjectiveTargetCount(objective);
        String type = objective.getType() == null ? "" : objective.getType();
        switch (LostTalesQuestObjectiveType.of(type)) {
            case GOTO:
                return "Travel to the destination.";
            case GATHER: {
                String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
                return "Gather " + target + (item.length() > 0 ? " " + prettifyResourceName(item) : " item" + (target == 1 ? "" : "s")) + ".";
            }
            case CRAFT: {
                String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
                return "Craft " + target + (item.length() > 0 ? " " + prettifyResourceName(item) : " item" + (target == 1 ? "" : "s")) + ".";
            }
            case KILL: {
                String entity = firstNonEmpty(objective.getParam("entity", ""), objective.getParam("entityId", ""), objective.getParam("target", ""), objective.getParam("group", ""));
                return "Defeat " + target + (entity.length() > 0 ? " " + prettifyResourceName(entity) : " enem" + (target == 1 ? "y" : "ies")) + ".";
            }
            case TALK: {
                String who = objectiveEntityName(objective);
                return "Speak to " + (who.length() > 0 ? who : "the person named") + ".";
            }
            case DELIVER: {
                String who = objectiveEntityName(objective);
                String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""));
                return "Bring " + (who.length() > 0 ? who + " " : "")
                        + target + (item.length() > 0 ? " " + prettifyResourceName(item) : " item" + (target == 1 ? "" : "s")) + ".";
            }
            default:
                return getReadableObjectiveType(type);
        }
    }

    /** Who a talk or delivery objective names, prettified; empty for none. */
    private static String objectiveEntityName(
            LostTalesQuestObjectiveDefinition objective) {
        String entity = firstNonEmpty(objective.getParam("entity", ""),
                objective.getParam("entityId", ""),
                objective.getParam("npc", ""),
                objective.getParam("target", ""));
        if (entity.length() == 0) {
            return "";
        }
        // A selector may list several spellings of the same person; the
        // first is the one written for a reader.
        int comma = entity.indexOf(',');
        return prettifyResourceName(comma < 0 ? entity
                : entity.substring(0, comma));
    }

    private static String prettifyResourceName(String value) {
        if (value == null) {
            return "";
        }
        String text = value.trim();
        int colon = text.indexOf(':');
        if (colon >= 0 && colon + 1 < text.length()) {
            text = text.substring(colon + 1);
        }
        int at = text.indexOf('@');
        if (at >= 0) {
            text = text.substring(0, at);
        }
        text = text.replace('-', ' ').replace('_', ' ');
        return text;
    }

    public static String getReadableObjectiveType(String type) {
        if (type == null || type.length() == 0) {
            return "Objective";
        }
        switch (LostTalesQuestObjectiveType.of(type)) {
            case GOTO:
                return "Travel";
            case GATHER:
                return "Gather";
            case CRAFT:
                return "Craft";
            case KILL:
                return "Defeat";
            case TALK:
                return "Speak";
            case DELIVER:
                return "Deliver";
            default:
                return Character.toUpperCase(type.charAt(0)) + type.substring(1);
        }
    }

    public static String buildObjectiveParamSummary(LostTalesQuestObjectiveDefinition objective) {
        if (objective == null || objective.getParams().isEmpty()) {
            return "";
        }

        String type = objective.getType() == null ? "" : objective.getType();
        if (LostTalesQuestObjectiveType.GOTO.is(objective)) {
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

        if (LostTalesQuestObjectiveType.GATHER.is(objective)
                || LostTalesQuestObjectiveType.CRAFT.is(objective)) {
            String item = firstNonEmpty(objective.getParam("item", ""), objective.getParam("itemId", ""), objective.getParam("target", ""));
            if (item.length() > 0) {
                return "Item " + item;
            }
            String tag = firstNonEmpty(objective.getParam("tag", ""), objective.getParam("ore", ""), objective.getParam("oreDict", ""), objective.getParam("oredict", ""));
            return tag.length() == 0 ? "" : "Tag " + tag;
        }

        if (LostTalesQuestObjectiveType.of(objective).isNpcVisit()) {
            String who = firstNonEmpty(objective.getParam("entity", ""),
                    objective.getParam("entityId", ""),
                    objective.getParam("npc", ""),
                    objective.getParam("target", ""));
            String item = firstNonEmpty(objective.getParam("item", ""),
                    objective.getParam("itemId", ""));
            StringBuilder text = new StringBuilder();
            if (who.length() > 0) {
                text.append("Recipient ").append(who);
            }
            if (item.length() > 0) {
                text.append(text.length() > 0 ? ", item " : "Item ").append(item);
            }
            return text.toString();
        }

        if (LostTalesQuestObjectiveType.KILL.is(objective)) {
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
        if (LostTalesQuestObjectiveType.of(objective).countsToOne()) {
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
