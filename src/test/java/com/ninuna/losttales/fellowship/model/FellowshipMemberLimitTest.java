package com.ninuna.losttales.fellowship.model;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * LOTR's size setting decides when a fellowship is full, held between two
 * members and fifty. A fellowship above a lowered setting keeps its
 * members; fifty stays the most any fellowship holds. Colours repeat once
 * every colour is worn.
 */
public final class FellowshipMemberLimitTest {

    @Test
    public void theSettingDecidesWhenAFellowshipIsFull() {
        Fellowship fellowship = fellowshipOf(3);
        assertFalse(fellowship.isFull(4));
        assertTrue(fellowship.isFull(3));
        assertTrue("a fellowship above a lowered setting is full", fellowship.isFull(2));
        assertEquals("and keeps every member", 3, fellowship.getMemberCount());
    }

    @Test
    public void theSettingIsHeldBetweenTwoAndFifty() {
        assertEquals(2, Fellowship.clampMemberLimit(0));
        assertEquals(2, Fellowship.clampMemberLimit(2));
        assertEquals(5, Fellowship.clampMemberLimit(5));
        assertEquals(50, Fellowship.clampMemberLimit(50));
        assertEquals(50, Fellowship.clampMemberLimit(400));
        assertFalse("a setting below two is read as two", fellowshipOf(1).isFull(1));
    }

    @Test
    public void fiftyMembersIsTheMostAFellowshipHolds() {
        Fellowship fellowship = fellowshipOf(Fellowship.MAX_MEMBERS);
        assertTrue(fellowship.isFull(Fellowship.MAX_MEMBERS));
        assertFalse("no one more is taken in", fellowship.addMember(new FellowshipMember(
                UUID.randomUUID(), UUID.randomUUID(), "One Too Many", 99L,
                FellowshipColor.GREEN)));
        assertEquals(Fellowship.MAX_MEMBERS, fellowship.getMemberCount());
    }

    @Test
    public void aNewMemberWearsAColourNobodyWearsThenTheLeastWorn() {
        Fellowship fellowship = fellowshipOf(2);
        FellowshipColor next = fellowship.nextColor();
        assertTrue(next != FellowshipColor.values()[0] && next != FellowshipColor.values()[1]);

        Fellowship full = fellowshipOf(FellowshipColor.values().length + 1);
        assertEquals("the least worn once every colour is worn",
                FellowshipColor.values()[1], full.nextColor());
    }

    @Test
    public void aColourIsFreeUntilEveryColourIsWorn() {
        Fellowship fellowship = fellowshipOf(2);
        UUID second = fellowship.getMembers().get(1).getIdentityId();
        assertFalse("another member wears it",
                fellowship.isColorAvailable(FellowshipColor.values()[0], second));
        assertTrue(fellowship.isColorAvailable(FellowshipColor.values()[2], second));

        Fellowship full = fellowshipOf(FellowshipColor.values().length + 1);
        UUID last = full.getMembers().get(full.getMemberCount() - 1).getIdentityId();
        assertTrue("any colour once every colour is worn by another",
                full.isColorAvailable(FellowshipColor.values()[0], last));
    }

    static Fellowship fellowshipOf(int members) {
        List<FellowshipMember> list = new ArrayList<FellowshipMember>();
        FellowshipColor[] colours = FellowshipColor.values();
        for (int index = 0; index < members; index++) {
            list.add(new FellowshipMember(UUID.randomUUID(), UUID.randomUUID(),
                    "Member " + index, index, colours[index % colours.length]));
        }
        return new Fellowship(UUID.randomUUID(), list.get(0).getIdentityId(), list,
                Collections.<UUID>emptyList(), "Grey Company", null,
                EnumSet.allOf(FellowshipSwitch.class), 1L, 0L,
                Fellowship.CURRENT_DATA_VERSION);
    }
}
