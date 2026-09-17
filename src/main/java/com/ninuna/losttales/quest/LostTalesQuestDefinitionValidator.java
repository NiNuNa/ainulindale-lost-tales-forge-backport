package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
/** Reports lightweight quest data warnings without preventing a world from loading. */
public final class LostTalesQuestDefinitionValidator {
    private LostTalesQuestDefinitionValidator() {}

    /** Told about a definition that is loaded but reads oddly. */
    public interface Warnings {
        void warn(String message);
    }

    public static void logWarnings(Collection<LostTalesQuestDefinition> quests) {
        validate(quests, LOG);
    }

    /**
     * Every warning the definitions raise, as the lines they would be
     * logged as. What a test asks of the bundled files.
     */
    public static List<String> describeWarnings(
            Collection<LostTalesQuestDefinition> quests) {
        final List<String> found = new ArrayList<String>();
        validate(quests, new Warnings() {
            @Override
            public void warn(String message) {
                found.add(message);
            }
        });
        return found;
    }

    public static void validate(Collection<LostTalesQuestDefinition> quests,
                                Warnings out) {
        if (quests == null || out == null) {
            return;
        }
        for (LostTalesQuestDefinition quest : quests) {
            validateQuest(quest, out);
        }
    }

    private static void validateQuest(LostTalesQuestDefinition quest, Warnings out) {
        if (quest == null) {
            return;
        }
        validateDialogue(quest, out);
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage == null) {
                continue;
            }
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                validateObjective(quest, stage, objective, out);
            }
        }
    }

    private static void validateObjective(LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, Warnings out) {
        if (objective == null) {
            return;
        }
        String type = objective.getType() == null ? "" : objective.getType().trim().toLowerCase(Locale.ROOT);
        Map<String, String> params = objective.getParams();

        switch (LostTalesQuestObjectiveType.of(type)) {
            case GATHER:
            case CRAFT:
                if (!hasAny(params, "item", "itemId", "items", "tag", "ore", "oreDict", "oredict")) {
                    warn(out, quest, stage, objective, "missing 'item' or OreDictionary-style 'tag' parameter for type '%s'", type);
                }
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                return;
            case KILL:
                if (!hasAny(params, "entity", "entityId", "type", "tag", "group")) {
                    warn(out, quest, stage, objective, "has no entity selector; it will count any killed entity");
                }
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            case GOTO:
                boolean hasMarker = hasAny(params, "marker", "markerId", "mapMarker");
                boolean hasAnyCoordinate = hasAny(params, "x", "y", "z");
                if (!hasMarker && !hasAnyCoordinate) {
                    warn(out, quest, stage, objective, "missing marker or x/y/z coordinates for type 'goto'");
                } else if (!hasMarker && (!hasAny(params, "x") || !hasAny(params, "y") || !hasAny(params, "z"))) {
                    warn(out, quest, stage, objective, "has incomplete coordinates for type 'goto'; expected x, y, and z or a marker id");
                }
                validateNumber(out, quest, stage, objective, params.get("x"), "x");
                validateNumber(out, quest, stage, objective, params.get("y"), "y");
                validateNumber(out, quest, stage, objective, params.get("z"), "z");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            case TALK:
                if (!hasAny(params, "entity", "entityId", "npc", "target")) {
                    warn(out, quest, stage, objective, "missing 'entity' parameter for type 'talk'; nobody can be spoken to");
                }
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            case DELIVER:
                if (!hasAny(params, "entity", "entityId", "npc", "target")) {
                    warn(out, quest, stage, objective, "missing 'entity' parameter for type 'deliver'; nobody can be handed anything");
                }
                if (!hasAny(params, "item", "itemId", "items", "tag", "ore", "oreDict", "oredict")) {
                    warn(out, quest, stage, objective, "missing 'item' or OreDictionary-style 'tag' parameter for type 'deliver'; nothing would change hands");
                }
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            default:
                warn(out, quest, stage, objective, "uses unknown objective type '%s'", objective.getType());
        }
    }

    /**
     * A quest's conversation: every entry has to be one
     * {@link LostTalesQuestDialogue} knows, since a misspelt one would
     * simply never be said, and a quest that is offered in conversation
     * needs a giver to have it with.
     */
    private static void validateDialogue(LostTalesQuestDefinition quest,
                                         Warnings out) {
        Map<String, String> dialogue = quest.getDialogue();
        if (dialogue.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> line : dialogue.entrySet()) {
            String key = line.getKey() == null ? ""
                    : line.getKey().trim().toLowerCase(Locale.ROOT);
            if (!DIALOGUE_LINES.contains(key)) {
                out.warn("quest=" + quest.getId() + " dialogue names '"
                        + line.getKey() + "', which nothing says");
            }
            if (line.getValue() != null
                    && line.getValue().length() > LostTalesQuestDialogue.MAX_LINE) {
                out.warn("quest=" + quest.getId() + " dialogue line '"
                        + key + "' is longer than "
                        + LostTalesQuestDialogue.MAX_LINE + " characters");
            }
        }
        LostTalesQuestDialogue spoken = LostTalesQuestDialogue.of(quest);
        if (spoken.isOffered()
                && !hasAny(quest.getInteraction(), "entity", "entityId",
                        "npc", "target")) {
            out.warn("quest=" + quest.getId() + " is offered in conversation "
                    + "but its interaction names nobody to have it with");
        }
    }

    /** The entries a conversation may hold. */
    private static final java.util.Set<String> DIALOGUE_LINES =
            new java.util.HashSet<String>(java.util.Arrays.asList(
                    LostTalesQuestDialogue.OFFER.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.MORE.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.ACCEPT.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.DECLINE.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.PROGRESS.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.HAND_IN.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.HAND_OVER.toLowerCase(Locale.ROOT),
                    LostTalesQuestDialogue.LEAVE.toLowerCase(Locale.ROOT)));

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

    private static void validateInteger(Warnings out, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String value, String key) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        try {
            Integer.parseInt(value.trim());
        } catch (Exception ignored) {
            warn(out, quest, stage, objective, "has non-integer '%s': %s", key, value);
        }
    }

    private static void validateNumber(Warnings out, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String value, String key) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        try {
            Double.parseDouble(value.trim());
        } catch (Exception ignored) {
            warn(out, quest, stage, objective, "has non-numeric '%s': %s", key, value);
        }
    }

    private static void warn(Warnings out, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String message, Object... args) {
        String formatted;
        try {
            formatted = String.format(Locale.ROOT, message, args);
        } catch (Exception ignored) {
            formatted = message;
        }
        out.warn("quest=" + (quest == null ? "<unknown>" : quest.getId())
                + " stage=" + (stage == null ? "<unknown>" : stage.getId())
                + " objective=" + (objective == null ? "<unknown>" : objective.getId())
                + " " + formatted);
    }

    /** Where {@link #logWarnings} puts what it finds. */
    private static final Warnings LOG = new Warnings() {
        @Override
        public void warn(String message) {
            FMLLog.warning("[%s] Quest objective warning: %s",
                    LostTalesMetaData.MOD_ID, message);
        }
    };
}
