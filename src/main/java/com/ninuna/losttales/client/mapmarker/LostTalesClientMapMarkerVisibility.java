package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.biome.LOTRBiome;
import lotr.common.world.genlayer.LOTRGenLayerWorld;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;

/** Shared client rule for discovered, regional-question-mark, and hidden states. */
public final class LostTalesClientMapMarkerVisibility {

    private LostTalesClientMapMarkerVisibility() {}

    public static boolean isDiscovered(LostTalesMapMarkerData marker) {
        return marker != null
                && LostTalesClientQuestProgressStore
                .isMarkerDiscovered(marker.getId());
    }

    public static boolean isUndiscoveredRegionVisible(
            LostTalesMapMarkerData marker) {
        if (marker == null || !marker.isDiscoverable()
                || isDiscovered(marker)
                || marker.isHiddenUntilDiscovered()
                || marker.getDimensionId()
                != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return false;
        }
        try {
            LOTRWaypoint.Region region = resolveVisibilityRegion(marker);
            LOTRPlayerData data = LOTRLevelData.getData(minecraft.thePlayer);
            return region != null && data != null
                    && data.isFTRegionUnlocked(region);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static LOTRWaypoint.Region resolveVisibilityRegion(
            LostTalesMapMarkerData marker) {
        LOTRBiome biome = LOTRGenLayerWorld.getBiomeOrOcean(
                LOTRWaypoint.worldToMapX(marker.getX()),
                LOTRWaypoint.worldToMapZ(marker.getZ()));
        if (biome != null && biome.getBiomeWaypoints() != null) {
            return biome.getBiomeWaypoints();
        }
        String waypointCode = marker.getFastTravelWaypointCode();
        LOTRWaypoint waypoint = waypointCode == null
                || waypointCode.length() == 0
                ? null : LOTRWaypoint.waypointForName(waypointCode);
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
