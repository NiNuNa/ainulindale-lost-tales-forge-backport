package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.entity.combat.LostTalesCombatEngagement;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Server-to-client replacement snapshot of entities engaged with this player. */
public class LostTalesMobAggroSyncPacket implements IMessage {
    public static final int MAX_ENTITY_IDS = 512;
    public static final int MIN_TRACKING_RADIUS = 8;
    public static final int MAX_TRACKING_RADIUS = 128;
    private static final int HEADER_BYTES = 16;
    private static final int ENTRY_BYTES = 5;

    private int dimensionId;
    private int sequence;
    private int trackingRadius;
    private boolean malformed;
    private final List<Entry> entries = new ArrayList<Entry>();

    public LostTalesMobAggroSyncPacket() {}

    public LostTalesMobAggroSyncPacket(int dimensionId, int sequence, int trackingRadius, Collection<Entry> entries) {
        this.dimensionId = dimensionId;
        this.sequence = Math.max(0, sequence);
        this.trackingRadius = Math.max(MIN_TRACKING_RADIUS, Math.min(MAX_TRACKING_RADIUS, trackingRadius));
        if (entries != null) {
            Set<Integer> seenEntityIds = new HashSet<Integer>();
            for (Entry entry : entries) {
                if (entry != null && entry.getEntityId() >= 0
                        && entry.getEngagement() != LostTalesCombatEngagement.NONE
                        && seenEntityIds.add(Integer.valueOf(entry.getEntityId()))) {
                    this.entries.add(entry);
                    if (this.entries.size() >= MAX_ENTITY_IDS) {
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.entries.clear();
        this.malformed = false;
        if (buf == null || buf.readableBytes() < HEADER_BYTES) {
            markMalformed(buf);
            return;
        }

        this.dimensionId = buf.readInt();
        this.sequence = buf.readInt();
        this.trackingRadius = buf.readInt();
        int count = buf.readInt();
        if (this.sequence < 0 || this.trackingRadius < MIN_TRACKING_RADIUS || this.trackingRadius > MAX_TRACKING_RADIUS
                || count < 0 || count > MAX_ENTITY_IDS
                || buf.readableBytes() < count * ENTRY_BYTES) {
            markMalformed(buf);
            return;
        }

        Set<Integer> seenEntityIds = new HashSet<Integer>();
        for (int i = 0; i < count; i++) {
            int entityId = buf.readInt();
            LostTalesCombatEngagement engagement = LostTalesCombatEngagement.fromNetworkId(buf.readUnsignedByte());
            if (entityId < 0 || engagement == LostTalesCombatEngagement.NONE
                    || !seenEntityIds.add(Integer.valueOf(entityId))) {
                this.malformed = true;
                continue;
            }
            this.entries.add(new Entry(entityId, engagement));
        }
    }

    @Override
    public void toBytes(ByteBuf buf) {
        int count = Math.min(this.entries.size(), MAX_ENTITY_IDS);
        buf.writeInt(this.dimensionId);
        buf.writeInt(Math.max(0, this.sequence));
        buf.writeInt(Math.max(MIN_TRACKING_RADIUS, Math.min(MAX_TRACKING_RADIUS, this.trackingRadius)));
        buf.writeInt(count);
        for (int i = 0; i < count; i++) {
            Entry entry = this.entries.get(i);
            buf.writeInt(entry.getEntityId());
            buf.writeByte(entry.getEngagement().getNetworkId());
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
        return Collections.unmodifiableList(new ArrayList<Entry>(this.entries));
    }

    public List<Integer> getEntityIds() {
        List<Integer> ids = new ArrayList<Integer>(this.entries.size());
        for (Entry entry : this.entries) {
            ids.add(Integer.valueOf(entry.getEntityId()));
        }
        return Collections.unmodifiableList(ids);
    }

    private void markMalformed(ByteBuf buf) {
        this.malformed = true;
        this.entries.clear();
        if (buf != null && buf.readableBytes() > 0) {
            buf.skipBytes(buf.readableBytes());
        }
    }

    public static final class Entry {
        private final int entityId;
        private final LostTalesCombatEngagement engagement;

        public Entry(int entityId, LostTalesCombatEngagement engagement) {
            this.entityId = entityId;
            this.engagement = engagement == null ? LostTalesCombatEngagement.NONE : engagement;
        }

        public int getEntityId() {
            return this.entityId;
        }

        public LostTalesCombatEngagement getEngagement() {
            return this.engagement;
        }
    }

    /** Common-safe clientbound handler; actual client work is queued by the sided proxy. */
    public static class Handler implements IMessageHandler<LostTalesMobAggroSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesMobAggroSyncPacket message, MessageContext ctx) {
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
