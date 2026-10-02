package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipGoHereMarker;
import com.ninuna.losttales.storage.NbtQuarantine;
import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Versioned NBT codec for persistent personal fellowship markers. The root
 * and each marker are read only at the version this build writes; any other
 * version keeps the whole store as it is, read-only. A marker that lacks a
 * key this build always writes goes to the quarantine whole.
 */
public final class FellowshipGoHereMarkerNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MARKERS = "Markers";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_MARKER_INDEX = "MarkerIndex";
    private static final String TAG_FELLOWSHIP_UUID = "FellowshipUUID";
    private static final String TAG_OWNER_CHARACTER_UUID = "OwnerCharacterUUID";
    private static final String TAG_DIMENSION_ID = "DimensionId";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_UPDATED_AT = "UpdatedAt";

    private FellowshipGoHereMarkerNbtCodec() {}

    public static void write(NBTTagCompound output,
                             Collection<FellowshipGoHereMarker> markers,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);
        ArrayList<FellowshipGoHereMarker> ordered = new ArrayList<FellowshipGoHereMarker>();
        if (markers != null) {
            ordered.addAll(markers);
        }
        Collections.sort(ordered, MARKER_ORDER);
        NBTTagList list = new NBTTagList();
        for (FellowshipGoHereMarker marker : ordered) {
            if (marker != null) {
                list.appendTag(writeMarker(marker));
            }
        }
        output.setTag(TAG_MARKERS, list);
        NbtQuarantine.write(output, quarantinedEntries);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version != CURRENT_ROOT_DATA_VERSION) {
            LostTalesLog.warning("Fellowship marker data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (safeSource.hasKey(TAG_MARKERS)
                && !safeSource.hasKey(TAG_MARKERS, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safeSource, -1);
        }

        boolean repaired = !safeSource.hasKey(TAG_MARKERS, Constants.NBT.TAG_LIST);
        NbtQuarantine.Read quarantine = NbtQuarantine.readCurrentVersionOnly(safeSource);
        if (!quarantine.isSupported()) {
            return ReadResult.unsupported(safeSource, quarantine.getUnsupportedVersion());
        }
        repaired |= quarantine.isRepaired();
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.getEntries());
        LinkedHashMap<UUID, FellowshipGoHereMarker> markers =
                new LinkedHashMap<UUID, FellowshipGoHereMarker>();
        NBTTagList list = safeSource.getTagList(
                TAG_MARKERS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            MarkerReadResult markerResult = readMarker(raw);
            if (markerResult.unsupportedVersion != Integer.MIN_VALUE) {
                return ReadResult.unsupported(
                        safeSource, markerResult.unsupportedVersion);
            }
            if (markerResult.marker == null) {
                quarantinedEntries.add(NbtQuarantine.entry(
                        markerResult.failureReason, TAG_MARKER_INDEX, index, raw));
                repaired = true;
                continue;
            }
            UUID ownerIdentityId = markerResult.marker.getOwnerIdentityId();
            FellowshipGoHereMarker previous = markers.get(ownerIdentityId);
            if (previous != null) {
                FellowshipGoHereMarker retained = previous.getUpdatedAt()
                        >= markerResult.marker.getUpdatedAt()
                        ? previous : markerResult.marker;
                FellowshipGoHereMarker discarded = retained == previous
                        ? markerResult.marker : previous;
                markers.put(ownerIdentityId, retained);
                quarantinedEntries.add(NbtQuarantine.entry(
                        "duplicate_owner_marker", TAG_MARKER_INDEX, index,
                        writeMarker(discarded)));
                repaired = true;
            } else {
                markers.put(ownerIdentityId, markerResult.marker);
            }
            repaired |= markerResult.repaired;
        }
        return ReadResult.success(markers, repaired, quarantinedEntries);
    }

    public static NBTTagCompound createQuarantineEntry(String reason,
                                                        FellowshipGoHereMarker marker) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (marker != null) {
            if (marker.getFellowshipId() != null) {
                NbtTags.writeUuid(entry, TAG_FELLOWSHIP_UUID, marker.getFellowshipId());
            }
            NbtTags.writeUuid(entry, TAG_OWNER_CHARACTER_UUID,
                    marker.getOwnerIdentityId());
            entry.setInteger(TAG_DIMENSION_ID, marker.getDimensionId());
        }
        return entry;
    }

    private static NBTTagCompound writeMarker(FellowshipGoHereMarker marker) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, FellowshipGoHereMarker.CURRENT_DATA_VERSION);
        if (marker.getFellowshipId() != null) {
            NbtTags.writeUuid(tag, TAG_FELLOWSHIP_UUID, marker.getFellowshipId());
        }
        NbtTags.writeUuid(tag, TAG_OWNER_CHARACTER_UUID, marker.getOwnerIdentityId());
        tag.setInteger(TAG_DIMENSION_ID, marker.getDimensionId());
        tag.setDouble(TAG_X, marker.getX());
        tag.setDouble(TAG_Y, marker.getY());
        tag.setDouble(TAG_Z, marker.getZ());
        tag.setLong(TAG_UPDATED_AT, marker.getUpdatedAt());
        return tag;
    }

    private static MarkerReadResult readMarker(NBTTagCompound source) {
        if (source == null) {
            return MarkerReadResult.failed("missing_marker");
        }
        int version = source.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? source.getInteger(TAG_DATA_VERSION) : 0;
        if (version != FellowshipGoHereMarker.CURRENT_DATA_VERSION) {
            return MarkerReadResult.unsupported(version);
        }
        // A marker without a fellowship is its character's own.
        UUID fellowshipId = NbtTags.readUuid(source, TAG_FELLOWSHIP_UUID);
        UUID ownerIdentityId = NbtTags.readUuid(source, TAG_OWNER_CHARACTER_UUID);
        if (ownerIdentityId == null) {
            return MarkerReadResult.failed("missing_required_identity");
        }
        if (!source.hasKey(TAG_DIMENSION_ID, Constants.NBT.TAG_INT)
                || !source.hasKey(TAG_X, Constants.NBT.TAG_DOUBLE)
                || !source.hasKey(TAG_Y, Constants.NBT.TAG_DOUBLE)
                || !source.hasKey(TAG_Z, Constants.NBT.TAG_DOUBLE)) {
            return MarkerReadResult.failed("missing_coordinates");
        }
        if (!source.hasKey(TAG_UPDATED_AT, Constants.NBT.TAG_LONG)
                || source.getLong(TAG_UPDATED_AT) < 0L) {
            return MarkerReadResult.failed("missing_or_invalid_updated_at");
        }
        try {
            return MarkerReadResult.success(new FellowshipGoHereMarker(
                    fellowshipId,
                    ownerIdentityId,
                    source.getInteger(TAG_DIMENSION_ID),
                    source.getDouble(TAG_X),
                    source.getDouble(TAG_Y),
                    source.getDouble(TAG_Z),
                    source.getLong(TAG_UPDATED_AT)),
                    false);
        } catch (IllegalArgumentException exception) {
            return MarkerReadResult.failed("invalid_marker_data");
        }
    }

    private static final Comparator<FellowshipGoHereMarker> MARKER_ORDER =
            new Comparator<FellowshipGoHereMarker>() {
                @Override
                public int compare(FellowshipGoHereMarker left,
                                   FellowshipGoHereMarker right) {
                    return left.getOwnerIdentityId().toString().compareTo(
                            right.getOwnerIdentityId().toString());
                }
            };

    public static final class ReadResult {
        private final Map<UUID, FellowshipGoHereMarker> markers;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, FellowshipGoHereMarker> markers,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly,
                           int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.markers = Collections.unmodifiableMap(
                    new LinkedHashMap<UUID, FellowshipGoHereMarker>(markers));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(
                Map<UUID, FellowshipGoHereMarker> markers,
                boolean repaired,
                List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(markers, repaired, quarantineEntries,
                    false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original,
                                              int version) {
            return new ReadResult(
                    Collections.<UUID, FellowshipGoHereMarker>emptyMap(),
                    false,
                    Collections.<NBTTagCompound>emptyList(),
                    true,
                    version,
                    original == null ? new NBTTagCompound()
                            : (NBTTagCompound) original.copy());
        }

        public Map<UUID, FellowshipGoHereMarker> getMarkers() {
            return this.markers;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public List<NBTTagCompound> getQuarantineEntriesCopy() {
            ArrayList<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
            for (NBTTagCompound entry : this.quarantineEntries) {
                copies.add((NBTTagCompound) entry.copy());
            }
            return Collections.unmodifiableList(copies);
        }

        public boolean isReadOnly() {
            return this.readOnly;
        }

        public int getUnsupportedVersion() {
            return this.unsupportedVersion;
        }

        public NBTTagCompound getOriginalDataCopy() {
            return this.originalData == null ? null
                    : (NBTTagCompound) this.originalData.copy();
        }
    }

    private static final class MarkerReadResult {
        private final FellowshipGoHereMarker marker;
        private final boolean repaired;
        private final String failureReason;
        private final int unsupportedVersion;

        private MarkerReadResult(FellowshipGoHereMarker marker,
                                 boolean repaired,
                                 String failureReason,
                                 int unsupportedVersion) {
            this.marker = marker;
            this.repaired = repaired;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static MarkerReadResult success(
                FellowshipGoHereMarker marker, boolean repaired) {
            return new MarkerReadResult(
                    marker, repaired, null, Integer.MIN_VALUE);
        }

        private static MarkerReadResult failed(String reason) {
            return new MarkerReadResult(
                    null, false, reason, Integer.MIN_VALUE);
        }

        private static MarkerReadResult unsupported(int version) {
            return new MarkerReadResult(null, false, null, version);
        }
    }
}
