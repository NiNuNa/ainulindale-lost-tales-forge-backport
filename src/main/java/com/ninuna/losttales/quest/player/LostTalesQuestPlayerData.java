package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;
import net.minecraftforge.common.util.Constants;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Forge 1.7.10 player quest storage.
 *
 * This is the closest practical replacement for modern player attachments/capabilities.
 * It stores active and completed quest IDs in player NBT and can be copied on respawn.
 */
public final class LostTalesQuestPlayerData implements IExtendedEntityProperties {
    public static final String PROPERTY_ID = "LostTalesQuestData";

    private final Map<String, LostTalesQuestProgress> activeQuests = new LinkedHashMap<String, LostTalesQuestProgress>();
    private final Set<String> completedQuests = new LinkedHashSet<String>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Map<String, LostTalesMapMarkerDefinition> dynamicMapMarkers = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
    private final Set<String> pinnedQuestIds = new LinkedHashSet<String>();
    private String pinnedMapMarkerId = "";
    private EntityPlayer player;

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
        NBTTagCompound data = new NBTTagCompound();

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
            markerTag.setInteger("DimensionId", marker.getDimensionId());
            markerTag.setDouble("X", marker.getX());
            markerTag.setDouble("Y", marker.getY());
            markerTag.setDouble("Z", marker.getZ());
            markerTag.setBoolean("HiddenUntilDiscovered", marker.isHiddenUntilDiscovered());
            dynamicMarkerList.appendTag(markerTag);
        }
        data.setTag("DynamicMapMarkers", dynamicMarkerList);
        data.setString("PinnedMapMarkerId", this.pinnedMapMarkerId == null ? "" : this.pinnedMapMarkerId);

        compound.setTag(PROPERTY_ID, data);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        this.activeQuests.clear();
        this.completedQuests.clear();
        this.discoveredMarkerIds.clear();
        this.dynamicMapMarkers.clear();
        this.pinnedQuestIds.clear();
        this.pinnedMapMarkerId = "";

        if (compound == null || !compound.hasKey(PROPERTY_ID)) {
            return;
        }

        NBTTagCompound data = compound.getCompoundTag(PROPERTY_ID);
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

        NBTTagList markerList = data.getTagList("DiscoveredMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < markerList.tagCount(); i++) {
            NBTTagCompound markerTag = markerList.getCompoundTagAt(i);
            String markerId = markerTag.getString("MarkerId");
            if (markerId != null && markerId.length() > 0) {
                this.discoveredMarkerIds.add(markerId);
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

        this.pinnedMapMarkerId = data.getString("PinnedMapMarkerId");
        if (this.pinnedMapMarkerId == null || !this.discoveredMarkerIds.contains(this.pinnedMapMarkerId)) {
            this.pinnedMapMarkerId = "";
        }
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

    public Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.discoveredMarkerIds));
    }

    public Collection<LostTalesMapMarkerDefinition> getDynamicMapMarkers() {
        return Collections.unmodifiableCollection(new ArrayList<LostTalesMapMarkerDefinition>(this.dynamicMapMarkers.values()));
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

    public void startQuest(String questId, String firstStageId) {
        if (questId == null || questId.length() == 0 || this.activeQuests.containsKey(questId)) {
            return;
        }
        this.activeQuests.put(questId, new LostTalesQuestProgress(questId, 0, firstStageId));
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
        markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (markerId.length() == 0) {
            return false;
        }
        return this.discoveredMarkerIds.add(markerId);
    }

    public boolean discoverDynamicMarker(LostTalesMapMarkerDefinition marker) {
        if (marker == null || marker.getId() == null || marker.getId().length() == 0) {
            return false;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(marker.getId());
        LostTalesMapMarkerDefinition normalized = new LostTalesMapMarkerDefinition(
                markerId,
                marker.getName() == null || marker.getName().length() == 0 ? markerId : marker.getName(),
                marker.getIconName() == null || marker.getIconName().length() == 0 ? "quest" : marker.getIconName(),
                marker.getColorName() == null || marker.getColorName().length() == 0 ? "white" : marker.getColorName(),
                marker.getDimensionId(),
                marker.getX(),
                marker.getY(),
                marker.getZ(),
                marker.isHiddenUntilDiscovered()
        );
        LostTalesMapMarkerDefinition old = this.dynamicMapMarkers.put(markerId, normalized);
        boolean discoveredChanged = this.discoveredMarkerIds.add(markerId);
        return discoveredChanged || !sameMarker(old, normalized);
    }

    public boolean isMarkerDiscovered(String markerId) {
        return markerId != null && this.discoveredMarkerIds.contains(markerId);
    }

    public boolean forgetMarker(String markerId) {
        if (markerId == null || markerId.trim().length() == 0) {
            return false;
        }
        markerId = markerId.trim();
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
        if (markerId == null || markerId.length() == 0) {
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
        boolean changed = this.pinnedMapMarkerId != null && this.pinnedMapMarkerId.length() > 0;
        this.pinnedMapMarkerId = "";
        return changed;
    }

    public boolean setPinnedQuestId(String questId) {
        return pinQuestId(questId);
    }

    public boolean pinQuestId(String questId) {
        if (questId == null || questId.length() == 0 || !this.activeQuests.containsKey(questId)) {
            return false;
        }
        return this.pinnedQuestIds.add(questId);
    }

    public boolean unpinQuestId(String questId) {
        if (questId == null || questId.length() == 0) {
            return false;
        }
        return this.pinnedQuestIds.remove(questId);
    }

    public boolean clearPinnedQuestId() {
        boolean changed = !this.pinnedQuestIds.isEmpty();
        this.pinnedQuestIds.clear();
        return changed;
    }

    public boolean completeQuest(String questId) {
        if (questId == null || questId.length() == 0) {
            return false;
        }
        boolean wasActive = this.activeQuests.remove(questId) != null;
        this.pinnedQuestIds.remove(questId);
        boolean wasNewlyCompleted = this.completedQuests.add(questId);
        return wasActive || wasNewlyCompleted;
    }

    public boolean setQuestStage(String questId, int stageIndex, String stageId) {
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
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        return progress == null ? 0 : progress.addObjectiveProgress(objectiveId, amount, maxValue);
    }

    public void setObjectiveProgress(String questId, String objectiveId, int value) {
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (progress != null) {
            progress.setObjectiveProgress(objectiveId, value);
        }
    }

    public boolean resetQuest(String questId) {
        if (questId == null || questId.length() == 0) {
            return false;
        }
        boolean removedActive = this.activeQuests.remove(questId) != null;
        this.pinnedQuestIds.remove(questId);
        boolean removedCompleted = this.completedQuests.remove(questId);
        return removedActive || removedCompleted;
    }

    public boolean abandonQuest(String questId) {
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
        this.discoveredMarkerIds.clear();
        this.dynamicMapMarkers.clear();
        this.pinnedQuestIds.clear();
        this.pinnedMapMarkerId = "";
        if (oldData == null) {
            return;
        }

        for (LostTalesQuestProgress progress : oldData.activeQuests.values()) {
            this.activeQuests.put(progress.getQuestId(), progress.copy());
        }
        this.completedQuests.addAll(oldData.completedQuests);
        this.discoveredMarkerIds.addAll(oldData.discoveredMarkerIds);
        this.dynamicMapMarkers.putAll(oldData.dynamicMapMarkers);
        for (String questId : oldData.pinnedQuestIds) {
            if (questId != null && this.activeQuests.containsKey(questId)) {
                this.pinnedQuestIds.add(questId);
            }
        }
        this.pinnedMapMarkerId = oldData.pinnedMapMarkerId != null && this.discoveredMarkerIds.contains(oldData.pinnedMapMarkerId) ? oldData.pinnedMapMarkerId : "";
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
                markerTag.getInteger("DimensionId"),
                markerTag.getDouble("X"),
                markerTag.getDouble("Y"),
                markerTag.getDouble("Z"),
                !markerTag.hasKey("HiddenUntilDiscovered") || markerTag.getBoolean("HiddenUntilDiscovered")
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
                && left.getDimensionId() == right.getDimensionId()
                && Math.abs(left.getX() - right.getX()) < 0.01D
                && Math.abs(left.getY() - right.getY()) < 0.01D
                && Math.abs(left.getZ() - right.getZ()) < 0.01D
                && left.isHiddenUntilDiscovered() == right.isHiddenUntilDiscovered();
    }

    private static String safe(String value, String fallback) {
        return value == null || value.length() == 0 ? fallback : value;
    }

}
