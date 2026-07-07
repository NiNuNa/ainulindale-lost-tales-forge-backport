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
    private final Map<String, Integer> objectiveProgress = new LinkedHashMap<String, Integer>();

    public LostTalesQuestProgress(String questId, int stageIndex, String stageId) {
        this(questId, stageIndex, stageId, null);
    }

    public LostTalesQuestProgress(String questId, int stageIndex, String stageId, Map<String, Integer> objectiveProgress) {
        this.questId = questId;
        this.stageIndex = Math.max(0, stageIndex);
        this.stageId = stageId == null ? "" : stageId;
        if (objectiveProgress != null) {
            for (Map.Entry<String, Integer> entry : objectiveProgress.entrySet()) {
                if (entry.getKey() != null && entry.getKey().length() > 0 && entry.getValue() != null) {
                    this.objectiveProgress.put(entry.getKey(), Math.max(0, entry.getValue()));
                }
            }
        }
    }

    public LostTalesQuestProgress copy() {
        return new LostTalesQuestProgress(this.questId, this.stageIndex, this.stageId, this.objectiveProgress);
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

        return new LostTalesQuestProgress(questId, tag.getInteger("StageIndex"), tag.getString("StageId"), objectiveProgress);
    }
}
