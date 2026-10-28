package com.ninuna.losttales.quest.player;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.compat.lotr.LotrQuestReference;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestDefinitionNbt;
import com.ninuna.losttales.quest.LostTalesQuestIds;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.LostTalesQuestRegistry;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import com.ninuna.losttales.storage.NbtTags;
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
 * The quest log of the character being played, kept with the player
 * ({@link IExtendedEntityProperties}) and copied across death: running
 * quests, the History, tracked quests, discovered and placed markers, the
 * missives the player took, and the rewards waiting for room
 * ({@link WaitingRewards}). A character switch swaps the whole log.
 *
 * <p>A missive's quest is kept while it runs, while it stands among the
 * latest {@link #MAX_ENDED_MISSIVES} ended ones and while its rewards
 * wait, then forgotten with its History line, so the log never outgrows
 * what it can save or send.</p>
 */
public final class LostTalesQuestPlayerData implements IExtendedEntityProperties {
    public static final String PROPERTY_ID = "LostTalesQuestData";
    public static final int CURRENT_DATA_VERSION = 3;
    static final int MAX_ACTIVE_QUESTS = 1024;
    static final int MAX_QUEST_ID_HISTORY = 8192;
    static final int MAX_MISSIVES = 512;
    /** The most ended missives kept, with their quests and History lines; the oldest goes first. */
    public static final int MAX_ENDED_MISSIVES = 32;
    static final int MAX_GIVER_MARKERS = 2048;
    static final int MAX_IDENTIFIER_CHARACTERS = 256;
    static final int MAX_NAME_CHARACTERS = 1024;
    static final int MAX_HISTORY_DETAIL_CHARACTERS = 2048;

    private static final String TAG_DATA_VERSION = "DataVersion";

    private final Map<String, LostTalesQuestProgress> activeQuests = new LinkedHashMap<String, LostTalesQuestProgress>();
    private final Map<String, LostTalesQuestHistoryEntry> questHistory = new LinkedHashMap<String, LostTalesQuestHistoryEntry>();
    private final Set<String> discoveredMarkerIds = new LinkedHashSet<String>();
    private final Map<String, String> discoveredMarkerIdByCanonicalKey =
            new LinkedHashMap<String, String>();
    private final Map<String, LostTalesMapMarkerDefinition> giverMarkers = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
    private final Map<String, String> giverMarkerIdByCanonicalKey =
            new LinkedHashMap<String, String>();
    private final Map<String, LostTalesQuestDefinition> missiveDefinitions = new LinkedHashMap<String, LostTalesQuestDefinition>();
    private final Set<String> trackedQuestIds = new LinkedHashSet<String>();
    private String trackedMapMarkerId = "";
    /** Whether this character was given its starter items (QUEST-29). */
    private boolean startersGiven;
    /**
     * The quests whose rumours this character heard (QUEST-20), oldest
     * first, each with the marker of the place it points to; empty for
     * none.
     */
    private final Map<String, String> heardRumours = new LinkedHashMap<String, String>();
    /** The most rumours a log keeps; the oldest goes first. */
    public static final int MAX_RUMOURS = 256;
    /** Where the log keeps its {@link WaitingRewards}. */
    static final String TAG_WAITING_REWARDS = "WaitingRewards";
    /** The rewards that found no room, each under its quest (QUEST-44). */
    private final WaitingRewards waitingRewards = new WaitingRewards();
    /** Waiting rewards that could not be read, kept whole as they were. */
    private final java.util.List<NBTTagCompound> waitingQuarantine =
            new ArrayList<NBTTagCompound>();
    /**
     * The quests whose stage ended on a hand-over still to be made, on
     * the player's next tick. Never saved: a stage still full asks again.
     */
    private final Set<String> handOversDue = new LinkedHashSet<String>();
    /** Counts every change to the discovered markers; never saved. */
    private int markerChanges;
    /** What the LOTR waypoint check saw when it last ran; never saved. */
    private Object waypointCheckSeen;
    /**
     * Whether a character switch put this log in place since the client
     * was last sent it; never saved.
     */
    private boolean replaced;
    /**
     * Whether the log is kept whole as it was read and changes nothing:
     * saved under any version but {@link #CURRENT_DATA_VERSION}, malformed
     * or past its bounds.
     */
    private boolean readOnly;
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
        return data;
    }

    @Override
    public void saveNBTData(NBTTagCompound compound) {
        if (compound == null) {
            return;
        }
        if (this.readOnly && this.preservedReadOnlyData != null) {
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

        NBTTagList historyList = new NBTTagList();
        for (LostTalesQuestHistoryEntry entry : this.questHistory.values()) {
            NBTTagCompound questTag = new NBTTagCompound();
            questTag.setString("QuestId", entry.getQuestId());
            questTag.setString("Outcome", entry.getOutcome().name());
            questTag.setString("Detail", entry.getDetail());
            questTag.setLong("WorldTime", entry.getWorldTime());
            NBTTagList optionalObjectives = new NBTTagList();
            for (String objectiveId
                    : entry.getCompletedOptionalObjectiveIds()) {
                NBTTagCompound objectiveTag = new NBTTagCompound();
                objectiveTag.setString("ObjectiveId", objectiveId);
                optionalObjectives.appendTag(objectiveTag);
            }
            questTag.setTag("CompletedOptionalObjectives",
                    optionalObjectives);
            questTag.setInteger("Completions", entry.getCompletions());
            questTag.setTag("StagePath", LostTalesQuestProgress
                    .writeStagePath(entry.getStagePath()));
            historyList.appendTag(questTag);
        }
        data.setTag("QuestHistory", historyList);

        NBTTagList trackedQuestList = new NBTTagList();
        for (String questId : this.trackedQuestIds) {
            if (questId == null || questId.length() == 0
                    || (!this.activeQuests.containsKey(questId)
                        && !LotrQuestReference.isLotrQuest(questId))) {
                continue;
            }
            NBTTagCompound questTag = new NBTTagCompound();
            questTag.setString("QuestId", questId);
            trackedQuestList.appendTag(questTag);
        }
        data.setTag("TrackedQuestIds", trackedQuestList);

        NBTTagList markerList = new NBTTagList();
        for (String markerId : this.discoveredMarkerIds) {
            NBTTagCompound markerTag = new NBTTagCompound();
            markerTag.setString("MarkerId", markerId);
            markerList.appendTag(markerTag);
        }
        data.setTag("DiscoveredMarkers", markerList);

        NBTTagList missiveList = new NBTTagList();
        for (LostTalesQuestDefinition quest : this.missiveDefinitions.values()) {
            if (quest == null || quest.getId() == null || quest.getId().length() == 0) {
                continue;
            }
            missiveList.appendTag(LostTalesQuestDefinitionNbt.write(quest));
        }
        data.setTag("MissiveDefinitions", missiveList);

        NBTTagList giverMarkerList = new NBTTagList();
        for (LostTalesMapMarkerDefinition marker : this.giverMarkers.values()) {
            if (marker == null || marker.getId() == null || marker.getId().length() == 0) {
                continue;
            }
            NBTTagCompound markerTag = new NBTTagCompound();
            markerTag.setString("MarkerId", marker.getId());
            markerTag.setString("Name", marker.getName() == null ? "" : marker.getName());
            markerTag.setString("NamedAfter", marker.getNamedAfter());
            markerTag.setString("Icon", marker.getIconName() == null ? "quest" : marker.getIconName());
            markerTag.setString("Color", marker.getColorName() == null ? "white" : marker.getColorName());
            markerTag.setString("Category", marker.getCategoryName() == null ? "" : marker.getCategoryName());
            markerTag.setBoolean("HasFastTravel", marker.hasFastTravel());
            markerTag.setInteger("DimensionId", marker.getDimensionId());
            markerTag.setDouble("X", marker.getX());
            markerTag.setDouble("Y", marker.getY());
            markerTag.setDouble("Z", marker.getZ());
            markerTag.setDouble("CompassFadeInRadius", marker.getCompassFadeInRadius());
            markerTag.setDouble("DiscoveryRadius", marker.getDiscoveryRadius());
            markerTag.setBoolean("HiddenUntilDiscovered", marker.isHiddenUntilDiscovered());
            markerTag.setBoolean("IsDiscoverable", marker.isDiscoverable());
            markerTag.setBoolean("RequiresRegionUnlock",
                    marker.requiresRegionUnlock());
            markerTag.setInteger("Priority", marker.getPriority());
            giverMarkerList.appendTag(markerTag);
        }
        data.setTag("GiverMarkers", giverMarkerList);
        data.setString("TrackedMapMarkerId", this.trackedMapMarkerId == null ? "" : this.trackedMapMarkerId);
        data.setBoolean("StartersGiven", this.startersGiven);
        NBTTagList rumourList = new NBTTagList();
        for (Map.Entry<String, String> rumour : this.heardRumours.entrySet()) {
            NBTTagCompound rumourTag = new NBTTagCompound();
            rumourTag.setString("QuestId", rumour.getKey());
            if (rumour.getValue().length() > 0) {
                rumourTag.setString("Marker", rumour.getValue());
            }
            rumourList.appendTag(rumourTag);
        }
        data.setTag("HeardRumours", rumourList);
        if (!this.waitingRewards.isEmpty() || !this.waitingQuarantine.isEmpty()) {
            data.setTag(TAG_WAITING_REWARDS, WaitingRewardsNbtCodec.write(
                    this.waitingRewards, this.waitingQuarantine));
        }

        compound.setTag(PROPERTY_ID, data);
    }

    @Override
    public void loadNBTData(NBTTagCompound compound) {
        loadNBTData(compound, true, true);
    }

    private void loadNBTData(NBTTagCompound compound,
                             boolean registerMissiveDefinitions,
                             boolean logWarnings) {
        this.markerChanges++;
        this.activeQuests.clear();
        this.questHistory.clear();
        this.discoveredMarkerIds.clear();
        this.discoveredMarkerIdByCanonicalKey.clear();
        this.giverMarkers.clear();
        this.giverMarkerIdByCanonicalKey.clear();
        this.missiveDefinitions.clear();
        this.trackedQuestIds.clear();
        this.trackedMapMarkerId = "";
        this.startersGiven = false;
        this.heardRumours.clear();
        this.waitingRewards.clear();
        this.waitingQuarantine.clear();
        this.readOnly = false;
        this.preservedReadOnlyData = null;

        if (compound == null || !compound.hasKey(PROPERTY_ID)) {
            return;
        }
        if (!compound.hasKey(PROPERTY_ID, Constants.NBT.TAG_COMPOUND)) {
            enterReadOnlyMode(compound.getTag(PROPERTY_ID),
                    "Quest data property is malformed and will be preserved without modification",
                    logWarnings);
            return;
        }

        NBTTagCompound originalData = compound.getCompoundTag(PROPERTY_ID);
        int storedVersion = originalData.hasKey(TAG_DATA_VERSION,
                Constants.NBT.TAG_INT)
                ? originalData.getInteger(TAG_DATA_VERSION) : -1;
        if (storedVersion != CURRENT_DATA_VERSION) {
            enterReadOnlyMode(originalData,
                    "Quest data uses unsupported version " + storedVersion
                            + " and will be preserved without modification",
                    logWarnings);
            return;
        }

        NBTTagCompound data = (NBTTagCompound)originalData.copy();
        if (!isStructurallyReasonable(data)) {
            enterReadOnlyMode(originalData,
                    "Quest data exceeds structural safety limits and will "
                            + "be preserved without modification",
                    logWarnings);
            return;
        }
        WaitingRewardsNbtCodec.ReadResult waiting = WaitingRewardsNbtCodec.read(
                data.hasKey(TAG_WAITING_REWARDS, Constants.NBT.TAG_COMPOUND)
                        ? data.getCompoundTag(TAG_WAITING_REWARDS) : null);
        if (data.hasKey(TAG_WAITING_REWARDS)
                && !data.hasKey(TAG_WAITING_REWARDS, Constants.NBT.TAG_COMPOUND)
                || !waiting.isReadable()) {
            enterReadOnlyMode(originalData,
                    "Quest data holds waiting rewards this build cannot read"
                            + " and will be preserved without modification",
                    logWarnings);
            return;
        }
        this.waitingRewards.copyFrom(waiting.getRewards());
        this.waitingQuarantine.addAll(waiting.getQuarantined());
        NBTTagList activeList = data.getTagList("ActiveQuests", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < activeList.tagCount(); i++) {
            LostTalesQuestProgress progress = LostTalesQuestProgress.readFromNBT(activeList.getCompoundTagAt(i));
            if (progress != null) {
                this.activeQuests.put(progress.getQuestId(), progress);
            }
        }

        NBTTagList trackedQuestList = data.getTagList("TrackedQuestIds", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < trackedQuestList.tagCount(); i++) {
            String questId = trackedQuestList.getCompoundTagAt(i).getString("QuestId");
            if (questId != null && questId.length() > 0
                    && (this.activeQuests.containsKey(questId)
                        || LotrQuestReference.isLotrQuest(questId))) {
                this.trackedQuestIds.add(questId);
            }
        }

        NBTTagList historyList = data.getTagList("QuestHistory", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < historyList.tagCount(); i++) {
            NBTTagCompound questTag = historyList.getCompoundTagAt(i);
            String questId = questTag.getString("QuestId");
            LostTalesQuestHistoryEntry.Outcome outcome = LostTalesQuestHistoryEntry.Outcome.fromName(questTag.getString("Outcome"));
            if (questId != null && questId.length() > 0 && outcome != null) {
                LinkedHashSet<String> completedOptionalObjectives =
                        new LinkedHashSet<String>();
                NBTTagList optionalObjectives = questTag.getTagList(
                        "CompletedOptionalObjectives",
                        Constants.NBT.TAG_COMPOUND);
                for (int j = 0; j < optionalObjectives.tagCount(); j++) {
                    completedOptionalObjectives.add(optionalObjectives
                            .getCompoundTagAt(j).getString("ObjectiveId"));
                }
                this.questHistory.put(questId, new LostTalesQuestHistoryEntry(
                        questId, outcome, questTag.getString("Detail"),
                        questTag.getLong("WorldTime"),
                        completedOptionalObjectives,
                        questTag.getInteger("Completions"),
                        LostTalesQuestProgress.readStagePath(questTag,
                                "StagePath")));
            }
        }

        NBTTagList markerList = data.getTagList("DiscoveredMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < markerList.tagCount(); i++) {
            NBTTagCompound markerTag = markerList.getCompoundTagAt(i);
            String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerTag.getString("MarkerId"));
            addDiscoveredMarkerId(markerId);
        }
        NBTTagList missiveList = data.getTagList("MissiveDefinitions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < missiveList.tagCount(); i++) {
            LostTalesQuestDefinition quest = LostTalesQuestDefinitionNbt.read(missiveList.getCompoundTagAt(i));
            if (quest != null && quest.getId() != null && quest.getId().length() > 0) {
                this.missiveDefinitions.put(quest.getId(), quest);
            }
        }
        // The missives go to the registry together, so its list is sorted
        // once per load rather than once per missive.
        if (registerMissiveDefinitions && !this.missiveDefinitions.isEmpty()) {
            LostTalesQuestRegistry.registerMissives(
                    this.missiveDefinitions.values());
        }

        NBTTagList giverMarkerList = data.getTagList("GiverMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < giverMarkerList.tagCount(); i++) {
            LostTalesMapMarkerDefinition marker = readGiverMarker(giverMarkerList.getCompoundTagAt(i));
            if (marker != null) {
                addLoadedGiverMarker(marker);
            }
        }

        String requestedTrackedMarkerId =
                LostTalesQuestMarkerHelper.normalizeMarkerId(
                        data.getString("TrackedMapMarkerId"));
        String storedTrackedMarkerId =
                findDiscoveredMarkerId(requestedTrackedMarkerId);
        this.trackedMapMarkerId = storedTrackedMarkerId == null
                ? "" : storedTrackedMarkerId;
        this.startersGiven = data.getBoolean("StartersGiven");
        NBTTagList rumourList = data.getTagList("HeardRumours", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rumourList.tagCount(); i++) {
            NBTTagCompound rumourTag = rumourList.getCompoundTagAt(i);
            this.heardRumours.put(rumourTag.getString("QuestId"),
                    LostTalesQuestMarkerHelper.normalizeMarkerId(
                            rumourTag.getString("Marker")));
        }
        pruneInvalidReferences();
    }

    /** The quests whose rumours this character heard, oldest first (QUEST-20). */
    public Set<String> getHeardRumours() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(this.heardRumours.keySet()));
    }

    /**
     * The marker of the place each rumour heard points to, by quest,
     * oldest first; empty for a rumour that names none (QUEST-20).
     */
    public Map<String, String> getRumourMarkers() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<String, String>(this.heardRumours));
    }

    /**
     * Keeps that this character heard the quest's rumour, with the marker
     * of the place it points to; nothing of that place is discovered
     * (QUEST-20). Past {@link #MAX_RUMOURS} the oldest goes; a marker id
     * past {@link #MAX_IDENTIFIER_CHARACTERS} points nowhere. Answers
     * whether the rumour is new.
     */
    public boolean hearRumour(String questId, String markerId) {
        if (!isWritable() || !LostTalesQuestIds.fits(questId) || questId.length() == 0
                || this.heardRumours.containsKey(questId)) {
            return false;
        }
        while (this.heardRumours.size() >= MAX_RUMOURS) {
            this.heardRumours.remove(this.heardRumours.keySet().iterator().next());
        }
        String marker = LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        this.heardRumours.put(questId,
                marker.length() > MAX_IDENTIFIER_CHARACTERS ? "" : marker);
        return true;
    }

    /** Whether this character was given its starter items (QUEST-29). */
    public boolean wereStartersGiven() {
        return this.startersGiven;
    }

    /** Marks this character's starter items given; never again for it. */
    public boolean markStartersGiven() {
        if (!isWritable() || this.startersGiven) {
            return false;
        }
        this.startersGiven = true;
        return true;
    }

    /** Every quest's rewards waiting for room (QUEST-44), in order: a copy. */
    public Map<String, java.util.List<WaitingReward>> getWaitingRewards() {
        return this.waitingRewards.all();
    }

    /** What waits under the quest; empty for nothing. */
    public java.util.List<WaitingReward> getWaitingRewards(String questId) {
        return this.waitingRewards.get(questId);
    }

    /**
     * Keeps the rewards waiting under the quest, each beside one of its
     * kind. Answers what could not be kept: everything while the log is
     * read-only, the rest past a bound of {@link WaitingRewards}.
     */
    public java.util.List<WaitingReward> addWaitingRewards(String questId,
            Collection<WaitingReward> rewards) {
        java.util.List<WaitingReward> unkept = new ArrayList<WaitingReward>();
        if (rewards == null) {
            return unkept;
        }
        for (WaitingReward reward : rewards) {
            if (reward == null) {
                continue;
            }
            int left = isWritable() ? this.waitingRewards.add(questId, reward)
                    : reward.getCount();
            if (left > 0) {
                unkept.add(reward.withCount(left));
            }
        }
        return unkept;
    }

    /**
     * Puts what is left of the quest's waiting rewards after a collection
     * in their place; nothing left takes the quest off the list. Answers
     * whether the log took it: never while it is read-only.
     */
    public boolean replaceWaitingRewards(String questId,
            Collection<WaitingReward> remaining) {
        if (!isWritable()) {
            return false;
        }
        this.waitingRewards.replace(questId, remaining == null
                ? java.util.Collections.<WaitingReward>emptyList()
                : new ArrayList<WaitingReward>(remaining));
        return true;
    }

    @Override
    public void init(Entity entity, World world) {
        // The data needs nothing from the player it is attached to.
    }

    /**
     * Whether the log is read-only: saved under any version but the
     * current one, malformed or past its bounds, and kept whole. The
     * name is the one every store of the mod answers by.
     */
    public boolean isReadOnlyForNewerVersion() {
        return this.readOnly;
    }

    private boolean isWritable() {
        return !this.readOnly;
    }

    private void enterReadOnlyMode(NBTBase preservedData,
                                   String message, boolean logWarning) {
        this.readOnly = true;
        this.preservedReadOnlyData = preservedData == null ? null : preservedData.copy();
        if (logWarning) {
            FMLLog.warning("[%s] %s", LostTalesMetaData.MOD_ID, message);
        }
    }

    /**
     * The log as a character keeps it: a copy, without the wrapper the
     * player's own save puts round it.
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
     * Checks a character's log without registering its missives with the
     * server; answers a copy.
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

    /**
     * Replaces the live player's quest state from an already detached
     * payload, as a character switch does: the client's next log takes the
     * place of the one it holds, without news.
     */
    public void replaceCharacterState(NBTTagCompound characterState) {
        NBTTagCompound validated = validateCharacterState(characterState);
        this.handOversDue.clear();
        loadCharacterState(validated, true, true);
        if (isReadOnlyForNewerVersion()) {
            throw new IllegalArgumentException(
                    "Quest character state became unavailable while applying");
        }
        this.replaced = true;
    }

    /**
     * Whether a character switch put this log in place since this was
     * last asked; asking forgets it.
     */
    public boolean takeReplaced() {
        boolean was = this.replaced;
        this.replaced = false;
        return was;
    }

    private void loadCharacterState(NBTTagCompound characterState,
                                    boolean registerMissiveDefinitions,
                                    boolean logWarnings) {
        NBTTagCompound wrapper = new NBTTagCompound();
        if (characterState != null) {
            wrapper.setTag(PROPERTY_ID, characterState.copy());
        }
        loadNBTData(wrapper, registerMissiveDefinitions, logWarnings);
    }

    public Collection<LostTalesQuestProgress> getActiveQuests() {
        ArrayList<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : this.activeQuests.values()) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableCollection(copy);
    }

    /** The quests ever completed, however a later run of each ended. */
    public Set<String> getCompletedQuestIds() {
        LinkedHashSet<String> completed = new LinkedHashSet<String>();
        for (LostTalesQuestHistoryEntry entry : this.questHistory.values()) {
            if (entry.wasEverCompleted()) {
                completed.add(entry.getQuestId());
            }
        }
        return Collections.unmodifiableSet(completed);
    }

    public Collection<LostTalesQuestHistoryEntry> getQuestHistory() {
        return Collections.unmodifiableCollection(
                new ArrayList<LostTalesQuestHistoryEntry>(
                        this.questHistory.values()));
    }

    public LostTalesQuestHistoryEntry getQuestHistoryEntry(String questId) {
        return questId == null ? null : this.questHistory.get(questId);
    }

    /** Marks the quest's hand-over due: its stage ended, and its items go on the player's next tick. */
    public void markHandOverDue(String questId) {
        if (questId != null && this.activeQuests.containsKey(questId)) {
            this.handOversDue.add(questId);
        }
    }

    public boolean hasHandOversDue() {
        return !this.handOversDue.isEmpty();
    }

    /** Takes the quests whose hand-over is due, and clears the marks. */
    public Set<String> takeHandOversDue() {
        Set<String> due = new LinkedHashSet<String>(this.handOversDue);
        this.handOversDue.clear();
        return due;
    }

    /** Every quest the log names: running, in the History, tracked, heard of, and with rewards waiting. */
    public Set<String> getQuestIdsInLog() {
        Set<String> named = new LinkedHashSet<String>(this.activeQuests.keySet());
        named.addAll(this.questHistory.keySet());
        named.addAll(this.trackedQuestIds);
        named.addAll(this.heardRumours.keySet());
        named.addAll(this.waitingRewards.questIds());
        return named;
    }

    public Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(this.discoveredMarkerIds));
    }

    public Collection<LostTalesMapMarkerDefinition> getGiverMarkers() {
        return Collections.unmodifiableCollection(new ArrayList<LostTalesMapMarkerDefinition>(this.giverMarkers.values()));
    }

    public Collection<LostTalesQuestDefinition> getMissiveDefinitions() {
        return Collections.unmodifiableCollection(new ArrayList<LostTalesQuestDefinition>(this.missiveDefinitions.values()));
    }

    /**
     * Keeps a missive's quest with the log, in place of one of the same id.
     * A full log forgets its oldest ended missive to make room. Answers
     * whether it is kept: not while every missive the log can save and
     * send is still running.
     */
    public boolean rememberMissiveDefinition(LostTalesQuestDefinition quest) {
        if (!isWritable() || quest == null
                || !LostTalesQuestIds.fits(quest.getId())) {
            return false;
        }
        forgetEndedMissives();
        if (!this.missiveDefinitions.containsKey(quest.getId())
                && this.missiveDefinitions.size() >= MAX_MISSIVES) {
            forgetEndedMissivesPast(Math.max(0, countEndedMissives() - 1));
            if (this.missiveDefinitions.size() >= MAX_MISSIVES) {
                return false;
            }
        }
        this.missiveDefinitions.put(quest.getId(), quest);
        LostTalesQuestRegistry.registerMissive(quest);
        return true;
    }

    /**
     * Forgets the missives the log does not keep: one neither running
     * nor in the History, and the oldest ended ones past
     * {@link #MAX_ENDED_MISSIVES}, each with its History line. A missive
     * whose rewards wait for room stays until they are collected. Called
     * as a quest ends and as the player logs in; answers whether any went.
     */
    public boolean forgetEndedMissives() {
        return forgetEndedMissivesPast(MAX_ENDED_MISSIVES);
    }

    /** How many ended missives the log may forget now. */
    private int countEndedMissives() {
        return endedMissives().size();
    }

    /** The ended missives the log may forget, oldest first: none whose rewards wait. */
    private java.util.List<String> endedMissives() {
        ArrayList<String> ended = new ArrayList<String>();
        for (String questId : this.questHistory.keySet()) {
            if (this.missiveDefinitions.containsKey(questId)
                    && !this.activeQuests.containsKey(questId)
                    && !this.waitingRewards.has(questId)) {
                ended.add(questId);
            }
        }
        return ended;
    }

    private boolean forgetEndedMissivesPast(int keep) {
        if (!isWritable()) {
            return false;
        }
        boolean changed = false;
        ArrayList<String> unheld = new ArrayList<String>();
        for (String questId : this.missiveDefinitions.keySet()) {
            if (!this.activeQuests.containsKey(questId)
                    && !this.questHistory.containsKey(questId)
                    && !this.waitingRewards.has(questId)) {
                unheld.add(questId);
            }
        }
        for (String questId : unheld) {
            this.missiveDefinitions.remove(questId);
            changed = true;
        }
        java.util.List<String> ended = endedMissives();
        for (int index = 0; index < ended.size() - keep; index++) {
            String questId = ended.get(index);
            this.questHistory.remove(questId);
            this.missiveDefinitions.remove(questId);
            this.trackedQuestIds.remove(questId);
            changed = true;
        }
        return changed;
    }

    public LostTalesMapMarkerDefinition getGiverMarker(String markerId) {
        String storedId = findGiverMarkerId(markerId);
        return storedId == null ? null
                : this.giverMarkers.get(storedId);
    }

    public LostTalesQuestProgress getActiveQuest(String questId) {
        return this.activeQuests.get(questId);
    }

    public boolean isQuestActive(String questId) {
        return this.activeQuests.containsKey(questId);
    }

    /**
     * Whether the quest was ever completed, however a later run of it
     * ended: what a {@code completed} prerequisite reads (QUEST-10).
     */
    public boolean isQuestCompleted(String questId) {
        LostTalesQuestHistoryEntry entry = getQuestHistoryEntry(questId);
        return entry != null && entry.wasEverCompleted();
    }

    /**
     * Starts a quest at its first stage. A quest taken again keeps its
     * History line until this run ends, which then takes its place and
     * keeps its count of completions (QUEST-10).
     */
    public void startQuest(String questId, String firstStageId, long acceptedWorldTime, long deadlineWorldTime) {
        if (!isWritable()) {
            return;
        }
        if (!LostTalesQuestIds.fits(questId) || this.activeQuests.containsKey(questId)) {
            return;
        }
        // A quest taken is no rumour.
        this.heardRumours.remove(questId);
        this.activeQuests.put(questId, new LostTalesQuestProgress(questId, 0, firstStageId, null, acceptedWorldTime, deadlineWorldTime));
    }

    public Set<String> getTrackedQuestIds() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(this.trackedQuestIds));
    }

    public boolean isQuestTracked(String questId) {
        return questId != null && this.trackedQuestIds.contains(questId) && this.activeQuests.containsKey(questId);
    }

    public String getTrackedMapMarkerId() {
        return this.trackedMapMarkerId == null ? "" : this.trackedMapMarkerId;
    }

    public boolean discoverMarker(String markerId) {
        if (!isWritable()) {
            return false;
        }
        return addDiscoveredMarkerId(markerId);
    }

    public boolean discoverGiverMarker(LostTalesMapMarkerDefinition marker) {
        if (!isWritable()) {
            return false;
        }
        if (!isValidGiverMarker(marker)) {
            return false;
        }
        String requestedId =
                LostTalesQuestMarkerHelper.normalizeMarkerId(
                        marker.getId());
        String key = markerCanonicalKey(requestedId);
        String storedId = this.giverMarkerIdByCanonicalKey.get(key);
        if (storedId == null) {
            if (this.giverMarkers.size() >= MAX_GIVER_MARKERS
                    || (findDiscoveredMarkerId(requestedId) == null
                        && this.discoveredMarkerIds.size()
                                >= MAX_QUEST_ID_HISTORY)) {
                return false;
            }
            storedId = requestedId;
            this.giverMarkerIdByCanonicalKey.put(key, storedId);
        }
        LostTalesMapMarkerDefinition normalized =
                normalizeGiverMarker(marker, storedId);
        LostTalesMapMarkerDefinition old =
                this.giverMarkers.put(storedId, normalized);
        boolean discoveredChanged = addDiscoveredMarkerId(storedId);
        if (discoveredChanged || !sameMarker(old, normalized)) {
            this.markerChanges++;
            return true;
        }
        return false;
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
        String giverId =
                this.giverMarkerIdByCanonicalKey.remove(key);
        if (giverId != null
                && this.giverMarkers.remove(giverId) != null) {
            changed = true;
        }
        if (changed) {
            this.markerChanges++;
        }
        if (sameMarkerIdentity(markerId, this.trackedMapMarkerId)) {
            this.trackedMapMarkerId = "";
            changed = true;
        }
        return changed;
    }

    public boolean setTrackedMapMarkerId(String markerId) {
        if (!isWritable()) {
            return false;
        }
        String normalized =
                LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        if (normalized.length() == 0) {
            return clearTrackedMapMarkerId();
        }
        String discoveredId = findDiscoveredMarkerId(normalized);
        if (discoveredId == null) {
            return false;
        }
        if (sameMarkerIdentity(discoveredId,
                this.trackedMapMarkerId)) {
            return false;
        }
        this.trackedMapMarkerId = discoveredId;
        return true;
    }

    public boolean clearTrackedMapMarkerId() {
        if (!isWritable()) {
            return false;
        }
        boolean changed = this.trackedMapMarkerId != null && this.trackedMapMarkerId.length() > 0;
        this.trackedMapMarkerId = "";
        return changed;
    }

    public boolean trackQuestId(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0 || !this.activeQuests.containsKey(questId)) {
            return false;
        }
        return this.trackedQuestIds.add(questId);
    }

    public boolean trackQuestReference(String questReference) {
        if (!isWritable() || questReference == null
                || questReference.length() == 0) {
            return false;
        }
        if (!this.activeQuests.containsKey(questReference)
                && !LotrQuestReference.isLotrQuest(questReference)) {
            return false;
        }
        return this.trackedQuestIds.add(questReference);
    }

    public boolean untrackQuestId(String questId) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        return this.trackedQuestIds.remove(questId);
    }

    public boolean clearTrackedQuestId() {
        if (!isWritable()) {
            return false;
        }
        boolean changed = !this.trackedQuestIds.isEmpty();
        this.trackedQuestIds.clear();
        return changed;
    }

    /**
     * Ends a running quest as completed. Its History line keeps the
     * optional objectives it achieved and no words: what they add is
     * worded by each reader's game. A failure's or an abandon's line keeps
     * its reason as a lang key instead.
     */
    public boolean completeQuest(String questId, long worldTime,
            Collection<String> completedOptionalObjectiveIds) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        LostTalesQuestProgress progress = this.activeQuests.remove(questId);
        this.trackedQuestIds.remove(questId);
        LostTalesQuestHistoryEntry before = this.questHistory.get(questId);
        LostTalesQuestHistoryEntry old = this.questHistory.put(questId,
                new LostTalesQuestHistoryEntry(questId,
                        LostTalesQuestHistoryEntry.Outcome.COMPLETED,
                        "", worldTime,
                        completedOptionalObjectiveIds,
                        completionsBefore(before) + (progress != null
                                || before == null || !before.isCompleted()
                                ? 1 : 0),
                        pathOf(progress, before)));
        forgetEndedMissives();
        return progress != null || old == null || !old.isCompleted();
    }

    /** How many completions a History line counts; none for no line. */
    private static int completionsBefore(LostTalesQuestHistoryEntry before) {
        return before == null ? 0 : before.getCompletions();
    }

    /** The stages a run reached: its own path, else the line's it replaces. */
    private static java.util.List<String> pathOf(LostTalesQuestProgress progress,
            LostTalesQuestHistoryEntry before) {
        return progress != null ? progress.getStagePath()
                : before != null ? before.getStagePath()
                : Collections.<String>emptyList();
    }

    public boolean failQuest(String questId, String reason, long worldTime) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        LostTalesQuestProgress progress = this.activeQuests.remove(questId);
        this.trackedQuestIds.remove(questId);
        LostTalesQuestHistoryEntry before = this.questHistory.get(questId);
        LostTalesQuestHistoryEntry old = this.questHistory.put(questId,
                new LostTalesQuestHistoryEntry(questId,
                        LostTalesQuestHistoryEntry.Outcome.FAILED,
                        boundedDetail(reason), worldTime,
                        Collections.<String>emptySet(),
                        completionsBefore(before), pathOf(progress, before)));
        forgetEndedMissives();
        return progress != null || old == null || !old.isFailed();
    }

    /** Records who gave a running quest: their entity id, own name and kind. */
    public boolean setQuestGiver(String questId, String entityId, String name,
                                 String kind) {
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (!isWritable() || progress == null) {
            return false;
        }
        progress.setGiver(entityId, name, kind);
        return true;
    }

    /** Sets when the running quest's stage runs out of time; 0 for no limit. */
    public boolean setStageDeadline(String questId, long worldTime) {
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (!isWritable() || progress == null) {
            return false;
        }
        progress.setStageDeadlineWorldTime(worldTime);
        return true;
    }

    /**
     * Marks a running quest as waiting for its hand-in, every stage done
     * (QUEST-31). Answers whether it changed.
     */
    public boolean markReturning(String questId) {
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (!isWritable() || progress == null || progress.isReturning()) {
            return false;
        }
        progress.setReturning(true);
        return true;
    }

    /** Marks a running quest as waiting for a choice, or not; answers whether it changed. */
    public boolean setChoosing(String questId, boolean choosing) {
        LostTalesQuestProgress progress = this.activeQuests.get(questId);
        if (!isWritable() || progress == null || progress.isChoosing() == choosing) {
            return false;
        }
        progress.setChoosing(choosing);
        return true;
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
        this.trackedQuestIds.remove(questId);
        boolean removedHistory = this.questHistory.remove(questId) != null;
        forgetEndedMissives();
        return removedActive || removedHistory;
    }

    public boolean abandonQuest(String questId, String reason,
            long worldTime) {
        if (!isWritable()) {
            return false;
        }
        if (questId == null || questId.length() == 0) {
            return false;
        }
        LostTalesQuestProgress progress = this.activeQuests.remove(questId);
        this.trackedQuestIds.remove(questId);
        if (progress == null) {
            return false;
        }
        this.questHistory.put(questId, new LostTalesQuestHistoryEntry(
                questId, LostTalesQuestHistoryEntry.Outcome.ABANDONED,
                boundedDetail(reason), worldTime,
                Collections.<String>emptySet(),
                completionsBefore(this.questHistory.get(questId)),
                progress.getStagePath()));
        forgetEndedMissives();
        return true;
    }

    public void copyFrom(LostTalesQuestPlayerData oldData) {
        this.markerChanges++;
        this.activeQuests.clear();
        this.questHistory.clear();
        this.discoveredMarkerIds.clear();
        this.discoveredMarkerIdByCanonicalKey.clear();
        this.giverMarkers.clear();
        this.giverMarkerIdByCanonicalKey.clear();
        this.missiveDefinitions.clear();
        this.trackedQuestIds.clear();
        this.trackedMapMarkerId = "";
        this.startersGiven = false;
        this.heardRumours.clear();
        this.waitingRewards.clear();
        this.waitingQuarantine.clear();
        this.readOnly = false;
        this.preservedReadOnlyData = null;
        if (oldData == null) {
            return;
        }
        if (oldData.readOnly) {
            this.readOnly = true;
            this.preservedReadOnlyData = oldData.preservedReadOnlyData == null
                    ? null
                    : oldData.preservedReadOnlyData.copy();
            return;
        }

        for (LostTalesQuestProgress progress : oldData.activeQuests.values()) {
            this.activeQuests.put(progress.getQuestId(), progress.copy());
        }
        this.questHistory.putAll(oldData.questHistory);
        for (String markerId : oldData.discoveredMarkerIds) {
            addDiscoveredMarkerId(markerId);
        }
        for (LostTalesMapMarkerDefinition marker :
                oldData.giverMarkers.values()) {
            addLoadedGiverMarker(marker);
        }
        this.missiveDefinitions.putAll(oldData.missiveDefinitions);
        LostTalesQuestRegistry.registerMissives(this.missiveDefinitions.values());
        for (String questId : oldData.trackedQuestIds) {
            if (questId != null && (this.activeQuests.containsKey(questId)
                    || LotrQuestReference.isLotrQuest(questId))) {
                this.trackedQuestIds.add(questId);
            }
        }
        this.startersGiven = oldData.startersGiven;
        this.heardRumours.putAll(oldData.heardRumours);
        this.waitingRewards.copyFrom(oldData.waitingRewards);
        for (NBTTagCompound aside : oldData.waitingQuarantine) {
            this.waitingQuarantine.add((NBTTagCompound) aside.copy());
        }
        String copiedTrackedMarkerId =
                findDiscoveredMarkerId(oldData.trackedMapMarkerId);
        this.trackedMapMarkerId = copiedTrackedMarkerId == null
                ? "" : copiedTrackedMarkerId;
        pruneInvalidReferences();
    }

    /**
     * Removes the references that point at nothing: a tracked
     * quest that is not running, a tracked marker that is not discovered.
     * Runs as the log is loaded or copied, and after login, respawn or a
     * change of world. A log held read-only (saved under any version but
     * {@link #CURRENT_DATA_VERSION}, or malformed) is left as it is.
     * Returns true when anything was cleaned up.
     */
    public boolean pruneInvalidReferences() {
        if (!isWritable()) {
            return false;
        }
        boolean changed = false;
        ArrayList<String> invalidTrackedQuests = new ArrayList<String>();
        for (String questId : this.trackedQuestIds) {
            if (questId == null || questId.length() == 0
                    || (!this.activeQuests.containsKey(questId)
                        && !LotrQuestReference.isLotrQuest(questId))) {
                invalidTrackedQuests.add(questId);
            }
        }
        if (!invalidTrackedQuests.isEmpty()) {
            this.trackedQuestIds.removeAll(invalidTrackedQuests);
            changed = true;
        }
        if (this.trackedMapMarkerId != null
                && this.trackedMapMarkerId.length() > 0
                && findDiscoveredMarkerId(
                        this.trackedMapMarkerId) == null
                && !LostTalesMapMarkerCatalog.isVisibleByDefault(
                        this.trackedMapMarkerId)) {
            this.trackedMapMarkerId = "";
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
        this.markerChanges++;
        return true;
    }

    /** How many times the discovered markers changed since this log was made. */
    public int getMarkerChanges() {
        return this.markerChanges;
    }

    /** What the LOTR waypoint check saw when it last ran; null before it ran. */
    public Object getWaypointCheckSeen() {
        return this.waypointCheckSeen;
    }

    public void setWaypointCheckSeen(Object seen) {
        this.waypointCheckSeen = seen;
    }

    private boolean addLoadedGiverMarker(
            LostTalesMapMarkerDefinition marker) {
        if (!isValidGiverMarker(marker)) {
            return false;
        }
        String markerId =
                LostTalesQuestMarkerHelper.normalizeMarkerId(
                        marker.getId());
        String key = markerCanonicalKey(markerId);
        if (this.giverMarkerIdByCanonicalKey.containsKey(key)
                || this.giverMarkers.size() >= MAX_GIVER_MARKERS
                || (findDiscoveredMarkerId(markerId) == null
                    && this.discoveredMarkerIds.size()
                            >= MAX_QUEST_ID_HISTORY)) {
            return false;
        }
        LostTalesMapMarkerDefinition normalized =
                normalizeGiverMarker(marker, markerId);
        this.giverMarkers.put(markerId, normalized);
        this.giverMarkerIdByCanonicalKey.put(key, markerId);
        addDiscoveredMarkerId(markerId);
        return true;
    }

    private String findDiscoveredMarkerId(String markerId) {
        String key = markerCanonicalKey(markerId);
        return key.length() == 0 ? null
                : this.discoveredMarkerIdByCanonicalKey.get(key);
    }

    private String findGiverMarkerId(String markerId) {
        String key = markerCanonicalKey(markerId);
        return key.length() == 0 ? null
                : this.giverMarkerIdByCanonicalKey.get(key);
    }

    private static boolean sameMarkerIdentity(
            String first, String second) {
        String firstKey = markerCanonicalKey(first);
        return firstKey.length() > 0
                && firstKey.equals(markerCanonicalKey(second));
    }

    /** A marker's key among this player's markers; empty for an id longer than a stored one may be. */
    private static String markerCanonicalKey(String markerId) {
        String normalized =
                LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        return normalized.length() > MAX_IDENTIFIER_CHARACTERS ? ""
                : LostTalesQuestMarkerHelper.markerCanonicalKey(normalized);
    }

    private static LostTalesMapMarkerDefinition normalizeGiverMarker(
            LostTalesMapMarkerDefinition marker,
            String persistedMarkerId) {
        return new LostTalesMapMarkerDefinition(
                persistedMarkerId,
                marker.getNamedAfter().length() > 0
                        ? safe(marker.getName(), "")
                        : safe(marker.getName(), persistedMarkerId),
                safe(marker.getIconName(), "quest"),
                safe(marker.getColorName(), "white"),
                safe(marker.getCategoryName(), ""),
                "",
                marker.hasFastTravel(), marker.getDimensionId(),
                marker.getX(), marker.getY(), marker.getZ(),
                marker.getCompassFadeInRadius(),
                marker.getDiscoveryRadius(),
                marker.isHiddenUntilDiscovered(),
                marker.isDiscoverable(),
                marker.requiresRegionUnlock(),
                LostTalesMapMarkerSource.QUEST_GIVER,
                false, "", marker.getPriority(), marker.getNamedAfter());
    }

    private static boolean isValidGiverMarker(
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
                && Double.isFinite(marker.getX())
                && Double.isFinite(marker.getY())
                && Double.isFinite(marker.getZ())
                && Double.isFinite(marker.getCompassFadeInRadius())
                && marker.getCompassFadeInRadius() >= 0.0D
                && Double.isFinite(marker.getDiscoveryRadius())
                && marker.getDiscoveryRadius() >= 0.0D;
    }

    private static LostTalesMapMarkerDefinition readGiverMarker(NBTTagCompound markerTag) {
        if (markerTag == null) {
            return null;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(markerTag.getString("MarkerId"));
        if (markerId.length() == 0) {
            return null;
        }
        String namedAfter = markerTag.getString("NamedAfter");
        return new LostTalesMapMarkerDefinition(
                markerId,
                namedAfter.length() > 0 ? markerTag.getString("Name")
                        : safe(markerTag.getString("Name"), markerId),
                safe(markerTag.getString("Icon"), "quest"),
                safe(markerTag.getString("Color"), "white"),
                markerTag.getString("Category"),
                "",
                markerTag.getBoolean("HasFastTravel"),
                markerTag.getInteger("DimensionId"),
                markerTag.getDouble("X"),
                markerTag.getDouble("Y"),
                markerTag.getDouble("Z"),
                markerTag.getDouble("CompassFadeInRadius"),
                markerTag.getDouble("DiscoveryRadius"),
                markerTag.getBoolean("HiddenUntilDiscovered"),
                markerTag.getBoolean("IsDiscoverable"),
                markerTag.getBoolean("RequiresRegionUnlock"),
                LostTalesMapMarkerSource.QUEST_GIVER,
                false, "", markerTag.getInteger("Priority"),
                namedAfter
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
                && left.getNamedAfter().equals(right.getNamedAfter())
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

    private static String boundedDetail(String value) {
        String detail = value == null ? "" : value.trim();
        return detail.length() <= MAX_HISTORY_DETAIL_CHARACTERS ? detail
                : detail.substring(0, MAX_HISTORY_DETAIL_CHARACTERS);
    }

    /**
     * Whether the log is shaped as this build writes it, within every
     * bound: each History line with its outcome as written, its count and
     * its path, each running quest with its path. Any other log is kept
     * whole and read-only.
     */
    static boolean isStructurallyReasonable(NBTTagCompound data) {
        if (!NbtTags.hasCompoundListWithinLimit(
                data, "ActiveQuests", MAX_ACTIVE_QUESTS)
                || !NbtTags.hasCompoundListWithinLimit(
                data, "TrackedQuestIds", MAX_ACTIVE_QUESTS)
                || !NbtTags.hasCompoundListWithinLimit(
                data, "QuestHistory", MAX_QUEST_ID_HISTORY)
                || !NbtTags.hasCompoundListWithinLimit(
                data, "DiscoveredMarkers", MAX_QUEST_ID_HISTORY)
                || !NbtTags.hasCompoundListWithinLimit(
                data, "MissiveDefinitions", MAX_MISSIVES)
                || !NbtTags.hasCompoundListWithinLimit(
                data, "GiverMarkers", MAX_GIVER_MARKERS)
                || !hasReasonableOptionalString(
                data, "TrackedMapMarkerId", MAX_IDENTIFIER_CHARACTERS)
                || !NbtTags.hasCompoundListWithinLimit(
                data, "HeardRumours", MAX_RUMOURS)
                || !hasQuestIdList(data, "HeardRumours")
                || !hasRumourMarkers(data)) {
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
        if (!hasQuestIdList(data, "TrackedQuestIds")
                || !hasReasonableIdList(
                data, "DiscoveredMarkers", "MarkerId")) {
            return false;
        }

        NBTTagList history = data.getTagList(
                "QuestHistory", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < history.tagCount(); i++) {
            NBTTagCompound entry = history.getCompoundTagAt(i);
            if (!entry.hasKey("QuestId", Constants.NBT.TAG_STRING)
                    || !LostTalesQuestIds.fits(entry.getString("QuestId"))
                    || LostTalesQuestHistoryEntry.Outcome.fromName(
                    entry.getString("Outcome")) == null
                    || !hasReasonableOptionalString(entry, "Detail",
                    MAX_HISTORY_DETAIL_CHARACTERS)
                    || !NbtTags.hasCompoundListWithinLimit(entry,
                    "CompletedOptionalObjectives",
                    LostTalesQuestProgress.MAX_OBJECTIVE_ENTRIES)
                    || !hasReasonableIdList(entry,
                    "CompletedOptionalObjectives", "ObjectiveId")
                    || entry.getLong("WorldTime") < 0L
                    || !entry.hasKey("Completions", Constants.NBT.TAG_INT)
                    || entry.getInteger("Completions") < 0
                    || !LostTalesQuestProgress.isReasonableStagePath(entry,
                    "StagePath")) {
                return false;
            }
        }

        NBTTagList missives = data.getTagList(
                "MissiveDefinitions", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < missives.tagCount(); i++) {
            if (!LostTalesQuestDefinitionNbt.isStructurallyReasonable(
                    missives.getCompoundTagAt(i))) {
                return false;
            }
        }

        NBTTagList giverMarkers = data.getTagList(
                "GiverMarkers", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < giverMarkers.tagCount(); i++) {
            if (!isReasonableGiverMarker(
                    giverMarkers.getCompoundTagAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Whether every {@code QuestId} in the list is a quest id within the bound. */
    private static boolean hasQuestIdList(NBTTagCompound owner, String listKey) {
        NBTTagList list = owner.getTagList(
                listKey, Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < list.tagCount(); i++) {
            if (!LostTalesQuestIds.fits(
                    list.getCompoundTagAt(i).getString("QuestId"))) {
                return false;
            }
        }
        return true;
    }

    /** Whether each rumour's place, where it names one, is a marker id within the bound (QUEST-20). */
    private static boolean hasRumourMarkers(NBTTagCompound data) {
        NBTTagList rumours = data.getTagList(
                "HeardRumours", Constants.NBT.TAG_COMPOUND);
        for (int i = 0; i < rumours.tagCount(); i++) {
            if (!hasReasonableOptionalString(rumours.getCompoundTagAt(i),
                    "Marker", MAX_IDENTIFIER_CHARACTERS)) {
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

    private static boolean isReasonableGiverMarker(NBTTagCompound marker) {
        if (!hasReasonableRequiredString(
                marker, "MarkerId", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Name", MAX_NAME_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Icon", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Color", MAX_IDENTIFIER_CHARACTERS)
                || !hasReasonableOptionalString(
                marker, "Category", MAX_NAME_CHARACTERS)
                || !hasReasonableOptionalString(marker, "NamedAfter",
                LostTalesMapMarkerNamedAfter.MAX_LENGTH)
                || !LostTalesMapMarkerNamedAfter.isValid(
                        marker.getString("NamedAfter"))) {
            return false;
        }
        double x = marker.getDouble("X");
        double y = marker.getDouble("Y");
        double z = marker.getDouble("Z");
        double fadeRadius = marker.getDouble("CompassFadeInRadius");
        double discoveryRadius = marker.getDouble("DiscoveryRadius");
        int priority = marker.getInteger("Priority");
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && Double.isFinite(fadeRadius) && fadeRadius >= 0.0D
                && Double.isFinite(discoveryRadius) && discoveryRadius >= 0.0D
                && priority >= LostTalesMapMarkerDefinition.MIN_PRIORITY
                && priority <= LostTalesMapMarkerDefinition.MAX_PRIORITY;
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
}
