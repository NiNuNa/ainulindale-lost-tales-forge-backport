package com.ninuna.losttales.client.quest;

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
    private static final Set<String> DISCOVERED_MARKERS = new LinkedHashSet<String>();
    private static String pinnedQuestId = "";
    private static String pinnedMapMarkerId = "";
    private static boolean receivedSync;

    private LostTalesClientQuestProgressStore() {}

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds) {
        update(activeQuests, completedQuestIds, "", Collections.<String>emptySet(), "");
    }

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestIdIn) {
        update(activeQuests, completedQuestIds, pinnedQuestIdIn, Collections.<String>emptySet(), "");
    }

    public static synchronized void update(Collection<LostTalesQuestProgress> activeQuests, Collection<String> completedQuestIds, String pinnedQuestIdIn, Collection<String> discoveredMarkerIds, String pinnedMapMarkerIdIn) {
        ACTIVE_QUESTS.clear();
        COMPLETED_QUESTS.clear();
        DISCOVERED_MARKERS.clear();
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

        if (discoveredMarkerIds != null) {
            for (String markerId : discoveredMarkerIds) {
                if (markerId != null && markerId.length() > 0) {
                    DISCOVERED_MARKERS.add(markerId);
                }
            }
        }

        pinnedQuestId = pinnedQuestIdIn != null && ACTIVE_QUESTS.containsKey(pinnedQuestIdIn) ? pinnedQuestIdIn : "";
        pinnedMapMarkerId = pinnedMapMarkerIdIn != null && DISCOVERED_MARKERS.contains(pinnedMapMarkerIdIn) ? pinnedMapMarkerIdIn : "";
    }

    public static synchronized void clear() {
        ACTIVE_QUESTS.clear();
        COMPLETED_QUESTS.clear();
        DISCOVERED_MARKERS.clear();
        pinnedQuestId = "";
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

    public static synchronized boolean isMarkerDiscovered(String markerId) {
        return markerId != null && DISCOVERED_MARKERS.contains(markerId);
    }

    public static synchronized String getPinnedQuestId() {
        return pinnedQuestId == null ? "" : pinnedQuestId;
    }

    public static synchronized boolean isQuestPinned(String questId) {
        return questId != null && questId.equals(pinnedQuestId);
    }

    public static synchronized LostTalesQuestProgress getPinnedQuest() {
        LostTalesQuestProgress progress = ACTIVE_QUESTS.get(getPinnedQuestId());
        return progress == null ? null : progress.copy();
    }

    public static synchronized boolean hasPinnedQuest() {
        return pinnedQuestId != null && pinnedQuestId.length() > 0 && ACTIVE_QUESTS.containsKey(pinnedQuestId);
    }

    public static synchronized String getPinnedMapMarkerId() {
        return pinnedMapMarkerId == null ? "" : pinnedMapMarkerId;
    }

    public static synchronized boolean isMapMarkerPinned(String markerId) {
        return markerId != null && markerId.equals(pinnedMapMarkerId);
    }

    public static synchronized boolean hasPinnedMapMarker() {
        return pinnedMapMarkerId != null && pinnedMapMarkerId.length() > 0 && DISCOVERED_MARKERS.contains(pinnedMapMarkerId);
    }

    public static synchronized boolean hasAnyState() {
        return !ACTIVE_QUESTS.isEmpty() || !COMPLETED_QUESTS.isEmpty();
    }

    public static synchronized boolean hasReceivedSync() {
        return receivedSync;
    }
}
