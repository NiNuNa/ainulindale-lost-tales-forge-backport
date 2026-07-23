package com.ninuna.losttales.mapmarker;

import com.mojang.authlib.GameProfile;
import com.ninuna.losttales.block.tileentity.LostTalesTileEntityWaystone;
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
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;

/**
 * Main-thread server authority for waystone settings. The block link and
 * repository revision are revalidated for every request.
 */
public final class LostTalesWaystoneSettingsService {
    private static final double MIN_DISCOVERY_RADIUS = 1.0D;

    private LostTalesWaystoneSettingsService() {}

    public static void apply(
            EntityPlayerMP player,
            LostTalesWaystoneSettingsRequestPacket request) {
        if (player == null || request == null || request.isMalformed()) {
            return;
        }
        Context context = resolve(player, request);
        if (context == null) {
            deny(player, "chat.losttales.waystone.invalid");
            return;
        }
        if (context.record.getRevision() != request.getExpectedRevision()) {
            deny(player, "chat.losttales.waystone.stale");
            sendState(player, context.tile, context.record);
            return;
        }
        if (!LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                player, context.record, context.world,
                request.getX(), request.getY(), request.getZ(), true)) {
            deny(player, "chat.losttales.waystone.denied");
            sendState(player, context.tile, context.record);
            return;
        }

        LostTalesMapMarkerRecord updated;
        switch (request.getOperation()) {
            case SAVE:
                updated = applySettings(
                        player, context.record, request);
                break;
            case SHARE_PLAYER:
                updated = applyPlayerSharing(
                        player, context.record,
                        request.getTargetPlayerName(), false);
                break;
            case UNSHARE_PLAYER:
                updated = applyPlayerSharing(
                        player, context.record,
                        request.getTargetPlayerName(), true);
                break;
            case SHARE_FELLOWSHIP:
                updated = applyFellowshipSharing(
                        player, context.record,
                        request.getTargetPlayerName(), false);
                break;
            case UNSHARE_FELLOWSHIP:
                updated = applyFellowshipSharing(
                        player, context.record,
                        request.getTargetPlayerName(), true);
                break;
            default:
                updated = null;
        }
        if (updated == null) {
            sendState(player, context.tile, context.record);
            return;
        }

        try {
            context.data.saveRecord(updated);
            context.tile.linkTo(updated);
        } catch (RuntimeException exception) {
            deny(player, "chat.losttales.waystone.save_failed");
            sendState(player, context.tile, context.record);
            return;
        }
        LostTalesMapMarkerSyncManager.syncAll();
        sendState(player, context.tile, updated);
        player.addChatMessage(new ChatComponentTranslation(
                "chat.losttales.waystone.saved"));
    }

    public static void sendState(
            EntityPlayerMP player,
            LostTalesTileEntityWaystone tile,
            LostTalesMapMarkerRecord record) {
        if (player == null || tile == null || record == null
                || player.worldObj == null
                || !tile.isUseableByPlayer(player)) {
            return;
        }
        boolean operator =
                LostTalesWaystonePermissionPolicy.isOperator(player);
        boolean canEdit =
                LostTalesWaystonePermissionPolicy.canBreakOrEdit(
                        player, record, player.worldObj,
                        tile.xCoord, tile.yCoord, tile.zCoord, false);
        LostTalesNetworkHandler.CHANNEL.sendTo(
                new LostTalesWaystoneStatePacket(
                        player.dimension,
                        tile.xCoord, tile.yCoord, tile.zCoord,
                        record, canEdit, operator),
                player);
    }

    private static Context resolve(
            EntityPlayerMP player,
            LostTalesWaystoneSettingsRequestPacket request) {
        World world = player.worldObj;
        if (world == null || world.isRemote
                || player.dimension != world.provider.dimensionId) {
            return null;
        }
        TileEntity raw = world.getTileEntity(
                request.getX(), request.getY(), request.getZ());
        if (!(raw instanceof LostTalesTileEntityWaystone)) {
            return null;
        }
        LostTalesTileEntityWaystone tile =
                (LostTalesTileEntityWaystone)raw;
        if (!tile.isUseableByPlayer(player)
                || !tile.isLinked()
                || !request.getMarkerId().equals(tile.getMarkerId())) {
            return null;
        }
        LostTalesMapMarkerWorldData data =
                LostTalesMapMarkerStorage.get(world);
        LostTalesMapMarkerRecord record =
                data.getRecord(request.getMarkerId());
        if (record == null || !record.isLinked()
                || record.getLinkedDimensionId()
                        != world.provider.dimensionId
                || record.getLinkedX() != request.getX()
                || record.getLinkedY() != request.getY()
                || record.getLinkedZ() != request.getZ()
                || record.getLinkToken() == null
                || !record.getLinkToken().equals(tile.getLinkToken())) {
            return null;
        }
        return new Context(world, tile, data, record);
    }

    private static LostTalesMapMarkerRecord applySettings(
            EntityPlayerMP player,
            LostTalesMapMarkerRecord record,
            LostTalesWaystoneSettingsRequestPacket request) {
        LostTalesMapMarkerEditableSettings requested =
                request.getSettings();
        if (requested == null) {
            deny(player, "chat.losttales.waystone.invalid_settings");
            return null;
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
                || visibility == null) {
            deny(player, "chat.losttales.waystone.invalid_settings");
            return null;
        }
        if (visibility == LostTalesMapMarkerVisibility.PUBLIC
                && !LostTalesWaystonePermissionPolicy.canMakePublic(
                        player)) {
            deny(player, "chat.losttales.waystone.public_denied");
            return null;
        }
        if (changesPhysicalFields(record, requested)) {
            deny(player, "chat.losttales.waystone.invalid_settings");
            return null;
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
                        visibility);
        try {
            return record.withEditableSettings(normalized);
        } catch (IllegalArgumentException exception) {
            deny(player, "chat.losttales.waystone.invalid_settings");
            return null;
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

    private static LostTalesMapMarkerRecord applyPlayerSharing(
            EntityPlayerMP player,
            LostTalesMapMarkerRecord record,
            String playerName, boolean remove) {
        UUID targetId = findPlayerId(playerName);
        if (targetId == null) {
            deny(player, "chat.losttales.waystone.player_not_found");
            return null;
        }
        if (targetId.equals(record.getOwnerPlayerId())) {
            deny(player, "chat.losttales.waystone.invalid_share");
            return null;
        }
        Set<UUID> shared =
                new LinkedHashSet<UUID>(record.getSharedPlayerIds());
        if (remove) {
            shared.remove(targetId);
        } else {
            if (shared.size()
                    >= LostTalesMapMarkerRecord.MAX_SHARED_PLAYERS) {
                deny(player, "chat.losttales.waystone.share_limit");
                return null;
            }
            shared.add(targetId);
        }
        LostTalesMapMarkerVisibility visibility =
                record.getVisibility();
        if (!remove
                && visibility == LostTalesMapMarkerVisibility.PRIVATE) {
            visibility = LostTalesMapMarkerVisibility.SHARED;
        }
        return record.withSharedPlayers(shared, visibility);
    }

    private static LostTalesMapMarkerRecord
    applyFellowshipSharing(
            EntityPlayerMP player,
            LostTalesMapMarkerRecord record,
            String fellowshipName, boolean remove) {
        LOTRFellowship fellowship = resolveFellowship(
                player, record, fellowshipName, remove);
        if (fellowship == null
                || fellowship.getFellowshipID() == null
                || !remove && fellowship.isDisbanded()) {
            deny(player,
                    "chat.losttales.waystone.fellowship_not_found");
            return null;
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
                deny(player,
                        "chat.losttales.waystone.share_limit");
                return null;
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
        return record.withSharedFellowships(
                shared, visibility);
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

    private static UUID findPlayerId(String playerName) {
        String normalized =
                playerName == null ? "" : playerName.trim();
        MinecraftServer server = MinecraftServer.getServer();
        if (normalized.length() == 0 || server == null
                || server.getConfigurationManager() == null
                || server.func_152358_ax() == null) {
            return null;
        }
        GameProfile profile = server.func_152358_ax()
                .func_152655_a(normalized);
        return profile == null ? null : profile.getId();
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
