package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
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
        if (isAllowed(player, waypoint)) {
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
        LOTRAbstractWaypoint target = data == null
                ? null : data.getTargetFTWaypoint();
        if (target != null && !isAllowed(player, target)) {
            data.setTargetFTWaypoint(null);
            data.setTicksUntilFT(0);
        }
    }

    public static boolean isAllowed(
            EntityPlayerMP player, LOTRAbstractWaypoint waypoint) {
        LostTalesMapMarkerDefinition marker =
                LostTalesMapMarkerWaypointRegistry
                        .getMarkerForWaypoint(waypoint);
        if (marker == null || !marker.isDiscoverable()) {
            return true;
        }
        LostTalesQuestPlayerData data =
                LostTalesQuestPlayerData.get(player);
        return data != null && data.isMarkerDiscovered(marker.getId());
    }
}
