package com.ninuna.losttales.mapmarker;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
import com.ninuna.losttales.character.server.KnownAccounts;
import com.ninuna.losttales.character.storage.CharacterStorage;
import com.ninuna.losttales.character.storage.CharacterWorldData;
import com.ninuna.losttales.compat.lotr.LostTalesWaystonePermissionPolicy;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesWaystoneSettingsRequestPacket;
import com.ninuna.losttales.network.packet.LostTalesWaystoneStatePacket;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRPlayerData;
import lotr.common.fellowship.LOTRFellowship;
import lotr.common.fellowship.LOTRFellowshipData;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * Main-thread server authority for waystone settings. The block link and
 * repository revision are revalidated for every request, and every
 * answer goes back to the player as the waystone's state, saying why.
 */
public final class LostTalesWaystoneSettingsService {
    private static final double MIN_DISCOVERY_RADIUS = 1.0D;

    private LostTalesWaystoneSettingsService() {}

    /**
     * The player used a waystone. It passes the checks every request
     * passes — it stands where its record links it, within the player's
     * reach, holding the record's link token — and its state goes to the
     * player as an opening, which opens the waystone's page. A waystone
     * that fails them opens nothing.
     */
    public static boolean open(
            EntityPlayerMP player, LostTalesTileEntityWaystone tile) {
        if (player == null || tile == null) {
            return false;
        }
        Context context = resolve(player, tile.xCoord, tile.yCoord,
                tile.zCoord, tile.getMarkerId());
        if (context == null || context.tile != tile) {
            return false;
        }
        sendState(player, context.tile, context.record,
                LostTalesWaystoneStateReason.OPENED);
        return true;
    }

    public static void apply(
            EntityPlayerMP player,
            LostTalesWaystoneSettingsRequestPacket request) {
        if (player == null || request == null || request.isMalformed()) {
            return;
        }
        Context context = resolve(player, request.getX(), request.getY(),
                request.getZ(), request.getMarkerId());
        if (context == null) {
            deny(player, "chat.losttales.waystone.invalid");
            return;
        }
        if (context.record.getRevision() != request.getExpectedRevision()) {
            refuse(player, context, LostTalesWaystoneStateReason.STALE);
            return;
        }
        if (!LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                player, context.record, context.world,
                request.getX(), request.getY(), request.getZ(), true)) {
            refuse(player, context, LostTalesWaystoneStateReason.DENIED);
            return;
        }

        Outcome outcome;
        switch (request.getOperation()) {
            case SAVE:
                outcome = applySettings(
                        player, context.record, request);
                break;
            case SHARE_PLAYER:
                outcome = applyPlayerSharing(
                        context.world, context.record,
                        request.getTargetPlayerName(), false);
                break;
            case UNSHARE_PLAYER:
                outcome = applyPlayerSharing(
                        context.world, context.record,
                        request.getTargetPlayerName(), true);
                break;
            case SHARE_FELLOWSHIP:
                outcome = applyFellowshipSharing(
                        player, context.record,
                        request.getTargetPlayerName(), false);
                break;
            case UNSHARE_FELLOWSHIP:
                outcome = applyFellowshipSharing(
                        player, context.record,
                        request.getTargetPlayerName(), true);
                break;
            default:
                outcome = Outcome.refused(
                        LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        if (outcome.refusal != null) {
            refuse(player, context, outcome.refusal);
            return;
        }

        LostTalesMapMarkerRecord updated = outcome.updated;
        try {
            context.data.saveRecord(updated);
            context.tile.linkTo(updated);
        } catch (RuntimeException exception) {
            refuse(player, context,
                    LostTalesWaystoneStateReason.SAVE_FAILED);
            return;
        }
        LostTalesMapMarkerSyncManager.syncViewersOf(context.record, updated);
        sendState(player, context.tile, updated,
                LostTalesWaystoneStateReason.SAVED);
        player.addChatMessage(new ChatComponentTranslation(
                LostTalesWaystoneStateReason.SAVED.getMessageKey()));
    }

    public static void sendState(
            EntityPlayerMP player,
            LostTalesTileEntityWaystone tile,
            LostTalesMapMarkerRecord record,
            LostTalesWaystoneStateReason reason) {
        if (player == null || tile == null || record == null
                || reason == null || player.worldObj == null
                || !tile.isUseableByPlayer(player)) {
            return;
        }
        boolean operator =
                LostTalesWaystonePermissionPolicy.managesWaystones(player);
        boolean canEdit =
                LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                        player, record, player.worldObj,
                        tile.xCoord, tile.yCoord, tile.zCoord, false);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesWaystoneStatePacket(
                        player.dimension,
                        tile.xCoord, tile.yCoord, tile.zCoord,
                        record, canEdit, operator, reason),
                player);
    }

    /**
     * A request turned down: the chat says why, and the state as it
     * stands goes back to the page saying the same.
     */
    private static void refuse(
            EntityPlayerMP player, Context context,
            LostTalesWaystoneStateReason reason) {
        deny(player, reason.getMessageKey());
        sendState(player, context.tile, context.record, reason);
    }

    /**
     * The waystone at a place in the player's own world, linked to the
     * marker named and within the player's reach, its record linked back
     * to that very block by the same token; null where any of it fails.
     */
    private static Context resolve(
            EntityPlayerMP player, int x, int y, int z, String markerId) {
        World world = player.worldObj;
        if (world == null || world.isRemote || markerId == null
                || markerId.length() == 0
                || player.dimension != world.provider.dimensionId) {
            return null;
        }
        // Reach first: the position comes from the client, and reading a
        // tile entity there could load a chunk far from the player.
        if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D)
                > LostTalesTileEntityWaystone.REACH_SQ) {
            return null;
        }
        TileEntity raw = world.getTileEntity(x, y, z);
        if (!(raw instanceof LostTalesTileEntityWaystone)) {
            return null;
        }
        LostTalesTileEntityWaystone tile =
                (LostTalesTileEntityWaystone)raw;
        if (!tile.isUseableByPlayer(player)
                || !tile.isLinked()
                || !markerId.equals(tile.getMarkerId())) {
            return null;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(world);
        LostTalesMapMarkerRecord record = data.getRecord(markerId);
        if (record == null || !record.isLinked()
                || record.getLinkedDimensionId()
                        != world.provider.dimensionId
                || record.getLinkedX() != x
                || record.getLinkedY() != y
                || record.getLinkedZ() != z
                || record.getLinkToken() == null
                || !record.getLinkToken().equals(tile.getLinkToken())) {
            return null;
        }
        return new Context(world, tile, data, record);
    }

    private static Outcome applySettings(
            EntityPlayerMP player,
            LostTalesMapMarkerRecord record,
            LostTalesWaystoneSettingsRequestPacket request) {
        LostTalesMapMarkerEditableSettings requested =
                request.getSettings();
        if (requested == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        String name = trim(requested.getName());
        String icon = trim(requested.getIconName());
        String color = normalizeColor(requested.getColorName());
        String category = trim(requested.getCategoryName());
        String description = trim(requested.getDescription());
        String structureType = record.getWaystoneStructureType();
        double x = requested.getX();
        double y = requested.getY();
        double z = requested.getZ();
        double compassRadius = requested.getCompassFadeInRadius();
        double discoveryRadius = requested.getDiscoveryRadius();
        LostTalesMapMarkerVisibility visibility =
                requested.getVisibility();
        if (name.length() == 0
                || name.length()
                        > LostTalesMapMarkerRecord.MAX_NAME_LENGTH
                || icon.length() == 0
                || icon.length()
                        > LostTalesMapMarkerRecord.MAX_NAME_LENGTH
                || color == null
                || category.length() == 0
                || category.length()
                        > LostTalesMapMarkerRecord.MAX_NAME_LENGTH
                || description.length()
                        > LostTalesMapMarkerRecord.MAX_TEXT_LENGTH
                || !validCoordinate(x)
                || !validMarkerY(y)
                || !validCoordinate(z)
                || !validRadius(compassRadius, 0.0D)
                || !validRadius(
                        discoveryRadius, MIN_DISCOVERY_RADIUS)
                || requested.getPriority()
                        < LostTalesMapMarkerDefinition.MIN_PRIORITY
                || requested.getPriority()
                        > LostTalesMapMarkerDefinition.MAX_PRIORITY
                || visibility == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        if (visibility == LostTalesMapMarkerVisibility.PUBLIC
                && !LostTalesWaystonePermissionPolicy.canMakePublic(
                        player)) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.PUBLIC_DENIED);
        }
        if (changesPhysicalFields(record, requested)) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
        LostTalesMapMarkerEditableSettings normalized =
                new LostTalesMapMarkerEditableSettings(
                        name, icon, color, category, description,
                        requested.hasFastTravel(),
                        record.getDimensionId(),
                        record.getX(), record.getY(), record.getZ(),
                        compassRadius, discoveryRadius,
                        requested.isHiddenUntilDiscovered(),
                        requested.isDiscoverable(),
                        requested.requiresRegionUnlock(),
                        record.hasWaystone(), structureType,
                        requested.getPriority(),
                        visibility);
        try {
            return Outcome.of(record.withEditableSettings(normalized));
        } catch (IllegalArgumentException exception) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS);
        }
    }

    private static boolean changesPhysicalFields(
            LostTalesMapMarkerRecord record,
            LostTalesMapMarkerEditableSettings settings) {
        return record.getDimensionId() != settings.getDimensionId()
                || different(record.getX(), settings.getX())
                || different(record.getY(), settings.getY())
                || different(record.getZ(), settings.getZ())
                || record.hasWaystone() != settings.hasWaystone()
                || !record.getWaystoneStructureType().equals(
                        trim(settings.getWaystoneStructureType())
                                .toLowerCase(Locale.ROOT));
    }

    private static boolean different(double first, double second) {
        return Double.doubleToLongBits(first)
                != Double.doubleToLongBits(second);
    }

    private static boolean validCoordinate(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && Math.abs(value)
                        <= LostTalesMapMarkerRecord
                                .MAX_ABSOLUTE_COORDINATE;
    }

    private static boolean validMarkerY(double value) {
        return validCoordinate(value)
                && (LostTalesMapMarkerHeightResolver.isAutomatic(value)
                    || (value >= 0.0D && value <= 255.0D));
    }

    private static boolean validRadius(
            double value, double minimum) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= minimum
                && value <= LostTalesMapMarkerRecord.MAX_RADIUS;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static Outcome applyPlayerSharing(
            World world, LostTalesMapMarkerRecord record,
            String playerName, boolean remove) {
        UUID targetId = remove ? sharedNamed(world, record, playerName) : null;
        if (targetId == null) {
            targetId = KnownAccounts.find(world, playerName);
        }
        if (targetId == null) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.PLAYER_NOT_FOUND);
        }
        if (targetId.equals(record.getOwnerPlayerId())) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.INVALID_SHARE);
        }
        Set<UUID> shared =
                new LinkedHashSet<UUID>(record.getSharedPlayerIds());
        if (remove) {
            shared.remove(targetId);
        } else {
            if (shared.size()
                    >= LostTalesMapMarkerRecord.MAX_SHARED_PLAYERS) {
                return Outcome.refused(
                        LostTalesWaystoneStateReason.SHARE_LIMIT);
            }
            shared.add(targetId);
        }
        LostTalesMapMarkerVisibility visibility =
                record.getVisibility();
        if (!remove
                && visibility == LostTalesMapMarkerVisibility.PRIVATE) {
            visibility = LostTalesMapMarkerVisibility.SHARED;
        }
        return Outcome.of(record.withSharedPlayers(shared, visibility));
    }

    private static Outcome applyFellowshipSharing(
            EntityPlayerMP player,
            LostTalesMapMarkerRecord record,
            String fellowshipName, boolean remove) {
        LOTRFellowship fellowship = resolveFellowship(
                player, record, fellowshipName, remove);
        if (fellowship == null
                || fellowship.getFellowshipID() == null
                || !remove && fellowship.isDisbanded()) {
            return Outcome.refused(
                    LostTalesWaystoneStateReason.FELLOWSHIP_NOT_FOUND);
        }
        Set<UUID> shared = new LinkedHashSet<UUID>(
                record.getSharedFellowshipIds());
        UUID fellowshipId = fellowship.getFellowshipID();
        if (remove) {
            shared.remove(fellowshipId);
        } else {
            if (shared.size()
                    >= LostTalesMapMarkerRecord
                            .MAX_SHARED_FELLOWSHIPS) {
                return Outcome.refused(
                        LostTalesWaystoneStateReason.SHARE_LIMIT);
            }
            shared.add(fellowshipId);
        }
        LostTalesMapMarkerVisibility visibility =
                record.getVisibility();
        if (!remove
                && visibility
                        == LostTalesMapMarkerVisibility.PRIVATE) {
            visibility = LostTalesMapMarkerVisibility.SHARED;
        }
        return Outcome.of(record.withSharedFellowships(
                shared, visibility));
    }

    private static LOTRFellowship resolveFellowship(
            EntityPlayerMP player,
            LostTalesMapMarkerRecord record,
            String fellowshipName, boolean remove) {
        String normalized = trim(fellowshipName);
        if (normalized.length() == 0) {
            return null;
        }
        if (remove) {
            for (UUID fellowshipId
                    : record.getSharedFellowshipIds()) {
                LOTRFellowship fellowship =
                        LOTRFellowshipData.getFellowship(
                                fellowshipId);
                if (fellowship != null
                        && ((fellowship.getName() != null
                                && fellowship.getName()
                                        .equalsIgnoreCase(normalized))
                        || fellowshipId.toString()
                                .equalsIgnoreCase(normalized))) {
                    return fellowship;
                }
            }
        }
        UUID fellowshipOwner =
                record.getOwnerPlayerId() == null
                        ? player.getUniqueID()
                        : record.getOwnerPlayerId();
        LOTRPlayerData ownerData =
                LOTRLevelData.getData(fellowshipOwner);
        return ownerData == null ? null
                : ownerData.getFellowshipByName(normalized);
    }

    /**
     * The player already shared with who goes by that name, so one who has
     * not visited in a while can still be taken off the list.
     */
    private static UUID sharedNamed(
            World world, LostTalesMapMarkerRecord record, String playerName) {
        String wanted = trim(playerName);
        if (wanted.length() == 0) {
            return null;
        }
        CharacterWorldData storage;
        try {
            storage = CharacterStorage.get(world);
        } catch (RuntimeException unreadable) {
            return null;
        }
        for (UUID shared : record.getSharedPlayerIds()) {
            if (wanted.equalsIgnoreCase(
                    KnownAccounts.nameOf(shared, storage.getRoster(shared)))) {
                return shared;
            }
        }
        return null;
    }

    private static String normalizeColor(String value) {
        String color = value == null ? ""
                : value.trim().toLowerCase(Locale.ROOT)
                        .replace(' ', '_').replace('-', '_');
        if ("white".equals(color) || "red".equals(color)
                || "green".equals(color) || "blue".equals(color)
                || "yellow".equals(color) || "gold".equals(color)
                || "orange".equals(color) || "purple".equals(color)
                || "violet".equals(color) || "gray".equals(color)
                || "grey".equals(color) || "dark_gray".equals(color)
                || "dark_grey".equals(color) || "black".equals(color)) {
            return color;
        }
        String hex = color;
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        } else if (hex.startsWith("0x")) {
            hex = hex.substring(2);
        }
        if (hex.matches("[0-9a-f]{6}")) {
            return "#" + hex;
        }
        return null;
    }

    private static void deny(EntityPlayerMP player, String key) {
        if (player != null) {
            player.addChatMessage(new ChatComponentTranslation(key));
        }
    }

    /** What a request comes to: the record it makes, or why it was turned down. */
    private static final class Outcome {
        private final LostTalesMapMarkerRecord updated;
        private final LostTalesWaystoneStateReason refusal;

        private Outcome(LostTalesMapMarkerRecord updated,
                        LostTalesWaystoneStateReason refusal) {
            this.updated = updated;
            this.refusal = refusal;
        }

        static Outcome of(LostTalesMapMarkerRecord updated) {
            return updated == null ? refused(
                    LostTalesWaystoneStateReason.INVALID_SETTINGS)
                    : new Outcome(updated, null);
        }

        static Outcome refused(LostTalesWaystoneStateReason reason) {
            return new Outcome(null, reason);
        }
    }

    private static final class Context {
        private final World world;
        private final LostTalesTileEntityWaystone tile;
        private final LostTalesMapMarkerWorldData data;
        private final LostTalesMapMarkerRecord record;

        private Context(
                World world, LostTalesTileEntityWaystone tile,
                LostTalesMapMarkerWorldData data,
                LostTalesMapMarkerRecord record) {
            this.world = world;
            this.tile = tile;
            this.data = data;
            this.record = record;
        }
    }
}
