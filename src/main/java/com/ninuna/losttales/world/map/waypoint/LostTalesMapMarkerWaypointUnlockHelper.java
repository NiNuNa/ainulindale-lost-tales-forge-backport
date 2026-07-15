package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerCatalog;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.LostTalesQuestMarkerHelper;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
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
        if (!(player instanceof EntityPlayerMP) || marker == null
                || !marker.hasFastTravel() || !marker.isDiscoverable()) {
            return false;
        }
        LOTRWaypoint.Region region =
                LostTalesMapMarkerWaypointRegistry.getRegionForMarker(
                        marker.getId());
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
        LostTalesMapMarkerDefinition marker =
                LostTalesMapMarkerCatalog.getMarker(markerId);
        if (marker == null || !marker.isDiscoverable()) {
            return false;
        }
        LOTRWaypoint.Region region =
                LostTalesMapMarkerWaypointRegistry.getRegionForMarker(markerId);
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

    /**
     * Makes private Lost Tales fast-travel regions match marker discovery.
     * Native LOTR biome regions are deliberately left intact: they record
     * which areas the active character has visited and reveal anonymous map
     * markers. Per-marker fast-travel authorization is enforced separately.
     */
    public static boolean reconcileBundledWaypointRegions(
            EntityPlayerMP player, Set<String> discoveredMarkerIds) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) {
            return false;
        }

        Map<LOTRWaypoint.Region, Boolean> desiredRegions =
                new LinkedHashMap<LOTRWaypoint.Region, Boolean>();
        for (LostTalesMapMarkerDefinition marker :
                LostTalesMapMarkerCatalog.getMarkers()) {
            // Never mutate native biome/faction regions. They belong to LOTR
            // and are used by non-discoverable markers exactly as-is.
            if (!LostTalesMapMarkerWaypointRegistry
                    .usesPrivateRegion(marker)) {
                continue;
            }
            LOTRWaypoint.Region region =
                    LostTalesMapMarkerWaypointRegistry.getRegionForMarker(
                            marker.getId());
            if (region == null) {
                continue;
            }
            String markerId = LostTalesQuestMarkerHelper.normalizeMarkerId(marker.getId());
            boolean discovered = !marker.isDiscoverable()
                    || discoveredMarkerIds != null
                    && discoveredMarkerIds.contains(markerId);
            Boolean existing = desiredRegions.get(region);
            desiredRegions.put(region,
                    Boolean.valueOf(discovered || Boolean.TRUE.equals(existing)));
        }

        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        if (lotrData == null) {
            return false;
        }
        boolean changed = false;
        for (Map.Entry<LOTRWaypoint.Region, Boolean> entry :
                desiredRegions.entrySet()) {
            LOTRWaypoint.Region region = entry.getKey();
            boolean shouldBeUnlocked = entry.getValue().booleanValue();
            boolean isUnlocked = lotrData.isFTRegionUnlocked(region);
            if (shouldBeUnlocked && !isUnlocked) {
                lotrData.unlockFTRegion(region);
                changed |= lotrData.isFTRegionUnlocked(region);
            } else if (!shouldBeUnlocked && isUnlocked) {
                lotrData.lockFTRegion(region);
                changed |= !lotrData.isFTRegionUnlocked(region);
            }
        }
        if (changed) {
            LOTRLevelData.saveData(player.getUniqueID());
        }
        return changed;
    }

}
