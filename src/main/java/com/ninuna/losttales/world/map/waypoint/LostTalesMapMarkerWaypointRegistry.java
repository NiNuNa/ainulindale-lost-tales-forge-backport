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
import java.util.Locale;
import java.util.Map;
import lotr.common.LOTRDimension;
import lotr.common.fac.LOTRFaction;
import lotr.common.world.map.LOTRWaypoint;

/**
 * Registers Lost Tales JSON map markers as public LOTR waypoints.
 *
 * <p>These are not LOTRCustomWaypoint instances. They are normal global/public
 * LOTRWaypoint enum entries with one private region per marker. The waypoint is
 * created hidden and becomes visible for a player only after that player
 * discovers the corresponding Lost Tales map marker and we unlock its region.</p>
 */
public final class LostTalesMapMarkerWaypointRegistry {
    private static final String LOTR_WAYPOINT_MARKER_PREFIX = "lotr:waypoint:";
    private static final Map<String, LOTRWaypoint.Region> REGIONS_BY_MARKER_ID = new LinkedHashMap<String, LOTRWaypoint.Region>();
    private static final Map<String, LOTRWaypoint> WAYPOINTS_BY_MARKER_ID = new LinkedHashMap<String, LOTRWaypoint>();
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

    public static boolean isExistingLotrWaypointMarker(LostTalesMapMarkerDefinition marker) {
        if (marker == null) {
            return false;
        }
        String code = marker.getLotrWaypointCode();
        if (code != null && code.length() > 0) {
            return true;
        }
        String id = marker.getId();
        return id != null && id.startsWith(LOTR_WAYPOINT_MARKER_PREFIX);
    }

    private static void registerMarkerWaypoint(LostTalesMapMarkerDefinition marker) {
        if (marker == null || !marker.isWaypoint()) {
            return;
        }
        if (marker.getDimensionId() != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return;
        }
        if (isExistingLotrWaypointMarker(marker)) {
            return;
        }

        String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(marker.getId());
        if (markerId.length() == 0 || WAYPOINTS_BY_MARKER_ID.containsKey(markerId)) {
            return;
        }

        String enumBase = toEnumName(markerId);
        try {
            registerWaypointLocalization(enumBase, marker);
            LOTRWaypoint.Region region = LostTalesUtil.addWaypointRegion(enumBase + "_REGION");
            LOTRWaypoint waypoint = LostTalesUtil.addWaypoint(
                    enumBase,
                    region,
                    LOTRFaction.UNALIGNED,
                    LOTRWaypoint.worldToMapX(marker.getX()),
                    LOTRWaypoint.worldToMapZ(marker.getZ()),
                    true
            );
            REGIONS_BY_MARKER_ID.put(markerId, region);
            WAYPOINTS_BY_MARKER_ID.put(markerId, waypoint);
        } catch (RuntimeException e) {
            FMLLog.warning("[%s] Failed to register public LOTR waypoint for map marker %s: %s", LostTalesMetaData.MOD_ID, markerId, e.getMessage());
        }
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
}
