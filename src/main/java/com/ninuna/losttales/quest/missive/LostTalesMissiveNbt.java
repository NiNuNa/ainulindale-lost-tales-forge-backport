package com.ninuna.losttales.quest.missive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/** NBT serialization helpers for generated missives and future missive letter item stacks. */
public final class LostTalesMissiveNbt {
    public static final String TAG_MISSIVE = "LostTalesMissive";
    public static final int MAX_OBJECTIVES = 512;
    public static final int MAX_MAP_ENTRIES = 256;
    public static final int MAX_IDENTIFIER_CHARACTERS = 256;
    public static final int MAX_NAME_CHARACTERS = 1024;
    public static final int MAX_TEXT_CHARACTERS = 8192;
    public static final int MAX_MAP_VALUE_CHARACTERS = 4096;

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

    public static boolean hasMissive(ItemStack stack) {
        return readFromItemStack(stack) != null;
    }

    public static NBTTagCompound writeToNBT(LostTalesMissiveData missive) {
        NBTTagCompound tag = new NBTTagCompound();
        if (missive == null) {
            return tag;
        }

        tag.setString("QuestId", missive.getQuestId());
        tag.setString("QuestType", missive.getQuestType());
        tag.setString("Title", missive.getTitle());
        tag.setString("Description", missive.getDescription());
        tag.setString("Issuer", missive.getIssuer());
        tag.setString("FlavorText", missive.getFlavorText());
        tag.setBoolean("Repeatable", missive.isRepeatable());
        tag.setBoolean("FirstComeFirstServed", missive.isFirstComeFirstServed());
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
                tag.getString("Title"),
                tag.getString("Description"),
                tag.getString("Issuer"),
                tag.getString("FlavorText"),
                !tag.hasKey("Repeatable") || tag.getBoolean("Repeatable"),
                !tag.hasKey("FirstComeFirstServed") || tag.getBoolean("FirstComeFirstServed"),
                tag.getLong("GenerationWorldTime"),
                tag.getLong("TimeLimitTicks"),
                readStringMap(tag.getTagList("GenerationContext", Constants.NBT.TAG_COMPOUND)),
                readObjectives(tag.getTagList("Objectives", Constants.NBT.TAG_COMPOUND)),
                new LostTalesMissiveRewardData(readStringMap(tag.getTagList("Rewards", Constants.NBT.TAG_COMPOUND)))
        );
        return missive.isValid() ? missive : null;
    }

    public static boolean isStructurallyReasonable(NBTTagCompound tag) {
        if (!hasReasonableString(tag, "QuestId",
                MAX_IDENTIFIER_CHARACTERS, true)
                || !hasReasonableString(tag, "QuestType",
                MAX_IDENTIFIER_CHARACTERS, true)
                || !hasReasonableString(tag, "Title",
                MAX_NAME_CHARACTERS, true)
                || !hasReasonableString(tag, "Description",
                MAX_TEXT_CHARACTERS, false)
                || !hasReasonableString(tag, "Issuer",
                MAX_NAME_CHARACTERS, false)
                || !hasReasonableString(tag, "FlavorText",
                MAX_TEXT_CHARACTERS, false)
                || tag.getLong("GenerationWorldTime") < 0L
                || tag.getLong("TimeLimitTicks") < 0L
                || !isStringMapReasonable(tag, "GenerationContext")
                || !isStringMapReasonable(tag, "Rewards")
                || !hasCompoundListWithinLimit(
                tag, "Objectives", MAX_OBJECTIVES)) {
            return false;
        }
        NBTTagList objectives = tag.getTagList(
                "Objectives", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < objectives.tagCount(); i++) {
            NBTTagCompound objective = objectives.getCompoundTagAt(i);
            if (!hasReasonableString(objective, "Id",
                    MAX_IDENTIFIER_CHARACTERS, true)
                    || !hasReasonableString(objective, "Type",
                    MAX_IDENTIFIER_CHARACTERS, true)
                    || !hasReasonableString(objective, "Description",
                    MAX_TEXT_CHARACTERS, false)
                    || !isStringMapReasonable(objective, "Params")) {
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
            objectiveTag.setString("Description", objective.getDescription());
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
                    objectiveTag.getString("Description"),
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

    private static boolean isStringMapReasonable(
            NBTTagCompound owner, String key) {
        if (!hasCompoundListWithinLimit(owner, key, MAX_MAP_ENTRIES)) {
            return false;
        }
        NBTTagList list = owner.getTagList(
                key, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound entry = list.getCompoundTagAt(i);
            if (!hasReasonableString(entry, "Key",
                    MAX_IDENTIFIER_CHARACTERS, true)
                    || !hasReasonableString(entry, "Value",
                    MAX_MAP_VALUE_CHARACTERS, false)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasCompoundListWithinLimit(
            NBTTagCompound owner, String key, int maximum) {
        if (owner == null) {
            return false;
        }
        if (!owner.hasKey(key)) {
            return true;
        }
        NBTBase raw = owner.getTag(key);
        if (!(raw instanceof NBTTagList)) {
            return false;
        }
        NBTTagList list = (NBTTagList) raw;
        return (list.tagCount() == 0
                || list.func_150303_d() == Constants.NBT.TAG_COMPOUND)
                && list.tagCount() <= maximum;
    }

    private static boolean hasReasonableString(
            NBTTagCompound owner, String key, int maximum,
            boolean required) {
        if (owner == null || !owner.hasKey(key)) {
            return !required;
        }
        if (!owner.hasKey(key, Constants.NBT.TAG_STRING)) {
            return false;
        }
        String value = owner.getString(key);
        return (!required || value.length() > 0)
                && value.length() <= maximum;
    }
}
