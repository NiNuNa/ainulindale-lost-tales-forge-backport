package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdentity;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    private static final Set<String> PINNED_QUESTS = new LinkedHashSet<String>();
    private static String pinnedMapMarkerId = "";
    private static boolean receivedSync;

    private LostTalesClientQuestProgressStore() {}

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<LostTalesQuestHistoryEntry> questHistory, Collection<String> pinnedQuestIdsIn, Collection<String> discoveredMarkerIds, String pinnedMapMarkerIdIn) {
        ACTIVE_QUESTS.clear();
        QUEST_HISTORY.clear();
        DISCOVERED_MARKERS.clear();
        DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.clear();
        PINNED_QUESTS.clear();
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

        if (pinnedQuestIdsIn != null) {
            for (String questId : pinnedQuestIdsIn) {
                if (questId != null && questId.length() > 0) {
                    PINNED_QUESTS.add(questId);
                }
            }
        }
        String storedPinnedMarkerId =
                findDiscoveredMarkerId(pinnedMapMarkerIdIn);
        pinnedMapMarkerId = storedPinnedMarkerId == null
                ? "" : storedPinnedMarkerId;
    }

    public static synchronized void clear() {
        ACTIVE_QUESTS.clear();
        QUEST_HISTORY.clear();
        DISCOVERED_MARKERS.clear();
        DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.clear();
        PINNED_QUESTS.clear();
        pinnedMapMarkerId = "";
        receivedSync = false;
    }

    public static synchronized Collection<LostTalesQuestProgress> getActiveQuests() {
        ArrayList<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (LostTalesQuestProgress progress : ACTIVE_QUESTS.values()) {
            copy.add(progress.copy());
        }
        return Collections.unmodifiableCollection(copy);
    }

    public static synchronized Set<String> getCompletedQuestIds() {
        LinkedHashSet<String> completed = new LinkedHashSet<String>();
        for (LostTalesQuestHistoryEntry entry : QUEST_HISTORY.values()) {
            if (entry.isCompleted()) {
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

    public static synchronized Set<String> getDiscoveredMarkerIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(DISCOVERED_MARKERS));
    }

    public static synchronized LostTalesQuestProgress getActiveQuest(String questId) {
        LostTalesQuestProgress progress = ACTIVE_QUESTS.get(questId);
        return progress == null ? null : progress.copy();
    }

    public static synchronized boolean isQuestActive(String questId) {
        return ACTIVE_QUESTS.containsKey(questId);
    }

    public static synchronized boolean isQuestCompleted(String questId) {
        LostTalesQuestHistoryEntry entry = getQuestHistoryEntry(questId);
        return entry != null && entry.isCompleted();
    }

    public static synchronized boolean isQuestFailed(String questId) {
        LostTalesQuestHistoryEntry entry = getQuestHistoryEntry(questId);
        return entry != null && entry.isFailed();
    }

    public static synchronized boolean isQuestAbandoned(String questId) {
        LostTalesQuestHistoryEntry entry = getQuestHistoryEntry(questId);
        return entry != null && entry.isAbandoned();
    }

    public static synchronized boolean isMarkerDiscovered(String markerId) {
        return findDiscoveredMarkerId(markerId) != null;
    }

    public static synchronized String getPinnedQuestId() {
        for (String questId : PINNED_QUESTS) {
            if (ACTIVE_QUESTS.containsKey(questId)) {
                return questId;
            }
        }
        return "";
    }

    public static synchronized Set<String> getPinnedQuestIds() {
        return Collections.unmodifiableSet(
                new LinkedHashSet<String>(PINNED_QUESTS));
    }

    public static synchronized boolean isQuestPinned(String questId) {
        return questId != null && PINNED_QUESTS.contains(questId) && ACTIVE_QUESTS.containsKey(questId);
    }

    public static synchronized boolean isQuestReferencePinned(
            String questReference) {
        return questReference != null
                && PINNED_QUESTS.contains(questReference);
    }

    public static synchronized Collection<LostTalesQuestProgress> getPinnedQuests() {
        ArrayList<LostTalesQuestProgress> copy = new ArrayList<LostTalesQuestProgress>();
        for (String questId : PINNED_QUESTS) {
            LostTalesQuestProgress progress = ACTIVE_QUESTS.get(questId);
            if (progress != null) {
                copy.add(progress.copy());
            }
        }
        return Collections.unmodifiableCollection(copy);
    }

    public static synchronized boolean hasPinnedQuest() {
        return !getPinnedQuestIds().isEmpty();
    }

    public static synchronized String getPinnedMapMarkerId() {
        return pinnedMapMarkerId == null ? "" : pinnedMapMarkerId;
    }

    public static synchronized boolean isMapMarkerPinned(String markerId) {
        return sameMarkerIdentity(markerId, pinnedMapMarkerId);
    }

    public static synchronized boolean hasPinnedMapMarker() {
        return findDiscoveredMarkerId(pinnedMapMarkerId) != null;
    }

    public static synchronized boolean hasReceivedSync() {
        return receivedSync;
    }

    private static void addDiscoveredMarkerId(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim();
        String key = markerCanonicalKey(normalized);
        if (key.length() == 0
                || DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY
                        .containsKey(key)) {
            return;
        }
        DISCOVERED_MARKERS.add(normalized);
        DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.put(key, normalized);
    }

    private static String findDiscoveredMarkerId(String markerId) {
        String key = markerCanonicalKey(markerId);
        return key.length() == 0 ? null
                : DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY.get(key);
    }

    private static boolean sameMarkerIdentity(
            String first, String second) {
        String firstKey = markerCanonicalKey(first);
        return firstKey.length() > 0
                && firstKey.equals(markerCanonicalKey(second));
    }

    private static String markerCanonicalKey(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim();
        if (normalized.length() == 0) {
            return "";
        }
        return LostTalesMapMarkerIdentity.create(
                normalized,
                LostTalesMapMarkerIdentity.Authority.QUEST_PLAYER)
                .getCanonicalKey();
    }
}
