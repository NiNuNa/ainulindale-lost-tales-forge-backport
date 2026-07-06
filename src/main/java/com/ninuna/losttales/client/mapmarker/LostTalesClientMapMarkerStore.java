package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import net.minecraft.client.resources.IResourceManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client cache for shared/static and server-synced map markers.
 *
 * Bundled JSON markers are loaded from resources. Quest-giver markers are not
 * hard-coded in JSON; the server sends them after the player discovers a real
 * quest giver in-world.
 */
public final class LostTalesClientMapMarkerStore {
    private static volatile List<LostTalesMapMarkerData> sharedMarkers = createFallbackMarkers();
    private static volatile List<LostTalesMapMarkerData> dynamicMarkers = Collections.emptyList();

    private LostTalesClientMapMarkerStore() {}

    public static List<LostTalesMapMarkerData> getSharedMarkers() {
        return getAllMarkers();
    }

    public static List<LostTalesMapMarkerData> getAllMarkers() {
        ArrayList<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        markers.addAll(sharedMarkers);
        markers.addAll(dynamicMarkers);
        return Collections.unmodifiableList(markers);
    }

    public static Set<String> getSharedMarkerIds() {
        Set<String> ids = new LinkedHashSet<String>();
        for (LostTalesMapMarkerData marker : getAllMarkers()) {
            if (marker != null && marker.getId() != null && marker.getId().length() > 0) {
                ids.add(marker.getId());
            }
        }
        return Collections.unmodifiableSet(ids);
    }

    public static LostTalesMapMarkerData getSharedMarker(String markerId) {
        if (markerId == null || markerId.length() == 0) {
            return null;
        }
        // Player/server markers should win over bundled JSON if an ID overlaps.
        for (LostTalesMapMarkerData marker : dynamicMarkers) {
            if (marker != null && markerId.equals(marker.getId())) {
                return marker;
            }
        }
        for (LostTalesMapMarkerData marker : sharedMarkers) {
            if (marker != null && markerId.equals(marker.getId())) {
                return marker;
            }
        }
        return null;
    }

    public static boolean hasSharedMarker(String markerId) {
        return getSharedMarker(markerId) != null;
    }

    public static void reloadFromResources(IResourceManager resourceManager) {
        List<LostTalesMapMarkerData> loaded = LostTalesMapMarkerResourceLoader.loadSharedMarkers(resourceManager);
        if (loaded.isEmpty()) {
            loaded = createFallbackMarkers();
        }
        sharedMarkers = Collections.unmodifiableList(new ArrayList<LostTalesMapMarkerData>(loaded));
    }

    public static void setDynamicMarkers(Collection<LostTalesMapMarkerDefinition> markers) {
        Map<String, LostTalesMapMarkerData> byId = new LinkedHashMap<String, LostTalesMapMarkerData>();
        if (markers != null) {
            for (LostTalesMapMarkerDefinition marker : markers) {
                LostTalesMapMarkerData data = toClientMarker(marker);
                if (data != null) {
                    byId.put(data.getId(), data);
                }
            }
        }
        dynamicMarkers = Collections.unmodifiableList(new ArrayList<LostTalesMapMarkerData>(byId.values()));
    }

    public static void clearDynamicMarkers() {
        dynamicMarkers = Collections.emptyList();
    }

    private static LostTalesMapMarkerData toClientMarker(LostTalesMapMarkerDefinition marker) {
        if (marker == null || marker.getId() == null || marker.getId().length() == 0) {
            return null;
        }
        String name = marker.getName() == null || marker.getName().length() == 0 ? marker.getId() : marker.getName();
        String icon = marker.getIconName() == null || marker.getIconName().length() == 0 ? "quest" : marker.getIconName();
        String color = marker.getColorName() == null || marker.getColorName().length() == 0 ? "white" : marker.getColorName();
        return new LostTalesMapMarkerData(
                marker.getId(),
                name,
                icon,
                color,
                "Quest Marker",
                false,
                marker.getDimensionId(),
                marker.getX(),
                marker.getY(),
                marker.getZ(),
                128.0D,
                8.0D,
                marker.isHiddenUntilDiscovered()
        );
    }

    private static List<LostTalesMapMarkerData> createFallbackMarkers() {
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        markers.add(new LostTalesMapMarkerData("fallback-town", "Town", LostTalesCompassMarkerIcon.FORT.name(), "red", 0, 15.0D, 64.0D, 15.0D, 80.0D, 8.0D));
        markers.add(new LostTalesMapMarkerData("fallback-cheese-fort", "Cheese's Fort", LostTalesCompassMarkerIcon.FORT.name(), "white", 0, -14.0D, 64.0D, -23.0D, 250.0D, 8.0D));
        markers.add(new LostTalesMapMarkerData("fallback-nether-hub", "Nether Hub", LostTalesCompassMarkerIcon.FORT.name(), "red", -1, 10.0D, 70.0D, -12.0D, 64.0D, 10.0D));
        return Collections.unmodifiableList(markers);
    }
}
