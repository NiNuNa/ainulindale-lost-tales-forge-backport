package com.ninuna.losttales.quest.missive;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A missive as the server writes it: its quest id, the words it is
 * worded by ({@link MissiveWords}), objectives, reward and time limit, and
 * when and where it was posted. Plain data, kept in a letter's item data
 * ({@link LostTalesMissiveNbt}). A letter from a bundled template holds
 * template ids and a target, no sentence in any language, so each player
 * reads it in their own; one from a server's template ({@link #isWritten},
 * QUEST-42) holds its title, issuer and flavour as its operator wrote
 * them.
 */
public final class LostTalesMissiveData {
    public static final String QUEST_ID_PREFIX = "losttales:missive/generated/";

    private final String questId;
    private final String questType;
    private final boolean written;
    private final String titleId;
    private final String descriptionId;
    private final String issuerId;
    private final String flavorId;
    private final String target;
    private final long generationWorldTime;
    private final long timeLimitTicks;
    private final Map<String, String> generationContext;
    private final List<LostTalesMissiveObjectiveData> objectives;
    private final LostTalesMissiveRewardData rewardData;

    public LostTalesMissiveData(String questId, String questType, boolean written, String titleId, String descriptionId, String issuerId, String flavorId, String target, long generationWorldTime, long timeLimitTicks, Map<String, String> generationContext, List<LostTalesMissiveObjectiveData> objectives, LostTalesMissiveRewardData rewardData) {
        this.questId = LostTalesMissiveObjectiveData.clean(questId);
        this.questType = LostTalesMissiveObjectiveData.clean(questType);
        this.written = written;
        this.titleId = LostTalesMissiveObjectiveData.clean(titleId);
        this.descriptionId = LostTalesMissiveObjectiveData.clean(descriptionId);
        this.issuerId = LostTalesMissiveObjectiveData.clean(issuerId);
        this.flavorId = LostTalesMissiveObjectiveData.clean(flavorId);
        this.target = LostTalesMissiveObjectiveData.clean(target);
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

    /**
     * Whether the title, issuer and flavour are words a server's template
     * wrote, shown as written in every language, rather than template ids
     * (QUEST-42). The description is a template id either way.
     */
    public boolean isWritten() {
        return this.written;
    }

    /** The title's template id, {@code trouble_on_the_road}, or the title as written. */
    public String getTitleId() {
        return this.titleId;
    }

    /** The description's template id, {@code kill} or {@code gather}; empty for none. */
    public String getDescriptionId() {
        return this.descriptionId;
    }

    /** Who wrote the letter, as a template id or as written; empty for nobody named. */
    public String getIssuerId() {
        return this.issuerId;
    }

    /** The flavour line's template id, or the line as written; empty for none. */
    public String getFlavorId() {
        return this.flavorId;
    }

    /** What the letter is about: a creature kind, a group or an item id; empty for nothing named. */
    public String getTarget() {
        return this.target;
    }

    /**
     * The world time the notice was first posted at. Its days run from
     * here wherever its letter goes up again (QUEST-34); a board takes it
     * down after its expiry.
     */
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

    /**
     * Whether the missive can be read: a quest id and kind, a title, the
     * issuer and flavour empty or well formed (template ids, or written
     * words for a letter written so), a description id empty or well
     * formed, a target empty or well formed, and objectives that are each
     * readable.
     */
    public boolean isValid() {
        if (this.questId.length() == 0 || this.questType.length() == 0
                || !MissiveWords.isWord(this.titleId, this.written)
                || !isOptionalTemplateId(this.descriptionId)
                || this.issuerId.length() > 0
                        && !MissiveWords.isWord(this.issuerId, this.written)
                || this.flavorId.length() > 0
                        && !MissiveWords.isWord(this.flavorId, this.written)
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
        private boolean written;
        private String titleId = "";
        private String descriptionId = "";
        private String issuerId = "";
        private String flavorId = "";
        private String target = "";
        private long generationWorldTime;
        private long timeLimitTicks;
        private final Map<String, String> generationContext = new LinkedHashMap<String, String>();
        private final List<LostTalesMissiveObjectiveData> objectives = new ArrayList<LostTalesMissiveObjectiveData>();
        private LostTalesMissiveRewardData rewardData = LostTalesMissiveRewardData.empty();

        private Builder(String questId, String questType) {
            this.questId = questId;
            this.questType = questType;
        }

        /** The title, issuer and flavour given are written words, not template ids (QUEST-42). */
        public Builder written(boolean written) {
            this.written = written;
            return this;
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
            return new LostTalesMissiveData(this.questId, this.questType, this.written, this.titleId, this.descriptionId, this.issuerId, this.flavorId, this.target, this.generationWorldTime, this.timeLimitTicks, this.generationContext, this.objectives, this.rewardData);
        }
    }
}
