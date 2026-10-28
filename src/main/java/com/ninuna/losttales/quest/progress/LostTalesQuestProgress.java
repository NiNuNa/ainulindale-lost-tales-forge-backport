package com.ninuna.losttales.quest.progress;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * One running quest of a player: its stage, the stages it has passed
 * through, when it was taken, its deadline and its stage's, each
 * objective's count, who gave it (their entity's id, own name and kind),
 * and whether it waits for its hand-in, every stage done (QUEST-31).
 */
public final class LostTalesQuestProgress {
    public static final int MAX_OBJECTIVE_ENTRIES = 512;
    public static final int MAX_IDENTIFIER_CHARACTERS = 256;
    /** The most stages a path holds: as many as a quest may have. */
    public static final int MAX_PATH_STAGES = 256;
    /** The longest giver's own name kept. */
    public static final int MAX_GIVER_NAME_CHARACTERS = 64;

    private final String questId;
    private int stageIndex;
    private String stageId;
    private long acceptedWorldTime;
    private long deadlineWorldTime;
    private final Map<String, Integer> objectiveProgress = new LinkedHashMap<String, Integer>();
    private final List<String> stagePath = new ArrayList<String>();
    private long stageDeadlineWorldTime;
    private boolean returning;
    private boolean choosing;
    private String giverId = "";
    private String giverName = "";
    private String giverKind = "";

    public LostTalesQuestProgress(String questId, int stageIndex, String stageId, Map<String, Integer> objectiveProgress, long acceptedWorldTime, long deadlineWorldTime) {
        this(questId, stageIndex, stageId, objectiveProgress,
                acceptedWorldTime, deadlineWorldTime,
                Collections.<String>emptyList());
    }

    /**
     * A running quest that has passed through the stages {@code stagePath}
     * names, in the order it reached them; the stage it is on ends the path
     * where the path does not name it yet.
     */
    public LostTalesQuestProgress(String questId, int stageIndex,
            String stageId, Map<String, Integer> objectiveProgress,
            long acceptedWorldTime, long deadlineWorldTime,
            Collection<String> stagePath) {
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
        if (stagePath != null) {
            for (String reached : stagePath) {
                reach(reached);
            }
        }
        reach(this.stageId);
    }

    public LostTalesQuestProgress copy() {
        LostTalesQuestProgress copy = withoutCounts();
        for (Map.Entry<String, Integer> entry : this.objectiveProgress.entrySet()) {
            copy.objectiveProgress.put(entry.getKey(), entry.getValue());
        }
        return copy;
    }

    /** The same running quest with no objective counted: what must match for counts alone to update it. */
    public LostTalesQuestProgress withoutCounts() {
        LostTalesQuestProgress copy = new LostTalesQuestProgress(this.questId,
                this.stageIndex, this.stageId, null, this.acceptedWorldTime,
                this.deadlineWorldTime, this.stagePath);
        copy.stageDeadlineWorldTime = this.stageDeadlineWorldTime;
        copy.returning = this.returning;
        copy.choosing = this.choosing;
        copy.giverId = this.giverId;
        copy.giverName = this.giverName;
        copy.giverKind = this.giverKind;
        return copy;
    }

    /**
     * Who gave the quest: their entity's id, their own name (a name tag or
     * a LOTR NPC's personal name; empty for one known by kind) and their
     * kind as the game registers it.
     */
    public void setGiver(String entityId, String name, String kind) {
        this.giverId = isReasonableIdentifier(entityId) ? entityId : "";
        String own = name == null ? "" : name.trim();
        this.giverName = own.length() <= MAX_GIVER_NAME_CHARACTERS ? own : "";
        this.giverKind = isReasonableIdentifier(kind) ? kind : "";
    }

    /** The giver's entity id, as text; empty where the quest was not given by a creature. */
    public String getGiverId() {
        return this.giverId;
    }

    /** The giver's own name; empty for one known only by kind. */
    public String getGiverName() {
        return this.giverName;
    }

    /** The giver's kind as the game registers it ({@code losttales.Nia}); empty for none. */
    public String getGiverKind() {
        return this.giverKind;
    }

    /** Whether every stage is done and the quest waits to be handed in (QUEST-31). */
    public boolean isReturning() {
        return this.returning;
    }

    public void setReturning(boolean returning) {
        this.returning = returning;
    }

    /** Whether its stage is done and waits for the player's choice with the giver (QUEST-32). */
    public boolean isChoosing() {
        return this.choosing;
    }

    public void setChoosing(boolean choosing) {
        this.choosing = choosing;
    }

    /** When the stage's own time limit runs out; 0 for none. */
    public long getStageDeadlineWorldTime() {
        return this.stageDeadlineWorldTime;
    }

    public void setStageDeadlineWorldTime(long worldTime) {
        this.stageDeadlineWorldTime = Math.max(0L, worldTime);
    }

    /**
     * The ids of the stages the quest has reached, in the order it reached
     * them, the stage it is on last: what its journal tells (QUEST-30).
     */
    public List<String> getStagePath() {
        return Collections.unmodifiableList(new ArrayList<String>(this.stagePath));
    }

    /** Adds a stage to the path, once, while the path has room. */
    private void reach(String reached) {
        if (isReasonableIdentifier(reached)
                && !this.stagePath.contains(reached)
                && this.stagePath.size() < MAX_PATH_STAGES) {
            this.stagePath.add(reached);
        }
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

    /** Moves the quest to a stage, which joins the path. */
    public void setStage(int stageIndex, String stageId) {
        this.stageIndex = Math.max(0, stageIndex);
        this.stageId = stageId == null ? "" : stageId;
        reach(this.stageId);
    }

    public long getAcceptedWorldTime() {
        return this.acceptedWorldTime;
    }

    public long getDeadlineWorldTime() {
        return this.deadlineWorldTime;
    }

    /** Whether the quest or its stage has a time limit. */
    public boolean hasTimeLimit() {
        return effectiveDeadline() > 0L;
    }

    /** The time left on the nearer of the quest's and its stage's limits; 0 for none. */
    public long getRemainingTicks(long worldTime) {
        if (!this.hasTimeLimit()) {
            return 0L;
        }
        return Math.max(0L, effectiveDeadline() - Math.max(0L, worldTime));
    }

    public boolean isExpired(long worldTime) {
        return this.hasTimeLimit() && Math.max(0L, worldTime) >= effectiveDeadline();
    }

    /** The nearer of the quest's and its stage's deadlines; 0 for none. */
    private long effectiveDeadline() {
        if (this.deadlineWorldTime <= 0L) {
            return this.stageDeadlineWorldTime;
        }
        return this.stageDeadlineWorldTime <= 0L ? this.deadlineWorldTime
                : Math.min(this.deadlineWorldTime, this.stageDeadlineWorldTime);
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
        tag.setTag("StagePath", writeStagePath(this.stagePath));
        tag.setLong("StageDeadlineWorldTime", this.stageDeadlineWorldTime);
        tag.setBoolean("Returning", this.returning);
        tag.setBoolean("Choosing", this.choosing);
        tag.setString("GiverId", this.giverId);
        tag.setString("GiverName", this.giverName);
        tag.setString("GiverKind", this.giverKind);
        return tag;
    }

    /** A path of stage ids as the log saves it, a History line's too. */
    public static NBTTagList writeStagePath(List<String> stagePath) {
        NBTTagList list = new NBTTagList();
        for (String reached : stagePath) {
            NBTTagCompound stageTag = new NBTTagCompound();
            stageTag.setString("StageId", reached);
            list.appendTag(stageTag);
        }
        return list;
    }

    /** A path as {@link #writeStagePath} saved it under {@code key}. */
    public static List<String> readStagePath(NBTTagCompound owner, String key) {
        List<String> path = new ArrayList<String>();
        NBTTagList list = owner.getTagList(key, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            path.add(list.getCompoundTagAt(i).getString("StageId"));
        }
        return path;
    }

    /**
     * Whether a saved path is one {@link #writeStagePath} could have
     * written: every running quest and History line has one, empty or not.
     */
    public static boolean isReasonableStagePath(NBTTagCompound owner, String key) {
        if (!owner.hasKey(key, Constants.NBT.TAG_LIST)) {
            return false;
        }
        NBTTagList list = (NBTTagList) owner.getTag(key);
        if (list.tagCount() > MAX_PATH_STAGES || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            return false;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            if (!isReasonableIdentifier(list.getCompoundTagAt(i)
                    .getString("StageId"))) {
                return false;
            }
        }
        return true;
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

        LostTalesQuestProgress progress = new LostTalesQuestProgress(
                questId,
                tag.getInteger("StageIndex"),
                tag.getString("StageId"),
                objectiveProgress,
                tag.getLong("AcceptedWorldTime"),
                tag.getLong("DeadlineWorldTime"),
                readStagePath(tag, "StagePath")
        );
        progress.setStageDeadlineWorldTime(tag.getLong("StageDeadlineWorldTime"));
        progress.setReturning(tag.getBoolean("Returning"));
        progress.setChoosing(tag.getBoolean("Choosing"));
        progress.setGiver(tag.getString("GiverId"), tag.getString("GiverName"),
                tag.getString("GiverKind"));
        return progress;
    }

    public static boolean isStructurallyReasonable(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey("QuestId", Constants.NBT.TAG_STRING)) {
            return false;
        }
        String questId = tag.getString("QuestId");
        String stageId = tag.getString("StageId");
        if (!LostTalesQuestIds.fits(questId)
                || stageId.length() > MAX_IDENTIFIER_CHARACTERS
                || tag.getString("GiverId").length() > MAX_IDENTIFIER_CHARACTERS
                || tag.getString("GiverName").length() > MAX_GIVER_NAME_CHARACTERS
                || tag.getString("GiverKind").length() > MAX_IDENTIFIER_CHARACTERS
                || tag.getLong("StageDeadlineWorldTime") < 0L
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
        if (objectives.tagCount() > MAX_OBJECTIVE_ENTRIES
                || !isReasonableStagePath(tag, "StagePath")) {
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
