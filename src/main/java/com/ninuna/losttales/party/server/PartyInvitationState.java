package com.ninuna.losttales.party.server;

import com.ninuna.losttales.party.model.Party;
import com.ninuna.losttales.party.model.PartyInvitation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/** Authorized server-side invitation view for one active character. */
public final class PartyInvitationState {

    private final boolean successful;
    private final PartyErrorId errorId;
    private final UUID activeCharacterId;
    private final Party party;
    private final List<PartyInvitation> incomingInvitations;
    private final List<PartyInvitation> outgoingInvitations;

    private PartyInvitationState(boolean successful,
                                 PartyErrorId errorId,
                                 UUID activeCharacterId,
                                 Party party,
                                 List<PartyInvitation> incomingInvitations,
                                 List<PartyInvitation> outgoingInvitations) {
        this.successful = successful;
        this.errorId = errorId == null
                ? PartyErrorId.INTERNAL_ERROR : errorId;
        this.activeCharacterId = activeCharacterId;
        this.party = party;
        this.incomingInvitations = immutableCopy(incomingInvitations);
        this.outgoingInvitations = immutableCopy(outgoingInvitations);
    }

    public static PartyInvitationState success(
            UUID activeCharacterId,
            Party party,
            List<PartyInvitation> incomingInvitations,
            List<PartyInvitation> outgoingInvitations) {
        return new PartyInvitationState(
                true,
                PartyErrorId.NONE,
                activeCharacterId,
                party,
                incomingInvitations,
                outgoingInvitations);
    }

    public static PartyInvitationState failure(PartyErrorId errorId) {
        if (errorId == null || errorId == PartyErrorId.NONE) {
            errorId = PartyErrorId.INTERNAL_ERROR;
        }
        return new PartyInvitationState(
                false,
                errorId,
                null,
                null,
                Collections.<PartyInvitation>emptyList(),
                Collections.<PartyInvitation>emptyList());
    }

    public boolean isSuccessful() {
        return this.successful;
    }

    public PartyErrorId getErrorId() {
        return this.errorId;
    }

    public UUID getActiveCharacterId() {
        return this.activeCharacterId;
    }

    public Party getParty() {
        return this.party;
    }

    public List<PartyInvitation> getIncomingInvitations() {
        return this.incomingInvitations;
    }

    public List<PartyInvitation> getOutgoingInvitations() {
        return this.outgoingInvitations;
    }

    private static List<PartyInvitation> immutableCopy(
            List<PartyInvitation> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(
                new ArrayList<PartyInvitation>(source));
    }
}
