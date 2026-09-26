package com.ninuna.losttales.gui.screen.character;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** A drag turns the figure round within one turn; the wheel zooms it a tenth a notch, within its bounds. */
public final class CharacterFigureStageTest {
    @Test
    public void aDragTurnsTheFigureRoundWithinOneTurn() {
        assertEquals(25.0F + 14.0F, CharacterFigureStage.turnedBy(25.0F, 10.0F),
                1.0E-4F);
        assertEquals("a turn past the start comes round from the end",
                360.0F - 14.0F + 5.0F,
                CharacterFigureStage.turnedBy(5.0F, -10.0F), 1.0E-4F);
        assertEquals(0.0F, CharacterFigureStage.turnedBy(346.0F, 10.0F),
                1.0E-4F);
    }

    @Test
    public void theWheelZoomsATenthANotchWithinItsBounds() {
        assertEquals(1.2F, CharacterFigureStage.zoomed(1.0F, 2), 1.0E-4F);
        assertEquals(0.6F, CharacterFigureStage.zoomed(1.0F, -9), 1.0E-4F);
        assertEquals(1.6F, CharacterFigureStage.zoomed(1.5F, 3), 1.0E-4F);
    }
}
