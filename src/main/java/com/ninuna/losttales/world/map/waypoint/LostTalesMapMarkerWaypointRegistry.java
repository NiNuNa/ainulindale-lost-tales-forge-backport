package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import com.ninuna.losttales.util.LostTalesUtil;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.registry.LanguageRegistry;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRWaypoint;

/**
 * Registers Lost Tales JSON map markers as public LOTR waypoints.
 *
 * <p>These are not LOTRCustomWaypoint instances. They are normal global/public
 * LOTRWaypoint enum entries. Existing LOTR and Lost Tales waypoints are bound
 * to the same marker index; only newly generated discoverable waypoints need
 * a private fast-travel region. Map visibility follows the waypoint region
 * that LOTR unlocks when its associated biome is visited.</p>
 */
public final class LostTalesMapMarkerWaypointRegistry {
    private static final String LOTR_WAYPOINT_MARKER_PREFIX = "lotr:waypoint:";
    private static final Map<String, LOTRWaypoint.Region> REGIONS_BY_MARKER_ID = new LinkedHashMap<String, LOTRWaypoint.Region>();
    private static final Map<String, LOTRWaypoint> WAYPOINTS_BY_MARKER_ID = new LinkedHashMap<String, LOTRWaypoint>();
    private static final Set<String> PRIVATE_REGION_MARKER_IDS =
            new LinkedHashSet<String>();
    private static boolean registered;

    private LostTalesMapMarkerWaypointRegistry() {}

    public static synchronized void initAndRegisterWaypoints() {
        if (registered) {
            return;
        }

        LostTalesMapMarkerCatalog.ensureLoaded();
        for (LostTalesMapMarkerDefinition marker : LostTalesMapMarkerCatalog.getMarkers()) {
            registerMarkerWaypoint(marker);
        }
        registered = true;
    }

    public static synchronized LOTRWaypoint.Region getRegionForMarker(String markerId) {
        initAndRegisterWaypoints();
        return REGIONS_BY_MARKER_ID.get(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    public static synchronized LOTRWaypoint getWaypointForMarker(String markerId) {
        initAndRegisterWaypoints();
        return WAYPOINTS_BY_MARKER_ID.get(LostTalesQuestMarkerHelper.normalizeMarkerId(markerId));
    }

    public static synchronized Map<String, LOTRWaypoint> getRegisteredWaypoints() {
        initAndRegisterWaypoints();
        return Collections.unmodifiableMap(new LinkedHashMap<String, LOTRWaypoint>(WAYPOINTS_BY_MARKER_ID));
    }

    /** Resolves the bundled Lost Tales definition that owns a LOTR waypoint. */
    public static synchronized LostTalesMapMarkerDefinition getMarkerForWaypoint(
            LOTRAbstractWaypoint waypoint) {
        if (waypoint == null) {
            return null;
        }
        initAndRegisterWaypoints();
        for (Map.Entry<String, LOTRWaypoint> entry
                : WAYPOINTS_BY_MARKER_ID.entrySet()) {
            if (entry.getValue() == waypoint) {
                return LostTalesMapMarkerCatalog.getMarker(entry.getKey());
            }
        }

        String code = normalizeWaypointKey(waypoint.getCodeName());
        for (LostTalesMapMarkerDefinition marker
                : LostTalesMapMarkerCatalog.getMarkers()) {
            if (marker == null || !marker.hasFastTravel()) {
                continue;
            }
            String markerCode = normalizeWaypointKey(
                    marker.getFastTravelWaypointCode());
            if (markerCode.length() > 0 && markerCode.equals(code)) {
                return marker;
            }
            if (markerCode.length() == 0
                    && Math.abs(marker.getX() - waypoint.getXCoord()) <= 0.5D
                    && Math.abs(marker.getZ() - waypoint.getZCoord()) <= 0.5D) {
                return marker;
            }
        }
        return null;
    }

    public static boolean isExistingLotrWaypointMarker(LostTalesMapMarkerDefinition marker) {
        if (marker == null) {
            return false;
        }
        String code = marker.getFastTravelWaypointCode();
        if (code != null && code.length() > 0) {
            return true;
        }
        String id = marker.getId();
        return id != null && id.startsWith(LOTR_WAYPOINT_MARKER_PREFIX);
    }

    private static void registerMarkerWaypoint(LostTalesMapMarkerDefinition marker) {
        if (marker == null || !marker.hasFastTravel()) {
            return;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return;
        }
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(marker.getId());
        if (markerId.length() == 0 || WAYPOINTS_BY_MARKER_ID.containsKey(markerId)) {
            return;
        }

        if (isExistingLotrWaypointMarker(marker)) {
            bindExistingWaypoint(markerId, marker);
            return;
        }

        String enumBase = toEnumName(markerId);
        try {
            registerWaypointLocalization(enumBase, marker);
            // Discoverable markers need a private region that unlocks only
            // after proximity discovery. Non-discoverable markers instead
            // inherit their normal biome/faction region, so the standard LOTR
            // visited-region rule controls visibility and fast travel.
            LOTRWaypoint.Region region =
                    resolveInheritedRegion(marker);
            if (region == null) {
                region = LostTalesUtil.addWaypointRegion(
                        enumBase + "_REGION");
                PRIVATE_REGION_MARKER_IDS.add(markerId);
                if (!marker.isDiscoverable()
                        && marker.requiresRegionUnlock()) {
                    FMLLog.warning("[%s] Non-discoverable marker %s has no LOTR biome/faction region; it will remain locked", LostTalesMetaData.MOD_ID, markerId);
                }
            }
            LOTRWaypoint waypoint = LostTalesUtil.addWaypoint(
                    enumBase,
                    region,
                    LOTRFaction.UNALIGNED,
                    LostTalesMapCoordinateHelper
                            .worldToMapImageX(marker.getX()),
                    LostTalesMapCoordinateHelper
                            .worldToMapImageZ(marker.getZ()),
                    shouldHideGeneratedWaypoint(marker)
            );
            REGIONS_BY_MARKER_ID.put(markerId, region);
            WAYPOINTS_BY_MARKER_ID.put(markerId, waypoint);
        } catch (RuntimeException e) {
            FMLLog.warning("[%s] Failed to register public LOTR waypoint for map marker %s: %s", LostTalesMetaData.MOD_ID, markerId, e.getMessage());
        }
    }

    private static void bindExistingWaypoint(
            String markerId, LostTalesMapMarkerDefinition marker) {
        String code = marker.getFastTravelWaypointCode();
        LOTRWaypoint waypoint = resolveExistingWaypoint(marker);
        if (waypoint == null) {
            FMLLog.warning("[%s] Map marker %s references missing LOTR waypoint %s",
                    LostTalesMetaData.MOD_ID, markerId, code);
            return;
        }
        WAYPOINTS_BY_MARKER_ID.put(markerId, waypoint);
        LOTRWaypoint.Region region = LostTalesMapMarkerRegionResolver
                .resolveWaypointRegion(waypoint);
        if (region != null) {
            REGIONS_BY_MARKER_ID.put(markerId, region);
        } else {
            FMLLog.warning("[%s] LOTR waypoint %s for map marker %s has no region",
                    LostTalesMetaData.MOD_ID, code, markerId);
        }
    }

    static LOTRWaypoint resolveExistingWaypoint(
            LostTalesMapMarkerDefinition marker) {
        if (marker == null) {
            return null;
        }
        String code = marker.getFastTravelWaypointCode();
        return code == null || code.length() == 0
                ? null : LOTRWaypoint.waypointForName(code);
    }

    /** Package-visible for policy regression tests without mutating the enum. */
    static LOTRWaypoint.Region resolveInheritedRegion(
            LostTalesMapMarkerDefinition marker) {
        return marker != null && !marker.isDiscoverable()
                && marker.requiresRegionUnlock()
                ? LostTalesMapMarkerRegionResolver.resolve(marker) : null;
    }

    static boolean usesPrivateRegion(LostTalesMapMarkerDefinition marker) {
        if (marker == null || !marker.hasFastTravel()) {
            return false;
        }
        if (isExistingLotrWaypointMarker(marker)) {
            return !marker.requiresRegionUnlock();
        }
        initAndRegisterWaypoints();
        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(
                marker.getId());
        if (REGIONS_BY_MARKER_ID.containsKey(markerId)) {
            return PRIVATE_REGION_MARKER_IDS.contains(markerId);
        }
        // Synthetic/dynamic markers are not present in the bundled registry.
        // Preserve their private-region policy without touching ordinary
        // native LOTR waypoints, which returned above.
        return marker.isDiscoverable()
                || !marker.requiresRegionUnlock();
    }

    static boolean shouldHideGeneratedWaypoint(
            LostTalesMapMarkerDefinition marker) {
        return marker != null && marker.isDiscoverable();
    }

    private static void registerWaypointLocalization(String enumBase, LostTalesMapMarkerDefinition marker) {
        if (enumBase == null || enumBase.length() == 0 || marker == null) {
            return;
        }
        String displayName = marker.getName() == null || marker.getName().length() == 0 ? enumBase : marker.getName();
        String description = marker.getDescription() == null || marker.getDescription().length() == 0
                ? "A discovered location in Middle-earth."
                : marker.getDescription();
        LanguageRegistry.instance().addStringLocalization("lotr.waypoint." + enumBase, "en_US", displayName);
        LanguageRegistry.instance().addStringLocalization("lotr.waypoint." + enumBase + ".info", "en_US", description);
    }

    private static String toEnumName(String markerId) {
        String normalized = markerId == null ? "" : markerId.trim().toUpperCase(Locale.ROOT);
        int namespaceIndex = normalized.indexOf(':');
        if (namespaceIndex >= 0 && namespaceIndex < normalized.length() - 1) {
            normalized = normalized.substring(namespaceIndex + 1);
        }
        StringBuilder builder = new StringBuilder("LOSTTALES_");
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c >= 'A' && c <= 'Z' || c >= '0' && c <= '9') {
                builder.append(c);
            } else {
                builder.append('_');
            }
        }
        while (builder.indexOf("__") >= 0) {
            int index = builder.indexOf("__");
            builder.deleteCharAt(index);
        }
        if (builder.length() <= "LOSTTALES_".length()) {
            builder.append("MARKER");
        }
        return builder.toString();
    }

    private static String normalizeWaypointKey(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
