package com.ninuna.losttales.quest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small shared helper for the comma-separated marker-id fields used in quest JSON. */
public final class LostTalesQuestMarkerHelper {
    private LostTalesQuestMarkerHelper() {}

    public static List<String> collectQuestMarkerIds(LostTalesQuestDefinition quest) {
        ArrayList<String> ids = new ArrayList<String>();
        if (quest == null || quest.getMarkers().isEmpty()) {
            return ids;
        }

        for (String value : quest.getMarkers().values()) {
            addMarkerIds(ids, value);
        }
        return ids;
    }

    public static List<String> collectQuestMarkerIds(Iterable<LostTalesQuestDefinition> quests) {
        ArrayList<String> ids = new ArrayList<String>();
        if (quests == null) {
            return ids;
        }

        for (LostTalesQuestDefinition quest : quests) {
            if (quest == null) {
                continue;
            }
            for (String markerId : collectQuestMarkerIds(quest)) {
                if (!ids.contains(markerId)) {
                    ids.add(markerId);
                }
            }
        }
        return ids;
    }


    public static List<String> collectStaticQuestMarkerIds(LostTalesQuestDefinition quest) {
        ArrayList<String> ids = new ArrayList<String>();
        if (quest == null || quest.getMarkers().isEmpty()) {
            return ids;
        }

        for (Map.Entry<String, String> entry : quest.getMarkers().entrySet()) {
            if (!isDynamicQuestGiverMarkerKey(entry.getKey())) {
                addMarkerIds(ids, entry.getValue());
            }
        }
        return ids;
    }

    public static List<String> collectDynamicQuestGiverMarkerIds(LostTalesQuestDefinition quest) {
        ArrayList<String> ids = new ArrayList<String>();
        if (quest == null || quest.getMarkers().isEmpty()) {
            return ids;
        }

        for (Map.Entry<String, String> entry : quest.getMarkers().entrySet()) {
            if (isDynamicQuestGiverMarkerKey(entry.getKey())) {
                addMarkerIds(ids, entry.getValue());
            }
        }
        return ids;
    }

    public static boolean isDynamicQuestGiverMarkerKey(String key) {
        if (key == null) {
            return false;
        }
        String normalized = key.trim().toLowerCase().replace("_", "").replace("-", "");
        return "giver".equals(normalized)
                || "questgiver".equals(normalized)
                || "npc".equals(normalized)
                || "source".equals(normalized)
                || "turnin".equals(normalized)
                || "return".equals(normalized);
    }

    public static void addMarkerIds(List<String> ids, String value) {
        if (ids == null || value == null) {
            return;
        }

        String[] parts = value.split(",");
        for (String part : parts) {
            String markerId = normalizeMarkerId(part);
            if (markerId.length() > 0 && !ids.contains(markerId)) {
                ids.add(markerId);
            }
        }
    }

    public static String normalizeMarkerId(String markerId) {
        return markerId == null ? "" : markerId.trim();
    }

    public static String describeMarkerMap(Map<String, String> markers) {
        ArrayList<String> ids = new ArrayList<String>();
        if (markers != null) {
            for (String value : markers.values()) {
                addMarkerIds(ids, value);
            }
        }
        if (ids.isEmpty()) {
            return "none";
        }
        StringBuilder builder = new StringBuilder();
        for (String id : ids) {
            if (builder.length() > 0) {
                builder.append(", ");
            }
            builder.append(id);
        }
        return builder.toString();
    }
}
