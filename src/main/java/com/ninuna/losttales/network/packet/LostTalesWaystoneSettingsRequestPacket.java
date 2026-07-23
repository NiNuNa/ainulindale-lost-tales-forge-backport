package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneSettingsOperation;
import com.ninuna.losttales.mapmarker.LostTalesWaystoneSettingsService;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/** Explicit bounded operations; never accepts a serialized marker record. */
public final class LostTalesWaystoneSettingsRequestPacket
        implements IMessage {
    private static final int MAX_PACKET_BYTES = 16384;
    private static final int MAX_MARKER_ID_BYTES = 1024;
    private static final int MAX_TEXT_BYTES = 1024;
    private static final int MAX_DESCRIPTION_BYTES = 4096;
    private static final int MAX_STRUCTURE_ID_BYTES = 512;
    private static final int MAX_PLAYER_NAME_BYTES = 64;

    private LostTalesWaystoneSettingsOperation operation;
    private int x;
    private int y;
    private int z;
    private String markerId = "";
    private long expectedRevision;
    private LostTalesMapMarkerEditableSettings settings;
    private String targetPlayerName = "";
    private boolean malformed;

    public LostTalesWaystoneSettingsRequestPacket() {}

    public static LostTalesWaystoneSettingsRequestPacket save(
            int x, int y, int z, String markerId, long expectedRevision,
            LostTalesMapMarkerEditableSettings settings) {
        LostTalesWaystoneSettingsRequestPacket packet =
                new LostTalesWaystoneSettingsRequestPacket();
        packet.operation = LostTalesWaystoneSettingsOperation.SAVE;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.markerId = markerId == null ? "" : markerId;
        packet.expectedRevision = expectedRevision;
        packet.settings = settings;
        packet.validate();
        return packet;
    }

    public static LostTalesWaystoneSettingsRequestPacket share(
            boolean remove, int x, int y, int z,
            String markerId, long expectedRevision,
            String playerName) {
        LostTalesWaystoneSettingsRequestPacket packet =
                new LostTalesWaystoneSettingsRequestPacket();
        packet.operation = remove
                ? LostTalesWaystoneSettingsOperation.UNSHARE_PLAYER
                : LostTalesWaystoneSettingsOperation.SHARE_PLAYER;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.markerId = markerId == null ? "" : markerId;
        packet.expectedRevision = expectedRevision;
        packet.targetPlayerName =
                playerName == null ? "" : playerName;
        packet.validate();
        return packet;
    }

    public static LostTalesWaystoneSettingsRequestPacket
    shareFellowship(
            boolean remove, int x, int y, int z,
            String markerId, long expectedRevision,
            String fellowshipName) {
        LostTalesWaystoneSettingsRequestPacket packet =
                new LostTalesWaystoneSettingsRequestPacket();
        packet.operation = remove
                ? LostTalesWaystoneSettingsOperation
                        .UNSHARE_FELLOWSHIP
                : LostTalesWaystoneSettingsOperation
                        .SHARE_FELLOWSHIP;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.markerId = markerId == null ? "" : markerId;
        packet.expectedRevision = expectedRevision;
        packet.targetPlayerName =
                fellowshipName == null ? "" : fellowshipName;
        packet.validate();
        return packet;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null
                    || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid waystone settings packet size");
            }
            this.operation =
                    LostTalesWaystoneSettingsOperation.fromNetworkId(
                            buffer.readUnsignedByte());
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.markerId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_MARKER_ID_BYTES);
            this.expectedRevision = buffer.readLong();
            if (this.operation
                    == LostTalesWaystoneSettingsOperation.SAVE) {
                String name = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_TEXT_BYTES);
                String icon = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_TEXT_BYTES);
                String color = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_TEXT_BYTES);
                String category = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_TEXT_BYTES);
                String description =
                        LostTalesPacketCodec.readUtf8String(
                                buffer, MAX_DESCRIPTION_BYTES);
                boolean fastTravel = buffer.readBoolean();
                int markerDimensionId = buffer.readInt();
                double markerX = buffer.readDouble();
                double markerY = buffer.readDouble();
                double markerZ = buffer.readDouble();
                double compassRadius = buffer.readDouble();
                double discoveryRadius = buffer.readDouble();
                boolean hiddenUntilDiscovered = buffer.readBoolean();
                boolean discoverable = buffer.readBoolean();
                boolean requiresRegionUnlock = buffer.readBoolean();
                boolean hasWaystone = buffer.readBoolean();
                String structureType =
                        LostTalesPacketCodec.readUtf8String(
                                buffer, MAX_STRUCTURE_ID_BYTES);
                int visibilityId = buffer.readUnsignedByte();
                LostTalesMapMarkerVisibility visibility = visibilityId
                        >= LostTalesMapMarkerVisibility.values().length
                        ? null
                        : LostTalesMapMarkerVisibility.values()[visibilityId];
                this.settings =
                        new LostTalesMapMarkerEditableSettings(
                                name, icon, color, category, description,
                                fastTravel, markerDimensionId,
                                markerX, markerY, markerZ,
                                compassRadius, discoveryRadius,
                                hiddenUntilDiscovered, discoverable,
                                requiresRegionUnlock, hasWaystone,
                                structureType, visibility);
                this.targetPlayerName = "";
            } else {
                this.targetPlayerName =
                        LostTalesPacketCodec.readUtf8String(
                                buffer, MAX_PLAYER_NAME_BYTES);
                this.settings = null;
            }
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeByte(this.operation.getNetworkId());
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.markerId, MAX_MARKER_ID_BYTES);
        buffer.writeLong(this.expectedRevision);
        if (this.operation
                == LostTalesWaystoneSettingsOperation.SAVE) {
            LostTalesMapMarkerEditableSettings value = this.settings;
            LostTalesPacketCodec.writeUtf8String(
                    buffer, value.getName(), MAX_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, value.getIconName(), MAX_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, value.getColorName(), MAX_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, value.getCategoryName(), MAX_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(
                    buffer, value.getDescription(),
                    MAX_DESCRIPTION_BYTES);
            buffer.writeBoolean(value.hasFastTravel());
            buffer.writeInt(value.getDimensionId());
            buffer.writeDouble(value.getX());
            buffer.writeDouble(value.getY());
            buffer.writeDouble(value.getZ());
            buffer.writeDouble(value.getCompassFadeInRadius());
            buffer.writeDouble(value.getDiscoveryRadius());
            buffer.writeBoolean(value.isHiddenUntilDiscovered());
            buffer.writeBoolean(value.isDiscoverable());
            buffer.writeBoolean(value.requiresRegionUnlock());
            buffer.writeBoolean(value.hasWaystone());
            LostTalesPacketCodec.writeUtf8String(
                    buffer, value.getWaystoneStructureType(),
                    MAX_STRUCTURE_ID_BYTES);
            buffer.writeByte(value.getVisibility().ordinal());
        } else {
            LostTalesPacketCodec.writeUtf8String(
                    buffer, this.targetPlayerName,
                    MAX_PLAYER_NAME_BYTES);
        }
    }

    private void validate() {
        if (this.operation == null || this.markerId == null
                || this.markerId.length() == 0
                || this.expectedRevision < 1L
                || this.x < -30000000 || this.x > 30000000
                || this.z < -30000000 || this.z > 30000000
                || this.y < -4096 || this.y > 4096) {
            throw new IllegalArgumentException(
                    "invalid waystone request context");
        }
        if (this.operation
                == LostTalesWaystoneSettingsOperation.SAVE) {
            if (!isValidSettings(this.settings)) {
                throw new IllegalArgumentException(
                        "invalid waystone settings");
            }
        } else if (this.targetPlayerName == null
                || this.targetPlayerName.trim().length() == 0) {
            throw new IllegalArgumentException(
                    "missing shared player name");
        }
    }

    public LostTalesWaystoneSettingsOperation getOperation() {
        return this.operation;
    }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    public String getMarkerId() { return this.markerId; }
    public long getExpectedRevision() { return this.expectedRevision; }
    public LostTalesMapMarkerEditableSettings getSettings() {
        return this.settings;
    }
    public String getTargetPlayerName() {
        return this.targetPlayerName;
    }
    public boolean isMalformed() { return this.malformed; }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    private static boolean isValidSettings(
            LostTalesMapMarkerEditableSettings value) {
        return value != null
                && value.getName().trim().length() > 0
                && value.getVisibility() != null
                && LostTalesPacketCodec.isUtf8WithinLimit(
                        value.getName(), MAX_TEXT_BYTES)
                && LostTalesPacketCodec.isUtf8WithinLimit(
                        value.getIconName(), MAX_TEXT_BYTES)
                && LostTalesPacketCodec.isUtf8WithinLimit(
                        value.getColorName(), MAX_TEXT_BYTES)
                && LostTalesPacketCodec.isUtf8WithinLimit(
                        value.getCategoryName(), MAX_TEXT_BYTES)
                && LostTalesPacketCodec.isUtf8WithinLimit(
                        value.getDescription(), MAX_DESCRIPTION_BYTES)
                && LostTalesPacketCodec.isUtf8WithinLimit(
                        value.getWaystoneStructureType(),
                        MAX_STRUCTURE_ID_BYTES)
                && isFinite(value.getX())
                && isFinite(value.getY())
                && isFinite(value.getZ())
                && isFinite(value.getCompassFadeInRadius())
                && isFinite(value.getDiscoveryRadius());
    }

    public static final class Handler implements IMessageHandler<
            LostTalesWaystoneSettingsRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesWaystoneSettingsRequestPacket message,
                MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.WAYSTONE_SETTINGS,
                    message.isMalformed(),
                    "LostTalesWaystoneSettingsRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesWaystoneSettingsService.apply(
                                    livePlayer, message);
                        }
                    });
            return null;
        }
    }
}
