package com.ninuna.losttales.client.mapmarker;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.util.LostTalesDimensionHelper;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lotr.common.LOTRDimension;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

final class LostTalesMapMarkerResourceLoader {
    private static final String[] SHARED_MARKER_FILES = new String[] {
            "towns",
            "settlements",
            "cities",
            "forts",
            "camps",
            "caves",
            "lotr_waypoints"
    };

    private LostTalesMapMarkerResourceLoader() {}

    static List<LostTalesMapMarkerData> loadSharedMarkers(IResourceManager resourceManager) {
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        if (resourceManager == null) {
            return markers;
        }

        for (String fileName : SHARED_MARKER_FILES) {
            ResourceLocation location = new ResourceLocation(LostTalesMetaData.MOD_ID, "map_marker/" + fileName + ".json");
            loadFile(resourceManager, location, markers);
        }
        return markers;
    }

    private static void loadFile(IResourceManager resourceManager, ResourceLocation location, List<LostTalesMapMarkerData> markers) {
        Reader reader = null;
        try {
            IResource resource = resourceManager.getResource(location);
            reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);

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
                if (entryElement == null || !entryElement.isJsonObject()) continue;

                LostTalesMapMarkerData marker = parseMarker(entryElement.getAsJsonObject());
                if (marker != null) {
                    markers.add(marker);
                }
            }
        } catch (IOException ignored) {
            // Missing marker files are allowed; this lets resource packs override
            // only the groups they need without breaking the HUD.
        } catch (RuntimeException ignored) {
            // A broken marker file should not crash resource reload or the HUD.
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {}
            }
        }
    }

    private static LostTalesMapMarkerData parseMarker(JsonObject object) {
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
        boolean hasFastTravel = getBoolean(object, "hasFastTravel", false);
        String fastTravelWaypointCode = getString(object, "fastTravelWaypointCode", "");
        String icon = getString(object, "icon", hasFastTravel ? "fort" : "undiscovered");
        String color = getString(object, "color", "white");
        String category = getString(object, "category", hasFastTravel ? LostTalesMapMarkerData.CATEGORY_POINT_OF_INTEREST : LostTalesMapMarkerData.CATEGORY_DEFAULT);
        String description = getString(object, "description", getString(object, "info", getString(object, "lore", "")));
        int dimensionId = parseDimensionId(getString(object, "dimension", "lotr:middle_earth"));
        double x = object.get("x").getAsDouble();
        double y = object.get("y").getAsDouble();
        double z = object.get("z").getAsDouble();
        double compassFadeInRadius = getDouble(object, "compassFadeInRadius", getDouble(object, "fadeInRadius", 128.0D));
        double discoveryRadius = Math.max(1.0D, getDouble(object, "discoveryRadius", getDouble(object, "unlockRadius", 8.0D)));
        boolean hiddenUntilDiscovered = getBoolean(object, "hiddenUntilDiscovered", getBoolean(object, "requiresDiscovery", false));
        boolean discoverable = getBoolean(object, "isDiscoverable", true);
        if (getBoolean(object, "discoveredByDefault", false)) {
            hiddenUntilDiscovered = false;
            discoverable = false;
        }

        return new LostTalesMapMarkerData(id, name, icon, color, category, description, hasFastTravel, fastTravelWaypointCode, dimensionId, x, y, z, compassFadeInRadius, discoveryRadius, hiddenUntilDiscovered, discoverable);
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

    private static double getDouble(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            return element.getAsDouble();
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

    private static int parseDimensionId(String dimensionName) {
        return LostTalesDimensionHelper.parseDimensionId(dimensionName, LOTRDimension.MIDDLE_EARTH.dimensionID);
    }

}
