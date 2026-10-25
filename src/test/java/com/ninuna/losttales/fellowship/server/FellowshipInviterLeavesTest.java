package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipInvitation;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.storage.FellowshipInvitationWorldData;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * An invitation stands for someone who may still invite: a guide made a
 * plain member takes back the invitations they sent at once (FEL-33), as
 * a leader handing on the lead does; everyone else's stay.
 */
public final class FellowshipInviterLeavesTest {

    private static final UUID FELLOWSHIP = new UUID(2L, 2L);

    private final FellowshipMember aldric = member("Aldric", 1L, FellowshipColor.GREEN);
    private final FellowshipMember beren = member("Beren", 2L, FellowshipColor.BLUE);
    private final FellowshipMember celeb = member("Celeb", 3L, FellowshipColor.RED);

    @Test
    public void aGuideMadeAPlainMemberTakesBackTheirInvitations() {
        Fellowship grey = company();
        FellowshipInvitationWorldData invitations = store();
        invitations.saveInvitation(invitation(1, beren));
        invitations.saveInvitation(invitation(2, beren));
        invitations.saveInvitation(invitation(3, aldric));

        assertTrue(FellowshipService.changeGuide(grey, invitations,
                beren.getIdentityId(), false));
        assertFalse(grey.isGuide(beren.getIdentityId()));
        assertNull(invitations.getInvitation(new UUID(900L, 1L)));
        assertNull(invitations.getInvitation(new UUID(900L, 2L)));
        assertNotNull("the leader's stays", invitations.getInvitation(new UUID(900L, 3L)));
    }

    @Test
    public void makingAGuideTakesNothingBack() {
        Fellowship grey = company();
        FellowshipInvitationWorldData invitations = store();
        invitations.saveInvitation(invitation(1, beren));

        assertTrue(FellowshipService.changeGuide(grey, invitations,
                celeb.getIdentityId(), true));
        assertTrue(grey.isGuide(celeb.getIdentityId()));
        assertFalse("Beren is a guide already", FellowshipService.changeGuide(grey,
                null, beren.getIdentityId(), true));
        assertEquals(1, invitations.getInvitationCount());
    }

    @Test
    public void aLeaderHandingOnTheLeadTakesBackTheirs() {
        Fellowship grey = company();
        FellowshipInvitationWorldData invitations = store();
        invitations.saveInvitation(invitation(1, aldric));
        invitations.saveInvitation(invitation(2, beren));

        assertTrue(FellowshipService.handOver(grey, invitations,
                aldric.getIdentityId(), beren.getIdentityId()));
        assertTrue(grey.isLeader(beren.getIdentityId()));
        assertNull(invitations.getInvitation(new UUID(900L, 1L)));
        assertNotNull("the new leader's stays", invitations.getInvitation(new UUID(900L, 2L)));
    }

    private Fellowship company() {
        Fellowship grey = Fellowship.createNew(FELLOWSHIP, "The Grey Company", aldric, 1L);
        grey.addMember(beren);
        grey.addMember(celeb);
        grey.setGuide(beren.getIdentityId(), true);
        return grey;
    }

    private static FellowshipInvitation invitation(int index, FellowshipMember from) {
        return new FellowshipInvitation(new UUID(900L, index), FELLOWSHIP,
                from.getIdentityId(), from.getOwnerId(), from.getCharacterName(),
                new UUID(700L, index), new UUID(500L, index), "Dior", 1000L, 90000L);
    }

    private static FellowshipInvitationWorldData store() {
        return new FellowshipInvitationWorldData(FellowshipInvitationWorldData.DATA_NAME);
    }

    private static FellowshipMember member(String name, long joinedAt, FellowshipColor color) {
        return new FellowshipMember(UUID.randomUUID(), UUID.randomUUID(), name, joinedAt,
                color);
    }
}
