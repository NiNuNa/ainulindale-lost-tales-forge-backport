package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.party.model.PartyGoHereMarker;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.WorldSavedData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Persistent one-marker-per-character party marker store. */
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
            copyTagContents(this.preservedNewerData, compound);
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

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public synchronized PartyGoHereMarker getMarker(UUID ownerCharacterId) {
        return ownerCharacterId == null ? null
                : this.markersByOwnerCharacter.get(ownerCharacterId);
    }

    public synchronized Collection<PartyGoHereMarker> getMarkers() {
        return Collections.unmodifiableList(
                new ArrayList<PartyGoHereMarker>(
                        this.markersByOwnerCharacter.values()));
    }

    public synchronized List<PartyGoHereMarker> getMarkersForParty(
            UUID partyId) {
        ArrayList<PartyGoHereMarker> markers =
                new ArrayList<PartyGoHereMarker>();
        if (partyId != null) {
            for (PartyGoHereMarker marker
                    : this.markersByOwnerCharacter.values()) {
                if (partyId.equals(marker.getPartyId())) {
                    markers.add(marker);
                }
            }
        }
        return Collections.unmodifiableList(markers);
    }

    public synchronized boolean saveMarker(PartyGoHereMarker marker) {
        ensureWritable();
        if (marker == null) {
            throw new IllegalArgumentException("marker must not be null");
        }
        PartyGoHereMarker previous = this.markersByOwnerCharacter.put(
                marker.getOwnerCharacterId(), marker);
        boolean changed = !sameMarker(previous, marker);
        if (changed) {
            markDirty();
        }
        return changed;
    }

    public synchronized PartyGoHereMarker removeMarker(
            UUID ownerCharacterId) {
        ensureWritable();
        PartyGoHereMarker removed = ownerCharacterId == null ? null
                : this.markersByOwnerCharacter.remove(ownerCharacterId);
        if (removed != null) {
            markDirty();
        }
        return removed;
    }

    public synchronized int removeMarkersForParty(UUID partyId) {
        ensureWritable();
        if (partyId == null) {
            return 0;
        }
        ArrayList<UUID> owners = new ArrayList<UUID>();
        for (PartyGoHereMarker marker
                : this.markersByOwnerCharacter.values()) {
            if (partyId.equals(marker.getPartyId())) {
                owners.add(marker.getOwnerCharacterId());
            }
        }
        int removed = 0;
        for (UUID owner : owners) {
            if (this.markersByOwnerCharacter.remove(owner) != null) {
                removed++;
            }
        }
        if (removed > 0) {
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
                && left.getOwnerCharacterId().equals(
                right.getOwnerCharacterId())
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
