package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private static volatile List<LostTalesMapMarkerData> decorativeMarkers =
            createFallbackMarkers();
    private static volatile LostTalesClientMapMarkerIndex.Snapshot
            decorativeSnapshot =
                    LostTalesClientMapMarkerIndex
                            .createDecorativeSnapshot(decorativeMarkers);
    private static final LostTalesClientMapMarkerIndex INDEX =
            new LostTalesClientMapMarkerIndex();

    static {
        INDEX.replaceWorldMarkers(decorativeMarkers);
    }

    private LostTalesClientMapMarkerStore() {}

    public static List<LostTalesMapMarkerData> getSharedMarkers() {
        return getAllMarkers();
    }

    public static List<LostTalesMapMarkerData> getAllMarkers() {
        return INDEX.getPersistentSnapshot().getAllMarkers();
    }

    /** Map-only merge; party markers never enter compass or world-HUD lists. */
    public static List<LostTalesMapMarkerData> getMapMarkers(
            Collection<LostTalesMapMarkerData> partyMarkers) {
        return INDEX.getMapSnapshot(partyMarkers).getAllMarkers();
    }

    public static List<LostTalesMapMarkerData> getDecorativeMarkers() {
        return decorativeMarkers;
    }

    public static Set<String> getSharedMarkerIds() {
        return INDEX.getPersistentSnapshot().getMarkerIds();
    }

    public static LostTalesMapMarkerData getSharedMarker(String markerId) {
        if (markerId == null || markerId.length() == 0) {
            return null;
        }
        return INDEX.getPersistentSnapshot().findById(markerId);
    }

    static Object getSnapshotIdentity() {
        return INDEX.getPersistentSnapshot();
    }

    static LostTalesMapMarkerData findMappedWaypointMarker(
            String waypointCode, String waypointDisplay,
            int worldX, int worldZ) {
        return findMappedWaypointMarker(
                INDEX.getPersistentSnapshot(),
                waypointCode, waypointDisplay,
                worldX, worldZ);
    }

    static boolean hasDecorativeWaypointMapping(
            String waypointCode, String waypointDisplay,
            int worldX, int worldZ) {
        return findMappedWaypointMarker(
                decorativeSnapshot, waypointCode, waypointDisplay,
                worldX, worldZ) != null;
    }

    private static LostTalesMapMarkerData findMappedWaypointMarker(
            LostTalesClientMapMarkerIndex.Snapshot snapshot,
            String waypointCode, String waypointDisplay,
            int worldX, int worldZ) {
        return snapshot == null ? null
                : snapshot.findMappedWaypointMarker(
                        waypointCode, waypointDisplay,
                        worldX, worldZ);
    }

    public static boolean hasSharedMarker(String markerId) {
        return getSharedMarker(markerId) != null;
    }

    public static synchronized void reloadFromResources(IResourceManager resourceManager) {
        List<LostTalesMapMarkerData> loaded = LostTalesMapMarkerResourceLoader.loadSharedMarkers(resourceManager);
        if (loaded.isEmpty()) {
            loaded = createFallbackMarkers();
        }
        decorativeMarkers = Collections.unmodifiableList(
                new ArrayList<LostTalesMapMarkerData>(loaded));
        decorativeSnapshot = LostTalesClientMapMarkerIndex
                .createDecorativeSnapshot(decorativeMarkers);
        INDEX.replaceWorldMarkers(decorativeMarkers);
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
        INDEX.replaceQuestMarkers(byId.values());
    }

    public static synchronized void setServerMarkers(
            Collection<LostTalesMapMarkerDefinition> markers) {
        Map<String, LostTalesMapMarkerData> byId =
                new LinkedHashMap<String, LostTalesMapMarkerData>();
        if (markers != null) {
            for (LostTalesMapMarkerDefinition marker : markers) {
                LostTalesMapMarkerData data = toClientMarker(marker);
                if (data != null) {
                    byId.put(data.getId(), data);
                }
            }
        }
        INDEX.replaceWorldMarkers(byId.values());
    }

    public static synchronized void clearDynamicMarkers() {
        INDEX.replacePersistentMarkers(
                decorativeMarkers,
                Collections.<LostTalesMapMarkerData>emptyList());
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
                marker.getDimensionId(),
                marker.getX(),
                marker.getY(),
                marker.getZ(),
                marker.getCompassFadeInRadius(),
                marker.getDiscoveryRadius(),
                marker.isHiddenUntilDiscovered(),
                marker.isDiscoverable(),
                marker.requiresRegionUnlock(),
                marker.hasWaystone(),
                marker.getPriority(),
                marker.getSource()
        );
    }

    private static List<LostTalesMapMarkerData> createFallbackMarkers() {
        List<LostTalesMapMarkerData> markers = new ArrayList<LostTalesMapMarkerData>();
        int middleEarth = LOTRDimension.MIDDLE_EARTH.dimensionID;
        markers.add(new LostTalesMapMarkerData("fallback-town", "Town", LostTalesCompassMarkerIcon.TOWN.name(), "red", middleEarth, 15.0D, LostTalesMapMarkerDefinition.AUTOMATIC_Y, 15.0D, 160.0D, 10.0D));
        markers.add(new LostTalesMapMarkerData("fallback-cheese-fort", "Cheese's Fort", LostTalesCompassMarkerIcon.TOWN.name(), "white", middleEarth, -180.0D, LostTalesMapMarkerDefinition.AUTOMATIC_Y, -140.0D, 250.0D, 10.0D));
        return Collections.unmodifiableList(markers);
    }
}
