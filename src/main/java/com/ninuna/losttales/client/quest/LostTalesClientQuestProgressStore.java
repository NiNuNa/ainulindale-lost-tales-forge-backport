package com.ninuna.losttales.client.quest;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdentity;
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
    private static final Set<String> COMPLETED_QUESTS = new LinkedHashSet<String>();
    private static final Set<String> FAILED_QUESTS = new LinkedHashSet<String>();
    private static final Set<String> DISCOVERED_MARKERS = new LinkedHashSet<String>();
    private static final Map<String, String>
            DISCOVERED_MARKER_IDS_BY_CANONICAL_KEY =
                    new LinkedHashMap<String, String>();
    private static final Set<String> PINNED_QUESTS = new LinkedHashSet<String>();
    private static String pinnedMapMarkerId = "";
    private static boolean receivedSync;

    private LostTalesClientQuestProgressStore() {}

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds) {
        update(activeQuests, completedQuestIds, Collections.<String>emptySet(), Collections.<String>emptySet(), Collections.<String>emptySet(), "");
    }

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestIdIn) {
        LinkedHashSet<String> pinned = new LinkedHashSet<String>();
        if (pinnedQuestIdIn != null && pinnedQuestIdIn.length() > 0) {
            pinned.add(pinnedQuestIdIn);
        }
        update(activeQuests, completedQuestIds, Collections.<String>emptySet(), pinned, Collections.<String>emptySet(), "");
    }

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestIdIn, Collection<String> discoveredMarkerIds, String pinnedMapMarkerIdIn) {
        LinkedHashSet<String> pinned = new LinkedHashSet<String>();
        if (pinnedQuestIdIn != null && pinnedQuestIdIn.length() > 0) {
            pinned.add(pinnedQuestIdIn);
        }
        update(activeQuests, completedQuestIds, Collections.<String>emptySet(), pinned, discoveredMarkerIds, pinnedMapMarkerIdIn);
    }

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, Collection<String> failedQuestIds, Collection<String> pinnedQuestIdsIn, Collection<String> discoveredMarkerIds, String pinnedMapMarkerIdIn) {
        ACTIVE_QUESTS.clear();
        COMPLETED_QUESTS.clear();
        FAILED_QUESTS.clear();
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

        if (completedQuestIds != null) {
            for (String questId : completedQuestIds) {
                if (questId != null && questId.length() > 0) {
                    COMPLETED_QUESTS.add(questId);
                }
            }
        }

        if (failedQuestIds != null) {
            for (String questId : failedQuestIds) {
                if (questId != null && questId.length() > 0) {
                    FAILED_QUESTS.add(questId);
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
                if (questId != null && ACTIVE_QUESTS.containsKey(questId)) {
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
        COMPLETED_QUESTS.clear();
        FAILED_QUESTS.clear();
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
        return Collections.unmodifiableSet(new LinkedHashSet<String>(COMPLETED_QUESTS));
    }

    public static synchronized Set<String> getFailedQuestIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(FAILED_QUESTS));
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
        return COMPLETED_QUESTS.contains(questId);
    }

    public static synchronized boolean isQuestFailed(String questId) {
        return questId != null && FAILED_QUESTS.contains(questId);
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
        LinkedHashSet<String> copy = new LinkedHashSet<String>();
        for (String questId : PINNED_QUESTS) {
            if (ACTIVE_QUESTS.containsKey(questId)) {
                copy.add(questId);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    public static synchronized boolean isQuestPinned(String questId) {
        return questId != null && PINNED_QUESTS.contains(questId) && ACTIVE_QUESTS.containsKey(questId);
    }

    public static synchronized LostTalesQuestProgress getPinnedQuest() {
        LostTalesQuestProgress progress = ACTIVE_QUESTS.get(getPinnedQuestId());
        return progress == null ? null : progress.copy();
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

    public static synchronized boolean hasAnyState() {
        return !ACTIVE_QUESTS.isEmpty() || !COMPLETED_QUESTS.isEmpty() || !FAILED_QUESTS.isEmpty();
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
