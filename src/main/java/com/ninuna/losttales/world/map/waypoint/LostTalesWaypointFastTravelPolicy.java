package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.compat.lotr.LostTalesLotrWaystoneTravelAdapter;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;

/** Server-authoritative discovery gate for marker-backed LOTR fast travel. */
public final class LostTalesWaypointFastTravelPolicy {

    private static final String DENIAL_KEY =
            "chat.losttales.fast_travel.marker_undiscovered";

    private LostTalesWaypointFastTravelPolicy() {}

    /**
     * ASM-compatible replacement for LOTRPlayerData#setTargetFTWaypoint in the
     * LOTR packet handler. The original operation is called only after the
     * active character's discovery state authorizes this exact waypoint.
     */
    public static void setTargetIfAllowed(
            LOTRPlayerData data, LOTRAbstractWaypoint waypoint,
            EntityPlayerMP player) {
        if (data == null || waypoint == null || player == null) {
            return;
        }
        LostTalesMapMarkerRecord record =
                getSavedMarker(player, waypoint);
        if (isAllowed(player, waypoint)) {
            if (record != null && record.isLinked()) {
                LOTRCustomWaypoint safe =
                        LostTalesLotrWaystoneTravelAdapter
                                .createSafeDestinationWaypoint(
                                        player, record);
                if (safe == null) {
                    player.closeScreen();
                    player.addChatMessage(new ChatComponentTranslation(
                            "chat.losttales.fast_travel.unsafe"));
                    return;
                }
                data.setTargetFTWaypoint(safe);
                LostTalesLotrWaystoneTravelAdapter
                        .trackNativeTravel(
                                player, safe, record);
                return;
            }
            data.setTargetFTWaypoint(waypoint);
            return;
        }
        player.closeScreen();
        player.addChatMessage(new ChatComponentTranslation(DENIAL_KEY));
    }

    /** Defense in depth if another coremod prevents the packet hook applying. */
    public static void cancelUnauthorizedTarget(EntityPlayerMP player) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote) {
            return;
        }
        LOTRPlayerData data = LOTRLevelData.getData(player);
        LostTalesLotrWaystoneTravelAdapter.revalidatePending(
                player, data);
        LOTRAbstractWaypoint target = data == null
                ? null : data.getTargetFTWaypoint();
        if (target != null && !isAllowed(player, target)) {
            data.setTargetFTWaypoint(null);
            data.setTicksUntilFT(0);
        }
    }

    public static boolean isAllowed(
            EntityPlayerMP player, LOTRAbstractWaypoint waypoint) {
        LostTalesMapMarkerRecord record =
                getSavedMarker(player, waypoint);
        if (record == null) {
            LostTalesMapMarkerDefinition marker =
                    LostTalesMapMarkerWaypointRegistry
                            .getMarkerForWaypoint(waypoint);
            if (marker == null) {
                return true;
            }
            return false;
        }
        if (!record.hasFastTravel()
                || record.getDimensionId() != player.dimension
                || !LostTalesMapMarkerVisibilityPolicy.canView(
                        record, player)) {
            return false;
        }
        if (record.requiresRegionUnlock()) {
            LOTRWaypoint.Region markerRegion =
                    LostTalesMapMarkerRegionResolver.resolve(
                            player.worldObj, record.toDefinition());
            LOTRPlayerData lotrData = LOTRLevelData.getData(player);
            if (markerRegion == null || lotrData == null
                    || !lotrData.isFTRegionUnlocked(markerRegion)) {
                return false;
            }
        }
        if (!record.isDiscoverable()) {
            return true;
        }
        LostTalesQuestPlayerData data =
                LostTalesQuestPlayerData.get(player);
        return data != null && data.isMarkerDiscovered(record.getId());
    }

    private static LostTalesMapMarkerRecord getSavedMarker(
            EntityPlayerMP player, LOTRAbstractWaypoint waypoint) {
        if (player == null || player.worldObj == null
                || waypoint == null) {
            return null;
        }
        com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(player.worldObj);
        if (waypoint instanceof LOTRCustomWaypoint) {
            LostTalesMapMarkerRecord byTravelId =
                    data.findByLotrTravelId(waypoint.getID());
            if (byTravelId != null) {
                return byTravelId;
            }
        }
        LostTalesMapMarkerDefinition marker =
                LostTalesMapMarkerWaypointRegistry
                        .getMarkerForWaypoint(waypoint);
        return marker == null ? null : data.getRecord(marker.getId());
    }
}
