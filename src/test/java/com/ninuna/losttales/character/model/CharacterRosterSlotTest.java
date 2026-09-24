package com.ninuna.losttales.character.model;

import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Filling the last open slot opens the next, until all nine are open. */
public final class CharacterRosterSlotTest {

    @Test
    public void onlyTheLastOpenSlotOpensTheNext() {
        CharacterRoster roster = new CharacterRoster(UUID.randomUUID());
        assertEquals(CharacterRoster.INITIAL_UNLOCKED_SLOTS,
                roster.getUnlockedSlotCount());

        assertTrue(roster.unlockNextSlotAfter(0));
        assertEquals(2, roster.getUnlockedSlotCount());
        assertFalse("an earlier slot opens nothing",
                roster.unlockNextSlotAfter(0));
        assertEquals(2, roster.getUnlockedSlotCount());
    }

    @Test
    public void noSlotOpensPastTheLast() {
        CharacterRoster roster = new CharacterRoster(UUID.randomUUID());
        for (int slot = 0; slot < CharacterRoster.MAX_SLOTS - 1; slot++) {
            assertTrue(roster.unlockNextSlotAfter(slot));
        }
        assertEquals(CharacterRoster.MAX_SLOTS, roster.getUnlockedSlotCount());
        assertFalse(roster.unlockNextSlotAfter(CharacterRoster.MAX_SLOTS - 1));
        assertEquals(CharacterRoster.MAX_SLOTS, roster.getUnlockedSlotCount());
    }
}
