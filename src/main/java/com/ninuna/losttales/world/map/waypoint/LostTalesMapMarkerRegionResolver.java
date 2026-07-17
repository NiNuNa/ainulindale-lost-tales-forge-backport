package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import lotr.common.LOTRDimension;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;

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

    /**
     * Runtime resolver that uses the actual world biome when available. This
     * matches the region LOTR unlocks while the player explores and avoids
     * early-startup biome-image timing differences.
     */
    public static LOTRWaypoint.Region resolve(
            World world, LostTalesMapMarkerDefinition marker) {
        if (marker == null) {
            return null;
        }
        return resolve(world, marker.getDimensionId(), marker.getX(),
                marker.getZ(), marker.getFastTravelWaypointCode());
    }

    public static LOTRWaypoint.Region resolve(
            World world, int dimensionId, double worldX, double worldZ,
            String fastTravelWaypointCode) {
        LOTRWaypoint.Region waypointRegion = resolveWaypointRegion(
                fastTravelWaypointCode);
        if (waypointRegion != null) {
            return waypointRegion;
        }
        if (world != null && world.provider != null
                && world.provider.dimensionId == dimensionId
                && dimensionId
                == LOTRDimension.MIDDLE_EARTH.dimensionID) {
            try {
                BiomeGenBase biome = world.getBiomeGenForCoords(
                        MathHelper.floor_double(worldX),
                        MathHelper.floor_double(worldZ));
                if (biome instanceof LOTRBiome) {
                    LOTRWaypoint.Region region =
                            ((LOTRBiome)biome).getBiomeWaypoints();
                    if (region != null) {
                        return region;
                    }
                }
            } catch (RuntimeException ignored) {
                // Fall through to the static Middle-earth map resolver.
            }
        }
        return resolve(dimensionId, worldX, worldZ,
                fastTravelWaypointCode);
    }

    public static LOTRWaypoint.Region resolve(
            int dimensionId, double worldX, double worldZ,
            String fastTravelWaypointCode) {
        if (dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return null;
        }

        LOTRWaypoint.Region waypointRegion = resolveWaypointRegion(
                fastTravelWaypointCode);
        if (waypointRegion != null) {
            return waypointRegion;
        }

        LOTRBiome biome = null;
        try {
            if (LOTRGenLayerWorld.loadedBiomeImage()) {
                biome = LOTRGenLayerWorld.getBiomeOrOcean(
                        LOTRWaypoint.worldToMapX(worldX),
                        LOTRWaypoint.worldToMapZ(worldZ));
            }
        } catch (RuntimeException ignored) {
            // Dedicated bootstrap and headless tests can reach this helper
            // before LOTR's biome layers exist. Fall back to waypoint region.
        }
        if (biome != null && biome.getBiomeWaypoints() != null) {
            return biome.getBiomeWaypoints();
        }

        return null;
    }

    private static LOTRWaypoint.Region resolveWaypointRegion(
            String fastTravelWaypointCode) {
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
