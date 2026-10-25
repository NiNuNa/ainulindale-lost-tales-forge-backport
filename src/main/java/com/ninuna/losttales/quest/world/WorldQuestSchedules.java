package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.WorldSavedData;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraftforge.common.util.Constants;

/**
 * Starts the world quests {@code worldQuestSchedules} names, each at its
 * time on the server's real clock (QUEST-36): looked at once a minute and
 * as the server starts, so a time missed while it was down is caught up
 * the same day. A quest already running is not started again. When each
 * schedule last started its quest is kept in the world, so a restart
 * never starts it twice for one time.
 */
public final class WorldQuestSchedules {
    private WorldQuestSchedules() {}

    /** The schedules the server's settings write, each line that is not one logged once per reading. */
    static List<WorldQuestSchedule> configured() {
        List<WorldQuestSchedule> schedules = new ArrayList<WorldQuestSchedule>();
        String[] lines = LostTalesConfig.worldQuestSchedules;
        if (lines == null) {
            return schedules;
        }
        for (String line : lines) {
            if (line == null || line.trim().length() == 0) {
                continue;
            }
            WorldQuestSchedule schedule = WorldQuestSchedule.parse(line);
            if (schedule != null) {
                schedules.add(schedule);
            }
        }
        return schedules;
    }

    /** Logs each line of the setting that is no schedule, and why; as the settings are read. */
    public static void logProblems() {
        String[] lines = LostTalesConfig.worldQuestSchedules;
        if (lines == null) {
            return;
        }
        for (String line : lines) {
            String problem = line == null || line.trim().length() == 0 ? ""
                    : WorldQuestSchedule.problem(line);
            if (problem.length() > 0) {
                LostTalesLog.warning("The world quest schedule '%s' is left out: %s",
                        line.trim(), problem);
            }
        }
    }

    /** Starts every scheduled quest whose time has come and was not kept yet. */
    static void check(MinecraftServer server, long nowMillis) {
        List<WorldQuestSchedule> schedules = configured();
        if (schedules.isEmpty()) {
            return;
        }
        Store store = store(server);
        if (store == null) {
            return;
        }
        for (WorldQuestSchedule schedule : due(schedules, store, nowMillis,
                TimeZone.getDefault())) {
            WorldQuests.StartResult result = WorldQuests.start(server,
                    schedule.getQuestId());
            if (result != WorldQuests.StartResult.STARTED
                    && result != WorldQuests.StartResult.ALREADY_RUNNING) {
                LostTalesLog.warning("The world quest schedule '%s' could not start"
                        + " its quest: %s", schedule.getLine(), result);
            }
        }
    }

    /**
     * The schedules whose quest starts now: each whose time has come
     * today and was not kept yet, kept in the record before its quest
     * starts. None while the record cannot be read, and none whose line
     * the record has no room for, since its quest would start again after
     * a restart.
     */
    static List<WorldQuestSchedule> due(List<WorldQuestSchedule> schedules,
                                        Store store, long nowMillis,
                                        TimeZone zone) {
        List<WorldQuestSchedule> due = new ArrayList<WorldQuestSchedule>();
        if (!store.isUsable()) {
            return due;
        }
        for (WorldQuestSchedule schedule : schedules) {
            long at = schedule.latestDue(nowMillis, zone);
            if (!WorldQuestSchedule.isToday(at, nowMillis, zone)
                    || at <= store.lastStart(schedule.getLine())) {
                continue;
            }
            if (store.started(schedule.getLine(), at)) {
                due.add(schedule);
            }
        }
        return due;
    }

    private static Store store(MinecraftServer server) {
        WorldServer overworld = server == null ? null : server.worldServerForDimension(0);
        MapStorage storage = overworld == null ? null : overworld.mapStorage;
        if (storage == null) {
            return null;
        }
        Store store = (Store) storage.loadData(Store.class, Store.DATA_NAME);
        if (store == null) {
            store = new Store(Store.DATA_NAME);
            storage.setData(Store.DATA_NAME, store);
        }
        return store;
    }

    /**
     * When each schedule last started its quest, by its line, in real
     * milliseconds. An entry that cannot be read, or the earlier of two
     * for one line, is set aside in the quarantine, never dropped
     * (DATA-10). Data of another version, a quarantine this build cannot
     * read, or one that would hold more than {@link NbtQuarantine#MAX_ENTRIES}
     * is kept as it was and no schedule starts anything, rather than one
     * starting twice. A line is never forgotten: once the record holds
     * {@link #MAX_ENTRIES} lines, a line it does not hold yet starts nothing.
     */
    public static final class Store extends WorldSavedData {
        public static final String DATA_NAME = "losttales_world_quest_schedule";
        static final int DATA_VERSION = 2;
        /** The most schedule lines remembered; as many as a settings list holds sensibly. */
        static final int MAX_ENTRIES = 256;
        /** The longest line remembered. */
        static final int MAX_LINE_LENGTH = 512;
        static final String TAG_VERSION = "DataVersion";
        static final String TAG_STARTS = "Starts";
        static final String TAG_LINE = "Line";
        static final String TAG_AT = "At";
        /** Where an entry set aside stood in the list of starts. */
        static final String TAG_START_INDEX = "StartIndex";

        private final Map<String, Long> starts = new LinkedHashMap<String, Long>();
        private final List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
        /** The lines refused since the record was read, so each is logged once. */
        private final Set<String> refused = new HashSet<String>();
        private NBTTagCompound preserved;
        /** Why the record is kept as it was, for the log. */
        private String preservedBecause;
        private boolean warned;

        public Store(String name) {
            super(name);
        }

        @Override
        public synchronized void readFromNBT(NBTTagCompound compound) {
            this.starts.clear();
            this.quarantined.clear();
            this.refused.clear();
            this.preserved = null;
            this.preservedBecause = null;
            this.warned = false;
            if (compound.getInteger(TAG_VERSION) != DATA_VERSION) {
                preserve(compound, "it is of version "
                        + compound.getInteger(TAG_VERSION));
                return;
            }
            if (!NbtTags.hasCompoundListWithinLimit(compound, TAG_STARTS, MAX_ENTRIES)) {
                preserve(compound, "its starts are no list of at most "
                        + MAX_ENTRIES + " entries");
                return;
            }
            NbtQuarantine.Read stored = NbtQuarantine.read(compound);
            if (!stored.isSupported()) {
                preserve(compound, "its quarantine cannot be read");
                return;
            }
            List<NBTTagCompound> aside = new ArrayList<NBTTagCompound>(stored.getEntries());
            Map<String, Integer> indexByLine = new HashMap<String, Integer>();
            NBTTagList list = compound.getTagList(TAG_STARTS, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < list.tagCount(); index++) {
                NBTTagCompound entry = list.getCompoundTagAt(index);
                String problem = problem(entry);
                if (problem.length() > 0) {
                    aside.add(NbtQuarantine.entry(problem, TAG_START_INDEX, index, entry));
                    continue;
                }
                String line = entry.getString(TAG_LINE);
                long at = entry.getLong(TAG_AT);
                Long earlier = this.starts.get(line);
                if (earlier == null) {
                    this.starts.put(line, Long.valueOf(at));
                    indexByLine.put(line, Integer.valueOf(index));
                    continue;
                }
                // The later time stays; on a tie, the one read first.
                int setAside = index;
                if (at > earlier.longValue()) {
                    setAside = indexByLine.get(line).intValue();
                    this.starts.put(line, Long.valueOf(at));
                    indexByLine.put(line, Integer.valueOf(index));
                }
                aside.add(NbtQuarantine.entry("duplicate_line", TAG_START_INDEX,
                        setAside, list.getCompoundTagAt(setAside)));
            }
            if (aside.size() > NbtQuarantine.MAX_ENTRIES) {
                this.starts.clear();
                preserve(compound, "its quarantine would hold " + aside.size()
                        + " entries, past its " + NbtQuarantine.MAX_ENTRIES);
                return;
            }
            this.quarantined.addAll(aside);
            if (aside.size() > stored.getEntries().size()) {
                markDirty();
            }
        }

        /** Why a saved start cannot be read, as its quarantine reason; empty for one that can. */
        private static String problem(NBTTagCompound entry) {
            if (!entry.hasKey(TAG_LINE, Constants.NBT.TAG_STRING)) {
                return "missing_line";
            }
            String line = entry.getString(TAG_LINE);
            if (line.length() == 0 || line.length() > MAX_LINE_LENGTH) {
                return "unreadable_line";
            }
            return entry.hasKey(TAG_AT, Constants.NBT.TAG_LONG) ? "" : "missing_time";
        }

        private void preserve(NBTTagCompound compound, String because) {
            this.preserved = (NBTTagCompound) compound.copy();
            this.preservedBecause = because;
        }

        @Override
        public synchronized void writeToNBT(NBTTagCompound compound) {
            if (this.preserved != null) {
                NbtTags.copyContents(this.preserved, compound);
                return;
            }
            compound.setInteger(TAG_VERSION, DATA_VERSION);
            NBTTagList list = new NBTTagList();
            for (Map.Entry<String, Long> entry : this.starts.entrySet()) {
                NBTTagCompound tag = new NBTTagCompound();
                tag.setString(TAG_LINE, entry.getKey());
                tag.setLong(TAG_AT, entry.getValue().longValue());
                list.appendTag(tag);
            }
            compound.setTag(TAG_STARTS, list);
            NbtQuarantine.write(compound, this.quarantined);
        }

        /** Whether schedules may start quests; a record kept as it was is logged once. */
        synchronized boolean isUsable() {
            if (this.preserved != null && !this.warned) {
                this.warned = true;
                LostTalesLog.warning("The world quest schedules' record cannot be"
                        + " read, since %s; no schedule starts a quest until it can",
                        this.preservedBecause);
            }
            return this.preserved == null;
        }

        /** When the schedule last started its quest; 0 for never. */
        synchronized long lastStart(String line) {
            Long at = this.starts.get(line);
            return at == null ? 0L : at.longValue();
        }

        /**
         * Keeps that the schedule started its quest for the time
         * {@code at}. False, and its quest is not to start, when the record
         * cannot keep the line: one it does not hold yet while it holds
         * {@link #MAX_ENTRIES}, or one longer than {@link #MAX_LINE_LENGTH}.
         * Each line refused is logged once.
         */
        synchronized boolean started(String line, long at) {
            if (this.preserved != null || line == null || line.length() == 0) {
                return false;
            }
            if (line.length() > MAX_LINE_LENGTH) {
                refuse(line, "it is longer than " + MAX_LINE_LENGTH + " characters");
                return false;
            }
            if (!this.starts.containsKey(line) && this.starts.size() >= MAX_ENTRIES) {
                refuse(line, "the record holds its " + MAX_ENTRIES
                        + " schedules and forgets none");
                return false;
            }
            this.starts.put(line, Long.valueOf(at));
            markDirty();
            return true;
        }

        private void refuse(String line, String because) {
            if (this.refused.add(line)) {
                LostTalesLog.warning("The world quest schedule '%s' starts nothing,"
                        + " since %s", line, because);
            }
        }
    }
}
