package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable presentation shared by Lost Tales and LOTR quests. */
public final class ClientQuestEntry {
    public enum Source {
        LOST_TALES,
        LOTR
    }

    public enum Status {
        ACTIVE,
        COMPLETED,
        FAILED,
        ABANDONED
    }

    private final Source source;
    private final String reference;
    private final String title;
    private final String subtitle;
    private final String category;
    private final String journalText;
    private final Status status;
    private final boolean tracked;
    private final int stageNumber;
    private final int stageCount;
    private final long remainingTicks;
    private final List<Objective> objectives;
    private final List<String> rewards;
    private final List<Target> targets;
    private final LostTalesQuestDefinition lostTalesDefinition;
    private final LostTalesQuestProgress lostTalesProgress;
    private final LostTalesQuestHistoryEntry historyEntry;

    public ClientQuestEntry(Source source, String reference, String title,
            String subtitle, String category, String journalText, Status status,
            boolean tracked, int stageNumber, int stageCount,
            long remainingTicks, List<Objective> objectives,
            List<String> rewards, List<Target> targets,
            LostTalesQuestDefinition lostTalesDefinition,
            LostTalesQuestProgress lostTalesProgress) {
        this(source, reference, title, subtitle, category, journalText,
                status, tracked, stageNumber, stageCount, remainingTicks,
                objectives, rewards, targets, lostTalesDefinition,
                lostTalesProgress, null);
    }

    public ClientQuestEntry(Source source, String reference, String title,
            String subtitle, String category, String journalText, Status status,
            boolean tracked, int stageNumber, int stageCount,
            long remainingTicks, List<Objective> objectives,
            List<String> rewards, List<Target> targets,
            LostTalesQuestDefinition lostTalesDefinition,
            LostTalesQuestProgress lostTalesProgress,
            LostTalesQuestHistoryEntry historyEntry) {
        this.source = source == null ? Source.LOST_TALES : source;
        this.reference = safe(reference);
        this.title = safe(title);
        this.subtitle = safe(subtitle);
        this.category = safe(category);
        this.journalText = safe(journalText);
        this.status = status == null ? Status.ACTIVE : status;
        this.tracked = tracked;
        this.stageNumber = Math.max(0, stageNumber);
        this.stageCount = Math.max(0, stageCount);
        this.remainingTicks = remainingTicks;
        this.objectives = immutableCopy(objectives);
        this.rewards = immutableStringCopy(rewards);
        this.targets = immutableTargetCopy(targets);
        this.lostTalesDefinition = lostTalesDefinition;
        this.lostTalesProgress = lostTalesProgress == null
                ? null : lostTalesProgress.copy();
        this.historyEntry = historyEntry;
    }

    public Source getSource() {
        return this.source;
    }

    public String getReference() {
        return this.reference;
    }

    public String getTitle() {
        return this.title;
    }

    public String getSubtitle() {
        return this.subtitle;
    }

    public String getCategory() {
        return this.category;
    }

    public String getJournalText() {
        return this.journalText;
    }

    public Status getStatus() {
        return this.status;
    }

    public boolean isActive() {
        return this.status == Status.ACTIVE;
    }

    public boolean isCompleted() {
        return this.status == Status.COMPLETED;
    }

    public boolean isFailed() {
        return this.status == Status.FAILED;
    }

    public boolean isAbandoned() {
        return this.status == Status.ABANDONED;
    }

    public boolean isTracked() {
        return this.tracked && isActive();
    }

    public int getStageNumber() {
        return this.stageNumber;
    }

    public int getStageCount() {
        return this.stageCount;
    }

    public long getRemainingTicks() {
        return this.remainingTicks;
    }

    public boolean hasDeadline() {
        return this.remainingTicks >= 0L;
    }

    public List<Objective> getObjectives() {
        return this.objectives;
    }

    public List<String> getRewards() {
        return this.rewards;
    }

    public List<Target> getTargets() {
        return this.targets;
    }

    public LostTalesQuestDefinition getLostTalesDefinition() {
        return this.lostTalesDefinition;
    }

    public LostTalesQuestProgress getLostTalesProgress() {
        return this.lostTalesProgress == null
                ? null : this.lostTalesProgress.copy();
    }

    public LostTalesQuestHistoryEntry getHistoryEntry() {
        return this.historyEntry;
    }

    public static final class Objective {
        private final String text;
        private final int current;
        private final int target;
        private final boolean complete;
        private final boolean optional;

        public Objective(String text, int current, int target,
                boolean complete, boolean optional) {
            this.text = safe(text);
            this.current = Math.max(0, current);
            this.target = Math.max(1, target);
            this.complete = complete;
            this.optional = optional;
        }

        public String getText() {
            return this.text;
        }

        public int getCurrent() {
            return this.current;
        }

        public int getTarget() {
            return this.target;
        }

        public boolean isComplete() {
            return this.complete;
        }

        public boolean isOptional() {
            return this.optional;
        }
    }

    public static final class Target {
        private final int dimensionId;
        private final double x;
        private final double y;
        private final double z;

        public Target(int dimensionId, double x, double y, double z) {
            this.dimensionId = dimensionId;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getDimensionId() {
            return this.dimensionId;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static List<Objective> immutableCopy(List<Objective> values) {
        return Collections.unmodifiableList(new ArrayList<Objective>(
                values == null ? Collections.<Objective>emptyList() : values));
    }

    private static List<String> immutableStringCopy(List<String> values) {
        ArrayList<String> copy = new ArrayList<String>();
        if (values != null) {
            for (String value : values) {
                if (value != null && value.length() > 0) {
                    copy.add(value);
                }
            }
        }
        return Collections.unmodifiableList(copy);
    }

    private static List<Target> immutableTargetCopy(List<Target> values) {
        return Collections.unmodifiableList(new ArrayList<Target>(
                values == null ? Collections.<Target>emptyList() : values));
    }
}
