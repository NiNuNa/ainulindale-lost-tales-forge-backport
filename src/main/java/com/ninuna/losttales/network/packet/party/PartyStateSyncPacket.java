package com.ninuna.losttales.network.packet.party;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.sync.PartyInvitationSnapshot;
import com.ninuna.losttales.party.sync.PartyInviteTargetSnapshot;
import com.ninuna.losttales.party.sync.PartyMemberSnapshot;
import com.ninuna.losttales.party.sync.PartySnapshot;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Complete private authoritative party state for one client. */
public final class PartyStateSyncPacket implements IMessage {

    private int requestId;
    private PartyStateSnapshot snapshot;
    private boolean malformed;

    public PartyStateSyncPacket() {}

    public PartyStateSyncPacket(int requestId, PartyStateSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot must not be null");
        }
        this.requestId = requestId;
        this.snapshot = snapshot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.requestId = buffer.readInt();
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            long synchronizationSequence = buffer.readLong();
            PartyErrorId stateErrorId = PartyErrorId.fromId(
                    LostTalesPacketCodec.readUtf8String(
                            buffer, PartyPacketCodec.MAX_ERROR_ID_BYTES));

            if (synchronizationSequence <= 0L) {
                throw new PartyPacketCodec.DecodeException(
                        "invalid synchronization sequence");
            }
            if (stateErrorId != PartyErrorId.NONE) {
                LostTalesPacketCodec.requireFinished(buffer);
                this.snapshot = PartyStateSnapshot.failure(
                        ownerId, synchronizationSequence, stateErrorId);
                return;
            }

            UUID activeCharacterId = LostTalesPacketCodec.readUuid(buffer);
            PartySnapshot party = readParty(buffer);
            boolean incomingTruncated = buffer.readBoolean();
            int incomingCount = buffer.readUnsignedShort();
            if (incomingCount > PartyStateSnapshot.MAX_INCOMING_INVITATIONS) {
                throw new PartyPacketCodec.DecodeException(
                        "too many incoming invitations");
            }
            List<PartyInvitationSnapshot> incoming =
                    readInvitations(buffer, incomingCount);
            boolean outgoingTruncated = buffer.readBoolean();
            int outgoingCount = buffer.readUnsignedShort();
            if (outgoingCount > PartyStateSnapshot.MAX_OUTGOING_INVITATIONS) {
                throw new PartyPacketCodec.DecodeException(
                        "too many outgoing invitations");
            }
            List<PartyInvitationSnapshot> outgoing =
                    readInvitations(buffer, outgoingCount);
            boolean inviteTargetsTruncated = buffer.readBoolean();
            int inviteTargetCount = buffer.readUnsignedShort();
            if (inviteTargetCount > PartyStateSnapshot.MAX_INVITE_TARGETS) {
                throw new PartyPacketCodec.DecodeException(
                        "too many invite targets");
            }
            List<PartyInviteTargetSnapshot> inviteTargets =
                    readInviteTargets(buffer, inviteTargetCount);
            LostTalesPacketCodec.requireFinished(buffer);

            PartyStateSnapshot decoded = new PartyStateSnapshot(
                    ownerId,
                    synchronizationSequence,
                    PartyErrorId.NONE,
                    activeCharacterId,
                    party,
                    incoming,
                    outgoing,
                    incomingTruncated,
                    outgoingTruncated,
                    inviteTargets,
                    inviteTargetsTruncated);
            if (decoded.getIncomingInvitations().size() != incomingCount
                    || decoded.getOutgoingInvitations().size() != outgoingCount
                    || decoded.getInviteTargets().size() != inviteTargetCount) {
                throw new PartyPacketCodec.DecodeException(
                        "unauthorized or inconsistent private party data");
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
        buffer.writeInt(this.requestId);
        LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getOwnerId());
        buffer.writeLong(this.snapshot.getSynchronizationSequence());
        LostTalesPacketCodec.writeUtf8String(
                buffer,
                this.snapshot.getStateErrorId().getId(),
                PartyPacketCodec.MAX_ERROR_ID_BYTES);
        if (!this.snapshot.isAvailable()) {
            return;
        }

        LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getActiveCharacterId());
        writeParty(buffer, this.snapshot.getParty());
        buffer.writeBoolean(this.snapshot.isIncomingTruncated());
        buffer.writeShort(this.snapshot.getIncomingInvitations().size());
        for (PartyInvitationSnapshot invitation
                : this.snapshot.getIncomingInvitations()) {
            writeInvitation(buffer, invitation);
        }
        buffer.writeBoolean(this.snapshot.isOutgoingTruncated());
        buffer.writeShort(this.snapshot.getOutgoingInvitations().size());
        for (PartyInvitationSnapshot invitation
                : this.snapshot.getOutgoingInvitations()) {
            writeInvitation(buffer, invitation);
        }
        buffer.writeBoolean(this.snapshot.isInviteTargetsTruncated());
        buffer.writeShort(this.snapshot.getInviteTargets().size());
        for (PartyInviteTargetSnapshot target
                : this.snapshot.getInviteTargets()) {
            writeInviteTarget(buffer, target);
        }
    }

    public int getRequestId() {
        return this.requestId;
    }

    public PartyStateSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static PartySnapshot readParty(ByteBuf buffer) {
        boolean present = buffer.readBoolean();
        if (!present) {
            return null;
        }
        UUID partyId = LostTalesPacketCodec.readUuid(buffer);
        UUID leaderCharacterId = LostTalesPacketCodec.readUuid(buffer);
        long createdAt = buffer.readLong();
        long revision = buffer.readLong();
        int dataVersion = buffer.readInt();
        int memberCount = buffer.readUnsignedByte();
        if (createdAt < 0L || revision < 0L || dataVersion <= 0
                || memberCount <= 0 || memberCount > Party.MAX_MEMBERS) {
            throw new PartyPacketCodec.DecodeException("invalid party header");
        }

        ArrayList<PartyMemberSnapshot> members =
                new ArrayList<PartyMemberSnapshot>(memberCount);
        Set<UUID> characterIds = new HashSet<UUID>();
        Set<PartyColor> colors = EnumSet.noneOf(PartyColor.class);
        for (int index = 0; index < memberCount; index++) {
            UUID characterId = LostTalesPacketCodec.readUuid(buffer);
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            String characterName = LostTalesPacketCodec.readUtf8String(
                    buffer, PartyPacketCodec.MAX_NAME_BYTES);
            long joinedAt = buffer.readLong();
            PartyColor color = PartyColor.fromNetworkId(
                    buffer.readUnsignedByte());
            if (joinedAt < 0L || color == null
                    || !characterIds.add(characterId)
                    || !colors.add(color)) {
                throw new PartyPacketCodec.DecodeException(
                        "invalid party member");
            }
            members.add(new PartyMemberSnapshot(
                    characterId, ownerId, characterName, joinedAt, color));
        }
        if (!characterIds.contains(leaderCharacterId)) {
            throw new PartyPacketCodec.DecodeException("missing party leader");
        }
        return new PartySnapshot(
                partyId,
                leaderCharacterId,
                createdAt,
                revision,
                dataVersion,
                members);
    }

    private static void writeParty(ByteBuf buffer, PartySnapshot party) {
        buffer.writeBoolean(party != null);
        if (party == null) {
            return;
        }
        LostTalesPacketCodec.writeUuid(buffer, party.getPartyId());
        LostTalesPacketCodec.writeUuid(buffer, party.getLeaderCharacterId());
        buffer.writeLong(party.getCreatedAt());
        buffer.writeLong(party.getRevision());
        buffer.writeInt(party.getDataVersion());
        buffer.writeByte(party.getMemberCount());
        for (PartyMemberSnapshot member : party.getMembers()) {
            LostTalesPacketCodec.writeUuid(buffer, member.getCharacterId());
            LostTalesPacketCodec.writeUuid(buffer, member.getOwnerId());
            LostTalesPacketCodec.writeUtf8String(
                    buffer,
                    member.getCharacterName(),
                    PartyPacketCodec.MAX_NAME_BYTES);
            buffer.writeLong(member.getJoinedAt());
            buffer.writeByte(member.getColor().getNetworkId());
        }
    }

    private static List<PartyInvitationSnapshot> readInvitations(
            ByteBuf buffer, int count) {
        ArrayList<PartyInvitationSnapshot> invitations =
                new ArrayList<PartyInvitationSnapshot>(count);
        Set<UUID> invitationIds = new HashSet<UUID>();
        for (int index = 0; index < count; index++) {
            UUID invitationId = LostTalesPacketCodec.readUuid(buffer);
            UUID partyId = LostTalesPacketCodec.readUuid(buffer);
            UUID invitingCharacterId = LostTalesPacketCodec.readUuid(buffer);
            UUID invitingOwnerId = LostTalesPacketCodec.readUuid(buffer);
            String invitingCharacterName = LostTalesPacketCodec.readUtf8String(
                    buffer, PartyPacketCodec.MAX_NAME_BYTES);
            UUID targetCharacterId = LostTalesPacketCodec.readUuid(buffer);
            UUID targetOwnerId = LostTalesPacketCodec.readUuid(buffer);
            String targetCharacterName = LostTalesPacketCodec.readUtf8String(
                    buffer, PartyPacketCodec.MAX_NAME_BYTES);
            long createdAt = buffer.readLong();
            long expiresAt = buffer.readLong();
            if (!invitationIds.add(invitationId)
                    || createdAt < 0L || expiresAt <= createdAt) {
                throw new PartyPacketCodec.DecodeException(
                        "invalid invitation snapshot");
            }
            invitations.add(new PartyInvitationSnapshot(
                    invitationId,
                    partyId,
                    invitingCharacterId,
                    invitingOwnerId,
                    invitingCharacterName,
                    targetCharacterId,
                    targetOwnerId,
                    targetCharacterName,
                    createdAt,
                    expiresAt));
        }
        return invitations;
    }

    private static void writeInvitation(ByteBuf buffer,
                                        PartyInvitationSnapshot invitation) {
        LostTalesPacketCodec.writeUuid(buffer, invitation.getInvitationId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getPartyId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getInvitingCharacterId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getInvitingOwnerId());
        LostTalesPacketCodec.writeUtf8String(
                buffer,
                invitation.getInvitingCharacterName(),
                PartyPacketCodec.MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUuid(buffer, invitation.getTargetCharacterId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getTargetOwnerId());
        LostTalesPacketCodec.writeUtf8String(
                buffer,
                invitation.getTargetCharacterName(),
                PartyPacketCodec.MAX_NAME_BYTES);
        buffer.writeLong(invitation.getCreatedAt());
        buffer.writeLong(invitation.getExpiresAt());
    }

    private static List<PartyInviteTargetSnapshot> readInviteTargets(
            ByteBuf buffer, int count) {
        ArrayList<PartyInviteTargetSnapshot> targets =
                new ArrayList<PartyInviteTargetSnapshot>(count);
        Set<UUID> ownerIds = new HashSet<UUID>();
        Set<UUID> characterIds = new HashSet<UUID>();
        for (int index = 0; index < count; index++) {
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            UUID characterId = LostTalesPacketCodec.readUuid(buffer);
            String playerName = LostTalesPacketCodec.readUtf8String(
                    buffer, PartyPacketCodec.MAX_NAME_BYTES);
            String characterName = LostTalesPacketCodec.readUtf8String(
                    buffer, PartyPacketCodec.MAX_NAME_BYTES);
            if (!ownerIds.add(ownerId) || !characterIds.add(characterId)) {
                throw new PartyPacketCodec.DecodeException(
                        "duplicate invite target identity");
            }
            targets.add(new PartyInviteTargetSnapshot(
                    ownerId, characterId, playerName, characterName));
        }
        return targets;
    }

    private static void writeInviteTarget(
            ByteBuf buffer, PartyInviteTargetSnapshot target) {
        LostTalesPacketCodec.writeUuid(buffer, target.getOwnerId());
        LostTalesPacketCodec.writeUuid(buffer, target.getCharacterId());
        LostTalesPacketCodec.writeUtf8String(
                buffer, target.getPlayerName(),
                PartyPacketCodec.MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, target.getCharacterName(),
                PartyPacketCodec.MAX_NAME_BYTES);
    }

    public static final class Handler implements IMessageHandler<PartyStateSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final PartyStateSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handlePartyStateSync(message);
                }
            });
            return null;
        }
    }
}
