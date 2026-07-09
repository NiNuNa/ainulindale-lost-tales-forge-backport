package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/** Unlocks public/global LOTR waypoints that are backed by Lost Tales map markers. */
public final class LostTalesMapMarkerWaypointUnlockHelper {
    private LostTalesMapMarkerWaypointUnlockHelper() {}

    public static boolean unlockWaypointForDiscoveredMarker(EntityPlayer player, String markerId) {
        return unlockWaypointForDiscoveredMarker(player, LostTalesMapMarkerCatalog.getMarker(markerId));
    }

    public static boolean unlockWaypointForDiscoveredMarker(EntityPlayer player, LostTalesMapMarkerDefinition marker) {
        if (!(player instanceof EntityPlayerMP) || marker == null || !marker.hasFastTravel()) {
            return false;
        }
        if (LostTalesMapMarkerWaypointRegistry.isExistingLotrWaypointMarker(marker)) {
            return false;
        }

        LOTRWaypoint.Region region = LostTalesMapMarkerWaypointRegistry.getRegionForMarker(marker.getId());
        if (region == null) {
            return false;
        }

        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        if (lotrData == null || lotrData.isFTRegionUnlocked(region)) {
            return false;
        }
        lotrData.unlockFTRegion(region);
        return true;
    }

    public static boolean lockWaypointForForgottenMarker(EntityPlayer player, String markerId) {
        if (!(player instanceof EntityPlayerMP)) {
            return false;
        }
        LOTRWaypoint.Region region = LostTalesMapMarkerWaypointRegistry.getRegionForMarker(markerId);
        if (region == null) {
            return false;
        }
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        if (lotrData == null || !lotrData.isFTRegionUnlocked(region)) {
            return false;
        }
        lotrData.lockFTRegion(region);
        return true;
    }
}
