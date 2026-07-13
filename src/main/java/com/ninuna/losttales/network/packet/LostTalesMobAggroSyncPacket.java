package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.entity.combat.LostTalesCombatEngagement;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-to-client replacement snapshot of entities engaged with this party. */
public class LostTalesMobAggroSyncPacket implements IMessage {
    public static final int MAX_ENTITY_IDS = 512;
    public static final int MIN_TRACKING_RADIUS = 8;
    public static final int MAX_TRACKING_RADIUS = 128;
    public static final int MAX_ENTITY_NAME_BYTES = 128;
    private static final int HEADER_BYTES = 16;
    private static final double MAX_WORLD_COORDINATE = 30000000.0D;
    private static final double MAX_VERTICAL_COORDINATE = 4096.0D;

    private int dimensionId;
    private int sequence;
    private int trackingRadius;
    private boolean malformed;
    private final List<Entry> entries = new ArrayList<Entry>();

    public LostTalesMobAggroSyncPacket() {}

    public LostTalesMobAggroSyncPacket(int dimensionId, int sequence,
                                       int trackingRadius,
                                       Collection<Entry> entries) {
        this.dimensionId = dimensionId;
        this.sequence = Math.max(0, sequence);
        this.trackingRadius = clampRadius(trackingRadius);
        if (entries != null) {
            Set<Integer> seenEntityIds = new HashSet<Integer>();
            for (Entry entry : entries) {
                if (entry != null && entry.isValid()
                        && seenEntityIds.add(Integer.valueOf(
                        entry.getEntityId()))) {
                    this.entries.add(entry);
                    if (this.entries.size() >= MAX_ENTITY_IDS) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.entries.clear();
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() < HEADER_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "truncated combat marker packet");
            }
            this.dimensionId = buffer.readInt();
            this.sequence = buffer.readInt();
            this.trackingRadius = buffer.readInt();
            int count = buffer.readInt();
            if (this.sequence < 0
                    || this.trackingRadius < MIN_TRACKING_RADIUS
                    || this.trackingRadius > MAX_TRACKING_RADIUS
                    || count < 0 || count > MAX_ENTITY_IDS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid combat marker header");
            }

            Set<Integer> seenEntityIds = new HashSet<Integer>();
            for (int index = 0; index < count; index++) {
                int entityId = buffer.readInt();
                LostTalesCombatEngagement engagement =
                        LostTalesCombatEngagement.fromNetworkId(
                        buffer.readUnsignedByte());
                boolean sharedFromParty = buffer.readBoolean();
                double x = buffer.readDouble();
                double y = buffer.readDouble();
                double z = buffer.readDouble();
                String name = LostTalesPacketCodec.readUtf8String(
                        buffer, MAX_ENTITY_NAME_BYTES);
                Entry entry = new Entry(entityId, engagement,
                        sharedFromParty, name, x, y, z);
                if (!entry.isValid()
                        || !seenEntityIds.add(Integer.valueOf(entityId))) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid combat marker entry");
                }
                this.entries.add(entry);
            }
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            markMalformed(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        int count = Math.min(this.entries.size(), MAX_ENTITY_IDS);
        buffer.writeInt(this.dimensionId);
        buffer.writeInt(Math.max(0, this.sequence));
        buffer.writeInt(clampRadius(this.trackingRadius));
        buffer.writeInt(count);
        for (int index = 0; index < count; index++) {
            Entry entry = this.entries.get(index);
            buffer.writeInt(entry.getEntityId());
            buffer.writeByte(entry.getEngagement().getNetworkId());
            buffer.writeBoolean(entry.isSharedFromParty());
            buffer.writeDouble(entry.getX());
            buffer.writeDouble(entry.getY());
            buffer.writeDouble(entry.getZ());
            LostTalesPacketCodec.writeUtf8String(
                    buffer, entry.getName(), MAX_ENTITY_NAME_BYTES);
        }
    }

    public int getDimensionId() {
        return this.dimensionId;
    }

    public int getSequence() {
        return this.sequence;
    }

    public int getTrackingRadius() {
        return this.trackingRadius;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(
                new ArrayList<Entry>(this.entries));
    }

    public List<Integer> getEntityIds() {
        List<Integer> ids = new ArrayList<Integer>(this.entries.size());
        for (Entry entry : this.entries) {
            ids.add(Integer.valueOf(entry.getEntityId()));
        }
        return Collections.unmodifiableList(ids);
    }

    private void markMalformed(ByteBuf buffer) {
        this.malformed = true;
        this.entries.clear();
        if (buffer != null && buffer.readableBytes() > 0) {
            buffer.skipBytes(buffer.readableBytes());
        }
    }

    private static int clampRadius(int radius) {
        return Math.max(MIN_TRACKING_RADIUS,
                Math.min(MAX_TRACKING_RADIUS, radius));
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    public static final class Entry {
        private final int entityId;
        private final LostTalesCombatEngagement engagement;
        private final boolean sharedFromParty;
        private final String name;
        private final double x;
        private final double y;
        private final double z;

        public Entry(int entityId, LostTalesCombatEngagement engagement,
                     boolean sharedFromParty, String name,
                     double x, double y, double z) {
            this.entityId = entityId;
            this.engagement = engagement == null
                    ? LostTalesCombatEngagement.NONE : engagement;
            this.sharedFromParty = sharedFromParty;
            String safeName = name == null ? "Enemy" : name.trim();
            this.name = safeName.length() == 0 ? "Enemy" : safeName;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int getEntityId() {
            return this.entityId;
        }

        public LostTalesCombatEngagement getEngagement() {
            return this.engagement;
        }

        public boolean isSharedFromParty() {
            return this.sharedFromParty;
        }

        public String getName() {
            return this.name;
        }

        public double getX() {
            return this.x;
        }

        public double getY() {
            return this.y;
        }

        public double getZ() {
            return this.z;
        }

        private boolean isValid() {
            return this.entityId >= 0
                    && this.engagement != LostTalesCombatEngagement.NONE
                    && isFinite(this.x) && isFinite(this.y) && isFinite(this.z)
                    && Math.abs(this.x) <= MAX_WORLD_COORDINATE
                    && Math.abs(this.z) <= MAX_WORLD_COORDINATE
                    && Math.abs(this.y) <= MAX_VERTICAL_COORDINATE
                    && this.name.getBytes(StandardCharsets.UTF_8).length
                    <= MAX_ENTITY_NAME_BYTES;
        }

        @Override
        public boolean equals(Object value) {
            if (this == value) {
                return true;
            }
            if (!(value instanceof Entry)) {
                return false;
            }
            Entry other = (Entry) value;
            return this.entityId == other.entityId
                    && this.engagement == other.engagement
                    && this.sharedFromParty == other.sharedFromParty
                    && this.name.equals(other.name)
                    && Double.doubleToLongBits(this.x)
                    == Double.doubleToLongBits(other.x)
                    && Double.doubleToLongBits(this.y)
                    == Double.doubleToLongBits(other.y)
                    && Double.doubleToLongBits(this.z)
                    == Double.doubleToLongBits(other.z);
        }

        @Override
        public int hashCode() {
            int result = this.entityId;
            result = 31 * result + this.engagement.hashCode();
            result = 31 * result + (this.sharedFromParty ? 1 : 0);
            result = 31 * result + this.name.hashCode();
            long bits = Double.doubleToLongBits(this.x);
            result = 31 * result + (int) (bits ^ bits >>> 32);
            bits = Double.doubleToLongBits(this.y);
            result = 31 * result + (int) (bits ^ bits >>> 32);
            bits = Double.doubleToLongBits(this.z);
            result = 31 * result + (int) (bits ^ bits >>> 32);
            return result;
        }
    }

    /** Common-safe clientbound handler; actual client work is queued by the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesMobAggroSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesMobAggroSyncPacket message,
                                  MessageContext context) {
            if (LostTalesMod.proxy != null) {
                LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                    @Override
                    public void run() {
                        if (LostTalesMod.proxy != null) {
                            LostTalesMod.proxy.handleMobAggroSync(message);
                        }
                    }
                });
            }
            return null;
        }
    }
}
