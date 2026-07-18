package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.LostTalesQuestObjectiveDefinition;
import com.ninuna.losttales.quest.LostTalesQuestStageDefinition;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
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
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    static final int MAX_ACTIVE_QUESTS = 1024;
    static final int MAX_QUEST_ID_COLLECTION = 8192;
    static final int MAX_OBJECTIVE_PROGRESS = 512;
    static final int MAX_DYNAMIC_MARKERS = 2048;
    static final int MAX_DYNAMIC_QUESTS = 512;
    static final int MAX_QUEST_STAGES = 256;
    static final int MAX_STAGE_OBJECTIVES = 512;
    static final int MAX_STRING_MAP_ENTRIES = 256;
    static final int MAX_IDENTIFIER_BYTES = 256;
    static final int MAX_NAME_BYTES = 1024;
    static final int MAX_TEXT_BYTES = 8192;
    static final int MAX_MAP_VALUE_BYTES = 4096;

    private final List<LostTalesQuestProgress> activeQuests = new ArrayList<LostTalesQuestProgress>();
    private final Set<String> completedQuestIds = new LinkedHashSet<String>();
    private final Set<String> failedQuestIds = new LinkedHashSet<String>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Set<String> pinnedQuestIds = new LinkedHashSet<String>();
    private final List<LostTalesMapMarkerDefinition> dynamicMapMarkers = new ArrayList<LostTalesMapMarkerDefinition>();
    private final List<LostTalesQuestDefinition> dynamicQuestDefinitions = new ArrayList<LostTalesQuestDefinition>();
    private String pinnedMapMarkerId = "";
    private boolean malformed;

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
        clearState();
        this.malformed = false;
        try {
            if (buf == null || buf.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid quest-sync packet size");
            }

            int activeCount = LostTalesPacketCodec.readCount(
                    buf, MAX_ACTIVE_QUESTS, "active quest");
            for (int i = 0; i < activeCount; i++) {
                String questId = readIdentifier(buf);
                int stageIndex = buf.readInt();
                String stageId = readIdentifier(buf);
                long acceptedWorldTime = buf.readLong();
                long deadlineWorldTime = buf.readLong();
                if (stageIndex < 0 || acceptedWorldTime < 0L
                        || deadlineWorldTime < 0L
                        || deadlineWorldTime > 0L
                        && acceptedWorldTime > 0L
                        && deadlineWorldTime < acceptedWorldTime) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid active quest timing");
                }

                Map<String, Integer> objectiveProgress =
                        new LinkedHashMap<String, Integer>();
                int objectiveCount = LostTalesPacketCodec.readCount(
                        buf, MAX_OBJECTIVE_PROGRESS, "objective progress");
                for (int j = 0; j < objectiveCount; j++) {
                    String objectiveId = readIdentifier(buf);
                    int progress = buf.readInt();
                    if (objectiveId.length() == 0 || progress < 0) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "invalid objective progress");
                    }
                    objectiveProgress.put(objectiveId, progress);
                }

                if (questId.length() == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "active quest ID is empty");
                }
                this.activeQuests.add(new LostTalesQuestProgress(
                        questId, stageIndex, stageId, objectiveProgress,
                        acceptedWorldTime, deadlineWorldTime));
            }

            // Retained on the wire for compatibility with older clients.
            addIfPresent(this.pinnedQuestIds, readIdentifier(buf));

            int pinnedQuestCount = LostTalesPacketCodec.readCount(
                    buf, MAX_QUEST_ID_COLLECTION, "pinned quest");
            for (int i = 0; i < pinnedQuestCount; i++) {
                addIfPresent(this.pinnedQuestIds, readIdentifier(buf));
            }

            this.pinnedMapMarkerId = readIdentifier(buf);
            readIdentifierSet(buf, this.discoveredMarkerIds,
                    "discovered marker");
            readIdentifierSet(buf, this.completedQuestIds,
                    "completed quest");
            readIdentifierSet(buf, this.failedQuestIds, "failed quest");

            int dynamicMarkerCount = LostTalesPacketCodec.readCount(
                    buf, MAX_DYNAMIC_MARKERS, "dynamic marker");
            for (int i = 0; i < dynamicMarkerCount; i++) {
                String markerId = readIdentifier(buf);
                String name = readName(buf);
                String icon = readIdentifier(buf);
                String color = readIdentifier(buf);
                String category = readName(buf);
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
                if (markerId.length() == 0
                        || !isFinite(x) || !isFinite(y) || !isFinite(z)
                        || !isFinite(compassFadeInRadius)
                        || !isFinite(discoveryRadius)
                        || compassFadeInRadius < 0.0D
                        || discoveryRadius < 0.0D) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid dynamic marker");
                }
                this.dynamicMapMarkers.add(
                        new LostTalesMapMarkerDefinition(
                                markerId, name, icon, color, category, "",
                                hasFastTravel, "", dimensionId, x, y, z,
                                compassFadeInRadius, discoveryRadius, hidden,
                                discoverable, requiresRegionUnlock));
            }

            int dynamicQuestCount = LostTalesPacketCodec.readCount(
                    buf, MAX_DYNAMIC_QUESTS, "dynamic quest");
            for (int i = 0; i < dynamicQuestCount; i++) {
                LostTalesQuestDefinition quest = readQuestDefinition(buf);
                if (quest == null || quest.getId() == null
                        || quest.getId().length() == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid dynamic quest");
                }
                this.dynamicQuestDefinitions.add(quest);
            }
            LostTalesPacketCodec.requireFinished(buf);
        } catch (RuntimeException exception) {
            clearState();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buf);
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int startIndex = buf.writerIndex();
        LostTalesPacketCodec.writeCount(buf, this.activeQuests.size(),
                MAX_ACTIVE_QUESTS, "active quest");
        for (LostTalesQuestProgress progress : this.activeQuests) {
            if (progress == null || progress.getStageIndex() < 0
                    || progress.getAcceptedWorldTime() < 0L
                    || progress.getDeadlineWorldTime() < 0L
                    || progress.getDeadlineWorldTime() > 0L
                    && progress.getAcceptedWorldTime() > 0L
                    && progress.getDeadlineWorldTime()
                    < progress.getAcceptedWorldTime()) {
                throw new IllegalStateException("invalid active quest");
            }
            writeIdentifier(buf, progress.getQuestId());
            buf.writeInt(progress.getStageIndex());
            writeIdentifier(buf, progress.getStageId());
            buf.writeLong(progress.getAcceptedWorldTime());
            buf.writeLong(progress.getDeadlineWorldTime());

            Map<String, Integer> objectiveProgress = progress.getObjectiveProgress();
            LostTalesPacketCodec.writeCount(buf, objectiveProgress.size(),
                    MAX_OBJECTIVE_PROGRESS, "objective progress");
            for (Map.Entry<String, Integer> entry : objectiveProgress.entrySet()) {
                if (entry.getKey() == null || entry.getKey().length() == 0
                        || entry.getValue() == null
                        || entry.getValue() < 0) {
                    throw new IllegalStateException(
                            "invalid objective progress");
                }
                writeIdentifier(buf, entry.getKey());
                buf.writeInt(entry.getValue());
            }
        }

        writeIdentifier(buf, getPinnedQuestId());
        writeIdentifierSet(buf, this.pinnedQuestIds, "pinned quest");
        writeIdentifier(buf, this.pinnedMapMarkerId);
        writeIdentifierSet(buf, this.discoveredMarkerIds,
                "discovered marker");
        writeIdentifierSet(buf, this.completedQuestIds, "completed quest");
        writeIdentifierSet(buf, this.failedQuestIds, "failed quest");

        LostTalesPacketCodec.writeCount(buf, this.dynamicMapMarkers.size(),
                MAX_DYNAMIC_MARKERS, "dynamic marker");
        for (LostTalesMapMarkerDefinition marker : this.dynamicMapMarkers) {
            if (!isValidMarker(marker)) {
                throw new IllegalStateException("invalid dynamic marker");
            }
            writeIdentifier(buf, marker.getId());
            writeName(buf, marker.getName());
            writeIdentifier(buf, marker.getIconName());
            writeIdentifier(buf, marker.getColorName());
            writeName(buf, marker.getCategoryName());
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

        LostTalesPacketCodec.writeCount(buf,
                this.dynamicQuestDefinitions.size(), MAX_DYNAMIC_QUESTS,
                "dynamic quest");
        for (LostTalesQuestDefinition quest : this.dynamicQuestDefinitions) {
            writeQuestDefinition(buf, quest);
        }
        if (buf.writerIndex() - startIndex > MAX_PACKET_BYTES) {
            throw new IllegalStateException(
                    "quest snapshot exceeds packet limit");
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

    public boolean isMalformed() {
        return this.malformed;
    }

    private static void writeQuestDefinition(ByteBuf buf, LostTalesQuestDefinition quest) {
        if (quest == null || quest.getId() == null
                || quest.getId().length() == 0) {
            throw new IllegalStateException("invalid dynamic quest");
        }
        writeIdentifier(buf, quest.getId());
        writeName(buf, quest.getTitle());
        writeText(buf, quest.getDescription());
        buf.writeBoolean(quest.isRepeatable());
        writeIdentifier(buf, quest.getStartMode());
        writeStringMap(buf, quest.getPrerequisites(), "prerequisite");
        writeStringMap(buf, quest.getRewards(), "reward");
        writeStringMap(buf, quest.getInteraction(), "interaction");
        writeStringMap(buf, quest.getMarkers(), "quest marker");
        writeStringMap(buf, quest.getJournalLog(), "journal log");

        List<LostTalesQuestStageDefinition> stages = quest.getStages();
        LostTalesPacketCodec.writeCount(buf, stages.size(),
                MAX_QUEST_STAGES, "quest stage");
        for (LostTalesQuestStageDefinition stage : stages) {
            if (stage == null) {
                throw new IllegalStateException("invalid quest stage");
            }
            writeIdentifier(buf, stage.getId());
            List<LostTalesQuestObjectiveDefinition> objectives =
                    stage.getObjectives();
            LostTalesPacketCodec.writeCount(buf, objectives.size(),
                    MAX_STAGE_OBJECTIVES, "stage objective");
            for (LostTalesQuestObjectiveDefinition objective : objectives) {
                if (objective == null || objective.getId() == null
                        || objective.getId().length() == 0
                        || objective.getType() == null
                        || objective.getType().length() == 0) {
                    throw new IllegalStateException(
                            "invalid quest objective");
                }
                writeIdentifier(buf, objective.getId());
                writeIdentifier(buf, objective.getType());
                writeText(buf, objective.getDescription());
                buf.writeBoolean(objective.isOptional());
                writeStringMap(buf, objective.getParams(),
                        "objective parameter");
            }
        }
    }

    private static LostTalesQuestDefinition readQuestDefinition(ByteBuf buf) {
        String id = readIdentifier(buf);
        String title = readName(buf);
        String description = readText(buf);
        boolean repeatable = buf.readBoolean();
        String startMode = readIdentifier(buf);
        Map<String, String> prerequisites = readStringMap(
                buf, "prerequisite");
        Map<String, String> rewards = readStringMap(buf, "reward");
        Map<String, String> interaction = readStringMap(buf, "interaction");
        Map<String, String> markers = readStringMap(buf, "quest marker");
        Map<String, String> journalLog = readStringMap(buf, "journal log");

        List<LostTalesQuestStageDefinition> stages = new ArrayList<LostTalesQuestStageDefinition>();
        int stageCount = LostTalesPacketCodec.readCount(
                buf, MAX_QUEST_STAGES, "quest stage");
        for (int i = 0; i < stageCount; i++) {
            String stageId = readIdentifier(buf);
            List<LostTalesQuestObjectiveDefinition> objectives = new ArrayList<LostTalesQuestObjectiveDefinition>();
            int objectiveCount = LostTalesPacketCodec.readCount(
                    buf, MAX_STAGE_OBJECTIVES, "stage objective");
            for (int j = 0; j < objectiveCount; j++) {
                String objectiveId = readIdentifier(buf);
                String objectiveType = readIdentifier(buf);
                String objectiveDescription = readText(buf);
                boolean optional = buf.readBoolean();
                Map<String, String> params = readStringMap(
                        buf, "objective parameter");
                if (objectiveId.length() == 0
                        || objectiveType.length() == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid quest objective");
                }
                objectives.add(new LostTalesQuestObjectiveDefinition(
                        objectiveId, objectiveType, objectiveDescription,
                        optional, params));
            }
            stages.add(new LostTalesQuestStageDefinition(stageId, objectives));
        }

        if (id.length() == 0) {
            throw new LostTalesPacketCodec.DecodeException(
                    "dynamic quest ID is empty");
        }
        return new LostTalesQuestDefinition(id, title, description, repeatable, startMode, prerequisites, rewards, interaction, markers, journalLog, stages);
    }

    private static void writeStringMap(ByteBuf buf, Map<String, String> values,
                                       String fieldName) {
        if (values == null || values.isEmpty()) {
            buf.writeInt(0);
            return;
        }
        LostTalesPacketCodec.writeCount(buf, values.size(),
                MAX_STRING_MAP_ENTRIES, fieldName);
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().length() == 0) {
                throw new IllegalStateException(
                        "invalid " + fieldName + " key");
            }
            writeIdentifier(buf, entry.getKey());
            LostTalesPacketCodec.writeUtf8String(
                    buf, safeStatic(entry.getValue()), MAX_MAP_VALUE_BYTES);
        }
    }

    private static Map<String, String> readStringMap(ByteBuf buf,
                                                     String fieldName) {
        LinkedHashMap<String, String> map = new LinkedHashMap<String, String>();
        int count = LostTalesPacketCodec.readCount(
                buf, MAX_STRING_MAP_ENTRIES, fieldName);
        for (int i = 0; i < count; i++) {
            String key = readIdentifier(buf);
            String value = LostTalesPacketCodec.readUtf8String(
                    buf, MAX_MAP_VALUE_BYTES);
            if (key.length() == 0) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid " + fieldName + " key");
            }
            map.put(key, value);
        }
        return map;
    }

    private void clearState() {
        this.activeQuests.clear();
        this.completedQuestIds.clear();
        this.failedQuestIds.clear();
        this.discoveredMarkerIds.clear();
        this.pinnedQuestIds.clear();
        this.dynamicMapMarkers.clear();
        this.dynamicQuestDefinitions.clear();
        this.pinnedMapMarkerId = "";
    }

    private static void readIdentifierSet(ByteBuf buf, Set<String> target,
                                          String fieldName) {
        int count = LostTalesPacketCodec.readCount(
                buf, MAX_QUEST_ID_COLLECTION, fieldName);
        for (int i = 0; i < count; i++) {
            String value = readIdentifier(buf);
            if (value.length() == 0) {
                throw new LostTalesPacketCodec.DecodeException(
                        "empty " + fieldName + " ID");
            }
            target.add(value);
        }
    }

    private static void writeIdentifierSet(ByteBuf buf, Set<String> values,
                                           String fieldName) {
        LostTalesPacketCodec.writeCount(buf, values.size(),
                MAX_QUEST_ID_COLLECTION, fieldName);
        for (String value : values) {
            if (value == null || value.length() == 0) {
                throw new IllegalStateException(
                        "empty " + fieldName + " ID");
            }
            writeIdentifier(buf, value);
        }
    }

    private static void addIfPresent(Set<String> target, String value) {
        if (value != null && value.length() > 0) {
            target.add(value);
        }
    }

    private static String readIdentifier(ByteBuf buf) {
        return LostTalesPacketCodec.readUtf8String(
                buf, MAX_IDENTIFIER_BYTES);
    }

    private static String readName(ByteBuf buf) {
        return LostTalesPacketCodec.readUtf8String(buf, MAX_NAME_BYTES);
    }

    private static String readText(ByteBuf buf) {
        return LostTalesPacketCodec.readUtf8String(buf, MAX_TEXT_BYTES);
    }

    private static void writeIdentifier(ByteBuf buf, String value) {
        LostTalesPacketCodec.writeUtf8String(
                buf, safeStatic(value), MAX_IDENTIFIER_BYTES);
    }

    private static void writeName(ByteBuf buf, String value) {
        LostTalesPacketCodec.writeUtf8String(
                buf, safeStatic(value), MAX_NAME_BYTES);
    }

    private static void writeText(ByteBuf buf, String value) {
        LostTalesPacketCodec.writeUtf8String(
                buf, safeStatic(value), MAX_TEXT_BYTES);
    }

    private static boolean isValidMarker(
            LostTalesMapMarkerDefinition marker) {
        return marker != null && marker.getId() != null
                && marker.getId().length() > 0
                && isFinite(marker.getX()) && isFinite(marker.getY())
                && isFinite(marker.getZ())
                && isFinite(marker.getCompassFadeInRadius())
                && isFinite(marker.getDiscoveryRadius())
                && marker.getCompassFadeInRadius() >= 0.0D
                && marker.getDiscoveryRadius() >= 0.0D;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
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

    /** Common-safe clientbound handler; real client work is delegated to the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesQuestSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesQuestSyncPacket message,
                MessageContext ctx) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleQuestSync(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
