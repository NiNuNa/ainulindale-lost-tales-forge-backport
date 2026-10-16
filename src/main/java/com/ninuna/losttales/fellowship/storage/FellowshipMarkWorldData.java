package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent store of every mark, of both kinds, by mark id; a character's
 * go-here mark is found by its owner too, one an owner.
 */
public final class FellowshipMarkWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_fellowship_marks";

    private final Map<UUID, FellowshipMark> marks =
            new LinkedHashMap<UUID, FellowshipMark>();
    /** Each owner's go-here mark, by the owner. */
    private final Map<UUID, UUID> goHereByOwner = new HashMap<UUID, UUID>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public FellowshipMarkWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.marks.clear();
        this.goHereByOwner.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;
        FellowshipMarkNbtCodec.ReadResult result = FellowshipMarkNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }
        for (FellowshipMark mark : result.getMarks().values()) {
            put(mark);
        }
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion && this.preservedNewerData != null) {
            NbtTags.copyContents(this.preservedNewerData, compound);
            return;
        }
        FellowshipMarkNbtCodec.write(compound, this.marks.values(),
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized FellowshipMark getMark(UUID markId) {
        return markId == null ? null : this.marks.get(markId);
    }

    /** A fellowship's marks, the oldest first; never a go-here mark. */
    public synchronized List<FellowshipMark> getMarks(UUID fellowshipId) {
        List<FellowshipMark> held = new ArrayList<FellowshipMark>();
        if (fellowshipId == null) {
            return Collections.unmodifiableList(held);
        }
        for (FellowshipMark mark : this.marks.values()) {
            if (fellowshipId.equals(mark.getFellowshipId())) {
                held.add(mark);
            }
        }
        return Collections.unmodifiableList(held);
    }

    /** A character's go-here mark; null for none. */
    public synchronized FellowshipMark getGoHere(UUID ownerIdentityId) {
        UUID markId = ownerIdentityId == null ? null
                : this.goHereByOwner.get(ownerIdentityId);
        return markId == null ? null : this.marks.get(markId);
    }

    public synchronized Collection<FellowshipMark> getAllMarks() {
        return Collections.unmodifiableList(
                new ArrayList<FellowshipMark>(this.marks.values()));
    }

    public synchronized int getMarkCount() {
        return this.marks.size();
    }

    /**
     * Places or moves a mark. A go-here mark stands in for any other its
     * owner had.
     */
    public synchronized void saveMark(FellowshipMark mark) {
        ensureWritable();
        if (mark == null) {
            throw new IllegalArgumentException("mark must not be null");
        }
        if (mark.isGoHere()) {
            FellowshipMark previous = getGoHere(mark.getPlacedBy());
            if (previous != null && !previous.getMarkId().equals(mark.getMarkId())) {
                this.marks.remove(previous.getMarkId());
            }
        }
        FellowshipMark replaced = this.marks.get(mark.getMarkId());
        if (replaced != null && replaced.isGoHere()) {
            this.goHereByOwner.remove(replaced.getPlacedBy());
        }
        put(mark);
        markDirty();
    }

    public synchronized FellowshipMark removeMark(UUID markId) {
        ensureWritable();
        FellowshipMark removed = markId == null ? null : this.marks.remove(markId);
        if (removed != null) {
            if (removed.isGoHere()) {
                this.goHereByOwner.remove(removed.getPlacedBy());
            }
            markDirty();
        }
        return removed;
    }

    /** Takes every mark of a fellowship away, as it ends; answers how many went. */
    public synchronized int removeMarksOf(UUID fellowshipId) {
        ensureWritable();
        int removed = 0;
        for (FellowshipMark mark : getMarks(fellowshipId)) {
            this.marks.remove(mark.getMarkId());
            removed++;
        }
        if (removed > 0) {
            markDirty();
        }
        return removed;
    }

    public synchronized void quarantine(String reason, FellowshipMark mark) {
        ensureWritable();
        this.quarantinedEntries.add(
                FellowshipMarkNbtCodec.createQuarantineEntry(reason, mark));
        markDirty();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private void put(FellowshipMark mark) {
        this.marks.put(mark.getMarkId(), mark);
        if (mark.isGoHere()) {
            this.goHereByOwner.put(mark.getPlacedBy(), mark.getMarkId());
        }
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Fellowship mark data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }
}
