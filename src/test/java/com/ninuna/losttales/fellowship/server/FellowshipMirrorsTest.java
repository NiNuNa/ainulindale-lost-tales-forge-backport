package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.compat.lotr.LotrFellowshipMirror;
import com.ninuna.losttales.fellowship.model.Fellowship;
import com.ninuna.losttales.fellowship.model.FellowshipColor;
import com.ninuna.losttales.fellowship.model.FellowshipFixtures;
import com.ninuna.losttales.fellowship.model.FellowshipMember;
import com.ninuna.losttales.fellowship.sync.FellowshipMemberPresence;
import com.ninuna.losttales.fellowship.sync.FellowshipSnapshot;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * LOTR's fellowship behind one of ours holds the accounts playing one of
 * its characters, now or as they logged out; an account playing another
 * character is not in it. The leader's account holds it while it plays
 * the leader, else the first such account does, those online first, and
 * while nobody plays one the leader's account holds it alone, for the
 * record only (FEL-02). Guides playing are its admins.
 */
public final class FellowshipMirrorsTest {
    private static final UUID LEADER_ACCOUNT = new UUID(1L, 1L);
    private static final UUID GUIDE_ACCOUNT = new UUID(2L, 2L);
    private static final UUID MEMBER_ACCOUNT = new UUID(3L, 3L);
    private static final FellowshipMember LEADER = new FellowshipMember(
            new UUID(10L, 10L), LEADER_ACCOUNT, "Aldric", 1L, FellowshipColor.GREEN);
    private static final FellowshipMember GUIDE = new FellowshipMember(
            new UUID(20L, 20L), GUIDE_ACCOUNT, "Beren", 2L, FellowshipColor.BLUE);
    private static final FellowshipMember MEMBER = new FellowshipMember(
            new UUID(30L, 30L), MEMBER_ACCOUNT, "Hurin", 3L, FellowshipColor.RED);

    @Test
    public void theLeadersAccountHoldsItWhilePlayingTheLeader() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                playing(LEADER, GUIDE), loggedOutAs());
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertEquals(Collections.singletonList(GUIDE_ACCOUNT), shape.members());
        assertEquals(Collections.singleton(GUIDE_ACCOUNT), shape.admins());
    }

    @Test
    public void elseTheFirstAccountPlayingOneHoldsIt() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                playing(MEMBER, GUIDE), loggedOutAs());
        assertEquals(GUIDE_ACCOUNT, shape.owner());
        assertEquals(Collections.singletonList(MEMBER_ACCOUNT), shape.members());
        assertTrue("an owner is no admin", shape.admins().isEmpty());
    }

    @Test
    public void whileNobodyPlaysOneTheLeadersAccountHoldsItAlone() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(), playing(),
                loggedOutAs());
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertTrue(shape.members().isEmpty());
    }

    /**
     * An account that logged out playing a member stays, after those
     * online; one that logged out playing another character does not.
     */
    @Test
    public void anAccountThatLoggedOutPlayingAMemberStays() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                playing(MEMBER), loggedOutAs(GUIDE));
        assertEquals(MEMBER_ACCOUNT, shape.owner());
        assertEquals(Collections.singletonList(GUIDE_ACCOUNT), shape.members());
        assertEquals(Collections.singleton(GUIDE_ACCOUNT), shape.admins());

        shape = FellowshipMirrors.shapeOf(fellowship(), playing(), loggedOutAs(LEADER));
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertTrue(shape.members().isEmpty());
    }

    /** The leader's account playing the leader takes part in what the fellowship shares. */
    @Test
    public void theLeadersAccountPlayingTheLeaderTakesPart() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                playing(LEADER), loggedOutAs());
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertTrue(shape.ownerPlays());
        assertTrue(FellowshipMirrors.playsAMember(LEADER_ACCOUNT, fellowship(),
                playing(LEADER), loggedOutAs()));
    }

    /**
     * The leader's account playing another character while a member is
     * played is not in LOTR's fellowship at all, and takes no part.
     */
    @Test
    public void theLeadersAccountPlayingAnotherCharacterTakesNoPart() {
        FellowshipSnapshot.Presence presence = playing(new FellowshipMember[] {GUIDE},
                LEADER);
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                presence, loggedOutAs());
        assertEquals(GUIDE_ACCOUNT, shape.owner());
        assertTrue(shape.ownerPlays());
        assertTrue(shape.members().isEmpty());
        assertFalse(FellowshipMirrors.playsAMember(LEADER_ACCOUNT, fellowship(),
                presence, loggedOutAs()));
    }

    /**
     * While nobody plays a member, the leader's account holds LOTR's
     * fellowship for the record only, whether it plays another character
     * or logged out as one (FEL-02).
     */
    @Test
    public void whileNobodyPlaysOneTheLeadersAccountHoldsItForTheRecordOnly() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                playing(new FellowshipMember[0], LEADER), loggedOutAs());
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertFalse(shape.ownerPlays());

        shape = FellowshipMirrors.shapeOf(fellowship(), playing(), loggedOutAs());
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertFalse(shape.ownerPlays());
        assertFalse(FellowshipMirrors.playsAMember(LEADER_ACCOUNT, fellowship(),
                playing(), loggedOutAs()));
    }

    /** An account that logged out playing the leader still takes part. */
    @Test
    public void theLeadersAccountLoggedOutAsTheLeaderTakesPart() {
        LotrFellowshipMirror.Shape shape = FellowshipMirrors.shapeOf(fellowship(),
                playing(), loggedOutAs(LEADER));
        assertEquals(LEADER_ACCOUNT, shape.owner());
        assertTrue(shape.ownerPlays());
    }

    /** These members' accounts are offline and logged out playing them. */
    private static FellowshipMirrors.LastPlayed loggedOutAs(FellowshipMember... members) {
        final Set<UUID> last = new HashSet<UUID>();
        for (FellowshipMember member : members) {
            last.add(member.getIdentityId());
        }
        return new FellowshipMirrors.LastPlayed() {
            @Override
            public boolean of(FellowshipMember member) {
                return last.contains(member.getIdentityId());
            }
        };
    }

    private static Fellowship fellowship() {
        Fellowship fellowship = FellowshipFixtures.of(new UUID(5L, 5L),
                LEADER.getIdentityId(), Arrays.asList(LEADER, GUIDE, MEMBER));
        fellowship.setGuide(GUIDE.getIdentityId(), true);
        return fellowship;
    }

    /** Only these members' accounts play them now. */
    private static FellowshipSnapshot.Presence playing(FellowshipMember... members) {
        return playing(members, new FellowshipMember[0]);
    }

    /**
     * These members' accounts play them now, those of {@code elsewhere}
     * play another character; every other account is offline.
     */
    private static FellowshipSnapshot.Presence playing(FellowshipMember[] members,
                                                       FellowshipMember... elsewhere) {
        final Set<UUID> here = new HashSet<UUID>();
        for (FellowshipMember member : members) {
            here.add(member.getIdentityId());
        }
        final Set<UUID> other = new HashSet<UUID>();
        for (FellowshipMember member : elsewhere) {
            other.add(member.getIdentityId());
        }
        return new FellowshipSnapshot.Presence() {
            @Override
            public FellowshipMemberPresence of(FellowshipMember member) {
                return here.contains(member.getIdentityId()) ? FellowshipMemberPresence.HERE
                        : other.contains(member.getIdentityId())
                        ? FellowshipMemberPresence.ELSEWHERE : FellowshipMemberPresence.AWAY;
            }

            @Override
            public String elsewhereName(FellowshipMember member) {
                return "";
            }
        };
    }
}
