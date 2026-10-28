package com.ninuna.losttales.world.map.waypoint;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerIdResolver;
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

/**
 * The server's checks on LOTR's own fast travel to a place that is a
 * marker: the player may see it, its region is unlocked, and the played
 * character has discovered it where it must be discovered.
 */
public final class LostTalesWaypointFastTravelPolicy {

    private static final String DENIAL_KEY =
            "chat.losttales.fast_travel.marker_undiscovered";

    private LostTalesWaypointFastTravelPolicy() {}

    /**
     * Stands in for {@code LOTRPlayerData.setTargetFTWaypoint} in LOTR's
     * fast-travel packet handler (the coremod's redirect): the target is
     * set only where the player may travel to it, and a waystone's target
     * is moved to where its house says a traveller arrives.
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

    /**
     * Every tick: checks a journey under way again, and drops a target the
     * player may not travel to, which only a packet hook that did not land
     * lets through.
     */
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

    /**
     * Whether the player has reached a marker: they may see it, its region
     * is unlocked for them where it needs one, and the played character
     * has discovered it where it must be discovered. Sharing a place in
     * the chat asks this, whatever its fast travel or world.
     */
    public static boolean hasVisited(
            EntityPlayerMP player, LostTalesMapMarkerRecord record) {
        if (player == null || record == null || player.worldObj == null
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

    /**
     * The marker id a fast-travel destination stands for: the saved record
     * behind a custom waypoint, the bundled marker registered for a native
     * waypoint, or the canonical {@code lotr:waypoint:name} id of a native
     * waypoint without one. Empty when nothing answers.
     */
    public static String resolveMarkerId(
            EntityPlayerMP player, LOTRAbstractWaypoint waypoint) {
        if (waypoint == null) {
            return "";
        }
        LostTalesMapMarkerRecord record = getSavedMarker(player, waypoint);
        if (record != null && record.getId() != null
                && record.getId().length() > 0) {
            return record.getId();
        }
        LostTalesMapMarkerDefinition marker =
                LostTalesMapMarkerWaypointRegistry
                        .getMarkerForWaypoint(waypoint);
        if (marker != null && marker.getId() != null
                && marker.getId().length() > 0) {
            return marker.getId();
        }
        if (waypoint instanceof LOTRWaypoint) {
            String code = waypoint.getCodeName();
            return code == null || code.length() == 0 ? ""
                    : LostTalesMapMarkerIdResolver.LOTR_WAYPOINT_PREFIX
                            + code.toLowerCase(java.util.Locale.ROOT);
        }
        return "";
    }

    /**
     * Whether the player may fast-travel to the waypoint: one that is no
     * marker always, a marker with fast travel in the player's world that
     * they have reached ({@link #hasVisited}); a bundled marker whose
     * record is gone never.
     */
    public static boolean isAllowed(
            EntityPlayerMP player, LOTRAbstractWaypoint waypoint) {
        LostTalesMapMarkerRecord record =
                getSavedMarker(player, waypoint);
        if (record == null) {
            return LostTalesMapMarkerWaypointRegistry
                    .getMarkerForWaypoint(waypoint) == null;
        }
        return record.hasFastTravel()
                && record.getDimensionId() == player.dimension
                && hasVisited(player, record);
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
