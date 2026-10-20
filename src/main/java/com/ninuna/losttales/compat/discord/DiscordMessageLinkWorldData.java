package com.ninuna.losttales.compat.discord;

import com.ninuna.losttales.storage.NbtTags;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

/**
 * The Discord bridge's memory in the world save: the message links, so a
 * restart hands them back with the chat history they index into, and
 * each kept topic's last two writes and their words, so a restart keeps
 * Discord's pace. The live stores stay {@link DiscordMessageLinks} and
 * {@link DiscordTopicMemory}; this is where they are read from as the
 * server starts and written to as the world saves: from the live stores
 * while the server runs, and from the snapshots {@link #detach} took as
 * it stops, since the bridge moves on to empty ones before the last save
 * is written. Each is taken on its own: the topics come back whatever
 * became of the history, and links that wait for their history are
 * written back as they were read.
 *
 * <p>The bridge's worker changes the live stores as well as the server
 * thread, so nothing here waits to be marked: every change moves a
 * store's revision on, and the save is dirty while that differs from the
 * revision of its last write, which a change made during a write cannot
 * slip past. A store made read-only by newer-version data is preserved
 * verbatim and never written; the links and topics then live in memory
 * alone for the run. No webhook URL is held here at any point.</p>
 */
public final class DiscordMessageLinkWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_discord_links";

    /** The links read from the save, until a live map takes them. */
    private final List<DiscordMessageLinks.SavedLink> restored =
            new ArrayList<DiscordMessageLinks.SavedLink>();
    /** The topics read from the save, until a live memory takes them. */
    private final List<DiscordTopicMemory.SavedTopic> restoredTopics =
            new ArrayList<DiscordTopicMemory.SavedTopic>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    /** The live map written from while the server runs, or null. */
    private DiscordMessageLinks attached;
    /** The live map's revision as of the last write taken from it. */
    private long writtenRevision;
    /** The snapshot the stopping server left to be written, or null. */
    private List<DiscordMessageLinks.SavedLink> held;
    /** The live topic memory written from while the server runs, or null. */
    private DiscordTopicMemory attachedTopics;
    /** The topic memory's revision as of the last write taken from it. */
    private long writtenTopicRevision;
    /** The topics the stopping server left to be written, or null. */
    private List<DiscordTopicMemory.SavedTopic> heldTopics;

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public DiscordMessageLinkWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.restored.clear();
        this.restoredTopics.clear();
        this.quarantinedEntries.clear();
        this.attached = null;
        this.held = null;
        this.attachedTopics = null;
        this.heldTopics = null;
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
        this.restoredTopics.addAll(result.getTopics());
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion) {
            if (this.preservedNewerData != null) {
                NbtTags.copyContents(this.preservedNewerData, compound);
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
            // No live map took them: a run without its history, or a
            // start that failed after the read. What was read is written
            // back as it was.
            links = this.restored;
        }
        List<DiscordTopicMemory.SavedTopic> topics;
        if (this.heldTopics != null) {
            topics = this.heldTopics;
        } else if (this.attachedTopics != null) {
            DiscordTopicMemory.Snapshot snapshot = this.attachedTopics.snapshot();
            topics = snapshot.topics;
            this.writtenTopicRevision = snapshot.revision;
        } else {
            topics = this.restoredTopics;
        }
        DiscordMessageLinkNbtCodec.write(compound, links, topics, this.quarantinedEntries);
    }

    /** Dirty when marked, or while a live store has moved on since the last write. */
    @Override
    public synchronized boolean isDirty() {
        return super.isDirty() || (this.attached != null
                && this.attached.revision() != this.writtenRevision)
                || (this.attachedTopics != null
                        && this.attachedTopics.revision() != this.writtenTopicRevision);
    }

    /** The links read from the save, oldest first; what a live map is restored from. */
    synchronized List<DiscordMessageLinks.SavedLink> restoredLinks() {
        return Collections.unmodifiableList(
                new ArrayList<DiscordMessageLinks.SavedLink>(this.restored));
    }

    /** The topics read from the save; what a live memory is restored from. */
    synchronized List<DiscordTopicMemory.SavedTopic> restoredTopics() {
        return Collections.unmodifiableList(
                new ArrayList<DiscordTopicMemory.SavedTopic>(this.restoredTopics));
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
     * Writes the topics from {@code live} from now on, starting from what
     * it holds now, which is what it was restored from. A read-only store
     * takes no live memory.
     */
    synchronized void attachTopics(DiscordTopicMemory live) {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Discord topics in this save are read-only (version "
                            + this.unsupportedDataVersion + ")");
        }
        if (live == null) {
            return;
        }
        this.attachedTopics = live;
        this.heldTopics = null;
        this.restoredTopics.clear();
        this.writtenTopicRevision = live.revision();
    }

    /**
     * Keeps the live stores' last state for the saves still to come and
     * stops writing from them: what the stopping server calls once the
     * bridge has stopped, its offline topic written, so the last save
     * writes the links and the topics and not the empty stores the bridge
     * moves on to.
     */
    synchronized void detach() {
        boolean changed = false;
        if (this.attached != null) {
            DiscordMessageLinks.Snapshot snapshot = this.attached.snapshot();
            changed = snapshot.revision != this.writtenRevision;
            this.held = snapshot.links;
            this.attached = null;
        }
        if (this.attachedTopics != null) {
            DiscordTopicMemory.Snapshot snapshot = this.attachedTopics.snapshot();
            changed |= snapshot.revision != this.writtenTopicRevision;
            this.heldTopics = snapshot.topics;
            this.attachedTopics = null;
        }
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
}
