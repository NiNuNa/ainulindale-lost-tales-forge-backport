package com.ninuna.losttales.fellowship.sync;

import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipMark;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * What one account is shown of the fellowships of the character it plays:
 * the one it travels with is one of them, it sees the invitations only of
 * the fellowships it leads or guides, whom to invite only while one of
 * those has room, and only the marks it may see.
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

    /**
     * The go-here marks of the character played and of the members of the
     * fellowship it travels with; a fellowship's marks, at most the most it
     * holds, for its fellowships only.
     */
    @Test
    public void onlyTheMarksTheCharacterMaySeeAreKept() {
        UUID stranger = new UUID(9L, 9L);
        List<FellowshipMark> marks = new ArrayList<FellowshipMark>();
        marks.add(FellowshipMark.goHere(new UUID(20L, 1L), PLAYED, 0, 1.0D, 1.0D, 1L));
        marks.add(FellowshipMark.goHere(new UUID(20L, 2L), OTHER, 0, 2.0D, 2.0D, 1L));
        marks.add(FellowshipMark.goHere(new UUID(20L, 3L), stranger, 0, 3.0D, 3.0D, 1L));
        for (int index = 0; index <= FellowshipMark.MAX_PER_FELLOWSHIP; index++) {
            marks.add(FellowshipMark.ofFellowship(new UUID(30L, index), LED, "Camp " + index,
                    PLAYED, 0, index, index, 1L));
        }
        marks.add(FellowshipMark.ofFellowship(new UUID(40L, 1L), new UUID(99L, 99L), "Bree",
                PLAYED, 0, 5.0D, 5.0D, 1L));
        FellowshipStateSnapshot state = new FellowshipStateSnapshot(OWNER, 1L,
                FellowshipErrorId.NONE, 8, PLAYED, Arrays.asList(fellowship(LED, PLAYED)), LED,
                marks, FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                false, false, Collections.<FellowshipInviteTargetSnapshot>emptyList(), false);

        assertEquals(2 + FellowshipMark.MAX_PER_FELLOWSHIP, state.getMarks().size());
        assertEquals(new UUID(20L, 2L), state.getGoHereOf(OTHER).getMarkId());
        assertNull(state.getGoHereOf(stranger));
        assertEquals(FellowshipMark.MAX_PER_FELLOWSHIP, state.getFellowshipMarks(LED).size());
        assertEquals(state.getMarks().size(), state.getCompassMarks().size());
    }

    /** Travelling with none, the character sees its own go-here mark and no member's. */
    @Test
    public void withoutAFellowshipTravelledWithOnlyTheOwnGoHereMarkIsKept() {
        List<FellowshipMark> marks = Arrays.asList(
                FellowshipMark.goHere(new UUID(20L, 1L), PLAYED, 0, 1.0D, 1.0D, 1L),
                FellowshipMark.goHere(new UUID(20L, 2L), OTHER, 0, 2.0D, 2.0D, 1L));
        FellowshipStateSnapshot state = new FellowshipStateSnapshot(OWNER, 1L,
                FellowshipErrorId.NONE, 8, PLAYED, Collections.<FellowshipSnapshot>emptyList(),
                null, marks, FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                Collections.<FellowshipInvitationSnapshot>emptyList(),
                false, false, Collections.<FellowshipInviteTargetSnapshot>emptyList(), false);

        assertEquals(1, state.getMarks().size());
        assertEquals(PLAYED, state.getMarks().get(0).getPlacedBy());
    }

    private static FellowshipStateSnapshot state(List<FellowshipSnapshot> fellowships,
                                                 UUID travelling, int limit,
                                                 List<FellowshipInvitationSnapshot> sent,
                                                 List<FellowshipInviteTargetSnapshot> targets) {
        return new FellowshipStateSnapshot(OWNER, 1L, FellowshipErrorId.NONE, limit, PLAYED,
                fellowships, travelling, Collections.<FellowshipMark>emptyList(),
                FellowshipErrorId.NONE,
                Collections.<FellowshipInvitationSnapshot>emptyList(), sent,
                false, false, targets, false);
    }

    /** A fellowship of the character played and one other, led by {@code leader}. */
    private static FellowshipSnapshot fellowship(UUID id, UUID leader) {
        List<FellowshipMemberSnapshot> members = new ArrayList<FellowshipMemberSnapshot>();
        members.add(new FellowshipMemberSnapshot(PLAYED, OWNER, "Aldric",
                FellowshipColor.GREEN, FellowshipMemberPresence.HERE, ""));
        members.add(new FellowshipMemberSnapshot(OTHER, new UUID(4L, 4L), "Beleg",
                FellowshipColor.BLUE, FellowshipMemberPresence.AWAY, ""));
        return new FellowshipSnapshot(id, leader, "Company " + id.getLeastSignificantBits(),
                null, EnumSet.allOf(FellowshipSwitch.class),
                Collections.<UUID>emptyList(), 1L, null, members);
    }

    private static FellowshipInvitationSnapshot invitation(UUID fellowshipId) {
        return new FellowshipInvitationSnapshot(UUID.randomUUID(), fellowshipId,
                "Company", PLAYED, OWNER, "Aldric", new UUID(5L, 5L), "Hurin", 2L);
    }
}
