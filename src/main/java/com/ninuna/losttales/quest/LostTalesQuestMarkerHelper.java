package com.ninuna.losttales.quest;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdentity;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
/** Reads the comma-separated marker ids a quest file's {@code markers} and objectives name. */
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

    /**
     * Whether a {@code markers} key is {@code giver}: its markers are placed
     * where the player meets the giver. Every other key names markers that
     * stand where the marker files put them.
     */
    public static boolean isDynamicQuestGiverMarkerKey(String key) {
        return GIVER_KEY.equals(key);
    }

    /** The {@code markers} key whose markers stand where the giver was met. */
    public static final String GIVER_KEY = "giver";

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

    /**
     * The key a marker id names a player's quest marker by, so two
     * spellings of one marker meet; empty for no id.
     */
    public static String markerCanonicalKey(String markerId) {
        String normalized = normalizeMarkerId(markerId);
        if (normalized.length() == 0) {
            return "";
        }
        return LostTalesMapMarkerIdentity.create(normalized)
                .getCanonicalKey();
    }
}
