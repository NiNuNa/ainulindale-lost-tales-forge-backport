package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyGoHereMarkerSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackedMemberSnapshot;
import com.ninuna.losttales.party.sync.PartyTrackingSnapshot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded server-authoritative party positions and Go Here markers. */
public final class PartyTrackingSyncPacket implements IMessage {

    private PartyTrackingSnapshot snapshot;
    private boolean malformed;

    public PartyTrackingSyncPacket() {}

    public PartyTrackingSyncPacket(PartyTrackingSnapshot snapshot) {
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
                throw new PartyPacketCodec.DecodeException(
                        "invalid party tracking sequence");
            }
            boolean hasParty = buffer.readBoolean();
            UUID partyId = null;
            long partyRevision = -1L;
            List<PartyTrackedMemberSnapshot> members =
                    new ArrayList<PartyTrackedMemberSnapshot>();
            if (hasParty) {
                partyId = LostTalesPacketCodec.readUuid(buffer);
                partyRevision = buffer.readLong();
                if (partyRevision < 0L) {
                    throw new PartyPacketCodec.DecodeException(
                            "invalid party tracking revision");
                }

                int memberCount = buffer.readUnsignedByte();
                if (memberCount > Party.MAX_MEMBERS) {
                    throw new PartyPacketCodec.DecodeException(
                            "too many tracked party members");
                }
                Set<UUID> memberIds = new HashSet<UUID>();
                for (int index = 0; index < memberCount; index++) {
                    UUID identityId = LostTalesPacketCodec.readUuid(buffer);
                    if (!memberIds.add(identityId)) {
                        throw new PartyPacketCodec.DecodeException(
                                "duplicate tracked member identity");
                    }
                    String name = LostTalesPacketCodec.readUtf8String(
                            buffer, PartyPacketCodec.MAX_NAME_BYTES);
                    PartyColor color = PartyColor.fromNetworkId(
                            buffer.readUnsignedByte());
                    if (color == null) {
                        throw new PartyPacketCodec.DecodeException(
                                "invalid tracked member color");
                    }
                    members.add(new PartyTrackedMemberSnapshot(
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
            if (markerCount > Party.MAX_MEMBERS) {
                throw new PartyPacketCodec.DecodeException(
                        "too many Go Here markers");
            }
            List<PartyGoHereMarkerSnapshot> markers =
                    new ArrayList<PartyGoHereMarkerSnapshot>(markerCount);
            Set<UUID> markerOwners = new HashSet<UUID>();
            for (int index = 0; index < markerCount; index++) {
                UUID identityId = LostTalesPacketCodec.readUuid(buffer);
                if (!markerOwners.add(identityId)) {
                    throw new PartyPacketCodec.DecodeException(
                            "duplicate marker owner identity");
                }
                String name = LostTalesPacketCodec.readUtf8String(
                        buffer, PartyPacketCodec.MAX_NAME_BYTES);
                PartyColor color = PartyColor.fromNetworkId(
                        buffer.readUnsignedByte());
                if (color == null) {
                    throw new PartyPacketCodec.DecodeException(
                            "invalid marker owner color");
                }
                markers.add(new PartyGoHereMarkerSnapshot(
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
            this.snapshot = hasParty
                    ? new PartyTrackingSnapshot(
                    ownerId, sequence, activeIdentityId,
                    partyId, partyRevision, members, markers)
                    : PartyTrackingSnapshot.noParty(
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
        buffer.writeBoolean(this.snapshot.hasParty());
        if (this.snapshot.hasParty()) {
            LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getPartyId());
            buffer.writeLong(this.snapshot.getPartyRevision());
            buffer.writeByte(this.snapshot.getTrackedMembers().size());
            for (PartyTrackedMemberSnapshot member
                    : this.snapshot.getTrackedMembers()) {
                LostTalesPacketCodec.writeUuid(buffer, member.getIdentityId());
                LostTalesPacketCodec.writeUtf8String(buffer,
                        member.getCharacterName(),
                        PartyPacketCodec.MAX_NAME_BYTES);
                buffer.writeByte(member.getColor().getNetworkId());
                buffer.writeInt(member.getDimensionId());
                buffer.writeDouble(member.getX());
                buffer.writeDouble(member.getY());
                buffer.writeDouble(member.getZ());
            }
        }
        buffer.writeByte(this.snapshot.getGoHereMarkers().size());
        for (PartyGoHereMarkerSnapshot marker
                : this.snapshot.getGoHereMarkers()) {
            LostTalesPacketCodec.writeUuid(
                    buffer, marker.getOwnerIdentityId());
            LostTalesPacketCodec.writeUtf8String(buffer,
                    marker.getOwnerCharacterName(),
                    PartyPacketCodec.MAX_NAME_BYTES);
            buffer.writeByte(marker.getOwnerColor().getNetworkId());
            buffer.writeInt(marker.getDimensionId());
            buffer.writeDouble(marker.getX());
            buffer.writeDouble(marker.getY());
            buffer.writeDouble(marker.getZ());
            buffer.writeLong(marker.getUpdatedAt());
        }
    }

    public PartyTrackingSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements
            IMessageHandler<PartyTrackingSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final PartyTrackingSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handlePartyTrackingSync(message);
                }
            });
            return null;
        }
    }
}
