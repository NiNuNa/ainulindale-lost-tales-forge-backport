package com.ninuna.losttales.quest;

import com.ninuna.losttales.quest.player.WaitingReward;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Hands reward items and starters to a player and keeps account of what
 * found no room (QUEST-44): what a quest pays as it ends, and what a
 * player collects from the journal later, go through here alike. The
 * hands say how much of each they took; whatever they did not waits.
 */
public final class QuestRewardPayout {

    /** Whoever the rewards are handed to. */
    public interface Hands {
        /**
         * Hands over what fits of the reward; answers how many of it did
         * not fit, all of it for a reward the game cannot make now.
         */
        int hand(WaitingReward reward);
    }

    private QuestRewardPayout() {}

    /**
     * Hands each reward over in order, and answers what was left of them,
     * each kind once with its counts added, in the order they came.
     */
    public static List<WaitingReward> leftOver(Collection<WaitingReward> owed,
                                               Hands hands) {
        List<WaitingReward> left = new ArrayList<WaitingReward>();
        if (owed == null || hands == null) {
            return left;
        }
        for (WaitingReward reward : owed) {
            if (reward == null || reward.getCount() <= 0) {
                continue;
            }
            int notTaken = Math.max(0, Math.min(reward.getCount(), hands.hand(reward)));
            if (notTaken > 0) {
                merge(left, reward.withCount(notTaken));
            }
        }
        return left;
    }

    /** Adds the reward to the list, beside one of its kind where one stands. */
    static void merge(List<WaitingReward> rewards, WaitingReward reward) {
        for (int index = 0; index < rewards.size(); index++) {
            WaitingReward kept = rewards.get(index);
            if (kept.isSameKind(reward)) {
                long total = (long) kept.getCount() + reward.getCount();
                rewards.set(index, kept.withCount(
                        (int) Math.min(Integer.MAX_VALUE, total)));
                return;
            }
        }
        rewards.add(reward);
    }
}
