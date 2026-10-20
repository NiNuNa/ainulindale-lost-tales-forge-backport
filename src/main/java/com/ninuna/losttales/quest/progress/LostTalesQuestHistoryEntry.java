package com.ninuna.losttales.quest.progress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A quest's History line: how it last ended (completed, failed or
 * abandoned), why (a lang key) and when, the optional objectives it achieved, the
 * stages it reached, and how many times it was ever completed. The line
 * stays while the quest is taken again, and the next end replaces it,
 * keeping the count (QUEST-10).
 */
public final class LostTalesQuestHistoryEntry {
    public enum Outcome {
        COMPLETED,
        FAILED,
        ABANDONED;

        public static Outcome fromName(String value) {
            if (value != null) {
                for (Outcome outcome : values()) {
                    if (outcome.name().equalsIgnoreCase(value)) {
                        return outcome;
                    }
                }
            }
            return null;
        }
    }

    private final String questId;
    private final Outcome outcome;
    private final String detail;
    private final long worldTime;
    private final Set<String> completedOptionalObjectiveIds;
    private final int completions;
    private final List<String> stagePath;

    /**
     * A line counting {@code completions}, at least one for a completed
     * quest, and the {@code stagePath} reached, bounded as a running
     * quest's path is. {@code detail} is a failure's or an abandon's
     * reason, a lang key; a completion keeps none.
     */
    public LostTalesQuestHistoryEntry(String questId, Outcome outcome,
            String detail, long worldTime,
            Collection<String> completedOptionalObjectiveIds,
            int completions, Collection<String> stagePath) {
        this.questId = questId == null ? "" : questId;
        this.outcome = outcome == null ? Outcome.FAILED : outcome;
        this.detail = detail == null ? "" : detail;
        this.worldTime = Math.max(0L, worldTime);
        LinkedHashSet<String> completedOptional =
                new LinkedHashSet<String>();
        if (completedOptionalObjectiveIds != null) {
            for (String objectiveId : completedOptionalObjectiveIds) {
                if (objectiveId != null && objectiveId.length() > 0) {
                    completedOptional.add(objectiveId);
                }
            }
        }
        this.completedOptionalObjectiveIds =
                Collections.unmodifiableSet(completedOptional);
        this.completions = Math.max(this.outcome == Outcome.COMPLETED ? 1 : 0,
                completions);
        List<String> path = new ArrayList<String>();
        if (stagePath != null) {
            for (String reached : stagePath) {
                if (reached != null && reached.length() > 0
                        && reached.length() <= LostTalesQuestProgress.MAX_IDENTIFIER_CHARACTERS
                        && !path.contains(reached)
                        && path.size() < LostTalesQuestProgress.MAX_PATH_STAGES) {
                    path.add(reached);
                }
            }
        }
        this.stagePath = Collections.unmodifiableList(path);
    }

    /** How many times the quest was ever completed, this end included. */
    public int getCompletions() {
        return this.completions;
    }

    /**
     * Whether the quest was completed at least once, however its last run
     * ended: what a {@code completed} prerequisite reads (QUEST-10).
     */
    public boolean wasEverCompleted() {
        return this.completions > 0;
    }

    /** The stages the quest had reached when it ended, in order. */
    public List<String> getStagePath() {
        return this.stagePath;
    }

    public String getQuestId() {
        return this.questId;
    }

    public Outcome getOutcome() {
        return this.outcome;
    }

    public String getDetail() {
        return this.detail;
    }

    public long getWorldTime() {
        return this.worldTime;
    }

    public Set<String> getCompletedOptionalObjectiveIds() {
        return this.completedOptionalObjectiveIds;
    }

    public boolean isOptionalObjectiveCompleted(String objectiveId) {
        return objectiveId != null
                && this.completedOptionalObjectiveIds.contains(objectiveId);
    }

    public boolean isFailed() {
        return this.outcome == Outcome.FAILED;
    }

    public boolean isCompleted() {
        return this.outcome == Outcome.COMPLETED;
    }

    public boolean isAbandoned() {
        return this.outcome == Outcome.ABANDONED;
    }
}
