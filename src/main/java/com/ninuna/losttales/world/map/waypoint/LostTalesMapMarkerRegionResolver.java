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
                marker.getLotrWaypointId());
    }

    /** Resolves the same region that owns the marker's LOTR waypoint. */
    public static LOTRWaypoint.Region resolve(
            World world, LostTalesMapMarkerDefinition marker) {
        if (marker == null) {
            return null;
        }
        return resolve(world, marker.getDimensionId(), marker.getX(),
                marker.getZ(), marker.getLotrWaypointId());
    }

    public static LOTRWaypoint.Region resolve(
            World world, int dimensionId, double worldX, double worldZ,
            String lotrWaypointId) {
        LOTRWaypoint.Region waypointRegion = resolveWaypointRegion(
                lotrWaypointId);
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
                lotrWaypointId);
    }

    public static LOTRWaypoint.Region resolve(
            int dimensionId, double worldX, double worldZ,
            String lotrWaypointId) {
        if (dimensionId != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return null;
        }

        LOTRWaypoint.Region waypointRegion = resolveWaypointRegion(
                lotrWaypointId);
        if (waypointRegion != null) {
            return waypointRegion;
        }

        try {
            if (LOTRGenLayerWorld.loadedBiomeImage()) {
                LOTRBiome biome = LOTRGenLayerWorld.getBiomeOrOcean(
                        LOTRWaypoint.worldToMapX(worldX),
                        LOTRWaypoint.worldToMapZ(worldZ));
                return biome == null ? null : biome.getBiomeWaypoints();
            }
        } catch (RuntimeException ignored) {
            // Dedicated bootstrap and headless tests can reach this helper
            // before LOTR's biome layers exist.
        }
        return null;
    }

    static LOTRWaypoint.Region resolveWaypointRegion(
            String lotrWaypointId) {
        LOTRWaypoint waypoint = lotrWaypointId == null
                || lotrWaypointId.length() == 0
                ? null : LOTRWaypoint.waypointForName(
                        lotrWaypointId);
        return resolveWaypointRegion(waypoint);
    }

    static LOTRWaypoint.Region resolveWaypointRegion(
            LOTRWaypoint waypoint) {
        if (waypoint == null) {
            return null;
        }
        for (LOTRWaypoint.Region region : LOTRWaypoint.Region.values()) {
            if (region != null && region.waypoints != null
                    && region.waypoints.contains(waypoint)) {
                return region;
            }
        }
        return null;
    }
}
