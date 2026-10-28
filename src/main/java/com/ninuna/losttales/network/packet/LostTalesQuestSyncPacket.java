package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.quest.QuestRumourPlace;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.quest.player.WaitingReward;
import com.ninuna.losttales.quest.player.WaitingRewards;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
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
 * A player's whole quest log: running quests and their counts, the
 * History, tracked quests, discovered and placed markers, the missives'
 * quests, the rumours heard with the places they point to, and the
 * rewards waiting for room. It is sent when anything but a running
 * quest's counts
 * changes; counts alone travel as {@link LostTalesQuestProgressPacket}.
 */
public class LostTalesQuestSyncPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    static final int MAX_ACTIVE_QUESTS = 1024;
    static final int MAX_QUEST_ID_COLLECTION = 8192;
    static final int MAX_OBJECTIVE_PROGRESS = 512;
    static final int MAX_GIVER_MARKERS = 2048;
    static final int MAX_MISSIVES = 512;
    /** Quest ids, and the stage, objective and marker ids beside them, all within the one quest-id bound. */
    static final int MAX_IDENTIFIER_BYTES = LostTalesQuestIds.MAX_BYTES;
    static final int MAX_NAME_BYTES = 1024;
    static final int MAX_TEXT_BYTES = 8192;

    private final List<LostTalesQuestProgress> activeQuests = new ArrayList<LostTalesQuestProgress>();
    private final List<LostTalesQuestHistoryEntry> questHistory = new ArrayList<LostTalesQuestHistoryEntry>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Set<String> trackedQuestIds = new LinkedHashSet<String>();
    private final List<LostTalesMapMarkerDefinition> giverMarkers = new ArrayList<LostTalesMapMarkerDefinition>();
    private final List<LostTalesQuestDefinition> missiveDefinitions = new ArrayList<LostTalesQuestDefinition>();
    private String trackedMapMarkerId = "";
    private final Set<String> heardRumours = new LinkedHashSet<String>();
    /** Where those rumours point, by quest, each a quest among them (QUEST-20). */
    private final Map<String, QuestRumourPlace> rumourPlaces =
            new LinkedHashMap<String, QuestRumourPlace>();
    /** Whether the log is another character's, taken in place of the one held without news. */
    private boolean replaces;
    private boolean malformed;
    /** The rewards waiting for room, each quest's in order (QUEST-44). */
    private final Map<String, List<WaitingReward>> waitingRewards =
            new LinkedHashMap<String, List<WaitingReward>>();

    public LostTalesQuestSyncPacket() {}

    public LostTalesQuestSyncPacket(Collection<LostTalesQuestProgress> activeQuests, Collection<LostTalesQuestHistoryEntry> questHistory, Collection<String> trackedQuestIds, Collection<String> discoveredMarkerIds, String trackedMapMarkerId, Collection<LostTalesMapMarkerDefinition> giverMarkers, Collection<LostTalesQuestDefinition> missiveDefinitions) {
        if (activeQuests != null) {
            for (LostTalesQuestProgress progress : activeQuests) {
                if (progress != null) {
                    this.activeQuests.add(progress.copy());
                }
            }
        }
        if (questHistory != null) {
            for (LostTalesQuestHistoryEntry entry : questHistory) {
                if (entry != null && entry.getQuestId().length() > 0) {
                    this.questHistory.add(entry);
                }
            }
        }
        if (trackedQuestIds != null) {
            for (String questId : trackedQuestIds) {
                if (questId != null && questId.length() > 0) {
                    this.trackedQuestIds.add(questId);
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
        if (giverMarkers != null) {
            for (LostTalesMapMarkerDefinition marker : giverMarkers) {
                if (marker != null && marker.getId() != null && marker.getId().length() > 0) {
                    this.giverMarkers.add(marker);
                }
            }
        }
        if (missiveDefinitions != null) {
            for (LostTalesQuestDefinition quest : missiveDefinitions) {
                if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                    this.missiveDefinitions.add(quest);
                }
            }
        }
        this.trackedMapMarkerId = trackedMapMarkerId == null ? "" : trackedMapMarkerId;
    }

    public static LostTalesQuestSyncPacket fromPlayerData(LostTalesQuestPlayerData data) {
        if (data == null) {
            return new LostTalesQuestSyncPacket();
        }
        LostTalesQuestSyncPacket packet = new LostTalesQuestSyncPacket(data.getActiveQuests(),
                data.getQuestHistory(), data.getTrackedQuestIds(),
                data.getDiscoveredMarkerIds(), data.getTrackedMapMarkerId(),
                data.getGiverMarkers(),
                data.getMissiveDefinitions());
        return packet.withHeardRumours(data.getHeardRumours())
                .withWaitingRewards(data.getWaitingRewards());
    }

    /**
     * The same log with the rewards waiting for room (QUEST-44), each
     * quest's as many as a log keeps; a reward that could not be kept is
     * left out.
     */
    public LostTalesQuestSyncPacket withWaitingRewards(
            Map<String, List<WaitingReward>> rewards) {
        this.waitingRewards.clear();
        if (rewards == null) {
            return this;
        }
        for (Map.Entry<String, List<WaitingReward>> entry : rewards.entrySet()) {
            if (this.waitingRewards.size() >= WaitingRewards.MAX_QUESTS
                    || !LostTalesQuestIds.fits(entry.getKey())
                    || entry.getValue() == null) {
                continue;
            }
            List<WaitingReward> kept = new ArrayList<WaitingReward>();
            for (WaitingReward reward : entry.getValue()) {
                if (reward != null && reward.isValid()
                        && kept.size() < WaitingRewards.MAX_KINDS_EACH) {
                    kept.add(reward);
                }
            }
            if (!kept.isEmpty()) {
                this.waitingRewards.put(entry.getKey(),
                        Collections.unmodifiableList(kept));
            }
        }
        return this;
    }

    /** The rewards waiting for room, each quest's in order. */
    public Map<String, List<WaitingReward>> getWaitingRewards() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, List<WaitingReward>>(this.waitingRewards));
    }

    /**
     * The same log with the rumours its character heard (QUEST-20); a
     * place kept for a rumour not among them goes.
     */
    public LostTalesQuestSyncPacket withHeardRumours(Collection<String> rumours) {
        this.heardRumours.clear();
        if (rumours != null) {
            for (String questId : rumours) {
                if (questId != null && questId.length() > 0
                        && this.heardRumours.size() < LostTalesQuestPlayerData.MAX_RUMOURS) {
                    this.heardRumours.add(questId);
                }
            }
        }
        this.rumourPlaces.keySet().retainAll(this.heardRumours);
        return this;
    }

    /** The quests whose rumours the character heard. */
    public Set<String> getHeardRumours() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.heardRumours));
    }

    /**
     * The same log with the places its rumours point to (QUEST-20): each a
     * rumour the log holds, valid, one for each quest; others are left out.
     */
    public LostTalesQuestSyncPacket withRumourPlaces(
            Collection<QuestRumourPlace> places) {
        this.rumourPlaces.clear();
        if (places != null) {
            for (QuestRumourPlace place : places) {
                if (place != null && place.isValid()
                        && this.heardRumours.contains(place.getQuestId())
                        && !this.rumourPlaces.containsKey(place.getQuestId())) {
                    this.rumourPlaces.put(place.getQuestId(), place);
                }
            }
        }
        return this;
    }

    /** Where the rumours heard point, in the order they were heard. */
    public List<QuestRumourPlace> getRumourPlaces() {
        return Collections.unmodifiableList(
                new ArrayList<QuestRumourPlace>(this.rumourPlaces.values()));
    }

    /**
     * The same log as the one a character switch put in place: the client
     * takes it instead of the log it holds, with no banner and no news.
     */
    public LostTalesQuestSyncPacket replacing() {
        this.replaces = true;
        return this;
    }

    /** Whether the log replaces the one the client holds, as after a character switch. */
    public boolean isReplacement() {
        return this.replaces;
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
                this.activeQuests.add(readProgress(buf));
            }

            int trackedQuestCount = LostTalesPacketCodec.readCount(
                    buf, MAX_QUEST_ID_COLLECTION, "tracked quest");
            for (int i = 0; i < trackedQuestCount; i++) {
                addIfPresent(this.trackedQuestIds, readIdentifier(buf));
            }

            this.trackedMapMarkerId = readIdentifier(buf);
            readIdentifierSet(buf, this.discoveredMarkerIds,
                    "discovered marker");
            int historyCount = LostTalesPacketCodec.readCount(
                    buf, MAX_QUEST_ID_COLLECTION, "quest history");
            for (int i = 0; i < historyCount; i++) {
                String questId = readIdentifier(buf);
                LostTalesQuestHistoryEntry.Outcome outcome =
                        LostTalesQuestHistoryEntry.Outcome.fromName(
                                readIdentifier(buf));
                String detail = readText(buf);
                long worldTime = buf.readLong();
                if (questId.length() == 0 || outcome == null
                        || worldTime < 0L) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid quest history");
                }
                LinkedHashSet<String> completedOptionalObjectives =
                        new LinkedHashSet<String>();
                readIdentifierSet(buf, completedOptionalObjectives,
                        "completed optional objective",
                        MAX_OBJECTIVE_PROGRESS);
                int completions = buf.readInt();
                if (completions < 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid quest completions");
                }
                LinkedHashSet<String> stagePath = new LinkedHashSet<String>();
                readIdentifierSet(buf, stagePath, "stage path",
                        LostTalesQuestProgress.MAX_PATH_STAGES);
                this.questHistory.add(new LostTalesQuestHistoryEntry(
                        questId, outcome, detail, worldTime,
                        completedOptionalObjectives, completions, stagePath));
            }

            int giverMarkerCount = LostTalesPacketCodec.readCount(
                    buf, MAX_GIVER_MARKERS, "giver marker");
            for (int i = 0; i < giverMarkerCount; i++) {
                String markerId = readIdentifier(buf);
                String name = readName(buf);
                String namedAfter = LostTalesPacketCodec.readUtf8String(buf,
                        LostTalesMapMarkerNamedAfter.MAX_LENGTH);
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
                int priority = buf.readInt();
                if (markerId.length() == 0
                        || !LostTalesMapMarkerNamedAfter.isValid(namedAfter)
                        || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || !Double.isFinite(compassFadeInRadius)
                        || !Double.isFinite(discoveryRadius)
                        || compassFadeInRadius < 0.0D
                        || discoveryRadius < 0.0D) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid giver marker");
                }
                this.giverMarkers.add(
                        new LostTalesMapMarkerDefinition(
                                markerId, name, icon, color, category, "",
                                hasFastTravel, dimensionId, x, y, z,
                                compassFadeInRadius, discoveryRadius, hidden,
                                discoverable, requiresRegionUnlock,
                                LostTalesMapMarkerSource.QUEST_GIVER,
                                false, "", priority, namedAfter));
            }

            int missiveCount = LostTalesPacketCodec.readCount(
                    buf, MAX_MISSIVES, "missive");
            for (int i = 0; i < missiveCount; i++) {
                LostTalesQuestDefinition quest = LostTalesQuestDefinitionCodec.read(buf);
                if (quest == null || quest.getId() == null
                        || quest.getId().length() == 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid missive");
                }
                this.missiveDefinitions.add(quest);
            }
            readIdentifierSet(buf, this.heardRumours, "heard rumour",
                    LostTalesQuestPlayerData.MAX_RUMOURS);
            int placeCount = LostTalesPacketCodec.readCount(buf,
                    LostTalesQuestPlayerData.MAX_RUMOURS, "rumour place");
            for (int i = 0; i < placeCount; i++) {
                QuestRumourPlace place = new QuestRumourPlace(
                        readIdentifier(buf), readIdentifier(buf),
                        readName(buf), LostTalesPacketCodec.readUtf8String(buf,
                                LostTalesMapMarkerNamedAfter.MAX_LENGTH),
                        buf.readInt(), buf.readDouble(), buf.readDouble());
                if (!place.isValid()
                        || !this.heardRumours.contains(place.getQuestId())
                        || this.rumourPlaces.containsKey(place.getQuestId())
                        || !LostTalesMapMarkerNamedAfter.isValid(
                                place.getNamedAfter())) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid rumour place");
                }
                this.rumourPlaces.put(place.getQuestId(), place);
            }
            readWaitingRewards(buf, this.waitingRewards);
            this.replaces = buf.readBoolean();
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
            writeProgress(buf, progress);
        }

        writeIdentifierSet(buf, this.trackedQuestIds, "tracked quest");
        writeIdentifier(buf, this.trackedMapMarkerId);
        writeIdentifierSet(buf, this.discoveredMarkerIds,
                "discovered marker");
        LostTalesPacketCodec.writeCount(buf, this.questHistory.size(),
                MAX_QUEST_ID_COLLECTION, "quest history");
        for (LostTalesQuestHistoryEntry entry : this.questHistory) {
            if (entry == null || entry.getQuestId().length() == 0
                    || entry.getOutcome() == null
                    || entry.getWorldTime() < 0L) {
                throw new IllegalStateException("invalid quest history");
            }
            writeIdentifier(buf, entry.getQuestId());
            writeIdentifier(buf, entry.getOutcome().name());
            writeText(buf, entry.getDetail());
            buf.writeLong(entry.getWorldTime());
            writeIdentifierSet(buf,
                    entry.getCompletedOptionalObjectiveIds(),
                    "completed optional objective",
                    MAX_OBJECTIVE_PROGRESS);
            buf.writeInt(entry.getCompletions());
            writeIdentifierSet(buf, new LinkedHashSet<String>(
                    entry.getStagePath()), "stage path",
                    LostTalesQuestProgress.MAX_PATH_STAGES);
        }

        LostTalesPacketCodec.writeCount(buf, this.giverMarkers.size(),
                MAX_GIVER_MARKERS, "giver marker");
        for (LostTalesMapMarkerDefinition marker : this.giverMarkers) {
            if (!isValidMarker(marker)) {
                throw new IllegalStateException("invalid giver marker");
            }
            writeIdentifier(buf, marker.getId());
            writeName(buf, marker.getName());
            LostTalesPacketCodec.writeUtf8String(buf, marker.getNamedAfter(),
                    LostTalesMapMarkerNamedAfter.MAX_LENGTH);
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
            buf.writeInt(marker.getPriority());
        }

        LostTalesPacketCodec.writeCount(buf,
                this.missiveDefinitions.size(), MAX_MISSIVES,
                "missive");
        for (LostTalesQuestDefinition quest : this.missiveDefinitions) {
            LostTalesQuestDefinitionCodec.write(buf, quest);
        }
        writeIdentifierSet(buf, this.heardRumours, "heard rumour",
                LostTalesQuestPlayerData.MAX_RUMOURS);
        LostTalesPacketCodec.writeCount(buf, this.rumourPlaces.size(),
                LostTalesQuestPlayerData.MAX_RUMOURS, "rumour place");
        for (QuestRumourPlace place : this.rumourPlaces.values()) {
            writeIdentifier(buf, place.getQuestId());
            writeIdentifier(buf, place.getMarkerId());
            writeName(buf, place.getName());
            LostTalesPacketCodec.writeUtf8String(buf, place.getNamedAfter(),
                    LostTalesMapMarkerNamedAfter.MAX_LENGTH);
            buf.writeInt(place.getDimensionId());
            buf.writeDouble(place.getX());
            buf.writeDouble(place.getZ());
        }
        writeWaitingRewards(buf, this.waitingRewards);
        buf.writeBoolean(this.replaces);
        if (buf.writerIndex() - startIndex > MAX_PACKET_BYTES) {
            throw new IllegalStateException(
                    "quest snapshot exceeds packet limit");
        }
    }

    /**
     * One running quest as both quest packets carry it: its id, stage,
     * times, each objective's count and the stages it has reached.
     */
    static void writeProgress(ByteBuf buf, LostTalesQuestProgress progress) {
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
        writeIdentifierSet(buf, new LinkedHashSet<String>(
                progress.getStagePath()), "stage path",
                LostTalesQuestProgress.MAX_PATH_STAGES);
        buf.writeLong(progress.getStageDeadlineWorldTime());
        buf.writeBoolean(progress.isReturning());
        buf.writeBoolean(progress.isChoosing());
        writeIdentifier(buf, progress.getGiverId());
        writeName(buf, progress.getGiverName());
        writeIdentifier(buf, progress.getGiverKind());
    }

    /** One running quest as {@link #writeProgress} wrote it; throws on anything else. */
    static LostTalesQuestProgress readProgress(ByteBuf buf) {
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
        LinkedHashSet<String> stagePath = new LinkedHashSet<String>();
        readIdentifierSet(buf, stagePath, "stage path",
                LostTalesQuestProgress.MAX_PATH_STAGES);
        long stageDeadline = buf.readLong();
        boolean returning = buf.readBoolean();
        boolean choosing = buf.readBoolean();
        String giverId = readIdentifier(buf);
        String giverName = readName(buf);
        String giverKind = readIdentifier(buf);

        if (questId.length() == 0 || stageDeadline < 0L
                || giverName.length() > LostTalesQuestProgress.MAX_GIVER_NAME_CHARACTERS) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid active quest");
        }
        LostTalesQuestProgress read = new LostTalesQuestProgress(questId,
                stageIndex, stageId, objectiveProgress, acceptedWorldTime,
                deadlineWorldTime, stagePath);
        read.setStageDeadlineWorldTime(stageDeadline);
        read.setReturning(returning);
        read.setChoosing(choosing);
        read.setGiver(giverId, giverName, giverKind);
        return read;
    }

    /**
     * The log with every running quest's counts left out: what must stay
     * the same for counts alone to bring a client up to date.
     */
    public LostTalesQuestSyncPacket withoutObjectiveCounts() {
        List<LostTalesQuestProgress> uncounted =
                new ArrayList<LostTalesQuestProgress>(this.activeQuests.size());
        for (LostTalesQuestProgress progress : this.activeQuests) {
            uncounted.add(progress.withoutCounts());
        }
        return new LostTalesQuestSyncPacket(uncounted, this.questHistory,
                this.trackedQuestIds, this.discoveredMarkerIds,
                this.trackedMapMarkerId, this.giverMarkers,
                this.missiveDefinitions).withHeardRumours(this.heardRumours)
                .withRumourPlaces(this.rumourPlaces.values())
                .withWaitingRewards(this.waitingRewards);
    }

    public List<LostTalesQuestProgress> getActiveQuests() {
        List<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : this.activeQuests) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableList(copy);
    }

    public List<LostTalesQuestHistoryEntry> getQuestHistory() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesQuestHistoryEntry>(
                        this.questHistory));
    }

    public Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.discoveredMarkerIds));
    }

    public List<LostTalesMapMarkerDefinition> getGiverMarkers() {
        return Collections.unmodifiableList(new ArrayList<LostTalesMapMarkerDefinition>(this.giverMarkers));
    }

    public List<LostTalesQuestDefinition> getMissiveDefinitions() {
        return Collections.unmodifiableList(new ArrayList<LostTalesQuestDefinition>(this.missiveDefinitions));
    }

    public Set<String> getTrackedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.trackedQuestIds));
    }

    public String getTrackedMapMarkerId() {
        return this.trackedMapMarkerId == null ? "" : this.trackedMapMarkerId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private void clearState() {
        this.replaces = false;
        this.waitingRewards.clear();
        this.heardRumours.clear();
        this.rumourPlaces.clear();
        this.activeQuests.clear();
        this.questHistory.clear();
        this.discoveredMarkerIds.clear();
        this.trackedQuestIds.clear();
        this.giverMarkers.clear();
        this.missiveDefinitions.clear();
        this.trackedMapMarkerId = "";
    }

    /**
     * The rewards waiting for room, as {@link #writeWaitingRewards} wrote
     * them: each quest once, at least one reward each, every reward valid
     * and of a kind its quest names once; throws on anything else.
     */
    private static void readWaitingRewards(ByteBuf buf,
            Map<String, List<WaitingReward>> target) {
        int questCount = LostTalesPacketCodec.readCount(buf,
                WaitingRewards.MAX_QUESTS, "waiting quest");
        for (int i = 0; i < questCount; i++) {
            String questId = readIdentifier(buf);
            int kinds = LostTalesPacketCodec.readCount(buf,
                    WaitingRewards.MAX_KINDS_EACH, "waiting reward");
            if (questId.length() == 0 || target.containsKey(questId) || kinds == 0) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid waiting rewards");
            }
            List<WaitingReward> rewards = new ArrayList<WaitingReward>();
            for (int j = 0; j < kinds; j++) {
                boolean starter = buf.readBoolean();
                String id = readIdentifier(buf);
                int meta = buf.readInt();
                int count = buf.readInt();
                String relic = readIdentifier(buf);
                WaitingReward reward = starter ? WaitingReward.starter(id, count)
                        : relic.length() > 0 ? WaitingReward.relic(id, relic, count)
                        : WaitingReward.item(id, meta, count);
                if (!reward.isValid() || starter && (meta != 0 || relic.length() > 0)
                        || relic.length() > 0 && meta != 0) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid waiting reward");
                }
                for (WaitingReward before : rewards) {
                    if (before.isSameKind(reward)) {
                        throw new LostTalesPacketCodec.DecodeException(
                                "waiting reward named twice");
                    }
                }
                rewards.add(reward);
            }
            target.put(questId, Collections.unmodifiableList(rewards));
        }
    }

    private static void writeWaitingRewards(ByteBuf buf,
            Map<String, List<WaitingReward>> rewards) {
        LostTalesPacketCodec.writeCount(buf, rewards.size(),
                WaitingRewards.MAX_QUESTS, "waiting quest");
        for (Map.Entry<String, List<WaitingReward>> entry : rewards.entrySet()) {
            writeIdentifier(buf, entry.getKey());
            LostTalesPacketCodec.writeCount(buf, entry.getValue().size(),
                    WaitingRewards.MAX_KINDS_EACH, "waiting reward");
            for (WaitingReward reward : entry.getValue()) {
                buf.writeBoolean(reward.isStarter());
                writeIdentifier(buf, reward.getId());
                buf.writeInt(reward.getMeta());
                buf.writeInt(reward.getCount());
                writeIdentifier(buf, reward.getRelic());
            }
        }
    }

    private static void readIdentifierSet(ByteBuf buf, Set<String> target,
                                          String fieldName) {
        readIdentifierSet(buf, target, fieldName,
                MAX_QUEST_ID_COLLECTION);
    }

    private static void readIdentifierSet(ByteBuf buf, Set<String> target,
                                          String fieldName, int maximum) {
        int count = LostTalesPacketCodec.readCount(
                buf, maximum, fieldName);
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
        writeIdentifierSet(buf, values, fieldName,
                MAX_QUEST_ID_COLLECTION);
    }

    private static void writeIdentifierSet(ByteBuf buf, Set<String> values,
                                           String fieldName, int maximum) {
        LostTalesPacketCodec.writeCount(buf, values.size(),
                maximum, fieldName);
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
                && Double.isFinite(marker.getX()) && Double.isFinite(marker.getY())
                && Double.isFinite(marker.getZ())
                && Double.isFinite(marker.getCompassFadeInRadius())
                && Double.isFinite(marker.getDiscoveryRadius())
                && marker.getCompassFadeInRadius() >= 0.0D
                && marker.getDiscoveryRadius() >= 0.0D;
    }

    private static String safeStatic(String value) {
        return value == null ? "" : value;
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
