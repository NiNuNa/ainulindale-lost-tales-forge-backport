package com.ninuna.losttales.party.storage;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.party.model.PartyGoHereMarker;
import cpw.mods.fml.common.FMLLog;
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

/** Versioned NBT codec for persistent personal party markers. */
public final class PartyGoHereMarkerNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;
    public static final int CURRENT_QUARANTINE_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MARKERS = "Markers";
    private static final String TAG_QUARANTINE = "Quarantine";
    private static final String TAG_ENTRIES = "Entries";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_MARKER_INDEX = "MarkerIndex";
    private static final String TAG_ORIGINAL_DATA = "OriginalData";
    private static final String TAG_PARTY_UUID = "PartyUUID";
    private static final String TAG_OWNER_CHARACTER_UUID = "OwnerCharacterUUID";
    private static final String TAG_DIMENSION_ID = "DimensionId";
    private static final String TAG_X = "X";
    private static final String TAG_Y = "Y";
    private static final String TAG_Z = "Z";
    private static final String TAG_UPDATED_AT = "UpdatedAt";

    private PartyGoHereMarkerNbtCodec() {}

    public static void write(NBTTagCompound output,
                             Collection<PartyGoHereMarker> markers,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);
        ArrayList<PartyGoHereMarker> ordered = new ArrayList<PartyGoHereMarker>();
        if (markers != null) {
            ordered.addAll(markers);
        }
        Collections.sort(ordered, MARKER_ORDER);
        NBTTagList list = new NBTTagList();
        for (PartyGoHereMarker marker : ordered) {
            if (marker != null) {
                list.appendTag(writeMarker(marker));
            }
        }
        output.setTag(TAG_MARKERS, list);
        output.setTag(TAG_QUARANTINE, writeQuarantine(quarantinedEntries));
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_ROOT_DATA_VERSION || version < 0) {
            warn("Party marker data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (safeSource.hasKey(TAG_MARKERS)
                && !safeSource.hasKey(TAG_MARKERS, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safeSource, -1);
        }

        boolean repaired = version != CURRENT_ROOT_DATA_VERSION
                || !safeSource.hasKey(TAG_MARKERS, Constants.NBT.TAG_LIST);
        QuarantineReadResult quarantine = readQuarantine(safeSource);
        if (!quarantine.supported) {
            return ReadResult.unsupported(safeSource, quarantine.unsupportedVersion);
        }
        repaired |= quarantine.repaired;
        ArrayList<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.entries);
        LinkedHashMap<UUID, PartyGoHereMarker> markers =
                new LinkedHashMap<UUID, PartyGoHereMarker>();
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
                quarantinedEntries.add(createQuarantineEntry(
                        markerResult.failureReason, index, raw));
                repaired = true;
                continue;
            }
            UUID ownerCharacterId = markerResult.marker.getOwnerCharacterId();
            PartyGoHereMarker previous = markers.get(ownerCharacterId);
            if (previous != null) {
                PartyGoHereMarker retained = previous.getUpdatedAt()
                        >= markerResult.marker.getUpdatedAt()
                        ? previous : markerResult.marker;
                PartyGoHereMarker discarded = retained == previous
                        ? markerResult.marker : previous;
                markers.put(ownerCharacterId, retained);
                quarantinedEntries.add(createQuarantineEntry(
                        "duplicate_owner_marker", index,
                        writeMarker(discarded)));
                repaired = true;
            } else {
                markers.put(ownerCharacterId, markerResult.marker);
            }
            repaired |= markerResult.repaired;
        }
        return ReadResult.success(markers, repaired, quarantinedEntries);
    }

    public static NBTTagCompound createQuarantineEntry(String reason,
                                                        PartyGoHereMarker marker) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        if (marker != null) {
            if (marker.getPartyId() != null) {
                writeUuid(entry, TAG_PARTY_UUID, marker.getPartyId());
            }
            writeUuid(entry, TAG_OWNER_CHARACTER_UUID,
                    marker.getOwnerCharacterId());
            entry.setInteger(TAG_DIMENSION_ID, marker.getDimensionId());
        }
        return entry;
    }

    private static NBTTagCompound writeMarker(PartyGoHereMarker marker) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, PartyGoHereMarker.CURRENT_DATA_VERSION);
        if (marker.getPartyId() != null) {
            writeUuid(tag, TAG_PARTY_UUID, marker.getPartyId());
        }
        writeUuid(tag, TAG_OWNER_CHARACTER_UUID, marker.getOwnerCharacterId());
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
        if (version > PartyGoHereMarker.CURRENT_DATA_VERSION || version < 0) {
            return MarkerReadResult.unsupported(version);
        }
        UUID partyId = readUuid(source, TAG_PARTY_UUID);
        UUID ownerCharacterId = readUuid(source, TAG_OWNER_CHARACTER_UUID);
        if (ownerCharacterId == null
                || (version < 2 && partyId == null)) {
            return MarkerReadResult.failed("missing_required_identity");
        }
        if (!source.hasKey(TAG_DIMENSION_ID, Constants.NBT.TAG_INT)
                || !source.hasKey(TAG_X, Constants.NBT.TAG_DOUBLE)
                || !source.hasKey(TAG_Y, Constants.NBT.TAG_DOUBLE)
                || !source.hasKey(TAG_Z, Constants.NBT.TAG_DOUBLE)) {
            return MarkerReadResult.failed("missing_coordinates");
        }
        long updatedAt = source.hasKey(TAG_UPDATED_AT, Constants.NBT.TAG_LONG)
                ? source.getLong(TAG_UPDATED_AT) : 0L;
        try {
            return MarkerReadResult.success(new PartyGoHereMarker(
                    partyId,
                    ownerCharacterId,
                    source.getInteger(TAG_DIMENSION_ID),
                    source.getDouble(TAG_X),
                    source.getDouble(TAG_Y),
                    source.getDouble(TAG_Z),
                    updatedAt),
                    version != PartyGoHereMarker.CURRENT_DATA_VERSION
                            || !source.hasKey(TAG_UPDATED_AT,
                            Constants.NBT.TAG_LONG));
        } catch (IllegalArgumentException exception) {
            return MarkerReadResult.failed("invalid_marker_data");
        }
    }

    private static NBTTagCompound writeQuarantine(
            Collection<NBTTagCompound> entries) {
        NBTTagCompound root = new NBTTagCompound();
        root.setInteger(TAG_DATA_VERSION, CURRENT_QUARANTINE_DATA_VERSION);
        NBTTagList list = new NBTTagList();
        if (entries != null) {
            for (NBTTagCompound entry : entries) {
                if (entry != null) {
                    list.appendTag(entry.copy());
                }
            }
        }
        root.setTag(TAG_ENTRIES, list);
        return root;
    }

    private static QuarantineReadResult readQuarantine(NBTTagCompound source) {
        if (!source.hasKey(TAG_QUARANTINE)) {
            return QuarantineReadResult.success(
                    Collections.<NBTTagCompound>emptyList(), true);
        }
        if (!source.hasKey(TAG_QUARANTINE, Constants.NBT.TAG_COMPOUND)) {
            return QuarantineReadResult.unsupported(-1);
        }
        NBTTagCompound root = source.getCompoundTag(TAG_QUARANTINE);
        int version = root.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? root.getInteger(TAG_DATA_VERSION) : 0;
        if (version > CURRENT_QUARANTINE_DATA_VERSION || version < 0) {
            return QuarantineReadResult.unsupported(version);
        }
        if (root.hasKey(TAG_ENTRIES)
                && !root.hasKey(TAG_ENTRIES, Constants.NBT.TAG_LIST)) {
            return QuarantineReadResult.unsupported(-1);
        }
        ArrayList<NBTTagCompound> entries = new ArrayList<NBTTagCompound>();
        NBTTagList list = root.getTagList(
                TAG_ENTRIES, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            entries.add((NBTTagCompound) list.getCompoundTagAt(index).copy());
        }
        return QuarantineReadResult.success(entries,
                version != CURRENT_QUARANTINE_DATA_VERSION
                        || !root.hasKey(TAG_ENTRIES,
                        Constants.NBT.TAG_LIST));
    }

    private static NBTTagCompound createQuarantineEntry(String reason,
                                                         int markerIndex,
                                                         NBTTagCompound original) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        entry.setInteger(TAG_MARKER_INDEX, markerIndex);
        if (original != null) {
            entry.setTag(TAG_ORIGINAL_DATA, original.copy());
        }
        return entry;
    }

    private static void writeUuid(NBTTagCompound tag, String key, UUID value) {
        tag.setLong(key + "Most", value.getMostSignificantBits());
        tag.setLong(key + "Least", value.getLeastSignificantBits());
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        String most = key + "Most";
        String least = key + "Least";
        if (!tag.hasKey(most, Constants.NBT.TAG_LONG)
                || !tag.hasKey(least, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(tag.getLong(most), tag.getLong(least));
    }

    private static void warn(String format, Object... args) {
        FMLLog.warning("[%s] " + format, prependModId(args));
    }

    private static Object[] prependModId(Object[] args) {
        Object[] values = new Object[(args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        return values;
    }

    private static final Comparator<PartyGoHereMarker> MARKER_ORDER =
            new Comparator<PartyGoHereMarker>() {
                @Override
                public int compare(PartyGoHereMarker left,
                                   PartyGoHereMarker right) {
                    return left.getOwnerCharacterId().toString().compareTo(
                            right.getOwnerCharacterId().toString());
                }
            };

    public static final class ReadResult {
        private final Map<UUID, PartyGoHereMarker> markers;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, PartyGoHereMarker> markers,
                           boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly,
                           int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.markers = Collections.unmodifiableMap(
                    new LinkedHashMap<UUID, PartyGoHereMarker>(markers));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(
                Map<UUID, PartyGoHereMarker> markers,
                boolean repaired,
                List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(markers, repaired, quarantineEntries,
                    false, -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original,
                                              int version) {
            return new ReadResult(
                    Collections.<UUID, PartyGoHereMarker>emptyMap(),
                    false,
                    Collections.<NBTTagCompound>emptyList(),
                    true,
                    version,
                    original == null ? new NBTTagCompound()
                            : (NBTTagCompound) original.copy());
        }

        public Map<UUID, PartyGoHereMarker> getMarkers() {
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
        private final PartyGoHereMarker marker;
        private final boolean repaired;
        private final String failureReason;
        private final int unsupportedVersion;

        private MarkerReadResult(PartyGoHereMarker marker,
                                 boolean repaired,
                                 String failureReason,
                                 int unsupportedVersion) {
            this.marker = marker;
            this.repaired = repaired;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static MarkerReadResult success(
                PartyGoHereMarker marker, boolean repaired) {
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

    private static final class QuarantineReadResult {
        private final List<NBTTagCompound> entries;
        private final boolean repaired;
        private final boolean supported;
        private final int unsupportedVersion;

        private QuarantineReadResult(List<NBTTagCompound> entries,
                                     boolean repaired,
                                     boolean supported,
                                     int unsupportedVersion) {
            this.entries = entries;
            this.repaired = repaired;
            this.supported = supported;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static QuarantineReadResult success(
                List<NBTTagCompound> entries, boolean repaired) {
            return new QuarantineReadResult(entries, repaired, true, -1);
        }

        private static QuarantineReadResult unsupported(int version) {
            return new QuarantineReadResult(
                    Collections.<NBTTagCompound>emptyList(),
                    false, false, version);
        }
    }
}
