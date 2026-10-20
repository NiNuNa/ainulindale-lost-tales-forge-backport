package com.ninuna.losttales.fellowship.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * When a character joined each of its fellowships: where each one's
 * conversation starts for it (FEL-29). A founder joined at the founding,
 * a member who came back at its latest joining, and a switch or a release
 * keeps the joining with the figure.
 */
public final class FellowshipJoiningsTest {
    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    private final FellowshipMember aldric = new FellowshipMember(UUID.randomUUID(),
            ALICE, "Aldric", 1000L, FellowshipColor.GREEN);

    @Test
    public void aFounderJoinedAtTheFounding() {
        Fellowship grey = Fellowship.createNew(UUID.randomUUID(), "Grey Company",
                this.aldric, 1000L);
        Map<UUID, Long> joined = Fellowship.joinings(
                Collections.singletonList(grey), this.aldric.getIdentityId());
        assertEquals(Long.valueOf(grey.getCreatedAt()), joined.get(grey.getFellowshipId()));
    }

    @Test
    public void eachFellowshipNamesItsOwnJoiningAndOthersAreLeftOut() {
        Fellowship grey = Fellowship.createNew(UUID.randomUUID(), "Grey Company",
                this.aldric, 1000L);
        Fellowship rangers = Fellowship.createNew(UUID.randomUUID(), "Rangers",
                new FellowshipMember(UUID.randomUUID(), BOB, "Beren", 500L,
                        FellowshipColor.GREEN), 500L);
        assertTrue(rangers.addMember(new FellowshipMember(this.aldric.getIdentityId(),
                ALICE, "Aldric", 3000L, rangers.nextColor())));
        Fellowship elsewhere = Fellowship.createNew(UUID.randomUUID(), "Wardens",
                new FellowshipMember(UUID.randomUUID(), BOB, "Celeb", 200L,
                        FellowshipColor.GREEN), 200L);

        Map<UUID, Long> joined = Fellowship.joinings(
                Arrays.asList(grey, rangers, elsewhere), this.aldric.getIdentityId());
        assertEquals(2, joined.size());
        assertEquals(Long.valueOf(1000L), joined.get(grey.getFellowshipId()));
        assertEquals(Long.valueOf(3000L), joined.get(rangers.getFellowshipId()));
        assertFalse(joined.containsKey(elsewhere.getFellowshipId()));
        assertTrue(Fellowship.joinings(null, this.aldric.getIdentityId()).isEmpty());
        assertTrue(Fellowship.joinings(Collections.singletonList(grey), null).isEmpty());
    }

    @Test
    public void aMemberWhoCameBackJoinedAtItsLatestJoining() {
        Fellowship grey = Fellowship.createNew(UUID.randomUUID(), "Grey Company",
                this.aldric, 1000L);
        UUID beren = UUID.randomUUID();
        assertTrue(grey.addMember(new FellowshipMember(beren, BOB, "Beren", 2000L,
                grey.nextColor())));
        grey.removeMember(beren);
        assertTrue(grey.addMember(new FellowshipMember(beren, BOB, "Beren", 4000L,
                grey.nextColor())));
        assertEquals(Long.valueOf(4000L), Fellowship.joinings(
                Collections.singletonList(grey), beren).get(grey.getFellowshipId()));
    }

    /** A released figure and its next holder keep the place it took (CHAR-36). */
    @Test
    public void aReleaseAndANewHolderKeepTheJoining() {
        Fellowship grey = Fellowship.createNew(UUID.randomUUID(), "Grey Company",
                this.aldric, 1000L);
        UUID beren = UUID.randomUUID();
        assertTrue(grey.addMember(new FellowshipMember(beren, BOB, "Beren", 2000L,
                grey.nextColor())));
        assertTrue(grey.refreshMemberIdentity(beren, FellowshipMember.UNHELD, "Beren"));
        assertEquals(2000L, grey.getMember(beren).getJoinedAt());
        assertTrue(grey.refreshMemberIdentity(beren, UUID.randomUUID(), "Beren"));
        assertEquals(2000L, grey.getMember(beren).getJoinedAt());
        assertTrue(grey.changeMemberColor(beren, FellowshipColor.values()[5]));
        assertEquals(2000L, grey.getMember(beren).getJoinedAt());
    }
}
