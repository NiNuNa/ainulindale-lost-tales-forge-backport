package com.ninuna.losttales.client.mapmarker;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerRegionResolver;
import java.util.HashMap;
import java.util.Map;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.client.Minecraft;

/** Shared client rule for discovered, regional-question-mark, and hidden states. */
public final class LostTalesClientMapMarkerVisibility {
    private static final Map<String, Boolean> REGION_UNLOCK_CACHE =
            new HashMap<String, Boolean>();
    private static Object regionCacheWorld;
    private static Object regionCachePlayer;
    private static Object regionCacheSnapshot;
    private static long regionCacheTick = Long.MIN_VALUE;

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

    /** Full-map visibility policy for discovered and discoverable markers. */
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
                || isWaypointRegionUnlocked(marker));
    }

    private static boolean isWaypointRegionUnlocked(
            LostTalesMapMarkerData marker) {
        if (marker == null || marker.getDimensionId()
                != LOTRDimension.MIDDLE_EARTH.dimensionID) {
            return false;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null
                || minecraft.theWorld == null) {
            return false;
        }
        long tick = minecraft.theWorld.getTotalWorldTime();
        Object snapshot =
                LostTalesClientMapMarkerStore.getSnapshotIdentity();
        if (regionCacheWorld != minecraft.theWorld
                || regionCachePlayer != minecraft.thePlayer
                || regionCacheSnapshot != snapshot
                || regionCacheTick != tick) {
            REGION_UNLOCK_CACHE.clear();
            regionCacheWorld = minecraft.theWorld;
            regionCachePlayer = minecraft.thePlayer;
            regionCacheSnapshot = snapshot;
            regionCacheTick = tick;
        }
        String cacheKey = marker.getId();
        Boolean cached = cacheKey == null
                ? null : REGION_UNLOCK_CACHE.get(cacheKey);
        if (cached != null) {
            return cached.booleanValue();
        }
        boolean unlocked = computeWaypointRegionUnlocked(
                minecraft, marker);
        if (cacheKey != null && cacheKey.length() > 0) {
            REGION_UNLOCK_CACHE.put(
                    cacheKey, Boolean.valueOf(unlocked));
        }
        return unlocked;
    }

    private static boolean computeWaypointRegionUnlocked(
            Minecraft minecraft, LostTalesMapMarkerData marker) {
        try {
            LOTRWaypoint.Region region =
                    LostTalesMapMarkerRegionResolver.resolve(
                            minecraft.theWorld,
                            marker.getDimensionId(), marker.getX(),
                            marker.getZ(),
                            marker.getLotrWaypointId());
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
