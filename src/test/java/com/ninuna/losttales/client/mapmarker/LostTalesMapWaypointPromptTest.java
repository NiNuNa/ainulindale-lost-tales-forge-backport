package com.ninuna.losttales.client.mapmarker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LostTalesMapWaypointPromptTest {
    @Test
    public void thePanelStaysCenteredAndInsideTheScaledScreen() {
        for (int index = 0; index < 2; index++) {
            boolean editing = index == 1;
            LostTalesMapWaypointPrompt.Layout layout =
                    LostTalesMapWaypointPrompt.calculateLayout(
                            854, 480, editing);

            assertEquals((854 - layout.width) / 2, layout.x);
            assertEquals((480 - layout.height) / 2, layout.y);
            assertTrue(layout.x + layout.width <= 854);
            assertTrue(layout.y + layout.height <= 480);
        }
    }

    @Test
    public void editingOffersFourActionsAndCreatingOffersTwo() {
        LostTalesMapWaypointPrompt.Layout creating =
                LostTalesMapWaypointPrompt.calculateLayout(
                        854, 480, false);
        assertEquals(0, creating.travel.width);
        assertEquals(0, creating.delete.width);
        assertTrue(creating.confirm.width > 0);
        assertTrue(creating.cancel.width > 0);

        LostTalesMapWaypointPrompt.Layout editing =
                LostTalesMapWaypointPrompt.calculateLayout(
                        854, 480, true);
        assertTrue(editing.travel.width > 0);
        assertTrue(editing.delete.width > 0);
        assertTrue(editing.confirm.width > 0);
        assertTrue(editing.cancel.width > 0);
    }

    @Test
    public void theActionRowsAreOrderedAndDoNotOverlapThemselves() {
        LostTalesMapWaypointPrompt.Layout layout =
                LostTalesMapWaypointPrompt.calculateLayout(
                        854, 480, true);

        // What the waypoint can do, above what happens to it.
        assertEquals(layout.travel.x + layout.travel.width,
                layout.placeMarker.x);
        assertTrue(layout.placeMarker.x + layout.placeMarker.width
                <= layout.x + layout.width);
        assertEquals(layout.confirm.x + layout.confirm.width,
                layout.delete.x);
        assertEquals(layout.delete.x + layout.delete.width,
                layout.cancel.x);
        assertTrue(layout.cancel.x + layout.cancel.width
                <= layout.x + layout.width);
        assertTrue("the rows must not overlap",
                layout.travel.y + layout.travel.height
                        <= layout.confirm.y);
    }

    @Test
    public void bothTextFieldsSitAboveTheSwatches() {
        LostTalesMapWaypointPrompt.Layout layout =
                LostTalesMapWaypointPrompt.calculateLayout(
                        854, 480, true);

        assertTrue(layout.nameField.y + layout.nameField.height
                <= layout.noteField.y);
        assertTrue(layout.noteField.y + layout.noteField.height
                <= layout.swatchRow.y);
        assertEquals(layout.nameField.width, layout.noteField.width);
    }

    @Test
    public void everyShareRowSitsInsideThePanel() {
        LostTalesMapWaypointPrompt.Layout layout =
                LostTalesMapWaypointPrompt.calculateLayout(
                        854, 480, true);

        for (int row = 0;
             row < LostTalesMapWaypointPrompt.VISIBLE_FELLOWSHIPS; row++) {
            LostTalesMapWaypointPrompt.Bounds bounds =
                    layout.shareRow(row);
            assertTrue("row " + row,
                    bounds.y >= layout.y);
            // The list must never reach down into the action row.
            assertTrue("row " + row,
                    bounds.y + bounds.height <= layout.confirm.y);
        }
    }

    @Test
    public void aNameLotrWouldRefuseCannotBeConfirmed() {
        assertFalse(LostTalesMapWaypointPrompt.isValidName(null));
        assertFalse(LostTalesMapWaypointPrompt.isValidName("   "));
        assertFalse(LostTalesMapWaypointPrompt.isValidName(
                repeat('a', LostTalesMapWaypointPrompt.MAX_NAME_LENGTH
                        + 1)));
        assertTrue(LostTalesMapWaypointPrompt.isValidName("North Camp"));
        assertTrue(LostTalesMapWaypointPrompt.isValidName(
                repeat('a', LostTalesMapWaypointPrompt.MAX_NAME_LENGTH)));
    }

    @Test
    public void aTinyScreenStillProducesUsableBounds() {
        LostTalesMapWaypointPrompt.Layout layout =
                LostTalesMapWaypointPrompt.calculateLayout(
                        120, 80, true);

        assertTrue(layout.x >= 0);
        assertTrue(layout.y >= 0);
        assertTrue(layout.x + layout.width <= 120);
        assertTrue(layout.y + layout.height <= 80);
        assertTrue(layout.cancel.x + layout.cancel.width
                <= layout.x + layout.width);
    }

    private static String repeat(char character, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(character);
        }
        return builder.toString();
    }
}
