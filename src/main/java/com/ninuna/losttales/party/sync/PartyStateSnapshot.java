package com.ninuna.losttales.party.sync;

import com.ninuna.losttales.party.model.PartyInvitation;
import com.ninuna.losttales.party.server.PartyErrorId;
import com.ninuna.losttales.party.server.PartyInvitationState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Complete private party state for one account's active character. */
public final class PartyStateSnapshot {

    public static final int MAX_INCOMING_INVITATIONS = 128;
    public static final int MAX_OUTGOING_INVITATIONS = 128;
    public static final int MAX_INVITE_TARGETS = 64;

    private final UUID ownerId;
    private final long synchronizationSequence;
    private final PartyErrorId stateErrorId;
    private final UUID activeCharacterId;
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
                              UUID activeCharacterId,
                              PartySnapshot party,
                              List<PartyInvitationSnapshot> incomingInvitations,
                              List<PartyInvitationSnapshot> outgoingInvitations,
                              boolean incomingTruncated,
                              boolean outgoingTruncated) {
        this(ownerId, synchronizationSequence, stateErrorId, activeCharacterId,
                party, incomingInvitations, outgoingInvitations,
                incomingTruncated, outgoingTruncated,
                Collections.<PartyInviteTargetSnapshot>emptyList(), false);
    }

    public PartyStateSnapshot(UUID ownerId,
                              long synchronizationSequence,
                              PartyErrorId stateErrorId,
                              UUID activeCharacterId,
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
        PartyErrorId safeError = stateErrorId == null
                ? PartyErrorId.INTERNAL_ERROR : stateErrorId;
        this.ownerId = ownerId;
        this.synchronizationSequence = synchronizationSequence;
        this.stateErrorId = safeError;

        if (safeError != PartyErrorId.NONE) {
            this.activeCharacterId = null;
            this.party = null;
            this.incomingInvitations = Collections.emptyList();
            this.outgoingInvitations = Collections.emptyList();
            this.incomingTruncated = false;
            this.outgoingTruncated = false;
            this.inviteTargets = Collections.emptyList();
            this.inviteTargetsTruncated = false;
            return;
        }
        if (activeCharacterId == null) {
            throw new IllegalArgumentException("successful party state requires an active character");
        }
        if (party != null && !party.containsMember(activeCharacterId)) {
            throw new IllegalArgumentException("active character must belong to the supplied party");
        }
        this.activeCharacterId = activeCharacterId;
        this.party = party;
        this.incomingInvitations = filterIncoming(activeCharacterId, incomingInvitations);
        this.outgoingInvitations = filterOutgoing(
                activeCharacterId, party, outgoingInvitations);
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
                activeCharacterId, party, inviteTargets);
        if (inviteTargetsTruncated
                && this.inviteTargets.size() < MAX_INVITE_TARGETS) {
            throw new IllegalArgumentException(
                    "truncated invite target snapshots must fill their packet limit");
        }
        this.inviteTargetsTruncated = inviteTargetsTruncated;
    }

    public static PartyStateSnapshot fromState(UUID ownerId,
                                               long synchronizationSequence,
                                               PartyInvitationState state) {
        return fromState(ownerId, synchronizationSequence, state,
                Collections.<PartyInviteTargetSnapshot>emptyList(), false);
    }

    public static PartyStateSnapshot fromState(UUID ownerId,
                                               long synchronizationSequence,
                                               PartyInvitationState state,
                                               List<PartyInviteTargetSnapshot> inviteTargets,
                                               boolean inviteTargetsTruncated) {
        if (state == null || !state.isSuccessful()) {
            PartyErrorId error = state == null
                    ? PartyErrorId.INTERNAL_ERROR : state.getErrorId();
            return failure(ownerId, synchronizationSequence, error);
        }
        ArrayList<PartyInvitationSnapshot> incoming = convert(
                state.getIncomingInvitations(), MAX_INCOMING_INVITATIONS);
        ArrayList<PartyInvitationSnapshot> outgoing = convert(
                state.getOutgoingInvitations(), MAX_OUTGOING_INVITATIONS);
        return new PartyStateSnapshot(
                ownerId,
                synchronizationSequence,
                PartyErrorId.NONE,
                state.getActiveCharacterId(),
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
                                             PartyErrorId errorId) {
        if (errorId == null || errorId == PartyErrorId.NONE) {
            errorId = PartyErrorId.INTERNAL_ERROR;
        }
        return new PartyStateSnapshot(
                ownerId,
                synchronizationSequence,
                errorId,
                null,
                null,
                Collections.<PartyInvitationSnapshot>emptyList(),
                Collections.<PartyInvitationSnapshot>emptyList(),
                false,
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

    public UUID getActiveCharacterId() {
        return this.activeCharacterId;
    }

    public PartySnapshot getParty() {
        return this.party;
    }

    public long getPartyRevision() {
        return this.party == null ? -1L : this.party.getRevision();
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
            UUID activeCharacterId,
            List<PartyInvitationSnapshot> source) {
        ArrayList<PartyInvitationSnapshot> accepted =
                new ArrayList<PartyInvitationSnapshot>();
        Set<UUID> invitationIds = new HashSet<UUID>();
        if (source != null) {
            for (PartyInvitationSnapshot invitation : source) {
                if (invitation == null
                        || accepted.size() >= MAX_INCOMING_INVITATIONS
                        || !activeCharacterId.equals(invitation.getTargetCharacterId())
                        || !invitationIds.add(invitation.getInvitationId())) {
                    continue;
                }
                accepted.add(invitation);
            }
        }
        return Collections.unmodifiableList(accepted);
    }

    private static List<PartyInvitationSnapshot> filterOutgoing(
            UUID activeCharacterId,
            PartySnapshot party,
            List<PartyInvitationSnapshot> source) {
        if (party == null || !party.isLeader(activeCharacterId)) {
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

    private static List<PartyInviteTargetSnapshot> filterInviteTargets(
            UUID activeCharacterId,
            PartySnapshot party,
            List<PartyInviteTargetSnapshot> source) {
        if (party == null || party.isFull()
                || !party.isLeader(activeCharacterId)) {
            return Collections.emptyList();
        }
        ArrayList<PartyInviteTargetSnapshot> accepted =
                new ArrayList<PartyInviteTargetSnapshot>();
        Set<UUID> ownerIds = new HashSet<UUID>();
        Set<UUID> characterIds = new HashSet<UUID>();
        if (source != null) {
            for (PartyInviteTargetSnapshot target : source) {
                if (target == null
                        || accepted.size() >= MAX_INVITE_TARGETS
                        || activeCharacterId.equals(target.getCharacterId())
                        || party.containsMember(target.getCharacterId())
                        || !ownerIds.add(target.getOwnerId())
                        || !characterIds.add(target.getCharacterId())) {
                    continue;
                }
                accepted.add(target);
            }
        }
        return Collections.unmodifiableList(accepted);
    }
}
