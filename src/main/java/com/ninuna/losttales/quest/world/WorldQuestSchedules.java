package com.ninuna.losttales.quest.world;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        if (store == null || !store.isUsable()) {
            return;
        }
        TimeZone zone = TimeZone.getDefault();
        for (WorldQuestSchedule schedule : schedules) {
            long due = schedule.latestDue(nowMillis, zone);
            if (!WorldQuestSchedule.isToday(due, nowMillis, zone)
                    || due <= store.lastStart(schedule.getLine())) {
                continue;
            }
            store.started(schedule.getLine(), due);
            WorldQuests.StartResult result = WorldQuests.start(server,
                    schedule.getQuestId());
            if (result != WorldQuests.StartResult.STARTED
                    && result != WorldQuests.StartResult.ALREADY_RUNNING) {
                LostTalesLog.warning("The world quest schedule '%s' could not start"
                        + " its quest: %s", schedule.getLine(), result);
            }
        }
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
     * milliseconds. Data of another version is kept as it was and no
     * schedule starts anything, rather than one starting twice.
     */
    public static final class Store extends WorldSavedData {
        public static final String DATA_NAME = "losttales_world_quest_schedule";
        static final int DATA_VERSION = 1;
        /** The most schedules remembered; as many as a settings list holds sensibly. */
        static final int MAX_ENTRIES = 256;
        static final String TAG_VERSION = "DataVersion";
        static final String TAG_STARTS = "Starts";
        static final String TAG_LINE = "Line";
        static final String TAG_AT = "At";

        private final Map<String, Long> starts = new LinkedHashMap<String, Long>();
        private NBTTagCompound preserved;
        private boolean warned;

        public Store(String name) {
            super(name);
        }

        @Override
        public synchronized void readFromNBT(NBTTagCompound compound) {
            this.starts.clear();
            this.preserved = null;
            if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                    || !NbtTags.hasCompoundListWithinLimit(compound, TAG_STARTS, MAX_ENTRIES)) {
                this.preserved = (NBTTagCompound) compound.copy();
                return;
            }
            NBTTagList list = compound.getTagList(TAG_STARTS, Constants.NBT.TAG_COMPOUND);
            for (int index = 0; index < list.tagCount(); index++) {
                NBTTagCompound entry = list.getCompoundTagAt(index);
                String line = entry.getString(TAG_LINE);
                if (line.length() > 0 && line.length() <= 512
                        && entry.hasKey(TAG_AT, Constants.NBT.TAG_LONG)) {
                    this.starts.put(line, Long.valueOf(entry.getLong(TAG_AT)));
                }
            }
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
        }

        synchronized boolean isUsable() {
            if (this.preserved != null && !this.warned) {
                this.warned = true;
                LostTalesLog.warning("The world quest schedules' record cannot be"
                        + " read; no schedule starts a quest until it can");
            }
            return this.preserved == null;
        }

        /** When the schedule last started its quest; 0 for never. */
        synchronized long lastStart(String line) {
            Long at = this.starts.get(line);
            return at == null ? 0L : at.longValue();
        }

        /** Keeps that the schedule started its quest for the time {@code at}. */
        synchronized void started(String line, long at) {
            if (line == null || line.length() == 0 || line.length() > 512) {
                return;
            }
            this.starts.remove(line);
            while (this.starts.size() >= MAX_ENTRIES) {
                this.starts.remove(this.starts.keySet().iterator().next());
            }
            this.starts.put(line, Long.valueOf(at));
            markDirty();
        }
    }
}
