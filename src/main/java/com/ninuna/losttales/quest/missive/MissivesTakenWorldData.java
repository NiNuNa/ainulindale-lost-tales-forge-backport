package com.ninuna.losttales.quest.missive;

import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * The missives taken in this world, each by its quest id with the time its
 * notice was posted (QUEST-34). A letter whose missive stands here starts
 * nothing, so a copy of a letter, made in creative mode or by a
 * duplication bug, never pays twice, nor once for each player who holds
 * one.
 *
 * <p>An entry is kept for as long as a copy of its letter could still go
 * up on a board or be accepted: a letter keeps its first posting's days
 * wherever it is pinned, so the record keeps each for the longest days a
 * notice may stand from then ({@link MissivesTaken}), and for good while
 * notices never expire or where its letter has no posting time. Past
 * {@link #MAX_ENTRIES} a new one is refused, so no missive is taken until
 * older ones are forgotten.</p>
 *
 * <p>An entry that cannot be read, or the later of two for one missive,
 * is set aside in the quarantine, never dropped (DATA-10). Data of
 * another version, a quarantine this build cannot read, or one that would
 * hold more than {@link NbtQuarantine#MAX_ENTRIES} is kept as it was and
 * the store goes read-only: then no missive can be taken, rather than one
 * taken twice.</p>
 */
public final class MissivesTakenWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_missives_taken";
    static final int DATA_VERSION = 2;
    /** The most missives the world remembers at once. */
    public static final int MAX_ENTRIES = 65536;
    static final String TAG_VERSION = "DataVersion";
    static final String TAG_TAKEN = "Taken";
    static final String TAG_QUEST_ID = "QuestId";
    static final String TAG_POSTED_AT = "PostedAt";
    /** Where an entry set aside stood in the list of missives taken. */
    static final String TAG_TAKEN_INDEX = "TakenIndex";

    /** Each missive taken, by quest id, with when its notice was posted; oldest first. */
    private final Map<String, Long> taken = new LinkedHashMap<String, Long>();
    private final List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
    private NBTTagCompound preserved;
    /** Whether a refusal for unreadable data was logged in this world. */
    private boolean refusalLogged;

    public MissivesTakenWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.taken.clear();
        this.quarantined.clear();
        this.preserved = null;
        if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                || !compound.hasKey(TAG_TAKEN, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(compound, TAG_TAKEN, MAX_ENTRIES)) {
            preserve(compound);
            return;
        }
        NbtQuarantine.Read stored = NbtQuarantine.read(compound);
        if (!stored.isSupported()) {
            preserve(compound);
            return;
        }
        List<NBTTagCompound> aside = new ArrayList<NBTTagCompound>(stored.getEntries());
        NBTTagList list = compound.getTagList(TAG_TAKEN, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String problem = problem(entry);
            if (problem.length() == 0
                    && this.taken.containsKey(entry.getString(TAG_QUEST_ID))) {
                problem = "duplicate_missive";
            }
            if (problem.length() > 0) {
                aside.add(NbtQuarantine.entry(problem, TAG_TAKEN_INDEX, index, entry));
                continue;
            }
            this.taken.put(entry.getString(TAG_QUEST_ID),
                    Long.valueOf(entry.getLong(TAG_POSTED_AT)));
        }
        if (aside.size() > NbtQuarantine.MAX_ENTRIES) {
            this.taken.clear();
            preserve(compound);
            return;
        }
        this.quarantined.addAll(aside);
        if (aside.size() > stored.getEntries().size()) {
            markDirty();
        }
    }

    /** Why a saved entry cannot be read, as its quarantine reason; empty for one that can. */
    private static String problem(NBTTagCompound entry) {
        if (!entry.hasKey(TAG_QUEST_ID, Constants.NBT.TAG_STRING)) {
            return "missing_quest_id";
        }
        String questId = entry.getString(TAG_QUEST_ID);
        if (questId.length() == 0 || !LostTalesQuestIds.fits(questId)) {
            return "unreadable_quest_id";
        }
        if (!entry.hasKey(TAG_POSTED_AT, Constants.NBT.TAG_LONG)
                || entry.getLong(TAG_POSTED_AT) < 0L) {
            return "unreadable_posting_time";
        }
        return "";
    }

    private void preserve(NBTTagCompound compound) {
        this.preserved = (NBTTagCompound) compound.copy();
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.preserved != null) {
            NbtTags.copyContents(this.preserved, compound);
            return;
        }
        compound.setInteger(TAG_VERSION, DATA_VERSION);
        NBTTagList list = new NBTTagList();
        for (Map.Entry<String, Long> entry : this.taken.entrySet()) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setString(TAG_QUEST_ID, entry.getKey());
            tag.setLong(TAG_POSTED_AT, entry.getValue().longValue());
            list.appendTag(tag);
        }
        compound.setTag(TAG_TAKEN, list);
        NbtQuarantine.write(compound, this.quarantined);
    }

    /** Whether missives can be taken here: the store could be read. */
    public synchronized boolean isUsable() {
        return this.preserved == null;
    }

    /** Whether this is the first refusal since the data was read: it is logged once. */
    synchronized boolean firstRefusal() {
        boolean first = !this.refusalLogged;
        this.refusalLogged = true;
        return first;
    }

    /** Whether the missive was taken already, by anyone. */
    public synchronized boolean wasTaken(String questId) {
        return questId != null && this.taken.containsKey(questId);
    }

    /**
     * Records the missive as taken. Entries no copy could bring back at
     * {@code now} go first: those first posted {@code keepTicks} or longer
     * before, none while it is 0. Answers whether it was recorded: never
     * while the store is read-only, nor once it holds {@link #MAX_ENTRIES}.
     */
    public synchronized boolean take(String questId, long postedAt, long now,
                                     long keepTicks) {
        if (!isUsable() || questId == null || questId.length() == 0
                || !LostTalesQuestIds.fits(questId)) {
            return false;
        }
        if (forgetPast(now, keepTicks)) {
            markDirty();
        }
        if (!this.taken.containsKey(questId) && this.taken.size() >= MAX_ENTRIES) {
            return false;
        }
        this.taken.put(questId, Long.valueOf(Math.max(0L, postedAt)));
        markDirty();
        return true;
    }

    /** How many missives the world remembers now. */
    public synchronized int size() {
        return this.taken.size();
    }

    /**
     * Forgets the entries posted {@code keepTicks} or longer before
     * {@code now}, by the one rule for a notice's days
     * ({@link MissiveNotice#isPast}); answers whether any went.
     */
    private boolean forgetPast(long now, long keepTicks) {
        if (keepTicks <= 0L) {
            return false;
        }
        boolean forgot = false;
        Iterator<Map.Entry<String, Long>> entries = this.taken.entrySet().iterator();
        while (entries.hasNext()) {
            if (MissiveNotice.isPast(entries.next().getValue().longValue(),
                    keepTicks, now)) {
                entries.remove();
                forgot = true;
            }
        }
        return forgot;
    }
}
