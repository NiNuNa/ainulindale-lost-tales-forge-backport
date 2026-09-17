package com.ninuna.losttales.quest.progress;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Immutable terminal record of a completed, failed, or abandoned quest. */
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

    public LostTalesQuestHistoryEntry(String questId, Outcome outcome,
            String detail, long worldTime) {
        this(questId, outcome, detail, worldTime,
                Collections.<String>emptySet());
    }

    public LostTalesQuestHistoryEntry(String questId, Outcome outcome,
            String detail, long worldTime,
            Collection<String> completedOptionalObjectiveIds) {
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
