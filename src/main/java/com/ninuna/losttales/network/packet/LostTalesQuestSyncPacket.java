package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
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
 * This packet intentionally stays snapshot-based for Forge 1.7.10 simplicity. It
 * carries active/completed quests, objective counters, the tracked quest, and the
 * player's discovered/tracked map marker IDs.
 */
public class LostTalesQuestSyncPacket implements IMessage {
    private final List<LostTalesQuestProgress> activeQuests = new ArrayList<LostTalesQuestProgress>();
    private final Set<String> completedQuestIds = new LinkedHashSet<String>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final List<LostTalesMapMarkerDefinition> dynamicMapMarkers = new ArrayList<LostTalesMapMarkerDefinition>();
    private String pinnedQuestId = "";
    private String pinnedMapMarkerId = "";

    public LostTalesQuestSyncPacket() {}

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds) {
        this(activeQuests, completedQuestIds, "", Collections.<String>emptySet(), "", Collections.<LostTalesMapMarkerDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestId) {
        this(activeQuests, completedQuestIds, pinnedQuestId, Collections.<String>emptySet(), "", Collections.<LostTalesMapMarkerDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestId, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId) {
        this(activeQuests, completedQuestIds, pinnedQuestId, discoveredMarkerIds, pinnedMapMarkerId, Collections.<LostTalesMapMarkerDefinition>emptyList());
    }

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestId, Collection<String> discoveredMarkerIds, String pinnedMapMarkerId, Collection<LostTalesMapMarkerDefinition> dynamicMapMarkers) {
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
        this.pinnedQuestId = pinnedQuestId == null ? "" : pinnedQuestId;
        this.pinnedMapMarkerId = pinnedMapMarkerId == null ? "" : pinnedMapMarkerId;
    }

    public static LostTalesQuestSyncPacket fromPlayerData(LostTalesQuestPlayerData data) {
        if (data == null) {
            return new LostTalesQuestSyncPacket();
        }
        return new LostTalesQuestSyncPacket(data.getActiveQuests(), data.getCompletedQuestIds(), data.getPinnedQuestId(), data.getDiscoveredMarkerIds(), data.getPinnedMapMarkerId(), data.getDynamicMapMarkers());
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.activeQuests.clear();
        this.completedQuestIds.clear();
        this.discoveredMarkerIds.clear();
        this.dynamicMapMarkers.clear();
        this.pinnedQuestId = "";
        this.pinnedMapMarkerId = "";

        int activeCount = buf.readInt();
        for (int i = 0; i < activeCount; i++) {
            String questId = ByteBufUtils.readUTF8String(buf);
            int stageIndex = buf.readInt();
            String stageId = ByteBufUtils.readUTF8String(buf);

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
                this.activeQuests.add(new LostTalesQuestProgress(questId, stageIndex, stageId, objectiveProgress));
            }
        }

        this.pinnedQuestId = ByteBufUtils.readUTF8String(buf);
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

        int dynamicMarkerCount = buf.readInt();
        for (int i = 0; i < dynamicMarkerCount; i++) {
            String markerId = ByteBufUtils.readUTF8String(buf);
            String name = ByteBufUtils.readUTF8String(buf);
            String icon = ByteBufUtils.readUTF8String(buf);
            String color = ByteBufUtils.readUTF8String(buf);
            int dimensionId = buf.readInt();
            double x = buf.readDouble();
            double y = buf.readDouble();
            double z = buf.readDouble();
            boolean hidden = buf.readBoolean();
            if (markerId != null && markerId.length() > 0) {
                this.dynamicMapMarkers.add(new LostTalesMapMarkerDefinition(markerId, name, icon, color, dimensionId, x, y, z, hidden));
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

            Map<String, Integer> objectiveProgress = progress.getObjectiveProgress();
            buf.writeInt(objectiveProgress.size());
            for (Map.Entry<String, Integer> entry : objectiveProgress.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, safe(entry.getKey()));
                buf.writeInt(entry.getValue() == null ? 0 : Math.max(0, entry.getValue()));
            }
        }

        ByteBufUtils.writeUTF8String(buf, safe(this.pinnedQuestId));
        ByteBufUtils.writeUTF8String(buf, safe(this.pinnedMapMarkerId));

        buf.writeInt(this.discoveredMarkerIds.size());
        for (String markerId : this.discoveredMarkerIds) {
            ByteBufUtils.writeUTF8String(buf, safe(markerId));
        }

        buf.writeInt(this.completedQuestIds.size());
        for (String questId : this.completedQuestIds) {
            ByteBufUtils.writeUTF8String(buf, safe(questId));
        }

        buf.writeInt(this.dynamicMapMarkers.size());
        for (LostTalesMapMarkerDefinition marker : this.dynamicMapMarkers) {
            ByteBufUtils.writeUTF8String(buf, safe(marker.getId()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getName()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getIconName()));
            ByteBufUtils.writeUTF8String(buf, safe(marker.getColorName()));
            buf.writeInt(marker.getDimensionId());
            buf.writeDouble(marker.getX());
            buf.writeDouble(marker.getY());
            buf.writeDouble(marker.getZ());
            buf.writeBoolean(marker.isHiddenUntilDiscovered());
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

    public Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.discoveredMarkerIds));
    }

    public List<LostTalesMapMarkerDefinition> getDynamicMapMarkers() {
        return Collections.unmodifiableList(new ArrayList<LostTalesMapMarkerDefinition>(this.dynamicMapMarkers));
    }

    public String getPinnedQuestId() {
        return this.pinnedQuestId == null ? "" : this.pinnedQuestId;
    }

    public String getPinnedMapMarkerId() {
        return this.pinnedMapMarkerId == null ? "" : this.pinnedMapMarkerId;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
