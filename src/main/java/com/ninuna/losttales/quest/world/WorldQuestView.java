package com.ninuna.losttales.quest.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
    private final List<String> podiumNames;
    private final List<Integer> podiumParts;

    public WorldQuestView(String questId, WorldQuestRun.State state,
                          long endsAt, long endedAt,
                          Map<String, Integer> counts, int helpers,
                          int mine) {
        this(questId, state, endsAt, endedAt, counts, helpers, mine,
                Collections.<String>emptyList(), Collections.<Integer>emptyList());
    }

    /**
     * A view naming its podium (QUEST-36): the best helpers' character
     * names, best first, each beside how much it added.
     */
    public WorldQuestView(String questId, WorldQuestRun.State state,
                          long endsAt, long endedAt,
                          Map<String, Integer> counts, int helpers,
                          int mine, List<String> podiumNames,
                          List<Integer> podiumParts) {
        this.questId = questId == null ? "" : questId;
        this.state = state == null ? WorldQuestRun.State.STOPPED : state;
        this.endsAt = endsAt;
        this.endedAt = endedAt;
        this.counts = Collections.unmodifiableMap(
                new LinkedHashMap<String, Integer>(counts == null
                        ? Collections.<String, Integer>emptyMap() : counts));
        this.helpers = Math.max(0, helpers);
        this.mine = Math.max(0, mine);
        List<String> names = new ArrayList<String>();
        List<Integer> parts = new ArrayList<Integer>();
        for (int index = 0; podiumNames != null && podiumParts != null
                && index < podiumNames.size() && index < podiumParts.size()
                && names.size() < WorldQuestRules.PODIUM_PLACES; index++) {
            names.add(podiumNames.get(index) == null ? "" : podiumNames.get(index));
            parts.add(Integer.valueOf(Math.max(0, podiumParts.get(index).intValue())));
        }
        this.podiumNames = Collections.unmodifiableList(names);
        this.podiumParts = Collections.unmodifiableList(parts);
    }

    /** The run as the identity {@code viewer} sees it. */
    public static WorldQuestView of(WorldQuestRun run, UUID viewer) {
        return of(run, viewer, Collections.<String>emptyList(),
                Collections.<Integer>emptyList());
    }

    /** The run as {@code viewer} sees it, with its podium's names and parts. */
    public static WorldQuestView of(WorldQuestRun run, UUID viewer,
                                    List<String> podiumNames,
                                    List<Integer> podiumParts) {
        return new WorldQuestView(run.getQuestId(), run.getState(),
                run.getEndsAt(), run.getEndedAt(), run.getCounts(),
                run.getHelpers().size(), run.getHelped(viewer), podiumNames,
                podiumParts);
    }

    /** The best helpers' character names, best first; at most three. */
    public List<String> getPodiumNames() {
        return this.podiumNames;
    }

    /** How much each helper on the podium added, in its order. */
    public List<Integer> getPodiumParts() {
        return this.podiumParts;
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
