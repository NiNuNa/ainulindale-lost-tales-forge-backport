package com.ninuna.losttales.quest.missive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A missive as the server writes it: its quest id, the template ids and
 * target it is worded by ({@link MissiveWords}), objectives, reward and
 * time limit, and when and where it was posted. Plain data, kept in a
 * letter's item data ({@link LostTalesMissiveNbt}); it holds no sentence
 * in any language, so each player reads it in their own.
 */
public final class LostTalesMissiveData {
    public static final String QUEST_ID_PREFIX = "losttales:missive/generated/";

    private final String questId;
    private final String questType;
    private final String titleId;
    private final String descriptionId;
    private final String issuerId;
    private final String flavorId;
    private final String target;
    private final boolean repeatable;
    private final boolean firstComeFirstServed;
    private final long generationWorldTime;
    private final long timeLimitTicks;
    private final Map<String, String> generationContext;
    private final List<LostTalesMissiveObjectiveData> objectives;
    private final LostTalesMissiveRewardData rewardData;

    public LostTalesMissiveData(String questId, String questType, String titleId, String descriptionId, String issuerId, String flavorId, String target, boolean repeatable, boolean firstComeFirstServed, long generationWorldTime, long timeLimitTicks, Map<String, String> generationContext, List<LostTalesMissiveObjectiveData> objectives, LostTalesMissiveRewardData rewardData) {
        this.questId = LostTalesMissiveObjectiveData.clean(questId);
        this.questType = LostTalesMissiveObjectiveData.clean(questType);
        this.titleId = LostTalesMissiveObjectiveData.clean(titleId);
        this.descriptionId = LostTalesMissiveObjectiveData.clean(descriptionId);
        this.issuerId = LostTalesMissiveObjectiveData.clean(issuerId);
        this.flavorId = LostTalesMissiveObjectiveData.clean(flavorId);
        this.target = LostTalesMissiveObjectiveData.clean(target);
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

    /** The title's template id: {@code trouble_on_the_road}. */
    public String getTitleId() {
        return this.titleId;
    }

    /** The description's template id, {@code kill} or {@code gather}; empty for none. */
    public String getDescriptionId() {
        return this.descriptionId;
    }

    /** Who wrote the letter, as a template id; empty for nobody named. */
    public String getIssuerId() {
        return this.issuerId;
    }

    /** The flavour line's template id; empty for none. */
    public String getFlavorId() {
        return this.flavorId;
    }

    /** What the letter is about: a creature kind, a group or an item id; empty for nothing named. */
    public String getTarget() {
        return this.target;
    }

    public boolean isRepeatable() {
        return this.repeatable;
    }

    public boolean isFirstComeFirstServed() {
        return this.firstComeFirstServed;
    }

    /** The world time the notice was posted at, on the board it stands on; a board takes it down after its expiry. */
    public long getGenerationWorldTime() {
        return this.generationWorldTime;
    }

    /** The same missive, posted at {@code worldTime}: a letter pinned back on a board. */
    public LostTalesMissiveData postedAt(long worldTime) {
        return new LostTalesMissiveData(this.questId, this.questType,
                this.titleId, this.descriptionId, this.issuerId,
                this.flavorId, this.target, this.repeatable, this.firstComeFirstServed, worldTime,
                this.timeLimitTicks, this.generationContext, this.objectives,
                this.rewardData);
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

    /**
     * Whether the missive can be read: a quest id and kind, a title id,
     * every other template id empty or well formed, a target empty or
     * well formed, and objectives that are each readable.
     */
    public boolean isValid() {
        if (this.questId.length() == 0 || this.questType.length() == 0
                || !MissiveWords.isTemplateId(this.titleId)
                || !isOptionalTemplateId(this.descriptionId)
                || !isOptionalTemplateId(this.issuerId)
                || !isOptionalTemplateId(this.flavorId)
                || this.target.length() > 0 && !MissiveWords.isTarget(this.target)
                || this.objectives.isEmpty()) {
            return false;
        }
        for (LostTalesMissiveObjectiveData objective : this.objectives) {
            if (objective == null || !objective.isValid()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOptionalTemplateId(String id) {
        return id.length() == 0 || MissiveWords.isTemplateId(id);
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
        private String titleId = "";
        private String descriptionId = "";
        private String issuerId = "";
        private String flavorId = "";
        private String target = "";
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

        public Builder titleId(String titleId) {
            this.titleId = titleId == null ? "" : titleId;
            return this;
        }

        public Builder descriptionId(String descriptionId) {
            this.descriptionId = descriptionId == null ? "" : descriptionId;
            return this;
        }

        public Builder issuerId(String issuerId) {
            this.issuerId = issuerId == null ? "" : issuerId;
            return this;
        }

        public Builder flavorId(String flavorId) {
            this.flavorId = flavorId == null ? "" : flavorId;
            return this;
        }

        public Builder target(String target) {
            this.target = target == null ? "" : target;
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
            return new LostTalesMissiveData(this.questId, this.questType, this.titleId, this.descriptionId, this.issuerId, this.flavorId, this.target, this.repeatable, this.firstComeFirstServed, this.generationWorldTime, this.timeLimitTicks, this.generationContext, this.objectives, this.rewardData);
        }
    }
}
