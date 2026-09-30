package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/**
 * The world's world quests: every run kept, running or ended, and the
 * rewards waiting for helpers who were not there as their quest succeeded,
 * one for each win, so a quest won twice before its helper collects pays
 * twice. A win pays each account once. A store saved by a newer build is
 * kept whole and read-only.
 */
public final class WorldQuestWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_world_quests";

    private final Map<String, WorldQuestRun> runs =
            new LinkedHashMap<String, WorldQuestRun>();
    private final Map<UUID, List<String>> rewards =
            new LinkedHashMap<UUID, List<String>>();
    private final List<NBTTagCompound> quarantined =
            new ArrayList<NBTTagCompound>();
    private NBTTagCompound preservedNewerData;

    public WorldQuestWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.runs.clear();
        this.rewards.clear();
        this.quarantined.clear();
        this.preservedNewerData = null;
        WorldQuestNbtCodec.ReadResult read = WorldQuestNbtCodec.read(compound);
        if (!read.isSupported()) {
            this.preservedNewerData = read.unsupported;
            LostTalesLog.warning("World quest data uses version %d, which this"
                    + " build cannot read; it is kept as it is and read-only",
                    Integer.valueOf(read.unsupportedVersion));
            return;
        }
        this.runs.putAll(read.runs);
        this.rewards.putAll(read.rewards);
        this.quarantined.addAll(read.quarantined);
        if (read.repaired) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.preservedNewerData != null) {
            NbtTags.copyContents(this.preservedNewerData, compound);
            return;
        }
        WorldQuestNbtCodec.write(compound, this.runs.values(), this.rewards,
                this.quarantined);
    }

    /** Whether nothing may change: the saved store is a newer build's. */
    public synchronized boolean isReadOnly() {
        return this.preservedNewerData != null;
    }

    /** A copy of every run kept, in the order they were started. */
    public synchronized List<WorldQuestRun> runs() {
        List<WorldQuestRun> copies = new ArrayList<WorldQuestRun>();
        for (WorldQuestRun run : this.runs.values()) {
            copies.add(run.copy());
        }
        return copies;
    }

    /** A copy of the quest's run, or null for a quest never run. */
    public synchronized WorldQuestRun run(String questId) {
        WorldQuestRun run = this.runs.get(questId);
        return run == null ? null : run.copy();
    }

    /**
     * The quests running at {@code now} and not yet past their days, by
     * id, without copying their runs: what every kill and craft looks at.
     */
    public synchronized List<String> runningQuestIds(long now) {
        List<String> running = new ArrayList<String>();
        for (WorldQuestRun run : this.runs.values()) {
            if (run.isRunning() && now < run.getEndsAt()) {
                running.add(run.getQuestId());
            }
        }
        return running;
    }

    /** How far the quest's run has come on the objective; 0 for a quest never run. */
    public synchronized int count(String questId, String objectiveId) {
        WorldQuestRun run = this.runs.get(questId);
        return run == null ? 0 : run.getCount(objectiveId);
    }

    public synchronized int runningCount() {
        int running = 0;
        for (WorldQuestRun run : this.runs.values()) {
            if (run.isRunning()) {
                running++;
            }
        }
        return running;
    }

    /**
     * Starts a run of the quest, in place of an ended one of the same
     * quest; the oldest ended run makes room when the store is full.
     * False while read-only, while the quest runs already, or with no room.
     */
    public synchronized boolean start(String questId, long now, long endsAt) {
        if (isReadOnly()) {
            return false;
        }
        WorldQuestRun kept = this.runs.get(questId);
        if (kept != null && kept.isRunning()) {
            return false;
        }
        this.runs.remove(questId);
        if (this.runs.size() >= WorldQuestNbtCodec.MAX_RUNS) {
            String oldestEnded = null;
            for (WorldQuestRun run : this.runs.values()) {
                if (!run.isRunning()) {
                    oldestEnded = run.getQuestId();
                    break;
                }
            }
            if (oldestEnded == null) {
                return false;
            }
            this.runs.remove(oldestEnded);
        }
        this.runs.put(questId, new WorldQuestRun(questId,
                WorldQuestRun.State.RUNNING, now, endsAt, 0L,
                Collections.<String, Integer>emptyMap(),
                Collections.<UUID, Integer>emptyMap()));
        markDirty();
        return true;
    }

    /** Adds to a running quest's objective; answers what was added. */
    public synchronized int add(String questId, String objectiveId,
                                int amount, int goal, UUID helper) {
        WorldQuestRun run = this.runs.get(questId);
        if (isReadOnly() || run == null || !run.isRunning()) {
            return 0;
        }
        int added = run.add(objectiveId, amount, goal, helper);
        if (added > 0) {
            markDirty();
        }
        return added;
    }

    /**
     * Ends a running quest that pays nobody: it failed or was stopped.
     * Answers whether it ended.
     */
    public synchronized boolean end(String questId, WorldQuestRun.State state,
                                    long now) {
        return state != WorldQuestRun.State.COMPLETED
                && end(questId, state, now, Integer.MAX_VALUE,
                        WorldQuestPayees.EACH_ITS_OWN);
    }

    /**
     * Ends a running quest. On success each account is paid once: of its
     * identities that added at least {@code least}, the one that added
     * most gets the quest's reward waiting for it ({@link WorldQuestPayees}).
     * Answers whether it ended.
     */
    public synchronized boolean end(String questId, WorldQuestRun.State state,
                                    long now, int least,
                                    WorldQuestPayees.Accounts accounts) {
        WorldQuestRun run = this.runs.get(questId);
        if (isReadOnly() || run == null || !run.isRunning()
                || state == WorldQuestRun.State.RUNNING) {
            return false;
        }
        run.end(state, now);
        if (state == WorldQuestRun.State.COMPLETED) {
            int lost = 0;
            for (UUID payee : WorldQuestPayees.of(run.getHelpers(), least,
                    accounts)) {
                if (!waitReward(payee, questId)) {
                    lost++;
                }
            }
            if (lost > 0) {
                LostTalesLog.warning("The world quest %s was won, but %d"
                        + " helpers already wait for as many rewards as the"
                        + " world keeps; they are not paid for it", questId,
                        Integer.valueOf(lost));
            }
        }
        markDirty();
        return true;
    }

    /** Leaves the quest's reward waiting for the identity; false when the store has no room for it. */
    private boolean waitReward(UUID identity, String questId) {
        List<String> waiting = this.rewards.get(identity);
        if (waiting == null) {
            if (this.rewards.size() >= WorldQuestNbtCodec.MAX_REWARDED) {
                return false;
            }
            waiting = new ArrayList<String>();
            this.rewards.put(identity, waiting);
        }
        if (waiting.size() >= WorldQuestNbtCodec.MAX_REWARDS_EACH) {
            return false;
        }
        waiting.add(questId);
        return true;
    }

    /** Whether any reward waits for the identity: a cheap question asked often. */
    public synchronized boolean hasRewards(UUID identity) {
        return identity != null && this.rewards.containsKey(identity);
    }

    /** Takes the rewards waiting for the identity, one quest id for each; empty for none. */
    public synchronized List<String> takeRewards(UUID identity) {
        if (isReadOnly() || identity == null) {
            return Collections.emptyList();
        }
        List<String> waiting = this.rewards.remove(identity);
        if (waiting == null) {
            return Collections.emptyList();
        }
        markDirty();
        return waiting;
    }
}
