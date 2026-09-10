package com.ninuna.losttales.chat.server;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/**
 * The kept chat history in the world save: what {@link ChatHistory}
 * holds, written with the world so a restart hands it back. The live
 * store stays {@code ChatHistory}; this is where it is read from as the
 * server starts and written to as the world saves — from the live store
 * while the server runs, and from the snapshot {@link #hold} took as it
 * stops, since the store is cleared before the last save is written. A
 * store made read-only by newer-version data is preserved verbatim and
 * never written; the history then lives in memory alone for the run.
 */
public final class ChatHistoryWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_chat_history";

    private final List<ChatHistory.Entry> restored =
            new ArrayList<ChatHistory.Entry>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    /** The snapshot the stopping server left to be written, or null. */
    private List<ChatHistory.Entry> held;

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public ChatHistoryWorldData() {
        this(DATA_NAME);
    }

    public ChatHistoryWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.restored.clear();
        this.quarantinedEntries.clear();
        this.held = null;
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        ChatHistoryNbtCodec.ReadResult result = ChatHistoryNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }
        this.restored.addAll(result.getEntries());
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            copyTagContents(this.preservedNewerData, compound);
            return;
        }
        ChatHistoryNbtCodec.write(compound,
                this.held != null ? this.held : ChatHistory.snapshot(),
                this.quarantinedEntries);
    }

    /** The lines read from the save, oldest first; empty once taken. */
    public synchronized List<ChatHistory.Entry> takeRestored() {
        List<ChatHistory.Entry> entries =
                new ArrayList<ChatHistory.Entry>(this.restored);
        this.restored.clear();
        return Collections.unmodifiableList(entries);
    }

    /**
     * Keeps a snapshot for the saves still to come: what the stopping
     * server calls before the live store is cleared, so the last save
     * writes the history and not the emptiness after it.
     */
    public synchronized void hold(List<ChatHistory.Entry> snapshot) {
        this.held = snapshot == null ? null
                : new ArrayList<ChatHistory.Entry>(snapshot);
        markDirty();
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private static void copyTagContents(NBTTagCompound source,
                                        NBTTagCompound destination) {
        Set<?> keySet = source.func_150296_c();
        for (Object keyObject : keySet) {
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String) keyObject;
            NBTBase value = source.getTag(key);
            if (value != null) {
                destination.setTag(key, value.copy());
            }
        }
    }
}
