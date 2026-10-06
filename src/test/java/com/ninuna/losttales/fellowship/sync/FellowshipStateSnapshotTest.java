package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipSwitch;
import com.ninuna.losttales.fellowship.server.FellowshipErrorId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * What one account is shown of the fellowships of the character it plays:
 * the one it travels with is one of them, it sees the invitations only of
 * the fellowships it leads or guides, and whom to invite only while one of
 * those has room.
 */
public final class FellowshipStateSnapshotTest {
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final UUID PLAYED = new UUID(2L, 2L);
    private static final UUID OTHER = new UUID(3L, 3L);
    private static final UUID LED = new UUID(10L, 10L);
    private static final UUID JOINED = new UUID(11L, 11L);

    @Test(expected = IllegalArgumentException.class)
    public void theFellowshipTravelledWithIsOneOfThem() {
        state(Arrays.asList(fellowship(LED, PLAYED)), new UUID(99L, 99L), 8,
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                Collections.<FellowshipInviteTargetSnapshot>emptyList());
    }

    @Test(expected = IllegalArgumentException.class)
    public void aCharacterInNoFellowshipTravelsWithNone() {
        state(Collections.<FellowshipSnapshot>emptyList(), LED, 8,
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                Collections.<FellowshipInviteTargetSnapshot>emptyList());
    }

    @Test
    public void onlyTheInvitationsOfFellowshipsLedOrGuidedAreShown() {
        List<FellowshipInvitationSnapshot> sent = new ArrayList<FellowshipInvitationSnapshot>();
        sent.add(invitation(LED));
        sent.add(invitation(JOINED));
        FellowshipStateSnapshot state = state(Arrays.asList(fellowship(LED, PLAYED),
                fellowship(JOINED, OTHER)), LED, 8, sent,
                Collections.<FellowshipInviteTargetSnapshot>emptyList());
        assertEquals(1, state.getOutgoingInvitations().size());
        assertEquals(LED, state.getOutgoingInvitations().get(0).getFellowshipId());
    }

    @Test
    public void nobodyIsOfferedWhileNoFellowshipLedOrGuidedHasRoom() {
        List<FellowshipInviteTargetSnapshot> targets = Collections.singletonList(
                new FellowshipInviteTargetSnapshot(new UUID(7L, 7L), new UUID(8L, 8L),
                        "Stranger", "Hurin"));
        assertTrue(state(Arrays.asList(fellowship(JOINED, OTHER)), JOINED, 8,
                Collections.<FellowshipInvitationSnapshot>emptyList(), targets)
                .getInviteTargets().isEmpty());
        assertTrue("full", state(Arrays.asList(fellowship(LED, PLAYED)), LED, 2,
                Collections.<FellowshipInvitationSnapshot>emptyList(), targets)
                .getInviteTargets().isEmpty());
        assertEquals(1, state(Arrays.asList(fellowship(LED, PLAYED)), LED, 3,
                Collections.<FellowshipInvitationSnapshot>emptyList(), targets)
                .getInviteTargets().size());
    }

    private static FellowshipStateSnapshot state(List<FellowshipSnapshot> fellowships,
                                                 UUID travelling, int limit,
                                                 List<FellowshipInvitationSnapshot> sent,
                                                 List<FellowshipInviteTargetSnapshot> targets) {
        return new FellowshipStateSnapshot(OWNER, 1L, FellowshipErrorId.NONE, limit, PLAYED,
                fellowships, travelling, FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(), sent,
                false, false, targets, false);
    }

    /** A fellowship of the character played and one other, led by {@code leader}. */
    private static FellowshipSnapshot fellowship(UUID id, UUID leader) {
        List<FellowshipMemberSnapshot> members = new ArrayList<FellowshipMemberSnapshot>();
        members.add(new FellowshipMemberSnapshot(PLAYED, OWNER, "Aldric", 1L,
                FellowshipColor.GREEN, FellowshipMemberPresence.HERE, ""));
        members.add(new FellowshipMemberSnapshot(OTHER, new UUID(4L, 4L), "Beleg", 2L,
                FellowshipColor.BLUE, FellowshipMemberPresence.AWAY, ""));
        return new FellowshipSnapshot(id, leader, "Company " + id.getLeastSignificantBits(),
                null, EnumSet.allOf(FellowshipSwitch.class),
                Collections.<UUID>emptyList(), 1L, 1L, 2, members,
                Collections.<com.ninuna.losttales.fellowship.model.FellowshipMark>emptyList());
    }

    private static FellowshipInvitationSnapshot invitation(UUID fellowshipId) {
        return new FellowshipInvitationSnapshot(UUID.randomUUID(), fellowshipId,
                "Company", PLAYED, OWNER, "Aldric", new UUID(5L, 5L), new UUID(6L, 6L),
                "Hurin", 1L, 2L);
    }
}
