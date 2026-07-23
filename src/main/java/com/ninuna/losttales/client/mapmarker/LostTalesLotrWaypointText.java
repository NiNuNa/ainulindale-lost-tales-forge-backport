package com.ninuna.losttales.client.mapmarker;

import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Resolves display-only lore for markers mapped to native LOTR waypoints.
 *
 * <p>The native waypoint remains the source of its translated description
 * until the Lost Tales marker is given an explicit description.</p>
 */
public final class LostTalesLotrWaypointText {
    private LostTalesLotrWaypointText() {}

    public static String resolveDescription(
            LostTalesMapMarkerData marker, EntityPlayer player) {
        if (marker == null) {
            return "";
        }
        return resolveDescription(
                marker.getDescription(), marker.getLotrWaypointId(), player);
    }

    public static String resolveDescription(
            String configuredDescription, String lotrWaypointId,
            EntityPlayer player) {
        String configured = trim(configuredDescription);
        if (configured.length() > 0) {
            return configured;
        }
        String waypointId = trim(lotrWaypointId);
        if (waypointId.length() == 0) {
            return configured;
        }
        try {
            LOTRWaypoint waypoint = LOTRWaypoint.waypointForName(
                    waypointId);
            String nativeLore = waypoint == null
                    ? "" : trim(waypoint.getLoreText(player));
            return nativeLore.length() == 0 ? configured : nativeLore;
        } catch (RuntimeException ignored) {
            return configured;
        }
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
