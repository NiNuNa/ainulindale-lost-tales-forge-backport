package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerEditableSettings;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerVisibility;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Authoritative state for the open waystone settings screen. */
public final class LostTalesWaystoneStatePacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 16384;
    private static final int MAX_MARKER_ID_BYTES = 1024;
    private static final int MAX_TEXT_BYTES = 1024;
    private static final int MAX_DESCRIPTION_BYTES = 4096;
    private static final int MAX_STRUCTURE_ID_BYTES = 512;

    private int dimensionId;
    private int x;
    private int y;
    private int z;
    private String markerId = "";
    private long revision;
    private LostTalesMapMarkerEditableSettings settings;
    private int sharedPlayerCount;
    private int sharedFellowshipCount;
    private boolean canEdit;
    private boolean canMakePublic;
    private boolean malformed;

    public LostTalesWaystoneStatePacket() {}

    public LostTalesWaystoneStatePacket(
            int dimensionId, int x, int y, int z,
            LostTalesMapMarkerRecord record,
            boolean canEdit, boolean canMakePublic) {
        if (record == null) {
            throw new IllegalArgumentException(
                    "waystone state requires a marker record");
        }
        this.dimensionId = dimensionId;
        this.x = x;
        this.y = y;
        this.z = z;
        this.markerId = record.getId();
        this.revision = record.getRevision();
        this.settings =
                LostTalesMapMarkerEditableSettings.fromRecord(record);
        this.sharedPlayerCount = record.getSharedPlayerIds().size();
        this.sharedFellowshipCount =
                record.getSharedFellowshipIds().size();
        this.canEdit = canEdit;
        this.canMakePublic = canMakePublic;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null
                    || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid waystone state packet size");
            }
            this.dimensionId = buffer.readInt();
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.markerId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_MARKER_ID_BYTES);
            this.revision = buffer.readLong();
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
            this.settings = new LostTalesMapMarkerEditableSettings(
                    name, icon, color, category, description,
                    fastTravel, markerDimensionId,
                    markerX, markerY, markerZ,
                    compassRadius, discoveryRadius,
                    hiddenUntilDiscovered, discoverable,
                    requiresRegionUnlock, hasWaystone,
                    structureType, visibility);
            this.sharedPlayerCount = buffer.readInt();
            this.sharedFellowshipCount = buffer.readInt();
            this.canEdit = buffer.readBoolean();
            this.canMakePublic = buffer.readBoolean();
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
        buffer.writeInt(this.dimensionId);
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.markerId, MAX_MARKER_ID_BYTES);
        buffer.writeLong(this.revision);
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
        buffer.writeInt(this.sharedPlayerCount);
        buffer.writeInt(this.sharedFellowshipCount);
        buffer.writeBoolean(this.canEdit);
        buffer.writeBoolean(this.canMakePublic);
    }

    private void validate() {
        if (!LostTalesPacketCodec.isValidBlockPosition(
                this.x, this.y, this.z)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.markerId, MAX_MARKER_ID_BYTES)
                || this.markerId.length() == 0
                || this.revision < 1L
                || !isValidSettings(this.settings)
                || this.sharedPlayerCount < 0
                || this.sharedPlayerCount
                        > LostTalesMapMarkerRecord.MAX_SHARED_PLAYERS
                || this.sharedFellowshipCount < 0
                || this.sharedFellowshipCount
                        > LostTalesMapMarkerRecord
                                .MAX_SHARED_FELLOWSHIPS) {
            throw new IllegalArgumentException(
                    "invalid waystone state");
        }
    }

    public int getDimensionId() { return this.dimensionId; }
    public int getX() { return this.x; }
    public int getY() { return this.y; }
    public int getZ() { return this.z; }
    public String getMarkerId() { return this.markerId; }
    public long getRevision() { return this.revision; }
    public LostTalesMapMarkerEditableSettings getSettings() {
        return this.settings;
    }
    public String getName() { return this.settings.getName(); }
    public String getIconName() { return this.settings.getIconName(); }
    public String getColor() { return this.settings.getColorName(); }
    public String getCategoryName() {
        return this.settings.getCategoryName();
    }
    public String getDescription() {
        return this.settings.getDescription();
    }
    public boolean hasFastTravel() {
        return this.settings.hasFastTravel();
    }
    public int getMarkerDimensionId() {
        return this.settings.getDimensionId();
    }
    public double getMarkerX() { return this.settings.getX(); }
    public double getMarkerY() { return this.settings.getY(); }
    public double getMarkerZ() { return this.settings.getZ(); }
    public double getCompassFadeInRadius() {
        return this.settings.getCompassFadeInRadius();
    }
    public double getDiscoveryRadius() {
        return this.settings.getDiscoveryRadius();
    }
    public LostTalesMapMarkerVisibility getVisibility() {
        return this.settings.getVisibility();
    }
    public boolean isHiddenUntilDiscovered() {
        return this.settings.isHiddenUntilDiscovered();
    }
    public boolean isDiscoverable() {
        return this.settings.isDiscoverable();
    }
    public boolean requiresRegionUnlock() {
        return this.settings.requiresRegionUnlock();
    }
    public boolean hasWaystone() {
        return this.settings.hasWaystone();
    }
    public String getWaystoneStructureType() {
        return this.settings.getWaystoneStructureType();
    }
    public int getSharedPlayerCount() {
        return this.sharedPlayerCount;
    }
    public int getSharedFellowshipCount() {
        return this.sharedFellowshipCount;
    }
    public boolean canEdit() { return this.canEdit; }
    public boolean canMakePublic() {
        return this.canMakePublic;
    }
    public boolean isMalformed() { return this.malformed; }

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
                && finiteCoordinate(value.getX())
                && finiteCoordinate(value.getY())
                && finiteCoordinate(value.getZ())
                && finiteRadius(value.getCompassFadeInRadius())
                && finiteRadius(value.getDiscoveryRadius());
    }

    private static boolean finiteCoordinate(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && Math.abs(value)
                        <= LostTalesMapMarkerRecord.MAX_ABSOLUTE_COORDINATE;
    }

    private static boolean finiteRadius(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value)
                && value >= 0.0D
                && value <= LostTalesMapMarkerRecord.MAX_RADIUS;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesWaystoneStatePacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesWaystoneStatePacket message,
                MessageContext context) {
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleWaystoneState(message);
                }
            });
            return null;
        }
    }
}
