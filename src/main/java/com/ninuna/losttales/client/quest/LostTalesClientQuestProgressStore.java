package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.quest.player.WaitingReward;
import com.ninuna.losttales.quest.QuestRumourPlace;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
/** Client-side cache of the quest state most recently synced by the server. */
public final class LostTalesClientQuestProgressStore {
    private static final Map<String, LostTalesQuestProgress> ACTIVE_QUESTS = new LinkedHashMap<String, LostTalesQuestProgress>();
    private static final Map<String, LostTalesQuestHistoryEntry> QUEST_HISTORY = new LinkedHashMap<String, LostTalesQuestHistoryEntry>();
    private static final Set<String> DISCOVERED_MARKERS = new LinkedHashSet<String>();
    private static final Map<String, String>
            DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY =
                    new LinkedHashMap<String, String>();
    private static final Set<String> TRACKED_QUESTS = new LinkedHashSet<String>();
    private static String trackedMapMarkerId = "";
    private static boolean receivedSync;

    private LostTalesClientQuestProgressStore() {}

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<LostTalesQuestHistoryEntry> questHistory, Collection<String> trackedQuestIdsIn, Collection<String> discoveredMarkerIds, String trackedMapMarkerIdIn) {
        ACTIVE_QUESTS.clear();
        QUEST_HISTORY.clear();
        DISCOVERED_MARKERS.clear();
        DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.clear();
        TRACKED_QUESTS.clear();
        receivedSync = true;

        if (activeQuests != null) {
            for (LostTalesQuestProgress progress : activeQuests) {
                if (progress != null && progress.getQuestId() != null && progress.getQuestId().length() > 0) {
                    ACTIVE_QUESTS.put(progress.getQuestId(), progress.copy());
                }
            }
        }

        if (questHistory != null) {
            for (LostTalesQuestHistoryEntry entry : questHistory) {
                if (entry != null && entry.getQuestId().length() > 0) {
                    QUEST_HISTORY.put(entry.getQuestId(), entry);
                }
            }
        }

        if (discoveredMarkerIds != null) {
            for (String markerId : discoveredMarkerIds) {
                addDiscoveredMarkerId(markerId);
            }
        }

        if (trackedQuestIdsIn != null) {
            for (String questId : trackedQuestIdsIn) {
                if (questId != null && questId.length() > 0) {
                    TRACKED_QUESTS.add(questId);
                }
            }
        }
        String storedTrackedMarkerId =
                findDiscoveredMarkerId(trackedMapMarkerIdIn);
        trackedMapMarkerId = storedTrackedMarkerId == null
                ? "" : storedTrackedMarkerId;
    }

    /**
     * The running quests held, each one of {@code moved} standing in place
     * of the quest it names; one the log does not hold is left out.
     */
    public static synchronized Collection<LostTalesQuestProgress> withCounts(
            Collection<LostTalesQuestProgress> moved) {
        Map<String, LostTalesQuestProgress> active =
                new LinkedHashMap<String, LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : ACTIVE_QUESTS.values()) {
            active.put(progress.getQuestId(), progress.copy());
        }
        if (moved != null) {
            for (LostTalesQuestProgress progress : moved) {
                if (progress != null && active.containsKey(progress.getQuestId())) {
                    active.put(progress.getQuestId(), progress.copy());
                }
            }
        }
        return Collections.unmodifiableCollection(
                new ArrayList<LostTalesQuestProgress>(active.values()));
    }

    /** Puts {@code activeQuests} in place of the running quests held; the rest of the log stays. */
    public static synchronized void replaceActiveQuests(
            Collection<LostTalesQuestProgress> activeQuests) {
        ACTIVE_QUESTS.clear();
        if (activeQuests != null) {
            for (LostTalesQuestProgress progress : activeQuests) {
                if (progress != null && progress.getQuestId() != null
                        && progress.getQuestId().length() > 0) {
                    ACTIVE_QUESTS.put(progress.getQuestId(), progress.copy());
                }
            }
        }
    }

    /** The quests whose rumours the character heard, as the server last said (QUEST-20). */
    private static final Set<String> HEARD_RUMOURS = new LinkedHashSet<String>();

    public static synchronized void setHeardRumours(Collection<String> rumours) {
        HEARD_RUMOURS.clear();
        if (rumours != null) {
            HEARD_RUMOURS.addAll(rumours);
        }
    }

    public static synchronized Set<String> getHeardRumours() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(HEARD_RUMOURS));
    }

    /** Where the rumours heard point, by quest, as the server last said (QUEST-20). */
    private static final Map<String, QuestRumourPlace> RUMOUR_PLACES =
            new LinkedHashMap<String, QuestRumourPlace>();

    public static synchronized void setRumourPlaces(Collection<QuestRumourPlace> places) {
        RUMOUR_PLACES.clear();
        if (places != null) {
            for (QuestRumourPlace place : places) {
                if (place != null && place.isValid()) {
                    RUMOUR_PLACES.put(place.getQuestId(), place);
                }
            }
        }
    }

    /** Where the quest's rumour points; null for nowhere. */
    public static synchronized QuestRumourPlace getRumourPlace(String questId) {
        return questId == null ? null : RUMOUR_PLACES.get(questId);
    }

    /** Where every rumour heard points, in the order they were heard. */
    public static synchronized List<QuestRumourPlace> getRumourPlaces() {
        return Collections.unmodifiableList(
                new ArrayList<QuestRumourPlace>(RUMOUR_PLACES.values()));
    }

    public static synchronized void clear() {
        HEARD_RUMOURS.clear();
        RUMOUR_PLACES.clear();
        ACTIVE_QUESTS.clear();
        QUEST_HISTORY.clear();
        DISCOVERED_MARKERS.clear();
        DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.clear();
        TRACKED_QUESTS.clear();
        trackedMapMarkerId = "";
        receivedSync = false;
        WAITING_REWARDS.clear();
    }

    /** The rewards waiting for room, each quest's, as the server last said (QUEST-44). */
    private static final Map<String, List<WaitingReward>> WAITING_REWARDS =
            new LinkedHashMap<String, List<WaitingReward>>();

    public static synchronized void setWaitingRewards(
            Map<String, List<WaitingReward>> rewards) {
        WAITING_REWARDS.clear();
        if (rewards != null) {
            for (Map.Entry<String, List<WaitingReward>> entry : rewards.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null
                        && !entry.getValue().isEmpty()) {
                    WAITING_REWARDS.put(entry.getKey(), Collections.unmodifiableList(
                            new ArrayList<WaitingReward>(entry.getValue())));
                }
            }
        }
    }

    /** What waits under the quest; empty for nothing. */
    public static synchronized List<WaitingReward> getWaitingRewards(String questId) {
        List<WaitingReward> waiting = questId == null ? null
                : WAITING_REWARDS.get(questId);
        return waiting == null ? Collections.<WaitingReward>emptyList() : waiting;
    }

    public static synchronized Collection<LostTalesQuestProgress> getActiveQuests() {
        ArrayList<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : ACTIVE_QUESTS.values()) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableCollection(copy);
    }

    /** The quests ever completed, however a later run of each ended. */
    public static synchronized Set<String> getCompletedQuestIds() {
        LinkedHashSet<String> completed = new LinkedHashSet<String>();
        for (LostTalesQuestHistoryEntry entry : QUEST_HISTORY.values()) {
            if (entry.wasEverCompleted()) {
                completed.add(entry.getQuestId());
            }
        }
        return Collections.unmodifiableSet(completed);
    }

    public static synchronized Collection<LostTalesQuestHistoryEntry> getQuestHistory() {
        return Collections.unmodifiableCollection(
                new ArrayList<LostTalesQuestHistoryEntry>(
                        QUEST_HISTORY.values()));
    }

    public static synchronized LostTalesQuestHistoryEntry getQuestHistoryEntry(
            String questId) {
        return questId == null ? null : QUEST_HISTORY.get(questId);
    }

    public static synchronized LostTalesQuestProgress getActiveQuest(String questId) {
        LostTalesQuestProgress progress = ACTIVE_QUESTS.get(questId);
        return progress == null ? null : progress.copy();
    }

    public static synchronized boolean isQuestActive(String questId) {
        return ACTIVE_QUESTS.containsKey(questId);
    }

    /** Whether the quest was ever completed, however a later run of it ended. */
    public static synchronized boolean isQuestCompleted(String questId) {
        LostTalesQuestHistoryEntry entry = getQuestHistoryEntry(questId);
        return entry != null && entry.wasEverCompleted();
    }

    public static synchronized boolean isMarkerDiscovered(String markerId) {
        return findDiscoveredMarkerId(markerId) != null;
    }

    public static synchronized boolean isQuestTracked(String questId) {
        return questId != null && TRACKED_QUESTS.contains(questId) && ACTIVE_QUESTS.containsKey(questId);
    }

    public static synchronized boolean isQuestReferenceTracked(
            String questReference) {
        return questReference != null
                && TRACKED_QUESTS.contains(questReference);
    }

    public static synchronized Collection<LostTalesQuestProgress> getTrackedQuests() {
        ArrayList<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (String questId : TRACKED_QUESTS) {
            LostTalesQuestProgress progress = ACTIVE_QUESTS.get(questId);
            if (progress != null) {
                copy.add(progress.copy());
            }
        }
        return Collections.unmodifiableCollection(copy);
    }

    public static synchronized boolean isMapMarkerTracked(String markerId) {
        return sameMarkerIdentity(markerId, trackedMapMarkerId);
    }

    public static synchronized boolean hasReceivedSync() {
        return receivedSync;
    }

    private static void addDiscoveredMarkerId(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim();
        String key = LostTalesQuestMarkerHelper.markerCanonicalKey(normalized);
        if (key.length() == 0
                || DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY
                        .containsKey(key)) {
            return;
        }
        DISCOVERED_MARKERS.add(normalized);
        DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.put(key, normalized);
    }

    private static String findDiscoveredMarkerId(String markerId) {
        String key = LostTalesQuestMarkerHelper.markerCanonicalKey(markerId);
        return key.length() == 0 ? null
                : DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.get(key);
    }

    private static boolean sameMarkerIdentity(
            String first, String second) {
        String firstKey = LostTalesQuestMarkerHelper.markerCanonicalKey(first);
        return firstKey.length() > 0
                && firstKey.equals(LostTalesQuestMarkerHelper.markerCanonicalKey(second));
    }
}
