package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.world.WorldQuestRules;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The checks every quest file is held to, bundled or the server's own
 * (QUEST-04): a file with any warning is left out
 * ({@link BundledQuestFiles}, {@link ServerQuestFiles}), and a world quest
 * with one never runs.
 *
 * <p>A quest needs an id of at most {@link LostTalesQuestIds#MAX_BYTES}
 * bytes, a start mode of the four words, and stages. Each stage needs an
 * id of its own and at least one objective; each objective an id no other
 * objective of the quest has, a type of the six words, and what its type
 * asks for. Only a required gather says {@code take}, and only true or
 * false. Every journal line names a stage. Every key in params, rewards,
 * prerequisites and interaction is one something reads, every number is
 * one, and every list is parted by commas.</p>
 */
public final class LostTalesQuestDefinitionValidator {
    private LostTalesQuestDefinitionValidator() {}

    /** Told about a definition that is loaded but reads oddly. */
    public interface Warnings {
        void warn(String message);
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

    /**
     * Every warning one quest file raises: {@code unread}, what reading it
     * found nothing reads, then what the checker finds in what was read.
     * A file with any is left out (QUEST-04).
     */
    public static List<String> warningsOf(LostTalesQuestDefinition quest,
                                          List<String> unread) {
        List<String> found = new ArrayList<String>();
        if (unread != null) {
            found.addAll(unread);
        }
        found.addAll(describeWarnings(Collections.singletonList(quest)));
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
        validateConversation(quest, out);
        validateInteraction(quest, out);
        validatePrerequisites(quest, out);
        validateRewards(quest.getRewards(), "quest=" + quest.getId()
                + " rewards", out);
        validatePick(quest, out);
        validateRules(quest, out);
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
            validateTimeLimit(stage.getRules(), "quest=" + quest.getId()
                    + " stage=" + stageId, out);
            validateBranch(quest, stage, out);
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
        validateParams(out, quest, stage, objective);
        validateTake(out, quest, stage, objective, type);
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
                validateKillPlace(out, quest, stage, objective);
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
                if (isBoardDelivery(params)) {
                    if (!quest.isWorldQuest()) {
                        warn(out, quest, stage, objective,
                                "is handed in at a board, which only a world quest's delivery is");
                    }
                } else {
                    validateWhom(out, quest, stage, objective, "hand anything to");
                }
                validateItems(out, quest, stage, objective, type);
                validateInteger(out, quest, stage, objective, params.get("count"), "count");
                validateNumber(out, quest, stage, objective, params.get("radius"), "radius");
                return;
            default:
                warn(out, quest, stage, objective, "uses unknown objective type '%s'", objective.getType());
        }
    }

    /**
     * Every param is one an objective reads; {@code fellowshipShared} says
     * true or false; an {@code outcome} and the {@code reward.*} keys are
     * an optional objective's, the only kind that writes or pays them.
     */
    private static void validateParams(Warnings out, LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective) {
        Map<String, String> params = objective.getParams();
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> param : params.entrySet()) {
            String key = param.getKey();
            if (key.startsWith(REWARD_PREFIX)) {
                rewards.put(key.substring(REWARD_PREFIX.length()), param.getValue());
            } else if (!PARAM_KEYS.contains(key)) {
                warn(out, quest, stage, objective,
                        "names the parameter '%s', which nothing reads", key);
            }
        }
        if (params.containsKey("fellowshipShared")) {
            String shared = LostTalesQuestParams.value(params, "fellowshipShared");
            if (!"true".equals(shared) && !"false".equals(shared)) {
                warn(out, quest, stage, objective,
                        "has 'fellowshipShared' %s; it is true or false", shared);
            }
        }
        if (objective.isOptional()) {
            String where = "quest=" + quest.getId() + " stage="
                    + stage.getId() + " objective=" + objective.getId()
                    + " reward";
            validateRewards(rewards, where, out);
            if (rewards.containsKey(LostTalesQuestRewards.PICK)) {
                out.warn(where + " names a 'pick', which only the quest's own"
                        + " rewards offer, at its hand-in");
            }
        } else {
            if (params.containsKey("outcome")) {
                warn(out, quest, stage, objective,
                        "names 'outcome', which only an optional objective writes");
            }
            if (!rewards.isEmpty()) {
                warn(out, quest, stage, objective,
                        "names a reward, which only an optional objective pays");
            }
        }
    }

    /**
     * A reward block, the quest's or an optional objective's: every key
     * one a reward reads, every amount a whole number, every item named.
     */
    private static void validateRewards(Map<String, String> rewards,
                                        String where, Warnings out) {
        for (Map.Entry<String, String> reward : rewards.entrySet()) {
            String key = reward.getKey();
            String value = reward.getValue() == null ? "" : reward.getValue().trim();
            if (!REWARD_KEYS.contains(key)) {
                out.warn(where + " names '" + key + "', which nothing pays");
            } else if (INTEGER_REWARDS.contains(key) && !isWholeNumber(value)) {
                out.warn(where + " has '" + key + "' " + value
                        + "; it is a whole number");
            } else if ("items".equals(key) || LostTalesQuestRewards.PICK.equals(key)) {
                validateList(value, where + " " + key, out);
                for (String part : value.split(",")) {
                    if (LostTalesQuestItemSpec.parse(part, 1, 0).isEmpty()) {
                        out.warn(where + " " + key + " lists an entry naming no item");
                        break;
                    }
                }
            } else if (LostTalesQuestRewards.ALIGNMENT.equals(key)) {
                validateAlignment(value, where, out);
            } else if (LostTalesQuestRewards.SKILLS.equals(key)) {
                validateSkills(value, where, out);
            } else if (LostTalesQuestRewards.RELIC.equals(key)) {
                validateList(value, where + " relic", out);
                for (String relic : LostTalesQuestRewards.relics(rewards)) {
                    if (!com.ninuna.losttales.armour.Relics.isWritten(relic)) {
                        out.warn(where + " has 'relic' " + relic + "; it is a relic set's id,"
                                + " with a slot after a slash for one piece");
                    }
                }
            } else if (LostTalesQuestRewards.STARTER.equals(key)) {
                if (!LostTalesQuestRewards.isStarterId(value)) {
                    out.warn(where + " has 'starter' " + value + "; it is one quest's"
                            + " id, written namespace:path");
                }
            } else if (LostTalesQuestRewards.COINS.equals(key)
                    && (Integer.parseInt(value) < 1
                    || Integer.parseInt(value) > LostTalesQuestRewards.MAX_COINS)) {
                out.warn(where + " has 'coins' " + value + "; it is 1 to "
                        + LostTalesQuestRewards.MAX_COINS);
            }
        }
        if ((rewards.containsKey("count") || rewards.containsKey("meta"))
                && LostTalesQuestParams.value(rewards, "item").length() == 0) {
            out.warn(where + " names a 'count' or 'meta' but no 'item'");
        }
    }

    /**
     * A reward's {@code alignment}: {@code faction*amount}, parted by
     * commas, each a faction a player can hold alignment with and a whole
     * amount of 1 to {@link LostTalesQuestRewards#MAX_ALIGNMENT}.
     */
    private static void validateAlignment(String value, String where, Warnings out) {
        validateList(value, where + " alignment", out);
        for (String part : value.split(",")) {
            int star = part.lastIndexOf('*');
            String faction = (star < 0 ? part : part.substring(0, star)).trim();
            String amount = star < 0 ? "" : part.substring(star + 1).trim();
            if (!isWholeNumber(amount) || Integer.parseInt(amount) < 1
                    || Integer.parseInt(amount) > LostTalesQuestRewards.MAX_ALIGNMENT) {
                out.warn(where + " alignment '" + part.trim() + "' needs an amount"
                        + " of 1 to " + LostTalesQuestRewards.MAX_ALIGNMENT
                        + ", written faction*amount");
            } else if (!isAlignmentFaction(faction)) {
                out.warn(where + " alignment names '" + faction + "', which is"
                        + " no faction a player holds alignment with");
            }
        }
    }

    /**
     * A reward's {@code skills}: {@code skill*amount}, parted by commas,
     * each a skill's id and a whole amount of 1 to
     * {@link LostTalesQuestRewards#MAX_SKILL_EXPERIENCE}. Which skills a
     * server has is read as the quest pays; one it lacks pays nothing.
     */
    private static void validateSkills(String value, String where, Warnings out) {
        validateList(value, where + " skills", out);
        for (String part : value.split(",")) {
            int star = part.lastIndexOf('*');
            String skill = (star < 0 ? part : part.substring(0, star)).trim();
            String amount = star < 0 ? "" : part.substring(star + 1).trim();
            if (!isWholeNumber(amount) || Integer.parseInt(amount) < 1
                    || Integer.parseInt(amount) > LostTalesQuestRewards.MAX_SKILL_EXPERIENCE) {
                out.warn(where + " skills '" + part.trim() + "' needs an amount"
                        + " of 1 to " + LostTalesQuestRewards.MAX_SKILL_EXPERIENCE
                        + ", written skill*amount");
            } else if (skill.indexOf(':') <= 0 || skill.indexOf(':') == skill.length() - 1) {
                out.warn(where + " skills names '" + skill + "', which is no skill's id");
            }
        }
    }

    /** Whether LOTR knows the faction; read through its adapter. */
    private static boolean isAlignmentFaction(String factionId) {
        try {
            return com.ninuna.losttales.compat.lotr.LotrQuestRewards
                    .isAlignmentFaction(factionId);
        } catch (LinkageError noLotr) {
            return false;
        }
    }

    /**
     * A pick offers 1 to {@link LostTalesQuestRewards#MAX_PICKS} items,
     * and only a quest handed in to its giver offers one, at the hand-in.
     */
    private static void validatePick(LostTalesQuestDefinition quest, Warnings out) {
        String pick = LostTalesQuestParams.value(quest.getRewards(),
                LostTalesQuestRewards.PICK);
        if (pick.length() == 0) {
            return;
        }
        if (pick.split(",").length > LostTalesQuestRewards.MAX_PICKS) {
            out.warn("quest=" + quest.getId() + " rewards pick offers more than "
                    + LostTalesQuestRewards.MAX_PICKS + " items");
        }
        if (!LostTalesQuestRules.returnsToGiver(quest)) {
            out.warn("quest=" + quest.getId() + " rewards pick is taken at the"
                    + " hand-in, so the quest needs \"returnTo\": \"giver\"");
        }
    }

    /**
     * The quest's rules: {@code returnTo} is {@code giver}, for a quest
     * with a giver and a hand-in line; a {@code timeLimit} is a number of
     * in-game days; {@code failOn} names death or the giver killed, the
     * second only where there is a giver; {@code repeatAfter} is a number
     * of in-game days, on a repeatable quest.
     */
    private static void validateRules(LostTalesQuestDefinition quest, Warnings out) {
        String where = "quest=" + quest.getId();
        Map<String, String> rules = quest.getRules();
        boolean namesGiver = hasAny(quest.getInteraction(), "entity");
        if (rules.containsKey(LostTalesQuestRules.RETURN_TO)) {
            String returnTo = LostTalesQuestParams.value(rules,
                    LostTalesQuestRules.RETURN_TO);
            if (!LostTalesQuestRules.RETURN_GIVER.equals(returnTo)) {
                out.warn(where + " has 'returnTo' " + returnTo
                        + "; a quest file returns only to its giver");
            } else if (!namesGiver) {
                out.warn(where + " returns to its giver, but its interaction"
                        + " names no 'entity' to return to");
            } else if (!LostTalesQuestConversation.of(quest).isHandedIn()) {
                out.warn(where + " returns to its giver, but its conversation has"
                        + " no 'handIn' line for the giver to say");
            }
        }
        validateTimeLimit(rules, where, out);
        if (rules.containsKey(LostTalesQuestRules.REPEAT_AFTER)) {
            if (LostTalesQuestRules.repeatAfterTicks(quest) <= 0L) {
                out.warn(where + " has 'repeatAfter' " + LostTalesQuestParams.value(
                        rules, LostTalesQuestRules.REPEAT_AFTER) + "; it is a number of"
                        + " in-game days, " + LostTalesQuestRules.MIN_DAYS + " to "
                        + (int) LostTalesQuestRules.MAX_DAYS);
            } else if (!quest.isRepeatable()) {
                out.warn(where + " waits before it is taken again ('repeatAfter'),"
                        + " but is not repeatable");
            }
        }
        Map<String, String> podium = LostTalesQuestRules.podiumRewards(quest);
        if (!podium.isEmpty()) {
            if (!quest.isWorldQuest()) {
                out.warn(where + " names a 'podium', which only a world quest pays");
            }
            validateRewards(podium, where + " podium", out);
            if (podium.containsKey(LostTalesQuestRewards.PICK)) {
                out.warn(where + " podium names a 'pick'; a podium pays as it is");
            }
        }
        Map<String, String> rumour = LostTalesQuestRules.rumour(quest);
        if (!rumour.isEmpty()) {
            for (String key : rumour.keySet()) {
                if (!RUMOUR_KEYS.contains(key)) {
                    out.warn(where + " rumour names '" + key + "', which nothing reads");
                }
            }
            if (quest.isWorldQuest()) {
                out.warn(where + " has a rumour; a world quest is nobody's to take");
            }
            if (!LostTalesQuestObjectiveMatcher.namesWhom(rumour)) {
                out.warn(where + " rumour names no 'entity' or 'group' to tell it");
            }
            if (LostTalesQuestWords.rumourLine(quest).trim().length() == 0) {
                out.warn(where + " rumour has no 'line' to tell");
            }
        }
        if (rules.containsKey(LostTalesQuestRules.FAIL_ON)) {
            String failOn = LostTalesQuestParams.value(rules, LostTalesQuestRules.FAIL_ON);
            validateList(failOn, where + " failOn", out);
            for (String part : failOn.split(",")) {
                String event = part.trim();
                if (!LostTalesQuestRules.FAIL_DEATH.equals(event)
                        && !LostTalesQuestRules.FAIL_GIVER_KILLED.equals(event)) {
                    out.warn(where + " failOn names '" + event + "'; a quest fails"
                            + " on death or giverKilled");
                } else if (LostTalesQuestRules.FAIL_GIVER_KILLED.equals(event)
                        && !namesGiver) {
                    out.warn(where + " fails when its giver is killed, but its"
                            + " interaction names no 'entity'");
                }
            }
        }
    }

    /**
     * A stage's way on (QUEST-32): its {@code next} names a later stage or
     * {@code end}; its choices are two or three, each with words and a
     * later stage or {@code end}, with an {@code ask} line for the giver,
     * and only a quest with a giver to talk to ends a stage on a choice.
     */
    private static void validateBranch(LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, Warnings out) {
        String where = "quest=" + quest.getId() + " stage=" + stage.getId();
        int here = quest.getStages().indexOf(stage);
        Map<String, String> rules = stage.getRules();
        if (rules.containsKey(LostTalesQuestRules.NEXT)) {
            validateNext(quest, here, LostTalesQuestParams.value(rules,
                    LostTalesQuestRules.NEXT), where + " next", out);
        }
        List<LostTalesQuestRules.Choice> choices = LostTalesQuestRules.choices(stage);
        boolean written = false;
        for (String key : rules.keySet()) {
            written |= key.startsWith(LostTalesQuestRules.CHOICE_PREFIX);
        }
        if (!written) {
            return;
        }
        if (choices.size() < 2) {
            out.warn(where + " offers fewer than two choices");
        }
        for (int index = 0; index < choices.size(); index++) {
            LostTalesQuestRules.Choice choice = choices.get(index);
            if (LostTalesQuestWords.choiceWords(quest, stage, index).trim().length() == 0) {
                out.warn(where + " choice " + (index + 1) + " says nothing");
            }
            validateNext(quest, here, choice.getNext(),
                    where + " choice " + (index + 1), out);
        }
        if (LostTalesQuestWords.askLine(quest, stage).trim().length() == 0) {
            out.warn(where + " ends on a choice but has no 'ask' line for the giver");
        }
        if (rules.containsKey(LostTalesQuestRules.NEXT)) {
            out.warn(where + " ends on a choice, so its 'next' is never read");
        }
        if (!hasAny(quest.getInteraction(), "entity")) {
            out.warn(where + " ends on a choice, but the quest's interaction names"
                    + " no 'entity' to make it with");
        }
    }

    /** A stage leads only to a later stage, or to the end. */
    private static void validateNext(LostTalesQuestDefinition quest, int here,
                                     String next, String where, Warnings out) {
        if (LostTalesQuestRules.END.equals(next)) {
            return;
        }
        int target = LostTalesQuestRules.stageIndexOf(quest, next);
        if (target >= quest.getStages().size()) {
            out.warn(where + " names stage '" + next + "', which the quest does not have");
        } else if (target <= here) {
            out.warn(where + " leads back to stage '" + next + "'; a stage leads only"
                    + " to a later one or to end");
        }
    }

    /** A time limit is a number of in-game days within bounds. */
    private static void validateTimeLimit(Map<String, String> rules, String where,
                                          Warnings out) {
        if (!rules.containsKey(LostTalesQuestRules.TIME_LIMIT)) {
            return;
        }
        String value = LostTalesQuestParams.value(rules, LostTalesQuestRules.TIME_LIMIT);
        if (LostTalesQuestRules.timeLimitTicks(rules) <= 0L) {
            out.warn(where + " has 'timeLimit' " + value + "; it is a number of"
                    + " in-game days, " + LostTalesQuestRules.MIN_DAYS + " to "
                    + (int) LostTalesQuestRules.MAX_DAYS);
        }
    }

    /**
     * Prerequisites: the four quest lists, parted by commas, and a
     * {@code minLevel} that is a whole number of at least 0.
     */
    private static void validatePrerequisites(LostTalesQuestDefinition quest,
                                              Warnings out) {
        String where = "quest=" + quest.getId() + " prerequisites";
        for (Map.Entry<String, String> entry : quest.getPrerequisites().entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (!PREREQUISITE_KEYS.contains(key)) {
                out.warn(where + " names '" + key + "', which nothing checks");
            } else if (MIN_LEVEL.equals(key)) {
                if (!isWholeNumber(value) || Integer.parseInt(value) < 0) {
                    out.warn(where + " has 'minLevel' " + value
                            + "; it is a whole number of at least 0");
                }
            } else {
                validateList(value, where + " " + key, out);
            }
        }
    }

    /** A list is parted by commas, the one way every list is written. */
    private static void validateList(String value, String where, Warnings out) {
        if (value.indexOf(';') >= 0) {
            out.warn(where + " is parted by ';'; a list is parted by commas");
        }
    }

    private static boolean isWholeNumber(String value) {
        if (value == null || value.trim().length() == 0) {
            return false;
        }
        try {
            Integer.parseInt(value.trim());
            return true;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    /**
     * {@code take} is a required gather's, and says {@code true} or
     * {@code false}: any other objective, and an optional gather, would
     * never hand anything over.
     */
    private static void validateTake(Warnings out, LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective,
            LostTalesQuestObjectiveType type) {
        if (!objective.getParams().containsKey(LostTalesQuestParams.TAKE)) {
            return;
        }
        String take = LostTalesQuestParams.value(objective.getParams(),
                LostTalesQuestParams.TAKE);
        if (type != LostTalesQuestObjectiveType.GATHER) {
            warn(out, quest, stage, objective,
                    "names 'take', which only a gather objective reads");
        } else if (objective.isOptional()) {
            warn(out, quest, stage, objective,
                    "is optional and names 'take'; an optional objective never hands anything over");
        }
        if (!"true".equals(take) && !"false".equals(take)) {
            warn(out, quest, stage, objective,
                    "has 'take' %s; it is true or false", take);
        }
    }

    /**
     * A kill's place (QUEST-40): a {@code marker}, or {@code x}, {@code y}
     * and {@code z} all three, with a {@code radius} above 0 to count the
     * creatures that die within it. A world quest counts its kills
     * anywhere, so its kills name no place.
     */
    private static void validateKillPlace(Warnings out, LostTalesQuestDefinition quest,
            LostTalesQuestStageDefinition stage, LostTalesQuestObjectiveDefinition objective) {
        Map<String, String> params = objective.getParams();
        if (!LostTalesQuestParams.namesPlace(params)) {
            return;
        }
        if (quest.isWorldQuest()) {
            warn(out, quest, stage, objective,
                    "names a place; a world quest counts its kills anywhere");
            return;
        }
        if (!hasAny(params, "marker")
                && (!hasAny(params, "x") || !hasAny(params, "y") || !hasAny(params, "z"))) {
            warn(out, quest, stage, objective,
                    "has incomplete coordinates for type 'kill'; expected x, y, and z or a marker id");
        }
        validateNumber(out, quest, stage, objective, params.get("x"), "x");
        validateNumber(out, quest, stage, objective, params.get("y"), "y");
        validateNumber(out, quest, stage, objective, params.get("z"), "z");
        String radius = LostTalesQuestParams.value(params, "radius");
        Double within = LostTalesQuestParams.parseNumber(radius);
        if (radius.length() == 0 || within != null && within.doubleValue() <= 0.0D) {
            warn(out, quest, stage, objective,
                    "names a place but no 'radius' above 0; a kill counts the creatures"
                            + " that die within its radius of the place");
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

    /** Whom an objective names: an {@code entity} or a {@code group}, each group one of the six. */
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
                        "names the group '%s'; the groups are living, player, hostile, animal, npc and enemy",
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

    /** The keys a rumour block may hold. */
    static final Set<String> RUMOUR_KEYS = new HashSet<String>(Arrays.asList(
            "line", "entity", "group", "marker"));

    /** The params an objective may hold, beside the {@code reward.*} keys. */
    static final Set<String> PARAM_KEYS = new HashSet<String>(Arrays.asList(
            "item", "ore", "entity", "group", "count", "radius", "marker",
            "x", "y", "z", "dimension", "fellowshipShared",
            LostTalesQuestParams.TAKE, "outcome", LostTalesQuestParams.BOARD));

    /** Whether a delivery is handed in at any missive board ({@code "board": "true"}, QUEST-36). */
    public static boolean isBoardDelivery(Map<String, String> params) {
        return "true".equals(LostTalesQuestParams.value(params,
                LostTalesQuestParams.BOARD));
    }

    /** What an optional objective's params name its rewards under. */
    static final String REWARD_PREFIX = "reward.";

    /** The keys a reward block may hold. */
    static final Set<String> REWARD_KEYS = new HashSet<String>(Arrays.asList(
            "experience", "levels", "item", "count", "meta", "items",
            LostTalesQuestRewards.ALIGNMENT, LostTalesQuestRewards.COINS,
            LostTalesQuestRewards.PICK, LostTalesQuestRewards.SKILLS,
            LostTalesQuestRewards.STARTER, LostTalesQuestRewards.RELIC));

    /** The reward keys that are whole numbers. */
    private static final Set<String> INTEGER_REWARDS = new HashSet<String>(
            Arrays.asList("experience", "levels", "count", "meta",
                    LostTalesQuestRewards.COINS));

    private static final String MIN_LEVEL = "minLevel";

    /** The keys a prerequisites block may hold. */
    static final Set<String> PREREQUISITE_KEYS = new HashSet<String>(
            Arrays.asList("completed", "active", "notCompleted", "notActive",
                    MIN_LEVEL));

    /**
     * A quest's conversation: every entry has to be one
     * {@link LostTalesQuestConversation} knows, since a misspelt one is never
     * said, and a quest that is offered in conversation
     * needs a giver to have it with.
     */
    private static void validateConversation(LostTalesQuestDefinition quest,
                                         Warnings out) {
        Map<String, String> conversation = quest.getConversation();
        if (conversation.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> line : conversation.entrySet()) {
            String key = line.getKey() == null ? "" : line.getKey().trim();
            if (!CONVERSATION_LINES.contains(key)) {
                out.warn("quest=" + quest.getId() + " conversation names '"
                        + line.getKey() + "', which nothing says");
            }
            if (line.getValue() != null
                    && line.getValue().length() > LostTalesQuestConversation.MAX_LINE) {
                out.warn("quest=" + quest.getId() + " conversation line '"
                        + key + "' is longer than "
                        + LostTalesQuestConversation.MAX_LINE + " characters");
            }
        }
        LostTalesQuestConversation spoken = LostTalesQuestConversation.of(quest);
        if (spoken.isOffered()
                && !hasAny(quest.getInteraction(), "entity")) {
            out.warn("quest=" + quest.getId() + " is offered in conversation "
                    + "but its interaction names nobody to have it with");
        }
    }

    /** The entries a conversation may hold. */
    private static final Set<String> CONVERSATION_LINES =
            new HashSet<String>(LostTalesQuestConversation.LINES);

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
}
