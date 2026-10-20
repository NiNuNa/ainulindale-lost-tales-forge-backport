package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestDefinitionNbt;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * A missive as a letter and a board keep it: in the letter's item data,
 * its words as template ids and a target ({@link MissiveWords}), or, for a
 * letter from a server's template, its title, issuer and flavour as
 * written ({@code Written}, QUEST-42). A letter with any field out of
 * bounds reads as one that cannot be read.
 */
public final class LostTalesMissiveNbt {
    public static final String TAG_MISSIVE = "LostTalesMissive";
    public static final int MAX_OBJECTIVES = 512;
    public static final int MAX_IDENTIFIER_CHARACTERS = 256;

    private LostTalesMissiveNbt() {}

    public static void writeToItemStack(ItemStack stack, LostTalesMissiveData missive) {
        if (stack == null) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null) {
            root = new NBTTagCompound();
            stack.setTagCompound(root);
        }
        if (missive == null) {
            root.removeTag(TAG_MISSIVE);
            return;
        }
        root.setTag(TAG_MISSIVE, writeToNBT(missive));
    }

    public static LostTalesMissiveData readFromItemStack(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound()) {
            return null;
        }
        NBTTagCompound root = stack.getTagCompound();
        if (root == null || !root.hasKey(
                TAG_MISSIVE, Constants.NBT.TAG_COMPOUND)) {
            return null;
        }
        return readFromNBT(root.getCompoundTag(TAG_MISSIVE));
    }

    public static NBTTagCompound writeToNBT(LostTalesMissiveData missive) {
        NBTTagCompound tag = new NBTTagCompound();
        if (missive == null) {
            return tag;
        }

        tag.setString("QuestId", missive.getQuestId());
        tag.setString("QuestType", missive.getQuestType());
        tag.setBoolean("Written", missive.isWritten());
        tag.setString("TitleId", missive.getTitleId());
        tag.setString("DescriptionId", missive.getDescriptionId());
        tag.setString("IssuerId", missive.getIssuerId());
        tag.setString("FlavorId", missive.getFlavorId());
        tag.setString("Target", missive.getTarget());
        tag.setLong("GenerationWorldTime", missive.getGenerationWorldTime());
        tag.setLong("TimeLimitTicks", missive.getTimeLimitTicks());
        tag.setTag("GenerationContext", writeStringMap(missive.getGenerationContext()));
        tag.setTag("Objectives", writeObjectives(missive.getObjectives()));
        tag.setTag("Rewards", writeStringMap(missive.getRewardData().getRewards()));
        return tag;
    }

    public static LostTalesMissiveData readFromNBT(NBTTagCompound tag) {
        if (!isStructurallyReasonable(tag)) {
            return null;
        }

        LostTalesMissiveData missive = new LostTalesMissiveData(
                tag.getString("QuestId"),
                tag.getString("QuestType"),
                tag.getBoolean("Written"),
                tag.getString("TitleId"),
                tag.getString("DescriptionId"),
                tag.getString("IssuerId"),
                tag.getString("FlavorId"),
                tag.getString("Target"),
                tag.getLong("GenerationWorldTime"),
                tag.getLong("TimeLimitTicks"),
                readStringMap(tag.getTagList("GenerationContext", Constants.NBT.TAG_COMPOUND)),
                readObjectives(tag.getTagList("Objectives", Constants.NBT.TAG_COMPOUND)),
                new LostTalesMissiveRewardData(readStringMap(tag.getTagList("Rewards", Constants.NBT.TAG_COMPOUND)))
        );
        return missive.isValid() ? missive : null;
    }

    public static boolean isStructurallyReasonable(NBTTagCompound tag) {
        // A written letter's words are longer than an id; isValid holds each to its bound.
        int words = tag != null && tag.getBoolean("Written")
                ? MissiveWords.MAX_WRITTEN_BYTES : MissiveWords.MAX_TEMPLATE_ID_BYTES;
        if (!NbtTags.hasReasonableString(tag, "QuestId",
                MAX_IDENTIFIER_CHARACTERS, true)
                || !LostTalesQuestIds.fits(tag.getString("QuestId"))
                || !NbtTags.hasReasonableString(tag, "QuestType",
                MAX_IDENTIFIER_CHARACTERS, true)
                || !tag.hasKey("Written", Constants.NBT.TAG_BYTE)
                || !NbtTags.hasReasonableString(tag, "TitleId", words, true)
                || !NbtTags.hasReasonableString(tag, "DescriptionId",
                MissiveWords.MAX_TEMPLATE_ID_BYTES, false)
                || !NbtTags.hasReasonableString(tag, "IssuerId", words, false)
                || !NbtTags.hasReasonableString(tag, "FlavorId", words, false)
                || !NbtTags.hasReasonableString(tag, "Target",
                MissiveWords.MAX_TARGET_BYTES, false)
                || tag.getLong("GenerationWorldTime") < 0L
                || tag.getLong("TimeLimitTicks") < 0L
                || !LostTalesQuestDefinitionNbt.isStringMapReasonable(tag, "GenerationContext")
                || !LostTalesQuestDefinitionNbt.isStringMapReasonable(tag, "Rewards")
                || !NbtTags.hasCompoundListWithinLimit(
                tag, "Objectives", MAX_OBJECTIVES)) {
            return false;
        }
        NBTTagList objectives = tag.getTagList(
                "Objectives", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < objectives.tagCount(); i++) {
            NBTTagCompound objective = objectives.getCompoundTagAt(i);
            if (!NbtTags.hasReasonableString(objective, "Id",
                    MAX_IDENTIFIER_CHARACTERS, true)
                    || !NbtTags.hasReasonableString(objective, "Type",
                    MAX_IDENTIFIER_CHARACTERS, true)
                    || !LostTalesQuestDefinitionNbt.isStringMapReasonable(objective, "Params")) {
                return false;
            }
        }
        return true;
    }

    private static NBTTagList writeObjectives(List<LostTalesMissiveObjectiveData> objectives) {
        NBTTagList list = new NBTTagList();
        if (objectives == null) {
            return list;
        }
        for (LostTalesMissiveObjectiveData objective : objectives) {
            if (objective == null) {
                continue;
            }
            NBTTagCompound objectiveTag = new NBTTagCompound();
            objectiveTag.setString("Id", objective.getId());
            objectiveTag.setString("Type", objective.getType());
            objectiveTag.setBoolean("Optional", objective.isOptional());
            objectiveTag.setTag("Params", writeStringMap(objective.getParams()));
            list.appendTag(objectiveTag);
        }
        return list;
    }

    private static List<LostTalesMissiveObjectiveData> readObjectives(NBTTagList list) {
        ArrayList<LostTalesMissiveObjectiveData> objectives = new ArrayList<LostTalesMissiveObjectiveData>();
        if (list == null) {
            return objectives;
        }
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound objectiveTag = list.getCompoundTagAt(i);
            LostTalesMissiveObjectiveData objective = new LostTalesMissiveObjectiveData(
                    objectiveTag.getString("Id"),
                    objectiveTag.getString("Type"),
                    objectiveTag.getBoolean("Optional"),
                    readStringMap(objectiveTag.getTagList("Params", Constants.NBT.TAG_COMPOUND))
            );
            if (objective.isValid()) {
                objectives.add(objective);
            }
        }
        return objectives;
    }

    private static NBTTagList writeStringMap(Map<String, String> map) {
        NBTTagList list = new NBTTagList();
        if (map == null) {
            return list;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry == null || entry.getKey() == null || entry.getKey().trim().length() == 0 || entry.getValue() == null) {
                continue;
            }
            NBTTagCompound entryTag = new NBTTagCompound();
            entryTag.setString("Key", entry.getKey().trim());
            entryTag.setString("Value", entry.getValue());
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
            String key = entryTag.getString("Key");
            if (key != null && key.trim().length() > 0) {
                map.put(key.trim(), entryTag.getString("Value"));
            }
        }
        return map;
    }
}
