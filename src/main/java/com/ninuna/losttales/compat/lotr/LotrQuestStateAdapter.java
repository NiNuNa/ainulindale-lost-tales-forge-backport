package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.quest.LOTRMiniQuest;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Public-API adapter for character-owned LOTR miniquest and quest records. */
public final class LotrQuestStateAdapter {

    private static final UUID DETACHED_PLAYER_ID =
            UUID.fromString("6f45e23c-8c2d-4b15-afd4-e1e8cb08737c");

    private static final String TAG_MINI_QUESTS = "MiniQuests";
    private static final String TAG_MINI_QUESTS_COMPLETED =
            "MiniQuestsCompleted";
    private static final String TAG_COMPLETE_COUNT = "MQCompleteCount";
    private static final String TAG_COMPLETED_BOUNTIES = "MQCompletedBounties";
    private static final String TAG_TRACKING = "MiniQuestTrack";
    private static final String TAG_BOUNTIES_PLACED = "BountiesPlaced";
    private static final String TAG_QUEST_DATA = "QuestData";
    private static final String TAG_POUCHES = "Pouches";

    private static final int MAX_QUESTS_PER_LIST = 4096;
    private static final int MAX_BOUNTY_FACTIONS = 512;
    private static final int MAX_IDENTIFIER_LENGTH = 128;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_NODES = 200000;
    private static final int MAX_LIST_ENTRIES = 32768;
    private static final int MAX_COMPOUND_ENTRIES = 4096;
    private static final int MAX_KEY_LENGTH = 1024;
    private static final int MAX_STRING_LENGTH = 32767;
    private static final long MAX_TOTAL_STRING_LENGTH = 2000000L;
    private static final int MAX_BYTE_ARRAY_LENGTH = 1048576;
    private static final int MAX_INT_ARRAY_LENGTH = 262144;

    private static final Set<String> CHARACTER_KEYS = setOf(
            TAG_MINI_QUESTS,
            TAG_MINI_QUESTS_COMPLETED,
            TAG_COMPLETE_COUNT,
            TAG_COMPLETED_BOUNTIES,
            TAG_TRACKING,
            TAG_BOUNTIES_PLACED,
            TAG_QUEST_DATA);

    private static final Set<String> REQUIRED_KEYS = setOf(
            TAG_MINI_QUESTS,
            TAG_MINI_QUESTS_COMPLETED,
            TAG_COMPLETE_COUNT,
            TAG_COMPLETED_BOUNTIES,
            TAG_BOUNTIES_PLACED,
            TAG_QUEST_DATA);

    public NBTTagCompound capture(EntityPlayerMP player) {
        return extractCharacterState(save(requireLiveData(player)));
    }

    public NBTTagCompound createDefault() {
        return extractCharacterState(save(new LOTRPlayerData(DETACHED_PLAYER_ID)));
    }

    /** Validates the v36.15 schema and LOTR's own public load/save round trip. */
    public void validate(NBTTagCompound quests) {
        validateShape(quests);
        validateExpandedTree(quests);
        try {
            NBTTagCompound full = save(new LOTRPlayerData(DETACHED_PLAYER_ID));
            overlayCharacterState(full, quests);
            LOTRPlayerData detached = new LOTRPlayerData(DETACHED_PLAYER_ID);
            detached.load(full);
            validateLoadedQuests(detached, quests);
            NBTTagCompound canonical = extractCharacterState(save(detached));
            if (!canonical.equals(quests)) {
                throw new IllegalArgumentException(
                        "LOTR quest state contains unknown or non-canonical values");
            }
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (LinkageError error) {
            throw incompatible("LOTR quest API is incompatible", error);
        } catch (RuntimeException exception) {
            throw incompatible("LOTR quest state could not be validated", exception);
        }
    }

    /** Replaces only quest-related fields and persists the live LOTR cache. */
    public void apply(EntityPlayerMP player, NBTTagCompound quests) {
        validate(quests);
        LOTRPlayerData data = requireLiveData(player);
        if (LotrCharacterAdapter.getInstance().isFastTravelActive(player)) {
            throw new IllegalStateException(
                    "LOTR quest state cannot be applied during fast travel");
        }
        try {
            NBTTagCompound full = save(data);
            overlayCharacterState(full, quests);
            data.load(full);
            data.markDirty();
            LOTRLevelData.saveData(player.getUniqueID());
        } catch (LinkageError error) {
            throw incompatible("Unable to apply LOTR quest state", error);
        } catch (RuntimeException exception) {
            throw incompatible("Unable to apply LOTR quest state", exception);
        }
    }

    private static LOTRPlayerData requireLiveData(EntityPlayerMP player) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote
                || player.getUniqueID() == null) {
            throw new IllegalStateException(
                    "LOTR quest state requires a connected server player");
        }
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data == null) {
            throw new IllegalStateException("LOTR player data is unavailable");
        }
        return data;
    }

    private static NBTTagCompound save(LOTRPlayerData data) {
        NBTTagCompound full = new NBTTagCompound();
        data.save(full);
        return full;
    }

    private static NBTTagCompound extractCharacterState(NBTTagCompound full) {
        NBTTagCompound quests = new NBTTagCompound();
        for (String key : CHARACTER_KEYS) {
            if (full.hasKey(key)) {
                quests.setTag(key, full.getTag(key).copy());
            }
        }
        return quests;
    }

    private static void overlayCharacterState(NBTTagCompound full,
                                              NBTTagCompound quests) {
        for (String key : CHARACTER_KEYS) {
            full.removeTag(key);
        }
        for (Object keyObject : quests.func_150296_c()) {
            String key = (String) keyObject;
            full.setTag(key, quests.getTag(key).copy());
        }
    }

    private static void validateShape(NBTTagCompound quests) {
        if (quests == null) {
            throw new IllegalArgumentException("LOTR quest state is missing");
        }
        Set<?> keys = quests.func_150296_c();
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)
                    || !CHARACTER_KEYS.contains((String) keyObject)) {
                throw new IllegalArgumentException(
                        "LOTR quest state contains an unsupported field");
            }
        }
        for (String required : REQUIRED_KEYS) {
            if (!keys.contains(required)) {
                throw new IllegalArgumentException(
                        "LOTR quest state is missing " + required);
            }
        }

        requireCompoundList(quests, TAG_MINI_QUESTS, MAX_QUESTS_PER_LIST);
        requireCompoundList(quests, TAG_MINI_QUESTS_COMPLETED,
                MAX_QUESTS_PER_LIST);
        requireNonNegativeInteger(quests, TAG_COMPLETE_COUNT);
        requireNonNegativeInteger(quests, TAG_COMPLETED_BOUNTIES);
        requireStringList(quests, TAG_BOUNTIES_PLACED,
                MAX_BOUNTY_FACTIONS, true);

        if (quests.hasKey(TAG_TRACKING)) {
            if (!quests.hasKey(TAG_TRACKING, Constants.NBT.TAG_STRING)) {
                throw new IllegalArgumentException(
                        "LOTR tracked miniquest identifier must be a string");
            }
            parseUuid(quests.getString(TAG_TRACKING), TAG_TRACKING);
        }
        if (!quests.hasKey(TAG_QUEST_DATA, Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalArgumentException("LOTR QuestData must be a compound");
        }
        NBTTagCompound questData = quests.getCompoundTag(TAG_QUEST_DATA);
        Set<?> questDataKeys = questData.func_150296_c();
        if (questDataKeys.size() != 1 || !questDataKeys.contains(TAG_POUCHES)
                || !questData.hasKey(TAG_POUCHES, Constants.NBT.TAG_BYTE)) {
            throw new IllegalArgumentException(
                    "LOTR QuestData contains unsupported fields");
        }
        byte pouches = questData.getByte(TAG_POUCHES);
        if (pouches != 0 && pouches != 1) {
            throw new IllegalArgumentException(
                    "LOTR QuestData Pouches must be a canonical boolean");
        }
    }

    private static void validateLoadedQuests(LOTRPlayerData data,
                                             NBTTagCompound input) {
        List<LOTRMiniQuest> active = data.getMiniQuests();
        List<LOTRMiniQuest> completed = data.getMiniQuestsCompleted();
        if (active == null || completed == null
                || active.size() != input.getTagList(
                        TAG_MINI_QUESTS, Constants.NBT.TAG_COMPOUND).tagCount()
                || completed.size() != input.getTagList(
                        TAG_MINI_QUESTS_COMPLETED,
                        Constants.NBT.TAG_COMPOUND).tagCount()) {
            throw new IllegalArgumentException(
                    "LOTR quest state contains an invalid miniquest");
        }
        HashSet<UUID> questIds = new HashSet<UUID>();
        validateQuestIds(active, questIds);
        validateQuestIds(completed, questIds);
        if (input.hasKey(TAG_TRACKING)
                && data.getTrackingMiniQuest() == null) {
            throw new IllegalArgumentException(
                    "Tracked LOTR miniquest is not active");
        }
    }

    private static void validateQuestIds(List<LOTRMiniQuest> quests,
                                         Set<UUID> questIds) {
        for (LOTRMiniQuest quest : quests) {
            if (quest == null || quest.questUUID == null
                    || !questIds.add(quest.questUUID)) {
                throw new IllegalArgumentException(
                        "LOTR miniquests contain a missing or duplicate identifier");
            }
        }
    }

    private static void requireCompoundList(NBTTagCompound root, String key,
                                            int maximum) {
        if (!root.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(key + " must be an NBT list");
        }
        NBTTagList list = (NBTTagList) root.getTag(key);
        if (list.tagCount() > maximum || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalArgumentException(key + " is oversized or malformed");
        }
    }

    private static void requireStringList(NBTTagCompound root, String key,
                                          int maximum, boolean unique) {
        if (!root.hasKey(key, Constants.NBT.TAG_LIST)) {
            throw new IllegalArgumentException(key + " must be an NBT list");
        }
        NBTTagList list = (NBTTagList) root.getTag(key);
        if (list.tagCount() > maximum || list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_STRING) {
            throw new IllegalArgumentException(key + " is oversized or malformed");
        }
        HashSet<String> values = new HashSet<String>();
        for (int index = 0; index < list.tagCount(); index++) {
            String value = list.getStringTagAt(index);
            if (value.length() == 0 || value.length() > MAX_IDENTIFIER_LENGTH
                    || unique && !values.add(value)) {
                throw new IllegalArgumentException(
                        key + " contains an invalid or duplicate identifier");
            }
        }
    }

    private static void requireNonNegativeInteger(NBTTagCompound root,
                                                  String key) {
        if (!root.hasKey(key, Constants.NBT.TAG_INT)
                || root.getInteger(key) < 0) {
            throw new IllegalArgumentException(
                    key + " must be a non-negative integer");
        }
    }

    private static UUID parseUuid(String value, String description) {
        if (value == null || value.length() > 64) {
            throw new IllegalArgumentException(
                    description + " has an invalid UUID");
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(
                    description + " has an invalid UUID", exception);
        }
    }

    private static void validateExpandedTree(NBTTagCompound root) {
        validateTag(root, 0, new ValidationBudget());
    }

    private static void validateTag(NBTBase tag, int depth,
                                    ValidationBudget budget) {
        if (tag == null || depth > MAX_DEPTH || ++budget.nodes > MAX_NODES) {
            throw new IllegalArgumentException(
                    "LOTR quest state exceeds the expanded structure limit");
        }
        switch (tag.getId()) {
            case Constants.NBT.TAG_END:
                throw new IllegalArgumentException(
                        "LOTR quest state contains an end tag");
            case Constants.NBT.TAG_BYTE:
            case Constants.NBT.TAG_SHORT:
            case Constants.NBT.TAG_INT:
            case Constants.NBT.TAG_LONG:
                return;
            case Constants.NBT.TAG_FLOAT:
                requireFinite(((NBTTagFloat) tag).func_150288_h());
                return;
            case Constants.NBT.TAG_DOUBLE:
                requireFinite(((NBTTagDouble) tag).func_150286_g());
                return;
            case Constants.NBT.TAG_BYTE_ARRAY:
                if (((NBTTagByteArray) tag).func_150292_c().length
                        > MAX_BYTE_ARRAY_LENGTH) {
                    throw new IllegalArgumentException(
                            "LOTR quest state contains an oversized byte array");
                }
                return;
            case Constants.NBT.TAG_STRING:
                addString(((NBTTagString) tag).func_150285_a_(), budget);
                return;
            case Constants.NBT.TAG_LIST:
                validateList((NBTTagList) tag, depth, budget);
                return;
            case Constants.NBT.TAG_COMPOUND:
                validateCompound((NBTTagCompound) tag, depth, budget);
                return;
            case Constants.NBT.TAG_INT_ARRAY:
                if (((NBTTagIntArray) tag).func_150302_c().length
                        > MAX_INT_ARRAY_LENGTH) {
                    throw new IllegalArgumentException(
                            "LOTR quest state contains an oversized int array");
                }
                return;
            default:
                throw new IllegalArgumentException(
                        "LOTR quest state contains an unsupported NBT type");
        }
    }

    private static void validateList(NBTTagList list, int depth,
                                     ValidationBudget budget) {
        int count = list.tagCount();
        if (count > MAX_LIST_ENTRIES) {
            throw new IllegalArgumentException(
                    "LOTR quest state contains an oversized list");
        }
        int type = list.func_150303_d();
        if (type == Constants.NBT.TAG_LIST
                || type == Constants.NBT.TAG_BYTE_ARRAY) {
            throw new IllegalArgumentException(
                    "LOTR quest state contains an unsupported nested list type");
        }
        for (int index = 0; index < count; index++) {
            budget.nodes++;
            if (budget.nodes > MAX_NODES) {
                throw new IllegalArgumentException(
                        "LOTR quest state exceeds the expanded structure limit");
            }
            if (type == Constants.NBT.TAG_COMPOUND) {
                validateCompound(list.getCompoundTagAt(index), depth + 1, budget);
            } else if (type == Constants.NBT.TAG_STRING) {
                addString(list.getStringTagAt(index), budget);
            } else if (type == Constants.NBT.TAG_FLOAT) {
                requireFinite(list.func_150308_e(index));
            } else if (type == Constants.NBT.TAG_DOUBLE) {
                requireFinite(list.func_150309_d(index));
            } else if (type == Constants.NBT.TAG_INT_ARRAY
                    && list.func_150306_c(index).length > MAX_INT_ARRAY_LENGTH) {
                throw new IllegalArgumentException(
                        "LOTR quest state contains an oversized int array");
            }
        }
    }

    private static void validateCompound(NBTTagCompound compound, int depth,
                                         ValidationBudget budget) {
        Set<?> keys = compound.func_150296_c();
        if (keys.size() > MAX_COMPOUND_ENTRIES) {
            throw new IllegalArgumentException(
                    "LOTR quest state contains an oversized compound");
        }
        for (Object keyObject : keys) {
            if (!(keyObject instanceof String)) {
                throw new IllegalArgumentException(
                        "LOTR quest state contains an invalid key");
            }
            String key = (String) keyObject;
            if (key.length() == 0 || key.length() > MAX_KEY_LENGTH) {
                throw new IllegalArgumentException(
                        "LOTR quest state contains an invalid key length");
            }
            validateTag(compound.getTag(key), depth + 1, budget);
        }
    }

    private static void addString(String value, ValidationBudget budget) {
        if (value == null || value.length() > MAX_STRING_LENGTH) {
            throw new IllegalArgumentException(
                    "LOTR quest state contains an oversized string");
        }
        budget.stringCharacters += value.length();
        if (budget.stringCharacters > MAX_TOTAL_STRING_LENGTH) {
            throw new IllegalArgumentException(
                    "LOTR quest state exceeds the expanded string limit");
        }
    }

    private static void requireFinite(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException(
                    "LOTR quest state contains a non-finite number");
        }
    }

    private static IllegalStateException incompatible(String message,
                                                       Throwable cause) {
        return new IllegalStateException(message, cause);
    }

    private static Set<String> setOf(String... values) {
        LinkedHashSet<String> set = new LinkedHashSet<String>();
        if (values != null) {
            for (String value : values) {
                set.add(value);
            }
        }
        return set;
    }

    private static final class ValidationBudget {
        private int nodes;
        private long stringCharacters;
    }
}
