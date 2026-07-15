package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/**
 * Server-to-client snapshot of a player's quest state.
 *
 * <p>This packet intentionally stays snapshot-based for Forge 1.7.10 simplicity. It
 * carries active/completed quests, objective counters, tracked quests, and the
 * player's discovered/tracked map marker IDs.</p>
 */
public class LostTalesQuestSyncPacket implements IMessage {
    private final List<LostTalesQuestProgress> activeQuests = new ArrayList<LostTalesQuestProgress>();
    private final Set<String> completedQuestIds = new LinkedHashSet<String>();
    private final Set<String> failedQuestIds = new LinkedHashSet<String>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Set<String> pinnedQuestIds = new LinkedHashSet<String>();
    private final List<LostTalesMapMarkerDefinition> dynamicMapMarkers = new ArrayList<LostTalesMapMarkerDefinition>();
    private final List<LostTalesQuestDefinition> dynamicQuestDefinitions = new ArrayList<LostTalesQuestDefinition>();
    private String pinnedMapMarkerId = "";

    public LostTalesQuestSyncPacket() {}

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds) {
        this(activeQuests, completedQuestIds, Collections.<String>emptySet(), Collections.<String>emptySet(), "", Collections.<LostTalesMapMarkerDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestId) {
        this(activeQuests, completedQuestIds, singlePinned(pinnedQuestId), Collections.<String>emptySet(), "", Collections.<LostTalesMapMarkerDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestId, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId) {
        this(activeQuests, completedQuestIds, singlePinned(pinnedQuestId), discoveredMarkerIds, pinnedMapMarkerId, Collections.<LostTalesMapMarkerDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestId, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId, Collection<LostTalesMapMarkerDefinition> dynamicMapMarkers) {
        this(activeQuests, completedQuestIds, singlePinned(pinnedQuestId), discoveredMarkerIds, pinnedMapMarkerId, dynamicMapMarkers);
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, Collection<String> pinnedQuestIds, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId, Collection<LostTalesMapMarkerDefinition> dynamicMapMarkers) {
        this(activeQuests, completedQuestIds, pinnedQuestIds, discoveredMarkerIds, pinnedMapMarkerId, dynamicMapMarkers, Collections.<LostTalesQuestDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, Collection<String> pinnedQuestIds, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId, Collection<LostTalesMapMarkerDefinition> dynamicMapMarkers, Collection<LostTalesQuestDefinition> dynamicQuestDefinitions) {
        this(activeQuests, completedQuestIds, Collections.<String>emptySet(), pinnedQuestIds, discoveredMarkerIds, pinnedMapMarkerId, dynamicMapMarkers, dynamicQuestDefinitions);
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, Collection<String> failedQuestIds, Collection<String> pinnedQuestIds, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId, Collection<LostTalesMapMarkerDefinition> dynamicMapMarkers, Collection<LostTalesQuestDefinition> dynamicQuestDefinitions) {
        if (activeQuests != null) {
            for (LostTalesQuestProgress progress : activeQuests) {
                if (progress != null) {
                    this.activeQuests.add(progress.copy());
                }
            }
        }
        if (completedQuestIds != null) {
            for (String questId : completedQuestIds) {
                if (questId != null && questId.length() > 0) {
                    this.completedQuestIds.add(questId);
                }
            }
        }
        if (failedQuestIds != null) {
            for (String questId : failedQuestIds) {
                if (questId != null && questId.length() > 0) {
                    this.failedQuestIds.add(questId);
                }
            }
        }
        if (pinnedQuestIds != null) {
            for (String questId : pinnedQuestIds) {
                if (questId != null && questId.length() > 0) {
                    this.pinnedQuestIds.add(questId);
                }
            }
        }
        if (discoveredMarkerIds != null) {
            for (String markerId : discoveredMarkerIds) {
                if (markerId != null && markerId.length() > 0) {
                    this.discoveredMarkerIds.add(markerId);
                }
            }
        }
        if (dynamicMapMarkers != null) {
            for (LostTalesMapMarkerDefinition marker : dynamicMapMarkers) {
                if (marker != null && marker.getId() != null && marker.getId().length() > 0) {
                    this.dynamicMapMarkers.add(marker);
                }
            }
        }
        if (dynamicQuestDefinitions != null) {
            for (LostTalesQuestDefinition quest : dynamicQuestDefinitions) {
                if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                    this.dynamicQuestDefinitions.add(quest);
                }
            }
        }
        this.pinnedMapMarkerId = pinnedMapMarkerId == null ? "" : pinnedMapMarkerId;
    }

    public static LostTalesQuestSyncPacket fromPlayerData(LostTalesQuestPlayerData data) {
        if (data == null) {
            return new LostTalesQuestSyncPacket();
        }
        return new LostTalesQuestSyncPacket(data.getActiveQuests(), data.getCompletedQuestIds(), data.getFailedQuestIds(), data.getPinnedQuestIds(), data.getDiscoveredMarkerIds(), data.getPinnedMapMarkerId(), data.getDynamicMapMarkers(), data.getDynamicQuestDefinitions());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.activeQuests.clear();
        this.completedQuestIds.clear();
        this.failedQuestIds.clear();
        this.discoveredMarkerIds.clear();
        this.pinnedQuestIds.clear();
        this.dynamicMapMarkers.clear();
        this.dynamicQuestDefinitions.clear();
        this.pinnedMapMarkerId = "";

        int activeCount = buf.readInt();
        for (int i = 0; i < activeCount; i++) {
            String questId = ByteBufUtils.readUTF8String(buf);
            int stageIndex = buf.readInt();
            String stageId = ByteBufUtils.readUTF8String(buf);
            long acceptedWorldTime = buf.readLong();
            long deadlineWorldTime = buf.readLong();

            Map<String, Integer> objectiveProgress = new LinkedHashMap<String, Integer>();
            int objectiveCount = buf.readInt();
            for (int j = 0; j < objectiveCount; j++) {
                String objectiveId = ByteBufUtils.readUTF8String(buf);
                int progress = buf.readInt();
                if (objectiveId != null && objectiveId.length() > 0) {
                    objectiveProgress.put(objectiveId, Math.max(0, progress));
                }
            }

            if (questId != null && questId.length() > 0) {
                this.activeQuests.add(new LostTalesQuestProgress(questId, stageIndex, stageId, objectiveProgress, acceptedWorldTime, deadlineWorldTime));
            }
        }

        // Legacy first tracked quest, kept for old getter compatibility.
        String legacyPinnedQuestId = ByteBufUtils.readUTF8String(buf);
        if (legacyPinnedQuestId != null && legacyPinnedQuestId.length() > 0) {
            this.pinnedQuestIds.add(legacyPinnedQuestId);
        }

        int pinnedQuestCount = buf.readInt();
        for (int i = 0; i < pinnedQuestCount; i++) {
            String questId = ByteBufUtils.readUTF8String(buf);
            if (questId != null && questId.length() > 0) {
                this.pinnedQuestIds.add(questId);
            }
        }

        this.pinnedMapMarkerId = ByteBufUtils.readUTF8String(buf);

        int markerCount = buf.readInt();
        for (int i = 0; i < markerCount; i++) {
            String markerId = ByteBufUtils.readUTF8String(buf);
            if (markerId != null && markerId.length() > 0) {
                this.discoveredMarkerIds.add(markerId);
            }
        }

        int completedCount = buf.readInt();
        for (int i = 0; i < completedCount; i++) {
            String questId = ByteBufUtils.readUTF8String(buf);
            if (questId != null && questId.length() > 0) {
                this.completedQuestIds.add(questId);
            }
        }

        int failedCount = buf.readInt();
        for (int i = 0; i < failedCount; i++) {
            String questId = ByteBufUtils.readUTF8String(buf);
            if (questId != null && questId.length() > 0) {
                this.failedQuestIds.add(questId);
            }
        }

        int dynamicMarkerCount = buf.readInt();
        for (int i = 0; i < dynamicMarkerCount; i++) {
            String markerId = ByteBufUtils.readUTF8String(buf);
            String name = ByteBufUtils.readUTF8String(buf);
            String icon = ByteBufUtils.readUTF8String(buf);
            String color = ByteBufUtils.readUTF8String(buf);
            String category = ByteBufUtils.readUTF8String(buf);
            boolean hasFastTravel = buf.readBoolean();
            int dimensionId = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            double compassFadeInRadius = buf.readDouble();
            double discoveryRadius = buf.readDouble();
            boolean hidden = buf.readBoolean();
            boolean discoverable = buf.readBoolean();
            boolean requiresRegionUnlock = buf.readBoolean();
            if (markerId != null && markerId.length() > 0) {
                this.dynamicMapMarkers.add(
                        new LostTalesMapMarkerDefinition(
                                markerId, name, icon, color, category, "",
                                hasFastTravel, "", dimensionId, x, y, z,
                                compassFadeInRadius, discoveryRadius, hidden,
                                discoverable, requiresRegionUnlock));
            }
        }

        int dynamicQuestCount = buf.readInt();
        for (int i = 0; i < dynamicQuestCount; i++) {
            LostTalesQuestDefinition quest = readQuestDefinition(buf);
            if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                this.dynamicQuestDefinitions.add(quest);
            }
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.activeQuests.size());
        for (LostTalesQuestProgress progress : this.activeQuests) {
            ByteBufUtils.writeUTF8String(buf, safe(progress.getQuestId()));
            buf.writeInt(progress.getStageIndex());
            ByteBufUtils.writeUTF8String(buf, safe(progress.getStageId()));
            buf.writeLong(progress.getAcceptedWorldTime());
            buf.writeLong(progress.getDeadlineWorldTime());

            Map<String, Integer> objectiveProgress = progress.getObjectiveProgress();
            buf.writeInt(objectiveProgress.size());
            for (Map.Entry<String, Integer> entry : objectiveProgress.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, safe(entry.getKey()));
                buf.writeInt(entry.getValue() == null ? 0 : Math.max(0, entry.getValue()));
            }
        }

        ByteBufUtils.writeUTF8String(buf, safe(getPinnedQuestId()));
        buf.writeInt(this.pinnedQuestIds.size());
        for (String questId : this.pinnedQuestIds) {
            ByteBufUtils.writeUTF8String(buf, safe(questId));
        }
        ByteBufUtils.writeUTF8String(buf, safe(this.pinnedMapMarkerId));

        buf.writeInt(this.discoveredMarkerIds.size());
        for (String markerId : this.discoveredMarkerIds) {
            ByteBufUtils.writeUTF8String(buf, safe(markerId));
        }

        buf.writeInt(this.completedQuestIds.size());
        for (String questId : this.completedQuestIds) {
            ByteBufUtils.writeUTF8String(buf, safe(questId));
        }

        buf.writeInt(this.failedQuestIds.size());
        for (String questId : this.failedQuestIds) {
            ByteBufUtils.writeUTF8String(buf, safe(questId));
        }

        buf.writeInt(this.dynamicMapMarkers.size());
        for (LostTalesMapMarkerDefinition marker : this.dynamicMapMarkers) {
            ByteBufUtils.writeUTF8String(buf, safe(marker.getId()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getName()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getIconName()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getColorName()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getCategoryName()));
            buf.writeBoolean(marker.hasFastTravel());
            buf.writeInt(marker.getDimensionId());
            buf.writeDouble(marker.getX());
            buf.writeDouble(marker.getY());
            buf.writeDouble(marker.getZ());
            buf.writeDouble(marker.getCompassFadeInRadius());
            buf.writeDouble(marker.getDiscoveryRadius());
            buf.writeBoolean(marker.isHiddenUntilDiscovered());
            buf.writeBoolean(marker.isDiscoverable());
            buf.writeBoolean(marker.requiresRegionUnlock());
        }

        buf.writeInt(this.dynamicQuestDefinitions.size());
        for (LostTalesQuestDefinition quest : this.dynamicQuestDefinitions) {
            writeQuestDefinition(buf, quest);
        }
    }

    public List<LostTalesQuestProgress> getActiveQuests() {
        List<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : this.activeQuests) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public Set<String> getCompletedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.completedQuestIds));
    }

    public Set<String> getFailedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.failedQuestIds));
    }

    public Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.discoveredMarkerIds));
    }

    public List<LostTalesMapMarkerDefinition> getDynamicMapMarkers() {
        return Collections.unmodifiableList(new ArrayList<LostTalesMapMarkerDefinition>(this.dynamicMapMarkers));
    }

    public List<LostTalesQuestDefinition> getDynamicQuestDefinitions() {
        return Collections.unmodifiableList(new ArrayList<LostTalesQuestDefinition>(this.dynamicQuestDefinitions));
    }

    public String getPinnedQuestId() {
        for (String questId : this.pinnedQuestIds) {
            return questId == null ? "" : questId;
        }
        return "";
    }

    public Set<String> getPinnedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.pinnedQuestIds));
    }

    public String getPinnedMapMarkerId() {
        return this.pinnedMapMarkerId == null ? "" : this.pinnedMapMarkerId;
    }

    private static void writeQuestDefinition(ByteBuf buf, LostTalesQuestDefinition quest) {
        ByteBufUtils.writeUTF8String(buf, safeStatic(quest == null ? "" : quest.getId()));
        ByteBufUtils.writeUTF8String(buf, safeStatic(quest == null ? "" : quest.getTitle()));
        ByteBufUtils.writeUTF8String(buf, safeStatic(quest == null ? "" : quest.getDescription()));
        buf.writeBoolean(quest != null && quest.isRepeatable());
        ByteBufUtils.writeUTF8String(buf, safeStatic(quest == null ? "" : quest.getStartMode()));
        writeStringMap(buf, quest == null ? null : quest.getPrerequisites());
        writeStringMap(buf, quest == null ? null : quest.getRewards());
        writeStringMap(buf, quest == null ? null : quest.getInteraction());
        writeStringMap(buf, quest == null ? null : quest.getMarkers());
        writeStringMap(buf, quest == null ? null : quest.getJournalLog());

        List<LostTalesQuestStageDefinition> stages = quest == null ? Collections.<LostTalesQuestStageDefinition>emptyList() : quest.getStages();
        buf.writeInt(stages.size());
        for (LostTalesQuestStageDefinition stage : stages) {
            ByteBufUtils.writeUTF8String(buf, safeStatic(stage == null ? "" : stage.getId()));
            List<LostTalesQuestObjectiveDefinition> objectives = stage == null ? Collections.<LostTalesQuestObjectiveDefinition>emptyList() : stage.getObjectives();
            buf.writeInt(objectives.size());
            for (LostTalesQuestObjectiveDefinition objective : objectives) {
                ByteBufUtils.writeUTF8String(buf, safeStatic(objective == null ? "" : objective.getId()));
                ByteBufUtils.writeUTF8String(buf, safeStatic(objective == null ? "" : objective.getType()));
                ByteBufUtils.writeUTF8String(buf, safeStatic(objective == null ? "" : objective.getDescription()));
                buf.writeBoolean(objective != null && objective.isOptional());
                writeStringMap(buf, objective == null ? null : objective.getParams());
            }
        }
    }

    private static LostTalesQuestDefinition readQuestDefinition(ByteBuf buf) {
        String id = ByteBufUtils.readUTF8String(buf);
        String title = ByteBufUtils.readUTF8String(buf);
        String description = ByteBufUtils.readUTF8String(buf);
        boolean repeatable = buf.readBoolean();
        String startMode = ByteBufUtils.readUTF8String(buf);
        Map<String, String> prerequisites = readStringMap(buf);
        Map<String, String> rewards = readStringMap(buf);
        Map<String, String> interaction = readStringMap(buf);
        Map<String, String> markers = readStringMap(buf);
        Map<String, String> journalLog = readStringMap(buf);

        List<LostTalesQuestStageDefinition> stages = new ArrayList<LostTalesQuestStageDefinition>();
        int stageCount = Math.max(0, buf.readInt());
        for (int i = 0; i < stageCount; i++) {
            String stageId = ByteBufUtils.readUTF8String(buf);
            List<LostTalesQuestObjectiveDefinition> objectives = new ArrayList<LostTalesQuestObjectiveDefinition>();
            int objectiveCount = Math.max(0, buf.readInt());
            for (int j = 0; j < objectiveCount; j++) {
                String objectiveId = ByteBufUtils.readUTF8String(buf);
                String objectiveType = ByteBufUtils.readUTF8String(buf);
                String objectiveDescription = ByteBufUtils.readUTF8String(buf);
                boolean optional = buf.readBoolean();
                Map<String, String> params = readStringMap(buf);
                if (objectiveId != null && objectiveId.length() > 0 && objectiveType != null && objectiveType.length() > 0) {
                    objectives.add(new LostTalesQuestObjectiveDefinition(objectiveId, objectiveType, objectiveDescription, optional, params));
                }
            }
            stages.add(new LostTalesQuestStageDefinition(stageId, objectives));
        }

        if (id == null || id.length() == 0) {
            return null;
        }
        return new LostTalesQuestDefinition(id, title, description, repeatable, startMode, prerequisites, rewards, interaction, markers, journalLog, stages);
    }

    private static void writeStringMap(ByteBuf buf, Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            buf.writeInt(0);
            return;
        }
        buf.writeInt(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            ByteBufUtils.writeUTF8String(buf, safeStatic(entry.getKey()));
            ByteBufUtils.writeUTF8String(buf, safeStatic(entry.getValue()));
        }
    }

    private static Map<String, String> readStringMap(ByteBuf buf) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        int count = Math.max(0, buf.readInt());
        for (int i = 0; i < count; i++) {
            String key = ByteBufUtils.readUTF8String(buf);
            String value = ByteBufUtils.readUTF8String(buf);
            if (key != null && key.length() > 0) {
                map.put(key, value == null ? "" : value);
            }
        }
        return map;
    }

    private static String safeStatic(String value) {
        return value == null ? "" : value;
    }

    private static Set<String> singlePinned(String questId) {
        LinkedHashSet<String> pinned = new LinkedHashSet<String>();
        if (questId != null && questId.length() > 0) {
            pinned.add(questId);
        }
        return pinned;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    /** Common-safe clientbound handler; real client work is delegated to the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesQuestSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesQuestSyncPacket message, MessageContext ctx) {
            if (LostTalesMod.proxy != null) {
                LostTalesMod.proxy.handleQuestSync(message);
            }
            return null;
        }
    }
}
