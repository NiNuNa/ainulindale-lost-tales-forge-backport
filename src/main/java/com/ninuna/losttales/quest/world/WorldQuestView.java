package com.ninuna.losttales.quest.world;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A world quest's run as one player sees it: its state and times, the whole
 * server's count on each objective, how many helped, and how much the
 * identity the player plays added. What the server sends and the client
 * shows; it holds no other player's identity.
 */
public final class WorldQuestView {
    private final String questId;
    private final WorldQuestRun.State state;
    private final long endsAt;
    private final long endedAt;
    private final Map<String, Integer> counts;
    private final int helpers;
    private final int mine;

    public WorldQuestView(String questId, WorldQuestRun.State state,
                          long endsAt, long endedAt,
                          Map<String, Integer> counts, int helpers,
                          int mine) {
        this.questId = questId == null ? "" : questId;
        this.state = state == null ? WorldQuestRun.State.STOPPED : state;
        this.endsAt = endsAt;
        this.endedAt = endedAt;
        this.counts = Collections.unmodifiableMap(
                new LinkedHashMap<String, Integer>(counts == null
                        ? Collections.<String, Integer>emptyMap() : counts));
        this.helpers = Math.max(0, helpers);
        this.mine = Math.max(0, mine);
    }

    /** The run as the identity {@code viewer} sees it. */
    public static WorldQuestView of(WorldQuestRun run, UUID viewer) {
        return new WorldQuestView(run.getQuestId(), run.getState(),
                run.getEndsAt(), run.getEndedAt(), run.getCounts(),
                run.getHelpers().size(), run.getHelped(viewer));
    }

    public String getQuestId() {
        return this.questId;
    }

    public WorldQuestRun.State getState() {
        return this.state;
    }

    public boolean isRunning() {
        return this.state == WorldQuestRun.State.RUNNING;
    }

    public long getEndsAt() {
        return this.endsAt;
    }

    public long getEndedAt() {
        return this.endedAt;
    }

    public int getCount(String objectiveId) {
        Integer count = this.counts.get(objectiveId);
        return count == null ? 0 : count.intValue();
    }

    public Map<String, Integer> getCounts() {
        return this.counts;
    }

    /** How many identities helped so far. */
    public int getHelpers() {
        return this.helpers;
    }

    /** How much the identity the player plays added. */
    public int getMine() {
        return this.mine;
    }
}
