package com.ninuna.losttales.quest.progress;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/** Runtime state for one active player quest. */
public final class LostTalesQuestProgress {
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
                if (entry.getKey() != null && entry.getKey().length() > 0 && entry.getValue() != null) {
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
        if (objectiveId == null || objectiveId.length() == 0 || amount <= 0) {
            return getObjectiveProgress(objectiveId);
        }

        int current = getObjectiveProgress(objectiveId);
        int updated = current + amount;
        if (maxValue > 0) {
            updated = Math.min(updated, maxValue);
        }
        this.objectiveProgress.put(objectiveId, Math.max(0, updated));
        return updated;
    }

    public void setObjectiveProgress(String objectiveId, int value) {
        if (objectiveId == null || objectiveId.length() == 0) {
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
        if (tag == null || !tag.hasKey("QuestId")) {
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
}
