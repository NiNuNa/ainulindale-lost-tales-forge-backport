package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.PartyGoHereMarker;
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
public final class PartyGoHereMarkerWorldData extends WorldSavedData {

    public static final String DATA_NAME = "losttales_party_go_here_markers";

    private final Map<UUID, PartyGoHereMarker> markersByOwnerCharacter =
            new LinkedHashMap<UUID, PartyGoHereMarker>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();

    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;

    public PartyGoHereMarkerWorldData() {
        this(DATA_NAME);
    }

    public PartyGoHereMarkerWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.markersByOwnerCharacter.clear();
        this.quarantinedEntries.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;

        PartyGoHereMarkerNbtCodec.ReadResult result =
                PartyGoHereMarkerNbtCodec.read(compound);
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
        PartyGoHereMarkerNbtCodec.write(
                compound,
                this.markersByOwnerCharacter.values(),
                this.quarantinedEntries);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized PartyGoHereMarker getMarker(UUID ownerIdentityId) {
        return ownerIdentityId == null ? null
                : this.markersByOwnerCharacter.get(ownerIdentityId);
    }

    public synchronized Collection<PartyGoHereMarker> getMarkers() {
        return Collections.unmodifiableList(
                new ArrayList<PartyGoHereMarker>(
                        this.markersByOwnerCharacter.values()));
    }

    public synchronized boolean saveMarker(PartyGoHereMarker marker) {
        ensureWritable();
        if (marker == null) {
            throw new IllegalArgumentException("marker must not be null");
        }
        PartyGoHereMarker previous = this.markersByOwnerCharacter.put(
                marker.getOwnerIdentityId(), marker);
        boolean changed = !sameMarker(previous, marker);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public synchronized PartyGoHereMarker removeMarker(
            UUID ownerIdentityId) {
        ensureWritable();
        PartyGoHereMarker removed = ownerIdentityId == null ? null
                : this.markersByOwnerCharacter.remove(ownerIdentityId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public synchronized void quarantine(String reason,
                                        PartyGoHereMarker marker) {
        ensureWritable();
        this.quarantinedEntries.add(
                PartyGoHereMarkerNbtCodec.createQuarantineEntry(
                        reason, marker));
        markDirty();
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Party marker data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }

    private static boolean sameMarker(PartyGoHereMarker left,
                                      PartyGoHereMarker right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return equalNullable(left.getPartyId(), right.getPartyId())
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
