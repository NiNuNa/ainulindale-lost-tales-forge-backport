package com.ninuna.losttales.client.fellowship;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.fellowship.FellowshipActionRequestPacket;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.fellowship.sync.FellowshipStateSnapshot;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.client.Minecraft;

/**
 * Builds and sends the fellowship requests of the Fellowship page, the map and the
 * invitation lines in the chat. Each names the identity it was made for;
 * a change to the fellowship names the fellowship and the revision it was made
 * against, so the server refuses it once the fellowship has changed.
 */
public final class FellowshipClientRequestManager {

    private static final AtomicInteger NEXT_REQUEST_ID = new AtomicInteger();

    private FellowshipClientRequestManager() {}

    public static int requestState() {
        return send(FellowshipOperationType.REQUEST_STATE,
                null, null, FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION,
                null, null);
    }

    public static int createFellowship(UUID expectedActiveIdentityId, String name) {
        return send(FellowshipOperationType.CREATE,
                expectedActiveIdentityId, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION, null, null,
                false, 0, 0.0D, 0.0D, name == null ? "" : name, -1);
    }

    public static int leaveFellowship(UUID expectedActiveIdentityId,
                                 UUID expectedFellowshipId,
                                 long expectedFellowshipRevision) {
        return send(FellowshipOperationType.LEAVE,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null);
    }

    public static int removeMember(UUID expectedActiveIdentityId,
                                   UUID expectedFellowshipId,
                                   long expectedFellowshipRevision,
                                   UUID targetIdentityId) {
        return send(FellowshipOperationType.REMOVE_MEMBER,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, targetIdentityId, null);
    }

    public static int disbandFellowship(UUID expectedActiveIdentityId,
                                   UUID expectedFellowshipId,
                                   long expectedFellowshipRevision) {
        return send(FellowshipOperationType.DISBAND,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null);
    }

    public static int transferLeadership(UUID expectedActiveIdentityId,
                                         UUID expectedFellowshipId,
                                         long expectedFellowshipRevision,
                                         UUID targetIdentityId) {
        return send(FellowshipOperationType.TRANSFER_LEADERSHIP,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, targetIdentityId, null);
    }

    public static int setColor(UUID expectedActiveIdentityId,
                               UUID expectedFellowshipId,
                               long expectedFellowshipRevision,
                               FellowshipColor color) {
        return send(FellowshipOperationType.SET_COLOR,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, color);
    }

    /** The leader gives the fellowship a new name. */
    public static int renameFellowship(UUID expectedActiveIdentityId,
                                       UUID expectedFellowshipId,
                                       long expectedFellowshipRevision,
                                       String name) {
        return send(FellowshipOperationType.RENAME,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null,
                false, 0, 0.0D, 0.0D, name == null ? "" : name, -1);
    }

    /** The leader makes a member a guide, or no guide. */
    public static int setGuide(UUID expectedActiveIdentityId,
                               UUID expectedFellowshipId,
                               long expectedFellowshipRevision,
                               UUID targetIdentityId,
                               boolean guide) {
        return send(FellowshipOperationType.SET_GUIDE,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, targetIdentityId, null,
                false, 0, 0.0D, 0.0D, null, guide ? 1 : 0);
    }

    /** The fellowship wears the item the player holds, or none while they hold nothing. */
    public static int setIcon(UUID expectedActiveIdentityId,
                              UUID expectedFellowshipId,
                              long expectedFellowshipRevision) {
        return send(FellowshipOperationType.SET_ICON,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null);
    }

    public static int setSwitch(UUID expectedActiveIdentityId,
                                UUID expectedFellowshipId,
                                long expectedFellowshipRevision,
                                FellowshipSwitch fellowshipSwitch,
                                boolean on) {
        return send(FellowshipOperationType.SET_SWITCH,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null,
                false, 0, 0.0D, 0.0D, null,
                FellowshipOperationType.switchValue(fellowshipSwitch.getNetworkId(), on));
    }

    /** The player travels with this fellowship: the HUD, the marks and shared credit follow it. */
    public static int setTravelling(UUID expectedActiveIdentityId,
                                    UUID expectedFellowshipId,
                                    long expectedFellowshipRevision) {
        return send(FellowshipOperationType.SET_TRAVELLING,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null);
    }

    /** Places the player's go-here marker where they stand. */
    public static int setGoHereMarker(UUID expectedActiveIdentityId) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.thePlayer == null) {
            return failWithoutSend(FellowshipOperationType.SET_GO_HERE_MARKER);
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
        return send(FellowshipOperationType.SET_GO_HERE_MARKER,
                expectedActiveIdentityId, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION, null, null,
                true, dimensionId, x, z, null, -1);
    }

    public static int removeGoHereMarker(UUID expectedActiveIdentityId) {
        return send(FellowshipOperationType.REMOVE_GO_HERE_MARKER,
                expectedActiveIdentityId, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION, null, null);
    }

    /** Marks a place on the map for every member of the fellowship, named. */
    public static int placeMark(UUID expectedActiveIdentityId,
                                UUID expectedFellowshipId,
                                long expectedFellowshipRevision,
                                String name, int dimensionId, double x, double z) {
        return send(FellowshipOperationType.PLACE_MARK,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, null, null,
                true, dimensionId, x, z, name, -1);
    }

    /** Moves one of the fellowship's marks to a place picked on the map. */
    public static int moveMark(UUID expectedActiveIdentityId,
                               UUID expectedFellowshipId,
                               long expectedFellowshipRevision,
                               UUID markId, int dimensionId, double x, double z) {
        return send(FellowshipOperationType.MOVE_MARK,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, markId, null,
                true, dimensionId, x, z, null, -1);
    }

    /** Takes one of the fellowship's marks away. */
    public static int removeMark(UUID expectedActiveIdentityId,
                                 UUID expectedFellowshipId,
                                 long expectedFellowshipRevision,
                                 UUID markId) {
        return send(FellowshipOperationType.REMOVE_MARK,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, markId, null);
    }

    public static int invitePlayer(UUID expectedActiveIdentityId,
                                   UUID expectedFellowshipId,
                                   long expectedFellowshipRevision,
                                   UUID targetOwnerId) {
        return send(FellowshipOperationType.INVITE_PLAYER,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, targetOwnerId, null);
    }

    /** Accepts an invitation for the identity the fellowship state was last sent for. */
    public static int acceptInvitation(UUID invitationId) {
        return acceptInvitation(currentActiveIdentityId(), invitationId);
    }

    public static int acceptInvitation(UUID expectedActiveIdentityId,
                                       UUID invitationId) {
        return send(FellowshipOperationType.ACCEPT_INVITATION,
                expectedActiveIdentityId, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION,
                invitationId, null);
    }

    /** Declines an invitation for the identity the fellowship state was last sent for. */
    public static int declineInvitation(UUID invitationId) {
        return declineInvitation(currentActiveIdentityId(), invitationId);
    }

    public static int declineInvitation(UUID expectedActiveIdentityId,
                                        UUID invitationId) {
        return send(FellowshipOperationType.DECLINE_INVITATION,
                expectedActiveIdentityId, null,
                FellowshipActionRequestPacket.NO_FELLOWSHIP_REVISION,
                invitationId, null);
    }

    public static int cancelInvitation(UUID expectedActiveIdentityId,
                                       UUID expectedFellowshipId,
                                       long expectedFellowshipRevision,
                                       UUID invitationId) {
        return send(FellowshipOperationType.CANCEL_INVITATION,
                expectedActiveIdentityId, expectedFellowshipId,
                expectedFellowshipRevision, invitationId, null);
    }

    private static int send(FellowshipOperationType operationType,
                            UUID expectedActiveIdentityId,
                            UUID expectedFellowshipId,
                            long expectedFellowshipRevision,
                            UUID targetId,
                            FellowshipColor color) {
        return send(operationType, expectedActiveIdentityId,
                expectedFellowshipId, expectedFellowshipRevision, targetId, color,
                false, 0, 0.0D, 0.0D, null, -1);
    }

    private static int send(FellowshipOperationType operationType,
                            UUID expectedActiveIdentityId,
                            UUID expectedFellowshipId,
                            long expectedFellowshipRevision,
                            UUID targetId,
                            FellowshipColor color,
                            boolean hasMarkerPosition,
                            int markerDimensionId,
                            double markerX,
                            double markerZ,
                            String name,
                            int value) {
        int requestId = nextRequestId();
        ClientFellowshipStateCache.beginRequest(requestId, operationType);
        try {
            LostTalesNetworkHandler.CHANNEL.sendToServer(
                    new FellowshipActionRequestPacket(
                            requestId,
                            operationType,
                            expectedActiveIdentityId,
                            expectedFellowshipId,
                            expectedFellowshipRevision,
                            targetId,
                            color,
                            hasMarkerPosition,
                            markerDimensionId,
                            markerX,
                            markerZ,
                            name,
                            value));
        } catch (Throwable throwable) {
            ClientFellowshipStateCache.failLocalRequest(requestId, operationType);
        }
        return requestId;
    }

    private static int failWithoutSend(FellowshipOperationType operationType) {
        int requestId = nextRequestId();
        ClientFellowshipStateCache.beginRequest(requestId, operationType);
        ClientFellowshipStateCache.failLocalRequest(requestId, operationType);
        return requestId;
    }

    private static UUID currentActiveIdentityId() {
        FellowshipStateSnapshot snapshot = ClientFellowshipStateCache.getSnapshot();
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
