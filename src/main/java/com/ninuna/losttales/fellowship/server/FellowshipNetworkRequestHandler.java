package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.model.FellowshipNames;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationNotice;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Carries out queued fellowship requests on the logical server thread. Each
 * request names the identity it was made for and, where it acts on one
 * fellowship, that fellowship and the revision it saw; the service checks
 * both again. Whoever the change reaches is sent their state afterwards.
 */
public final class FellowshipNetworkRequestHandler {

    private FellowshipNetworkRequestHandler() {}

    public static void handleAction(EntityPlayerMP player,
                                    int requestId,
                                    FellowshipOperationType operationType,
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
        if (player == null || operationType == null) {
            return;
        }
        if (operationType == FellowshipOperationType.REQUEST_STATE) {
            FellowshipSyncManager.sendState(player, requestId);
            return;
        }

        FellowshipErrorId contextError = validateRequestContext(player, operationType,
                expectedActiveIdentityId, expectedFellowshipId);
        if (contextError != FellowshipErrorId.NONE) {
            FellowshipSyncManager.sendFailure(player, requestId, operationType,
                    contextError, -1L, true);
            return;
        }

        FellowshipSyncManager.AudienceSnapshot before =
                FellowshipSyncManager.captureFellowshipAudience(player.worldObj,
                        expectedFellowshipId);
        if (operationType == FellowshipOperationType.ACCEPT_INVITATION
                || operationType == FellowshipOperationType.DECLINE_INVITATION
                || operationType == FellowshipOperationType.CANCEL_INVITATION) {
            before = FellowshipSyncManager.combineAudiences(before,
                    FellowshipSyncManager.captureInvitationAudience(
                            player.worldObj, targetId));
        }
        FellowshipService service = FellowshipService.getInstance();
        UUID fellowshipId = expectedFellowshipId;
        long revision = expectedFellowshipRevision;
        try {
            switch (operationType) {
                case CREATE:
                    finish(player, requestId, operationType, before,
                            service.createFellowship(player, name));
                    return;
                case LEAVE:
                    finish(player, requestId, operationType, before,
                            service.leaveFellowship(player, fellowshipId, revision));
                    return;
                case REMOVE_MEMBER:
                    finish(player, requestId, operationType, before,
                            service.removeMember(player, fellowshipId, revision, targetId));
                    return;
                case DISBAND:
                    finish(player, requestId, operationType, before,
                            service.disbandFellowship(player, fellowshipId, revision));
                    return;
                case TRANSFER_LEADERSHIP:
                    finish(player, requestId, operationType, before,
                            service.transferLeadership(player, fellowshipId, revision,
                                    targetId));
                    return;
                case SET_GUIDE:
                    finish(player, requestId, operationType, before,
                            service.setGuide(player, fellowshipId, revision, targetId,
                                    value == 1));
                    return;
                case SET_COLOR:
                    finish(player, requestId, operationType, before,
                            service.setMemberColor(player, fellowshipId, revision, color));
                    return;
                case RENAME:
                    finish(player, requestId, operationType, before,
                            service.renameFellowship(player, fellowshipId, revision, name));
                    return;
                case SET_ICON:
                    finish(player, requestId, operationType, before,
                            service.setIcon(player, fellowshipId, revision));
                    return;
                case SET_SWITCH:
                    finish(player, requestId, operationType, before,
                            service.setSwitch(player, fellowshipId, revision,
                                    FellowshipSwitch.fromNetworkId(value >> 1),
                                    (value & 1) == 1));
                    return;
                case SET_TRAVELLING:
                    finish(player, requestId, operationType, before,
                            service.setTravelling(player, fellowshipId, revision));
                    return;
                case SET_GO_HERE_MARKER:
                    finish(player, requestId, operationType, before,
                            service.setGoHereMarker(player, hasMarkerPosition,
                                    markerDimensionId, markerX, markerZ));
                    return;
                case REMOVE_GO_HERE_MARKER:
                    finish(player, requestId, operationType, before,
                            service.removeGoHereMarker(player));
                    return;
                case PLACE_MARK:
                    finishMark(player, requestId, operationType, before,
                            service.placeMark(player, fellowshipId, revision, name,
                                    markerDimensionId, markerX, markerZ));
                    return;
                case MOVE_MARK:
                    finishMark(player, requestId, operationType, before,
                            service.moveMark(player, fellowshipId, revision, targetId,
                                    markerDimensionId, markerX, markerZ));
                    return;
                case REMOVE_MARK:
                    finishMark(player, requestId, operationType, before,
                            service.removeMark(player, fellowshipId, revision, targetId));
                    return;
                case INVITE_PLAYER: {
                    FellowshipInvitationOperationResult invited =
                            service.invitePlayer(player, fellowshipId, revision, targetId);
                    finish(player, requestId, operationType, before, invited);
                    tellInvited(invited);
                    return;
                }
                case ACCEPT_INVITATION:
                    finish(player, requestId, operationType, before,
                            service.acceptInvitation(player, targetId));
                    return;
                case DECLINE_INVITATION:
                    finish(player, requestId, operationType, before,
                            service.declineInvitation(player, targetId));
                    return;
                case CANCEL_INVITATION:
                    finish(player, requestId, operationType, before,
                            service.cancelInvitation(player, fellowshipId, revision,
                                    targetId));
                    return;
                default:
                    FellowshipSyncManager.sendFailure(player, requestId, operationType,
                            FellowshipErrorId.MALFORMED_REQUEST, -1L, true);
            }
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Fellowship %s request failed for player %s: %s",
                    LostTalesMetaData.MOD_ID, operationType.getId(),
                    player.getUniqueID(), throwable.toString());
            FellowshipSyncManager.sendFailure(player, requestId, operationType,
                    FellowshipErrorId.INTERNAL_ERROR, -1L, true);
        }
    }

    /**
     * Tells the invited player in a Server line, with Accept and Decline
     * to click, after their fellowship state has shown the invitation.
     */
    private static void tellInvited(FellowshipInvitationOperationResult result) {
        FellowshipInvitation invitation = result.isSuccessful()
                ? result.getInvitation() : null;
        Fellowship fellowship = result.getFellowship();
        EntityPlayerMP invited = invitation == null ? null
                : LostTalesServerPlayers.findOnline(invitation.getTargetOwnerId());
        if (invited != null && fellowship != null) {
            invited.addChatMessage(FellowshipInvitationNotice.line(
                    FellowshipNames.component(
                            invitation.getInvitingCharacterName()),
                    fellowship.getName(), invitation.getInvitationId()));
        }
    }

    /** Operations on the requester's own marker, which needs no character. */
    private static boolean isPersonalMarkerOperation(FellowshipOperationType operationType) {
        return operationType == FellowshipOperationType.SET_GO_HERE_MARKER
                || operationType == FellowshipOperationType.REMOVE_GO_HERE_MARKER;
    }

    private static FellowshipErrorId validateRequestContext(
            EntityPlayerMP player,
            FellowshipOperationType operationType,
            UUID expectedActiveIdentityId,
            UUID expectedFellowshipId) {
        FellowshipService service = FellowshipService.getInstance();
        if (isPersonalMarkerOperation(operationType)) {
            // A personal marker may be owned by the player when they have no
            // character, so this operation is checked against whoever the
            // server says owns it rather than against a character alone.
            FellowshipService.PersonalMarkerContext owner =
                    service.resolvePersonalMarkerOwner(player);
            if (!owner.isValid()) {
                return owner.errorId;
            }
            return expectedActiveIdentityId != null
                    && expectedActiveIdentityId.equals(owner.ownerId)
                    ? FellowshipErrorId.NONE
                    : FellowshipErrorId.ACTIVE_CHARACTER_CHANGED;
        }
        FellowshipService.ActiveIdentityContext active = service.resolveActiveIdentity(player);
        if (!active.isValid()) {
            return active.errorId;
        }
        // The request names the identity it was made for; a switch to
        // another character, or to or from the account, has changed it.
        if (expectedActiveIdentityId == null
                || !expectedActiveIdentityId.equals(active.gameplayId())) {
            return FellowshipErrorId.ACTIVE_CHARACTER_CHANGED;
        }
        if (!operationType.requiresFellowshipRevision()) {
            return FellowshipErrorId.NONE;
        }
        FellowshipWorldData fellowshipData = service.getFellowshipData(player.worldObj);
        if (fellowshipData == null) {
            return FellowshipErrorId.INTERNAL_ERROR;
        }
        if (fellowshipData.isReadOnlyForNewerVersion()) {
            return FellowshipErrorId.FELLOWSHIP_STORAGE_READ_ONLY;
        }
        return fellowshipData.isMember(active.gameplayId(), expectedFellowshipId)
                ? FellowshipErrorId.NONE
                : FellowshipErrorId.STALE_FELLOWSHIP_CONTEXT;
    }

    private static void finish(EntityPlayerMP player, int requestId,
                               FellowshipOperationType operationType,
                               FellowshipSyncManager.AudienceSnapshot before,
                               FellowshipOperationResult result) {
        FellowshipSyncManager.sendResultAndState(player, requestId, operationType, result);
        if (result.wasChanged()) {
            FellowshipSyncManager.sendStateToAudience(FellowshipSyncManager.collectAudience(
                    before, result.getFellowship(), result.getAffectedMember(), null),
                    player.getUniqueID());
            mirror(player, result.getFellowship());
        }
    }

    private static void finish(EntityPlayerMP player, int requestId,
                               FellowshipOperationType operationType,
                               FellowshipSyncManager.AudienceSnapshot before,
                               FellowshipInvitationOperationResult result) {
        FellowshipSyncManager.sendResultAndState(player, requestId, operationType, result);
        if (result.wasChanged()) {
            FellowshipSyncManager.sendStateToAudience(FellowshipSyncManager.collectAudience(
                    before, result.getFellowship(), result.getAffectedMember(),
                    result.getInvitation()), player.getUniqueID());
            mirror(player, result.getFellowship());
        }
    }

    /** As {@link #finish}, and the fellowship's conversation is told what became of the mark. */
    private static void finishMark(EntityPlayerMP player, int requestId,
                                   FellowshipOperationType operationType,
                                   FellowshipSyncManager.AudienceSnapshot before,
                                   FellowshipOperationResult result) {
        finish(player, requestId, operationType, before, result);
        if (result.isSuccessful()) {
            FellowshipMarkNotice.tell(operationType, result.getFellowship(),
                    result.getAffectedMember(), result.getMark());
        }
    }

    /** LOTR's fellowship behind the one changed follows it; one that ended is ended. */
    private static void mirror(EntityPlayerMP player, Fellowship fellowship) {
        FellowshipMirrors.reconcile(player.worldObj,
                fellowship == null ? null : fellowship.getFellowshipId());
    }
}
