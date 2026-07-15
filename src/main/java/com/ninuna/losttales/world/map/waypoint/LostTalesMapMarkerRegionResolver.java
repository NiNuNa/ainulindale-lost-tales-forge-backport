package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import lotr.common.LOTRDimension;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRWaypoint;

/** Resolves the normal LOTR biome/faction fast-travel region at a marker. */
public final class LostTalesMapMarkerRegionResolver {
    private LostTalesMapMarkerRegionResolver() {}

    public static LOTRWaypoint.Region resolve(
            LostTalesMapMarkerDefinition marker) {
        if (marker == null) {
            return null;
        }
        return resolve(marker.getDimensionId(), marker.getX(), marker.getZ(),
                marker.getFastTravelWaypointCode());
    }

    public static LOTRWaypoint.Region resolve(
            int dimensionId, double worldX, double worldZ,
            String fastTravelWaypointCode) {
        if (dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return null;
        }

        LOTRBiome biome = null;
        try {
            biome = LOTRGenLayerWorld.getBiomeOrOcean(
                    LOTRWaypoint.worldToMapX(worldX),
                    LOTRWaypoint.worldToMapZ(worldZ));
        } catch (RuntimeException ignored) {
            // Dedicated bootstrap and headless tests can reach this helper
            // before LOTR's biome layers exist. Fall back to waypoint region.
        }
        if (biome != null && biome.getBiomeWaypoints() != null) {
            return biome.getBiomeWaypoints();
        }

        LOTRWaypoint waypoint = fastTravelWaypointCode == null
                || fastTravelWaypointCode.length() == 0
                ? null : LOTRWaypoint.waypointForName(
                        fastTravelWaypointCode);
        if (waypoint != null) {
            for (LOTRWaypoint.Region region : LOTRWaypoint.Region.values()) {
                if (region != null && region.waypoints != null
                        && region.waypoints.contains(waypoint)) {
                    return region;
                }
            }
        }
        return null;
    }
}
