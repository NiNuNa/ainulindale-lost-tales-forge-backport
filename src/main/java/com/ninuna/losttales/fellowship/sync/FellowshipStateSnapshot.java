package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import com.ninuna.losttales.fellowship.server.FellowshipInvitationState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything one account may see of the fellowships of the identity it
 * plays: the fellowships, the one it travels with, whether it may make
 * another, its invitations, and whom it may invite.
 */
public final class FellowshipStateSnapshot {

    public static final int MAX_INCOMING_INVITATIONS = 128;
    public static final int MAX_OUTGOING_INVITATIONS = 128;
    public static final int MAX_INVITE_TARGETS = 64;

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final FellowshipErrorId stateErrorId;
    /** The server's setting for how many members make a fellowship full. */
    private final int memberLimit;
    private final UUID activeIdentityId;
    private final List<FellowshipSnapshot> fellowships;
    private final UUID travellingFellowshipId;
    private final FellowshipErrorId createRefusal;
    private final List<FellowshipInvitationSnapshot> incomingInvitations;
    private final List<FellowshipInvitationSnapshot> outgoingInvitations;
    private final boolean incomingTruncated;
    private final boolean outgoingTruncated;
    private final List<FellowshipInviteTargetSnapshot> inviteTargets;
    private final boolean inviteTargetsTruncated;

    public FellowshipStateSnapshot(UUID ownerId,
                                   long synchronizationSequence,
                                   FellowshipErrorId stateErrorId,
                                   int memberLimit,
                                   UUID activeIdentityId,
                                   List<FellowshipSnapshot> fellowships,
                                   UUID travellingFellowshipId,
                                   FellowshipErrorId createRefusal,
                                   List<FellowshipInvitationSnapshot> incomingInvitations,
                                   List<FellowshipInvitationSnapshot> outgoingInvitations,
                                   boolean incomingTruncated,
                                   boolean outgoingTruncated,
                                   List<FellowshipInviteTargetSnapshot> inviteTargets,
                                   boolean inviteTargetsTruncated) {
        if (ownerId == null || synchronizationSequence <= 0L) {
            throw new IllegalArgumentException("owner and synchronization sequence are required");
        }
        if (memberLimit != Fellowship.clampMemberLimit(memberLimit)) {
            throw new IllegalArgumentException("member limit out of range");
        }
        FellowshipErrorId safeError = stateErrorId == null
                ? FellowshipErrorId.INTERNAL_ERROR : stateErrorId;
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.stateErrorId = safeError;
        this.memberLimit = memberLimit;

        if (safeError != FellowshipErrorId.NONE) {
            this.activeIdentityId = null;
            this.fellowships = Collections.emptyList();
            this.travellingFellowshipId = null;
            this.createRefusal = FellowshipErrorId.NONE;
            this.incomingInvitations = Collections.emptyList();
            this.outgoingInvitations = Collections.emptyList();
            this.incomingTruncated = false;
            this.outgoingTruncated = false;
            this.inviteTargets = Collections.emptyList();
            this.inviteTargetsTruncated = false;
            return;
        }
        if (activeIdentityId == null) {
            throw new IllegalArgumentException("successful fellowship state requires an active character");
        }
        if (!isCreateRefusal(createRefusal)) {
            throw new IllegalArgumentException("invalid create refusal");
        }
        this.activeIdentityId = activeIdentityId;
        this.fellowships = acceptFellowships(activeIdentityId, fellowships);
        if (this.fellowships.isEmpty() ? travellingFellowshipId != null
                : findIn(this.fellowships, travellingFellowshipId) == null) {
            throw new IllegalArgumentException("travelling fellowship must be one of the fellowships");
        }
        this.travellingFellowshipId = travellingFellowshipId;
        this.createRefusal = createRefusal;
        this.incomingInvitations = filterIncoming(activeIdentityId, incomingInvitations);
        this.outgoingInvitations = filterOutgoing(
                activeIdentityId, this.fellowships, outgoingInvitations);
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
                activeIdentityId, this.fellowships, memberLimit, inviteTargets);
        if (inviteTargetsTruncated
                && this.inviteTargets.size() < MAX_INVITE_TARGETS) {
            throw new IllegalArgumentException(
                    "truncated invite target snapshots must fill their packet limit");
        }
        this.inviteTargetsTruncated = inviteTargetsTruncated;
    }

    public static FellowshipStateSnapshot fromState(UUID ownerId,
                                                    long synchronizationSequence,
                                                    int memberLimit,
                                                    FellowshipInvitationState state,
                                                    FellowshipSnapshot.Presence presence,
                                                    List<FellowshipInviteTargetSnapshot> inviteTargets,
                                                    boolean inviteTargetsTruncated) {
        if (state == null || !state.isSuccessful()) {
            FellowshipErrorId error = state == null
                    ? FellowshipErrorId.INTERNAL_ERROR : state.getErrorId();
            return failure(ownerId, synchronizationSequence, memberLimit, error);
        }
        ArrayList<FellowshipSnapshot> fellowships = new ArrayList<FellowshipSnapshot>();
        for (Fellowship fellowship : state.getFellowships()) {
            fellowships.add(FellowshipSnapshot.fromFellowship(fellowship, presence));
        }
        ArrayList<FellowshipInvitationSnapshot> incoming = convert(
                state, state.getIncomingInvitations(), MAX_INCOMING_INVITATIONS);
        ArrayList<FellowshipInvitationSnapshot> outgoing = convert(
                state, state.getOutgoingInvitations(), MAX_OUTGOING_INVITATIONS);
        return new FellowshipStateSnapshot(
                ownerId,
                synchronizationSequence,
                FellowshipErrorId.NONE,
                memberLimit,
                state.getActiveIdentityId(),
                fellowships,
                state.getTravellingFellowshipId(),
                state.getCreateRefusal(),
                incoming,
                outgoing,
                incoming.size() >= MAX_INCOMING_INVITATIONS
                        && state.getIncomingInvitations().size() > incoming.size(),
                outgoing.size() >= MAX_OUTGOING_INVITATIONS
                        && state.getOutgoingInvitations().size() > outgoing.size(),
                inviteTargets,
                inviteTargetsTruncated);
    }

    public static FellowshipStateSnapshot failure(UUID ownerId,
                                                  long synchronizationSequence,
                                                  int memberLimit,
                                                  FellowshipErrorId errorId) {
        if (errorId == null || errorId == FellowshipErrorId.NONE) {
            errorId = FellowshipErrorId.INTERNAL_ERROR;
        }
        return new FellowshipStateSnapshot(
                ownerId,
                synchronizationSequence,
                errorId,
                memberLimit,
                null,
                Collections.<FellowshipSnapshot>emptyList(),
                null,
                FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                false,
                false,
                Collections.<FellowshipInviteTargetSnapshot>emptyList(),
                false);
    }

    /** Whether an error can say why a fellowship may not be made; NONE says it may. */
    public static boolean isCreateRefusal(FellowshipErrorId errorId) {
        return errorId == FellowshipErrorId.NONE
                || errorId == FellowshipErrorId.CREATION_DISABLED
                || errorId == FellowshipErrorId.TOO_MANY_FELLOWSHIPS
                || errorId == FellowshipErrorId.LEAD_LIMIT_REACHED;
    }

    public UUID getOwnerId() {
        return this.ownerId;
    }

    public long getSynchronizationSequence() {
        return this.synchronizationSequence;
    }

    public FellowshipErrorId getStateErrorId() {
        return this.stateErrorId;
    }

    public boolean isAvailable() {
        return this.stateErrorId == FellowshipErrorId.NONE;
    }

    /** The server's setting for how many members make a fellowship full. */
    public int getMemberLimit() {
        return this.memberLimit;
    }

    /** Whether a fellowship has as many members as the setting allows, or more. */
    public boolean isFull(FellowshipSnapshot fellowship) {
        return fellowship != null && fellowship.getMemberCount() >= this.memberLimit;
    }

    public UUID getActiveIdentityId() {
        return this.activeIdentityId;
    }

    /** The fellowships the identity is in, in the order it joined them. */
    public List<FellowshipSnapshot> getFellowships() {
        return this.fellowships;
    }

    /** One of the identity's fellowships; null for one it is not in. */
    public FellowshipSnapshot getFellowship(UUID fellowshipId) {
        return findIn(this.fellowships, fellowshipId);
    }

    /** The fellowship the identity travels with; null while it is in none. */
    public FellowshipSnapshot getTravellingFellowship() {
        return findIn(this.fellowships, this.travellingFellowshipId);
    }

    /** Why the identity may not make a fellowship now; NONE when it may. */
    public FellowshipErrorId getCreateRefusal() {
        return this.createRefusal;
    }

    public List<FellowshipInvitationSnapshot> getIncomingInvitations() {
        return this.incomingInvitations;
    }

    public List<FellowshipInvitationSnapshot> getOutgoingInvitations() {
        return this.outgoingInvitations;
    }

    public boolean isIncomingTruncated() {
        return this.incomingTruncated;
    }

    public boolean isOutgoingTruncated() {
        return this.outgoingTruncated;
    }

    /**
     * The online players the identity may invite into one of the
     * fellowships it leads or guides; the page leaves out, for each
     * fellowship, its members and those it invited already.
     */
    public List<FellowshipInviteTargetSnapshot> getInviteTargets() {
        return this.inviteTargets;
    }

    public boolean isInviteTargetsTruncated() {
        return this.inviteTargetsTruncated;
    }

    /**
     * The players one fellowship may invite: those the server offers, but
     * its members, the accounts one of whose characters is a member, and
     * those it invited already.
     */
    public List<FellowshipInviteTargetSnapshot> getInviteTargets(FellowshipSnapshot fellowship) {
        List<FellowshipInviteTargetSnapshot> result =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        if (fellowship == null) {
            return result;
        }
        for (FellowshipInviteTargetSnapshot target : this.inviteTargets) {
            if (!fellowship.containsMember(target.getIdentityId())
                    && !fellowship.hasMemberOwnedBy(target.getOwnerId())
                    && !invited(fellowship, target)) {
                result.add(target);
            }
        }
        return result;
    }

    private boolean invited(FellowshipSnapshot fellowship, FellowshipInviteTargetSnapshot target) {
        for (FellowshipInvitationSnapshot invitation : this.outgoingInvitations) {
            if (invitation.getFellowshipId().equals(fellowship.getFellowshipId())
                    && invitation.getTargetIdentityId().equals(target.getIdentityId())) {
                return true;
            }
        }
        return false;
    }

    private static FellowshipSnapshot findIn(List<FellowshipSnapshot> fellowships,
                                             UUID fellowshipId) {
        if (fellowshipId == null) {
            return null;
        }
        for (FellowshipSnapshot fellowship : fellowships) {
            if (fellowshipId.equals(fellowship.getFellowshipId())) {
                return fellowship;
            }
        }
        return null;
    }

    private static List<FellowshipSnapshot> acceptFellowships(
            UUID activeIdentityId, List<FellowshipSnapshot> source) {
        ArrayList<FellowshipSnapshot> accepted = new ArrayList<FellowshipSnapshot>();
        Set<UUID> fellowshipIds = new HashSet<UUID>();
        if (source != null) {
            for (FellowshipSnapshot fellowship : source) {
                if (fellowship == null || accepted.size() >= Fellowship.MAX_FELLOWSHIPS_PER_IDENTITY
                        || !fellowship.containsMember(activeIdentityId)
                        || !fellowshipIds.add(fellowship.getFellowshipId())) {
                    throw new IllegalArgumentException(
                            "fellowships must be distinct, bounded and hold the active identity");
                }
                accepted.add(fellowship);
            }
        }
        return Collections.unmodifiableList(accepted);
    }

    private static ArrayList<FellowshipInvitationSnapshot> convert(
            FellowshipInvitationState state, List<FellowshipInvitation> source, int maximum) {
        ArrayList<FellowshipInvitationSnapshot> result =
                new ArrayList<FellowshipInvitationSnapshot>();
        for (FellowshipInvitation invitation : source) {
            if (result.size() >= maximum) {
                break;
            }
            String name = state.getFellowshipName(invitation.getFellowshipId());
            if (name != null) {
                result.add(FellowshipInvitationSnapshot.fromInvitation(invitation, name));
            }
        }
        return result;
    }

    private static List<FellowshipInvitationSnapshot> filterIncoming(
            UUID activeIdentityId,
            List<FellowshipInvitationSnapshot> source) {
        ArrayList<FellowshipInvitationSnapshot> accepted =
                new ArrayList<FellowshipInvitationSnapshot>();
        Set<UUID> invitationIds = new HashSet<UUID>();
        if (source != null) {
            for (FellowshipInvitationSnapshot invitation : source) {
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

    /** The invitations sent by the fellowships the identity leads or guides. */
    private static List<FellowshipInvitationSnapshot> filterOutgoing(
            UUID activeIdentityId,
            List<FellowshipSnapshot> fellowships,
            List<FellowshipInvitationSnapshot> source) {
        ArrayList<FellowshipInvitationSnapshot> accepted =
                new ArrayList<FellowshipInvitationSnapshot>();
        Set<UUID> invitationIds = new HashSet<UUID>();
        if (source != null) {
            for (FellowshipInvitationSnapshot invitation : source) {
                FellowshipSnapshot fellowship = invitation == null ? null
                        : findIn(fellowships, invitation.getFellowshipId());
                if (fellowship == null
                        || accepted.size() >= MAX_OUTGOING_INVITATIONS
                        || !fellowship.canManage(activeIdentityId)
                        || !invitationIds.add(invitation.getInvitationId())) {
                    continue;
                }
                accepted.add(invitation);
            }
        }
        return Collections.unmodifiableList(accepted);
    }

    /** No one while the identity leads or guides no fellowship with room; never itself. */
    private static List<FellowshipInviteTargetSnapshot> filterInviteTargets(
            UUID activeIdentityId,
            List<FellowshipSnapshot> fellowships,
            int memberLimit,
            List<FellowshipInviteTargetSnapshot> source) {
        boolean managesOneWithRoom = false;
        for (FellowshipSnapshot fellowship : fellowships) {
            if (fellowship.canManage(activeIdentityId)
                    && fellowship.getMemberCount() < memberLimit) {
                managesOneWithRoom = true;
            }
        }
        if (!managesOneWithRoom) {
            return Collections.emptyList();
        }
        ArrayList<FellowshipInviteTargetSnapshot> accepted =
                new ArrayList<FellowshipInviteTargetSnapshot>();
        Set<UUID> ownerIds = new HashSet<UUID>();
        Set<UUID> identityIds = new HashSet<UUID>();
        if (source != null) {
            for (FellowshipInviteTargetSnapshot target : source) {
                if (target == null
                        || accepted.size() >= MAX_INVITE_TARGETS
                        || activeIdentityId.equals(target.getIdentityId())
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
