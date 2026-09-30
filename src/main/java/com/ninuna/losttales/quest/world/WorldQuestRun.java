package com.ninuna.losttales.quest.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One run of a world quest: when it started and ends, how far the whole
 * server has come on each objective, and how much each helper added,
 * counted by the identity they played (a character, or the account's
 * own). Mutable only inside {@link WorldQuestWorldData}.
 */
public final class WorldQuestRun {
    public enum State {
        RUNNING,
        COMPLETED,
        FAILED,
        STOPPED;

        /** The state a saved name stands for; null for a name this build does not know. */
        static State of(String name) {
            for (State state : values()) {
                if (state.name().equals(name)) {
                    return state;
                }
            }
            return null;
        }
    }

    private final String questId;
    private State state;
    private final long startedAt;
    private final long endsAt;
    private long endedAt;
    private final Map<String, Integer> counts;
    private final Map<UUID, Integer> helpers;

    WorldQuestRun(String questId, State state, long startedAt, long endsAt,
                  long endedAt, Map<String, Integer> counts,
                  Map<UUID, Integer> helpers) {
        this.questId = questId;
        this.state = state;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.endedAt = endedAt;
        this.counts = new LinkedHashMap<String, Integer>(counts);
        this.helpers = new LinkedHashMap<UUID, Integer>(helpers);
    }

    public String getQuestId() {
        return this.questId;
    }

    public State getState() {
        return this.state;
    }

    public boolean isRunning() {
        return this.state == State.RUNNING;
    }

    /** The world time, in ticks, the run started at. */
    public long getStartedAt() {
        return this.startedAt;
    }

    /** The world time, in ticks, the run fails at if its goal is not reached. */
    public long getEndsAt() {
        return this.endsAt;
    }

    /** The world time it ended at; 0 while it runs. */
    public long getEndedAt() {
        return this.endedAt;
    }

    /** How far the whole server has come on the objective. */
    public int getCount(String objectiveId) {
        Integer count = this.counts.get(objectiveId);
        return count == null ? 0 : count.intValue();
    }

    public Map<String, Integer> getCounts() {
        return Collections.unmodifiableMap(this.counts);
    }

    /** How much the identity added, over every objective. */
    public int getHelped(UUID identityId) {
        Integer helped = identityId == null ? null : this.helpers.get(identityId);
        return helped == null ? 0 : helped.intValue();
    }

    public Map<UUID, Integer> getHelpers() {
        return Collections.unmodifiableMap(this.helpers);
    }

    WorldQuestRun copy() {
        return new WorldQuestRun(this.questId, this.state, this.startedAt,
                this.endsAt, this.endedAt, this.counts, this.helpers);
    }

    /**
     * Adds to an objective's count, never past its goal, and to the
     * helper's part by as much as it really added; a run already counting
     * as many objectives as the world keeps takes no new one. Answers what
     * was added.
     */
    int add(String objectiveId, int amount, int goal, UUID helper) {
        int current = getCount(objectiveId);
        int added = Math.max(0, Math.min(amount, goal - current));
        if (added == 0 || !this.counts.containsKey(objectiveId)
                && this.counts.size() >= WorldQuestNbtCodec.MAX_COUNTS) {
            return 0;
        }
        this.counts.put(objectiveId, Integer.valueOf(current + added));
        if (helper != null && (this.helpers.containsKey(helper)
                || this.helpers.size() < WorldQuestRules.MAX_HELPERS)) {
            long helped = (long)getHelped(helper) + added;
            this.helpers.put(helper, Integer.valueOf(
                    (int)Math.min(Integer.MAX_VALUE, helped)));
        }
        return added;
    }

    void end(State ended, long worldTime) {
        this.state = ended;
        this.endedAt = worldTime;
    }
}
