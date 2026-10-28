package com.ninuna.losttales.quest;

import java.util.Map;

/**
 * The keys a quest file writes beside its blocks, kept as the quest's and
 * each stage's rules, and how the game reads them:
 *
 * <ul>
 * <li>{@code returnTo}: {@code giver}, the quest waits for its giver once
 * its last stage is done and pays in the hand-in conversation (QUEST-31);
 * a missive's is {@code board}, handed in at any missive board.</li>
 * <li>{@code timeLimit}: in-game days, on the quest or on a stage, 0.05 to
 * {@link #MAX_DAYS}; the quest fails when it runs out (QUEST-32).</li>
 * <li>{@code failOn}: {@code death} (the character dies) and
 * {@code giverKilled} (the one who gave it is killed), parted by commas
 * (QUEST-32).</li>
 * <li>{@code repeatAfter}: in-game days, 0.05 to {@link #MAX_DAYS}, a
 * repeatable quest waits after a completion before it can be taken again
 * (QUEST-45).</li>
 * <li>{@code podium}: a world quest's second reward block, for its three
 * best helpers (QUEST-36), kept as {@code podium.<key>}.</li>
 * <li>{@code rumour}: a lead for the quest (QUEST-20): its {@code line},
 * the creatures that tell it ({@code entity} or {@code group}, as any
 * objective names whom) and a {@code marker} revealed with it; kept as
 * {@code rumour.<key>}.</li>
 * <li>A stage's {@code next}: the stage that follows it, a later one, or
 * {@code end}; the next in the file where it names none. Its
 * {@code choices} (two or three, each a {@code say} and a {@code next})
 * end it on a choice the giver offers in conversation, its {@code ask}
 * line what the giver says then (QUEST-32). Kept as {@code next},
 * {@code ask}, {@code choice.<n>.say} and {@code choice.<n>.next}.</li>
 * </ul>
 */
public final class LostTalesQuestRules {
    public static final String RETURN_TO = "returnTo";
    public static final String RETURN_GIVER = "giver";
    public static final String RETURN_BOARD = "board";
    public static final String TIME_LIMIT = "timeLimit";
    public static final String FAIL_ON = "failOn";
    public static final String FAIL_DEATH = "death";
    public static final String FAIL_GIVER_KILLED = "giverKilled";
    public static final String REPEAT_AFTER = "repeatAfter";
    public static final String PODIUM = "podium";
    public static final String RUMOUR = "rumour";
    /** What a rumour's keys are kept under among the rules. */
    public static final String RUMOUR_PREFIX = "rumour.";
    public static final String NEXT = "next";
    /** A stage's {@code next} that ends the quest there. */
    public static final String END = "end";
    public static final String ASK = "ask";
    public static final String CHOICES = "choices";
    /** What a stage's choices are kept under among its rules. */
    public static final String CHOICE_PREFIX = "choice.";
    /** The most choices a stage offers. */
    public static final int MAX_CHOICES = 3;
    /** What the podium's reward keys are kept under among the rules. */
    public static final String PODIUM_PREFIX = "podium.";
    /** The longest time limit, in in-game days. */
    public static final double MAX_DAYS = 365.0D;
    /** The shortest time limit, in in-game days: a little over an in-game hour. */
    public static final double MIN_DAYS = 0.05D;
    /** In-game ticks in a day. */
    public static final long TICKS_PER_DAY = 24000L;

    private LostTalesQuestRules() {}

    /** Where the quest is handed in: {@link #RETURN_GIVER}, {@link #RETURN_BOARD} or empty for nowhere. */
    public static String returnTo(LostTalesQuestDefinition quest) {
        return quest == null ? "" : LostTalesQuestParams.value(quest.getRules(), RETURN_TO);
    }

    /** Whether the quest waits for its giver, or a board, once its last stage is done. */
    public static boolean waitsForHandIn(LostTalesQuestDefinition quest) {
        String returnTo = returnTo(quest);
        return RETURN_GIVER.equals(returnTo) || RETURN_BOARD.equals(returnTo);
    }

    public static boolean returnsToGiver(LostTalesQuestDefinition quest) {
        return RETURN_GIVER.equals(returnTo(quest));
    }

    public static boolean returnsToBoard(LostTalesQuestDefinition quest) {
        return RETURN_BOARD.equals(returnTo(quest));
    }

    /**
     * The time limit the rules write, in ticks; 0 for none, or for a value
     * that is not a number of days within bounds (the checker names it).
     */
    public static long timeLimitTicks(Map<String, String> rules) {
        return daysAsTicks(LostTalesQuestParams.value(rules, TIME_LIMIT));
    }

    /**
     * How long a repeatable quest waits after a completion before it can
     * be taken again, in ticks (QUEST-45); 0 for no wait, or for a value
     * that is not a number of days within bounds (the checker names it).
     */
    public static long repeatAfterTicks(LostTalesQuestDefinition quest) {
        return quest == null ? 0L : daysAsTicks(
                LostTalesQuestParams.value(quest.getRules(), REPEAT_AFTER));
    }

    /** In-game days as written, {@link #MIN_DAYS} to {@link #MAX_DAYS}, in ticks; 0 for anything else. */
    private static long daysAsTicks(String written) {
        Double days = LostTalesQuestParams.parseNumber(written);
        if (days == null || days.isNaN() || days.doubleValue() < MIN_DAYS
                || days.doubleValue() > MAX_DAYS) {
            return 0L;
        }
        return Math.round(days.doubleValue() * TICKS_PER_DAY);
    }

    /** A quest's rumour block, as its file writes it ({@code line}, {@code entity}, {@code group}, {@code marker}); empty for none. */
    public static Map<String, String> rumour(LostTalesQuestDefinition quest) {
        Map<String, String> rumour = new java.util.LinkedHashMap<String, String>();
        if (quest == null) {
            return rumour;
        }
        for (Map.Entry<String, String> rule : quest.getRules().entrySet()) {
            if (rule.getKey().startsWith(RUMOUR_PREFIX)
                    && rule.getKey().length() > RUMOUR_PREFIX.length()) {
                rumour.put(rule.getKey().substring(RUMOUR_PREFIX.length()),
                        rule.getValue());
            }
        }
        return rumour;
    }

    /** A world quest's podium reward block, as its file writes it; empty for none. */
    public static Map<String, String> podiumRewards(LostTalesQuestDefinition quest) {
        Map<String, String> rewards = new java.util.LinkedHashMap<String, String>();
        if (quest == null) {
            return rewards;
        }
        for (Map.Entry<String, String> rule : quest.getRules().entrySet()) {
            if (rule.getKey().startsWith(PODIUM_PREFIX)
                    && rule.getKey().length() > PODIUM_PREFIX.length()) {
                rewards.put(rule.getKey().substring(PODIUM_PREFIX.length()),
                        rule.getValue());
            }
        }
        return rewards;
    }

    /**
     * One reply a stage's choice offers: the stage it leads to. Its words
     * are read through {@link LostTalesQuestWords#choiceWords}.
     */
    public static final class Choice {
        private final String next;

        Choice(String next) {
            this.next = next;
        }

        /** The stage it leads to, or {@link #END}. */
        public String getNext() {
            return this.next;
        }
    }

    /** The stage's choices, in order; empty for a stage that ends without one. */
    public static java.util.List<Choice> choices(LostTalesQuestStageDefinition stage) {
        java.util.List<Choice> choices = new java.util.ArrayList<Choice>();
        if (stage == null) {
            return choices;
        }
        for (int index = 0; index < MAX_CHOICES; index++) {
            String next = LostTalesQuestParams.value(stage.getRules(),
                    CHOICE_PREFIX + index + ".next");
            if (next.length() == 0) {
                break;
            }
            choices.add(new Choice(next));
        }
        return choices;
    }

    /**
     * The index of the stage that follows {@code stageIndex}, with no
     * choice in between: its {@code next}, else the next in the file; the
     * number of stages where the quest ends there.
     */
    public static int nextStageIndex(LostTalesQuestDefinition quest, int stageIndex) {
        java.util.List<LostTalesQuestStageDefinition> stages = quest.getStages();
        String next = stageIndex < 0 || stageIndex >= stages.size() ? ""
                : LostTalesQuestParams.value(stages.get(stageIndex).getRules(), NEXT);
        return next.length() == 0 ? stageIndex + 1 : stageIndexOf(quest, next);
    }

    /**
     * The index of the stage of id {@code stageId}; the number of stages
     * for {@link #END}, an empty id or a stage the quest lacks. Every
     * lookup of a stage by its id asks this.
     */
    public static int stageIndexOf(LostTalesQuestDefinition quest, String stageId) {
        java.util.List<LostTalesQuestStageDefinition> stages = quest.getStages();
        if (stageId == null || stageId.length() == 0) {
            return stages.size();
        }
        for (int index = 0; index < stages.size(); index++) {
            if (stageId.equals(stages.get(index).getId())) {
                return index;
            }
        }
        return stages.size();
    }

    /** Whether the quest fails on the event its {@code failOn} names. */
    public static boolean failsOn(LostTalesQuestDefinition quest, String event) {
        if (quest == null || event == null) {
            return false;
        }
        for (String part : LostTalesQuestParams.value(quest.getRules(), FAIL_ON).split(",")) {
            if (event.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether a fellowship member's kills and arrivals may count for this
     * objective (FEL-28, QUEST-25): one in no missive whose
     * {@code fellowshipShared} does not say false. The server's
     * {@code enableSharedQuestProgress} is asked apart.
     */
    public static boolean sharesWithFellowship(LostTalesQuestDefinition quest,
                                               LostTalesQuestObjectiveDefinition objective) {
        if (quest == null || objective == null
                || com.ninuna.losttales.quest.missive.MissiveWords.isMissive(quest.getWords())) {
            return false;
        }
        String value = LostTalesQuestParams.value(objective.getParams(), "fellowshipShared");
        return value.length() == 0 || Boolean.parseBoolean(value);
    }
}
