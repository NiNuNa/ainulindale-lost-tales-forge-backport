package com.ninuna.losttales.quest.progress;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/** Runtime state for one active player quest. */
public final class LostTalesQuestProgress {
    public static final int MAX_OBJECTIVE_ENTRIES = 512;
    public static final int MAX_IDENTIFIER_CHARACTERS = 256;

    private final String questId;
    private int stageIndex;
    private String stageId;
    private long acceptedWorldTime;
    private long deadlineWorldTime;
    private final Map<String, Integer> objectiveProgress = new LinkedHashMap<String, Integer>();

    public LostTalesQuestProgress(String questId, int stageIndex, String stageId) {
        this(questId, stageIndex, stageId, null, 0L, 0L);
    }

    public LostTalesQuestProgress(String questId, int stageIndex, String stageId, Map<String, Integer> objectiveProgress) {
        this(questId, stageIndex, stageId, objectiveProgress, 0L, 0L);
    }

    public LostTalesQuestProgress(String questId, int stageIndex, String stageId, Map<String, Integer> objectiveProgress, long acceptedWorldTime, long deadlineWorldTime) {
        this.questId = questId;
        this.stageIndex = Math.max(0, stageIndex);
        this.stageId = stageId == null ? "" : stageId;
        this.acceptedWorldTime = Math.max(0L, acceptedWorldTime);
        this.deadlineWorldTime = Math.max(0L, deadlineWorldTime);
        if (this.deadlineWorldTime > 0L && this.acceptedWorldTime > 0L && this.deadlineWorldTime < this.acceptedWorldTime) {
            this.deadlineWorldTime = this.acceptedWorldTime;
        }
        if (objectiveProgress != null) {
            for (Map.Entry<String, Integer> entry : objectiveProgress.entrySet()) {
                if (this.objectiveProgress.size() >= MAX_OBJECTIVE_ENTRIES) {
                    break;
                }
                if (isReasonableIdentifier(entry.getKey())
                        && entry.getValue() != null) {
                    this.objectiveProgress.put(entry.getKey(), Math.max(0, entry.getValue()));
                }
            }
        }
    }

    public LostTalesQuestProgress copy() {
        return new LostTalesQuestProgress(this.questId, this.stageIndex, this.stageId, this.objectiveProgress, this.acceptedWorldTime, this.deadlineWorldTime);
    }

    public String getQuestId() {
        return this.questId;
    }

    public int getStageIndex() {
        return this.stageIndex;
    }

    public String getStageId() {
        return this.stageId;
    }

    public void setStage(int stageIndex, String stageId) {
        this.stageIndex = Math.max(0, stageIndex);
        this.stageId = stageId == null ? "" : stageId;
    }

    public long getAcceptedWorldTime() {
        return this.acceptedWorldTime;
    }

    public long getDeadlineWorldTime() {
        return this.deadlineWorldTime;
    }

    public boolean hasTimeLimit() {
        return this.deadlineWorldTime > 0L;
    }

    public long getRemainingTicks(long worldTime) {
        if (!this.hasTimeLimit()) {
            return 0L;
        }
        return Math.max(0L, this.deadlineWorldTime - Math.max(0L, worldTime));
    }

    public boolean isExpired(long worldTime) {
        return this.hasTimeLimit() && Math.max(0L, worldTime) >= this.deadlineWorldTime;
    }

    public void setTiming(long acceptedWorldTime, long deadlineWorldTime) {
        this.acceptedWorldTime = Math.max(0L, acceptedWorldTime);
        this.deadlineWorldTime = Math.max(0L, deadlineWorldTime);
        if (this.deadlineWorldTime > 0L && this.acceptedWorldTime > 0L && this.deadlineWorldTime < this.acceptedWorldTime) {
            this.deadlineWorldTime = this.acceptedWorldTime;
        }
    }

    public Map<String, Integer> getObjectiveProgress() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, Integer>(this.objectiveProgress));
    }

    public int getObjectiveProgress(String objectiveId) {
        Integer value = this.objectiveProgress.get(objectiveId);
        return value == null ? 0 : value;
    }

    public int addObjectiveProgress(String objectiveId, int amount, int maxValue) {
        if (!isReasonableIdentifier(objectiveId) || amount <= 0) {
            return getObjectiveProgress(objectiveId);
        }

        int current = getObjectiveProgress(objectiveId);
        if (!this.objectiveProgress.containsKey(objectiveId)
                && this.objectiveProgress.size() >= MAX_OBJECTIVE_ENTRIES) {
            return current;
        }
        long updated = (long) current + (long) amount;
        if (maxValue > 0) {
            updated = Math.min(updated, maxValue);
        }
        int safeUpdated = (int)Math.min(Integer.MAX_VALUE,
                Math.max(0L, updated));
        this.objectiveProgress.put(objectiveId, safeUpdated);
        return safeUpdated;
    }

    public void setObjectiveProgress(String objectiveId, int value) {
        if (!isReasonableIdentifier(objectiveId)) {
            return;
        }
        if (!this.objectiveProgress.containsKey(objectiveId)
                && this.objectiveProgress.size() >= MAX_OBJECTIVE_ENTRIES) {
            return;
        }
        this.objectiveProgress.put(objectiveId, Math.max(0, value));
    }

    public void clearObjectiveProgress() {
        this.objectiveProgress.clear();
    }

    public NBTTagCompound writeToNBT() {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("QuestId", this.questId);
        tag.setInteger("StageIndex", this.stageIndex);
        tag.setString("StageId", this.stageId);
        tag.setLong("AcceptedWorldTime", this.acceptedWorldTime);
        tag.setLong("DeadlineWorldTime", this.deadlineWorldTime);

        NBTTagList objectiveList = new NBTTagList();
        for (Map.Entry<String, Integer> entry : this.objectiveProgress.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0 || entry.getValue() == null) {
                continue;
            }
            NBTTagCompound objectiveTag = new NBTTagCompound();
            objectiveTag.setString("ObjectiveId", entry.getKey());
            objectiveTag.setInteger("Progress", Math.max(0, entry.getValue()));
            objectiveList.appendTag(objectiveTag);
        }
        tag.setTag("ObjectiveProgress", objectiveList);
        return tag;
    }

    public static LostTalesQuestProgress readFromNBT(NBTTagCompound tag) {
        if (!isStructurallyReasonable(tag)) {
            return null;
        }
        String questId = tag.getString("QuestId");
        if (questId == null || questId.length() == 0) {
            return null;
        }

        Map<String, Integer> objectiveProgress = new LinkedHashMap<String, Integer>();
        NBTTagList objectiveList = tag.getTagList("ObjectiveProgress", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < objectiveList.tagCount(); i++) {
            NBTTagCompound objectiveTag = objectiveList.getCompoundTagAt(i);
            String objectiveId = objectiveTag.getString("ObjectiveId");
            if (objectiveId != null && objectiveId.length() > 0) {
                objectiveProgress.put(objectiveId, Math.max(0, objectiveTag.getInteger("Progress")));
            }
        }

        return new LostTalesQuestProgress(
                questId,
                tag.getInteger("StageIndex"),
                tag.getString("StageId"),
                objectiveProgress,
                tag.getLong("AcceptedWorldTime"),
                tag.getLong("DeadlineWorldTime")
        );
    }

    public static boolean isStructurallyReasonable(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("QuestId", Constants.NBT.TAG_STRING)) {
            return false;
        }
        String questId = tag.getString("QuestId");
        String stageId = tag.getString("StageId");
        if (!isReasonableIdentifier(questId)
                || stageId.length() > MAX_IDENTIFIER_CHARACTERS
                || tag.hasKey("ObjectiveProgress")
                && !(tag.getTag("ObjectiveProgress") instanceof NBTTagList)) {
            return false;
        }
        NBTBase rawObjectives = tag.getTag("ObjectiveProgress");
        NBTTagList objectives = rawObjectives instanceof NBTTagList
                ? (NBTTagList) rawObjectives : new NBTTagList();
        if (objectives.tagCount() > 0
                && objectives.func_150303_d()
                != Constants.NBT.TAG_COMPOUND) {
            return false;
        }
        if (objectives.tagCount() > MAX_OBJECTIVE_ENTRIES) {
            return false;
        }
        for (int i = 0; i < objectives.tagCount(); i++) {
            String objectiveId = objectives.getCompoundTagAt(i)
                    .getString("ObjectiveId");
            if (!isReasonableIdentifier(objectiveId)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReasonableIdentifier(String value) {
        return value != null && value.length() > 0
                && value.length() <= MAX_IDENTIFIER_CHARACTERS;
    }
}
