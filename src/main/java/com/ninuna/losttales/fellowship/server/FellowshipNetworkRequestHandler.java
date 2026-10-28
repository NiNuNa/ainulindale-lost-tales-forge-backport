package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.model.FellowshipNames;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.storage.FellowshipWorldData;
import com.ninuna.losttales.fellowship.sync.FellowshipInvitationNotice;
import com.ninuna.losttales.fellowship.sync.FellowshipOperationType;
import com.ninuna.losttales.quest.QuestFellows;
import com.ninuna.losttales.util.LostTalesServerPlayers;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.LinkedHashSet;
import java.util.Set;
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
        FellowshipOperationResult result = null;
        FellowshipInvitationOperationResult invitation = null;
        try {
            switch (operationType) {
                case CREATE:
                    result = service.createFellowship(player, name);
                    break;
                case LEAVE:
                    result = service.leaveFellowship(player, fellowshipId, revision);
                    break;
                case REMOVE_MEMBER:
                    result = service.removeMember(player, fellowshipId, revision, targetId);
                    break;
                case DISBAND:
                    result = service.disbandFellowship(player, fellowshipId, revision);
                    break;
                case TRANSFER_LEADERSHIP:
                    result = service.transferLeadership(player, fellowshipId, revision,
                            targetId);
                    break;
                case SET_GUIDE:
                    result = service.setGuide(player, fellowshipId, revision, targetId,
                            value == 1);
                    break;
                case SET_COLOR:
                    result = service.setMemberColor(player, fellowshipId, revision, color);
                    break;
                case RENAME:
                    result = service.renameFellowship(player, fellowshipId, revision, name);
                    break;
                case SET_ICON:
                    result = service.setIcon(player, fellowshipId, revision);
                    break;
                case SET_SWITCH:
                    result = service.setSwitch(player, fellowshipId, revision,
                            FellowshipSwitch.fromNetworkId(value >> 1), (value & 1) == 1);
                    break;
                case SET_TRAVELLING:
                    result = service.setTravelling(player, fellowshipId, revision);
                    break;
                case SET_GO_HERE_MARK:
                    result = service.setGoHere(player, hasMarkerPosition,
                            markerDimensionId, markerX, markerZ);
                    break;
                case REMOVE_GO_HERE_MARK:
                    result = service.removeGoHere(player);
                    break;
                case PLACE_MARK:
                    result = service.placeMark(player, fellowshipId, revision, name,
                            markerDimensionId, markerX, markerZ);
                    break;
                case MOVE_MARK:
                    result = service.moveMark(player, fellowshipId, revision, targetId,
                            markerDimensionId, markerX, markerZ);
                    break;
                case REMOVE_MARK:
                    result = service.removeMark(player, fellowshipId, revision, targetId);
                    break;
                case INVITE_PLAYER:
                    invitation = service.invitePlayer(player, fellowshipId, revision, name);
                    break;
                case ACCEPT_INVITATION:
                    invitation = service.acceptInvitation(player, targetId);
                    break;
                case DECLINE_INVITATION:
                    invitation = service.declineInvitation(player, targetId);
                    break;
                case CANCEL_INVITATION:
                    invitation = service.cancelInvitation(player, fellowshipId, revision,
                            targetId);
                    break;
                default:
                    FellowshipSyncManager.sendFailure(player, requestId, operationType,
                            FellowshipErrorId.MALFORMED_REQUEST, -1L, true);
                    return;
            }
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Fellowship %s request failed for player %s: %s",
                    LostTalesMetaData.MOD_ID, operationType.getId(),
                    player.getUniqueID(), throwable.toString());
            FellowshipSyncManager.sendFailure(player, requestId, operationType,
                    FellowshipErrorId.INTERNAL_ERROR, -1L, true);
            return;
        }
        // The request is carried out and answered once: a fault in telling
        // the players about it is logged, never answered a second time.
        try {
            if (invitation != null) {
                finish(player, requestId, operationType, before, invitation);
                if (operationType == FellowshipOperationType.INVITE_PLAYER) {
                    tellInvited(invitation);
                }
                if (invitation.wasChanged()
                        && operationType == FellowshipOperationType.ACCEPT_INVITATION) {
                    refreshQuestFellows(player, invitation.getAffectedMember(), null);
                }
            } else if (result != null) {
                finishResult(player, requestId, operationType, expectedActiveIdentityId,
                        before, result);
            }
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Fellowship %s request for player %s was carried out, "
                            + "but telling the players failed: %s",
                    LostTalesMetaData.MOD_ID, operationType.getId(),
                    player.getUniqueID(), throwable.toString());
        }
    }

    /** Sends the answer to a request carried out, and its state to whoever it reaches. */
    private static void finishResult(EntityPlayerMP player, int requestId,
                                     FellowshipOperationType operationType,
                                     UUID expectedActiveIdentityId,
                                     FellowshipSyncManager.AudienceSnapshot before,
                                     FellowshipOperationResult result) {
        switch (operationType) {
            case SET_GO_HERE_MARK:
            case REMOVE_GO_HERE_MARK:
                finishGoHere(player, requestId, operationType, expectedActiveIdentityId,
                        result);
                return;
            case PLACE_MARK:
            case MOVE_MARK:
            case REMOVE_MARK:
                finishMark(player, requestId, operationType, before, result);
                return;
            case SET_TRAVELLING:
                finish(player, requestId, operationType, before, result);
                // Who shares the player's quest credit goes with it, and
                // the journals dim by that (QUEST-25).
                QuestFellows.refreshAround(player);
                return;
            case LEAVE:
            case REMOVE_MEMBER:
            case DISBAND:
                finish(player, requestId, operationType, before, result);
                if (result.wasChanged()) {
                    refreshQuestFellows(player, result.getAffectedMember(),
                            result.wasFellowshipDisbanded() ? result.getFellowship() : null);
                }
                return;
            default:
                finish(player, requestId, operationType, before, result);
        }
    }

    /**
     * The journal's fellows follow the members (QUEST-25): the player, the
     * member who joined, left or was sent away, and every member of a
     * fellowship that {@code ended}, each playing now, are brought up to
     * date along with whoever was shown their counts.
     */
    private static void refreshQuestFellows(EntityPlayerMP player,
                                            FellowshipMember affected,
                                            Fellowship ended) {
        Set<UUID> accounts = new LinkedHashSet<UUID>();
        accounts.add(player.getUniqueID());
        if (affected != null && !affected.isUnheld()) {
            accounts.add(affected.getOwnerId());
        }
        if (ended != null) {
            for (FellowshipMember member : ended.getMembers()) {
                if (!member.isUnheld()) {
                    accounts.add(member.getOwnerId());
                }
            }
        }
        for (UUID account : accounts) {
            EntityPlayerMP online = account.equals(player.getUniqueID()) ? player
                    : LostTalesServerPlayers.findOnline(account);
            if (online != null) {
                QuestFellows.refreshAround(online);
            }
        }
    }

    /**
     * Tells the invited player in a Server line, with Accept and Decline
     * to click, after their fellowship state has shown the invitation:
     * only while they are online playing the character invited. Otherwise
     * the invitation waits for them on the page and in the Inbox.
     */
    private static void tellInvited(FellowshipInvitationOperationResult result) {
        FellowshipInvitation invitation = result.isSuccessful()
                ? result.getInvitation() : null;
        Fellowship fellowship = result.getFellowship();
        EntityPlayerMP invited = invitation == null ? null
                : LostTalesServerPlayers.findOnline(invitation.getTargetOwnerId());
        FellowshipService.ActiveIdentityContext playing = invited == null ? null
                : FellowshipService.getInstance().resolveActiveIdentity(invited);
        if (playing != null && playing.isValid()
                && playing.gameplayId().equals(invitation.getTargetIdentityId())
                && fellowship != null) {
            invited.addChatMessage(FellowshipInvitationNotice.line(
                    FellowshipNames.component(
                            invitation.getInvitingCharacterName()),
                    fellowship.getName(), invitation.getInvitationId()));
        }
    }

    private static FellowshipErrorId validateRequestContext(
            EntityPlayerMP player,
            FellowshipOperationType operationType,
            UUID expectedActiveIdentityId,
            UUID expectedFellowshipId) {
        FellowshipService service = FellowshipService.getInstance();
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

    /**
     * As {@link #finish}, for the player's own go-here mark: everyone in a
     * fellowship with the character is sent their state, since whoever
     * travels with one of them sees it.
     */
    private static void finishGoHere(EntityPlayerMP player, int requestId,
                                     FellowshipOperationType operationType,
                                     UUID ownerIdentityId,
                                     FellowshipOperationResult result) {
        FellowshipSyncManager.sendResultAndState(player, requestId, operationType, result);
        if (result.wasChanged()) {
            FellowshipSyncManager.sendStateToAudience(
                    FellowshipSyncManager.captureCharacterFellowshipAudience(
                            player.worldObj, ownerIdentityId),
                    player.getUniqueID());
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
