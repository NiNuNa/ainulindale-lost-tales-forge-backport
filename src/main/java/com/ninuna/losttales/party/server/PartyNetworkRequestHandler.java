package com.ninuna.losttales.party.server;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyColor;
import com.ninuna.losttales.party.storage.PartyWorldData;
import com.ninuna.losttales.party.sync.PartyOperationType;
import cpw.mods.fml.common.FMLLog;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/** Executes queued party requests on the logical server thread. */
public final class PartyNetworkRequestHandler {

    private PartyNetworkRequestHandler() {}

    public static void handleAction(EntityPlayerMP player,
                                    int requestId,
                                    PartyOperationType operationType,
                                    UUID expectedActiveCharacterId,
                                    UUID expectedPartyId,
                                    long expectedPartyRevision,
                                    UUID targetId,
                                    PartyColor color,
                                    boolean hasMarkerPosition,
                                    int markerDimensionId,
                                    double markerX,
                                    double markerZ) {
        if (player == null || operationType == null) {
            return;
        }
        if (operationType == PartyOperationType.REQUEST_STATE) {
            PartySyncManager.sendState(player, requestId);
            return;
        }

        PartyErrorId contextError = validateRequestContext(
                player,
                operationType,
                expectedActiveCharacterId,
                expectedPartyId);
        if (contextError != PartyErrorId.NONE) {
            PartySyncManager.sendFailure(
                    player,
                    requestId,
                    operationType,
                    contextError,
                    -1L,
                    true);
            return;
        }

        PartySyncManager.AudienceSnapshot before =
                PartySyncManager.captureActivePartyAudience(player);
        if (operationType == PartyOperationType.ACCEPT_INVITATION
                || operationType == PartyOperationType.DECLINE_INVITATION
                || operationType == PartyOperationType.CANCEL_INVITATION) {
            before = PartySyncManager.combineAudiences(
                    before,
                    PartySyncManager.captureInvitationAudience(
                            player.worldObj, targetId));
        }
        try {
            switch (operationType) {
                case CREATE:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.createParty(player));
                    return;
                case LEAVE:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.leaveParty(
                                    player, expectedPartyRevision));
                    return;
                case REMOVE_MEMBER:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.removeMember(
                                    player, expectedPartyRevision, targetId));
                    return;
                case DISBAND:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.disbandParty(
                                    player, expectedPartyRevision));
                    return;
                case TRANSFER_LEADERSHIP:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.transferLeadership(
                                    player, expectedPartyRevision, targetId));
                    return;
                case SET_COLOR:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.setMemberColor(
                                    player, expectedPartyRevision, color));
                    return;
                case SET_GO_HERE_MARKER:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.setGoHereMarker(
                                    player, expectedPartyRevision,
                                    hasMarkerPosition, markerDimensionId,
                                    markerX, markerZ));
                    return;
                case REMOVE_GO_HERE_MARKER:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.removeGoHereMarker(
                                    player, expectedPartyRevision));
                    return;
                case INVITE_PLAYER:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.invitePlayer(
                                    player, expectedPartyRevision, targetId));
                    return;
                case ACCEPT_INVITATION:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.acceptInvitation(
                                    player, targetId));
                    return;
                case DECLINE_INVITATION:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.declineInvitation(
                                    player, targetId));
                    return;
                case CANCEL_INVITATION:
                    finish(player, requestId, operationType, before,
                            PartyServerActionService.cancelInvitation(
                                    player, expectedPartyRevision, targetId));
                    return;
                default:
                    PartySyncManager.sendFailure(
                            player,
                            requestId,
                            operationType,
                            PartyErrorId.MALFORMED_REQUEST,
                            -1L,
                            true);
            }
        } catch (Throwable throwable) {
            FMLLog.warning("[%s] Party %s request failed for player %s: %s",
                    LostTalesMetaData.MOD_ID,
                    operationType.getId(),
                    player.getUniqueID(),
                    throwable.toString());
            PartySyncManager.sendFailure(
                    player,
                    requestId,
                    operationType,
                    PartyErrorId.INTERNAL_ERROR,
                    -1L,
                    true);
        }
    }

    private static PartyErrorId validateRequestContext(
            EntityPlayerMP player,
            PartyOperationType operationType,
            UUID expectedActiveCharacterId,
            UUID expectedPartyId) {
        PartyService service = PartyService.getInstance();
        PartyService.ActiveCharacterContext active =
                service.resolveActiveCharacter(player);
        if (!active.isValid()) {
            return active.errorId;
        }
        if (expectedActiveCharacterId == null
                || !expectedActiveCharacterId.equals(
                active.character.getCharacterId())) {
            return PartyErrorId.ACTIVE_CHARACTER_CHANGED;
        }
        if (!operationType.requiresPartyRevision()) {
            return PartyErrorId.NONE;
        }
        PartyWorldData partyData = service.getPartyData(player.worldObj);
        if (partyData == null) {
            return PartyErrorId.INTERNAL_ERROR;
        }
        if (partyData.isReadOnlyForNewerVersion()) {
            return PartyErrorId.PARTY_STORAGE_READ_ONLY;
        }
        Party party = partyData.getPartyForCharacter(
                active.character.getCharacterId());
        if (party == null) {
            return PartyErrorId.NOT_IN_PARTY;
        }
        return expectedPartyId != null
                && expectedPartyId.equals(party.getPartyId())
                ? PartyErrorId.NONE
                : PartyErrorId.STALE_PARTY_CONTEXT;
    }

    private static void finish(EntityPlayerMP player,
                               int requestId,
                               PartyOperationType operationType,
                               PartySyncManager.AudienceSnapshot before,
                               PartyOperationResult result) {
        PartySyncManager.sendResultAndState(
                player, requestId, operationType, result);
        if (result.wasChanged()) {
            PartySyncManager.AudienceSnapshot audience =
                    PartySyncManager.collectAudience(
                            before,
                            result.getParty(),
                            result.getAffectedMember(),
                            null);
            PartySyncManager.sendStateToAudience(
                    audience, player.getUniqueID());
        }
    }

    private static void finish(EntityPlayerMP player,
                               int requestId,
                               PartyOperationType operationType,
                               PartySyncManager.AudienceSnapshot before,
                               PartyInvitationOperationResult result) {
        PartySyncManager.sendResultAndState(
                player, requestId, operationType, result);
        if (result.wasChanged()) {
            PartySyncManager.AudienceSnapshot audience =
                    PartySyncManager.collectAudience(
                            before,
                            result.getParty(),
                            result.getAffectedMember(),
                            result.getInvitation());
            PartySyncManager.sendStateToAudience(
                    audience, player.getUniqueID());
        }
    }
}
