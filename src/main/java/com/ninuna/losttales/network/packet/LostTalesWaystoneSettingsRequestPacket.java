package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
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

/**
 * A request from a waystone's page, named by its block, its marker and the
 * revision the page read: Save with the settings the page edits, a share,
 * an unshare or a hand over with the name it names, or Destinations with
 * nothing more. Never a serialized marker record.
 */
public final class LostTalesWaystoneSettingsRequestPacket
        implements IMessage {
    private static final int MAX_PACKET_BYTES = 16384;
    private static final int MAX_MARKER_ID_BYTES = 1024;
    private static final int MAX_TEXT_BYTES = 1024;
    private static final int MAX_DESCRIPTION_BYTES = 4096;
    private static final int MAX_TARGET_NAME_BYTES = 128;

    private LostTalesWaystoneSettingsOperation operation;
    private int x;
    private int y;
    private int z;
    private String markerId = "";
    private long expectedRevision;
    private LostTalesMapMarkerEditableSettings settings;
    /** The character or fellowship a share names, or the character a hand over names. */
    private String targetName = "";
    private boolean malformed;

    public LostTalesWaystoneSettingsRequestPacket() {}

    public static LostTalesWaystoneSettingsRequestPacket save(
            int x, int y, int z, String markerId, long expectedRevision,
            LostTalesMapMarkerEditableSettings settings) {
        LostTalesWaystoneSettingsRequestPacket packet = request(
                LostTalesWaystoneSettingsOperation.SAVE,
                x, y, z, markerId, expectedRevision);
        packet.settings = settings;
        packet.validate();
        return packet;
    }

    public static LostTalesWaystoneSettingsRequestPacket share(
            boolean remove, int x, int y, int z,
            String markerId, long expectedRevision,
            String characterName) {
        return named(remove
                        ? LostTalesWaystoneSettingsOperation.UNSHARE_CHARACTER
                        : LostTalesWaystoneSettingsOperation.SHARE_CHARACTER,
                x, y, z, markerId, expectedRevision, characterName);
    }

    public static LostTalesWaystoneSettingsRequestPacket
    shareFellowship(
            boolean remove, int x, int y, int z,
            String markerId, long expectedRevision,
            String fellowshipName) {
        return named(remove
                        ? LostTalesWaystoneSettingsOperation.UNSHARE_FELLOWSHIP
                        : LostTalesWaystoneSettingsOperation.SHARE_FELLOWSHIP,
                x, y, z, markerId, expectedRevision, fellowshipName);
    }

    /** A manager's request that the character named own the waystone. */
    public static LostTalesWaystoneSettingsRequestPacket handOver(
            int x, int y, int z, String markerId, long expectedRevision,
            String characterName) {
        return named(LostTalesWaystoneSettingsOperation.HAND_OVER,
                x, y, z, markerId, expectedRevision, characterName);
    }

    /** Destinations pressed: the server opens the window to travel from the waystone. */
    public static LostTalesWaystoneSettingsRequestPacket destinations(
            int x, int y, int z, String markerId, long expectedRevision) {
        LostTalesWaystoneSettingsRequestPacket packet = request(
                LostTalesWaystoneSettingsOperation.DESTINATIONS,
                x, y, z, markerId, expectedRevision);
        packet.validate();
        return packet;
    }

    private static LostTalesWaystoneSettingsRequestPacket named(
            LostTalesWaystoneSettingsOperation operation,
            int x, int y, int z, String markerId, long expectedRevision,
            String name) {
        LostTalesWaystoneSettingsRequestPacket packet = request(
                operation, x, y, z, markerId, expectedRevision);
        packet.targetName = name == null ? "" : name;
        packet.validate();
        return packet;
    }

    private static LostTalesWaystoneSettingsRequestPacket request(
            LostTalesWaystoneSettingsOperation operation,
            int x, int y, int z, String markerId, long expectedRevision) {
        LostTalesWaystoneSettingsRequestPacket packet =
                new LostTalesWaystoneSettingsRequestPacket();
        packet.operation = operation;
        packet.x = x;
        packet.y = y;
        packet.z = z;
        packet.markerId = markerId == null ? "" : markerId;
        packet.expectedRevision = expectedRevision;
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
            this.settings = null;
            this.targetName = "";
            if (this.operation
                    == LostTalesWaystoneSettingsOperation.SAVE) {
                this.settings = readSettings(buffer);
            } else if (this.operation
                    != LostTalesWaystoneSettingsOperation.DESTINATIONS) {
                this.targetName =
                        LostTalesPacketCodec.readUtf8String(
                                buffer, MAX_TARGET_NAME_BYTES);
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
            writeSettings(buffer, this.settings);
        } else if (this.operation
                != LostTalesWaystoneSettingsOperation.DESTINATIONS) {
            LostTalesPacketCodec.writeUtf8String(
                    buffer, this.targetName,
                    MAX_TARGET_NAME_BYTES);
        }
    }

    private void validate() {
        if (this.operation == null || this.markerId == null
                || this.markerId.length() == 0
                || this.expectedRevision < 1L
                || !LostTalesPacketCodec.isValidBlockPosition(
                        this.x, this.y, this.z)) {
            throw new IllegalArgumentException(
                    "invalid waystone request context");
        }
        if (this.operation
                == LostTalesWaystoneSettingsOperation.SAVE) {
            if (!isValidSettings(this.settings)) {
                throw new IllegalArgumentException(
                        "invalid waystone settings");
            }
        } else if (this.operation
                != LostTalesWaystoneSettingsOperation.DESTINATIONS
                && (this.targetName == null
                    || this.targetName.trim().length() == 0
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            this.targetName, MAX_TARGET_NAME_BYTES))) {
            throw new IllegalArgumentException(
                    "missing or overlong shared name");
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
    public String getTargetName() {
        return this.targetName;
    }
    public boolean isMalformed() { return this.malformed; }

    /* ---- The settings, as both waystone packets carry them ---- */

    static void writeSettings(ByteBuf buffer,
                              LostTalesMapMarkerEditableSettings value) {
        LostTalesPacketCodec.writeUtf8String(
                buffer, value.getName(), MAX_TEXT_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, value.getIconName(), MAX_TEXT_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, value.getColorName(), MAX_TEXT_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, value.getCategoryName(), MAX_TEXT_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, value.getDescription(), MAX_DESCRIPTION_BYTES);
        buffer.writeBoolean(value.hasFastTravel());
        buffer.writeDouble(value.getCompassFadeInRadius());
        buffer.writeDouble(value.getDiscoveryRadius());
        buffer.writeBoolean(value.isHiddenUntilDiscovered());
        buffer.writeBoolean(value.isDiscoverable());
        buffer.writeBoolean(value.requiresRegionUnlock());
        buffer.writeInt(value.getPriority());
        buffer.writeByte(value.getVisibility().ordinal());
    }

    /** The settings as {@link #writeSettings} writes them; throws for a payload of any other shape. */
    static LostTalesMapMarkerEditableSettings readSettings(ByteBuf buffer) {
        String name = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_TEXT_BYTES);
        String icon = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_TEXT_BYTES);
        String color = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_TEXT_BYTES);
        String category = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_TEXT_BYTES);
        String description = LostTalesPacketCodec.readUtf8String(
                buffer, MAX_DESCRIPTION_BYTES);
        boolean fastTravel = buffer.readBoolean();
        double compassRadius = buffer.readDouble();
        double discoveryRadius = buffer.readDouble();
        boolean hiddenUntilDiscovered = buffer.readBoolean();
        boolean discoverable = buffer.readBoolean();
        boolean requiresRegionUnlock = buffer.readBoolean();
        int priority = buffer.readInt();
        int visibilityId = buffer.readUnsignedByte();
        if (visibilityId >= LostTalesMapMarkerVisibility.values().length) {
            throw new LostTalesPacketCodec.DecodeException(
                    "unknown waystone access");
        }
        return new LostTalesMapMarkerEditableSettings(
                name, icon, color, category, description, fastTravel,
                compassRadius, discoveryRadius, hiddenUntilDiscovered,
                discoverable, requiresRegionUnlock, priority,
                LostTalesMapMarkerVisibility.values()[visibilityId]);
    }

    /**
     * The one check of the settings either waystone packet carries: words
     * within the wire's bounds, both radii between none and the most a
     * marker keeps, a relevance within its range, and an access. The
     * server checks the rest against the waystone.
     */
    static boolean isValidSettings(LostTalesMapMarkerEditableSettings value) {
        return value != null
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
                && isRadius(value.getCompassFadeInRadius())
                && isRadius(value.getDiscoveryRadius())
                && value.getPriority()
                        >= LostTalesMapMarkerDefinition.MIN_PRIORITY
                && value.getPriority()
                        <= LostTalesMapMarkerDefinition.MAX_PRIORITY;
    }

    private static boolean isRadius(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= 0.0D
                && value <= LostTalesMapMarkerRecord.MAX_RADIUS;
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
