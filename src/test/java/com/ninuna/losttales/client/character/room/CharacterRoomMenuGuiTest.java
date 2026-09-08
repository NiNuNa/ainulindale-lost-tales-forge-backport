package com.ninuna.losttales.client.character.room;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The room menu's rows: centred as a block, spaced as the main menu spaces its own. */
public final class CharacterRoomMenuGuiTest {

    @Test
    public void rowsKeepTheMainMenuGapsAndSeparateThePlayControls() {
        int[] rows = CharacterRoomMenuGui.rowTops(480, true);
        assertEquals(CharacterRoomMenuGui.BUTTON_HEIGHT + CharacterRoomMenuGui.BUTTON_GAP,
                rows[1] - rows[0]);
        assertEquals(CharacterRoomMenuGui.BUTTON_HEIGHT + CharacterRoomMenuGui.BUTTON_GAP,
                rows[2] - rows[1]);
        assertEquals(CharacterRoomMenuGui.BUTTON_HEIGHT + CharacterRoomMenuGui.GROUP_GAP,
                rows[3] - rows[2]);
    }

    @Test
    public void withheldPlayControlsLeaveRoomForTheirNote() {
        int[] open = CharacterRoomMenuGui.rowTops(480, true);
        int[] locked = CharacterRoomMenuGui.rowTops(480, false);
        assertEquals(CharacterRoomMenuGui.NOTE_HEIGHT,
                (locked[3] - locked[2]) - (open[3] - open[2]));
    }

    @Test
    public void blockIsCentredWithItsHeaderOnAnyScreen() {
        for (int height : new int[] {240, 349, 480, 1080}) {
            for (boolean setUp : new boolean[] {true, false}) {
                int[] rows = CharacterRoomMenuGui.rowTops(height, setUp);
                int top = rows[0] - CharacterRoomMenuGui.HEADER_HEIGHT;
                int bottom = rows[3] + CharacterRoomMenuGui.BUTTON_HEIGHT;
                assertTrue(Math.abs(top - (height - bottom)) <= 1);
            }
        }
    }
}
