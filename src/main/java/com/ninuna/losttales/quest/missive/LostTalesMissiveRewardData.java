package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestRewards;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reward payload for generated missives. Values intentionally mirror quest JSON reward keys. */
public final class LostTalesMissiveRewardData {
    private final Map<String, String> rewards;

    public LostTalesMissiveRewardData(Map<String, String> rewards) {
        this.rewards = Collections.unmodifiableMap(LostTalesMissiveObjectiveData.copyStringMap(rewards));
    }

    public Map<String, String> getRewards() {
        return this.rewards;
    }

    public static LostTalesMissiveRewardData empty() {
        return new LostTalesMissiveRewardData(Collections.<String, String>emptyMap());
    }

    /** A notice's pay: experience and LOTR coins (QUEST-33). */
    public static LostTalesMissiveRewardData experienceAndCoins(int experience, int coins) {
        LinkedHashMap<String, String> rewards = new LinkedHashMap<String, String>();
        if (experience > 0) {
            rewards.put("experience", String.valueOf(experience));
        }
        if (coins > 0) {
            rewards.put(LostTalesQuestRewards.COINS, String.valueOf(coins));
        }
        return new LostTalesMissiveRewardData(rewards);
    }
}
