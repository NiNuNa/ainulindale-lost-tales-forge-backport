package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The items a server's quest file names that the game does not have, each
 * a warning that leaves the file out (QUEST-04): a misspelt reward would
 * pay nothing and a misspelt objective match nothing.
 *
 * <p>It runs where the game has registered every mod's items, as the
 * server reads its quest files; the shape checks of
 * {@link LostTalesQuestDefinitionValidator} run without the game. Every
 * item a quest names is looked at: what its objectives ask for, its
 * rewards, its pick, its podium and its optional objectives' rewards.</p>
 */
public final class QuestItemNames {
    /** Which item ids there are. */
    public interface Items {
        boolean has(String itemId);
    }

    /**
     * The game's own item registry: an id as the game registers it, or
     * that id in lower case, the way a reward is looked up.
     */
    public static final Items GAME = new Items() {
        @Override
        public boolean has(String itemId) {
            return LostTalesQuestItemSpec.parse(itemId, 1, 0).item() != null;
        }
    };

    private QuestItemNames() {}

    /** A line for each item id the quest names that {@code items} lacks, each id once. */
    public static List<String> problems(LostTalesQuestDefinition quest, Items items) {
        List<String> problems = new ArrayList<String>();
        if (quest == null || items == null) {
            return problems;
        }
        Set<String> named = new LinkedHashSet<String>();
        addRewardItems(named, quest.getRewards());
        addRewardItems(named, LostTalesQuestRules.podiumRewards(quest));
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                String asked = LostTalesQuestParams.value(objective.getParams(), "item");
                for (String part : asked.split(",")) {
                    addItem(named, LostTalesQuestItemSpec.parse(part, 1, 0));
                }
                addRewardItems(named, LostTalesQuestRewards.optionalRewards(objective));
            }
        }
        for (String itemId : named) {
            if (!items.has(itemId)) {
                problems.add("quest=" + quest.getId() + " names the item '" + itemId
                        + "', which the game does not have");
            }
        }
        return problems;
    }

    private static void addRewardItems(Set<String> named, Map<String, String> rewards) {
        addItem(named, LostTalesQuestItemSpec.rewardItem(rewards));
        for (LostTalesQuestItemSpec item : LostTalesQuestItemSpec.rewardItems(rewards)) {
            addItem(named, item);
        }
        for (LostTalesQuestItemSpec item : LostTalesQuestRewards.picks(rewards)) {
            addItem(named, item);
        }
    }

    private static void addItem(Set<String> named, LostTalesQuestItemSpec item) {
        if (item != null && !item.isEmpty()) {
            named.add(item.getItemId());
        }
    }
}
