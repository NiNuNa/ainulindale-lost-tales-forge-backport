package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * Small NBT serializer for quest definitions that are authored at runtime.
 *
 * Bundled quests still load from JSON resources. This helper exists so generated
 * missive quests can be saved in player data and synced to clients without needing
 * modern datapack systems that do not exist in Forge 1.7.10.
 */
public final class LostTalesQuestDefinitionNbt {
    private static final String KEY_ID = "Id";
    private static final String KEY_TITLE = "Title";
    private static final String KEY_DESCRIPTION = "Description";
    private static final String KEY_REPEATABLE = "Repeatable";
    private static final String KEY_START_MODE = "StartMode";
    private static final String KEY_PREREQUISITES = "Prerequisites";
    private static final String KEY_REWARDS = "Rewards";
    private static final String KEY_INTERACTION = "Interaction";
    private static final String KEY_MARKERS = "Markers";
    private static final String KEY_JOURNAL_LOG = "JournalLog";
    private static final String KEY_STAGES = "Stages";
    private static final String KEY_STAGE_ID = "StageId";
    private static final String KEY_OBJECTIVES = "Objectives";
    private static final String KEY_OBJECTIVE_ID = "ObjectiveId";
    private static final String KEY_OBJECTIVE_TYPE = "Type";
    private static final String KEY_OBJECTIVE_DESCRIPTION = "Description";
    private static final String KEY_OBJECTIVE_OPTIONAL = "Optional";
    private static final String KEY_OBJECTIVE_PARAMS = "Params";
    private static final String KEY_MAP_KEY = "Key";
    private static final String KEY_MAP_VALUE = "Value";

    private LostTalesQuestDefinitionNbt() {}

    public static NBTTagCompound write(LostTalesQuestDefinition quest) {
        NBTTagCompound tag = new NBTTagCompound();
        if (quest == null || quest.getId() == null || quest.getId().length() == 0) {
            return tag;
        }

        tag.setString(KEY_ID, quest.getId());
        tag.setString(KEY_TITLE, safe(quest.getTitle()));
        tag.setString(KEY_DESCRIPTION, safe(quest.getDescription()));
        tag.setBoolean(KEY_REPEATABLE, quest.isRepeatable());
        tag.setString(KEY_START_MODE, safe(quest.getStartMode()));
        tag.setTag(KEY_PREREQUISITES, writeStringMap(quest.getPrerequisites()));
        tag.setTag(KEY_REWARDS, writeStringMap(quest.getRewards()));
        tag.setTag(KEY_INTERACTION, writeStringMap(quest.getInteraction()));
        tag.setTag(KEY_MARKERS, writeStringMap(quest.getMarkers()));
        tag.setTag(KEY_JOURNAL_LOG, writeStringMap(quest.getJournalLog()));

        NBTTagList stages = new NBTTagList();
        for (LostTalesQuestStageDefinition stage : quest.getStages()) {
            if (stage == null) {
                continue;
            }
            NBTTagCompound stageTag = new NBTTagCompound();
            stageTag.setString(KEY_STAGE_ID, safe(stage.getId()));

            NBTTagList objectives = new NBTTagList();
            for (LostTalesQuestObjectiveDefinition objective : stage.getObjectives()) {
                if (objective == null) {
                    continue;
                }
                NBTTagCompound objectiveTag = new NBTTagCompound();
                objectiveTag.setString(KEY_OBJECTIVE_ID, safe(objective.getId()));
                objectiveTag.setString(KEY_OBJECTIVE_TYPE, safe(objective.getType()));
                objectiveTag.setString(KEY_OBJECTIVE_DESCRIPTION, safe(objective.getDescription()));
                objectiveTag.setBoolean(KEY_OBJECTIVE_OPTIONAL, objective.isOptional());
                objectiveTag.setTag(KEY_OBJECTIVE_PARAMS, writeStringMap(objective.getParams()));
                objectives.appendTag(objectiveTag);
            }
            stageTag.setTag(KEY_OBJECTIVES, objectives);
            stages.appendTag(stageTag);
        }
        tag.setTag(KEY_STAGES, stages);
        return tag;
    }

    public static LostTalesQuestDefinition read(NBTTagCompound tag) {
        if (tag == null || !tag.hasKey(KEY_ID)) {
            return null;
        }

        String id = tag.getString(KEY_ID);
        if (id == null || id.length() == 0) {
            return null;
        }

        List<LostTalesQuestStageDefinition> stages = new ArrayList<LostTalesQuestStageDefinition>();
        NBTTagList stageList = tag.getTagList(KEY_STAGES, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < stageList.tagCount(); i++) {
            NBTTagCompound stageTag = stageList.getCompoundTagAt(i);
            String stageId = stageTag.getString(KEY_STAGE_ID);
            List<LostTalesQuestObjectiveDefinition> objectives = new ArrayList<LostTalesQuestObjectiveDefinition>();

            NBTTagList objectiveList = stageTag.getTagList(KEY_OBJECTIVES, Constants.NBT.TAG_COMPOUND);
            for (int j = 0; j < objectiveList.tagCount(); j++) {
                NBTTagCompound objectiveTag = objectiveList.getCompoundTagAt(j);
                String objectiveId = objectiveTag.getString(KEY_OBJECTIVE_ID);
                String type = objectiveTag.getString(KEY_OBJECTIVE_TYPE);
                if (objectiveId == null || objectiveId.length() == 0 || type == null || type.length() == 0) {
                    continue;
                }
                objectives.add(new LostTalesQuestObjectiveDefinition(
                        objectiveId,
                        type,
                        objectiveTag.getString(KEY_OBJECTIVE_DESCRIPTION),
                        objectiveTag.getBoolean(KEY_OBJECTIVE_OPTIONAL),
                        readStringMap(objectiveTag.getTagList(KEY_OBJECTIVE_PARAMS, Constants.NBT.TAG_COMPOUND))
                ));
            }
            stages.add(new LostTalesQuestStageDefinition(stageId, objectives));
        }

        return new LostTalesQuestDefinition(
                id,
                tag.getString(KEY_TITLE),
                tag.getString(KEY_DESCRIPTION),
                tag.getBoolean(KEY_REPEATABLE),
                tag.getString(KEY_START_MODE),
                readStringMap(tag.getTagList(KEY_PREREQUISITES, Constants.NBT.TAG_COMPOUND)),
                readStringMap(tag.getTagList(KEY_REWARDS, Constants.NBT.TAG_COMPOUND)),
                readStringMap(tag.getTagList(KEY_INTERACTION, Constants.NBT.TAG_COMPOUND)),
                readStringMap(tag.getTagList(KEY_MARKERS, Constants.NBT.TAG_COMPOUND)),
                readStringMap(tag.getTagList(KEY_JOURNAL_LOG, Constants.NBT.TAG_COMPOUND)),
                stages
        );
    }

    private static NBTTagList writeStringMap(Map<String, String> values) {
        NBTTagList list = new NBTTagList();
        if (values == null || values.isEmpty()) {
            return list;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0 || entry.getValue() == null) {
                continue;
            }
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString(KEY_MAP_KEY, entry.getKey());
            entryTag.setString(KEY_MAP_VALUE, entry.getValue());
            list.appendTag(entryTag);
        }
        return list;
    }

    private static Map<String, String> readStringMap(NBTTagList list) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        if (list == null) {
            return map;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entryTag = list.getCompoundTagAt(i);
            String key = entryTag.getString(KEY_MAP_KEY);
            if (key != null && key.length() > 0) {
                map.put(key, entryTag.getString(KEY_MAP_VALUE));
            }
        }
        return map;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
