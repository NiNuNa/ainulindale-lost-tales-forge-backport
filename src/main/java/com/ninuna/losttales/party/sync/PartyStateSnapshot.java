package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyInvitation;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.server.PartyInvitationState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Complete private party state for the identity one account is playing. */
public final class PartyStateSnapshot {

    public static final int MAX_INCOMING_INVITATIONS = 128;
    public static final int MAX_OUTGOING_INVITATIONS = 128;
    public static final int MAX_INVITE_TARGETS = 64;

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final PartyErrorId stateErrorId;
    /** The server's setting for how many members make a party full. */
    private final int memberLimit;
    private final UUID activeIdentityId;
    private final PartySnapshot party;
    private final List<PartyInvitationSnapshot> incomingInvitations;
    private final List<PartyInvitationSnapshot> outgoingInvitations;
    private final boolean incomingTruncated;
    private final boolean outgoingTruncated;
    private final List<PartyInviteTargetSnapshot> inviteTargets;
    private final boolean inviteTargetsTruncated;

    public PartyStateSnapshot(UUID ownerId,
                              long synchronizationSequence,
                              PartyErrorId stateErrorId,
                              int memberLimit,
                              UUID activeIdentityId,
                              PartySnapshot party,
                              List<PartyInvitationSnapshot> incomingInvitations,
                              List<PartyInvitationSnapshot> outgoingInvitations,
                              boolean incomingTruncated,
                              boolean outgoingTruncated,
                              List<PartyInviteTargetSnapshot> inviteTargets,
                              boolean inviteTargetsTruncated) {
        if (ownerId == null || synchronizationSequence <= 0L) {
            throw new IllegalArgumentException("owner and synchronization sequence are required");
        }
        if (memberLimit != Party.clampMemberLimit(memberLimit)) {
            throw new IllegalArgumentException("member limit out of range");
        }
        PartyErrorId safeError = stateErrorId == null
                ? PartyErrorId.INTERNAL_ERROR : stateErrorId;
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.stateErrorId = safeError;
        this.memberLimit = memberLimit;

        if (safeError != PartyErrorId.NONE) {
            this.activeIdentityId = null;
            this.party = null;
            this.incomingInvitations = Collections.emptyList();
            this.outgoingInvitations = Collections.emptyList();
            this.incomingTruncated = false;
            this.outgoingTruncated = false;
            this.inviteTargets = Collections.emptyList();
            this.inviteTargetsTruncated = false;
            return;
        }
        if (activeIdentityId == null) {
            throw new IllegalArgumentException("successful party state requires an active character");
        }
        if (party != null && !party.containsMember(activeIdentityId)) {
            throw new IllegalArgumentException("active character must belong to the supplied party");
        }
        this.activeIdentityId = activeIdentityId;
        this.party = party;
        this.incomingInvitations = filterIncoming(activeIdentityId, incomingInvitations);
        this.outgoingInvitations = filterOutgoing(
                activeIdentityId, party, outgoingInvitations);
        if ((incomingTruncated
                && this.incomingInvitations.size() < MAX_INCOMING_INVITATIONS)
                || (outgoingTruncated
                && this.outgoingInvitations.size() < MAX_OUTGOING_INVITATIONS)) {
            throw new IllegalArgumentException(
                    "truncated invitation snapshots must fill their packet limit");
        }
        this.incomingTruncated = incomingTruncated;
        this.outgoingTruncated = outgoingTruncated;
        this.inviteTargets = filterInviteTargets(
                activeIdentityId, party, memberLimit, inviteTargets);
        if (inviteTargetsTruncated
                && this.inviteTargets.size() < MAX_INVITE_TARGETS) {
            throw new IllegalArgumentException(
                    "truncated invite target snapshots must fill their packet limit");
        }
        this.inviteTargetsTruncated = inviteTargetsTruncated;
    }

    public static PartyStateSnapshot fromState(UUID ownerId,
                                               long synchronizationSequence,
                                               int memberLimit,
                                               PartyInvitationState state,
                                               List<PartyInviteTargetSnapshot> inviteTargets,
                                               boolean inviteTargetsTruncated) {
        if (state == null || !state.isSuccessful()) {
            PartyErrorId error = state == null
                    ? PartyErrorId.INTERNAL_ERROR : state.getErrorId();
            return failure(ownerId, synchronizationSequence, memberLimit, error);
        }
        ArrayList<PartyInvitationSnapshot> incoming = convert(
                state.getIncomingInvitations(), MAX_INCOMING_INVITATIONS);
        ArrayList<PartyInvitationSnapshot> outgoing = convert(
                state.getOutgoingInvitations(), MAX_OUTGOING_INVITATIONS);
        return new PartyStateSnapshot(
                ownerId,
                synchronizationSequence,
                PartyErrorId.NONE,
                memberLimit,
                state.getActiveIdentityId(),
                state.getParty() == null ? null
                        : PartySnapshot.fromParty(state.getParty()),
                incoming,
                outgoing,
                state.getIncomingInvitations().size() > incoming.size(),
                state.getOutgoingInvitations().size() > outgoing.size(),
                inviteTargets,
                inviteTargetsTruncated);
    }

    public static PartyStateSnapshot failure(UUID ownerId,
                                             long synchronizationSequence,
                                             int memberLimit,
                                             PartyErrorId errorId) {
        if (errorId == null || errorId == PartyErrorId.NONE) {
            errorId = PartyErrorId.INTERNAL_ERROR;
        }
        return new PartyStateSnapshot(
                ownerId,
                synchronizationSequence,
                errorId,
                memberLimit,
                null,
                null,
                Collections.<PartyInvitationSnapshot>emptyList(),
                Collections.<PartyInvitationSnapshot>emptyList(),
                false,
                false,
                Collections.<PartyInviteTargetSnapshot>emptyList(),
                false);
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public long getSynchronizationSequence() {
        return this.synchronizationSequence;
    }

    public PartyErrorId getStateErrorId() {
        return this.stateErrorId;
    }

    public boolean isAvailable() {
        return this.stateErrorId == PartyErrorId.NONE;
    }

    /** The server's setting for how many members make a party full. */
    public int getMemberLimit() {
        return this.memberLimit;
    }

    /** Whether the player's party has as many members as the setting allows, or more. */
    public boolean isPartyFull() {
        return this.party != null
                && this.party.getMemberCount() >= this.memberLimit;
    }

    public UUID getActiveIdentityId() {
        return this.activeIdentityId;
    }

    public PartySnapshot getParty() {
        return this.party;
    }

    public List<PartyInvitationSnapshot> getIncomingInvitations() {
        return this.incomingInvitations;
    }

    public List<PartyInvitationSnapshot> getOutgoingInvitations() {
        return this.outgoingInvitations;
    }

    public boolean isIncomingTruncated() {
        return this.incomingTruncated;
    }

    public boolean isOutgoingTruncated() {
        return this.outgoingTruncated;
    }

    public List<PartyInviteTargetSnapshot> getInviteTargets() {
        return this.inviteTargets;
    }

    public boolean isInviteTargetsTruncated() {
        return this.inviteTargetsTruncated;
    }

    private static ArrayList<PartyInvitationSnapshot> convert(
            List<PartyInvitation> source, int maximum) {
        ArrayList<PartyInvitationSnapshot> result =
                new ArrayList<PartyInvitationSnapshot>();
        if (source != null) {
            for (PartyInvitation invitation : source) {
                if (invitation == null || result.size() >= maximum) {
                    break;
                }
                result.add(PartyInvitationSnapshot.fromInvitation(invitation));
            }
        }
        return result;
    }

    private static List<PartyInvitationSnapshot> filterIncoming(
            UUID activeIdentityId,
            List<PartyInvitationSnapshot> source) {
        ArrayList<PartyInvitationSnapshot> accepted =
                new ArrayList<PartyInvitationSnapshot>();
        Set<UUID> invitationIds = new HashSet<UUID>();
        if (source != null) {
            for (PartyInvitationSnapshot invitation : source) {
                if (invitation == null
                        || accepted.size() >= MAX_INCOMING_INVITATIONS
                        || !activeIdentityId.equals(invitation.getTargetIdentityId())
                        || !invitationIds.add(invitation.getInvitationId())) {
                    continue;
                }
                accepted.add(invitation);
            }
        }
        return Collections.unmodifiableList(accepted);
    }

    private static List<PartyInvitationSnapshot> filterOutgoing(
            UUID activeIdentityId,
            PartySnapshot party,
            List<PartyInvitationSnapshot> source) {
        if (party == null || !party.isLeader(activeIdentityId)) {
            return Collections.emptyList();
        }
        ArrayList<PartyInvitationSnapshot> accepted =
                new ArrayList<PartyInvitationSnapshot>();
        Set<UUID> invitationIds = new HashSet<UUID>();
        if (source != null) {
            for (PartyInvitationSnapshot invitation : source) {
                if (invitation == null
                        || accepted.size() >= MAX_OUTGOING_INVITATIONS
                        || !party.getPartyId().equals(invitation.getPartyId())
                        || !invitationIds.add(invitation.getInvitationId())) {
                    continue;
                }
                accepted.add(invitation);
            }
        }
        return Collections.unmodifiableList(accepted);
    }

    /**
     * The players the leader of a party with room may invite: none already
     * in the party, and no account one of whose identities is.
     */
    private static List<PartyInviteTargetSnapshot> filterInviteTargets(
            UUID activeIdentityId,
            PartySnapshot party,
            int memberLimit,
            List<PartyInviteTargetSnapshot> source) {
        if (party == null || party.getMemberCount() >= memberLimit
                || !party.isLeader(activeIdentityId)) {
            return Collections.emptyList();
        }
        ArrayList<PartyInviteTargetSnapshot> accepted =
                new ArrayList<PartyInviteTargetSnapshot>();
        Set<UUID> ownerIds = new HashSet<UUID>();
        Set<UUID> identityIds = new HashSet<UUID>();
        if (source != null) {
            for (PartyInviteTargetSnapshot target : source) {
                if (target == null
                        || accepted.size() >= MAX_INVITE_TARGETS
                        || activeIdentityId.equals(target.getIdentityId())
                        || party.containsMember(target.getIdentityId())
                        || party.hasMemberOwnedBy(target.getOwnerId())
                        || !ownerIds.add(target.getOwnerId())
                        || !identityIds.add(target.getIdentityId())) {
                    continue;
                }
                accepted.add(target);
            }
        }
        return Collections.unmodifiableList(accepted);
    }
}
