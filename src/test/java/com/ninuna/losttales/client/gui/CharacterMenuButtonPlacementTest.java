package com.ninuna.losttales.client.gui;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;

/**
 * The character button never lands on a menu button, at any window size.
 * The column is centred, so every width puts it somewhere else.
 */
public class CharacterMenuButtonPlacementTest {

    /** Vanilla's column: 200 wide, centred, Multiplayer 24 below. */
    private static CharacterMenuButtonPlacement forWidth(int screenWidth) {
        int left = screenWidth / 2 - 100;
        int top = 100;
        return CharacterMenuButtonPlacement.beside(
                screenWidth, left, left + 200, top, top + 44);
    }

    @Test
    public void sitsRightOfTheColumnAndSpansIt() {
        CharacterMenuButtonPlacement placement = forWidth(854);
        assertNotNull(placement);
        assertEquals(854 / 2 + 100 + CharacterMenuButtonPlacement.GAP,
                placement.getX());
        assertEquals(100, placement.getY());
        assertEquals(44, placement.getHeight());
    }

    @Test
    public void clearsTheColumnOnBothSidesAtEveryWidth() {
        for (int screenWidth = 240; screenWidth <= 3840; screenWidth++) {
            int columnLeft = screenWidth / 2 - 100;
            int columnRight = columnLeft + 200;
            CharacterMenuButtonPlacement placement = forWidth(screenWidth);
            if (placement == null) {
                continue;
            }
            int buttonLeft = placement.getX();
            int buttonRight = buttonLeft + CharacterMenuButtonPlacement.WIDTH;
            boolean clear = buttonRight <= columnLeft
                    || buttonLeft >= columnRight;
            assertTrue("overlaps the column at width " + screenWidth, clear);
            assertTrue("runs off the left at width " + screenWidth,
                    buttonLeft >= 0);
            assertTrue("runs off the right at width " + screenWidth,
                    buttonRight <= screenWidth);
        }
    }

    @Test
    public void fallsBackToTheLeftWhenTheRightIsTooTight() {
        // The column ends 8 pixels from the right edge: no room beside it.
        CharacterMenuButtonPlacement placement =
                CharacterMenuButtonPlacement.beside(400, 60, 392, 100, 144);
        assertNotNull(placement);
        assertEquals(60 - CharacterMenuButtonPlacement.GAP
                - CharacterMenuButtonPlacement.WIDTH, placement.getX());
    }

    @Test
    public void offersNothingWhenNeitherSideFits() {
        assertNull(CharacterMenuButtonPlacement.beside(
                220, 2, 218, 100, 144));
    }

    @Test
    public void offersNothingForAColumnWithNoHeight() {
        assertNull(CharacterMenuButtonPlacement.beside(
                854, 327, 527, 100, 100));
    }
}
