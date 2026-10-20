package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerDefinition;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerNamedAfter;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerRecord;
import com.ninuna.losttales.mapmarker.LostTalesMapMarkerSource;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * One part of the server-authoritative markers visible to one player
 * (WAY-12). A snapshot goes in parts ({@link #split}): each carries the
 * snapshot's sequence, its own index and how many parts the snapshot has,
 * and at most {@link #MAX_MARKERS_PER_PART} markers in at most
 * {@link #MAX_PART_BYTES} bytes. The client swaps to the new markers once
 * the last part is in. A marker's own words travel as they are; a marker
 * with no name of its own travels with what it is called after, and each
 * client words it.
 */
public final class LostTalesMapMarkerSnapshotPacket implements IMessage {
    /** The most markers one part carries. */
    public static final int MAX_MARKERS_PER_PART = 2000;
    /**
     * The most bytes one part takes, header included: half of what a
     * custom payload may carry to a client (2,097,050 bytes). The widest
     * marker the store holds takes about 7 KiB, so a part of them still
     * carries some 140.
     */
    public static final int MAX_PART_BYTES = 1024 * 1024;
    /** The most parts one snapshot is sent in. */
    public static final int MAX_PARTS = 65536;
    /** The sequence, the index, the part count and the marker count. */
    static final int HEADER_BYTES = 16;
    /**
     * Each marker's switches, world, place, radii and relevance: one byte
     * per switch, four per whole number, eight per decimal.
     */
    static final int FIXED_MARKER_BYTES = 1 + 4 + 8 * 5 + 3 + 1 + 4;
    /**
     * Every text field holds whatever the marker store takes, at three
     * bytes for each UTF-16 unit, so no stored marker fails to write.
     */
    private static final int BYTES_PER_UNIT = 3;
    private static final int MAX_ID_BYTES =
            BYTES_PER_UNIT * LostTalesMapMarkerRecord.MAX_ID_LENGTH;
    private static final int MAX_NAME_BYTES =
            BYTES_PER_UNIT * LostTalesMapMarkerRecord.MAX_NAME_LENGTH;
    private static final int MAX_TEXT_BYTES =
            BYTES_PER_UNIT * LostTalesMapMarkerRecord.MAX_TEXT_LENGTH;
    private static final int MAX_NAMED_AFTER_BYTES =
            BYTES_PER_UNIT * LostTalesMapMarkerNamedAfter.MAX_LENGTH;

    private final List<LostTalesMapMarkerDefinition> markers =
            new ArrayList<LostTalesMapMarkerDefinition>();
    private int sequence;
    private int index;
    private int total;
    private boolean malformed;

    public LostTalesMapMarkerSnapshotPacket() {}

    /**
     * Part {@code index} of the {@code total} parts of snapshot
     * {@code sequence}, carrying {@code markers}.
     */
    public LostTalesMapMarkerSnapshotPacket(
            int sequence, int index, int total,
            Collection<LostTalesMapMarkerDefinition> markers) {
        this.sequence = sequence;
        this.index = index;
        this.total = total;
        if (markers != null) {
            for (LostTalesMapMarkerDefinition marker : markers) {
                if (marker != null) {
                    this.markers.add(marker);
                }
            }
        }
        validate();
    }

    /**
     * Snapshot {@code sequence} of {@code markers} in parts, in order: a
     * part closes at {@link #MAX_MARKERS_PER_PART} markers or before the
     * next marker would take it past {@link #MAX_PART_BYTES}. A snapshot
     * of no markers is one empty part.
     */
    public static List<LostTalesMapMarkerSnapshotPacket> split(
            int sequence, Collection<LostTalesMapMarkerDefinition> markers) {
        List<List<LostTalesMapMarkerDefinition>> groups =
                new ArrayList<List<LostTalesMapMarkerDefinition>>();
        List<LostTalesMapMarkerDefinition> group =
                new ArrayList<LostTalesMapMarkerDefinition>();
        int bytes = HEADER_BYTES;
        if (markers != null) {
            for (LostTalesMapMarkerDefinition marker : markers) {
                if (marker == null) {
                    continue;
                }
                int size = measure(marker);
                if (!group.isEmpty()
                        && (group.size() >= MAX_MARKERS_PER_PART
                            || bytes + size > MAX_PART_BYTES)) {
                    groups.add(group);
                    group = new ArrayList<LostTalesMapMarkerDefinition>();
                    bytes = HEADER_BYTES;
                }
                group.add(marker);
                bytes += size;
            }
        }
        groups.add(group);
        if (groups.size() > MAX_PARTS) {
            throw new IllegalArgumentException(
                    "map marker snapshot needs too many parts");
        }
        List<LostTalesMapMarkerSnapshotPacket> parts =
                new ArrayList<LostTalesMapMarkerSnapshotPacket>(groups.size());
        for (int at = 0; at < groups.size(); at++) {
            parts.add(new LostTalesMapMarkerSnapshotPacket(
                    sequence, at, groups.size(), groups.get(at)));
        }
        return parts;
    }

    /**
     * How many bytes {@code marker} takes in a part, as {@link #toBytes}
     * writes it: never fewer.
     */
    static int measure(LostTalesMapMarkerDefinition marker) {
        return text(marker.getId())
                + text(marker.getName())
                + text(marker.getNamedAfter())
                + text(marker.getIconName())
                + text(marker.getColorName())
                + text(marker.getCategoryName())
                + text(marker.getDescription())
                + text(marker.getSource().getSerializedName())
                + text(marker.getWaystoneStructureType())
                + FIXED_MARKER_BYTES;
    }

    /** How many bytes this part takes, as {@link #measure} counts them. */
    public int measuredBytes() {
        int bytes = HEADER_BYTES;
        for (LostTalesMapMarkerDefinition marker : this.markers) {
            bytes += measure(marker);
        }
        return bytes;
    }

    /** A string's UTF-8 bytes and its length's one or two bytes; a lone surrogate counts three. */
    private static int text(String value) {
        if (value == null) {
            return 1;
        }
        int bytes = 0;
        for (int at = 0; at < value.length(); at++) {
            char unit = value.charAt(at);
            if (unit < 0x80) {
                bytes += 1;
            } else if (unit < 0x800) {
                bytes += 2;
            } else if (Character.isHighSurrogate(unit)
                    && at + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(at + 1))) {
                bytes += 4;
                at++;
            } else {
                bytes += 3;
            }
        }
        return bytes + (bytes < 0x80 ? 1 : 2);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.markers.clear();
        this.malformed = false;
        try {
            if (buffer == null
                    || buffer.readableBytes() > MAX_PART_BYTES
                    || buffer.readableBytes() < HEADER_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid map marker snapshot size");
            }
            this.sequence = buffer.readInt();
            this.index = buffer.readInt();
            this.total = buffer.readInt();
            int count = LostTalesPacketCodec.readCount(
                    buffer, MAX_MARKERS_PER_PART, "map marker");
            for (int at = 0; at < count; at++) {
                String id = readId(buffer);
                String name = readName(buffer);
                String namedAfter = readNamedAfter(buffer);
                String icon = readName(buffer);
                String color = readName(buffer);
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
                int priority = buffer.readInt();
                if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                        || !Double.isFinite(compassRadius)
                        || !Double.isFinite(discoveryRadius)
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
                        source, hasWaystone, structureType, priority,
                        namedAfter));
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
        buffer.writeInt(this.sequence);
        buffer.writeInt(this.index);
        buffer.writeInt(this.total);
        LostTalesPacketCodec.writeCount(buffer, this.markers.size(),
                MAX_MARKERS_PER_PART, "map marker");
        for (LostTalesMapMarkerDefinition marker : this.markers) {
            writeId(buffer, marker.getId());
            writeName(buffer, marker.getName());
            LostTalesPacketCodec.writeUtf8String(buffer,
                    marker.getNamedAfter(), MAX_NAMED_AFTER_BYTES);
            writeName(buffer, marker.getIconName());
            writeName(buffer, marker.getColorName());
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
            buffer.writeInt(marker.getPriority());
        }
        if (buffer.writerIndex() - start > MAX_PART_BYTES) {
            throw new IllegalStateException(
                    "map marker snapshot part exceeds packet limit");
        }
    }

    /** What a marker is called after: nothing, or a reference in its own bounds. */
    private static String readNamedAfter(ByteBuf buffer) {
        String namedAfter = LostTalesPacketCodec.readUtf8String(buffer,
                MAX_NAMED_AFTER_BYTES);
        if (!LostTalesMapMarkerNamedAfter.isValid(namedAfter)) {
            throw new LostTalesPacketCodec.DecodeException(
                    "invalid map marker reference");
        }
        return namedAfter;
    }

    /** The snapshot this part belongs to; a newer one has a later sequence. */
    public int getSequence() {
        return this.sequence;
    }

    /** Which part this is, from 0. */
    public int getIndex() {
        return this.index;
    }

    /** How many parts the snapshot has. */
    public int getTotal() {
        return this.total;
    }

    public List<LostTalesMapMarkerDefinition> getMarkers() {
        return Collections.unmodifiableList(
                new ArrayList<LostTalesMapMarkerDefinition>(this.markers));
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /** A part fits where its index stands among at least one and at most {@link #MAX_PARTS} parts. */
    private void validate() {
        if (this.total < 1 || this.total > MAX_PARTS
                || this.index < 0 || this.index >= this.total) {
            throw new IllegalArgumentException(
                    "map marker snapshot part does not fit its snapshot");
        }
        if (this.markers.size() > MAX_MARKERS_PER_PART) {
            throw new IllegalArgumentException("too many map markers");
        }
        for (LostTalesMapMarkerDefinition marker : this.markers) {
            if (marker == null || marker.getId().length() == 0
                    || !LostTalesPacketCodec.isUtf8WithinLimit(
                            marker.getId(), MAX_ID_BYTES)
                    || !Double.isFinite(marker.getX())
                    || !Double.isFinite(marker.getY())
                    || !Double.isFinite(marker.getZ())
                    || !Double.isFinite(marker.getDiscoveryRadius())
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
