package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerRegionResolver;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
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
                || marker.isHiddenUntilDiscovered()) {
            return false;
        }
        return isRegionRequirementMet(marker);
    }

    /**
     * Full-map visibility policy. When discovery is disabled, the hidden flag
     * is meaningless and the marker follows only its normal LOTR region.
     */
    public static boolean isMapVisible(LostTalesMapMarkerData marker) {
        if (marker == null) {
            return false;
        }
        if (!isRegionRequirementMet(marker)) {
            return false;
        }
        if (!marker.isDiscoverable()) {
            return true;
        }
        if (isDiscovered(marker)) {
            return true;
        }
        return !marker.isHiddenUntilDiscovered();
    }

    public static boolean isNonDiscoverableVisible(
            LostTalesMapMarkerData marker) {
        return marker != null && !marker.isDiscoverable()
                && isRegionRequirementMet(marker);
    }

    /** Whether the independent LOTR biome/faction-region gate is satisfied. */
    public static boolean isRegionRequirementMet(
            LostTalesMapMarkerData marker) {
        return marker != null && (!marker.requiresRegionUnlock()
                || isLocationRegionUnlocked(marker));
    }

    private static boolean isLocationRegionUnlocked(
            LostTalesMapMarkerData marker) {
        if (marker == null || marker.getDimensionId()
                != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return false;
        }
        try {
            LOTRWaypoint.Region region =
                    LostTalesMapMarkerRegionResolver.resolve(
                            minecraft.theWorld,
                            marker.getDimensionId(), marker.getX(),
                            marker.getZ(),
                            marker.getFastTravelWaypointCode());
            LOTRPlayerData data = LOTRLevelData.getData(minecraft.thePlayer);
            return region != null && data != null
                    && data.isFTRegionUnlocked(region);
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Compass visibility is deliberately less revealing than map visibility
     * in one direction and more useful in another: a hidden marker remains
     * absent from the full map, but its nearby compass hint may still guide
     * the player to an anonymous discovery. Ordinary waypoint question marks
     * continue to require their LOTR region to have been unlocked.
     */
    public static boolean isUndiscoveredCompassVisible(
            LostTalesMapMarkerData marker) {
        if (marker == null || !marker.isDiscoverable()
                || isDiscovered(marker)) {
            return false;
        }
        return isRegionRequirementMet(marker);
    }

}
