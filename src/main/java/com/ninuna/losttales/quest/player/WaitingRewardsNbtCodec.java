package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/**
 * The saved form of a character's {@link WaitingRewards}, kept in its
 * quest log under {@link LostTalesQuestPlayerData}: its own data version,
 * each quest's rewards, and the quarantine ({@link NbtQuarantine}).
 *
 * <p>Fails closed: a version this build does not write, a shape it never
 * writes, lists past their bounds or a quarantine it cannot read make the
 * whole log read-only, kept as it was. A quest's entry that cannot be
 * read (an id past its bound or said twice, a reward without its parts,
 * a count out of bounds, a kind said twice) is set aside whole in the
 * quarantine, never dropped. An item the game no longer has is no fault:
 * it waits until the game has it again.</p>
 */
public final class WaitingRewardsNbtCodec {
    static final int DATA_VERSION = 1;
    static final String TAG_VERSION = "DataVersion";
    static final String TAG_REWARDS = "Rewards";
    static final String TAG_QUEST_ID = "QuestId";
    static final String TAG_ITEMS = "Items";
    static final String TAG_ITEM = "Item";
    static final String TAG_STARTER = "Starter";
    static final String TAG_META = "Meta";
    static final String TAG_COUNT = "Count";
    static final String TAG_RELIC = "Relic";
    static final String TAG_INDEX = "RewardIndex";

    private WaitingRewardsNbtCodec() {}

    /** The saved form of the rewards and the quarantine beside them. */
    public static NBTTagCompound write(WaitingRewards rewards,
                                       List<NBTTagCompound> quarantined) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_VERSION, DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (rewards != null) {
            for (Map.Entry<String, List<WaitingReward>> entry : rewards.all().entrySet()) {
                NBTTagCompound quest = new NBTTagCompound();
                quest.setString(TAG_QUEST_ID, entry.getKey());
                NBTTagList items = new NBTTagList();
                for (WaitingReward reward : entry.getValue()) {
                    NBTTagCompound item = new NBTTagCompound();
                    item.setString(reward.isStarter() ? TAG_STARTER : TAG_ITEM,
                            reward.getId());
                    item.setInteger(TAG_META, reward.getMeta());
                    item.setInteger(TAG_COUNT, reward.getCount());
                    if (reward.getRelic().length() > 0) {
                        item.setString(TAG_RELIC, reward.getRelic());
                    }
                    items.appendTag(item);
                }
                quest.setTag(TAG_ITEMS, items);
                list.appendTag(quest);
            }
        }
        root.setTag(TAG_REWARDS, list);
        NbtQuarantine.write(root, quarantined);
        return root;
    }

    /** What a saved form read as. */
    public static final class ReadResult {
        private final boolean readable;
        private final WaitingRewards rewards;
        private final List<NBTTagCompound> quarantined;

        private ReadResult(boolean readable, WaitingRewards rewards,
                           List<NBTTagCompound> quarantined) {
            this.readable = readable;
            this.rewards = rewards;
            this.quarantined = Collections.unmodifiableList(quarantined);
        }

        /** Whether this build can read it; if not, the log is kept read-only. */
        public boolean isReadable() {
            return this.readable;
        }

        public WaitingRewards getRewards() {
            return this.rewards;
        }

        /** The entries set aside, those read now and those set aside before. */
        public List<NBTTagCompound> getQuarantined() {
            return this.quarantined;
        }
    }

    /** Reads a saved form; null reads as nothing waiting. */
    public static ReadResult read(NBTTagCompound root) {
        WaitingRewards rewards = new WaitingRewards();
        List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
        if (root == null) {
            return new ReadResult(true, rewards, quarantined);
        }
        if (!root.hasKey(TAG_VERSION, Constants.NBT.TAG_INT)
                || root.getInteger(TAG_VERSION) != DATA_VERSION
                || !root.hasKey(TAG_REWARDS, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(root, TAG_REWARDS,
                        WaitingRewards.MAX_QUESTS)) {
            return unreadable();
        }
        NbtQuarantine.Read aside = NbtQuarantine.read(root);
        if (!aside.isSupported()
                || aside.getEntries().size() > NbtQuarantine.MAX_ENTRIES) {
            return unreadable();
        }
        quarantined.addAll(aside.getEntries());
        Set<String> seen = new HashSet<String>();
        NBTTagList list = root.getTagList(TAG_REWARDS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound quest = list.getCompoundTagAt(index);
            List<WaitingReward> read = readQuest(quest);
            String questId = quest.getString(TAG_QUEST_ID);
            if (read == null || !seen.add(questId)) {
                if (quarantined.size() >= NbtQuarantine.MAX_ENTRIES) {
                    return unreadable();
                }
                quarantined.add(NbtQuarantine.entry("unreadable_waiting_reward",
                        TAG_INDEX, index, quest));
                continue;
            }
            for (WaitingReward reward : read) {
                rewards.add(questId, reward);
            }
        }
        return new ReadResult(true, rewards, quarantined);
    }

    /** One quest's rewards as written; null where any part cannot be read. */
    private static List<WaitingReward> readQuest(NBTTagCompound quest) {
        if (!quest.hasKey(TAG_QUEST_ID, Constants.NBT.TAG_STRING)
                || !LostTalesQuestIds.fits(quest.getString(TAG_QUEST_ID))
                || !quest.hasKey(TAG_ITEMS, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(quest, TAG_ITEMS,
                        WaitingRewards.MAX_KINDS_EACH)) {
            return null;
        }
        NBTTagList items = quest.getTagList(TAG_ITEMS, Constants.NBT.TAG_COMPOUND);
        if (items.tagCount() == 0) {
            return null;
        }
        List<WaitingReward> read = new ArrayList<WaitingReward>();
        for (int index = 0; index < items.tagCount(); index++) {
            WaitingReward reward = readReward(items.getCompoundTagAt(index));
            if (reward == null) {
                return null;
            }
            for (WaitingReward before : read) {
                if (before.isSameKind(reward)) {
                    return null;
                }
            }
            read.add(reward);
        }
        return read;
    }

    /** One reward as written; null where it names neither or both, or a part is out of bounds. */
    private static WaitingReward readReward(NBTTagCompound item) {
        boolean isItem = item.hasKey(TAG_ITEM, Constants.NBT.TAG_STRING);
        boolean isStarter = item.hasKey(TAG_STARTER, Constants.NBT.TAG_STRING);
        boolean isRelic = item.hasKey(TAG_RELIC);
        if (isItem == isStarter
                || !item.hasKey(TAG_META, Constants.NBT.TAG_INT)
                || !item.hasKey(TAG_COUNT, Constants.NBT.TAG_INT)
                || isRelic && (isStarter || !item.hasKey(TAG_RELIC, Constants.NBT.TAG_STRING)
                || item.getInteger(TAG_META) != 0)) {
            return null;
        }
        WaitingReward reward = isStarter
                ? WaitingReward.starter(item.getString(TAG_STARTER),
                        item.getInteger(TAG_COUNT))
                : isRelic ? WaitingReward.relic(item.getString(TAG_ITEM),
                        item.getString(TAG_RELIC), item.getInteger(TAG_COUNT))
                : WaitingReward.item(item.getString(TAG_ITEM),
                        item.getInteger(TAG_META), item.getInteger(TAG_COUNT));
        if (isStarter && item.getInteger(TAG_META) != 0) {
            return null;
        }
        return reward.isValid() ? reward : null;
    }

    private static ReadResult unreadable() {
        return new ReadResult(false, new WaitingRewards(),
                new ArrayList<NBTTagCompound>());
    }
}
