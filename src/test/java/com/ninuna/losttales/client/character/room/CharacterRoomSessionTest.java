package com.ninuna.losttales.client.character.room;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterRoomSessionTest {

    @Test
    public void creatorOpensOnceThePlayerStandsInTheRoomWithNoScreenUp() {
        assertTrue(CharacterRoomSession.shouldOpenCreator(false, false));
    }

    @Test
    public void creatorWaitsForTheTerrainScreen() {
        assertFalse(CharacterRoomSession.shouldOpenCreator(false, true));
    }

    @Test
    public void creatorIsNotOpenedAgainUnasked() {
        // Closed by saving or escaping, the room is the player's to walk.
        assertFalse(CharacterRoomSession.shouldOpenCreator(true, false));
        assertFalse(CharacterRoomSession.shouldOpenCreator(true, true));
    }

    @Test
    public void closingTheCreatorLeavesTheRoomInFirstPerson() {
        assertEquals(0, CharacterRoomSession.perspectiveAfterScreen(true, 1));
        assertEquals(0, CharacterRoomSession.perspectiveAfterScreen(true, 2));
        // Any other screen leaves the player's own choice alone.
        assertEquals(1, CharacterRoomSession.perspectiveAfterScreen(false, 1));
        assertEquals(0, CharacterRoomSession.perspectiveAfterScreen(false, 0));
    }

    @Test
    public void menuIsOnlyForTheRoom() {
        // Outside a visit the use key keeps its ordinary meaning.
        CharacterRoomSession.clear();
        assertFalse(CharacterRoomSession.isActive());
        assertFalse(CharacterRoomSession.isInRoom(null));
        assertFalse(CharacterRoomSession.openMenu(null));
    }
}
