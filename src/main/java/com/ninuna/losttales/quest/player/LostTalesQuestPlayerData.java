package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdentity;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionNbt;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;
/**
 * Forge 1.7.10 player quest storage.
 *
 * This is the closest practical replacement for modern player attachments/capabilities.
 * It stores active and completed quest IDs in player NBT and can be copied on respawn.
 */
public final class LostTalesQuestPlayerData implements IExtendedEntityProperties {
    public static final String PROPERTY_ID = "LostTalesQuestData";
    public static final int CURRENT_DATA_VERSION = 1;
    static final int MAX_ACTIVE_QUESTS = 1024;
    static final int MAX_QUEST_ID_HISTORY = 8192;
    static final int MAX_DYNAMIC_QUESTS = 512;
    static final int MAX_DYNAMIC_MARKERS = 2048;
    static final int MAX_IDENTIFIER_CHARACTERS = 256;
    static final int MAX_NAME_CHARACTERS = 1024;

    private static final String TAG_DATA_VERSION = "DataVersion";

    private final Map<String, LostTalesQuestProgress> activeQuests = new LinkedHashMap<String, LostTalesQuestProgress>();
    private final Set<String> completedQuests = new LinkedHashSet<String>();
    private final Set<String> failedQuests = new LinkedHashSet<String>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Map<String, String> discoveredMarkerIdByCanonicalKey =
            new LinkedHashMap<String, String>();
    private final Map<String, LostTalesMapMarkerDefinition> dynamicMapMarkers = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
    private final Map<String, String> dynamicMarkerIdByCanonicalKey =
            new LinkedHashMap<String, String>();
    private final Map<String, LostTalesQuestDefinition> dynamicQuestDefinitions = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private final Set<String> pinnedQuestIds = new LinkedHashSet<String>();
    private String pinnedMapMarkerId = "";
    private EntityPlayer player;
    private boolean readOnlyForNewerVersion;
    private int unsupportedDataVersion = -1;
    private NBTBase preservedReadOnlyData;

    public static LostTalesQuestPlayerData get(EntityPlayer player) {
        if (player == null) {
            return null;
        }

        IExtendedEntityProperties properties = player.getExtendedProperties(PROPERTY_ID);
        if (properties instanceof LostTalesQuestPlayerData) {
            return (LostTalesQuestPlayerData) properties;
        }

        LostTalesQuestPlayerData data = new LostTalesQuestPlayerData();
        player.registerExtendedProperties(PROPERTY_ID, data);
        data.player = player;
        return data;
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        if (compound == null) {
            return;
        }
        if (this.readOnlyForNewerVersion && this.preservedReadOnlyData != null) {
            compound.setTag(PROPERTY_ID, this.preservedReadOnlyData.copy());
            return;
        }

        NBTTagCompound data = new NBTTagCompound();
        data.setInteger(TAG_DATA_VERSION, CURRENT_DATA_VERSION);

        NBTTagList activeList = new NBTTagList();
        for (LostTalesQuestProgress progress : this.activeQuests.values()) {
            activeList.appendTag(progress.writeToNBT());
        }
        data.setTag("ActiveQuests", activeList);

        NBTTagList completedList = new NBTTagList();
        for (String questId : this.completedQuests) {
            NBTTagCompound questTag = new NBTTagCompound();
            questTag.setString("QuestId", questId);
            completedList.appendTag(questTag);
        }
        data.setTag("CompletedQuests", completedList);

        NBTTagList failedList = new NBTTagList();
        for (String questId : this.failedQuests) {
            NBTTagCompound questTag = new NBTTagCompound();
            questTag.setString("QuestId", questId);
            failedList.appendTag(questTag);
        }
        data.setTag("FailedQuests", failedList);

        // Keep the old single-string tag as a migration/compatibility hint, but
        // store the real tracked quest state as a list so multiple quests can be
        // tracked at the same time.
        data.setString("PinnedQuestId", getPinnedQuestId());
        NBTTagList pinnedQuestList = new NBTTagList();
        for (String questId : this.pinnedQuestIds) {
            if (questId == null || questId.length() == 0 || !this.activeQuests.containsKey(questId)) {
                continue;
            }
            NBTTagCompound questTag = new NBTTagCompound();
            questTag.setString("QuestId", questId);
            pinnedQuestList.appendTag(questTag);
        }
        data.setTag("PinnedQuestIds", pinnedQuestList);

        NBTTagList markerList = new NBTTagList();
        for (String markerId : this.discoveredMarkerIds) {
            NBTTagCompound markerTag = new NBTTagCompound();
            markerTag.setString("MarkerId", markerId);
            markerList.appendTag(markerTag);
        }
        data.setTag("DiscoveredMarkers", markerList);

        NBTTagList dynamicQuestList = new NBTTagList();
        for (LostTalesQuestDefinition quest : this.dynamicQuestDefinitions.values()) {
            if (quest == null || quest.getId() == null || quest.getId().length() == 0) {
                continue;
            }
            dynamicQuestList.appendTag(LostTalesQuestDefinitionNbt.write(quest));
        }
        data.setTag("DynamicQuestDefinitions", dynamicQuestList);

        NBTTagList dynamicMarkerList = new NBTTagList();
        for (LostTalesMapMarkerDefinition marker : this.dynamicMapMarkers.values()) {
            if (marker == null || marker.getId() == null || marker.getId().length() == 0) {
                continue;
            }
            NBTTagCompound markerTag = new NBTTagCompound();
            markerTag.setString("MarkerId", marker.getId());
            markerTag.setString("Name", marker.getName() == null ? marker.getId() : marker.getName());
            markerTag.setString("Icon", marker.getIconName() == null ? "quest" : marker.getIconName());
            markerTag.setString("Color", marker.getColorName() == null ? "white" : marker.getColorName());
            markerTag.setString("Category", marker.getCategoryName() == null ? LostTalesMapMarkerDefinition.CATEGORY_DEFAULT : marker.getCategoryName());
            markerTag.setBoolean("HasFastTravel", marker.hasFastTravel());
            markerTag.setBoolean("Waypoint", marker.hasFastTravel());
            markerTag.setInteger("DimensionId", marker.getDimensionId());
            markerTag.setDouble("X", marker.getX());
            markerTag.setDouble("Y", marker.getY());
            markerTag.setDouble("Z", marker.getZ());
            markerTag.setDouble("CompassFadeInRadius", marker.getCompassFadeInRadius());
            markerTag.setDouble("DiscoveryRadius", marker.getDiscoveryRadius());
            markerTag.setBoolean("HiddenUntilDiscovered", marker.isHiddenUntilDiscovered());
            markerTag.setBoolean("IsDiscoverable", marker.isDiscoverable());
            markerTag.setBoolean("Discoverable", marker.isDiscoverable());
            markerTag.setBoolean("RequiresRegionUnlock",
                    marker.requiresRegionUnlock());
            markerTag.setInteger("Priority", marker.getPriority());
            dynamicMarkerList.appendTag(markerTag);
        }
        data.setTag("DynamicMapMarkers", dynamicMarkerList);
        data.setString("PinnedMapMarkerId", this.pinnedMapMarkerId == null ? "" : this.pinnedMapMarkerId);

        compound.setTag(PROPERTY_ID, data);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        loadNBTData(compound, true, true);
    }

    private void loadNBTData(NBTTagCompound compound,
                             boolean registerRuntimeDefinitions,
                             boolean logWarnings) {
        this.activeQuests.clear();
        this.completedQuests.clear();
        this.failedQuests.clear();
        this.discoveredMarkerIds.clear();
        this.discoveredMarkerIdByCanonicalKey.clear();
        this.dynamicMapMarkers.clear();
        this.dynamicMarkerIdByCanonicalKey.clear();
        this.dynamicQuestDefinitions.clear();
        this.pinnedQuestIds.clear();
        this.pinnedMapMarkerId = "";
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedReadOnlyData = null;

        if (compound == null || !compound.hasKey(PROPERTY_ID)) {
            return;
        }
        if (!compound.hasKey(PROPERTY_ID, Constants.NBT.TAG_COMPOUND)) {
            enterReadOnlyMode(compound.getTag(PROPERTY_ID), -1,
                    "Quest data property is malformed and will be preserved without modification",
                    logWarnings);
            return;
        }

        NBTTagCompound originalData = compound.getCompoundTag(PROPERTY_ID);
        LostTalesQuestDataMigrator.MigrationResult migration =
                LostTalesQuestDataMigrator.migrate(originalData, CURRENT_DATA_VERSION);
        if (!migration.isValid()) {
            enterReadOnlyMode(originalData, -1,
                    "Quest data is malformed and will be preserved without modification",
                    logWarnings);
            return;
        }
        if (!migration.isSupported()) {
            enterReadOnlyMode(originalData, migration.getVersion(),
                    "Quest data uses unsupported version " + migration.getVersion()
                            + " and will be preserved without modification",
                    logWarnings);
            return;
        }

        NBTTagCompound data = migration.getTag();
        if (!isStructurallyReasonable(data)) {
            enterReadOnlyMode(originalData, -1,
                    "Quest data exceeds structural safety limits and will "
                            + "be preserved without modification",
                    logWarnings);
            return;
        }
        NBTTagList activeList = data.getTagList("ActiveQuests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < activeList.tagCount(); i++) {
            LostTalesQuestProgress progress = LostTalesQuestProgress.readFromNBT(activeList.getCompoundTagAt(i));
            if (progress != null) {
                this.activeQuests.put(progress.getQuestId(), progress);
            }
        }

        NBTTagList pinnedQuestList = data.getTagList("PinnedQuestIds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < pinnedQuestList.tagCount(); i++) {
            String questId = pinnedQuestList.getCompoundTagAt(i).getString("QuestId");
            if (questId != null && questId.length() > 0 && this.activeQuests.containsKey(questId)) {
                this.pinnedQuestIds.add(questId);
            }
        }

        // Migration path for worlds saved before multi-tracking existed.
        if (this.pinnedQuestIds.isEmpty()) {
            String legacyPinnedQuestId = data.getString("PinnedQuestId");
            if (legacyPinnedQuestId != null && this.activeQuests.containsKey(legacyPinnedQuestId)) {
                this.pinnedQuestIds.add(legacyPinnedQuestId);
            }
        }

        NBTTagList completedList = data.getTagList("CompletedQuests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < completedList.tagCount(); i++) {
            NBTTagCompound questTag = completedList.getCompoundTagAt(i);
            String questId = questTag.getString("QuestId");
            if (questId != null && questId.length() > 0) {
                this.completedQuests.add(questId);
            }
        }

        NBTTagList failedList = data.getTagList("FailedQuests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < failedList.tagCount(); i++) {
            NBTTagCompound questTag = failedList.getCompoundTagAt(i);
            String questId = questTag.getString("QuestId");
            if (questId != null && questId.length() > 0) {
                this.failedQuests.add(questId);
            }
        }

        NBTTagList markerList = data.getTagList("DiscoveredMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < markerList.tagCount(); i++) {
            NBTTagCompound markerTag = markerList.getCompoundTagAt(i);
            String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerTag.getString("MarkerId"));
            addDiscoveredMarkerId(markerId);
        }
        NBTTagList dynamicQuestList = data.getTagList("DynamicQuestDefinitions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dynamicQuestList.tagCount(); i++) {
            LostTalesQuestDefinition quest = LostTalesQuestDefinitionNbt.read(dynamicQuestList.getCompoundTagAt(i));
            if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                this.dynamicQuestDefinitions.put(quest.getId(), quest);
                if (registerRuntimeDefinitions) {
                    LostTalesQuestRegistry.registerRuntimeQuest(quest);
                }
            }
        }

        NBTTagList dynamicMarkerList = data.getTagList("DynamicMapMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dynamicMarkerList.tagCount(); i++) {
            LostTalesMapMarkerDefinition marker = readDynamicMarker(dynamicMarkerList.getCompoundTagAt(i));
            if (marker != null) {
                addLoadedDynamicMarker(marker);
            }
        }

        String requestedPinnedMarkerId =
                LostTalesQuestMarkerHelper.normalizeMarkerId(
                        data.getString("PinnedMapMarkerId"));
        String storedPinnedMarkerId =
                findDiscoveredMarkerId(requestedPinnedMarkerId);
        this.pinnedMapMarkerId = storedPinnedMarkerId == null
                ? "" : storedPinnedMarkerId;
        pruneInvalidReferences();
    }

    @Override
    public void init(Entity entity, World world) {
        if (entity instanceof EntityPlayer) {
            this.player = (EntityPlayer) entity;
        }
    }

    public EntityPlayer getPlayer() {
        return this.player;
    }

    public boolean isReadOnlyForNewerVersion() {
        return this.readOnlyForNewerVersion;
    }

    public int getUnsupportedDataVersion() {
        return this.unsupportedDataVersion;
    }

    private boolean isWritable() {
        return !this.readOnlyForNewerVersion;
    }

    private void enterReadOnlyMode(NBTBase preservedData, int unsupportedVersion,
                                   String message, boolean logWarning) {
        this.readOnlyForNewerVersion = true;
        this.unsupportedDataVersion = unsupportedVersion;
        this.preservedReadOnlyData = preservedData == null ? null : preservedData.copy();
        if (logWarning) {
            FMLLog.warning("[%s] %s", LostTalesMetaData.MOD_ID, message);
        }
    }

    /**
     * Returns a detached, character-owned quest payload without the Forge
     * extended-properties wrapper used in vanilla player.dat.
     */
    public NBTTagCompound writeCharacterState() {
        NBTTagCompound wrapper = new NBTTagCompound();
        saveNBTData(wrapper);
        if (!wrapper.hasKey(PROPERTY_ID, Constants.NBT.TAG_COMPOUND)) {
            throw new IllegalStateException(
                    "Quest data cannot be represented as a character snapshot");
        }
        return (NBTTagCompound) wrapper.getCompoundTag(PROPERTY_ID).copy();
    }

    /**
     * Strictly validates a detached quest payload without registering its
     * server-global runtime definitions. The returned compound is independent
     * from the caller's NBT tree.
     */
    public static NBTTagCompound validateCharacterState(
            NBTTagCompound characterState) {
        if (characterState == null) {
            throw new IllegalArgumentException("Quest character state is missing");
        }
        LostTalesQuestPlayerData probe = new LostTalesQuestPlayerData();
        probe.loadCharacterState(characterState, false, false);
        if (probe.isReadOnlyForNewerVersion()) {
            throw new IllegalArgumentException(
                    "Quest character state is malformed or unsupported");
        }
        NBTTagCompound canonical = probe.writeCharacterState();
        if (!canonical.equals(characterState)) {
            throw new IllegalArgumentException(
                    "Quest character state is not canonical");
        }
        return (NBTTagCompound) canonical.copy();
    }

    /** Replaces the live player's quest state from an already detached payload. */
    public void replaceCharacterState(NBTTagCompound characterState) {
        NBTTagCompound validated = validateCharacterState(characterState);
        loadCharacterState(validated, true, true);
        if (isReadOnlyForNewerVersion()) {
            throw new IllegalArgumentException(
                    "Quest character state became unavailable while applying");
        }
    }

    private void loadCharacterState(NBTTagCompound characterState,
                                    boolean registerRuntimeDefinitions,
                                    boolean logWarnings) {
        NBTTagCompound wrapper = new NBTTagCompound();
        if (characterState != null) {
            wrapper.setTag(PROPERTY_ID, characterState.copy());
        }
        loadNBTData(wrapper, registerRuntimeDefinitions, logWarnings);
    }

    public Collection<LostTalesQuestProgress> getActiveQuests() {
        ArrayList<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : this.activeQuests.values()) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableCollection(copy);
    }

    public Set<String> getCompletedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.completedQuests));
    }

    public Set<String> getFailedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.failedQuests));
    }

    public Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.discoveredMarkerIds));
    }

    public Collection<LostTalesMapMarkerDefinition> getDynamicMapMarkers() {
        return Collections.unmodifiableCollection(new ArrayList<LostTalesMapMarkerDefinition>(this.dynamicMapMarkers.values()));
    }

    public Collection<LostTalesQuestDefinition> getDynamicQuestDefinitions() {
        return Collections.unmodifiableCollection(new ArrayList<LostTalesQuestDefinition>(this.dynamicQuestDefinitions.values()));
    }

    public LostTalesQuestDefinition getDynamicQuestDefinition(String questId) {
        return questId == null || questId.length() == 0 ? null : this.dynamicQuestDefinitions.get(questId);
    }

    public boolean rememberDynamicQuestDefinition(LostTalesQuestDefinition quest) {
        if (!isWritable()) {
            return false;
        }
        if (quest == null || quest.getId() == null || quest.getId().length() == 0) {
            return false;
        }
        LostTalesQuestDefinition old = this.dynamicQuestDefinitions.put(quest.getId(), quest);
        LostTalesQuestRegistry.registerRuntimeQuest(quest);
        return old == null || old != quest;
    }

    public LostTalesMapMarkerDefinition getDynamicMapMarker(String markerId) {
        String storedId = findDynamicMarkerId(markerId);
        return storedId == null ? null
                : this.dynamicMapMarkers.get(storedId);
    }

    public LostTalesQuestProgress getActiveQuest(String questId) {
        return this.activeQuests.get(questId);
    }

    public boolean isQuestActive(String questId) {
        return this.activeQuests.containsKey(questId);
    }

    public boolean isQuestCompleted(String questId) {
        return this.completedQuests.contains(questId);
    }

    public boolean isQuestFailed(String questId) {
        return questId != null && this.failedQuests.contains(questId);
    }

    public void startQuest(String questId, String firstStageId) {
        startQuest(questId, firstStageId, 0L, 0L);
    }

    public void startQuest(String questId, String firstStageId, long acceptedWorldTime, long deadlineWorldTime) {
        if (!isWritable()) {
            return;
        }
        if (questId == null || questId.length() == 0 || this.activeQuests.containsKey(questId)) {
            return;
        }
        this.failedQuests.remove(questId);
        this.activeQuests.put(questId, new LostTalesQuestProgress(questId, 0, firstStageId, null, acceptedWorldTime, deadlineWorldTime));
    }

    public String getPinnedQuestId() {
        for (String questId : this.pinnedQuestIds) {
            if (questId != null && questId.length() > 0 && this.activeQuests.containsKey(questId)) {
                return questId;
            }
        }
        return "";
    }

    public Set<String> getPinnedQuestIds() {
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        for (String questId : this.pinnedQuestIds) {
            if (questId != null && questId.length() > 0 && this.activeQuests.containsKey(questId)) {
                copy.add(questId);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    public boolean isQuestPinned(String questId) {
        return questId != null && this.pinnedQuestIds.contains(questId) && this.activeQuests.containsKey(questId);
    }

    public String getPinnedMapMarkerId() {
        return this.pinnedMapMarkerId == null ? "" : this.pinnedMapMarkerId;
    }

    public boolean discoverMarker(String markerId) {
        if (!isWritable()) {
            return false;
        }
        return addDiscoveredMarkerId(markerId);
    }

    public boolean discoverDynamicMarker(LostTalesMapMarkerDefinition marker) {
        if (!isWritable()) {
            return false;
        }
        if (!isValidDynamicMarker(marker)) {
            return false;
        }
        String requestedId =
                LostTalesQuestMarkerHelper.normalizeMarkerId(
                        marker.getId());
        String key = markerCanonicalKey(requestedId);
        String storedId = this.dynamicMarkerIdByCanonicalKey.get(key);
        if (storedId == null) {
            if (this.dynamicMapMarkers.size() >= MAX_DYNAMIC_MARKERS
                    || (findDiscoveredMarkerId(requestedId) == null
                        && this.discoveredMarkerIds.size()
                                >= MAX_QUEST_ID_HISTORY)) {
                return false;
            }
            storedId = requestedId;
            this.dynamicMarkerIdByCanonicalKey.put(key, storedId);
        }
        LostTalesMapMarkerDefinition normalized =
                normalizeDynamicMarker(marker, storedId);
        LostTalesMapMarkerDefinition old =
                this.dynamicMapMarkers.put(storedId, normalized);
        boolean discoveredChanged = addDiscoveredMarkerId(storedId);
        return discoveredChanged || !sameMarker(old, normalized);
    }

    public boolean isMarkerDiscovered(String markerId) {
        return findDiscoveredMarkerId(markerId) != null;
    }

    public boolean forgetMarker(String markerId) {
        if (!isWritable()) {
            return false;
        }
        String key = markerCanonicalKey(markerId);
        if (key.length() == 0) {
            return false;
        }
        String discoveredId =
                this.discoveredMarkerIdByCanonicalKey.remove(key);
        boolean changed = discoveredId != null
                && this.discoveredMarkerIds.remove(discoveredId);
        String dynamicId =
                this.dynamicMarkerIdByCanonicalKey.remove(key);
        if (dynamicId != null
                && this.dynamicMapMarkers.remove(dynamicId) != null) {
            changed = true;
        }
        if (sameMarkerIdentity(markerId, this.pinnedMapMarkerId)) {
            this.pinnedMapMarkerId = "";
            changed = true;
        }
        return changed;
    }

    public boolean setPinnedMapMarkerId(String markerId) {
        if (!isWritable()) {
            return false;
        }
        String normalized =
                LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (normalized.length() == 0) {
            return clearPinnedMapMarkerId();
        }
        String discoveredId = findDiscoveredMarkerId(normalized);
        if (discoveredId == null) {
            return false;
        }
        if (sameMarkerIdentity(discoveredId,
                this.pinnedMapMarkerId)) {
            return false;
        }
        this.pinnedMapMarkerId = discoveredId;
        return true;
    }

    public boolean clearPinnedMapMarkerId() {
        if (!isWritable()) {
            return false;
        }
        boolean changed = this.pinnedMapMarkerId != null && this.pinnedMapMarkerId.length() > 0;
        this.pinnedMapMarkerId = "";
        return changed;
    }

    public boolean setPinnedQuestId(String questId) {
        return pinQuestId(questId);
    }

    public boolean pinQuestId(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0 || !this.activeQuests.containsKey(questId)) {
            return false;
        }
        return this.pinnedQuestIds.add(questId);
    }

    public boolean hasPinnedQuest() {
        return !getPinnedQuestIds().isEmpty();
    }

    public boolean unpinQuestId(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        return this.pinnedQuestIds.remove(questId);
    }

    public boolean clearPinnedQuestId() {
        if (!isWritable()) {
            return false;
        }
        boolean changed = !this.pinnedQuestIds.isEmpty();
        this.pinnedQuestIds.clear();
        return changed;
    }

    public boolean completeQuest(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        boolean wasActive = this.activeQuests.remove(questId) != null;
        this.pinnedQuestIds.remove(questId);
        this.failedQuests.remove(questId);
        boolean wasNewlyCompleted = this.completedQuests.add(questId);
        return wasActive || wasNewlyCompleted;
    }

    public boolean failQuest(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        boolean wasActive = this.activeQuests.remove(questId) != null;
        this.pinnedQuestIds.remove(questId);
        this.completedQuests.remove(questId);
        boolean wasNewlyFailed = this.failedQuests.add(questId);
        return wasActive || wasNewlyFailed;
    }

    public boolean setQuestStage(String questId, int stageIndex, String stageId) {
        if (!isWritable()) {
            return false;
        }
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (progress == null) {
            return false;
        }
        progress.setStage(stageIndex, stageId);
        progress.clearObjectiveProgress();
        return true;
    }

    public int getObjectiveProgress(String questId, String objectiveId) {
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        return progress == null ? 0 : progress.getObjectiveProgress(objectiveId);
    }

    public int addObjectiveProgress(String questId, String objectiveId, int amount, int maxValue) {
        if (!isWritable()) {
            return getObjectiveProgress(questId, objectiveId);
        }
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        return progress == null ? 0 : progress.addObjectiveProgress(objectiveId, amount, maxValue);
    }

    public void setObjectiveProgress(String questId, String objectiveId, int value) {
        if (!isWritable()) {
            return;
        }
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (progress != null) {
            progress.setObjectiveProgress(objectiveId, value);
        }
    }

    public boolean resetQuest(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        boolean removedActive = this.activeQuests.remove(questId) != null;
        this.pinnedQuestIds.remove(questId);
        boolean removedCompleted = this.completedQuests.remove(questId);
        boolean removedFailed = this.failedQuests.remove(questId);
        return removedActive || removedCompleted || removedFailed;
    }

    public boolean abandonQuest(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        boolean changed = this.activeQuests.remove(questId) != null;
        this.pinnedQuestIds.remove(questId);
        return changed;
    }

    public void copyFrom(LostTalesQuestPlayerData oldData) {
        this.activeQuests.clear();
        this.completedQuests.clear();
        this.failedQuests.clear();
        this.discoveredMarkerIds.clear();
        this.discoveredMarkerIdByCanonicalKey.clear();
        this.dynamicMapMarkers.clear();
        this.dynamicMarkerIdByCanonicalKey.clear();
        this.dynamicQuestDefinitions.clear();
        this.pinnedQuestIds.clear();
        this.pinnedMapMarkerId = "";
        this.readOnlyForNewerVersion = false;
        this.unsupportedDataVersion = -1;
        this.preservedReadOnlyData = null;
        if (oldData == null) {
            return;
        }
        if (oldData.readOnlyForNewerVersion) {
            this.readOnlyForNewerVersion = true;
            this.unsupportedDataVersion = oldData.unsupportedDataVersion;
            this.preservedReadOnlyData = oldData.preservedReadOnlyData == null
                    ? null
                    : oldData.preservedReadOnlyData.copy();
            return;
        }

        for (LostTalesQuestProgress progress : oldData.activeQuests.values()) {
            this.activeQuests.put(progress.getQuestId(), progress.copy());
        }
        this.completedQuests.addAll(oldData.completedQuests);
        this.failedQuests.addAll(oldData.failedQuests);
        for (String markerId : oldData.discoveredMarkerIds) {
            addDiscoveredMarkerId(markerId);
        }
        for (LostTalesMapMarkerDefinition marker :
                oldData.dynamicMapMarkers.values()) {
            addLoadedDynamicMarker(marker);
        }
        this.dynamicQuestDefinitions.putAll(oldData.dynamicQuestDefinitions);
        LostTalesQuestRegistry.registerRuntimeQuests(this.dynamicQuestDefinitions.values());
        for (String questId : oldData.pinnedQuestIds) {
            if (questId != null && this.activeQuests.containsKey(questId)) {
                this.pinnedQuestIds.add(questId);
            }
        }
        String copiedPinnedMarkerId =
                findDiscoveredMarkerId(oldData.pinnedMapMarkerId);
        this.pinnedMapMarkerId = copiedPinnedMarkerId == null
                ? "" : copiedPinnedMarkerId;
        pruneInvalidReferences();
    }

    /**
     * Removes references that can become stale after a quest completes, is abandoned,
     * or older NBT is loaded. Returns true when anything was cleaned up.
     */
    public boolean pruneInvalidReferences() {
        if (!isWritable()) {
            return false;
        }
        boolean changed = false;
        ArrayList<String> invalidPinnedQuests = new ArrayList<String>();
        for (String questId : this.pinnedQuestIds) {
            if (questId == null || questId.length() == 0 || !this.activeQuests.containsKey(questId)) {
                invalidPinnedQuests.add(questId);
            }
        }
        if (!invalidPinnedQuests.isEmpty()) {
            this.pinnedQuestIds.removeAll(invalidPinnedQuests);
            changed = true;
        }
        if (this.pinnedMapMarkerId != null
                && this.pinnedMapMarkerId.length() > 0
                && findDiscoveredMarkerId(
                        this.pinnedMapMarkerId) == null
                && !LostTalesMapMarkerCatalog.isVisibleByDefault(
                        this.pinnedMapMarkerId)) {
            this.pinnedMapMarkerId = "";
            changed = true;
        }
        return changed;
    }

    private boolean addDiscoveredMarkerId(String markerId) {
        String normalized =
                LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        String key = markerCanonicalKey(normalized);
        if (key.length() == 0
                || this.discoveredMarkerIdByCanonicalKey
                        .containsKey(key)
                || this.discoveredMarkerIds.size()
                        >= MAX_QUEST_ID_HISTORY) {
            return false;
        }
        this.discoveredMarkerIds.add(normalized);
        this.discoveredMarkerIdByCanonicalKey.put(key, normalized);
        return true;
    }

    private boolean addLoadedDynamicMarker(
            LostTalesMapMarkerDefinition marker) {
        if (!isValidDynamicMarker(marker)) {
            return false;
        }
        String markerId =
                LostTalesQuestMarkerHelper.normalizeMarkerId(
                        marker.getId());
        String key = markerCanonicalKey(markerId);
        if (this.dynamicMarkerIdByCanonicalKey.containsKey(key)
                || this.dynamicMapMarkers.size() >= MAX_DYNAMIC_MARKERS
                || (findDiscoveredMarkerId(markerId) == null
                    && this.discoveredMarkerIds.size()
                            >= MAX_QUEST_ID_HISTORY)) {
            return false;
        }
        LostTalesMapMarkerDefinition normalized =
                normalizeDynamicMarker(marker, markerId);
        this.dynamicMapMarkers.put(markerId, normalized);
        this.dynamicMarkerIdByCanonicalKey.put(key, markerId);
        addDiscoveredMarkerId(markerId);
        return true;
    }

    private String findDiscoveredMarkerId(String markerId) {
        String key = markerCanonicalKey(markerId);
        return key.length() == 0 ? null
                : this.discoveredMarkerIdByCanonicalKey.get(key);
    }

    private String findDynamicMarkerId(String markerId) {
        String key = markerCanonicalKey(markerId);
        return key.length() == 0 ? null
                : this.dynamicMarkerIdByCanonicalKey.get(key);
    }

    private static boolean sameMarkerIdentity(
            String first, String second) {
        String firstKey = markerCanonicalKey(first);
        return firstKey.length() > 0
                && firstKey.equals(markerCanonicalKey(second));
    }

    private static String markerCanonicalKey(String markerId) {
        String normalized =
                LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (normalized.length() == 0
                || normalized.length() > MAX_IDENTIFIER_CHARACTERS) {
            return "";
        }
        return LostTalesMapMarkerIdentity.create(
                normalized,
                LostTalesMapMarkerIdentity.Authority.QUEST_PLAYER)
                .getCanonicalKey();
    }

    private static LostTalesMapMarkerDefinition normalizeDynamicMarker(
            LostTalesMapMarkerDefinition marker,
            String persistedMarkerId) {
        return new LostTalesMapMarkerDefinition(
                persistedMarkerId,
                safe(marker.getName(), persistedMarkerId),
                safe(marker.getIconName(), "quest"),
                safe(marker.getColorName(), "white"),
                safe(marker.getCategoryName(),
                        LostTalesMapMarkerDefinition.CATEGORY_DEFAULT),
                "",
                marker.hasFastTravel(), marker.getDimensionId(),
                marker.getX(), marker.getY(), marker.getZ(),
                marker.getCompassFadeInRadius(),
                marker.getDiscoveryRadius(),
                marker.isHiddenUntilDiscovered(),
                marker.isDiscoverable(),
                marker.requiresRegionUnlock(),
                LostTalesMapMarkerSource.QUEST_DYNAMIC,
                false, "", marker.getPriority());
    }

    private static boolean isValidDynamicMarker(
            LostTalesMapMarkerDefinition marker) {
        return marker != null
                && markerCanonicalKey(marker.getId()).length() > 0
                && safe(marker.getName(), "").length()
                        <= MAX_NAME_CHARACTERS
                && safe(marker.getIconName(), "").length()
                        <= MAX_IDENTIFIER_CHARACTERS
                && safe(marker.getColorName(), "").length()
                        <= MAX_IDENTIFIER_CHARACTERS
                && safe(marker.getCategoryName(), "").length()
                        <= MAX_NAME_CHARACTERS
                && isFinite(marker.getX())
                && isFinite(marker.getY())
                && isFinite(marker.getZ())
                && isFinite(marker.getCompassFadeInRadius())
                && marker.getCompassFadeInRadius() >= 0.0D
                && isFinite(marker.getDiscoveryRadius())
                && marker.getDiscoveryRadius() >= 0.0D;
    }

    private static LostTalesMapMarkerDefinition readDynamicMarker(NBTTagCompound markerTag) {
        if (markerTag == null) {
            return null;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerTag.getString("MarkerId"));
        if (markerId.length() == 0) {
            return null;
        }
        return new LostTalesMapMarkerDefinition(
                markerId,
                safe(markerTag.getString("Name"), markerId),
                safe(markerTag.getString("Icon"), "quest"),
                safe(markerTag.getString("Color"), "white"),
                safe(markerTag.getString("Category"), LostTalesMapMarkerDefinition.CATEGORY_DEFAULT),
                "",
                (markerTag.hasKey("HasFastTravel") ? markerTag.getBoolean("HasFastTravel") : (markerTag.hasKey("Waypoint") && markerTag.getBoolean("Waypoint"))),
                markerTag.getInteger("DimensionId"),
                markerTag.getDouble("X"),
                markerTag.getDouble("Y"),
                markerTag.getDouble("Z"),
                markerTag.hasKey("CompassFadeInRadius") ? markerTag.getDouble("CompassFadeInRadius") : (markerTag.hasKey("FadeInRadius") ? markerTag.getDouble("FadeInRadius") : 128.0D),
                markerTag.hasKey("DiscoveryRadius") ? markerTag.getDouble("DiscoveryRadius") : (markerTag.hasKey("UnlockRadius") ? markerTag.getDouble("UnlockRadius") : 8.0D),
                !markerTag.hasKey("HiddenUntilDiscovered") || markerTag.getBoolean("HiddenUntilDiscovered"),
                markerTag.hasKey("IsDiscoverable") ? markerTag.getBoolean("IsDiscoverable") : (markerTag.hasKey("Discoverable") ? markerTag.getBoolean("Discoverable") : (!markerTag.hasKey("HiddenUntilDiscovered") || markerTag.getBoolean("HiddenUntilDiscovered"))),
                markerTag.hasKey("RequiresRegionUnlock")
                        && markerTag.getBoolean("RequiresRegionUnlock"),
                LostTalesMapMarkerSource.QUEST_DYNAMIC,
                false, "", markerTag.hasKey("Priority")
                        ? markerTag.getInteger("Priority") : 0
        );
    }

    private static boolean sameMarker(LostTalesMapMarkerDefinition left, LostTalesMapMarkerDefinition right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return safe(left.getId(), "").equals(safe(right.getId(), ""))
                && safe(left.getName(), "").equals(safe(right.getName(), ""))
                && safe(left.getIconName(), "").equals(safe(right.getIconName(), ""))
                && safe(left.getColorName(), "").equals(safe(right.getColorName(), ""))
                && safe(left.getCategoryName(), "").equals(safe(right.getCategoryName(), ""))
                && left.hasFastTravel() == right.hasFastTravel()
                && left.getDimensionId() == right.getDimensionId()
                && Math.abs(left.getX() - right.getX()) < 0.01D
                && Math.abs(left.getY() - right.getY()) < 0.01D
                && Math.abs(left.getZ() - right.getZ()) < 0.01D
                && Math.abs(left.getCompassFadeInRadius() - right.getCompassFadeInRadius()) < 0.01D
                && Math.abs(left.getDiscoveryRadius() - right.getDiscoveryRadius()) < 0.01D
                && left.isHiddenUntilDiscovered() == right.isHiddenUntilDiscovered()
                && left.isDiscoverable() == right.isDiscoverable()
                && left.requiresRegionUnlock()
                == right.requiresRegionUnlock()
                && left.getPriority() == right.getPriority();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

    private static boolean isStructurallyReasonable(NBTTagCompound data) {
        if (!hasCompoundListWithinLimit(
                data, "ActiveQuests", MAX_ACTIVE_QUESTS)
                || !hasCompoundListWithinLimit(
                data, "PinnedQuestIds", MAX_ACTIVE_QUESTS)
                || !hasCompoundListWithinLimit(
                data, "CompletedQuests", MAX_QUEST_ID_HISTORY)
                || !hasCompoundListWithinLimit(
                data, "FailedQuests", MAX_QUEST_ID_HISTORY)
                || !hasCompoundListWithinLimit(
                data, "DiscoveredMarkers", MAX_QUEST_ID_HISTORY)
                || !hasCompoundListWithinLimit(
                data, "DynamicQuestDefinitions", MAX_DYNAMIC_QUESTS)
                || !hasCompoundListWithinLimit(
                data, "DynamicMapMarkers", MAX_DYNAMIC_MARKERS)
                || !hasReasonableOptionalString(
                data, "PinnedQuestId", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                data, "PinnedMapMarkerId", MAX_IDENTIFIER_CHARACTERS)) {
            return false;
        }

        NBTTagList activeQuests = data.getTagList(
                "ActiveQuests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < activeQuests.tagCount(); i++) {
            if (!LostTalesQuestProgress.isStructurallyReasonable(
                    activeQuests.getCompoundTagAt(i))) {
                return false;
            }
        }
        if (!hasReasonableIdList(data, "PinnedQuestIds", "QuestId")
                || !hasReasonableIdList(
                data, "CompletedQuests", "QuestId")
                || !hasReasonableIdList(data, "FailedQuests", "QuestId")
                || !hasReasonableIdList(
                data, "DiscoveredMarkers", "MarkerId")) {
            return false;
        }

        NBTTagList dynamicQuests = data.getTagList(
                "DynamicQuestDefinitions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dynamicQuests.tagCount(); i++) {
            if (!LostTalesQuestDefinitionNbt.isStructurallyReasonable(
                    dynamicQuests.getCompoundTagAt(i))) {
                return false;
            }
        }

        NBTTagList dynamicMarkers = data.getTagList(
                "DynamicMapMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < dynamicMarkers.tagCount(); i++) {
            if (!isReasonableDynamicMarker(
                    dynamicMarkers.getCompoundTagAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasReasonableIdList(
            NBTTagCompound owner, String listKey, String idKey) {
        NBTTagList list = owner.getTagList(
                listKey, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            String value = list.getCompoundTagAt(i).getString(idKey);
            if (value.length() == 0
                    || value.length() > MAX_IDENTIFIER_CHARACTERS) {
                return false;
            }
        }
        return true;
    }

    private static boolean isReasonableDynamicMarker(NBTTagCompound marker) {
        if (!hasReasonableRequiredString(
                marker, "MarkerId", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Name", MAX_NAME_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Icon", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Color", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Category", MAX_NAME_CHARACTERS)) {
            return false;
        }
        double x = marker.getDouble("X");
        double y = marker.getDouble("Y");
        double z = marker.getDouble("Z");
        double fadeRadius = marker.hasKey("CompassFadeInRadius")
                ? marker.getDouble("CompassFadeInRadius")
                : marker.getDouble("FadeInRadius");
        double discoveryRadius = marker.hasKey("DiscoveryRadius")
                ? marker.getDouble("DiscoveryRadius")
                : marker.getDouble("UnlockRadius");
        int priority = marker.hasKey("Priority", Constants.NBT.TAG_INT)
                ? marker.getInteger("Priority") : 0;
        return isFinite(x) && isFinite(y) && isFinite(z)
                && isFinite(fadeRadius) && fadeRadius >= 0.0D
                && isFinite(discoveryRadius) && discoveryRadius >= 0.0D
                && priority >= LostTalesMapMarkerDefinition.MIN_PRIORITY
                && priority <= LostTalesMapMarkerDefinition.MAX_PRIORITY;
    }

    private static boolean hasCompoundListWithinLimit(
            NBTTagCompound owner, String key, int maximum) {
        if (owner == null) {
            return false;
        }
        if (!owner.hasKey(key)) {
            return true;
        }
        NBTBase raw = owner.getTag(key);
        if (!(raw instanceof NBTTagList)) {
            return false;
        }
        NBTTagList list = (NBTTagList) raw;
        return (list.tagCount() == 0
                || list.func_150303_d() == Constants.NBT.TAG_COMPOUND)
                && list.tagCount() <= maximum;
    }

    private static boolean hasReasonableRequiredString(
            NBTTagCompound owner, String key, int maximum) {
        return owner != null
                && owner.hasKey(key, Constants.NBT.TAG_STRING)
                && owner.getString(key).length() > 0
                && owner.getString(key).length() <= maximum;
    }

    private static boolean hasReasonableOptionalString(
            NBTTagCompound owner, String key, int maximum) {
        return owner != null && (!owner.hasKey(key)
                || owner.hasKey(key, Constants.NBT.TAG_STRING)
                && owner.getString(key).length() <= maximum);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

}
