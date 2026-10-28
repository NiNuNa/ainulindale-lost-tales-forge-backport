package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNames;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.mapmarker.LostTalesWaystonePlacers;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneVisits;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapCoordinateHelper;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerRegionResolver;
import com.ninuna.losttales.world.map.waypoint.LostTalesWaypointFastTravelPolicy;
import com.ninuna.losttales.world.waystone.LostTalesWaystoneStructureRegistry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import lotr.common.LOTRConfig;
import lotr.common.LOTRDimension;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.world.map.LOTRAbstractWaypoint;
import lotr.common.world.map.LOTRCustomWaypoint;
import lotr.common.world.map.LOTRWaypoint;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Waystone travel through LOTR's own fast travel: its target, countdown,
 * cooldown, safety checks and teleport. A journey is checked as it is
 * asked for and again every tick of the countdown.
 */
public final class LostTalesLotrWaystoneTravelAdapter {
    private static final int[][] ADJACENT_OFFSETS = {
            {0, 1}, {1, 0}, {0, -1}, {-1, 0},
            {0, 2}, {2, 0}, {0, -2}, {-2, 0},
            {1, 1}, {1, -1}, {-1, -1}, {-1, 1}
    };
    private static final ConcurrentMap<UUID, PendingTravel> PENDING =
            new ConcurrentHashMap<UUID, PendingTravel>();
    private static final ConcurrentMap<UUID, PendingNativeTravel>
            NATIVE_PENDING =
                    new ConcurrentHashMap<UUID, PendingNativeTravel>();

    private LostTalesLotrWaystoneTravelAdapter() {}

    /**
     * Whether the player may travel from {@code source}, as Destinations
     * asks: they stand in Middle-earth, and its fast travel is on.
     */
    public static boolean canTravelFrom(EntityPlayerMP player,
                                        LostTalesMapMarkerRecord source) {
        return player != null && source != null
                && player.dimension == LOTRDimension.MIDDLE_EARTH.dimensionID
                && source.hasFastTravel();
    }

    /**
     * A place picked on the map after Destinations, travelled to from the
     * waystone. Only the one pick within the window Destinations opened
     * on the server counts ({@link LostTalesWaystoneVisits}); the window
     * closes with it, whatever comes of it.
     */
    public static void beginTravel(
            EntityPlayerMP player,
            int sourceX, int sourceY, int sourceZ,
            String sourceMarkerId, String destinationMarkerId) {
        if (player == null || !LostTalesWaystoneVisits.takeTravel(
                player.getUniqueID(), sourceMarkerId, player.dimension,
                sourceX, sourceY, sourceZ, System.currentTimeMillis())) {
            deny(player, "chat.losttales.fast_travel.window_closed");
            return;
        }
        ResolvedTravel resolved = resolve(
                player, sourceX, sourceY, sourceZ,
                sourceMarkerId, destinationMarkerId, true);
        if (resolved == null) {
            deny(player, "chat.losttales.fast_travel.invalid_waystone");
            return;
        }
        if (resolved.destination == null) {
            return;
        }
        if (!LOTRConfig.enableFastTravel) {
            deny(player, "chat.lotr.ftDisabled");
            return;
        }
        LOTRPlayerData lotrData = LOTRLevelData.getData(player);
        if (lotrData == null) {
            deny(player, "chat.losttales.fast_travel.unavailable");
            return;
        }
        int travelId;
        try {
            travelId = resolved.data.getOrCreateLotrTravelId(
                    resolved.destination.getId());
        } catch (RuntimeException exception) {
            deny(player, "chat.losttales.fast_travel.unavailable");
            return;
        }
        LOTRCustomWaypoint waypoint = createWaypoint(
                player.worldObj, resolved.destination, travelId);
        if (waypoint == null) {
            deny(player, "chat.losttales.fast_travel.unsafe");
            return;
        }
        if (lotrData.getTimeSinceFT()
                < lotrData.getWaypointFTTime(waypoint, player)) {
            deny(player, "lotr.fastTravel.moreTime",
                    LostTalesMapMarkerNames.component(
                            resolved.destination.getId(),
                            resolved.destination.getName(),
                            LostTalesWaystonePlacers.shown(player.worldObj,
                                    resolved.destination.getNamedAfter())));
            return;
        }
        if (!lotrData.canFastTravel()) {
            deny(player, "lotr.fastTravel.underAttack");
            return;
        }
        if (player.isPlayerSleeping()) {
            deny(player, "lotr.fastTravel.inBed");
            return;
        }

        PendingTravel pending = new PendingTravel(
                sourceX, sourceY, sourceZ,
                sourceMarkerId, destinationMarkerId,
                waypoint, resolved.destination.getRevision());
        NATIVE_PENDING.remove(player.getUniqueID());
        PENDING.put(player.getUniqueID(), pending);
        lotrData.setTargetFTWaypoint(waypoint);
        player.closeScreen();
    }

    /**
     * Checks the player's journey again, every tick of LOTR's countdown,
     * and calls it off with a line in the chat where a check fails.
     */
    public static void revalidatePending(
            EntityPlayerMP player, LOTRPlayerData lotrData) {
        if (player == null || lotrData == null
                || player.getUniqueID() == null) {
            return;
        }
        PendingTravel pending = PENDING.get(player.getUniqueID());
        if (pending == null) {
            revalidateNativePending(player, lotrData);
            return;
        }
        LOTRAbstractWaypoint target = lotrData.getTargetFTWaypoint();
        if (target == null) {
            PENDING.remove(player.getUniqueID(), pending);
            return;
        }
        if (target != pending.waypoint) {
            PENDING.remove(player.getUniqueID(), pending);
            return;
        }
        ResolvedTravel resolved = resolve(
                player, pending.sourceX, pending.sourceY,
                pending.sourceZ, pending.sourceMarkerId,
                pending.destinationMarkerId, false);
        boolean valid = resolved != null
                && LOTRConfig.enableFastTravel
                && lotrData.canFastTravel()
                && !player.isPlayerSleeping()
                && resolved.destination.getRevision()
                        == pending.destinationRevision;
        if (valid && lotrData.getTicksUntilFT() <= 1) {
            valid = prepareSafeDestination(
                    player.worldObj, resolved.destination,
                    pending.waypoint);
        }
        if (!valid) {
            PENDING.remove(player.getUniqueID(), pending);
            lotrData.setTargetFTWaypoint(null);
            lotrData.setTicksUntilFT(0);
            deny(player,
                    "chat.losttales.fast_travel.revalidation_failed");
        }
    }

    /** Forgets every travel waiting to finish; called as the server starts and stops. */
    public static void clear() {
        PENDING.clear();
        NATIVE_PENDING.clear();
    }

    /** Forgets the player's travel and the waystone they used last; called as they leave. */
    public static void clearPending(EntityPlayerMP player) {
        if (player != null && player.getUniqueID() != null) {
            PENDING.remove(player.getUniqueID());
            NATIVE_PENDING.remove(player.getUniqueID());
            LostTalesWaystoneVisits.forget(player.getUniqueID());
        }
    }

    public static void trackNativeTravel(
            EntityPlayerMP player, LOTRCustomWaypoint waypoint,
            LostTalesMapMarkerRecord destination) {
        if (player == null || player.getUniqueID() == null
                || waypoint == null || destination == null) {
            return;
        }
        PENDING.remove(player.getUniqueID());
        NATIVE_PENDING.put(
                player.getUniqueID(),
                new PendingNativeTravel(
                        destination.getId(), waypoint,
                        destination.getRevision()));
    }

    private static void revalidateNativePending(
            EntityPlayerMP player, LOTRPlayerData lotrData) {
        PendingNativeTravel pending =
                NATIVE_PENDING.get(player.getUniqueID());
        if (pending == null) {
            return;
        }
        LOTRAbstractWaypoint target =
                lotrData.getTargetFTWaypoint();
        if (target == null || target != pending.waypoint) {
            NATIVE_PENDING.remove(
                    player.getUniqueID(), pending);
            return;
        }
        LostTalesMapMarkerRecord destination =
                LostTalesMapMarkerStorage.get(player.worldObj)
                        .getRecord(pending.destinationMarkerId);
        boolean valid = destination != null
                && destination.getRevision()
                        == pending.destinationRevision
                && LostTalesWaypointFastTravelPolicy.isAllowed(
                        player, target);
        if (valid && lotrData.getTicksUntilFT() <= 1) {
            valid = prepareSafeDestination(
                    player.worldObj, destination,
                    pending.waypoint);
        }
        if (!valid) {
            NATIVE_PENDING.remove(
                    player.getUniqueID(), pending);
            lotrData.setTargetFTWaypoint(null);
            lotrData.setTicksUntilFT(0);
            deny(player,
                    "chat.losttales.fast_travel.revalidation_failed");
        }
    }

    /**
     * Where LOTR's own fast travel to a waystone arrives: where its house
     * says, else beside the waystone, as waystone travel arrives. Null for
     * a marker with no waystone standing, which keeps LOTR's own target,
     * and where no spot is safe.
     */
    public static LOTRCustomWaypoint createSafeDestinationWaypoint(
            EntityPlayerMP player, LostTalesMapMarkerRecord destination) {
        if (player == null || player.worldObj == null
                || destination == null || !destination.isLinked()) {
            return null;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(player.worldObj);
        int travelId;
        try {
            travelId = data.getOrCreateLotrTravelId(
                    destination.getId());
        } catch (RuntimeException exception) {
            return null;
        }
        return createWaypoint(
                player.worldObj, destination, travelId);
    }

    private static ResolvedTravel resolve(
            EntityPlayerMP player,
            int sourceX, int sourceY, int sourceZ,
            String sourceMarkerId, String destinationMarkerId,
            boolean sendSpecificDenial) {
        if (player == null || player.worldObj == null
                || player.worldObj.isRemote
                || player.dimension
                        != LOTRDimension.MIDDLE_EARTH.dimensionID
                || sourceMarkerId == null
                || destinationMarkerId == null
                || sourceMarkerId.equals(destinationMarkerId)) {
            return null;
        }
        World world = player.worldObj;
        // Reach first: the position comes from the client, and reading a
        // tile entity there could load a chunk far from the player.
        if (player.getDistanceSq(sourceX + 0.5D, sourceY + 0.5D,
                sourceZ + 0.5D) > LostTalesTileEntityWaystone.REACH_SQ) {
            return null;
        }
        TileEntity raw = world.getTileEntity(
                sourceX, sourceY, sourceZ);
        if (!(raw instanceof LostTalesTileEntityWaystone)) {
            return null;
        }
        LostTalesTileEntityWaystone tile =
                (LostTalesTileEntityWaystone)raw;
        if (!tile.isUseableByPlayer(player) || !tile.isLinked()
                || !sourceMarkerId.equals(tile.getMarkerId())) {
            return null;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(world);
        LostTalesMapMarkerRecord source =
                data.getRecord(sourceMarkerId);
        LostTalesMapMarkerRecord destination =
                data.getRecord(destinationMarkerId);
        if (source == null || !source.isLinkedTo(
                world.provider.dimensionId, sourceX, sourceY, sourceZ,
                tile.getLinkToken())
                || !source.hasFastTravel()
                || !LostTalesMapMarkerVisibilityPolicy.canView(
                        source, player)
                || destination == null
                || !destination.hasFastTravel()
                || destination.getDimensionId()
                        != LOTRDimension.MIDDLE_EARTH.dimensionID
                || !LostTalesMapMarkerVisibilityPolicy.canView(
                        destination, player)) {
            return null;
        }
        if (destination.isDiscoverable()) {
            LostTalesQuestPlayerData questData =
                    LostTalesQuestPlayerData.get(player);
            if (questData == null
                    || !questData.isMarkerDiscovered(
                            destination.getId())) {
                if (sendSpecificDenial) {
                    deny(player,
                            "chat.losttales.fast_travel.marker_undiscovered");
                    return ResolvedTravel.denied();
                }
                return null;
            }
        }
        if (destination.requiresRegionUnlock()) {
            LOTRPlayerData lotrData = LOTRLevelData.getData(player);
            LOTRWaypoint.Region region =
                    LostTalesMapMarkerRegionResolver.resolve(
                            world, destination.toDefinition());
            if (lotrData == null || region == null
                    || !lotrData.isFTRegionUnlocked(region)) {
                if (sendSpecificDenial) {
                    deny(player,
                            "chat.losttales.fast_travel.region_locked");
                    return ResolvedTravel.denied();
                }
                return null;
            }
        }
        return new ResolvedTravel(data, destination);
    }

    private static LOTRCustomWaypoint createWaypoint(
            World world, LostTalesMapMarkerRecord destination,
            int travelId) {
        int markerX = MathHelper.floor_double(destination.getX());
        int markerY = MathHelper.floor_double(destination.getY());
        int markerZ = MathHelper.floor_double(destination.getZ());
        if (!destination.isLinked()) {
            return createWaypointAt(
                    world, destination, travelId,
                    markerX, markerY, markerZ);
        }

        // A generated waystone's house says where a traveller arrives
        // (WAY-17); the spots beside the waystone stand in where it is
        // not safe, or for a waystone with no house.
        int[] arrival = LostTalesWaystoneStructureRegistry.arrival(
                destination.getWaystoneStructureType());
        if (arrival != null) {
            LOTRCustomWaypoint outsideHouse = createWaypointAt(
                    world, destination, travelId,
                    destination.getLinkedX() + arrival[0],
                    destination.getLinkedY() + arrival[1],
                    destination.getLinkedZ() + arrival[2]);
            if (prepareSafeDestination(
                    world, destination, outsideHouse)) {
                return outsideHouse;
            }
        }
        for (int[] offset : ADJACENT_OFFSETS) {
            LOTRCustomWaypoint adjacent = createWaypointAt(
                    world, destination, travelId,
                    destination.getLinkedX() + offset[0],
                    destination.getLinkedY(),
                    destination.getLinkedZ() + offset[1]);
            if (prepareSafeDestination(
                    world, destination, adjacent)) {
                return adjacent;
            }
        }
        return null;
    }

    private static LOTRCustomWaypoint createWaypointAt(
            World world, LostTalesMapMarkerRecord destination,
            int travelId, int x, int y, int z) {
        // LOTR keeps a waypoint's name as one string, so an unnamed
        // waystone's is the server's words for what it is called after:
        // its placing character by the name it has now (WAY-01).
        String name = destination.getName().length() > 0
                ? destination.getName()
                : LostTalesMapMarkerNamedAfter.shownName(
                        LostTalesWaystonePlacers.shown(world,
                                destination.getNamedAfter()));
        return new LOTRCustomWaypoint(
                name.length() > 0 ? name : destination.getId(),
                LostTalesMapCoordinateHelper.worldToMapImageX(
                        destination.getX()),
                LostTalesMapCoordinateHelper.worldToMapImageZ(
                        destination.getZ()),
                x, y, z, travelId);
    }

    private static boolean prepareSafeDestination(
            World world, LostTalesMapMarkerRecord destination,
            LOTRCustomWaypoint waypoint) {
        if (world == null || destination == null || waypoint == null
                || world.provider.dimensionId
                        != destination.getDimensionId()) {
            return false;
        }
        try {
            int safeY = waypoint.getYCoord(
                    world, waypoint.getXCoord(),
                    waypoint.getZCoord());
            return safeY > 0
                    && safeY < world.getActualHeight() - 1;
        } catch (LinkageError error) {
            return false;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private static void deny(
            EntityPlayerMP player, String translationKey,
            Object... arguments) {
        if (player != null) {
            player.closeScreen();
            player.addChatMessage(new ChatComponentTranslation(
                    translationKey, arguments));
        }
    }

    private static final class ResolvedTravel {
        private final LostTalesMapMarkerWorldData data;
        private final LostTalesMapMarkerRecord destination;

        private ResolvedTravel(
                LostTalesMapMarkerWorldData data,
                LostTalesMapMarkerRecord destination) {
            this.data = data;
            this.destination = destination;
        }

        private static ResolvedTravel denied() {
            return new ResolvedTravel(null, null);
        }
    }

    private static final class PendingTravel {
        private final int sourceX;
        private final int sourceY;
        private final int sourceZ;
        private final String sourceMarkerId;
        private final String destinationMarkerId;
        private final LOTRCustomWaypoint waypoint;
        private final long destinationRevision;

        private PendingTravel(
                int sourceX, int sourceY, int sourceZ,
                String sourceMarkerId, String destinationMarkerId,
                LOTRCustomWaypoint waypoint,
                long destinationRevision) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.sourceZ = sourceZ;
            this.sourceMarkerId = sourceMarkerId;
            this.destinationMarkerId = destinationMarkerId;
            this.waypoint = waypoint;
            this.destinationRevision = destinationRevision;
        }
    }

    private static final class PendingNativeTravel {
        private final String destinationMarkerId;
        private final LOTRCustomWaypoint waypoint;
        private final long destinationRevision;

        private PendingNativeTravel(
                String destinationMarkerId,
                LOTRCustomWaypoint waypoint,
                long destinationRevision) {
            this.destinationMarkerId = destinationMarkerId;
            this.waypoint = waypoint;
            this.destinationRevision = destinationRevision;
        }
    }
}
