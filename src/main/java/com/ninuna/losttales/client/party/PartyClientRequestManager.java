package com.ninuna.losttales.client.party;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.party.PartyActionRequestPacket;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyOperationType;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/** Client-only request builder used by future party interfaces. */
public final class PartyClientRequestManager {

    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger();

    private PartyClientRequestManager() {}

    public static int requestState() {
        return send(PartyOperationType.REQUEST_STATE,
                null, null, PartyActionRequestPacket.NO_PARTY_REVISION,
                null, null);
    }

    public static int createParty() {
        return createParty(currentActiveCharacterId());
    }

    public static int createParty(UUID expectedActiveCharacterId) {
        return send(PartyOperationType.CREATE,
                expectedActiveCharacterId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION, null, null);
    }

    public static int leaveParty(long expectedPartyRevision) {
        return leaveParty(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision);
    }

    public static int leaveParty(UUID expectedActiveCharacterId,
                                 UUID expectedPartyId,
                                 long expectedPartyRevision) {
        return send(PartyOperationType.LEAVE,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, null, null);
    }

    public static int removeMember(long expectedPartyRevision,
                                   UUID targetCharacterId) {
        return removeMember(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision, targetCharacterId);
    }

    public static int removeMember(UUID expectedActiveCharacterId,
                                   UUID expectedPartyId,
                                   long expectedPartyRevision,
                                   UUID targetCharacterId) {
        return send(PartyOperationType.REMOVE_MEMBER,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, targetCharacterId, null);
    }

    public static int disbandParty(long expectedPartyRevision) {
        return disbandParty(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision);
    }

    public static int disbandParty(UUID expectedActiveCharacterId,
                                   UUID expectedPartyId,
                                   long expectedPartyRevision) {
        return send(PartyOperationType.DISBAND,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, null, null);
    }

    public static int transferLeadership(long expectedPartyRevision,
                                         UUID targetCharacterId) {
        return transferLeadership(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision, targetCharacterId);
    }

    public static int transferLeadership(UUID expectedActiveCharacterId,
                                         UUID expectedPartyId,
                                         long expectedPartyRevision,
                                         UUID targetCharacterId) {
        return send(PartyOperationType.TRANSFER_LEADERSHIP,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, targetCharacterId, null);
    }

    public static int setColor(long expectedPartyRevision,
                               PartyColor color) {
        return setColor(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision, color);
    }

    public static int setColor(UUID expectedActiveCharacterId,
                               UUID expectedPartyId,
                               long expectedPartyRevision,
                               PartyColor color) {
        return send(PartyOperationType.SET_COLOR,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, null, color);
    }

    public static int setGoHereMarker(long expectedPartyRevision) {
        return setGoHereMarker(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision);
    }

    public static int setGoHereMarker(UUID expectedActiveCharacterId,
                                      UUID expectedPartyId,
                                      long expectedPartyRevision) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return failWithoutSend(PartyOperationType.SET_GO_HERE_MARKER);
        }
        return setGoHereMarker(expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, minecraft.thePlayer.dimension,
                minecraft.thePlayer.posX, minecraft.thePlayer.posZ);
    }

    public static int setGoHereMarker(UUID expectedActiveCharacterId,
                                      UUID expectedPartyId,
                                      long expectedPartyRevision,
                                      int dimensionId,
                                      double x,
                                      double z) {
        return send(PartyOperationType.SET_GO_HERE_MARKER,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, null, null,
                true, dimensionId, x, z);
    }

    public static int removeGoHereMarker(long expectedPartyRevision) {
        return removeGoHereMarker(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision);
    }

    public static int removeGoHereMarker(UUID expectedActiveCharacterId,
                                         UUID expectedPartyId,
                                         long expectedPartyRevision) {
        return send(PartyOperationType.REMOVE_GO_HERE_MARKER,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, null, null);
    }

    public static int invitePlayer(long expectedPartyRevision,
                                   UUID targetOwnerId) {
        return invitePlayer(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision, targetOwnerId);
    }

    public static int invitePlayer(UUID expectedActiveCharacterId,
                                   UUID expectedPartyId,
                                   long expectedPartyRevision,
                                   UUID targetOwnerId) {
        return send(PartyOperationType.INVITE_PLAYER,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, targetOwnerId, null);
    }

    public static int acceptInvitation(UUID invitationId) {
        return acceptInvitation(currentActiveCharacterId(), invitationId);
    }

    public static int acceptInvitation(UUID expectedActiveCharacterId,
                                       UUID invitationId) {
        return send(PartyOperationType.ACCEPT_INVITATION,
                expectedActiveCharacterId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION,
                invitationId, null);
    }

    public static int declineInvitation(UUID invitationId) {
        return declineInvitation(currentActiveCharacterId(), invitationId);
    }

    public static int declineInvitation(UUID expectedActiveCharacterId,
                                        UUID invitationId) {
        return send(PartyOperationType.DECLINE_INVITATION,
                expectedActiveCharacterId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION,
                invitationId, null);
    }

    public static int cancelInvitation(long expectedPartyRevision,
                                       UUID invitationId) {
        return cancelInvitation(currentActiveCharacterId(), currentPartyId(),
                expectedPartyRevision, invitationId);
    }

    public static int cancelInvitation(UUID expectedActiveCharacterId,
                                       UUID expectedPartyId,
                                       long expectedPartyRevision,
                                       UUID invitationId) {
        return send(PartyOperationType.CANCEL_INVITATION,
                expectedActiveCharacterId, expectedPartyId,
                expectedPartyRevision, invitationId, null);
    }

    private static int send(PartyOperationType operationType,
                            UUID expectedActiveCharacterId,
                            UUID expectedPartyId,
                            long expectedPartyRevision,
                            UUID targetId,
                            PartyColor color) {
        return send(operationType, expectedActiveCharacterId,
                expectedPartyId, expectedPartyRevision, targetId, color,
                false, 0, 0.0D, 0.0D);
    }

    private static int send(PartyOperationType operationType,
                            UUID expectedActiveCharacterId,
                            UUID expectedPartyId,
                            long expectedPartyRevision,
                            UUID targetId,
                            PartyColor color,
                            boolean hasMarkerPosition,
                            int markerDimensionId,
                            double markerX,
                            double markerZ) {
        int requestId = nextRequestId();
        ClientPartyStateCache.beginRequest(requestId, operationType);
        try {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new PartyActionRequestPacket(
                            requestId,
                            operationType,
                            expectedActiveCharacterId,
                            expectedPartyId,
                            expectedPartyRevision,
                            targetId,
                            color,
                            hasMarkerPosition,
                            markerDimensionId,
                            markerX,
                            markerZ));
        } catch (Throwable throwable) {
            ClientPartyStateCache.failLocalRequest(requestId, operationType);
        }
        return requestId;
    }

    private static int failWithoutSend(PartyOperationType operationType) {
        int requestId = nextRequestId();
        ClientPartyStateCache.beginRequest(requestId, operationType);
        ClientPartyStateCache.failLocalRequest(requestId, operationType);
        return requestId;
    }

    private static UUID currentActiveCharacterId() {
        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        return snapshot != null && snapshot.isAvailable()
                ? snapshot.getActiveCharacterId() : null;
    }

    private static UUID currentPartyId() {
        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        return snapshot != null && snapshot.isAvailable()
                && snapshot.getParty() != null
                ? snapshot.getParty().getPartyId() : null;
    }

    private static int nextRequestId() {
        while (true) {
            int next = NEXT_REQUEST_ID.incrementAndGet();
            if (next > 0) {
                return next;
            }
            NEXT_REQUEST_ID.compareAndSet(next, 0);
        }
    }
}
