package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lotr.common.LOTRDimension;
import net.minecraft.client.resources.IResourceManager;

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
    private static volatile MarkerSnapshot markerSnapshot = MarkerSnapshot.create(sharedMarkers, dynamicMarkers);

    private LostTalesClientMapMarkerStore() {}

    public static List<LostTalesMapMarkerData> getSharedMarkers() {
        return getAllMarkers();
    }

    public static List<LostTalesMapMarkerData> getAllMarkers() {
        return markerSnapshot.allMarkers;
    }

    public static Set<String> getSharedMarkerIds() {
        return markerSnapshot.markerIds;
    }

    public static LostTalesMapMarkerData getSharedMarker(String markerId) {
        if (markerId == null || markerId.length() == 0) {
            return null;
        }
        return markerSnapshot.markersById.get(markerId);
    }

    public static boolean hasSharedMarker(String markerId) {
        return getSharedMarker(markerId) != null;
    }

    public static synchronized void reloadFromResources(IResourceManager resourceManager) {
        List<LostTalesMapMarkerData> loaded = LostTalesMapMarkerResourceLoader.loadSharedMarkers(resourceManager);
        if (loaded.isEmpty()) {
            loaded = createFallbackMarkers();
        }
        sharedMarkers = Collections.unmodifiableList(new ArrayList<LostTalesMapMarkerData>(loaded));
        rebuildSnapshot();
    }

    public static synchronized void setDynamicMarkers(Collection<LostTalesMapMarkerDefinition> markers) {
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
        rebuildSnapshot();
    }

    public static synchronized void clearDynamicMarkers() {
        dynamicMarkers = Collections.emptyList();
        rebuildSnapshot();
    }

    private static void rebuildSnapshot() {
        markerSnapshot = MarkerSnapshot.create(sharedMarkers, dynamicMarkers);
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
                marker.getCategoryName(),
                marker.getDescription(),
                marker.hasFastTravel(),
                marker.getFastTravelWaypointCode(),
                marker.getDimensionId(),
                marker.getX(),
                marker.getY(),
                marker.getZ(),
                marker.getCompassFadeInRadius(),
                marker.getDiscoveryRadius(),
                marker.isHiddenUntilDiscovered(),
                marker.isDiscoverable()
        );
    }

    private static final class MarkerSnapshot {
        private final List<LostTalesMapMarkerData> allMarkers;
        private final Set<String> markerIds;
        private final Map<String, LostTalesMapMarkerData> markersById;

        private MarkerSnapshot(List<LostTalesMapMarkerData> allMarkers, Set<String> markerIds,
                               Map<String, LostTalesMapMarkerData> markersById) {
            this.allMarkers = allMarkers;
            this.markerIds = markerIds;
            this.markersById = markersById;
        }

        private static MarkerSnapshot create(List<LostTalesMapMarkerData> shared,
                                             List<LostTalesMapMarkerData> dynamic) {
            int sharedSize = shared == null ? 0 : shared.size();
            int dynamicSize = dynamic == null ? 0 : dynamic.size();
            List<LostTalesMapMarkerData> combined = new ArrayList<LostTalesMapMarkerData>(sharedSize + dynamicSize);
            Set<String> ids = new LinkedHashSet<String>();
            Map<String, LostTalesMapMarkerData> byId = new LinkedHashMap<String, LostTalesMapMarkerData>();

            addMarkers(shared, combined, ids, byId, false);
            addMarkers(dynamic, combined, ids, byId, true);

            return new MarkerSnapshot(
                    Collections.unmodifiableList(combined),
                    Collections.unmodifiableSet(ids),
                    Collections.unmodifiableMap(byId)
            );
        }

        private static void addMarkers(List<LostTalesMapMarkerData> source,
                                       List<LostTalesMapMarkerData> combined,
                                       Set<String> ids,
                                       Map<String, LostTalesMapMarkerData> byId,
                                       boolean replaceExisting) {
            if (source == null || source.isEmpty()) {
                return;
            }
            for (LostTalesMapMarkerData marker : source) {
                combined.add(marker);
                if (marker == null || marker.getId() == null || marker.getId().length() == 0) {
                    continue;
                }
                String markerId = marker.getId();
                ids.add(markerId);
                if (replaceExisting || !byId.containsKey(markerId)) {
                    byId.put(markerId, marker);
                }
            }
        }
    }

    private static List<LostTalesMapMarkerData> createFallbackMarkers() {
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        int middleEarth = LOTRDimension.MIDDLE_EARTH.dimensionID;
        markers.add(new LostTalesMapMarkerData("fallback-town", "Town", LostTalesCompassMarkerIcon.FORT.name(), "red", middleEarth, 15.0D, 64.0D, 15.0D, 160.0D, 10.0D));
        markers.add(new LostTalesMapMarkerData("fallback-cheese-fort", "Cheese's Fort", LostTalesCompassMarkerIcon.FORT.name(), "white", middleEarth, -180.0D, 64.0D, -140.0D, 250.0D, 10.0D));
        return Collections.unmodifiableList(markers);
    }
}
