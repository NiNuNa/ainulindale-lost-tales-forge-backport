package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
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

    private static final String TAG_DATA_VERSION = "DataVersion";

    private final Map<String, LostTalesQuestProgress> activeQuests = new LinkedHashMap<String, LostTalesQuestProgress>();
    private final Set<String> completedQuests = new LinkedHashSet<String>();
    private final Set<String> failedQuests = new LinkedHashSet<String>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Map<String, LostTalesMapMarkerDefinition> dynamicMapMarkers = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
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
        this.dynamicMapMarkers.clear();
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
            if (markerId.length() > 0) {
                this.discoveredMarkerIds.add(markerId);
            }
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
                this.dynamicMapMarkers.put(marker.getId(), marker);
                this.discoveredMarkerIds.add(marker.getId());
            }
        }

        this.pinnedMapMarkerId = LostTalesQuestMarkerHelper.normalizeMarkerId(data.getString("PinnedMapMarkerId"));
        if (!this.discoveredMarkerIds.contains(this.pinnedMapMarkerId)) {
            this.pinnedMapMarkerId = "";
        }
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
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        return markerId.length() == 0 ? null : this.dynamicMapMarkers.get(markerId);
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
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (markerId.length() == 0) {
            return false;
        }
        return this.discoveredMarkerIds.add(markerId);
    }

    public boolean discoverDynamicMarker(LostTalesMapMarkerDefinition marker) {
        if (!isWritable()) {
            return false;
        }
        if (marker == null || marker.getId() == null || marker.getId().length() == 0) {
            return false;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(marker.getId());
        LostTalesMapMarkerDefinition normalized = new LostTalesMapMarkerDefinition(
                markerId,
                marker.getName() == null || marker.getName().length() == 0 ? markerId : marker.getName(),
                marker.getIconName() == null || marker.getIconName().length() == 0 ? "quest" : marker.getIconName(),
                marker.getColorName() == null || marker.getColorName().length() == 0 ? "white" : marker.getColorName(),
                marker.getCategoryName() == null || marker.getCategoryName().length() == 0 ? LostTalesMapMarkerDefinition.CATEGORY_DEFAULT : marker.getCategoryName(),
                marker.hasFastTravel(),
                marker.getDimensionId(),
                marker.getX(),
                marker.getY(),
                marker.getZ(),
                marker.getCompassFadeInRadius(),
                marker.getDiscoveryRadius(),
                marker.isHiddenUntilDiscovered(),
                marker.isDiscoverable(),
                marker.requiresRegionUnlock()
        );
        LostTalesMapMarkerDefinition old = this.dynamicMapMarkers.put(markerId, normalized);
        boolean discoveredChanged = this.discoveredMarkerIds.add(markerId);
        return discoveredChanged || !sameMarker(old, normalized);
    }

    public boolean isMarkerDiscovered(String markerId) {
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        return markerId.length() > 0 && this.discoveredMarkerIds.contains(markerId);
    }

    public boolean forgetMarker(String markerId) {
        if (!isWritable()) {
            return false;
        }
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (markerId.length() == 0) {
            return false;
        }
        boolean changed = this.discoveredMarkerIds.remove(markerId);
        if (this.dynamicMapMarkers.remove(markerId) != null) {
            changed = true;
        }
        if (markerId.equals(this.pinnedMapMarkerId)) {
            this.pinnedMapMarkerId = "";
            changed = true;
        }
        return changed;
    }

    public boolean setPinnedMapMarkerId(String markerId) {
        if (!isWritable()) {
            return false;
        }
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (markerId.length() == 0) {
            return clearPinnedMapMarkerId();
        }
        if (!this.discoveredMarkerIds.contains(markerId)) {
            return false;
        }
        if (markerId.equals(this.pinnedMapMarkerId)) {
            return false;
        }
        this.pinnedMapMarkerId = markerId;
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
        this.dynamicMapMarkers.clear();
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
        this.discoveredMarkerIds.addAll(oldData.discoveredMarkerIds);
        this.dynamicMapMarkers.putAll(oldData.dynamicMapMarkers);
        this.dynamicQuestDefinitions.putAll(oldData.dynamicQuestDefinitions);
        LostTalesQuestRegistry.registerRuntimeQuests(this.dynamicQuestDefinitions.values());
        for (String questId : oldData.pinnedQuestIds) {
            if (questId != null && this.activeQuests.containsKey(questId)) {
                this.pinnedQuestIds.add(questId);
            }
        }
        this.pinnedMapMarkerId = oldData.pinnedMapMarkerId != null && this.discoveredMarkerIds.contains(oldData.pinnedMapMarkerId) ? oldData.pinnedMapMarkerId : "";
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
        if (this.pinnedMapMarkerId != null && this.pinnedMapMarkerId.length() > 0 && !this.discoveredMarkerIds.contains(this.pinnedMapMarkerId) && !LostTalesMapMarkerCatalog.isVisibleByDefault(this.pinnedMapMarkerId)) {
            this.pinnedMapMarkerId = "";
            changed = true;
        }
        return changed;
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
                        && markerTag.getBoolean("RequiresRegionUnlock")
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
                == right.requiresRegionUnlock();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

}
