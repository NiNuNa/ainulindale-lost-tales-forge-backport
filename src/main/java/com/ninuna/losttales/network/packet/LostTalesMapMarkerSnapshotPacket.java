package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/** Server-authoritative active marker definitions visible to one player. */
public final class LostTalesMapMarkerSnapshotPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 2 * 1024 * 1024;
    private static final int MAX_MARKERS = 4096;
    private static final int MAX_ID_BYTES = 256;
    private static final int MAX_NAME_BYTES = 1024;
    private static final int MAX_TEXT_BYTES = 8192;

    private final List<LostTalesMapMarkerDefinition> markers =
            new ArrayList<LostTalesMapMarkerDefinition>();
    private boolean malformed;

    public LostTalesMapMarkerSnapshotPacket() {}

    public LostTalesMapMarkerSnapshotPacket(
            Collection<LostTalesMapMarkerDefinition> markers) {
        if (markers != null) {
            for (LostTalesMapMarkerDefinition marker : markers) {
                if (marker != null) {
                    this.markers.add(marker);
                }
            }
        }
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.markers.clear();
        this.malformed = false;
        try {
            if (buffer == null
                    || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid map marker snapshot size");
            }
            int count = LostTalesPacketCodec.readCount(
                    buffer, MAX_MARKERS, "map marker");
            for (int index = 0; index < count; index++) {
                String id = readId(buffer);
                String name = readName(buffer);
                String icon = readId(buffer);
                String color = readId(buffer);
                String category = readName(buffer);
                String description = readText(buffer);
                boolean fastTravel = buffer.readBoolean();
                int dimensionId = buffer.readInt();
                double x = buffer.readDouble();
                double y = buffer.readDouble();
                double z = buffer.readDouble();
                double compassRadius = buffer.readDouble();
                double discoveryRadius = buffer.readDouble();
                boolean hidden = buffer.readBoolean();
                boolean discoverable = buffer.readBoolean();
                boolean requiresRegion = buffer.readBoolean();
                LostTalesMapMarkerSource source =
                        LostTalesMapMarkerSource.forSerializedName(
                                readId(buffer),
                                LostTalesMapMarkerSource.CUSTOM_PRESET);
                boolean hasWaystone = buffer.readBoolean();
                String structureType = readId(buffer);
                if (!isFinite(x) || !isFinite(y) || !isFinite(z)
                        || !isFinite(compassRadius)
                        || !isFinite(discoveryRadius)
                        || compassRadius < 0.0D
                        || discoveryRadius < 0.0D) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid map marker coordinates");
                }
                this.markers.add(new LostTalesMapMarkerDefinition(
                        id, name, icon, color, category, description,
                        fastTravel, dimensionId,
                        x, y, z, compassRadius, discoveryRadius,
                        hidden, discoverable, requiresRegion,
                        source, hasWaystone, structureType));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.markers.clear();
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        int start = buffer.writerIndex();
        LostTalesPacketCodec.writeCount(
                buffer, this.markers.size(), MAX_MARKERS, "map marker");
        for (LostTalesMapMarkerDefinition marker : this.markers) {
            writeId(buffer, marker.getId());
            writeName(buffer, marker.getName());
            writeId(buffer, marker.getIconName());
            writeId(buffer, marker.getColorName());
            writeName(buffer, marker.getCategoryName());
            writeText(buffer, marker.getDescription());
            buffer.writeBoolean(marker.hasFastTravel());
            buffer.writeInt(marker.getDimensionId());
            buffer.writeDouble(marker.getX());
            buffer.writeDouble(marker.getY());
            buffer.writeDouble(marker.getZ());
            buffer.writeDouble(marker.getCompassFadeInRadius());
            buffer.writeDouble(marker.getDiscoveryRadius());
            buffer.writeBoolean(marker.isHiddenUntilDiscovered());
            buffer.writeBoolean(marker.isDiscoverable());
            buffer.writeBoolean(marker.requiresRegionUnlock());
            writeId(buffer, marker.getSource().getSerializedName());
            buffer.writeBoolean(marker.hasWaystone());
            writeId(buffer, marker.getWaystoneStructureType());
        }
        if (buffer.writerIndex() - start > MAX_PACKET_BYTES) {
            throw new IllegalStateException(
                    "map marker snapshot exceeds packet limit");
        }
    }

    public List<LostTalesMapMarkerDefinition> getMarkers() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesMapMarkerDefinition>(this.markers));
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private void validate() {
        if (this.markers.size() > MAX_MARKERS) {
            throw new IllegalArgumentException("too many map markers");
        }
        for (LostTalesMapMarkerDefinition marker : this.markers) {
            if (marker == null || marker.getId().length() == 0
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            marker.getId(), MAX_ID_BYTES)
                    || !isFinite(marker.getX())
                    || !isFinite(marker.getY())
                    || !isFinite(marker.getZ())
                    || !isFinite(marker.getDiscoveryRadius())
                    || marker.getDiscoveryRadius() < 0.0D) {
                throw new IllegalArgumentException(
                        "invalid map marker snapshot entry");
            }
        }
    }

    private static String readId(ByteBuf buffer) {
        return LostTalesPacketCodec.readUtf8String(
                buffer, MAX_ID_BYTES);
    }

    private static String readName(ByteBuf buffer) {
        return LostTalesPacketCodec.readUtf8String(
                buffer, MAX_NAME_BYTES);
    }

    private static String readText(ByteBuf buffer) {
        return LostTalesPacketCodec.readUtf8String(
                buffer, MAX_TEXT_BYTES);
    }

    private static void writeId(ByteBuf buffer, String value) {
        LostTalesPacketCodec.writeUtf8String(
                buffer, value == null ? "" : value, MAX_ID_BYTES);
    }

    private static void writeName(ByteBuf buffer, String value) {
        LostTalesPacketCodec.writeUtf8String(
                buffer, value == null ? "" : value, MAX_NAME_BYTES);
    }

    private static void writeText(ByteBuf buffer, String value) {
        LostTalesPacketCodec.writeUtf8String(
                buffer, value == null ? "" : value, MAX_TEXT_BYTES);
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Handler
            implements IMessageHandler<
                    LostTalesMapMarkerSnapshotPacket, IMessage> {
        @Override
        public IMessage onMessage(
                final LostTalesMapMarkerSnapshotPacket message,
                MessageContext context) {
            if (message != null && !message.isMalformed()
                    && LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy
                                    .handleMapMarkerSnapshot(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
