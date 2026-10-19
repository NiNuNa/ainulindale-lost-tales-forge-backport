package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The parts of a reward block beyond experience and items (QUEST-33):
 * {@code alignment} with LOTR factions ({@code lotr:gondor*50}, a comma
 * list), {@code coins} (a whole number, LOTR's coins by value) and a
 * {@code pick} of up to {@link #MAX_PICKS} items of which the player takes
 * one at the hand-in. Every quest's optional objectives pay their own
 * {@code reward.*}; {@link #withOptionals} lays them beside the quest's so
 * every place shows every reward before the quest is taken.
 */
public final class LostTalesQuestRewards {
    public static final String ALIGNMENT = "alignment";
    public static final String COINS = "coins";
    public static final String PICK = "pick";
    /** The most items a pick offers. */
    public static final int MAX_PICKS = 3;
    /** The most alignment one faction gains from one reward. */
    public static final int MAX_ALIGNMENT = 1000;
    /** The most coins one reward pays. */
    public static final int MAX_COINS = 100000;
    /**
     * The start of the keys {@link #withOptionals} files an optional
     * objective's reward under: {@code optional.<objective id>.<key>}.
     */
    public static final String OPTIONAL_PREFIX = "optional.";

    private LostTalesQuestRewards() {}

    /** One faction's alignment, as a reward names it. */
    public static final class Alignment {
        private final String factionId;
        private final int amount;

        Alignment(String factionId, int amount) {
            this.factionId = factionId;
            this.amount = amount;
        }

        public String getFactionId() {
            return this.factionId;
        }

        public int getAmount() {
            return this.amount;
        }
    }

    /**
     * Each faction a reward block's {@code alignment} names with its
     * amount, as written: {@code faction*amount}, parted by commas. An
     * entry without a whole amount of 1 to {@link #MAX_ALIGNMENT} is left
     * out; the checker names it.
     */
    public static List<Alignment> alignments(Map<String, String> rewards) {
        List<Alignment> alignments = new ArrayList<Alignment>();
        String written = LostTalesQuestParams.value(rewards, ALIGNMENT);
        if (written.length() == 0) {
            return alignments;
        }
        for (String part : written.split(",")) {
            int star = part.lastIndexOf('*');
            String faction = (star < 0 ? part : part.substring(0, star)).trim();
            int amount = star < 0 ? -1 : LostTalesQuestParams.parseInt(
                    part.substring(star + 1), -1);
            if (faction.length() > 0 && amount >= 1 && amount <= MAX_ALIGNMENT) {
                alignments.add(new Alignment(faction, amount));
            }
        }
        return alignments;
    }

    /** The coins a reward block pays: 0 for none, at most {@link #MAX_COINS}. */
    public static int coins(Map<String, String> rewards) {
        int coins = LostTalesQuestParams.parseInt(
                LostTalesQuestParams.value(rewards, COINS), 0);
        return Math.max(0, Math.min(MAX_COINS, coins));
    }

    /**
     * The items a reward block's {@code pick} offers, in its order, at most
     * {@link #MAX_PICKS}; empty for a block with no pick.
     */
    public static List<LostTalesQuestItemSpec> picks(Map<String, String> rewards) {
        List<LostTalesQuestItemSpec> picks = new ArrayList<LostTalesQuestItemSpec>();
        String written = LostTalesQuestParams.value(rewards, PICK);
        if (written.length() == 0) {
            return picks;
        }
        for (String part : written.split(",")) {
            LostTalesQuestItemSpec item = LostTalesQuestItemSpec.parse(part, 1, 0);
            if (!item.isEmpty() && picks.size() < MAX_PICKS) {
                picks.add(item.rewarded());
            }
        }
        return picks;
    }

    /**
     * The quest's rewards with each optional objective's own beside them,
     * filed under {@code optional.<objective id>.<key>}, in the order the
     * file writes them: what every place shows (QUEST-33).
     */
    public static Map<String, String> withOptionals(LostTalesQuestDefinition quest) {
        if (quest == null) {
            return Collections.emptyMap();
        }
        Map<String, String> all = new LinkedHashMap<String, String>(quest.getRewards());
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (!objective.isOptional()) {
                    continue;
                }
                for (Map.Entry<String, String> reward
                        : optionalRewards(objective).entrySet()) {
                    all.put(OPTIONAL_PREFIX + objective.getId() + "."
                            + reward.getKey(), reward.getValue());
                }
            }
        }
        return all;
    }

    /** An optional objective's own reward block: its {@code reward.*} params. */
    public static Map<String, String> optionalRewards(
            LostTalesQuestObjectiveDefinition objective) {
        Map<String, String> rewards = new LinkedHashMap<String, String>();
        if (objective == null) {
            return rewards;
        }
        for (Map.Entry<String, String> param : objective.getParams().entrySet()) {
            String key = param.getKey();
            if (key != null && key.startsWith(LostTalesQuestDefinitionValidator.REWARD_PREFIX)
                    && key.length() > LostTalesQuestDefinitionValidator.REWARD_PREFIX.length()) {
                rewards.put(key.substring(LostTalesQuestDefinitionValidator
                        .REWARD_PREFIX.length()), param.getValue());
            }
        }
        return rewards;
    }

    /**
     * The optional objectives' blocks a map from {@link #withOptionals}
     * holds, each under its objective's id, in order; the quest's own
     * block under the empty id first.
     */
    public static Map<String, Map<String, String>> split(Map<String, String> rewards) {
        Map<String, Map<String, String>> blocks =
                new LinkedHashMap<String, Map<String, String>>();
        blocks.put("", new LinkedHashMap<String, String>());
        if (rewards == null) {
            return blocks;
        }
        for (Map.Entry<String, String> entry : rewards.entrySet()) {
            String key = entry.getKey() == null ? "" : entry.getKey();
            if (!key.startsWith(OPTIONAL_PREFIX)) {
                blocks.get("").put(key, entry.getValue());
                continue;
            }
            String rest = key.substring(OPTIONAL_PREFIX.length());
            int dot = rest.lastIndexOf('.');
            if (dot <= 0 || dot + 1 >= rest.length()) {
                continue;
            }
            String objective = rest.substring(0, dot);
            Map<String, String> block = blocks.get(objective);
            if (block == null) {
                block = new LinkedHashMap<String, String>();
                blocks.put(objective, block);
            }
            block.put(rest.substring(dot + 1), entry.getValue());
        }
        return blocks;
    }
}
