package com.ninuna.losttales.gui.hud;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Notices claim the shared slot top-down, each under the one before. */
public final class LostTalesNotificationHudTest {

    @Test
    public void firstClaimIsTheSlotsTopAndLaterClaimsStackUnderIt() {
        LostTalesNotificationHud.beginFrame();
        HudPlacementLayout.Bounds slot = LostTalesNotificationHud.placement(854, 480);
        int first = LostTalesNotificationHud.claim(854, 480, 30);
        int second = LostTalesNotificationHud.claim(854, 480, 34);
        int third = LostTalesNotificationHud.claim(854, 480, 9);
        assertEquals(slot.y, first);
        assertEquals(slot.y + 30 + LostTalesNotificationHud.GAP, second);
        assertEquals(slot.y + 30 + 34 + LostTalesNotificationHud.GAP * 2, third);
    }

    @Test
    public void aNewFrameStartsAtTheTopAgain() {
        LostTalesNotificationHud.beginFrame();
        LostTalesNotificationHud.claim(854, 480, 30);
        LostTalesNotificationHud.beginFrame();
        assertEquals(0, LostTalesNotificationHud.claimedThisFrame());
        assertEquals(LostTalesNotificationHud.placement(854, 480).y,
                LostTalesNotificationHud.claim(854, 480, 12));
    }

    @Test
    public void slotIsCentredOnTheScreenAtTheDefaultOffset() {
        HudPlacementLayout.Bounds slot = LostTalesNotificationHud.placement(854, 480);
        assertEquals(LostTalesNotificationHud.WIDTH, slot.width);
        assertEquals(854 / 2, LostTalesNotificationHud.centerX(854, 480));
    }
}
