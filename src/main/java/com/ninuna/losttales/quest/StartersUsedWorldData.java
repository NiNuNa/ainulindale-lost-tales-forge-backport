package com.ninuna.losttales.quest;

import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/**
 * The quest starters used in this world, each by its nonce (QUEST-34), as
 * the missives taken are kept. A starter whose nonce stands here starts
 * nothing, so a copy of a starter, made in creative mode or by a
 * duplication bug, never starts its quest a second time.
 *
 * <p>A starter never runs out, so an entry is kept for good. Past
 * {@link #MAX_ENTRIES} a new one is refused, so no starter starts a quest
 * until the record has room; none is forgotten to make it.</p>
 *
 * <p>An entry that cannot be read, or the later of two for one starter,
 * is set aside in the quarantine, never dropped (DATA-10). Data of
 * another version, a quarantine this build cannot read, or one that would
 * hold more than {@link NbtQuarantine#MAX_ENTRIES} is kept as it was and
 * the store goes read-only: then no starter starts a quest, rather than
 * one starting twice.</p>
 */
public final class StartersUsedWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_starters_used";
    static final int DATA_VERSION = 1;
    /** The most starters the world remembers. */
    public static final int MAX_ENTRIES = 65536;
    static final String TAG_VERSION = "DataVersion";
    static final String TAG_USED = "Used";
    static final String TAG_NONCE = "Nonce";
    /** Where an entry set aside stood in the list of starters used. */
    static final String TAG_USED_INDEX = "UsedIndex";

    /** Each starter used, by its nonce written as hex; oldest first. */
    private final Set<String> used = new LinkedHashSet<String>();
    private final List<NBTTagCompound> quarantined = new ArrayList<NBTTagCompound>();
    private NBTTagCompound preserved;
    /** Whether a refusal for unreadable or full data was logged in this world. */
    private boolean refusalLogged;

    public StartersUsedWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.used.clear();
        this.quarantined.clear();
        this.preserved = null;
        if (compound.getInteger(TAG_VERSION) != DATA_VERSION
                || !compound.hasKey(TAG_USED, Constants.NBT.TAG_LIST)
                || !NbtTags.hasCompoundListWithinLimit(compound, TAG_USED, MAX_ENTRIES)) {
            preserve(compound);
            return;
        }
        NbtQuarantine.Read stored = NbtQuarantine.read(compound);
        if (!stored.isSupported()) {
            preserve(compound);
            return;
        }
        List<NBTTagCompound> aside = new ArrayList<NBTTagCompound>(stored.getEntries());
        NBTTagList list = compound.getTagList(TAG_USED, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String problem = problem(entry);
            String key = problem.length() == 0 ? key(entry.getByteArray(TAG_NONCE)) : "";
            if (problem.length() == 0 && this.used.contains(key)) {
                problem = "duplicate_starter";
            }
            if (problem.length() > 0) {
                aside.add(NbtQuarantine.entry(problem, TAG_USED_INDEX, index, entry));
                continue;
            }
            this.used.add(key);
        }
        if (aside.size() > NbtQuarantine.MAX_ENTRIES) {
            this.used.clear();
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
        if (!entry.hasKey(TAG_NONCE, Constants.NBT.TAG_BYTE_ARRAY)) {
            return "missing_nonce";
        }
        return fits(entry.getByteArray(TAG_NONCE)) ? "" : "unreadable_nonce";
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
        for (String key : this.used) {
            NBTTagCompound tag = new NBTTagCompound();
            tag.setByteArray(TAG_NONCE, bytes(key));
            list.appendTag(tag);
        }
        compound.setTag(TAG_USED, list);
        NbtQuarantine.write(compound, this.quarantined);
    }

    /** Whether starters can start quests here: the store could be read. */
    public synchronized boolean isUsable() {
        return this.preserved == null;
    }

    /** Whether this is the first refusal since the data was read: it is logged once. */
    synchronized boolean firstRefusal() {
        boolean first = !this.refusalLogged;
        this.refusalLogged = true;
        return first;
    }

    /** Whether the starter with this nonce was used already, by anyone. */
    public synchronized boolean wasUsed(byte[] nonce) {
        return fits(nonce) && this.used.contains(key(nonce));
    }

    /**
     * Records the starter with this nonce as used. Answers whether it was
     * recorded: never while the store is read-only, for a nonce no starter
     * carries, nor once it holds {@link #MAX_ENTRIES}.
     */
    public synchronized boolean use(byte[] nonce) {
        if (!isUsable() || !fits(nonce)) {
            return false;
        }
        String key = key(nonce);
        if (!this.used.contains(key) && this.used.size() >= MAX_ENTRIES) {
            return false;
        }
        this.used.add(key);
        markDirty();
        return true;
    }

    /** How many starters the world remembers now. */
    public synchronized int size() {
        return this.used.size();
    }

    /** Whether a nonce is one a starter carries: {@link QuestStarters#NONCE_BYTES} long. */
    static boolean fits(byte[] nonce) {
        return nonce != null && nonce.length == QuestStarters.NONCE_BYTES;
    }

    private static String key(byte[] nonce) {
        StringBuilder hex = new StringBuilder(nonce.length * 2);
        for (byte value : nonce) {
            hex.append(Character.forDigit((value >> 4) & 0xF, 16));
            hex.append(Character.forDigit(value & 0xF, 16));
        }
        return hex.toString();
    }

    private static byte[] bytes(String key) {
        byte[] nonce = new byte[key.length() / 2];
        for (int index = 0; index < nonce.length; index++) {
            nonce[index] = (byte) Integer.parseInt(key.substring(index * 2, index * 2 + 2), 16);
        }
        return nonce;
    }
}
