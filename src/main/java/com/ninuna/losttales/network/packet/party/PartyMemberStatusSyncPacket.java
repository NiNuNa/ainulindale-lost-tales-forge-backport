package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.sync.PartyMemberAvailability;
import com.ninuna.losttales.party.sync.PartyMemberStatusSnapshot;
import com.ninuna.losttales.party.sync.PartyStatusSnapshot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Bounded server-authoritative health and availability state for one party. */
public final class PartyMemberStatusSyncPacket implements IMessage {

    private PartyStatusSnapshot snapshot;
    private boolean malformed;

    public PartyMemberStatusSyncPacket() {}

    public PartyMemberStatusSyncPacket(PartyStatusSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            UUID ownerId = PartyPacketCodec.readUuid(buffer);
            long sequence = buffer.readLong();
            UUID activeCharacterId = PartyPacketCodec.readUuid(buffer);
            if (sequence <= 0L) {
                throw new PartyPacketCodec.DecodeException(
                        "invalid party status sequence");
            }

            boolean hasParty = buffer.readBoolean();
            if (!hasParty) {
                PartyPacketCodec.requireFinished(buffer);
                this.snapshot = PartyStatusSnapshot.noParty(
                        ownerId, sequence, activeCharacterId);
                return;
            }

            UUID partyId = PartyPacketCodec.readUuid(buffer);
            long partyRevision = buffer.readLong();
            int count = buffer.readUnsignedByte();
            if (partyRevision < 0L || count <= 0
                    || count > Party.MAX_MEMBERS) {
                throw new PartyPacketCodec.DecodeException(
                        "invalid party status header");
            }

            List<PartyMemberStatusSnapshot> statuses =
                    new ArrayList<PartyMemberStatusSnapshot>(count);
            Set<UUID> characterIds = new HashSet<UUID>();
            for (int index = 0; index < count; index++) {
                UUID characterId = PartyPacketCodec.readUuid(buffer);
                PartyMemberAvailability availability =
                        PartyMemberAvailability.fromNetworkId(
                                buffer.readUnsignedByte());
                if (availability == null || !characterIds.add(characterId)) {
                    throw new PartyPacketCodec.DecodeException(
                            "invalid party member status identity");
                }
                int dimensionId = PartyMemberStatusSnapshot.NO_DIMENSION;
                float health = 0.0F;
                float maximumHealth = 0.0F;
                if (availability.hasLiveEntityData()) {
                    dimensionId = buffer.readInt();
                    health = buffer.readFloat();
                    maximumHealth = buffer.readFloat();
                }
                statuses.add(PartyMemberStatusSnapshot.decoded(
                        characterId, availability, dimensionId,
                        health, maximumHealth));
            }
            PartyPacketCodec.requireFinished(buffer);
            PartyStatusSnapshot decoded = new PartyStatusSnapshot(
                    ownerId, sequence, activeCharacterId,
                    partyId, partyRevision, statuses);
            if (decoded.getMemberStatuses().size() != count) {
                throw new PartyPacketCodec.DecodeException(
                        "inconsistent party member status list");
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
        PartyPacketCodec.writeUuid(buffer, this.snapshot.getOwnerId());
        buffer.writeLong(this.snapshot.getSynchronizationSequence());
        PartyPacketCodec.writeUuid(
                buffer, this.snapshot.getActiveCharacterId());
        buffer.writeBoolean(this.snapshot.hasParty());
        if (!this.snapshot.hasParty()) {
            return;
        }

        PartyPacketCodec.writeUuid(buffer, this.snapshot.getPartyId());
        buffer.writeLong(this.snapshot.getPartyRevision());
        buffer.writeByte(this.snapshot.getMemberStatuses().size());
        for (PartyMemberStatusSnapshot status
                : this.snapshot.getMemberStatuses()) {
            PartyPacketCodec.writeUuid(buffer, status.getCharacterId());
            buffer.writeByte(status.getAvailability().getNetworkId());
            if (status.getAvailability().hasLiveEntityData()) {
                buffer.writeInt(status.getDimensionId());
                buffer.writeFloat(status.getHealth());
                buffer.writeFloat(status.getMaximumHealth());
            }
        }
    }

    public PartyStatusSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements
            IMessageHandler<PartyMemberStatusSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final PartyMemberStatusSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handlePartyMemberStatusSync(message);
                }
            });
            return null;
        }
    }
}
