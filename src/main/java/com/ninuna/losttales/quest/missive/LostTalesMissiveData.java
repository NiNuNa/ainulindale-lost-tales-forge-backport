package com.ninuna.losttales.quest.missive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Server-authored generated missive data.
 *
 * This class is deliberately independent from blocks, items, GUIs, and networking
 * so generated missives can be stored on item stacks, tile entities, or player data.
 */
public final class LostTalesMissiveData {
    public static final String QUEST_ID_PREFIX = "losttales:missive/generated/";

    private final String questId;
    private final String questType;
    private final String title;
    private final String description;
    private final String issuer;
    private final String flavorText;
    private final boolean repeatable;
    private final boolean firstComeFirstServed;
    private final long generationWorldTime;
    private final long timeLimitTicks;
    private final Map<String, String> generationContext;
    private final List<LostTalesMissiveObjectiveData> objectives;
    private final LostTalesMissiveRewardData rewardData;

    public LostTalesMissiveData(String questId, String questType, String title, String description, String issuer, String flavorText, boolean repeatable, boolean firstComeFirstServed, long generationWorldTime, long timeLimitTicks, Map<String, String> generationContext, List<LostTalesMissiveObjectiveData> objectives, LostTalesMissiveRewardData rewardData) {
        this.questId = LostTalesMissiveObjectiveData.clean(questId);
        this.questType = LostTalesMissiveObjectiveData.clean(questType);
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.issuer = issuer == null ? "" : issuer;
        this.flavorText = flavorText == null ? "" : flavorText;
        this.repeatable = repeatable;
        this.firstComeFirstServed = firstComeFirstServed;
        this.generationWorldTime = Math.max(0L, generationWorldTime);
        this.timeLimitTicks = Math.max(0L, timeLimitTicks);
        this.generationContext = Collections.unmodifiableMap(LostTalesMissiveObjectiveData.copyStringMap(generationContext));
        this.objectives = Collections.unmodifiableList(copyObjectives(objectives));
        this.rewardData = rewardData == null ? LostTalesMissiveRewardData.empty() : rewardData;
    }

    public String getQuestId() {
        return this.questId;
    }

    public String getQuestType() {
        return this.questType;
    }

    public String getTitle() {
        return this.title;
    }

    public String getDescription() {
        return this.description;
    }

    public String getIssuer() {
        return this.issuer;
    }

    public String getFlavorText() {
        return this.flavorText;
    }

    public boolean isRepeatable() {
        return this.repeatable;
    }

    public boolean isFirstComeFirstServed() {
        return this.firstComeFirstServed;
    }

    public long getGenerationWorldTime() {
        return this.generationWorldTime;
    }

    public long getTimeLimitTicks() {
        return this.timeLimitTicks;
    }

    public boolean hasTimeLimit() {
        return this.timeLimitTicks > 0L;
    }

    public Map<String, String> getGenerationContext() {
        return this.generationContext;
    }

    public List<LostTalesMissiveObjectiveData> getObjectives() {
        return this.objectives;
    }

    public LostTalesMissiveRewardData getRewardData() {
        return this.rewardData;
    }

    public boolean isValid() {
        if (this.questId.length() == 0 || this.questType.length() == 0 || this.title.length() == 0 || this.objectives.isEmpty()) {
            return false;
        }
        for (LostTalesMissiveObjectiveData objective : this.objectives) {
            if (objective == null || !objective.isValid()) {
                return false;
            }
        }
        return true;
    }

    public static String createQuestId(String boardKey, long generationWorldTime, int sequence) {
        String cleanedBoardKey = boardKey == null ? "board" : boardKey.trim().toLowerCase();
        cleanedBoardKey = cleanedBoardKey.replace(' ', '_').replace(':', '_').replace('/', '_').replace('\\', '_');
        if (cleanedBoardKey.length() == 0) {
            cleanedBoardKey = "board";
        }
        return QUEST_ID_PREFIX + cleanedBoardKey + "/" + Math.max(0L, generationWorldTime) + "_" + Math.max(0, sequence);
    }

    private static List<LostTalesMissiveObjectiveData> copyObjectives(List<LostTalesMissiveObjectiveData> source) {
        ArrayList<LostTalesMissiveObjectiveData> copy = new ArrayList<LostTalesMissiveObjectiveData>();
        if (source == null) {
            return copy;
        }
        for (LostTalesMissiveObjectiveData objective : source) {
            if (objective != null) {
                copy.add(objective);
            }
        }
        return copy;
    }

    public static Builder builder(String questId, String questType) {
        return new Builder(questId, questType);
    }

    public static final class Builder {
        private final String questId;
        private final String questType;
        private String title = "";
        private String description = "";
        private String issuer = "";
        private String flavorText = "";
        private boolean repeatable = true;
        private boolean firstComeFirstServed = true;
        private long generationWorldTime;
        private long timeLimitTicks;
        private final Map<String, String> generationContext = new LinkedHashMap<String, String>();
        private final List<LostTalesMissiveObjectiveData> objectives = new ArrayList<LostTalesMissiveObjectiveData>();
        private LostTalesMissiveRewardData rewardData = LostTalesMissiveRewardData.empty();

        private Builder(String questId, String questType) {
            this.questId = questId;
            this.questType = questType;
        }

        public Builder title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        public Builder description(String description) {
            this.description = description == null ? "" : description;
            return this;
        }

        public Builder issuer(String issuer) {
            this.issuer = issuer == null ? "" : issuer;
            return this;
        }

        public Builder flavorText(String flavorText) {
            this.flavorText = flavorText == null ? "" : flavorText;
            return this;
        }

        public Builder repeatable(boolean repeatable) {
            this.repeatable = repeatable;
            return this;
        }

        public Builder firstComeFirstServed(boolean firstComeFirstServed) {
            this.firstComeFirstServed = firstComeFirstServed;
            return this;
        }

        public Builder generationWorldTime(long generationWorldTime) {
            this.generationWorldTime = generationWorldTime;
            return this;
        }

        public Builder timeLimitTicks(long timeLimitTicks) {
            this.timeLimitTicks = timeLimitTicks;
            return this;
        }

        public Builder context(String key, String value) {
            if (key != null && key.trim().length() > 0 && value != null) {
                this.generationContext.put(key.trim(), value);
            }
            return this;
        }

        public Builder objective(LostTalesMissiveObjectiveData objective) {
            if (objective != null) {
                this.objectives.add(objective);
            }
            return this;
        }

        public Builder rewardData(LostTalesMissiveRewardData rewardData) {
            this.rewardData = rewardData == null ? LostTalesMissiveRewardData.empty() : rewardData;
            return this;
        }

        public LostTalesMissiveData build() {
            return new LostTalesMissiveData(this.questId, this.questType, this.title, this.description, this.issuer, this.flavorText, this.repeatable, this.firstComeFirstServed, this.generationWorldTime, this.timeLimitTicks, this.generationContext, this.objectives, this.rewardData);
        }
    }
}
