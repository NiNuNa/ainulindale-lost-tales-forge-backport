package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberAvailability;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberStatusSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStatusSnapshot;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded server-authoritative health and availability state for one fellowship. */
public final class FellowshipMemberStatusSyncPacket implements IMessage {

    private FellowshipStatusSnapshot snapshot;
    private boolean malformed;

    public FellowshipMemberStatusSyncPacket() {}

    public FellowshipMemberStatusSyncPacket(FellowshipStatusSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            long sequence = buffer.readLong();
            UUID activeIdentityId = LostTalesPacketCodec.readUuid(buffer);
            if (sequence <= 0L) {
                throw new FellowshipPacketCodec.DecodeException(
                        "invalid fellowship status sequence");
            }

            boolean hasFellowship = buffer.readBoolean();
            if (!hasFellowship) {
                LostTalesPacketCodec.requireFinished(buffer);
                this.snapshot = FellowshipStatusSnapshot.noFellowship(
                        ownerId, sequence, activeIdentityId);
                return;
            }

            UUID fellowshipId = LostTalesPacketCodec.readUuid(buffer);
            long fellowshipRevision = buffer.readLong();
            int count = buffer.readUnsignedByte();
            if (fellowshipRevision < 0L || count <= 0
                    || count > Fellowship.MAX_MEMBERS) {
                throw new FellowshipPacketCodec.DecodeException(
                        "invalid fellowship status header");
            }

            List<FellowshipMemberStatusSnapshot> statuses =
                    new ArrayList<FellowshipMemberStatusSnapshot>(count);
            Set<UUID> identityIds = new HashSet<UUID>();
            for (int index = 0; index < count; index++) {
                UUID identityId = LostTalesPacketCodec.readUuid(buffer);
                FellowshipMemberAvailability availability =
                        FellowshipMemberAvailability.fromNetworkId(
                                buffer.readUnsignedByte());
                if (availability == null || !identityIds.add(identityId)) {
                    throw new FellowshipPacketCodec.DecodeException(
                            "invalid fellowship member status identity");
                }
                int dimensionId = FellowshipMemberStatusSnapshot.NO_DIMENSION;
                float health = 0.0F;
                float maximumHealth = 0.0F;
                ItemStack helmet = null;
                ItemStack heldItem = null;
                if (availability.hasLiveEntityData()) {
                    dimensionId = buffer.readInt();
                    health = buffer.readFloat();
                    maximumHealth = buffer.readFloat();
                    helmet = ByteBufUtils.readItemStack(buffer);
                    heldItem = ByteBufUtils.readItemStack(buffer);
                }
                statuses.add(FellowshipMemberStatusSnapshot.decoded(
                        identityId, availability, dimensionId,
                        health, maximumHealth, helmet, heldItem));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            FellowshipStatusSnapshot decoded = new FellowshipStatusSnapshot(
                    ownerId, sequence, activeIdentityId,
                    fellowshipId, fellowshipRevision, statuses);
            if (decoded.getMemberStatuses().size() != count) {
                throw new FellowshipPacketCodec.DecodeException(
                        "inconsistent fellowship member status list");
            }
            this.snapshot = decoded;
        } catch (RuntimeException exception) {
            this.snapshot = null;
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        if (this.snapshot == null) {
            throw new IllegalStateException("snapshot must not be null");
        }
        LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getOwnerId());
        buffer.writeLong(this.snapshot.getSynchronizationSequence());
        LostTalesPacketCodec.writeUuid(
                buffer, this.snapshot.getActiveIdentityId());
        buffer.writeBoolean(this.snapshot.hasFellowship());
        if (!this.snapshot.hasFellowship()) {
            return;
        }

        LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getFellowshipId());
        buffer.writeLong(this.snapshot.getFellowshipRevision());
        buffer.writeByte(this.snapshot.getMemberStatuses().size());
        for (FellowshipMemberStatusSnapshot status
                : this.snapshot.getMemberStatuses()) {
            LostTalesPacketCodec.writeUuid(buffer, status.getIdentityId());
            buffer.writeByte(status.getAvailability().getNetworkId());
            if (status.getAvailability().hasLiveEntityData()) {
                buffer.writeInt(status.getDimensionId());
                buffer.writeFloat(status.getHealth());
                buffer.writeFloat(status.getMaximumHealth());
                ByteBufUtils.writeItemStack(buffer, status.getHelmet());
                ByteBufUtils.writeItemStack(buffer, status.getHeldItem());
            }
        }
    }

    public FellowshipStatusSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements
            IMessageHandler<FellowshipMemberStatusSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final FellowshipMemberStatusSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleFellowshipMemberStatusSync(message);
                }
            });
            return null;
        }
    }
}
