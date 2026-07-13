package com.ninuna.losttales.party.server;

import com.ninuna.losttales.party.model.PartyColor;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Narrow logical-server facade for validated party actions.
 *
 * Packet handlers must pass through PartyServerPacketDispatcher before calling
 * this class. The dispatcher owns request throttling so each packet consumes
 * exactly one rate-limit token before it enters the bounded server task queue.
 */
public final class PartyServerActionService {

    private PartyServerActionService() {}

    public static PartyInvitationState getState(EntityPlayerMP player) {
        return PartyService.getInstance().getInvitationState(player);
    }

    public static PartyOperationResult createParty(EntityPlayerMP player) {
        return PartyService.getInstance().createParty(player);
    }

    public static PartyOperationResult leaveParty(EntityPlayerMP player,
                                                  long expectedPartyRevision) {
        return PartyService.getInstance().leaveParty(
                player, expectedPartyRevision);
    }

    public static PartyOperationResult removeMember(EntityPlayerMP player,
                                                     long expectedPartyRevision,
                                                     UUID targetCharacterId) {
        return PartyService.getInstance().removeMember(
                player, expectedPartyRevision, targetCharacterId);
    }

    public static PartyOperationResult disbandParty(EntityPlayerMP player,
                                                    long expectedPartyRevision) {
        return PartyService.getInstance().disbandParty(
                player, expectedPartyRevision);
    }

    public static PartyOperationResult transferLeadership(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetCharacterId) {
        return PartyService.getInstance().transferLeadership(
                player, expectedPartyRevision, targetCharacterId);
    }

    public static PartyOperationResult setMemberColor(
            EntityPlayerMP player,
            long expectedPartyRevision,
            PartyColor color) {
        return PartyService.getInstance().setMemberColor(
                player, expectedPartyRevision, color);
    }

    public static PartyOperationResult setGoHereMarker(
            EntityPlayerMP player,
            long expectedPartyRevision,
            boolean hasMarkerPosition,
            int markerDimensionId,
            double markerX,
            double markerZ) {
        return PartyService.getInstance().setGoHereMarker(
                player, expectedPartyRevision, hasMarkerPosition,
                markerDimensionId, markerX, markerZ);
    }

    public static PartyOperationResult removeGoHereMarker(
            EntityPlayerMP player,
            long expectedPartyRevision) {
        return PartyService.getInstance().removeGoHereMarker(
                player, expectedPartyRevision);
    }

    public static PartyInvitationOperationResult invitePlayer(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID targetOwnerId) {
        return PartyService.getInstance().invitePlayer(
                player, expectedPartyRevision, targetOwnerId);
    }

    public static PartyInvitationOperationResult acceptInvitation(
            EntityPlayerMP player, UUID invitationId) {
        return PartyService.getInstance().acceptInvitation(player, invitationId);
    }

    public static PartyInvitationOperationResult declineInvitation(
            EntityPlayerMP player, UUID invitationId) {
        return PartyService.getInstance().declineInvitation(player, invitationId);
    }

    public static PartyInvitationOperationResult cancelInvitation(
            EntityPlayerMP player,
            long expectedPartyRevision,
            UUID invitationId) {
        return PartyService.getInstance().cancelInvitation(
                player, expectedPartyRevision, invitationId);
    }
}
