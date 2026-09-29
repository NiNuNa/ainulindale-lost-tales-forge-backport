package com.ninuna.losttales.client.party;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.party.PartyActionRequestPacket;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.sync.PartyOperationType;
import com.ninuna.losttales.party.sync.PartyStateSnapshot;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Builds and sends the party requests of the Party page, the map and the
 * invitation lines in the chat. Each names the identity it was made for;
 * a change to the party names the party and the revision it was made
 * against, so the server refuses it once the party has changed.
 */
public final class PartyClientRequestManager {

    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger();

    private PartyClientRequestManager() {}

    public static int requestState() {
        return send(PartyOperationType.REQUEST_STATE,
                null, null, PartyActionRequestPacket.NO_PARTY_REVISION,
                null, null);
    }

    public static int createParty(UUID expectedActiveIdentityId) {
        return send(PartyOperationType.CREATE,
                expectedActiveIdentityId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION, null, null);
    }

    public static int leaveParty(UUID expectedActiveIdentityId,
                                 UUID expectedPartyId,
                                 long expectedPartyRevision) {
        return send(PartyOperationType.LEAVE,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, null, null);
    }

    public static int removeMember(UUID expectedActiveIdentityId,
                                   UUID expectedPartyId,
                                   long expectedPartyRevision,
                                   UUID targetIdentityId) {
        return send(PartyOperationType.REMOVE_MEMBER,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, targetIdentityId, null);
    }

    public static int disbandParty(UUID expectedActiveIdentityId,
                                   UUID expectedPartyId,
                                   long expectedPartyRevision) {
        return send(PartyOperationType.DISBAND,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, null, null);
    }

    public static int transferLeadership(UUID expectedActiveIdentityId,
                                         UUID expectedPartyId,
                                         long expectedPartyRevision,
                                         UUID targetIdentityId) {
        return send(PartyOperationType.TRANSFER_LEADERSHIP,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, targetIdentityId, null);
    }

    public static int setColor(UUID expectedActiveIdentityId,
                               UUID expectedPartyId,
                               long expectedPartyRevision,
                               PartyColor color) {
        return send(PartyOperationType.SET_COLOR,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, null, color);
    }

    /** The leader names the party; an empty name takes its name away. */
    public static int renameParty(UUID expectedActiveIdentityId,
                                  UUID expectedPartyId,
                                  long expectedPartyRevision,
                                  String name) {
        return send(PartyOperationType.RENAME,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, null, null,
                false, 0, 0.0D, 0.0D, name == null ? "" : name);
    }

    /** Places the player's go-here marker where they stand. */
    public static int setGoHereMarker(UUID expectedActiveIdentityId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return failWithoutSend(PartyOperationType.SET_GO_HERE_MARKER);
        }
        return setGoHereMarker(expectedActiveIdentityId,
                minecraft.thePlayer.dimension,
                minecraft.thePlayer.posX, minecraft.thePlayer.posZ);
    }

    /** Places the player's go-here marker at a place picked on the map. */
    public static int setGoHereMarker(UUID expectedActiveIdentityId,
                                      int dimensionId,
                                      double x,
                                      double z) {
        return send(PartyOperationType.SET_GO_HERE_MARKER,
                expectedActiveIdentityId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION, null, null,
                true, dimensionId, x, z, null);
    }

    public static int removeGoHereMarker(UUID expectedActiveIdentityId) {
        return send(PartyOperationType.REMOVE_GO_HERE_MARKER,
                expectedActiveIdentityId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION, null, null);
    }

    public static int invitePlayer(UUID expectedActiveIdentityId,
                                   UUID expectedPartyId,
                                   long expectedPartyRevision,
                                   UUID targetOwnerId) {
        return send(PartyOperationType.INVITE_PLAYER,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, targetOwnerId, null);
    }

    /** Accepts an invitation for the identity the party state was last sent for. */
    public static int acceptInvitation(UUID invitationId) {
        return acceptInvitation(currentActiveIdentityId(), invitationId);
    }

    public static int acceptInvitation(UUID expectedActiveIdentityId,
                                       UUID invitationId) {
        return send(PartyOperationType.ACCEPT_INVITATION,
                expectedActiveIdentityId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION,
                invitationId, null);
    }

    /** Declines an invitation for the identity the party state was last sent for. */
    public static int declineInvitation(UUID invitationId) {
        return declineInvitation(currentActiveIdentityId(), invitationId);
    }

    public static int declineInvitation(UUID expectedActiveIdentityId,
                                        UUID invitationId) {
        return send(PartyOperationType.DECLINE_INVITATION,
                expectedActiveIdentityId, null,
                PartyActionRequestPacket.NO_PARTY_REVISION,
                invitationId, null);
    }

    public static int cancelInvitation(UUID expectedActiveIdentityId,
                                       UUID expectedPartyId,
                                       long expectedPartyRevision,
                                       UUID invitationId) {
        return send(PartyOperationType.CANCEL_INVITATION,
                expectedActiveIdentityId, expectedPartyId,
                expectedPartyRevision, invitationId, null);
    }

    private static int send(PartyOperationType operationType,
                            UUID expectedActiveIdentityId,
                            UUID expectedPartyId,
                            long expectedPartyRevision,
                            UUID targetId,
                            PartyColor color) {
        return send(operationType, expectedActiveIdentityId,
                expectedPartyId, expectedPartyRevision, targetId, color,
                false, 0, 0.0D, 0.0D, null);
    }

    private static int send(PartyOperationType operationType,
                            UUID expectedActiveIdentityId,
                            UUID expectedPartyId,
                            long expectedPartyRevision,
                            UUID targetId,
                            PartyColor color,
                            boolean hasMarkerPosition,
                            int markerDimensionId,
                            double markerX,
                            double markerZ,
                            String name) {
        int requestId = nextRequestId();
        ClientPartyStateCache.beginRequest(requestId, operationType);
        try {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new PartyActionRequestPacket(
                            requestId,
                            operationType,
                            expectedActiveIdentityId,
                            expectedPartyId,
                            expectedPartyRevision,
                            targetId,
                            color,
                            hasMarkerPosition,
                            markerDimensionId,
                            markerX,
                            markerZ,
                            name));
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

    private static UUID currentActiveIdentityId() {
        PartyStateSnapshot snapshot = ClientPartyStateCache.getSnapshot();
        return snapshot != null && snapshot.isAvailable()
                ? snapshot.getActiveIdentityId() : null;
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
