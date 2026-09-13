package com.ninuna.losttales.client.gui;

import net.minecraftforge.client.event.RenderGameOverlayEvent.ElementType;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * While the chat is open the game's HUD fades as one layer, from the
 * first of its elements to the first element drawn after them. The
 * overlay as a whole is never touched — Forge answers a cancelled whole
 * by leaving the screen's projection unset — and neither are the chat,
 * the debug text, the player list or the screen-wide overlays.
 */
public final class LostTalesHudFadeTest {

    @Test
    public void theHudTheChatTakesThePlaceOfFades() {
        ElementType[] faded = {ElementType.CROSSHAIRS, ElementType.BOSSHEALTH,
                ElementType.HEALTH, ElementType.ARMOR, ElementType.FOOD,
                ElementType.HEALTHMOUNT, ElementType.AIR, ElementType.HOTBAR,
                ElementType.EXPERIENCE, ElementType.JUMPBAR};
        for (ElementType type : faded) {
            assertTrue(type.name(), LostTalesHudFade.fades(type));
            assertFalse(type.name(), LostTalesHudFade.endsLayer(type));
        }
    }

    @Test
    public void theWholeTheChatAndTheOverlaysAreNeverFaded() {
        ElementType[] kept = {ElementType.ALL, ElementType.CHAT,
                ElementType.TEXT, ElementType.DEBUG, ElementType.PLAYER_LIST,
                ElementType.HELMET, ElementType.PORTAL};
        for (ElementType type : kept) {
            assertFalse(type.name(), LostTalesHudFade.fades(type));
        }
        assertFalse(LostTalesHudFade.fades(null));
    }

    @Test
    public void whatIsDrawnAfterTheHudEndsItsLayer() {
        assertTrue(LostTalesHudFade.endsLayer(ElementType.DEBUG));
        assertTrue(LostTalesHudFade.endsLayer(ElementType.TEXT));
        assertTrue(LostTalesHudFade.endsLayer(ElementType.CHAT));
        assertTrue(LostTalesHudFade.endsLayer(ElementType.PLAYER_LIST));
        assertFalse(LostTalesHudFade.endsLayer(ElementType.ALL));
        assertFalse(LostTalesHudFade.endsLayer(ElementType.HELMET));
    }
}
