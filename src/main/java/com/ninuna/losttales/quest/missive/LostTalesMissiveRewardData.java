package com.ninuna.losttales.quest.missive;

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

    public boolean isEmpty() {
        return this.rewards.isEmpty();
    }

    public static LostTalesMissiveRewardData empty() {
        return new LostTalesMissiveRewardData(Collections.<String, String>emptyMap());
    }

    public static LostTalesMissiveRewardData experienceAndItems(int experience, String items) {
        LinkedHashMap<String, String> rewards = new LinkedHashMap<String, String>();
        if (experience > 0) {
            rewards.put("experience", String.valueOf(experience));
        }
        if (items != null && items.trim().length() > 0) {
            rewards.put("items", items.trim());
        }
        return new LostTalesMissiveRewardData(rewards);
    }
}
