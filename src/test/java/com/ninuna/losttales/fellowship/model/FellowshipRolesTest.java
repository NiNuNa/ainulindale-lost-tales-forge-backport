package com.ninuna.losttales.fellowship.model;

import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A fellowship has a leader and guides: the leader is never a guide, a
 * guide who leaves or leads is a guide no more, and a leader who leaves is
 * followed by the member who joined first. A new fellowship has every
 * switch on and no icon.
 */
public final class FellowshipRolesTest {

    @Test
    public void aNewFellowshipHasEverySwitchOnAndNoIcon() {
        Fellowship fellowship = FellowshipMemberLimitTest.fellowshipOf(1);
        Fellowship fresh = Fellowship.createNew(UUID.randomUUID(), "Grey Company",
                fellowship.getLeader(), 1L);
        for (FellowshipSwitch fellowshipSwitch : FellowshipSwitch.values()) {
            assertTrue(fresh.isOn(fellowshipSwitch));
        }
        assertNull(fresh.getIcon());
    }

    @Test
    public void theLeaderMakesGuidesButNeverOfThemselves() {
        Fellowship fellowship = FellowshipMemberLimitTest.fellowshipOf(3);
        UUID leader = fellowship.getLeaderIdentityId();
        UUID second = fellowship.getMembers().get(1).getIdentityId();
        assertFalse(fellowship.setGuide(leader, true));
        assertTrue(fellowship.setGuide(second, true));
        assertFalse("nothing changes", fellowship.setGuide(second, true));
        assertTrue(fellowship.canManage(second));
        assertFalse(fellowship.canManage(fellowship.getMembers().get(2).getIdentityId()));
    }

    @Test
    public void aGuideWhoLeadsOrLeavesIsAGuideNoMore() {
        Fellowship fellowship = FellowshipMemberLimitTest.fellowshipOf(3);
        UUID second = fellowship.getMembers().get(1).getIdentityId();
        UUID third = fellowship.getMembers().get(2).getIdentityId();
        fellowship.setGuide(second, true);
        fellowship.setGuide(third, true);
        assertTrue(fellowship.transferLeadership(second));
        assertFalse(fellowship.isGuide(second));
        fellowship.removeMember(third);
        assertTrue(fellowship.getGuides().isEmpty());
    }

    @Test
    public void aLeaderWhoLeavesIsFollowedByTheFirstToHaveJoined() {
        Fellowship fellowship = FellowshipMemberLimitTest.fellowshipOf(3);
        UUID second = fellowship.getMembers().get(1).getIdentityId();
        fellowship.setGuide(second, true);
        fellowship.removeMember(fellowship.getLeaderIdentityId());
        assertEquals(second, fellowship.getLeaderIdentityId());
        assertFalse("the new leader is no guide", fellowship.isGuide(second));
    }

    @Test
    public void aNameMustBeWellFormed() {
        assertTrue(Fellowship.isWellFormedName("Grey Company"));
        assertFalse(Fellowship.isWellFormedName(""));
        assertFalse(Fellowship.isWellFormedName(" Grey"));
        assertFalse(Fellowship.isWellFormedName("Grey§cCompany"));
        assertFalse(Fellowship.isWellFormedName("Grey​Company"));
        assertFalse(Fellowship.isWellFormedName("A name far longer than thirty-two characters"));
    }
}
