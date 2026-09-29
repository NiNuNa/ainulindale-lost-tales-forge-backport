package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.common.util.Constants;

/**
 * The world quests' saved form: every run kept, and the rewards waiting for
 * helpers who were not there as their quest succeeded. It fails closed: a
 * store of a newer version is kept whole and read-only, and a run or a
 * reward that cannot be read is set aside in the quarantine, never dropped.
 */
public final class WorldQuestNbtCodec {
    public static final int CURRENT_DATA_VERSION = 1;
    /** The most runs kept, running and ended. */
    public static final int MAX_RUNS = 16;
    /** The most objectives one run counts. */
    public static final int MAX_COUNTS = 64;
    /** The most identities with rewards waiting. */
    public static final int MAX_REWARDED = 65536;
    /** The most rewards waiting for one identity. */
    public static final int MAX_REWARDS_EACH = MAX_RUNS;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_RUNS = "Runs";
    private static final String TAG_REWARDS = "Rewards";
    private static final String TAG_QUEST_ID = "QuestId";
    private static final String TAG_STATE = "State";
    private static final String TAG_STARTED_AT = "StartedAt";
    private static final String TAG_ENDS_AT = "EndsAt";
    private static final String TAG_ENDED_AT = "EndedAt";
    private static final String TAG_COUNTS = "Counts";
    private static final String TAG_OBJECTIVE = "Objective";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_HELPERS = "Helpers";
    private static final String TAG_IDENTITY = "Identity";
    private static final String TAG_HELPED = "Helped";
    private static final String TAG_QUEST_IDS = "QuestIds";
    private static final String TAG_RUN_INDEX = "RunIndex";
    private static final String TAG_REWARD_INDEX = "RewardIndex";
    private static final int MAX_ID_LENGTH = 256;

    private WorldQuestNbtCodec() {}

    public static void write(NBTTagCompound output,
                             Collection<WorldQuestRun> runs,
                             Map<UUID, Set<String>> rewards,
                             Collection<NBTTagCompound> quarantined) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_DATA_VERSION);
        NBTTagList runList = new NBTTagList();
        for (WorldQuestRun run : runs) {
            runList.appendTag(writeRun(run));
        }
        output.setTag(TAG_RUNS, runList);
        NBTTagList rewardList = new NBTTagList();
        for (Map.Entry<UUID, Set<String>> entry : rewards.entrySet()) {
            NBTTagCompound reward = new NBTTagCompound();
            NbtTags.writeUuid(reward, TAG_IDENTITY, entry.getKey());
            NBTTagList ids = new NBTTagList();
            for (String questId : entry.getValue()) {
                ids.appendTag(new NBTTagString(questId));
            }
            reward.setTag(TAG_QUEST_IDS, ids);
            rewardList.appendTag(reward);
        }
        output.setTag(TAG_REWARDS, rewardList);
        NbtQuarantine.write(output, quarantined);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safe = source == null ? new NBTTagCompound() : source;
        int version = safe.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safe.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_DATA_VERSION || version < 0
                || safe.hasKey(TAG_RUNS)
                && !safe.hasKey(TAG_RUNS, Constants.NBT.TAG_LIST)
                || safe.hasKey(TAG_REWARDS)
                && !safe.hasKey(TAG_REWARDS, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safe, version);
        }
        NbtQuarantine.Read quarantine = NbtQuarantine.read(safe);
        if (!quarantine.isSupported()) {
            return ReadResult.unsupported(safe,
                    quarantine.getUnsupportedVersion());
        }
        boolean repaired = version != CURRENT_DATA_VERSION
                || quarantine.isRepaired();
        List<NBTTagCompound> quarantined =
                new ArrayList<NBTTagCompound>(quarantine.getEntries());

        LinkedHashMap<String, WorldQuestRun> runs =
                new LinkedHashMap<String, WorldQuestRun>();
        NBTTagList runList = safe.getTagList(TAG_RUNS,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < runList.tagCount(); index++) {
            NBTTagCompound raw = runList.getCompoundTagAt(index);
            String problem = runProblem(raw);
            WorldQuestRun run = problem.length() == 0 ? readRun(raw) : null;
            if (run == null || runs.size() >= MAX_RUNS
                    || runs.containsKey(run.getQuestId())) {
                quarantined.add(NbtQuarantine.entry(problem.length() > 0
                        ? problem : run == null ? "unreadable_run"
                        : runs.size() >= MAX_RUNS ? "too_many_runs"
                        : "duplicate_run", TAG_RUN_INDEX, index, raw));
                repaired = true;
                continue;
            }
            runs.put(run.getQuestId(), run);
        }

        LinkedHashMap<UUID, Set<String>> rewards =
                new LinkedHashMap<UUID, Set<String>>();
        NBTTagList rewardList = safe.getTagList(TAG_REWARDS,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < rewardList.tagCount(); index++) {
            NBTTagCompound raw = rewardList.getCompoundTagAt(index);
            UUID identity = NbtTags.readUuid(raw, TAG_IDENTITY);
            Set<String> ids = readRewardIds(raw);
            if (identity == null || ids == null || ids.isEmpty()
                    || rewards.containsKey(identity)
                    || rewards.size() >= MAX_REWARDED) {
                quarantined.add(NbtQuarantine.entry("unreadable_reward",
                        TAG_REWARD_INDEX, index, raw));
                repaired = true;
                continue;
            }
            rewards.put(identity, ids);
        }
        return ReadResult.success(runs, rewards, quarantined, repaired);
    }

    private static NBTTagCompound writeRun(WorldQuestRun run) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString(TAG_QUEST_ID, run.getQuestId());
        tag.setString(TAG_STATE, run.getState().name());
        tag.setLong(TAG_STARTED_AT, run.getStartedAt());
        tag.setLong(TAG_ENDS_AT, run.getEndsAt());
        tag.setLong(TAG_ENDED_AT, run.getEndedAt());
        NBTTagList counts = new NBTTagList();
        for (Map.Entry<String, Integer> entry : run.getCounts().entrySet()) {
            NBTTagCompound count = new NBTTagCompound();
            count.setString(TAG_OBJECTIVE, entry.getKey());
            count.setInteger(TAG_COUNT, entry.getValue().intValue());
            counts.appendTag(count);
        }
        tag.setTag(TAG_COUNTS, counts);
        NBTTagList helpers = new NBTTagList();
        for (Map.Entry<UUID, Integer> entry : run.getHelpers().entrySet()) {
            NBTTagCompound helper = new NBTTagCompound();
            NbtTags.writeUuid(helper, TAG_IDENTITY, entry.getKey());
            helper.setInteger(TAG_HELPED, entry.getValue().intValue());
            helpers.appendTag(helper);
        }
        tag.setTag(TAG_HELPERS, helpers);
        return tag;
    }

    /** Why a saved run cannot be read, as its quarantine reason; empty for one that can. */
    private static String runProblem(NBTTagCompound raw) {
        if (!NbtTags.hasReasonableString(raw, TAG_QUEST_ID, MAX_ID_LENGTH,
                true)) {
            return "missing_quest_id";
        }
        if (WorldQuestRun.State.of(raw.getString(TAG_STATE)) == null) {
            return "unknown_state";
        }
        if (!raw.hasKey(TAG_STARTED_AT, Constants.NBT.TAG_LONG)
                || !raw.hasKey(TAG_ENDS_AT, Constants.NBT.TAG_LONG)
                || !raw.hasKey(TAG_ENDED_AT, Constants.NBT.TAG_LONG)) {
            return "missing_times";
        }
        if (!NbtTags.hasCompoundListWithinLimit(raw, TAG_COUNTS, MAX_COUNTS)
                || !NbtTags.hasCompoundListWithinLimit(raw, TAG_HELPERS,
                        WorldQuestRules.MAX_HELPERS)) {
            return "counts_out_of_bounds";
        }
        return "";
    }

    private static WorldQuestRun readRun(NBTTagCompound raw) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        NBTTagList countList = raw.getTagList(TAG_COUNTS,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < countList.tagCount(); index++) {
            NBTTagCompound count = countList.getCompoundTagAt(index);
            String objective = count.getString(TAG_OBJECTIVE);
            int value = count.getInteger(TAG_COUNT);
            if (objective.length() == 0 || objective.length() > MAX_ID_LENGTH
                    || value < 0 || counts.containsKey(objective)) {
                return null;
            }
            counts.put(objective, Integer.valueOf(value));
        }
        Map<UUID, Integer> helpers = new LinkedHashMap<UUID, Integer>();
        NBTTagList helperList = raw.getTagList(TAG_HELPERS,
                Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < helperList.tagCount(); index++) {
            NBTTagCompound helper = helperList.getCompoundTagAt(index);
            UUID identity = NbtTags.readUuid(helper, TAG_IDENTITY);
            int helped = helper.getInteger(TAG_HELPED);
            if (identity == null || helped < 0
                    || helpers.containsKey(identity)) {
                return null;
            }
            helpers.put(identity, Integer.valueOf(helped));
        }
        return new WorldQuestRun(raw.getString(TAG_QUEST_ID),
                WorldQuestRun.State.of(raw.getString(TAG_STATE)),
                raw.getLong(TAG_STARTED_AT), raw.getLong(TAG_ENDS_AT),
                raw.getLong(TAG_ENDED_AT), counts, helpers);
    }

    private static Set<String> readRewardIds(NBTTagCompound raw) {
        if (!raw.hasKey(TAG_QUEST_IDS, Constants.NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList ids = raw.getTagList(TAG_QUEST_IDS,
                Constants.NBT.TAG_STRING);
        if (ids.tagCount() > MAX_REWARDS_EACH) {
            return null;
        }
        Set<String> result = new LinkedHashSet<String>();
        for (int index = 0; index < ids.tagCount(); index++) {
            String id = ids.getStringTagAt(index);
            if (id.length() == 0 || id.length() > MAX_ID_LENGTH) {
                return null;
            }
            result.add(id);
        }
        return result;
    }

    /** What a read found. */
    public static final class ReadResult {
        public final Map<String, WorldQuestRun> runs;
        public final Map<UUID, Set<String>> rewards;
        public final List<NBTTagCompound> quarantined;
        public final boolean repaired;
        /** The whole store as read, kept for a store this build cannot read; null otherwise. */
        public final NBTTagCompound unsupported;
        public final int unsupportedVersion;

        private ReadResult(Map<String, WorldQuestRun> runs,
                           Map<UUID, Set<String>> rewards,
                           List<NBTTagCompound> quarantined, boolean repaired,
                           NBTTagCompound unsupported,
                           int unsupportedVersion) {
            this.runs = runs;
            this.rewards = rewards;
            this.quarantined = quarantined;
            this.repaired = repaired;
            this.unsupported = unsupported;
            this.unsupportedVersion = unsupportedVersion;
        }

        static ReadResult success(Map<String, WorldQuestRun> runs,
                                  Map<UUID, Set<String>> rewards,
                                  List<NBTTagCompound> quarantined,
                                  boolean repaired) {
            return new ReadResult(runs, rewards, quarantined, repaired, null,
                    0);
        }

        static ReadResult unsupported(NBTTagCompound source, int version) {
            return new ReadResult(Collections.<String, WorldQuestRun>emptyMap(),
                    Collections.<UUID, Set<String>>emptyMap(),
                    Collections.<NBTTagCompound>emptyList(), false,
                    (NBTTagCompound)source.copy(), version);
        }

        public boolean isSupported() {
            return this.unsupported == null;
        }
    }
}
