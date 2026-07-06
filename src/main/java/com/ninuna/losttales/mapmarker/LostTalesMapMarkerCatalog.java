package com.ninuna.losttales.mapmarker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.quest.LostTalesQuestDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import cpw.mods.fml.common.FMLLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Server-safe catalog of bundled map marker JSON.
 *
 * The client HUD still uses IResourceManager so resource packs can override marker files.
 * This catalog deliberately reads the mod classpath only; it is used for validation,
 * commands, and logging on both integrated and dedicated servers.
 */
public final class LostTalesMapMarkerCatalog {
    private static final String[] SHARED_MARKER_FILES = new String[] {
            "towns",
            "settlements",
            "cities",
            "forts",
            "camps",
            "caves",
            "quest_hints"
    };

    private static final Map<String, LostTalesMapMarkerDefinition> MARKERS_BY_ID = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
    private static List<LostTalesMapMarkerDefinition> sortedMarkers = Collections.emptyList();
    private static boolean loaded;

    private LostTalesMapMarkerCatalog() {}

    public static synchronized void ensureLoaded() {
        if (!loaded) {
            reloadFromClasspath();
        }
    }

    public static synchronized void reloadFromClasspath() {
        Map<String, LostTalesMapMarkerDefinition> loadedMarkers = new LinkedHashMap<String, LostTalesMapMarkerDefinition>();
        for (String fileName : SHARED_MARKER_FILES) {
            loadFileFromClasspath(fileName, loadedMarkers);
        }

        ArrayList<LostTalesMapMarkerDefinition> sorted = new ArrayList<LostTalesMapMarkerDefinition>(loadedMarkers.values());
        Collections.sort(sorted, new Comparator<LostTalesMapMarkerDefinition>() {
            @Override
            public int compare(LostTalesMapMarkerDefinition left, LostTalesMapMarkerDefinition right) {
                return left.getName().compareToIgnoreCase(right.getName());
            }
        });

        MARKERS_BY_ID.clear();
        MARKERS_BY_ID.putAll(loadedMarkers);
        sortedMarkers = Collections.unmodifiableList(sorted);
        loaded = true;
    }

    public static synchronized Collection<LostTalesMapMarkerDefinition> getMarkers() {
        ensureLoaded();
        return sortedMarkers;
    }

    public static synchronized Set<String> getMarkerIds() {
        ensureLoaded();
        return Collections.unmodifiableSet(new LinkedHashSet<String>(MARKERS_BY_ID.keySet()));
    }

    public static synchronized LostTalesMapMarkerDefinition getMarker(String markerId) {
        ensureLoaded();
        return MARKERS_BY_ID.get(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    public static synchronized boolean containsMarker(String markerId) {
        ensureLoaded();
        return MARKERS_BY_ID.containsKey(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    public static String getDisplayName(String markerId) {
        LostTalesMapMarkerDefinition marker = getMarker(markerId);
        if (marker == null) {
            return LostTalesQuestMarkerHelper.normalizeMarkerId(markerId);
        }
        return marker.getId() + " (" + marker.getName() + ")";
    }

    public static void logQuestMarkerWarnings(Collection<LostTalesQuestDefinition> quests) {
        if (quests == null || quests.isEmpty()) {
            return;
        }

        ensureLoaded();
        for (LostTalesQuestDefinition quest : quests) {
            if (quest == null) {
                continue;
            }
            for (String markerId : LostTalesQuestMarkerHelper.collectStaticQuestMarkerIds(quest)) {
                if (!containsMarker(markerId)) {
                    FMLLog.warning("[%s] Quest %s references missing map marker id: %s", LostTalesMetaData.MOD_ID, quest.getId(), markerId);
                }
            }
        }
    }

    private static void loadFileFromClasspath(String fileName, Map<String, LostTalesMapMarkerDefinition> markers) {
        Reader reader = null;
        try {
            String path = "assets/" + LostTalesMetaData.MOD_ID + "/map_marker/" + fileName + ".json";
            InputStream stream = LostTalesMapMarkerCatalog.class.getClassLoader().getResourceAsStream(path);
            if (stream == null) {
                return;
            }
            reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
            JsonElement rootElement = new JsonParser().parse(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                return;
            }

            JsonElement markersElement = rootElement.getAsJsonObject().get("map_markers");
            if (markersElement == null || !markersElement.isJsonArray()) {
                return;
            }

            JsonArray array = markersElement.getAsJsonArray();
            for (JsonElement entryElement : array) {
                if (entryElement == null || !entryElement.isJsonObject()) {
                    continue;
                }
                LostTalesMapMarkerDefinition marker = parseMarker(entryElement.getAsJsonObject());
                if (marker != null) {
                    markers.put(marker.getId(), marker);
                }
            }
        } catch (RuntimeException e) {
            FMLLog.warning("[%s] Failed to parse map marker file %s.json: %s", LostTalesMetaData.MOD_ID, fileName, e.getMessage());
        } finally {
            closeQuietly(reader);
        }
    }

    private static LostTalesMapMarkerDefinition parseMarker(JsonObject object) {
        String name = getString(object, "name", null);
        if (name == null || name.length() == 0) {
            return null;
        }
        if (!hasNumber(object, "x") || !hasNumber(object, "y") || !hasNumber(object, "z")) {
            return null;
        }

        String id = LostTalesQuestMarkerHelper.normalizeMarkerId(getString(object, "id", name));
        if (id.length() == 0) {
            return null;
        }
        String icon = getString(object, "icon", "undiscovered");
        String color = getString(object, "color", "white");
        int dimensionId = LostTalesDimensionHelper.parseDimensionId(getString(object, "dimension", "minecraft:overworld"), 0);
        double x = object.get("x").getAsDouble();
        double y = object.get("y").getAsDouble();
        double z = object.get("z").getAsDouble();
        boolean hidden = getBoolean(object, "hiddenUntilDiscovered", getBoolean(object, "requiresDiscovery", false));
        if (getBoolean(object, "discoveredByDefault", false)) {
            hidden = false;
        }
        return new LostTalesMapMarkerDefinition(id, name, icon, color, dimensionId, x, y, z, hidden);
    }

    private static boolean hasNumber(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber();
    }

    private static String getString(JsonObject object, String key, String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsString();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static boolean getBoolean(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static void closeQuietly(Reader reader) {
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
    }
}
