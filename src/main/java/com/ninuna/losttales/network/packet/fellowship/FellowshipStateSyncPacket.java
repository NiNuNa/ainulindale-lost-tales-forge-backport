package com.ninuna.losttales.network.packet.fellowship;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.network.packet.LostTalesPacketCodec;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipIcon;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipInviteTargetSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything one client may see of its fellowships, whole, each time. The
 * server's member limit rides in every state, the unavailable one too.
 */
public final class FellowshipStateSyncPacket implements IMessage {

    private int requestId;
    private FellowshipStateSnapshot snapshot;
    private boolean malformed;

    public FellowshipStateSyncPacket() {}

    public FellowshipStateSyncPacket(int requestId, FellowshipStateSnapshot snapshot) {
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
            FellowshipErrorId stateErrorId = FellowshipErrorId.fromId(
                    LostTalesPacketCodec.readUtf8String(
                            buffer, FellowshipPacketCodec.MAX_ERROR_ID_BYTES));
            int memberLimit = buffer.readUnsignedByte();

            if (synchronizationSequence <= 0L) {
                throw new FellowshipPacketCodec.DecodeException(
                        "invalid synchronization sequence");
            }
            if (memberLimit != Fellowship.clampMemberLimit(memberLimit)) {
                throw new FellowshipPacketCodec.DecodeException(
                        "invalid fellowship member limit");
            }
            if (stateErrorId != FellowshipErrorId.NONE) {
                LostTalesPacketCodec.requireFinished(buffer);
                this.snapshot = FellowshipStateSnapshot.failure(
                        ownerId, synchronizationSequence, memberLimit,
                        stateErrorId);
                return;
            }

            UUID activeIdentityId = LostTalesPacketCodec.readUuid(buffer);
            FellowshipErrorId createRefusal = FellowshipErrorId.fromId(
                    LostTalesPacketCodec.readUtf8String(
                            buffer, FellowshipPacketCodec.MAX_ERROR_ID_BYTES));
            if (!FellowshipStateSnapshot.isCreateRefusal(createRefusal)) {
                throw new FellowshipPacketCodec.DecodeException("invalid create refusal");
            }
            int fellowshipCount = buffer.readUnsignedByte();
            if (fellowshipCount > Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY) {
                throw new FellowshipPacketCodec.DecodeException("too many fellowships");
            }
            ArrayList<FellowshipSnapshot> fellowships =
                    new ArrayList<FellowshipSnapshot>(fellowshipCount);
            for (int index = 0; index < fellowshipCount; index++) {
                fellowships.add(readFellowship(buffer));
            }
            UUID travellingFellowshipId = fellowshipCount == 0 ? null
                    : LostTalesPacketCodec.readUuid(buffer);
            boolean incomingTruncated = buffer.readBoolean();
            int incomingCount = buffer.readUnsignedShort();
            if (incomingCount > FellowshipStateSnapshot.MAX_INCOMING_INVITATIONS) {
                throw new FellowshipPacketCodec.DecodeException(
                        "too many incoming invitations");
            }
            List<FellowshipInvitationSnapshot> incoming =
                    readInvitations(buffer, incomingCount);
            boolean outgoingTruncated = buffer.readBoolean();
            int outgoingCount = buffer.readUnsignedShort();
            if (outgoingCount > FellowshipStateSnapshot.MAX_OUTGOING_INVITATIONS) {
                throw new FellowshipPacketCodec.DecodeException(
                        "too many outgoing invitations");
            }
            List<FellowshipInvitationSnapshot> outgoing =
                    readInvitations(buffer, outgoingCount);
            boolean inviteTargetsTruncated = buffer.readBoolean();
            int inviteTargetCount = buffer.readUnsignedShort();
            if (inviteTargetCount > FellowshipStateSnapshot.MAX_INVITE_TARGETS) {
                throw new FellowshipPacketCodec.DecodeException(
                        "too many invite targets");
            }
            List<FellowshipInviteTargetSnapshot> inviteTargets =
                    readInviteTargets(buffer, inviteTargetCount);
            LostTalesPacketCodec.requireFinished(buffer);

            FellowshipStateSnapshot decoded = new FellowshipStateSnapshot(
                    ownerId,
                    synchronizationSequence,
                    FellowshipErrorId.NONE,
                    memberLimit,
                    activeIdentityId,
                    fellowships,
                    travellingFellowshipId,
                    createRefusal,
                    incoming,
                    outgoing,
                    incomingTruncated,
                    outgoingTruncated,
                    inviteTargets,
                    inviteTargetsTruncated);
            if (decoded.getIncomingInvitations().size() != incomingCount
                    || decoded.getOutgoingInvitations().size() != outgoingCount
                    || decoded.getInviteTargets().size() != inviteTargetCount) {
                throw new FellowshipPacketCodec.DecodeException(
                        "unauthorized or inconsistent private fellowship data");
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
                FellowshipPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeByte(this.snapshot.getMemberLimit());
        if (!this.snapshot.isAvailable()) {
            return;
        }

        LostTalesPacketCodec.writeUuid(buffer, this.snapshot.getActiveIdentityId());
        LostTalesPacketCodec.writeUtf8String(buffer,
                this.snapshot.getCreateRefusal().getId(),
                FellowshipPacketCodec.MAX_ERROR_ID_BYTES);
        buffer.writeByte(this.snapshot.getFellowships().size());
        for (FellowshipSnapshot fellowship : this.snapshot.getFellowships()) {
            writeFellowship(buffer, fellowship);
        }
        if (!this.snapshot.getFellowships().isEmpty()) {
            LostTalesPacketCodec.writeUuid(buffer,
                    this.snapshot.getTravellingFellowship().getFellowshipId());
        }
        buffer.writeBoolean(this.snapshot.isIncomingTruncated());
        buffer.writeShort(this.snapshot.getIncomingInvitations().size());
        for (FellowshipInvitationSnapshot invitation
                : this.snapshot.getIncomingInvitations()) {
            writeInvitation(buffer, invitation);
        }
        buffer.writeBoolean(this.snapshot.isOutgoingTruncated());
        buffer.writeShort(this.snapshot.getOutgoingInvitations().size());
        for (FellowshipInvitationSnapshot invitation
                : this.snapshot.getOutgoingInvitations()) {
            writeInvitation(buffer, invitation);
        }
        buffer.writeBoolean(this.snapshot.isInviteTargetsTruncated());
        buffer.writeShort(this.snapshot.getInviteTargets().size());
        for (FellowshipInviteTargetSnapshot target
                : this.snapshot.getInviteTargets()) {
            writeInviteTarget(buffer, target);
        }
    }

    public int getRequestId() {
        return this.requestId;
    }

    public FellowshipStateSnapshot getSnapshot() {
        return this.snapshot;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    private static FellowshipSnapshot readFellowship(ByteBuf buffer) {
        UUID fellowshipId = LostTalesPacketCodec.readUuid(buffer);
        UUID leaderIdentityId = LostTalesPacketCodec.readUuid(buffer);
        String name = LostTalesPacketCodec.readUtf8String(
                buffer, FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES);
        long createdAt = buffer.readLong();
        long revision = buffer.readLong();
        int dataVersion = buffer.readInt();
        FellowshipIcon icon = null;
        if (buffer.readBoolean()) {
            String itemName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipIcon.MAX_ITEM_NAME_LENGTH);
            int damage = buffer.readUnsignedShort();
            if (!FellowshipIcon.isWellFormed(itemName, damage)) {
                throw new FellowshipPacketCodec.DecodeException("invalid fellowship icon");
            }
            icon = new FellowshipIcon(itemName, damage);
        }
        int switchBits = buffer.readUnsignedByte();
        Set<FellowshipSwitch> switchesOn = EnumSet.noneOf(FellowshipSwitch.class);
        for (FellowshipSwitch fellowshipSwitch : FellowshipSwitch.values()) {
            if ((switchBits & (1 << fellowshipSwitch.getNetworkId())) != 0) {
                switchesOn.add(fellowshipSwitch);
            }
        }
        int memberCount = buffer.readUnsignedByte();
        if (!Fellowship.isWellFormedName(name)
                || createdAt < 0L || revision < 0L || dataVersion <= 0
                || switchBits != switchBitsOf(switchesOn)
                || memberCount <= 0 || memberCount > Fellowship.MAX_MEMBERS) {
            throw new FellowshipPacketCodec.DecodeException("invalid fellowship header");
        }

        ArrayList<FellowshipMemberSnapshot> members =
                new ArrayList<FellowshipMemberSnapshot>(memberCount);
        Set<UUID> identityIds = new HashSet<UUID>();
        for (int index = 0; index < memberCount; index++) {
            UUID identityId = LostTalesPacketCodec.readUuid(buffer);
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            String characterName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES);
            long joinedAt = buffer.readLong();
            FellowshipColor color = FellowshipColor.fromNetworkId(
                    buffer.readUnsignedByte());
            FellowshipMemberPresence presence = FellowshipMemberPresence.fromNetworkId(
                    buffer.readUnsignedByte());
            String elsewhereName = presence == FellowshipMemberPresence.ELSEWHERE
                    ? LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES) : "";
            if (joinedAt < 0L || color == null || presence == null
                    || !identityIds.add(identityId)) {
                throw new FellowshipPacketCodec.DecodeException(
                        "invalid fellowship member");
            }
            members.add(new FellowshipMemberSnapshot(identityId, ownerId,
                    characterName, joinedAt, color, presence, elsewhereName));
        }
        if (!identityIds.contains(leaderIdentityId)) {
            throw new FellowshipPacketCodec.DecodeException("missing fellowship leader");
        }
        int guideCount = buffer.readUnsignedByte();
        Set<UUID> guides = new LinkedHashSet<UUID>();
        for (int index = 0; index < guideCount; index++) {
            UUID guide = LostTalesPacketCodec.readUuid(buffer);
            if (!identityIds.contains(guide) || guide.equals(leaderIdentityId)
                    || !guides.add(guide)) {
                throw new FellowshipPacketCodec.DecodeException("invalid fellowship guide");
            }
        }
        return new FellowshipSnapshot(fellowshipId, leaderIdentityId, name, icon,
                switchesOn, guides, createdAt, revision, dataVersion, members);
    }

    private static void writeFellowship(ByteBuf buffer, FellowshipSnapshot fellowship) {
        LostTalesPacketCodec.writeUuid(buffer, fellowship.getFellowshipId());
        LostTalesPacketCodec.writeUuid(buffer, fellowship.getLeaderIdentityId());
        LostTalesPacketCodec.writeUtf8String(buffer, fellowship.getName(),
                FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES);
        buffer.writeLong(fellowship.getCreatedAt());
        buffer.writeLong(fellowship.getRevision());
        buffer.writeInt(fellowship.getDataVersion());
        FellowshipIcon icon = fellowship.getIcon();
        buffer.writeBoolean(icon != null);
        if (icon != null) {
            LostTalesPacketCodec.writeUtf8String(buffer, icon.getItemName(),
                    FellowshipIcon.MAX_ITEM_NAME_LENGTH);
            buffer.writeShort(icon.getDamage());
        }
        buffer.writeByte(switchBitsOf(fellowship.getSwitchesOn()));
        buffer.writeByte(fellowship.getMemberCount());
        for (FellowshipMemberSnapshot member : fellowship.getMembers()) {
            LostTalesPacketCodec.writeUuid(buffer, member.getIdentityId());
            LostTalesPacketCodec.writeUuid(buffer, member.getOwnerId());
            LostTalesPacketCodec.writeUtf8String(
                    buffer,
                    member.getCharacterName(),
                    FellowshipPacketCodec.MAX_NAME_BYTES);
            buffer.writeLong(member.getJoinedAt());
            buffer.writeByte(member.getColor().getNetworkId());
            buffer.writeByte(member.getPresence().getNetworkId());
            if (member.getPresence() == FellowshipMemberPresence.ELSEWHERE) {
                LostTalesPacketCodec.writeUtf8String(buffer, member.getElsewhereName(),
                        FellowshipPacketCodec.MAX_NAME_BYTES);
            }
        }
        buffer.writeByte(fellowship.getGuides().size());
        for (UUID guide : fellowship.getGuides()) {
            LostTalesPacketCodec.writeUuid(buffer, guide);
        }
    }

    /** One bit per switch that is on, at its network id. */
    private static int switchBitsOf(Set<FellowshipSwitch> switchesOn) {
        int bits = 0;
        for (FellowshipSwitch fellowshipSwitch : switchesOn) {
            bits |= 1 << fellowshipSwitch.getNetworkId();
        }
        return bits;
    }

    private static List<FellowshipInvitationSnapshot> readInvitations(
            ByteBuf buffer, int count) {
        ArrayList<FellowshipInvitationSnapshot> invitations =
                new ArrayList<FellowshipInvitationSnapshot>(count);
        Set<UUID> invitationIds = new HashSet<UUID>();
        for (int index = 0; index < count; index++) {
            UUID invitationId = LostTalesPacketCodec.readUuid(buffer);
            UUID fellowshipId = LostTalesPacketCodec.readUuid(buffer);
            String fellowshipName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES);
            UUID invitingIdentityId = LostTalesPacketCodec.readUuid(buffer);
            UUID invitingOwnerId = LostTalesPacketCodec.readUuid(buffer);
            String invitingCharacterName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES);
            UUID targetIdentityId = LostTalesPacketCodec.readUuid(buffer);
            UUID targetOwnerId = LostTalesPacketCodec.readUuid(buffer);
            String targetCharacterName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES);
            long createdAt = buffer.readLong();
            long expiresAt = buffer.readLong();
            if (!invitationIds.add(invitationId)
                    || !Fellowship.isWellFormedName(fellowshipName)
                    || createdAt < 0L || expiresAt <= createdAt) {
                throw new FellowshipPacketCodec.DecodeException(
                        "invalid invitation snapshot");
            }
            invitations.add(new FellowshipInvitationSnapshot(
                    invitationId,
                    fellowshipId,
                    fellowshipName,
                    invitingIdentityId,
                    invitingOwnerId,
                    invitingCharacterName,
                    targetIdentityId,
                    targetOwnerId,
                    targetCharacterName,
                    createdAt,
                    expiresAt));
        }
        return invitations;
    }

    private static void writeInvitation(ByteBuf buffer,
                                        FellowshipInvitationSnapshot invitation) {
        LostTalesPacketCodec.writeUuid(buffer, invitation.getInvitationId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getFellowshipId());
        LostTalesPacketCodec.writeUtf8String(buffer, invitation.getFellowshipName(),
                FellowshipPacketCodec.MAX_FELLOWSHIP_NAME_BYTES);
        LostTalesPacketCodec.writeUuid(buffer, invitation.getInvitingIdentityId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getInvitingOwnerId());
        LostTalesPacketCodec.writeUtf8String(
                buffer,
                invitation.getInvitingCharacterName(),
                FellowshipPacketCodec.MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUuid(buffer, invitation.getTargetIdentityId());
        LostTalesPacketCodec.writeUuid(buffer, invitation.getTargetOwnerId());
        LostTalesPacketCodec.writeUtf8String(
                buffer,
                invitation.getTargetCharacterName(),
                FellowshipPacketCodec.MAX_NAME_BYTES);
        buffer.writeLong(invitation.getCreatedAt());
        buffer.writeLong(invitation.getExpiresAt());
    }

    private static List<FellowshipInviteTargetSnapshot> readInviteTargets(
            ByteBuf buffer, int count) {
        ArrayList<FellowshipInviteTargetSnapshot> targets =
                new ArrayList<FellowshipInviteTargetSnapshot>(count);
        Set<UUID> ownerIds = new HashSet<UUID>();
        Set<UUID> identityIds = new HashSet<UUID>();
        for (int index = 0; index < count; index++) {
            UUID ownerId = LostTalesPacketCodec.readUuid(buffer);
            UUID identityId = LostTalesPacketCodec.readUuid(buffer);
            String playerName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES);
            String characterName = LostTalesPacketCodec.readUtf8String(
                    buffer, FellowshipPacketCodec.MAX_NAME_BYTES);
            if (!ownerIds.add(ownerId) || !identityIds.add(identityId)) {
                throw new FellowshipPacketCodec.DecodeException(
                        "duplicate invite target identity");
            }
            targets.add(new FellowshipInviteTargetSnapshot(
                    ownerId, identityId, playerName, characterName));
        }
        return targets;
    }

    private static void writeInviteTarget(
            ByteBuf buffer, FellowshipInviteTargetSnapshot target) {
        LostTalesPacketCodec.writeUuid(buffer, target.getOwnerId());
        LostTalesPacketCodec.writeUuid(buffer, target.getIdentityId());
        LostTalesPacketCodec.writeUtf8String(
                buffer, target.getPlayerName(),
                FellowshipPacketCodec.MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, target.getCharacterName(),
                FellowshipPacketCodec.MAX_NAME_BYTES);
    }

    public static final class Handler implements IMessageHandler<FellowshipStateSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final FellowshipStateSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleFellowshipStateSync(message);
                }
            });
            return null;
        }
    }
}
