package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapFastTravelPromptTest {
    @Test
    public void promptStaysCenteredAndInsideTheScaledScreen() {
        LostTalesMapFastTravelPrompt.Layout layout =
                LostTalesMapFastTravelPrompt.calculateLayout(854, 480);

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
        LostTalesMapFastTravelPrompt.Layout layout =
                LostTalesMapFastTravelPrompt.calculateLayout(320, 180);
        int x = layout.placeMarker.x
                + layout.placeMarker.width / 2;
        int y = layout.placeMarker.y
                + layout.placeMarker.height / 2;

        assertEquals(LostTalesMapFastTravelPrompt.Action.NONE,
                prompt.mouseClicked(320, 180, x, y, 0, false));
        assertEquals(LostTalesMapFastTravelPrompt.Action.PLACE_MARKER,
                prompt.mouseClicked(320, 180, x, y, 0, true));
    }

    @Test
    public void narrowScreensKeepOrderedNonOverlappingActions() {
        LostTalesMapFastTravelPrompt.Layout layout =
                LostTalesMapFastTravelPrompt.calculateLayout(140, 90);

        assertEquals(layout.yes.x + layout.yes.width, layout.no.x);
        assertEquals(layout.no.x + layout.no.width,
                layout.placeMarker.x);
        assertTrue(layout.placeMarker.x
                + layout.placeMarker.width
                <= layout.x + layout.width);
    }
}
