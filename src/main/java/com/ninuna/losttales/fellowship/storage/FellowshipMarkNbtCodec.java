package com.ninuna.losttales.fellowship.storage;

import com.ninuna.losttales.fellowship.model.FellowshipMark;
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
 * Versioned NBT codec for fellowships' marks. The root and each mark are
 * read only at the version this build writes; any other version keeps the
 * whole store as it is, read-only. A mark that lacks a key this build
 * always writes, or one past the most a fellowship holds, goes to the
 * quarantine whole.
 */
public final class FellowshipMarkNbtCodec {

    public static final int CURRENT_ROOT_DATA_VERSION = 1;

    private static final String TAG_DATA_VERSION = "DataVersion";
    private static final String TAG_MARKS = "Marks";
    private static final String TAG_REASON = "Reason";
    private static final String TAG_MARK_INDEX = "MarkIndex";
    private static final String TAG_MARK_UUID = "MarkUUID";
    private static final String TAG_FELLOWSHIP_UUID = "FellowshipUUID";
    private static final String TAG_NAME = "Name";
    private static final String TAG_PLACED_BY = "PlacedByCharacterUUID";
    private static final String TAG_DIMENSION_ID = "DimensionId";
    private static final String TAG_X = "X";
    private static final String TAG_Z = "Z";
    private static final String TAG_PLACED_AT = "PlacedAt";

    private FellowshipMarkNbtCodec() {}

    public static void write(NBTTagCompound output,
                             Collection<FellowshipMark> marks,
                             Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(TAG_DATA_VERSION, CURRENT_ROOT_DATA_VERSION);
        List<FellowshipMark> ordered = new ArrayList<FellowshipMark>();
        if (marks != null) {
            ordered.addAll(marks);
        }
        Collections.sort(ordered, ORDER);
        NBTTagList list = new NBTTagList();
        for (FellowshipMark mark : ordered) {
            if (mark != null) {
                list.appendTag(writeMark(mark));
            }
        }
        output.setTag(TAG_MARKS, list);
        NbtQuarantine.write(output, quarantinedEntries);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safeSource = source == null ? new NBTTagCompound() : source;
        int version = safeSource.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                ? safeSource.getInteger(TAG_DATA_VERSION) : 0;
        if (version != CURRENT_ROOT_DATA_VERSION) {
            LostTalesLog.warning("Fellowship mark data uses unsupported version %d; data will remain read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safeSource, version);
        }
        if (safeSource.hasKey(TAG_MARKS)
                && !safeSource.hasKey(TAG_MARKS, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safeSource, -1);
        }
        boolean repaired = !safeSource.hasKey(TAG_MARKS, Constants.NBT.TAG_LIST);
        NbtQuarantine.Read quarantine = NbtQuarantine.readCurrentVersionOnly(safeSource);
        if (!quarantine.isSupported()) {
            return ReadResult.unsupported(safeSource, quarantine.getUnsupportedVersion());
        }
        repaired |= quarantine.isRepaired();
        List<NBTTagCompound> quarantinedEntries =
                new ArrayList<NBTTagCompound>(quarantine.getEntries());
        Map<UUID, FellowshipMark> marks = new LinkedHashMap<UUID, FellowshipMark>();
        Map<UUID, Integer> perFellowship = new LinkedHashMap<UUID, Integer>();
        NBTTagList list = safeSource.getTagList(TAG_MARKS, Constants.NBT.TAG_COMPOUND);
        for (int index = 0; index < list.tagCount(); index++) {
            NBTTagCompound raw = list.getCompoundTagAt(index);
            int markVersion = raw.hasKey(TAG_DATA_VERSION, Constants.NBT.TAG_INT)
                    ? raw.getInteger(TAG_DATA_VERSION) : 0;
            if (markVersion != FellowshipMark.CURRENT_DATA_VERSION) {
                return ReadResult.unsupported(safeSource, markVersion);
            }
            String failure = null;
            FellowshipMark mark = null;
            try {
                mark = readMark(raw);
            } catch (IllegalArgumentException invalid) {
                failure = "invalid_mark_data";
            }
            if (mark == null && failure == null) {
                failure = "missing_mark_key";
            }
            if (mark != null && marks.containsKey(mark.getMarkId())) {
                failure = "duplicate_mark";
            }
            Integer held = mark == null ? null : perFellowship.get(mark.getFellowshipId());
            if (failure == null && held != null
                    && held.intValue() >= FellowshipMark.MAX_PER_FELLOWSHIP) {
                failure = "too_many_marks";
            }
            if (failure != null) {
                quarantinedEntries.add(NbtQuarantine.entry(failure,
                        TAG_MARK_INDEX, index, raw));
                repaired = true;
                continue;
            }
            marks.put(mark.getMarkId(), mark);
            perFellowship.put(mark.getFellowshipId(),
                    Integer.valueOf(held == null ? 1 : held.intValue() + 1));
        }
        return ReadResult.success(marks, repaired, quarantinedEntries);
    }

    public static NBTTagCompound createQuarantineEntry(String reason,
                                                        FellowshipMark mark) {
        NBTTagCompound entry = mark == null ? new NBTTagCompound() : writeMark(mark);
        entry.setString(TAG_REASON, reason == null ? "unknown" : reason);
        return entry;
    }

    private static NBTTagCompound writeMark(FellowshipMark mark) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(TAG_DATA_VERSION, FellowshipMark.CURRENT_DATA_VERSION);
        NbtTags.writeUuid(tag, TAG_MARK_UUID, mark.getMarkId());
        NbtTags.writeUuid(tag, TAG_FELLOWSHIP_UUID, mark.getFellowshipId());
        tag.setString(TAG_NAME, mark.getName());
        NbtTags.writeUuid(tag, TAG_PLACED_BY, mark.getPlacedBy());
        tag.setInteger(TAG_DIMENSION_ID, mark.getDimensionId());
        tag.setDouble(TAG_X, mark.getX());
        tag.setDouble(TAG_Z, mark.getZ());
        tag.setLong(TAG_PLACED_AT, mark.getPlacedAt());
        return tag;
    }

    /** The mark a tag holds; null where a key this build always writes is missing. */
    private static FellowshipMark readMark(NBTTagCompound source) {
        UUID markId = NbtTags.readUuid(source, TAG_MARK_UUID);
        UUID fellowshipId = NbtTags.readUuid(source, TAG_FELLOWSHIP_UUID);
        UUID placedBy = NbtTags.readUuid(source, TAG_PLACED_BY);
        if (markId == null || fellowshipId == null || placedBy == null
                || !source.hasKey(TAG_NAME, Constants.NBT.TAG_STRING)
                || !source.hasKey(TAG_DIMENSION_ID, Constants.NBT.TAG_INT)
                || !source.hasKey(TAG_X, Constants.NBT.TAG_DOUBLE)
                || !source.hasKey(TAG_Z, Constants.NBT.TAG_DOUBLE)
                || !source.hasKey(TAG_PLACED_AT, Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new FellowshipMark(markId, fellowshipId,
                source.getString(TAG_NAME), placedBy,
                source.getInteger(TAG_DIMENSION_ID), source.getDouble(TAG_X),
                source.getDouble(TAG_Z), source.getLong(TAG_PLACED_AT));
    }

    /** A fellowship's marks together, the oldest first. */
    private static final Comparator<FellowshipMark> ORDER =
            new Comparator<FellowshipMark>() {
                @Override
                public int compare(FellowshipMark left, FellowshipMark right) {
                    int byFellowship = left.getFellowshipId().toString().compareTo(
                            right.getFellowshipId().toString());
                    if (byFellowship != 0) {
                        return byFellowship;
                    }
                    return left.getPlacedAt() != right.getPlacedAt()
                            ? (left.getPlacedAt() < right.getPlacedAt() ? -1 : 1)
                            : left.getMarkId().toString().compareTo(
                                    right.getMarkId().toString());
                }
            };

    public static final class ReadResult {
        private final Map<UUID, FellowshipMark> marks;
        private final boolean repaired;
        private final List<NBTTagCompound> quarantineEntries;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound originalData;

        private ReadResult(Map<UUID, FellowshipMark> marks, boolean repaired,
                           List<NBTTagCompound> quarantineEntries,
                           boolean readOnly, int unsupportedVersion,
                           NBTTagCompound originalData) {
            this.marks = Collections.unmodifiableMap(
                    new LinkedHashMap<UUID, FellowshipMark>(marks));
            this.repaired = repaired;
            this.quarantineEntries = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantineEntries));
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.originalData = originalData;
        }

        private static ReadResult success(Map<UUID, FellowshipMark> marks,
                                          boolean repaired,
                                          List<NBTTagCompound> quarantineEntries) {
            return new ReadResult(marks, repaired, quarantineEntries, false,
                    -1, null);
        }

        private static ReadResult unsupported(NBTTagCompound original,
                                              int version) {
            return new ReadResult(Collections.<UUID, FellowshipMark>emptyMap(),
                    false, Collections.<NBTTagCompound>emptyList(), true,
                    version, original == null ? new NBTTagCompound()
                            : (NBTTagCompound) original.copy());
        }

        public Map<UUID, FellowshipMark> getMarks() {
            return this.marks;
        }

        public boolean wasRepaired() {
            return this.repaired;
        }

        public List<NBTTagCompound> getQuarantineEntriesCopy() {
            List<NBTTagCompound> copies = new ArrayList<NBTTagCompound>();
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
}
