package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.storage.NbtTags;
import com.ninuna.losttales.util.LostTalesLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
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
    /** Every marker id the files ever seeded, so one removed is not seeded again. */
    private static final String SEEDED_MARKERS = "SeededMarkers";
    /**
     * The discovery grids, as the shift from a block to its cell: cells of
     * 16, 64 and 256 blocks. A marker stands on the first grid whose cells
     * are at least a quarter of its reach, so a scan walks at most 11 by 11
     * cells of each grid, however far a marker is found from.
     */
    private static final int[] DISCOVERY_CELL_SHIFTS = {4, 6, 8};
    /** How many cells of its grid a marker's reach spans at most. */
    private static final int CELLS_PER_REACH = 4;

    private final Map<String, LostTalesMapMarkerRecord> records =
            new LinkedHashMap<String, LostTalesMapMarkerRecord>();
    private final Map<String, String> markerIdByCanonicalKey =
            new LinkedHashMap<String, String>();
    private final Map<String, String> markerIdByLinkedPosition =
            new LinkedHashMap<String, String>();
    private final Map<String, Set<String>> markerIdsByChunk =
            new LinkedHashMap<String, Set<String>>();
    /**
     * The discoverable markers by the cell of the discovery grid that
     * suits how far each is found from ({@link #DISCOVERY_CELL_SHIFTS}).
     */
    private final Map<String, Set<String>> discoverableIdsByCell =
            new LinkedHashMap<String, Set<String>>();
    /** The widest reach on each discovery grid of each world, in blocks. */
    private final Map<String, Double> widestReachByGrid =
            new LinkedHashMap<String, Double>();
    private final List<NBTTagCompound> quarantinedEntries =
            new ArrayList<NBTTagCompound>();
    private final Map<String, Integer> lotrTravelIds =
            new LinkedHashMap<String, Integer>();
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTTagCompound preservedNewerData;
    private final Set<String> seededKeys = new LinkedHashSet<String>();
    /** The marker files' read this store last seeded from; held in memory only. */
    private int seededRevision = -1;

    public LostTalesMapMarkerWorldData(String name) {
        super(name);
    }

    @Override
    public synchronized void readFromNBT(NBTTagCompound compound) {
        this.records.clear();
        this.markerIdByCanonicalKey.clear();
        this.markerIdByLinkedPosition.clear();
        this.markerIdsByChunk.clear();
        this.discoverableIdsByCell.clear();
        this.widestReachByGrid.clear();
        this.quarantinedEntries.clear();
        this.lotrTravelIds.clear();
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedNewerData = null;
        this.seededKeys.clear();
        this.seededRevision = -1;

        LostTalesMapMarkerNbtCodec.ReadResult result =
                LostTalesMapMarkerNbtCodec.read(compound);
        if (result.isReadOnly()) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = result.getUnsupportedVersion();
            this.preservedNewerData = result.getOriginalCopy();
            return;
        }
        Set<String> seeded = readSeededKeys(compound);
        if (seeded == null) {
            LostTalesLog.warning("Map marker data does not say which markers the files seeded; preserving it read-only");
            this.readOnlyForNewerVersion = true;
            this.preservedNewerData = (NBTTagCompound)compound.copy();
            return;
        }
        this.seededKeys.addAll(seeded);
        this.records.putAll(result.getRecords());
        this.quarantinedEntries.addAll(result.getQuarantineCopy());
        boolean repaired = deduplicateLogicalRecords();
        repaired |= quarantineSharedLinks();
        if (this.quarantinedEntries.size()
                > LostTalesMapMarkerNbtCodec.MAX_QUARANTINED) {
            LostTalesLog.warning("Map marker quarantine would hold %d records, past its %d; preserving the data read-only",
                    Integer.valueOf(this.quarantinedEntries.size()),
                    Integer.valueOf(LostTalesMapMarkerNbtCodec.MAX_QUARANTINED));
            this.records.clear();
            this.quarantinedEntries.clear();
            this.seededKeys.clear();
            this.readOnlyForNewerVersion = true;
            this.preservedNewerData = (NBTTagCompound)compound.copy();
            return;
        }
        repaired |= alignLinkedMarkerPositions();
        rebuildIndexes();
        repaired |= readLotrTravelIds(compound);
        if (result.wasRepaired() || repaired) {
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
        LostTalesMapMarkerNbtCodec.write(
                compound, this.records.values(), this.quarantinedEntries);
        NBTTagList seeded = new NBTTagList();
        for (String key : this.seededKeys) {
            seeded.appendTag(new NBTTagString(key));
        }
        compound.setTag(SEEDED_MARKERS, seeded);
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
        String storedId = resolveStoredMarkerId(markerId);
        return storedId == null ? null : this.records.get(storedId);
    }

    public synchronized Collection<LostTalesMapMarkerRecord> getRecords() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesMapMarkerRecord>(
                        this.records.values()));
    }

    public synchronized Collection<LostTalesMapMarkerRecord>
    getRecordsNearChunks(int dimensionId, int centerChunkX,
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
            if (record != null) {
                result.add(record);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * The discoverable markers a player standing at {@code x, z} is within
     * reach of, across the ground: a marker's discovery radius, at most
     * {@link LostTalesMapMarkerRecord#MAX_PLACED_DISCOVERY_RADIUS}, as far
     * as the scan goes (WAY-15), times {@code reachScale}, which
     * Pathfinder widens (PROG-06). Each discovery grid is walked only as
     * far as the widest reach on it.
     */
    public synchronized Collection<LostTalesMapMarkerRecord>
    getDiscoveryCandidates(int dimensionId, double x, double z, double reachScale) {
        double scale = Math.max(1.0D, reachScale);
        LinkedHashSet<String> ids = new LinkedHashSet<String>();
        int blockX = floor(x);
        int blockZ = floor(z);
        for (int grid = 0; grid < DISCOVERY_CELL_SHIFTS.length; grid++) {
            Double widest = this.widestReachByGrid.get(
                    gridKey(dimensionId, grid));
            if (widest == null) {
                continue;
            }
            int shift = DISCOVERY_CELL_SHIFTS[grid];
            int cells = (int)Math.ceil(widest.doubleValue() * scale / (1 << shift))
                    + 1;
            int cellX = blockX >> shift;
            int cellZ = blockZ >> shift;
            for (int walkX = cellX - cells; walkX <= cellX + cells; walkX++) {
                for (int walkZ = cellZ - cells; walkZ <= cellZ + cells;
                     walkZ++) {
                    Set<String> bucket = this.discoverableIdsByCell.get(
                            cellKey(dimensionId, grid, walkX, walkZ));
                    if (bucket != null) {
                        ids.addAll(bucket);
                    }
                }
            }
        }
        ArrayList<LostTalesMapMarkerRecord> result =
                new ArrayList<LostTalesMapMarkerRecord>();
        for (String id : ids) {
            LostTalesMapMarkerRecord record = this.records.get(id);
            if (record == null) {
                continue;
            }
            double reach = discoveryReach(record) * scale;
            double dx = x - record.getX();
            double dz = z - record.getZ();
            if (dx * dx + dz * dz <= reach * reach) {
                result.add(record);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** How far across the ground a marker is found from: its radius, at least a block, at most as far as the scan goes. */
    private static double discoveryReach(LostTalesMapMarkerRecord record) {
        return Math.max(1.0D, Math.min(
                LostTalesMapMarkerRecord.MAX_PLACED_DISCOVERY_RADIUS,
                record.getDiscoveryRadius()));
    }

    /** The discovery grid a reach stands on: the first whose cells are a quarter of it or more. */
    private static int gridFor(double reach) {
        for (int grid = 0; grid < DISCOVERY_CELL_SHIFTS.length - 1; grid++) {
            if (reach <= CELLS_PER_REACH * (1 << DISCOVERY_CELL_SHIFTS[grid])) {
                return grid;
            }
        }
        return DISCOVERY_CELL_SHIFTS.length - 1;
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
                getRecord(record.getId());
        if (existing != null
                && !existing.getId().equals(record.getId())) {
            throw new IllegalStateException(
                    "logical marker identity is already stored as "
                            + existing.getId());
        }
        if (existing != null
                && record.getRevision() <= existing.getRevision()) {
            throw new IllegalStateException(
                    "marker revision is stale: " + record.getId());
        }
        if (record.isLinked()) {
            if (record.getDimensionId()
                            != record.getLinkedDimensionId()
                    || Double.doubleToLongBits(record.getX())
                            != Double.doubleToLongBits(
                                    record.getLinkedX())
                    || Double.doubleToLongBits(record.getY())
                            != Double.doubleToLongBits(
                                    record.getLinkedY())
                    || Double.doubleToLongBits(record.getZ())
                            != Double.doubleToLongBits(
                                    record.getLinkedZ())) {
                throw new IllegalArgumentException(
                        "linked marker position must match its waystone");
            }
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

    /**
     * Takes the character's share off every marker shared with it, each a
     * revision newer, as its purge does (WAY-03). Shares stand in no
     * lookup, so none is rebuilt. Answers how many markers it changed.
     */
    public synchronized int forgetSharedCharacter(UUID characterId) {
        ensureWritable();
        int changed = 0;
        if (characterId == null) {
            return changed;
        }
        for (Map.Entry<String, LostTalesMapMarkerRecord> entry
                : this.records.entrySet()) {
            LostTalesMapMarkerRecord record = entry.getValue();
            if (record.getSharedCharacterIds().contains(characterId)) {
                entry.setValue(record.withoutSharedCharacter(characterId));
                changed++;
            }
        }
        if (changed > 0) {
            markDirty();
        }
        return changed;
    }

    public synchronized boolean removeRecord(String markerId) {
        ensureWritable();
        String storedId = resolveStoredMarkerId(markerId);
        if (storedId == null
                || this.records.remove(storedId) == null) {
            return false;
        }
        this.lotrTravelIds.remove(storedId);
        rebuildIndexes();
        markDirty();
        return true;
    }

    /**
     * Puts in each marker of the files this store never seeded: a marker
     * new to the files, bundled or the server's own, arrives as the server
     * starts. One seeded before stays as the store has it, and one removed
     * since, its waystone broken, does not come back; only
     * {@code /losttales mapmarker reseed} brings it back. Answers whether
     * a record was added.
     */
    public synchronized boolean seedDefinitions(
            Collection<LostTalesMapMarkerDefinition> definitions) {
        if (this.readOnlyForNewerVersion) {
            return false;
        }
        ensureWritable();
        boolean added = false;
        boolean noted = false;
        LinkedHashSet<String> canonicalKeys =
                new LinkedHashSet<String>(
                        this.markerIdByCanonicalKey.keySet());
        if (definitions != null) {
            for (LostTalesMapMarkerDefinition definition : definitions) {
                if (definition == null) {
                    continue;
                }
                String canonicalKey = canonicalKey(definition.getId());
                if (canonicalKey == null
                        || !this.seededKeys.add(canonicalKey)) {
                    continue;
                }
                noted = true;
                if (canonicalKeys.add(canonicalKey)) {
                    this.records.put(definition.getId(),
                            LostTalesMapMarkerRecord.fromDefinition(
                                    definition));
                    added = true;
                }
            }
        }
        if (added) {
            rebuildIndexes();
        }
        if (noted) {
            markDirty();
        }
        return added;
    }

    /** Notes a marker as seeded, as a reseed made its record. */
    public synchronized void noteSeeded(String markerId) {
        String canonicalKey = canonicalKey(markerId);
        if (!this.readOnlyForNewerVersion && canonicalKey != null
                && this.seededKeys.add(canonicalKey)) {
            markDirty();
        }
    }

    /** The marker files' read the store last seeded from; -1 before any. */
    public synchronized int getSeededRevision() {
        return this.seededRevision;
    }

    public synchronized void setSeededRevision(int revision) {
        this.seededRevision = revision;
    }

    /**
     * The seeded markers' keys a store holds; null for a store without
     * the list, or with anything but keys in it.
     */
    private static Set<String> readSeededKeys(NBTTagCompound compound) {
        if (!compound.hasKey(SEEDED_MARKERS, Constants.NBT.TAG_LIST)) {
            return null;
        }
        NBTTagList list = (NBTTagList)compound.getTag(SEEDED_MARKERS);
        if (list.tagCount() > 0
                && list.func_150303_d() != Constants.NBT.TAG_STRING) {
            return null;
        }
        LinkedHashSet<String> keys = new LinkedHashSet<String>();
        for (int index = 0; index < list.tagCount(); index++) {
            String key = list.getStringTagAt(index);
            if (key.length() == 0) {
                return null;
            }
            keys.add(key);
        }
        return keys;
    }

    /**
     * Allocates an ID from the range LOTR does not use for normal custom
     * waypoints. The mapping is persistent so native use-count cooldowns stay
     * attached to the same marker across restarts.
     */
    public synchronized int getOrCreateLotrTravelId(String markerId) {
        ensureWritable();
        String storedId = resolveStoredMarkerId(markerId);
        if (storedId == null) {
            throw new IllegalArgumentException(
                    "travel marker is not in the repository");
        }
        Integer existing = this.lotrTravelIds.get(storedId);
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
                        storedId, Integer.valueOf(candidate));
                markDirty();
                return candidate;
            }
        }
        throw new IllegalStateException(
                "LOTR waystone travel ID range is exhausted");
    }

    public synchronized LostTalesMapMarkerRecord
    findByLotrTravelId(int travelId) {
        if (travelId < MIN_LOTR_TRAVEL_ID
                || travelId > MAX_LOTR_TRAVEL_ID) {
            return null;
        }
        for (Map.Entry<String, Integer> entry :
                this.lotrTravelIds.entrySet()) {
            if (entry.getValue() != null
                    && entry.getValue().intValue() == travelId) {
                return this.records.get(entry.getKey());
            }
        }
        return null;
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
            String storedId = resolveStoredMarkerId(markerId);
            int travelId = entry.hasKey(
                    TRAVEL_ID, Constants.NBT.TAG_INT)
                    ? entry.getInteger(TRAVEL_ID) : 0;
            if (markerId.length() == 0
                    || markerId.length()
                            > LostTalesMapMarkerRecord.MAX_ID_LENGTH
                    || travelId < MIN_LOTR_TRAVEL_ID
                    || travelId > MAX_LOTR_TRAVEL_ID
                    || storedId == null
                    || this.lotrTravelIds.containsKey(storedId)
                    || !used.add(Integer.valueOf(travelId))) {
                repaired = true;
                continue;
            }
            this.lotrTravelIds.put(
                    storedId, Integer.valueOf(travelId));
            if (!storedId.equals(markerId)) {
                repaired = true;
            }
        }
        return repaired;
    }

    private boolean deduplicateLogicalRecords() {
        LinkedHashMap<String, LostTalesMapMarkerRecord> retainedByKey =
                new LinkedHashMap<String, LostTalesMapMarkerRecord>();
        boolean repaired = false;
        for (LostTalesMapMarkerRecord candidate : this.records.values()) {
            String key = canonicalKey(candidate.getId());
            LostTalesMapMarkerRecord previous = retainedByKey.get(key);
            if (previous == null) {
                retainedByKey.put(key, candidate);
                continue;
            }
            LostTalesMapMarkerRecord retained =
                    previous.getRevision() >= candidate.getRevision()
                            ? previous : candidate;
            LostTalesMapMarkerRecord discarded =
                    retained == previous ? candidate : previous;
            retainedByKey.put(key, retained);
            this.quarantinedEntries.add(quarantineRecord(
                    "duplicate_logical_marker_id",
                    discarded, retained.getId()));
            repaired = true;
        }
        if (repaired) {
            this.records.clear();
            for (LostTalesMapMarkerRecord record :
                    retainedByKey.values()) {
                this.records.put(record.getId(), record);
            }
        }
        return repaired;
    }

    private boolean alignLinkedMarkerPositions() {
        boolean repaired = false;
        for (Map.Entry<String, LostTalesMapMarkerRecord> entry :
                this.records.entrySet()) {
            LostTalesMapMarkerRecord record = entry.getValue();
            if (!record.isLinked()
                    || (record.getDimensionId()
                            == record.getLinkedDimensionId()
                    && Double.doubleToLongBits(record.getX())
                            == Double.doubleToLongBits(
                                    record.getLinkedX())
                    && Double.doubleToLongBits(record.getY())
                            == Double.doubleToLongBits(
                                    record.getLinkedY())
                    && Double.doubleToLongBits(record.getZ())
                            == Double.doubleToLongBits(
                                    record.getLinkedZ()))) {
                continue;
            }
            entry.setValue(record.toBuilder()
                    .position(record.getLinkedDimensionId(),
                            record.getLinkedX(), record.getLinkedY(),
                            record.getLinkedZ())
                    .revision(record.getRevision() + 1L)
                    .build());
            repaired = true;
        }
        return repaired;
    }

    /**
     * Two records linked to one waystone block, which no write makes: the
     * one of the higher revision keeps the block, and the other is
     * quarantined whole, once, as the store loads.
     */
    private boolean quarantineSharedLinks() {
        LinkedHashMap<String, LostTalesMapMarkerRecord> holders =
                new LinkedHashMap<String, LostTalesMapMarkerRecord>();
        List<String> discarded = new ArrayList<String>();
        for (LostTalesMapMarkerRecord candidate : this.records.values()) {
            if (!candidate.isLinked()) {
                continue;
            }
            String key = linkKey(candidate.getLinkedDimensionId(),
                    candidate.getLinkedX(), candidate.getLinkedY(),
                    candidate.getLinkedZ());
            LostTalesMapMarkerRecord previous = holders.get(key);
            if (previous == null) {
                holders.put(key, candidate);
                continue;
            }
            LostTalesMapMarkerRecord retained =
                    previous.getRevision() >= candidate.getRevision()
                            ? previous : candidate;
            LostTalesMapMarkerRecord other =
                    retained == previous ? candidate : previous;
            holders.put(key, retained);
            discarded.add(other.getId());
            this.quarantinedEntries.add(quarantineRecord(
                    "duplicate_waystone_link", other, retained.getId()));
        }
        for (String id : discarded) {
            this.records.remove(id);
        }
        return !discarded.isEmpty();
    }

    /**
     * Rebuilds every lookup from the records. The store holds no two
     * records of one logical id and no two linked to one block: loading
     * quarantines them, and {@link #saveRecord} refuses them.
     */
    private void rebuildIndexes() {
        this.markerIdByCanonicalKey.clear();
        this.markerIdByLinkedPosition.clear();
        this.markerIdsByChunk.clear();
        this.discoverableIdsByCell.clear();
        this.widestReachByGrid.clear();
        for (LostTalesMapMarkerRecord record : this.records.values()) {
            String canonicalKey = canonicalKey(record.getId());
            if (!this.markerIdByCanonicalKey.containsKey(canonicalKey)) {
                this.markerIdByCanonicalKey.put(canonicalKey, record.getId());
            }
            if (record.isLinked()) {
                String linkKey = linkKey(record.getLinkedDimensionId(),
                        record.getLinkedX(), record.getLinkedY(),
                        record.getLinkedZ());
                if (!this.markerIdByLinkedPosition.containsKey(linkKey)) {
                    this.markerIdByLinkedPosition.put(linkKey, record.getId());
                }
            }
            int blockX = floor(record.getX());
            int blockZ = floor(record.getZ());
            add(this.markerIdsByChunk, chunkKey(
                    record.getDimensionId(), blockX >> 4, blockZ >> 4),
                    record.getId());
            if (record.isDiscoverable()) {
                double reach = discoveryReach(record);
                int grid = gridFor(reach);
                int shift = DISCOVERY_CELL_SHIFTS[grid];
                add(this.discoverableIdsByCell, cellKey(
                        record.getDimensionId(), grid, blockX >> shift,
                        blockZ >> shift), record.getId());
                String gridKey = gridKey(record.getDimensionId(), grid);
                Double widest = this.widestReachByGrid.get(gridKey);
                if (widest == null || widest.doubleValue() < reach) {
                    this.widestReachByGrid.put(gridKey,
                            Double.valueOf(reach));
                }
            }
        }
    }

    private static void add(Map<String, Set<String>> buckets, String key,
                            String markerId) {
        Set<String> bucket = buckets.get(key);
        if (bucket == null) {
            bucket = new LinkedHashSet<String>();
            buckets.put(key, bucket);
        }
        bucket.add(markerId);
    }

    private String resolveStoredMarkerId(String markerId) {
        String key = canonicalKey(markerId);
        return key == null ? null
                : this.markerIdByCanonicalKey.get(key);
    }

    private static String canonicalKey(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim();
        if (normalized.length() == 0) {
            return null;
        }
        return LostTalesMapMarkerIdentity.create(normalized)
                .getCanonicalKey();
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

    private static String gridKey(int dimensionId, int grid) {
        return dimensionId + ":" + grid;
    }

    private static String cellKey(
            int dimensionId, int grid, int cellX, int cellZ) {
        return dimensionId + ":" + grid + ":" + cellX + ":" + cellZ;
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

    private static NBTTagCompound quarantineRecord(
            String reason, LostTalesMapMarkerRecord discarded,
            String retainedMarkerId) {
        NBTTagCompound entry = quarantine(
                reason,
                discarded == null ? "" : discarded.getId(),
                retainedMarkerId);
        if (discarded != null) {
            entry.setTag("OriginalData",
                    LostTalesMapMarkerNbtCodec.writeRecord(discarded));
        }
        return entry;
    }
}
