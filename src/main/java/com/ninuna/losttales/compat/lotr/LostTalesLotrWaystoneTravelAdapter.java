package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerStorage;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibilityPolicy;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerWorldData;
import com.ninuna.losttales.quest.player.LostTalesQuestPlayerData;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapCoordinateHelper;
import com.ninuna.losttales.world.map.waypoint.LostTalesMapMarkerRegionResolver;
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
 * Version-specific bridge into LOTR's native fast-travel target, countdown,
 * cooldown, safety, and teleport implementation.
 */
public final class LostTalesLotrWaystoneTravelAdapter {
    private static final ConcurrentMap<UUID, PendingTravel> PENDING =
            new ConcurrentHashMap<UUID, PendingTravel>();

    private LostTalesLotrWaystoneTravelAdapter() {}

    public static void beginTravel(
            EntityPlayerMP player,
            int sourceX, int sourceY, int sourceZ,
            String sourceMarkerId, String destinationMarkerId) {
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
                resolved.destination, travelId);
        if (!prepareSafeDestination(
                player.worldObj, resolved.destination, waypoint)) {
            deny(player, "chat.losttales.fast_travel.unsafe");
            return;
        }
        if (lotrData.getTimeSinceFT()
                < lotrData.getWaypointFTTime(waypoint, player)) {
            deny(player, "lotr.fastTravel.moreTime",
                    resolved.destination.getName());
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
        PENDING.put(player.getUniqueID(), pending);
        lotrData.setTargetFTWaypoint(waypoint);
        player.closeScreen();
    }

    /**
     * Called once per server player tick. This is the final authority while
     * LOTR's own countdown is active.
     */
    public static void revalidatePending(
            EntityPlayerMP player, LOTRPlayerData lotrData) {
        if (player == null || lotrData == null
                || player.getUniqueID() == null) {
            return;
        }
        PendingTravel pending = PENDING.get(player.getUniqueID());
        if (pending == null) {
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
                        >= pending.destinationRevision;
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

    public static void clearPending(EntityPlayerMP player) {
        if (player != null && player.getUniqueID() != null) {
            PENDING.remove(player.getUniqueID());
        }
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
        if (!isLiveLink(source, tile, world,
                sourceX, sourceY, sourceZ)
                || !source.hasFastTravel()
                || !LostTalesMapMarkerVisibilityPolicy.canView(
                        source, player)
                || destination == null || !destination.isActive()
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
        return new ResolvedTravel(data, source, destination);
    }

    private static boolean isLiveLink(
            LostTalesMapMarkerRecord record,
            LostTalesTileEntityWaystone tile,
            World world, int x, int y, int z) {
        return record != null && record.isActive()
                && record.isLinked() && record.hasWaystone()
                && record.getLinkToken() != null
                && record.getLinkToken().equals(tile.getLinkToken())
                && record.getLinkedDimensionId()
                        == world.provider.dimensionId
                && record.getLinkedX() == x
                && record.getLinkedY() == y
                && record.getLinkedZ() == z;
    }

    private static LOTRCustomWaypoint createWaypoint(
            LostTalesMapMarkerRecord destination, int travelId) {
        int x = MathHelper.floor_double(destination.getX());
        int y = MathHelper.floor_double(destination.getY());
        int z = MathHelper.floor_double(destination.getZ());
        return new LOTRCustomWaypoint(
                destination.getName(),
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
        private final LostTalesMapMarkerRecord source;
        private final LostTalesMapMarkerRecord destination;

        private ResolvedTravel(
                LostTalesMapMarkerWorldData data,
                LostTalesMapMarkerRecord source,
                LostTalesMapMarkerRecord destination) {
            this.data = data;
            this.source = source;
            this.destination = destination;
        }

        private static ResolvedTravel denied() {
            return new ResolvedTravel(null, null, null);
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
}
