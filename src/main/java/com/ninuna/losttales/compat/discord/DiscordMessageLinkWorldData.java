package com.ninuna.losttales.compat.discord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/**
 * The Discord bridge's message links in the world save, so a restart
 * hands them back with the chat history they index into. The live map
 * stays {@link DiscordMessageLinks}; this is where it is read from as
 * the server starts and written to as the world saves: from the live
 * map while the server runs, and from the snapshot {@link #detach} took
 * as it stops, since the bridge moves on to an empty map before the
 * last save is written.
 *
 * <p>The bridge's worker changes the live map as well as the server
 * thread, so nothing here waits to be marked: every change moves the
 * map's revision on, and the store is dirty while that differs from the
 * revision of its last write, which a change made during a write cannot
 * slip past. A store made read-only by newer-version data is preserved
 * verbatim and never written; the links then live in memory alone for
 * the run. No webhook URL is held here at any point.</p>
 */
public final class DiscordMessageLinkWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_discord_links";

    /** The links read from the save, until a live map takes them. */
    private final List<DiscordMessageLinks.SavedLink> restored =
            new ArrayList<DiscordMessageLinks.SavedLink>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    /** The live map written from while the server runs, or null. */
    private DiscordMessageLinks attached;
    /** The live map's revision as of the last write taken from it. */
    private long writtenRevision;
    /** The snapshot the stopping server left to be written, or null. */
    private List<DiscordMessageLinks.SavedLink> held;

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public DiscordMessageLinkWorldData() {
        this(DATA_NAME);
    }

    public DiscordMessageLinkWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.restored.clear();
        this.quarantinedEntries.clear();
        this.attached = null;
        this.held = null;
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        DiscordMessageLinkNbtCodec.ReadResult result =
                DiscordMessageLinkNbtCodec.read(compound);
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
        if (this.readOnlyForNewerVersion) {
            if (this.preservedNewerData != null) {
                copyTagContents(this.preservedNewerData, compound);
            }
            return;
        }
        List<DiscordMessageLinks.SavedLink> links;
        if (this.held != null) {
            links = this.held;
        } else if (this.attached != null) {
            DiscordMessageLinks.Snapshot snapshot = this.attached.snapshot();
            links = snapshot.links;
            this.writtenRevision = snapshot.revision;
        } else {
            // No live map took them, which is a start that failed after
            // the read: what was read is written back as it was.
            links = this.restored;
        }
        DiscordMessageLinkNbtCodec.write(compound, links, this.quarantinedEntries);
    }

    /** Dirty when marked, or while the live map has moved on since the last write. */
    @Override
    public synchronized boolean isDirty() {
        return super.isDirty() || (this.attached != null
                && this.attached.revision() != this.writtenRevision);
    }

    /** The links read from the save, oldest first; what a live map is restored from. */
    synchronized List<DiscordMessageLinks.SavedLink> restoredLinks() {
        return Collections.unmodifiableList(
                new ArrayList<DiscordMessageLinks.SavedLink>(this.restored));
    }

    /**
     * Writes from {@code live} from now on, starting from what it holds
     * now. {@code changed} says the restore left out something the save
     * had, so the save is rewritten to follow. A read-only store takes
     * no live map.
     */
    synchronized void attach(DiscordMessageLinks live, boolean changed) {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Discord message links in this save are read-only (version "
                            + this.unsupportedDataVersion + ")");
        }
        if (live == null) {
            return;
        }
        this.attached = live;
        this.held = null;
        this.restored.clear();
        this.writtenRevision = live.revision();
        if (changed) {
            markDirty();
        }
    }

    /**
     * Keeps the live map's last state for the saves still to come and
     * stops writing from it: what the stopping server calls once the
     * bridge has stopped, so the last save writes the links and not the
     * empty map the bridge moves on to.
     */
    synchronized void detach() {
        if (this.attached == null) {
            return;
        }
        DiscordMessageLinks.Snapshot snapshot = this.attached.snapshot();
        boolean changed = snapshot.revision != this.writtenRevision;
        this.held = snapshot.links;
        this.attached = null;
        if (changed) {
            markDirty();
        }
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
