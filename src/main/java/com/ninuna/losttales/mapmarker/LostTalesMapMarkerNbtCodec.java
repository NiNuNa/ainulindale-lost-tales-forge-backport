package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.LostTalesMetaData;
import cpw.mods.fml.common.FMLLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;

/** Versioned, defensive NBT codec for authoritative marker records. */
public final class LostTalesMapMarkerNbtCodec {
    public static final int CURRENT_DATA_VERSION = 3;
    private static final int MAX_RECORDS = 16384;
    private static final int MAX_QUARANTINE = 4096;

    private static final String DATA_VERSION = "DataVersion";
    private static final String MARKERS = "Markers";
    private static final String QUARANTINE = "Quarantine";

    private LostTalesMapMarkerNbtCodec() {}

    public static void write(
            NBTTagCompound output,
            Collection<LostTalesMapMarkerRecord> records,
            Collection<NBTTagCompound> quarantinedEntries) {
        output.setInteger(DATA_VERSION, CURRENT_DATA_VERSION);
        ArrayList<LostTalesMapMarkerRecord> ordered =
                new ArrayList<LostTalesMapMarkerRecord>();
        if (records != null) {
            ordered.addAll(records);
        }
        Collections.sort(ordered,
                new Comparator<LostTalesMapMarkerRecord>() {
                    @Override
                    public int compare(LostTalesMapMarkerRecord left,
                                       LostTalesMapMarkerRecord right) {
                        return left.getId().compareTo(right.getId());
                    }
                });
        NBTTagList markerList = new NBTTagList();
        for (LostTalesMapMarkerRecord record : ordered) {
            if (record != null) {
                markerList.appendTag(writeRecord(record));
            }
        }
        output.setTag(MARKERS, markerList);

        NBTTagList quarantine = new NBTTagList();
        if (quarantinedEntries != null) {
            int count = 0;
            for (NBTTagCompound entry : quarantinedEntries) {
                if (entry != null && count++ < MAX_QUARANTINE) {
                    quarantine.appendTag(entry.copy());
                }
            }
        }
        output.setTag(QUARANTINE, quarantine);
    }

    public static ReadResult read(NBTTagCompound source) {
        NBTTagCompound safe = source == null
                ? new NBTTagCompound() : source;
        int version = safe.hasKey(DATA_VERSION, Constants.NBT.TAG_INT)
                ? safe.getInteger(DATA_VERSION) : 0;
        if (version < 0 || version > CURRENT_DATA_VERSION) {
            warn("Map marker data uses unsupported version %d; preserving it read-only",
                    Integer.valueOf(version));
            return ReadResult.unsupported(safe, version);
        }
        if (safe.hasKey(MARKERS)
                && !safe.hasKey(MARKERS, Constants.NBT.TAG_LIST)) {
            return ReadResult.unsupported(safe, -1);
        }

        LinkedHashMap<String, LostTalesMapMarkerRecord> records =
                new LinkedHashMap<String, LostTalesMapMarkerRecord>();
        ArrayList<NBTTagCompound> quarantine = readQuarantine(safe);
        boolean repaired = version != CURRENT_DATA_VERSION;
        NBTTagList markers = safe.getTagList(
                MARKERS, Constants.NBT.TAG_COMPOUND);
        int count = Math.min(markers.tagCount(), MAX_RECORDS);
        if (markers.tagCount() > MAX_RECORDS) {
            repaired = true;
            quarantine.add(quarantine(
                    "record_limit_exceeded", null, null));
        }
        for (int index = 0; index < count; index++) {
            NBTTagCompound raw = markers.getCompoundTagAt(index);
            RecordReadResult result = readRecord(raw);
            if (result.unsupportedVersion != Integer.MIN_VALUE) {
                return ReadResult.unsupported(
                        safe, result.unsupportedVersion);
            }
            if (result.record == null) {
                quarantine.add(quarantine(
                        result.failureReason, Integer.valueOf(index), raw));
                repaired = true;
                continue;
            }
            LostTalesMapMarkerRecord previous =
                    records.get(result.record.getId());
            if (previous == null) {
                records.put(result.record.getId(), result.record);
            } else {
                LostTalesMapMarkerRecord retained =
                        previous.getRevision() >= result.record.getRevision()
                                ? previous : result.record;
                LostTalesMapMarkerRecord discarded =
                        retained == previous ? result.record : previous;
                records.put(retained.getId(), retained);
                quarantine.add(quarantine(
                        "duplicate_marker_id", Integer.valueOf(index),
                        writeRecord(discarded)));
                repaired = true;
            }
        }
        trimQuarantine(quarantine);
        return ReadResult.success(records, quarantine, repaired);
    }

    private static NBTTagCompound writeRecord(
            LostTalesMapMarkerRecord record) {
        NBTTagCompound tag = new NBTTagCompound();
        tag.setInteger(DATA_VERSION, CURRENT_DATA_VERSION);
        tag.setString("Id", record.getId());
        tag.setString("Source", record.getSource().getSerializedName());
        tag.setString("Name", record.getName());
        tag.setString("Icon", record.getIconName());
        tag.setString("Color", record.getColorName());
        tag.setString("Category", record.getCategoryName());
        tag.setString("Description", record.getDescription());
        tag.setBoolean("FastTravel", record.hasFastTravel());
        tag.setInteger("Dimension", record.getDimensionId());
        tag.setDouble("X", record.getX());
        tag.setDouble("Y", record.getY());
        tag.setDouble("Z", record.getZ());
        tag.setDouble("CompassRadius", record.getCompassFadeInRadius());
        tag.setDouble("DiscoveryRadius", record.getDiscoveryRadius());
        tag.setBoolean("Hidden", record.isHiddenUntilDiscovered());
        tag.setBoolean("Discoverable", record.isDiscoverable());
        tag.setBoolean("RequiresRegion", record.requiresRegionUnlock());
        tag.setBoolean("HasWaystone", record.hasWaystone());
        tag.setString("StructureType", record.getWaystoneStructureType());
        writeUuid(tag, "Owner", record.getOwnerPlayerId());
        tag.setString("Visibility",
                record.getVisibility().getSerializedName());
        NBTTagList shared = new NBTTagList();
        for (UUID playerId : record.getSharedPlayerIds()) {
            NBTTagCompound entry = new NBTTagCompound();
            writeUuid(entry, "Player", playerId);
            shared.appendTag(entry);
        }
        tag.setTag("SharedPlayers", shared);
        NBTTagList sharedFellowships = new NBTTagList();
        for (UUID fellowshipId : record.getSharedFellowshipIds()) {
            NBTTagCompound entry = new NBTTagCompound();
            writeUuid(entry, "Fellowship", fellowshipId);
            sharedFellowships.appendTag(entry);
        }
        tag.setTag("SharedFellowships", sharedFellowships);
        tag.setString("GenerationState",
                record.getGenerationState().getSerializedName());
        tag.setString("GenerationMessage", record.getGenerationMessage());
        tag.setBoolean("Linked", record.isLinked());
        if (record.isLinked()) {
            tag.setInteger("LinkedDimension", record.getLinkedDimensionId());
            tag.setInteger("LinkedX", record.getLinkedX());
            tag.setInteger("LinkedY", record.getLinkedY());
            tag.setInteger("LinkedZ", record.getLinkedZ());
            writeUuid(tag, "LinkToken", record.getLinkToken());
        }
        tag.setLong("Revision", record.getRevision());
        return tag;
    }

    private static RecordReadResult readRecord(NBTTagCompound tag) {
        if (tag == null) {
            return RecordReadResult.failed("missing_record");
        }
        int version = tag.hasKey(DATA_VERSION, Constants.NBT.TAG_INT)
                ? tag.getInteger(DATA_VERSION) : 0;
        if (version < 0 || version > CURRENT_DATA_VERSION) {
            return RecordReadResult.unsupported(version);
        }
        if (!tag.hasKey("Id", Constants.NBT.TAG_STRING)
                || !tag.hasKey("Name", Constants.NBT.TAG_STRING)
                || !tag.hasKey("Dimension", Constants.NBT.TAG_INT)
                || !tag.hasKey("X", Constants.NBT.TAG_DOUBLE)
                || !tag.hasKey("Y", Constants.NBT.TAG_DOUBLE)
                || !tag.hasKey("Z", Constants.NBT.TAG_DOUBLE)) {
            return RecordReadResult.failed("missing_required_fields");
        }
        try {
            LostTalesMapMarkerSource source =
                    LostTalesMapMarkerSource.forSerializedName(
                            tag.getString("Source"),
                            LostTalesMapMarkerSource.QUEST_DYNAMIC);
            boolean hasWaystone = tag.getBoolean("HasWaystone");
            LostTalesMapMarkerRecord.Builder builder =
                    LostTalesMapMarkerRecord.builder(
                                    tag.getString("Id"), source)
                            .name(tag.getString("Name"))
                            .iconName(tag.getString("Icon"))
                            .colorName(tag.getString("Color"))
                            .categoryName(tag.getString("Category"))
                            .description(tag.getString("Description"))
                            .fastTravel(tag.getBoolean("FastTravel"))
                            .position(tag.getInteger("Dimension"),
                                    tag.getDouble("X"), tag.getDouble("Y"),
                                    tag.getDouble("Z"))
                            .radii(tag.hasKey("CompassRadius",
                                            Constants.NBT.TAG_DOUBLE)
                                            ? tag.getDouble("CompassRadius")
                                            : 128.0D,
                                    tag.hasKey("DiscoveryRadius",
                                            Constants.NBT.TAG_DOUBLE)
                                            ? tag.getDouble("DiscoveryRadius")
                                            : 8.0D)
                            .discovery(tag.getBoolean("Hidden"),
                                    !tag.hasKey("Discoverable")
                                            || tag.getBoolean("Discoverable"),
                                    tag.getBoolean("RequiresRegion"))
                            .waystone(hasWaystone,
                                    tag.getString("StructureType"))
                            .ownerPlayerId(readUuid(tag, "Owner"))
                            .visibility(
                                    LostTalesMapMarkerVisibility.forSerializedName(
                                            tag.getString("Visibility"),
                                            source == LostTalesMapMarkerSource.PLAYER_CREATED
                                                    ? LostTalesMapMarkerVisibility.PRIVATE
                                                    : LostTalesMapMarkerVisibility.PUBLIC))
                            .sharedPlayerIds(readSharedPlayers(tag))
                            .sharedFellowshipIds(
                                    readSharedFellowships(tag))
                            .generationState(
                                    LostTalesWaystoneGenerationState.forSerializedName(
                                            tag.getString("GenerationState"),
                                            hasWaystone
                                                    ? LostTalesWaystoneGenerationState.NOT_ATTEMPTED
                                                    : LostTalesWaystoneGenerationState.DISABLED),
                                    tag.getString("GenerationMessage"))
                            .revision(tag.hasKey("Revision",
                                            Constants.NBT.TAG_LONG)
                                            ? tag.getLong("Revision") : 1L);
            if (tag.getBoolean("Linked")) {
                UUID token = readUuid(tag, "LinkToken");
                if (token == null
                        || !tag.hasKey("LinkedDimension",
                                Constants.NBT.TAG_INT)
                        || !tag.hasKey("LinkedX", Constants.NBT.TAG_INT)
                        || !tag.hasKey("LinkedY", Constants.NBT.TAG_INT)
                        || !tag.hasKey("LinkedZ", Constants.NBT.TAG_INT)) {
                    return RecordReadResult.failed("invalid_waystone_link");
                }
                builder.link(tag.getInteger("LinkedDimension"),
                        tag.getInteger("LinkedX"),
                        tag.getInteger("LinkedY"),
                        tag.getInteger("LinkedZ"), token);
            }
            return RecordReadResult.success(builder.build());
        } catch (RuntimeException exception) {
            return RecordReadResult.failed("invalid_record_data");
        }
    }

    private static Set<UUID> readSharedPlayers(NBTTagCompound tag) {
        LinkedHashSet<UUID> players = new LinkedHashSet<UUID>();
        if (!tag.hasKey("SharedPlayers", Constants.NBT.TAG_LIST)) {
            return players;
        }
        NBTTagList list = tag.getTagList(
                "SharedPlayers", Constants.NBT.TAG_COMPOUND);
        int count = Math.min(
                list.tagCount(), LostTalesMapMarkerRecord.MAX_SHARED_PLAYERS);
        for (int index = 0; index < count; index++) {
            UUID playerId = readUuid(
                    list.getCompoundTagAt(index), "Player");
            if (playerId != null) {
                players.add(playerId);
            }
        }
        return players;
    }

    private static Set<UUID> readSharedFellowships(
            NBTTagCompound tag) {
        LinkedHashSet<UUID> fellowships =
                new LinkedHashSet<UUID>();
        if (!tag.hasKey(
                "SharedFellowships", Constants.NBT.TAG_LIST)) {
            return fellowships;
        }
        NBTTagList list = tag.getTagList(
                "SharedFellowships",
                Constants.NBT.TAG_COMPOUND);
        int count = Math.min(
                list.tagCount(),
                LostTalesMapMarkerRecord
                        .MAX_SHARED_FELLOWSHIPS);
        for (int index = 0; index < count; index++) {
            UUID fellowshipId = readUuid(
                    list.getCompoundTagAt(index),
                    "Fellowship");
            if (fellowshipId != null) {
                fellowships.add(fellowshipId);
            }
        }
        return fellowships;
    }

    private static ArrayList<NBTTagCompound> readQuarantine(
            NBTTagCompound source) {
        ArrayList<NBTTagCompound> entries =
                new ArrayList<NBTTagCompound>();
        if (!source.hasKey(QUARANTINE, Constants.NBT.TAG_LIST)) {
            return entries;
        }
        NBTTagList list = source.getTagList(
                QUARANTINE, Constants.NBT.TAG_COMPOUND);
        int count = Math.min(list.tagCount(), MAX_QUARANTINE);
        for (int index = 0; index < count; index++) {
            entries.add((NBTTagCompound)
                    list.getCompoundTagAt(index).copy());
        }
        return entries;
    }

    private static NBTTagCompound quarantine(
            String reason, Integer index, NBTTagCompound original) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Reason", reason == null ? "unknown" : reason);
        if (index != null) {
            entry.setInteger("MarkerIndex", index.intValue());
        }
        if (original != null) {
            entry.setTag("OriginalData", original.copy());
        }
        return entry;
    }

    private static void trimQuarantine(List<NBTTagCompound> entries) {
        while (entries.size() > MAX_QUARANTINE) {
            entries.remove(0);
        }
    }

    private static void writeUuid(
            NBTTagCompound tag, String key, UUID value) {
        if (value != null) {
            tag.setLong(key + "Most", value.getMostSignificantBits());
            tag.setLong(key + "Least", value.getLeastSignificantBits());
        }
    }

    private static UUID readUuid(NBTTagCompound tag, String key) {
        if (!tag.hasKey(key + "Most", Constants.NBT.TAG_LONG)
                || !tag.hasKey(key + "Least", Constants.NBT.TAG_LONG)) {
            return null;
        }
        return new UUID(
                tag.getLong(key + "Most"), tag.getLong(key + "Least"));
    }

    private static void warn(String format, Object... args) {
        Object[] values = new Object[
                (args == null ? 0 : args.length) + 1];
        values[0] = LostTalesMetaData.MOD_ID;
        if (args != null) {
            System.arraycopy(args, 0, values, 1, args.length);
        }
        try {
            FMLLog.warning("[%s] " + format, values);
        } catch (RuntimeException ignored) {
            // FML's logger is not bootstrapped in isolated codec unit tests.
        }
    }

    public static final class ReadResult {
        private final Map<String, LostTalesMapMarkerRecord> records;
        private final List<NBTTagCompound> quarantine;
        private final boolean repaired;
        private final boolean readOnly;
        private final int unsupportedVersion;
        private final NBTTagCompound original;

        private ReadResult(
                Map<String, LostTalesMapMarkerRecord> records,
                List<NBTTagCompound> quarantine,
                boolean repaired, boolean readOnly,
                int unsupportedVersion, NBTTagCompound original) {
            this.records = Collections.unmodifiableMap(
                    new LinkedHashMap<String, LostTalesMapMarkerRecord>(
                            records));
            this.quarantine = Collections.unmodifiableList(
                    new ArrayList<NBTTagCompound>(quarantine));
            this.repaired = repaired;
            this.readOnly = readOnly;
            this.unsupportedVersion = unsupportedVersion;
            this.original = original;
        }

        private static ReadResult success(
                Map<String, LostTalesMapMarkerRecord> records,
                List<NBTTagCompound> quarantine, boolean repaired) {
            return new ReadResult(records, quarantine, repaired,
                    false, -1, null);
        }

        private static ReadResult unsupported(
                NBTTagCompound source, int version) {
            return new ReadResult(
                    Collections.<String, LostTalesMapMarkerRecord>emptyMap(),
                    Collections.<NBTTagCompound>emptyList(),
                    false, true, version,
                    (NBTTagCompound)source.copy());
        }

        public Map<String, LostTalesMapMarkerRecord> getRecords() {
            return this.records;
        }

        public List<NBTTagCompound> getQuarantineCopy() {
            ArrayList<NBTTagCompound> copies =
                    new ArrayList<NBTTagCompound>();
            for (NBTTagCompound entry : this.quarantine) {
                copies.add((NBTTagCompound)entry.copy());
            }
            return copies;
        }

        public boolean wasRepaired() { return this.repaired; }
        public boolean isReadOnly() { return this.readOnly; }
        public int getUnsupportedVersion() { return this.unsupportedVersion; }
        public NBTTagCompound getOriginalCopy() {
            return this.original == null ? null
                    : (NBTTagCompound)this.original.copy();
        }
    }

    private static final class RecordReadResult {
        private final LostTalesMapMarkerRecord record;
        private final String failureReason;
        private final int unsupportedVersion;

        private RecordReadResult(
                LostTalesMapMarkerRecord record,
                String failureReason, int unsupportedVersion) {
            this.record = record;
            this.failureReason = failureReason;
            this.unsupportedVersion = unsupportedVersion;
        }

        private static RecordReadResult success(
                LostTalesMapMarkerRecord record) {
            return new RecordReadResult(
                    record, null, Integer.MIN_VALUE);
        }

        private static RecordReadResult failed(String reason) {
            return new RecordReadResult(
                    null, reason, Integer.MIN_VALUE);
        }

        private static RecordReadResult unsupported(int version) {
            return new RecordReadResult(null, null, version);
        }
    }
}
