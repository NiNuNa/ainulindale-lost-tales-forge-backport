package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rewards a character's inventory had no room for, each under the
 * quest that paid it, waiting in the journal until it is collected
 * (QUEST-44). Kept with the quest log ({@link LostTalesQuestPlayerData})
 * and saved by {@link WaitingRewardsNbtCodec}.
 *
 * <p>Rewards of one kind under one quest wait as one, their counts added.
 * Every bound refuses what would pass it and keeps what is there: at most
 * {@link #MAX_QUESTS} quests with rewards waiting, {@link #MAX_KINDS_EACH}
 * kinds under each and {@link #MAX_COUNT} of each kind.</p>
 */
public final class WaitingRewards {
    /** The most quests a character has rewards waiting under. */
    public static final int MAX_QUESTS = 256;
    /** The most kinds of reward waiting under one quest. */
    public static final int MAX_KINDS_EACH = 64;
    /** The most of one kind waiting under one quest. */
    public static final int MAX_COUNT = 1000000;

    private final Map<String, List<WaitingReward>> byQuest =
            new LinkedHashMap<String, List<WaitingReward>>();

    /**
     * Keeps the reward waiting under the quest, beside one of its kind
     * where one waits. Answers how many of it could not be kept: all of an
     * invalid reward, the rest past a bound.
     */
    public int add(String questId, WaitingReward reward) {
        if (reward == null) {
            return 0;
        }
        if (!LostTalesQuestIds.fits(questId) || !reward.isValid()) {
            return Math.max(0, reward.getCount());
        }
        List<WaitingReward> waiting = this.byQuest.get(questId);
        if (waiting == null) {
            if (this.byQuest.size() >= MAX_QUESTS) {
                return reward.getCount();
            }
            waiting = new ArrayList<WaitingReward>();
            this.byQuest.put(questId, waiting);
        }
        for (int index = 0; index < waiting.size(); index++) {
            WaitingReward kept = waiting.get(index);
            if (kept.isSameKind(reward)) {
                int room = MAX_COUNT - kept.getCount();
                int taken = Math.min(room, reward.getCount());
                if (taken > 0) {
                    waiting.set(index, kept.withCount(kept.getCount() + taken));
                }
                return reward.getCount() - taken;
            }
        }
        if (waiting.size() >= MAX_KINDS_EACH) {
            return reward.getCount();
        }
        waiting.add(reward);
        return 0;
    }

    /** What waits under the quest, in the order it came; empty for nothing. */
    public List<WaitingReward> get(String questId) {
        List<WaitingReward> waiting = questId == null ? null
                : this.byQuest.get(questId);
        return waiting == null ? Collections.<WaitingReward>emptyList()
                : Collections.unmodifiableList(new ArrayList<WaitingReward>(waiting));
    }

    /** Whether anything waits under the quest. */
    public boolean has(String questId) {
        return questId != null && this.byQuest.containsKey(questId);
    }

    /** The quests with rewards waiting, in the order their first came. */
    public Set<String> questIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.byQuest.keySet()));
    }

    public boolean isEmpty() {
        return this.byQuest.isEmpty();
    }

    /**
     * Puts {@code remaining} in place of what waits under the quest, as a
     * collection leaves it: nothing left takes the quest off the list.
     * Each is kept as {@link #add} keeps it.
     */
    public void replace(String questId, List<WaitingReward> remaining) {
        if (questId == null) {
            return;
        }
        this.byQuest.remove(questId);
        if (remaining == null) {
            return;
        }
        for (WaitingReward reward : remaining) {
            add(questId, reward);
        }
    }

    /** Every quest's rewards, in order: a copy. */
    public Map<String, List<WaitingReward>> all() {
        Map<String, List<WaitingReward>> copy =
                new LinkedHashMap<String, List<WaitingReward>>();
        for (Map.Entry<String, List<WaitingReward>> entry : this.byQuest.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableList(
                    new ArrayList<WaitingReward>(entry.getValue())));
        }
        return copy;
    }

    public void clear() {
        this.byQuest.clear();
    }

    /** Takes every quest's rewards as {@code other} holds them, in place of these. */
    public void copyFrom(WaitingRewards other) {
        this.byQuest.clear();
        if (other == null) {
            return;
        }
        for (Map.Entry<String, List<WaitingReward>> entry : other.byQuest.entrySet()) {
            this.byQuest.put(entry.getKey(),
                    new ArrayList<WaitingReward>(entry.getValue()));
        }
    }
}
