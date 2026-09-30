package com.ninuna.losttales.quest;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.quest.world.WorldQuestRules;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The checks every quest file is held to, bundled or the server's own. A
 * bundled quest with a warning still loads and the warning is logged; a
 * server's file with one is left out ({@link ServerQuestFiles}), and so is
 * a world quest from running.
 *
 * <p>A quest needs an id of at most {@link LostTalesQuestIds#MAX_BYTES}
 * bytes, a start mode of the four words, and stages. Each stage needs an
 * id of its own and at least one objective; each objective an id no other
 * objective of the quest has, a type of the six words, and what its type
 * asks for. Every journal line names a stage.</p>
 */
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
        if (!LostTalesQuestIds.fits(quest.getId())) {
            out.warn("quest=" + quest.getId() + " has an id longer than "
                    + LostTalesQuestIds.MAX_BYTES + " bytes");
        }
        if (!quest.isKnownStartMode()) {
            out.warn("quest=" + quest.getId() + " uses unknown startMode '"
                    + quest.getStartMode() + "'; it is item, interaction, any"
                    + " or locked");
        }
        validateDialogue(quest, out);
        validateInteraction(quest, out);
        for (String problem : WorldQuestRules.problems(quest)) {
            out.warn("quest=" + quest.getId() + " " + problem);
        }
        if (quest.getStages().isEmpty()) {
            out.warn("quest=" + quest.getId() + " has no stages");
        }
        Set<String> stageIds = new HashSet<String>();
        Set<String> objectiveIds = new HashSet<String>();
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage == null) {
                continue;
            }
            String stageId = stage.getId() == null ? "" : stage.getId();
            if (stageId.length() == 0) {
                out.warn("quest=" + quest.getId() + " has a stage without an id");
            } else if (!stageIds.add(stageId)) {
                out.warn("quest=" + quest.getId() + " has two stages with the id '"
                        + stageId + "'");
            }
            if (stage.getObjectives().isEmpty()) {
                out.warn("quest=" + quest.getId() + " stage=" + stageId
                        + " asks for nothing");
            }
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (objective == null) {
                    continue;
                }
                String objectiveId = objective.getId() == null ? "" : objective.getId();
                if (objectiveId.length() == 0) {
                    warn(out, quest, stage, objective, "has no id");
                } else if (!objectiveIds.add(objectiveId)) {
                    warn(out, quest, stage, objective,
                            "shares its id with another objective; they would share one count");
                } else if (LostTalesQuestIds.utf8Bytes(objectiveId) > LostTalesQuestIds.MAX_BYTES) {
                    warn(out, quest, stage, objective, "has an id longer than %d bytes",
                            Integer.valueOf(LostTalesQuestIds.MAX_BYTES));
                }
                validateObjective(quest, stage, objective, out);
            }
        }
        for (String logged : quest.getJournalLog().keySet()) {
            if (!stageIds.contains(logged)) {
                out.warn("quest=" + quest.getId() + " journalLog names stage '"
                        + logged + "', which the quest does not have");
            }
        }
    }

    private static void validateObjective(LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, Warnings out) {
        Map<String, String> params = objective.getParams();
        LostTalesQuestObjectiveType type = LostTalesQuestObjectiveType.of(objective);
        switch (type) {
            case GATHER:
            case CRAFT:
                validateItems(out, quest, stage, objective, type);
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                return;
            case KILL:
                validateWhom(out, quest, stage, objective, "defeat");
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            case GOTO:
                boolean hasMarker = hasAny(params, "marker");
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
                validateWhom(out, quest, stage, objective, "speak to");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            case DELIVER:
                validateWhom(out, quest, stage, objective, "hand anything to");
                validateItems(out, quest, stage, objective, type);
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            default:
                warn(out, quest, stage, objective, "uses unknown objective type '%s'", objective.getType());
        }
    }

    /** An item objective names its items by {@code item} or {@code ore}. */
    private static void validateItems(Warnings out, LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective,
            LostTalesQuestObjectiveType type) {
        if (!hasAny(objective.getParams(), "item", "ore")) {
            warn(out, quest, stage, objective,
                    "missing 'item' or OreDictionary 'ore' parameter for type '%s'",
                    type.canonicalName());
        }
    }

    /** Whom an objective names: an {@code entity} or a {@code group}, each group one of the five. */
    private static void validateWhom(Warnings out, LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective,
            String doing) {
        if (!LostTalesQuestObjectiveMatcher.namesWhom(objective.getParams())) {
            warn(out, quest, stage, objective,
                    "names no 'entity' or 'group'; nobody is there to %s", doing);
        }
        String groups = LostTalesQuestParams.value(objective.getParams(), "group");
        if (groups.length() == 0) {
            return;
        }
        for (String group : groups.split(",")) {
            if (!LostTalesQuestObjectiveMatcher.isGroup(group.trim())) {
                warn(out, quest, stage, objective,
                        "names the group '%s'; the groups are living, player, hostile, animal and npc",
                        group.trim());
            }
        }
    }

    /**
     * Who or what starts the quest by being touched: an {@code entity}, or
     * a {@code block} with its {@code meta}, {@code dimension},
     * {@code x}, {@code y}, {@code z} and {@code radius}. Nothing else is
     * read, so any other key is a misspelling.
     */
    private static void validateInteraction(LostTalesQuestDefinition quest,
                                            Warnings out) {
        for (String key : quest.getInteraction().keySet()) {
            if (!INTERACTION_KEYS.contains(key)) {
                out.warn("quest=" + quest.getId() + " interaction names '" + key
                        + "', which nothing reads");
            }
        }
    }

    /** The keys an interaction block may hold. */
    private static final Set<String> INTERACTION_KEYS = new HashSet<String>(
            Arrays.asList("entity", "block", "meta", "dimension", "x", "y", "z",
                    "radius"));

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
            String key = line.getKey() == null ? "" : line.getKey().trim();
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
                && !hasAny(quest.getInteraction(), "entity")) {
            out.warn("quest=" + quest.getId() + " is offered in conversation "
                    + "but its interaction names nobody to have it with");
        }
    }

    /** The entries a conversation may hold. */
    private static final Set<String> DIALOGUE_LINES = new HashSet<String>(
            Arrays.asList(LostTalesQuestDialogue.OFFER,
                    LostTalesQuestDialogue.MORE,
                    LostTalesQuestDialogue.ACCEPT,
                    LostTalesQuestDialogue.DECLINE,
                    LostTalesQuestDialogue.PROGRESS,
                    LostTalesQuestDialogue.HAND_IN,
                    LostTalesQuestDialogue.HAND_OVER,
                    LostTalesQuestDialogue.LEAVE));

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
        } catch (NumberFormatException notANumber) {
            warn(out, quest, stage, objective, "has non-integer '%s': %s", key, value);
        }
    }

    private static void validateNumber(Warnings out, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String value, String key) {
        if (value == null || value.trim().length() == 0) {
            return;
        }
        try {
            Double.parseDouble(value.trim());
        } catch (NumberFormatException notANumber) {
            warn(out, quest, stage, objective, "has non-numeric '%s': %s", key, value);
        }
    }

    private static void warn(Warnings out, LostTalesQuestDefinition quest, LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective, String message, Object... args) {
        out.warn("quest=" + (quest == null ? "<unknown>" : quest.getId())
                + " stage=" + (stage == null ? "<unknown>" : stage.getId())
                + " objective=" + (objective == null ? "<unknown>" : objective.getId())
                + " " + String.format(Locale.ROOT, message, args));
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
