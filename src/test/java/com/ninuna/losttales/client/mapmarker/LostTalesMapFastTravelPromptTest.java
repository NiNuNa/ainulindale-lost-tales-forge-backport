package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapFastTravelPromptTest {
    @Test
    public void promptStaysCenteredAndInsideTheScaledScreen() {
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(854, 480);

        assertEquals((854 - layout.width) / 2, layout.x);
        assertEquals((480 - layout.height) / 2, layout.y);
        assertTrue(layout.x >= 0);
        assertTrue(layout.y >= 0);
        assertTrue(layout.x + layout.width <= 854);
        assertTrue(layout.y + layout.height <= 480);
    }

    @Test
    public void disabledPlaceMarkerDoesNotProduceAnAction() {
        LostTalesMapFastTravelPrompt prompt =
                new LostTalesMapFastTravelPrompt("Bree");
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(320, 180);
        int x = layout.third.x + layout.third.width / 2;
        int y = layout.third.y + layout.third.height / 2;

        assertEquals(LostTalesMapFastTravelPrompt.Action.NONE,
                prompt.mouseClicked(320, 180, x, y, 0, false));
        assertEquals(LostTalesMapFastTravelPrompt.Action.PLACE_MARKER,
                prompt.mouseClicked(320, 180, x, y, 0, true));
    }

    @Test
    public void aBlockedDestinationRefusesTravelButStillOffersTheRest() {
        LostTalesMapFastTravelPrompt prompt =
                new LostTalesMapFastTravelPrompt("Bree",
                        "gui.losttales.map.fast_travel.blocked.cooldown",
                        false);
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(320, 180);

        assertEquals(LostTalesMapFastTravelPrompt.Action.NONE,
                prompt.mouseClicked(320, 180,
                        layout.first.x + layout.first.width / 2,
                        layout.first.y + layout.first.height / 2,
                        0, true));
        assertEquals(LostTalesMapFastTravelPrompt.Action.PLACE_MARKER,
                prompt.mouseClicked(320, 180,
                        layout.third.x + layout.third.width / 2,
                        layout.third.y + layout.third.height / 2,
                        0, true));
    }

    @Test
    public void narrowScreensKeepOrderedNonOverlappingActions() {
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(140, 90);

        assertEquals(layout.first.x + layout.first.width, layout.second.x);
        assertEquals(layout.second.x + layout.second.width, layout.third.x);
        assertTrue(layout.third.x + layout.third.width
                <= layout.x + layout.width);
    }

    /**
     * A destination with nowhere to step to must not offer the movement: no
     * arrows, and the keys that would take them do nothing rather than
     * reopening the popup on the place it is already showing.
     */
    @Test
    public void aLoneDestinationOffersNoStepping() {
        LostTalesMapFastTravelPrompt alone =
                new LostTalesMapFastTravelPrompt("Bree", null, false);
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(854, 480);

        assertEquals(LostTalesMapFastTravelPrompt.Action.NONE,
                alone.keyTyped(org.lwjgl.input.Keyboard.KEY_A));
        assertEquals(LostTalesMapFastTravelPrompt.Action.NONE,
                alone.keyTyped(org.lwjgl.input.Keyboard.KEY_RIGHT));
        assertEquals(LostTalesMapFastTravelPrompt.Action.NONE,
                alone.mouseClicked(854, 480,
                        layout.next.x + layout.next.width / 2,
                        layout.next.y + layout.next.height / 2, 0, true));
    }

    /** Both key pairs and both arrows step, and they agree on which way. */
    @Test
    public void everyWayOfSteppingAgreesOnTheDirection() {
        LostTalesMapFastTravelPrompt prompt =
                new LostTalesMapFastTravelPrompt("Bree", null, true);
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(854, 480);

        assertEquals(LostTalesMapFastTravelPrompt.Action.PREVIOUS,
                prompt.keyTyped(org.lwjgl.input.Keyboard.KEY_LEFT));
        assertEquals(LostTalesMapFastTravelPrompt.Action.PREVIOUS,
                prompt.keyTyped(org.lwjgl.input.Keyboard.KEY_A));
        assertEquals(LostTalesMapFastTravelPrompt.Action.NEXT,
                prompt.keyTyped(org.lwjgl.input.Keyboard.KEY_RIGHT));
        assertEquals(LostTalesMapFastTravelPrompt.Action.NEXT,
                prompt.keyTyped(org.lwjgl.input.Keyboard.KEY_D));
        assertEquals(LostTalesMapFastTravelPrompt.Action.PREVIOUS,
                prompt.mouseClicked(854, 480,
                        layout.previous.x + layout.previous.width / 2,
                        layout.previous.y + layout.previous.height / 2,
                        0, true));
        assertEquals(LostTalesMapFastTravelPrompt.Action.NEXT,
                prompt.mouseClicked(854, 480,
                        layout.next.x + layout.next.width / 2,
                        layout.next.y + layout.next.height / 2, 0, true));
    }

    /** The arrows may never sit on top of an answer to the question. */
    @Test
    public void theStepArrowsStayOutsideThePanel() {
        LostTalesMapChoicePrompt.Layout layout =
                LostTalesMapChoicePrompt.calculateLayout(854, 480);

        assertTrue(layout.previous.x + layout.previous.width <= layout.x);
        assertTrue(layout.next.x >= layout.x + layout.width);
        assertTrue(layout.previous.x >= 0);
        assertTrue(layout.next.x + layout.next.width <= 854);
    }

    /**
     * The destination is framed in the band the popup leaves free, so it is
     * never underneath the question being asked about it.
     */
    @Test
    public void theFocusAnchorClearsThePanelAtEveryScreenSize() {
        int[] widths = { 320, 480, 854, 1920 };
        int[] heights = { 180, 240, 480, 1080 };
        for (int index = 0; index < widths.length; index++) {
            LostTalesMapChoicePrompt.Layout layout =
                    LostTalesMapChoicePrompt.calculateLayout(
                            widths[index], heights[index]);
            float anchor = LostTalesMapFastTravelPrompt.focusAnchorY(
                    widths[index], heights[index]);

            assertTrue("the marker would sit off the top of the screen",
                    anchor >= 0.0F);
            assertTrue("the marker would sit off the bottom",
                    anchor <= heights[index]);
            assertTrue("the popup would cover the marker it is about",
                    anchor < layout.y
                            || anchor > layout.y + layout.height);
        }
    }
}
