package com.ninuna.losttales.mapmarker;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.WorldSavedData;
import net.minecraftforge.common.util.Constants;

/** Persistent server-authoritative marker repository and spatial indexes. */
public final class LostTalesMapMarkerWorldData extends WorldSavedData {
    public static final String DATA_NAME = "losttales_map_markers";
    public static final int MIN_LOTR_TRAVEL_ID = 1;
    public static final int MAX_LOTR_TRAVEL_ID = 19999;
    private static final String LOTR_TRAVEL_IDS = "LotrTravelIds";
    private static final String TRAVEL_MARKER_ID = "MarkerId";
    private static final String TRAVEL_ID = "TravelId";

    private final Map<String, LostTalesMapMarkerRecord> records =
            new LinkedHashMap<String, LostTalesMapMarkerRecord>();
    private final Map<String, String> markerIdByLinkedPosition =
            new LinkedHashMap<String, String>();
    private final Map<String, Set<String>> markerIdsByChunk =
            new LinkedHashMap<String, Set<String>>();
    private final Map<Integer, Double> maximumDiscoveryRadiusByDimension =
            new LinkedHashMap<Integer, Double>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    private final Map<String, Integer> lotrTravelIds =
            new LinkedHashMap<String, Integer>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;
    private transient boolean presetsSeededThisSession;

    public LostTalesMapMarkerWorldData() {
        this(DATA_NAME);
    }

    public LostTalesMapMarkerWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.records.clear();
        this.markerIdByLinkedPosition.clear();
        this.markerIdsByChunk.clear();
        this.maximumDiscoveryRadiusByDimension.clear();
        this.quarantinedEntries.clear();
        this.lotrTravelIds.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;
        this.presetsSeededThisSession = false;

        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalCopy();
            return;
        }
        this.records.putAll(result.getRecords());
        this.quarantinedEntries.addAll(result.getQuarantineCopy());
        boolean repaired = readLotrTravelIds(compound);
        repaired |= rebuildIndexes();
        if (result.wasRepaired() || repaired) {
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
        LostTalesMapMarkerNbtCodec.write(
                compound, this.records.values(), this.quarantinedEntries);
        NBTTagList travelIds = new NBTTagList();
        ArrayList<String> markerIds =
                new ArrayList<String>(this.lotrTravelIds.keySet());
        Collections.sort(markerIds);
        for (String markerId : markerIds) {
            Integer travelId = this.lotrTravelIds.get(markerId);
            if (travelId == null) {
                continue;
            }
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString(TRAVEL_MARKER_ID, markerId);
            entry.setInteger(TRAVEL_ID, travelId.intValue());
            travelIds.appendTag(entry);
        }
        compound.setTag(LOTR_TRAVEL_IDS, travelIds);
    }

    public synchronized boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public synchronized int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    public synchronized LostTalesMapMarkerRecord getRecord(String markerId) {
        return markerId == null ? null : this.records.get(markerId.trim());
    }

    public synchronized Collection<LostTalesMapMarkerRecord> getRecords() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesMapMarkerRecord>(
                        this.records.values()));
    }

    public synchronized Collection<LostTalesMapMarkerRecord>
    getActiveRecords() {
        ArrayList<LostTalesMapMarkerRecord> active =
                new ArrayList<LostTalesMapMarkerRecord>();
        for (LostTalesMapMarkerRecord record : this.records.values()) {
            if (record.isActive()) {
                active.add(record);
            }
        }
        return Collections.unmodifiableList(active);
    }

    public synchronized Collection<LostTalesMapMarkerRecord>
    getActiveRecordsNearChunks(int dimensionId, int centerChunkX,
                               int centerChunkZ, int radiusChunks) {
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        int radius = Math.max(0, Math.min(64, radiusChunks));
        for (int chunkX = centerChunkX - radius;
             chunkX <= centerChunkX + radius; chunkX++) {
            for (int chunkZ = centerChunkZ - radius;
                 chunkZ <= centerChunkZ + radius; chunkZ++) {
                Set<String> bucket = this.markerIdsByChunk.get(
                        chunkKey(dimensionId, chunkX, chunkZ));
                if (bucket != null) {
                    ids.addAll(bucket);
                }
            }
        }
        ArrayList<LostTalesMapMarkerRecord> result =
                new ArrayList<LostTalesMapMarkerRecord>();
        for (String id : ids) {
            LostTalesMapMarkerRecord record = this.records.get(id);
            if (record != null && record.isActive()) {
                result.add(record);
            }
        }
        return Collections.unmodifiableList(result);
    }

    public synchronized Collection<LostTalesMapMarkerRecord>
    getDiscoveryCandidates(int dimensionId, double x, double z) {
        Double maximum = this.maximumDiscoveryRadiusByDimension.get(
                Integer.valueOf(dimensionId));
        if (maximum == null || maximum.doubleValue() <= 0.0D) {
            return Collections.emptyList();
        }
        int radiusChunks = (int)Math.ceil(
                Math.min(1024.0D, maximum.doubleValue()) / 16.0D) + 1;
        return getActiveRecordsNearChunks(
                dimensionId, floor(x) >> 4, floor(z) >> 4,
                Math.min(64, radiusChunks));
    }

    public synchronized LostTalesMapMarkerRecord findByLinkedPosition(
            int dimensionId, int x, int y, int z) {
        String markerId = this.markerIdByLinkedPosition.get(
                linkKey(dimensionId, x, y, z));
        return markerId == null ? null : this.records.get(markerId);
    }

    public synchronized void saveRecord(
            LostTalesMapMarkerRecord record) {
        ensureWritable();
        if (record == null) {
            throw new IllegalArgumentException("record must not be null");
        }
        LostTalesMapMarkerRecord existing =
                this.records.get(record.getId());
        if (existing != null
                && record.getRevision() < existing.getRevision()) {
            throw new IllegalStateException(
                    "marker revision is stale: " + record.getId());
        }
        if (record.isLinked()) {
            String occupiedBy = this.markerIdByLinkedPosition.get(
                    linkKey(record.getLinkedDimensionId(),
                            record.getLinkedX(), record.getLinkedY(),
                            record.getLinkedZ()));
            if (occupiedBy != null && !occupiedBy.equals(record.getId())) {
                throw new IllegalStateException(
                        "waystone position is already linked to " + occupiedBy);
            }
        }
        this.records.put(record.getId(), record);
        rebuildIndexes();
        markDirty();
    }

    public synchronized boolean seedDefinitions(
            Collection<LostTalesMapMarkerDefinition> definitions) {
        if (this.readOnlyForNewerVersion) {
            return false;
        }
        ensureWritable();
        if (this.presetsSeededThisSession) {
            return false;
        }
        boolean changed = false;
        if (definitions != null) {
            for (LostTalesMapMarkerDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }
                LostTalesMapMarkerRecord existing =
                        this.records.get(definition.getId());
                if (existing == null) {
                    this.records.put(definition.getId(),
                            LostTalesMapMarkerRecord.fromDefinition(
                                    definition));
                    changed = true;
                    continue;
                }
                LostTalesMapMarkerRecord reconciled =
                        existing.reconcilePresetDefinition(definition);
                if (reconciled != existing) {
                    this.records.put(
                            reconciled.getId(), reconciled);
                    changed = true;
                }
            }
        }
        this.presetsSeededThisSession = true;
        if (changed) {
            rebuildIndexes();
            markDirty();
        }
        return changed;
    }

    public synchronized int getQuarantinedEntryCount() {
        return this.quarantinedEntries.size();
    }

    /**
     * Allocates an ID from the range LOTR does not use for normal custom
     * waypoints. The mapping is persistent so native use-count cooldowns stay
     * attached to the same marker across restarts.
     */
    public synchronized int getOrCreateLotrTravelId(String markerId) {
        ensureWritable();
        String normalized = markerId == null ? "" : markerId.trim();
        if (normalized.length() == 0
                || !this.records.containsKey(normalized)) {
            throw new IllegalArgumentException(
                    "travel marker is not in the repository");
        }
        Integer existing = this.lotrTravelIds.get(normalized);
        if (existing != null) {
            return existing.intValue();
        }
        boolean[] used = new boolean[MAX_LOTR_TRAVEL_ID + 1];
        for (Integer value : this.lotrTravelIds.values()) {
            if (value != null && value.intValue() >= MIN_LOTR_TRAVEL_ID
                    && value.intValue() <= MAX_LOTR_TRAVEL_ID) {
                used[value.intValue()] = true;
            }
        }
        for (int candidate = MIN_LOTR_TRAVEL_ID;
             candidate <= MAX_LOTR_TRAVEL_ID; candidate++) {
            if (!used[candidate]) {
                this.lotrTravelIds.put(
                        normalized, Integer.valueOf(candidate));
                markDirty();
                return candidate;
            }
        }
        throw new IllegalStateException(
                "LOTR waystone travel ID range is exhausted");
    }

    public synchronized int getLotrTravelId(String markerId) {
        Integer value = markerId == null
                ? null : this.lotrTravelIds.get(markerId.trim());
        return value == null ? 0 : value.intValue();
    }

    private boolean readLotrTravelIds(NBTTagCompound root) {
        if (root == null || !root.hasKey(LOTR_TRAVEL_IDS)) {
            return false;
        }
        if (!root.hasKey(LOTR_TRAVEL_IDS, Constants.NBT.TAG_LIST)) {
            return true;
        }
        NBTTagList list = root.getTagList(
                LOTR_TRAVEL_IDS, Constants.NBT.TAG_COMPOUND);
        boolean repaired = list.tagCount() > MAX_LOTR_TRAVEL_ID;
        Set<Integer> used = new LinkedHashSet<Integer>();
        int count = Math.min(
                list.tagCount(), MAX_LOTR_TRAVEL_ID);
        for (int index = 0; index < count; index++) {
            NBTTagCompound entry = list.getCompoundTagAt(index);
            String markerId = entry.hasKey(
                    TRAVEL_MARKER_ID, Constants.NBT.TAG_STRING)
                    ? entry.getString(TRAVEL_MARKER_ID).trim() : "";
            int travelId = entry.hasKey(
                    TRAVEL_ID, Constants.NBT.TAG_INT)
                    ? entry.getInteger(TRAVEL_ID) : 0;
            if (markerId.length() == 0
                    || markerId.length()
                            > LostTalesMapMarkerRecord.MAX_ID_LENGTH
                    || travelId < MIN_LOTR_TRAVEL_ID
                    || travelId > MAX_LOTR_TRAVEL_ID
                    || this.lotrTravelIds.containsKey(markerId)
                    || !used.add(Integer.valueOf(travelId))) {
                repaired = true;
                continue;
            }
            this.lotrTravelIds.put(
                    markerId, Integer.valueOf(travelId));
        }
        return repaired;
    }

    private boolean rebuildIndexes() {
        this.markerIdByLinkedPosition.clear();
        this.markerIdsByChunk.clear();
        this.maximumDiscoveryRadiusByDimension.clear();
        boolean repaired = false;
        for (LostTalesMapMarkerRecord record : this.records.values()) {
            if (record.isLinked()) {
                String key = linkKey(record.getLinkedDimensionId(),
                        record.getLinkedX(), record.getLinkedY(),
                        record.getLinkedZ());
                String previous = this.markerIdByLinkedPosition.get(key);
                if (previous == null) {
                    this.markerIdByLinkedPosition.put(
                            key, record.getId());
                } else if (!previous.equals(record.getId())) {
                    this.quarantinedEntries.add(quarantine(
                            "duplicate_waystone_link",
                            record.getId(), previous));
                    repaired = true;
                }
            }
            if (!record.isActive()) {
                continue;
            }
            int blockX = floor(record.getX());
            int blockZ = floor(record.getZ());
            String chunkKey = chunkKey(
                    record.getDimensionId(), blockX >> 4, blockZ >> 4);
            Set<String> bucket = this.markerIdsByChunk.get(chunkKey);
            if (bucket == null) {
                bucket = new LinkedHashSet<String>();
                this.markerIdsByChunk.put(chunkKey, bucket);
            }
            bucket.add(record.getId());
            if (record.isDiscoverable()) {
                Integer dimensionKey =
                        Integer.valueOf(record.getDimensionId());
                Double previousMaximum =
                        this.maximumDiscoveryRadiusByDimension.get(
                                dimensionKey);
                if (previousMaximum == null
                        || previousMaximum.doubleValue()
                                < record.getDiscoveryRadius()) {
                    this.maximumDiscoveryRadiusByDimension.put(
                            dimensionKey,
                            Double.valueOf(record.getDiscoveryRadius()));
                }
            }
        }
        return repaired;
    }

    private void ensureWritable() {
        if (this.readOnlyForNewerVersion) {
            throw new IllegalStateException(
                    "Map marker data is read-only because it uses unsupported version "
                            + this.unsupportedDataVersion);
        }
    }

    private static String linkKey(
            int dimensionId, int x, int y, int z) {
        return dimensionId + ":" + x + ":" + y + ":" + z;
    }

    private static String chunkKey(
            int dimensionId, int chunkX, int chunkZ) {
        return dimensionId + ":" + chunkX + ":" + chunkZ;
    }

    private static int floor(double value) {
        int truncated = (int)value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static NBTTagCompound quarantine(
            String reason, String markerId, String conflictingId) {
        NBTTagCompound entry = new NBTTagCompound();
        entry.setString("Reason", reason);
        entry.setString("MarkerId", markerId);
        entry.setString("ConflictingMarkerId", conflictingId);
        return entry;
    }

    private static void copyTagContents(
            NBTTagCompound source, NBTTagCompound destination) {
        for (Object keyObject : source.func_150296_c()) {
            if (!(keyObject instanceof String)) {
                continue;
            }
            String key = (String)keyObject;
            NBTBase value = source.getTag(key);
            if (value != null) {
                destination.setTag(key, value.copy());
            }
        }
    }
}
