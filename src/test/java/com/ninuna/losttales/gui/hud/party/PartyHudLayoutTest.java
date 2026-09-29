package com.ninuna.losttales.gui.hud.party;

import com.ninuna.losttales.party.model.Party;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** The party HUD stacks a row for every other member, as many as a party can hold. */
public final class PartyHudLayoutTest {

    @Test
    public void sevenOtherMembersStackSevenRows() {
        PartyHudLayout.Bounds bounds = PartyHudLayout.calculate(
                1920, 1080, 2.0D, 18.0D, 7);
        assertEquals(7, bounds.rowCount);
        assertEquals(PartyHudLayout.PANEL_PADDING * 2
                + 7 * PartyHudLayout.ROW_HEIGHT, bounds.height);
    }

    @Test
    public void theRowsStopAtEveryMemberButThePlayer() {
        assertEquals(Party.MAX_MEMBERS - 1, PartyHudLayout.MAX_ROWS);
        assertEquals(PartyHudLayout.MAX_ROWS, PartyHudLayout.calculate(
                1920, 1080, 2.0D, 18.0D, 12).rowCount);
        assertEquals(1, PartyHudLayout.calculate(
                1920, 1080, 2.0D, 18.0D, 0).rowCount);
    }

    @Test
    public void theHeightFollowsTheRows() {
        assertEquals(PartyHudLayout.PANEL_PADDING * 2
                + 3 * PartyHudLayout.ROW_HEIGHT, PartyHudLayout.height(3));
    }
}
