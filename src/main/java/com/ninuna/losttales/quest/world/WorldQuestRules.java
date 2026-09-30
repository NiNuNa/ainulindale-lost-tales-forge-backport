package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveTextHelper;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveType;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * What a world quest is, read from its file's {@code world} block: a quest
 * the whole server works on together, with one shared count for each of
 * its objectives. An operator starts it; nobody takes it for themselves.
 *
 * <pre>
 * "world": { "days": 7, "least": 5 }
 * </pre>
 *
 * <p>{@code days} is how many in-game days it runs before it fails,
 * 1 to 365, and must be written. {@code least} is how much a helper has to
 * add to be paid when it succeeds, 1 by default. It has one stage, and its
 * objectives are kills and crafts, which add up across players; the counts
 * are the whole server's goal.</p>
 */
public final class WorldQuestRules {
    public static final String DAYS = "days";
    public static final String LEAST = "least";
    public static final int MAX_DAYS = 365;
    /** The largest shared count an objective may ask for. */
    public static final int MAX_GOAL = 1000000;
    /** The most helpers one world quest remembers. */
    public static final int MAX_HELPERS = 4096;
    /**
     * The longest quest or objective id, in UTF-8 bytes, that players are
     * sent and the world keeps; the checker holds every quest to it.
     */
    public static final int MAX_ID_BYTES = LostTalesQuestIds.MAX_BYTES;
    /** In-game ticks in a day. */
    public static final long TICKS_PER_DAY = 24000L;

    private WorldQuestRules() {}

    /** How many in-game days the quest runs; 0 for a value that is missing or out of bounds. */
    public static int days(LostTalesQuestDefinition quest) {
        int days = integer(quest == null ? null : quest.getWorld(), DAYS, 0);
        return days >= 1 && days <= MAX_DAYS ? days : 0;
    }

    /** How much a helper must add to be paid: 1 unless the file says more. */
    public static int least(LostTalesQuestDefinition quest) {
        int least = integer(quest == null ? null : quest.getWorld(), LEAST, 1);
        return Math.max(1, Math.min(MAX_GOAL, least));
    }

    /** Whether an objective's count adds up across players: kills and crafts. */
    public static boolean adds(LostTalesQuestObjectiveDefinition objective) {
        return LostTalesQuestObjectiveType.KILL.is(objective)
                || LostTalesQuestObjectiveType.CRAFT.is(objective);
    }

    /** The objectives a world quest counts: its one stage's. */
    public static List<LostTalesQuestObjectiveDefinition> objectives(
            LostTalesQuestDefinition quest) {
        if (quest == null || quest.getStages().isEmpty()) {
            return Collections.emptyList();
        }
        return quest.getStages().get(0).getObjectives();
    }

    /** An objective's shared goal, as its {@code count} says. */
    public static int goal(LostTalesQuestObjectiveDefinition objective) {
        return Math.max(1, Math.min(MAX_GOAL, LostTalesQuestObjectiveTextHelper
                .getObjectiveTargetCount(objective)));
    }

    /**
     * What keeps a world quest from running, in words for the log and the
     * operator: an empty list for one that can run.
     */
    public static List<String> problems(LostTalesQuestDefinition quest) {
        List<String> problems = new ArrayList<String>();
        if (quest == null || !quest.isWorldQuest()) {
            return problems;
        }
        for (String key : quest.getWorld().keySet()) {
            if (!DAYS.equals(key) && !LEAST.equals(key)) {
                problems.add("its world block names '" + key
                        + "', which nothing reads");
            }
        }
        if (days(quest) == 0) {
            problems.add("its world block needs 'days', 1 to " + MAX_DAYS);
        }
        String least = quest.getWorld().get(LEAST);
        if (least != null && integer(quest.getWorld(), LEAST, -1) < 1) {
            problems.add("its world block's 'least' must be a whole number"
                    + " of at least 1");
        }
        if (!LostTalesQuestDefinition.START_MODE_LOCKED.equals(
                quest.getStartMode())) {
            problems.add("a world quest is started by an operator, so its"
                    + " startMode must be 'locked'");
        }
        if (!quest.getDialogue().isEmpty()) {
            problems.add("a world quest has no conversation");
        }
        if (quest.getStages().size() != 1) {
            problems.add("a world quest has exactly one stage");
            return problems;
        }
        LostTalesQuestStageDefinition stage = quest.getStages().get(0);
        if (stage == null || stage.getObjectives().isEmpty()) {
            problems.add("a world quest's stage needs an objective");
            return problems;
        }
        if (stage.getObjectives().size() > WorldQuestNbtCodec.MAX_COUNTS) {
            problems.add("a world quest counts at most "
                    + WorldQuestNbtCodec.MAX_COUNTS + " objectives");
        }
        for (LostTalesQuestObjectiveDefinition objective
                : stage.getObjectives()) {
            if (objective == null) {
                continue;
            }
            if (!adds(objective)) {
                problems.add("objective " + objective.getId() + " is a '"
                        + objective.getType() + "'; a world quest counts"
                        + " only kills and crafts");
            }
            if (objective.getParams().containsKey("radius")) {
                problems.add("objective " + objective.getId()
                        + " has a radius; a world quest counts its kills"
                        + " and crafts anywhere");
            }
            if (objective.isOptional()) {
                problems.add("objective " + objective.getId()
                        + " is optional; a world quest's objectives are"
                        + " all its goal");
            }
            int count = LostTalesQuestObjectiveTextHelper
                    .getObjectiveTargetCount(objective);
            if (count < 1 || count > MAX_GOAL) {
                problems.add("objective " + objective.getId()
                        + " needs a count of 1 to " + MAX_GOAL);
            }
        }
        return problems;
    }

    private static int integer(Map<String, String> block, String key,
                               int fallback) {
        String value = block == null ? null : block.get(key);
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }
}
