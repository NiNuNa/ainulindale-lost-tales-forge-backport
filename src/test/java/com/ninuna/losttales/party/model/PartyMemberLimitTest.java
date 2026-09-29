package com.ninuna.losttales.party.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The server's setting decides when a party is full, from two members to
 * eight. A party above a lowered setting keeps its members; eight stays
 * the most any party holds.
 */
public final class PartyMemberLimitTest {

    @Test
    public void theSettingDecidesWhenAPartyIsFull() {
        Party party = partyOf(3);
        assertFalse(party.isFull(4));
        assertTrue(party.isFull(3));
        assertTrue("a party above a lowered setting is full", party.isFull(2));
        assertEquals("and keeps every member", 3, party.getMemberCount());
    }

    @Test
    public void theSettingIsHeldBetweenTwoAndEight() {
        assertEquals(2, Party.clampMemberLimit(0));
        assertEquals(2, Party.clampMemberLimit(2));
        assertEquals(5, Party.clampMemberLimit(5));
        assertEquals(8, Party.clampMemberLimit(8));
        assertEquals(8, Party.clampMemberLimit(40));
        assertFalse("a setting below two is read as two", partyOf(1).isFull(1));
    }

    @Test
    public void eightMembersIsTheMostAPartyHolds() {
        Party party = partyOf(Party.MAX_MEMBERS);
        assertTrue(party.isFull(Party.MAX_MEMBERS));
        List<PartyMember> members = new ArrayList<PartyMember>(party.getMembers());
        members.add(new PartyMember(UUID.randomUUID(), UUID.randomUUID(),
                "Ninth", 99L, PartyColor.GREEN));
        Party capped = new Party(UUID.randomUUID(), party.getLeaderIdentityId(),
                members, 1L, 0L, Party.CURRENT_DATA_VERSION);
        assertEquals(Party.MAX_MEMBERS, capped.getMemberCount());
        assertEquals("every colour is worn", null, party.getFirstAvailableColor());
    }

    static Party partyOf(int members) {
        List<PartyMember> list = new ArrayList<PartyMember>();
        for (int index = 0; index < members; index++) {
            list.add(new PartyMember(UUID.randomUUID(), UUID.randomUUID(),
                    "Member " + index, index, PartyColor.values()[index]));
        }
        return new Party(UUID.randomUUID(), list.get(0).getIdentityId(), list,
                1L, 0L, Party.CURRENT_DATA_VERSION);
    }
}
