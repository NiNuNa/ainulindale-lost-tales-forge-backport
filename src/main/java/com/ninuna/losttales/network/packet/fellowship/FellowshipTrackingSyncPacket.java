package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.sync.FellowshipGoHereMarkerSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackedMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipTrackingSnapshot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded server-authoritative fellowship positions and Go Here markers. */
public final class FellowshipTrackingSyncPacket implements IMessage {

    private FellowshipTrackingSnapshot snapshot;
    private boolean malformed;

    public FellowshipTrackingSyncPacket() {}

    public FellowshipTrackingSyncPacket(FellowshipTrackingSnapshot snapshot) {
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
                        "invalid fellowship tracking sequence");
            }
            boolean hasFellowship = buffer.readBoolean();
            UUID fellowshipId = null;
            long fellowshipRevision = -1L;
            List<FellowshipTrackedMemberSnapshot> members =
                    new ArrayList<FellowshipTrackedMemberSnapshot>();
            if (hasFellowship) {
                fellowshipId = LostTalesPacketCodec.readUuid(buffer);
                fellowshipRevision = buffer.readLong();
                if (fellowshipRevision < 0L) {
                    throw new FellowshipPacketCodec.DecodeException(
                            "invalid fellowship tracking revision");
                }

                int memberCount = buffer.readUnsignedByte();
                if (memberCount > Fellowship.MAX_MEMBERS) {
                    throw new FellowshipPacketCodec.DecodeException(
                            "too many tracked fellowship members");
                }
                Set<UUID> memberIds = new HashSet<UUID>();
                for (int index = 0; index < memberCount; index++) {
                    UUID identityId = LostTalesPacketCodec.readUuid(buffer);
                    if (!memberIds.add(identityId)) {
                        throw new FellowshipPacketCodec.DecodeException(
                                "duplicate tracked member identity");
                    }
                    String name = FellowshipPacketCodec.shownName(
                            LostTalesPacketCodec.readUtf8String(
                                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES));
                    FellowshipColor color = FellowshipColor.fromNetworkId(
                            buffer.readUnsignedByte());
                    if (color == null) {
                        throw new FellowshipPacketCodec.DecodeException(
                                "invalid tracked member color");
                    }
                    members.add(new FellowshipTrackedMemberSnapshot(
                            identityId,
                            name,
                            color,
                            buffer.readInt(),
                            buffer.readDouble(),
                            buffer.readDouble(),
                            buffer.readDouble()));
                }
            }

            int markerCount = buffer.readUnsignedByte();
            if (markerCount > Fellowship.MAX_MEMBERS) {
                throw new FellowshipPacketCodec.DecodeException(
                        "too many Go Here markers");
            }
            List<FellowshipGoHereMarkerSnapshot> markers =
                    new ArrayList<FellowshipGoHereMarkerSnapshot>(markerCount);
            Set<UUID> markerOwners = new HashSet<UUID>();
            for (int index = 0; index < markerCount; index++) {
                UUID identityId = LostTalesPacketCodec.readUuid(buffer);
                if (!markerOwners.add(identityId)) {
                    throw new FellowshipPacketCodec.DecodeException(
                            "duplicate marker owner identity");
                }
                String name = FellowshipPacketCodec.shownName(
                        LostTalesPacketCodec.readUtf8String(
                                buffer, FellowshipPacketCodec.MAX_NAME_BYTES));
                FellowshipColor color = FellowshipColor.fromNetworkId(
                        buffer.readUnsignedByte());
                if (color == null) {
                    throw new FellowshipPacketCodec.DecodeException(
                            "invalid marker owner color");
                }
                markers.add(new FellowshipGoHereMarkerSnapshot(
                        identityId,
                        name,
                        color,
                        buffer.readInt(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readDouble(),
                        buffer.readLong()));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.snapshot = hasFellowship
                    ? new FellowshipTrackingSnapshot(
                    ownerId, sequence, activeIdentityId,
                    fellowshipId, fellowshipRevision, members, markers)
                    : FellowshipTrackingSnapshot.noFellowship(
                    ownerId, sequence, activeIdentityId, markers);
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
        if (this.snapshot.hasFellowship()) {
            LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getFellowshipId());
            buffer.writeLong(this.snapshot.getFellowshipRevision());
            buffer.writeByte(this.snapshot.getTrackedMembers().size());
            for (FellowshipTrackedMemberSnapshot member
                    : this.snapshot.getTrackedMembers()) {
                LostTalesPacketCodec.writeUuid(buffer, member.getIdentityId());
                LostTalesPacketCodec.writeUtf8String(buffer,
                        member.getCharacterName(),
                        FellowshipPacketCodec.MAX_NAME_BYTES);
                buffer.writeByte(member.getColor().getNetworkId());
                buffer.writeInt(member.getDimensionId());
                buffer.writeDouble(member.getX());
                buffer.writeDouble(member.getY());
                buffer.writeDouble(member.getZ());
            }
        }
        buffer.writeByte(this.snapshot.getGoHereMarkers().size());
        for (FellowshipGoHereMarkerSnapshot marker
                : this.snapshot.getGoHereMarkers()) {
            LostTalesPacketCodec.writeUuid(
                    buffer, marker.getOwnerIdentityId());
            LostTalesPacketCodec.writeUtf8String(buffer,
                    marker.getOwnerCharacterName(),
                    FellowshipPacketCodec.MAX_NAME_BYTES);
            buffer.writeByte(marker.getOwnerColor().getNetworkId());
            buffer.writeInt(marker.getDimensionId());
            buffer.writeDouble(marker.getX());
            buffer.writeDouble(marker.getY());
            buffer.writeDouble(marker.getZ());
            buffer.writeLong(marker.getUpdatedAt());
        }
    }

    public FellowshipTrackingSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements
            IMessageHandler<FellowshipTrackingSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final FellowshipTrackingSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleFellowshipTrackingSync(message);
                }
            });
            return null;
        }
    }
}
