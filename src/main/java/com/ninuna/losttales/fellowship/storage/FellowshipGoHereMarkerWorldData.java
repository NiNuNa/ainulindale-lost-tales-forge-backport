package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipGoHereMarker;
import com.ninuna.losttales.storage.NbtTags;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Persistent go-here marker store: one marker per identity. */
public final class FellowshipGoHereMarkerWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_fellowship_go_here_markers";

    private final Map<UUID, FellowshipGoHereMarker> markersByOwnerCharacter =
            new LinkedHashMap<UUID, FellowshipGoHereMarker>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public FellowshipGoHereMarkerWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.markersByOwnerCharacter.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        FellowshipGoHereMarkerNbtCodec.ReadResult result =
                FellowshipGoHereMarkerNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalDataCopy();
            return;
        }
        this.markersByOwnerCharacter.putAll(result.getMarkers());
        this.quarantinedEntries.addAll(result.getQuarantineEntriesCopy());
        if (result.wasRepaired()) {
            markDirty();
        }
    }

    @Override
    public synchronized void writeToNBT(NBTTagCompound compound) {
        if (this.readOnlyForNewerVersion
                && this.preservedNewerData != null) {
            NbtTags.copyContents(this.preservedNewerData, compound);
            return;
        }
        FellowshipGoHereMarkerNbtCodec.write(
                compound,
                this.markersByOwnerCharacter.values(),
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized FellowshipGoHereMarker getMarker(UUID ownerIdentityId) {
        return ownerIdentityId == null ? null
                : this.markersByOwnerCharacter.get(ownerIdentityId);
    }

    public synchronized Collection<FellowshipGoHereMarker> getMarkers() {
        return Collections.unmodifiableList(
                new ArrayList<FellowshipGoHereMarker>(
                        this.markersByOwnerCharacter.values()));
    }

    public synchronized boolean saveMarker(FellowshipGoHereMarker marker) {
        ensureWritable();
        if (marker == null) {
            throw new IllegalArgumentException("marker must not be null");
        }
        FellowshipGoHereMarker previous = this.markersByOwnerCharacter.put(
                marker.getOwnerIdentityId(), marker);
        boolean changed = !sameMarker(previous, marker);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public synchronized FellowshipGoHereMarker removeMarker(
            UUID ownerIdentityId) {
        ensureWritable();
        FellowshipGoHereMarker removed = ownerIdentityId == null ? null
                : this.markersByOwnerCharacter.remove(ownerIdentityId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public synchronized void quarantine(String reason,
                                        FellowshipGoHereMarker marker) {
        ensureWritable();
        this.quarantinedEntries.add(
                FellowshipGoHereMarkerNbtCodec.createQuarantineEntry(
                        reason, marker));
        markDirty();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Fellowship marker data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }

    private static boolean sameMarker(FellowshipGoHereMarker left,
                                      FellowshipGoHereMarker right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return equalNullable(left.getFellowshipId(), right.getFellowshipId())
                && left.getOwnerIdentityId().equals(
                right.getOwnerIdentityId())
                && left.getDimensionId() == right.getDimensionId()
                && Double.doubleToLongBits(left.getX())
                == Double.doubleToLongBits(right.getX())
                && Double.doubleToLongBits(left.getY())
                == Double.doubleToLongBits(right.getY())
                && Double.doubleToLongBits(left.getZ())
                == Double.doubleToLongBits(right.getZ())
                && left.getUpdatedAt() == right.getUpdatedAt();
    }

    private static boolean equalNullable(Object left, Object right) {
        return left == null ? right == null : left.equals(right);
    }
}
