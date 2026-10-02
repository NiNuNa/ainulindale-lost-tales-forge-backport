package com.ninuna.losttales.character.registry;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * A race nobody may choose is still a race.
 *
 * <p>No player may make a half-troll: the body is half again as broad as
 * a biped and eight pixels taller, built from boxes of its own, and no
 * armour in the game is cut for it. The race stays known, since a
 * server's own lore file may describe one, and that character has to
 * load, render and play.</p>
 */
public final class CharacterRaceSelectabilityTest {

    @Test
    public void nobodyMayChooseAHalfTroll() {
        CharacterRaceDefinition halfTroll =
                CharacterRaceRegistry.get(CharacterRaceRegistry.HALF_TROLL);
        assertNotNull("the race is still known", halfTroll);
        assertFalse("but nobody may choose it", halfTroll.isSelectable());
        assertFalse(selectableIds().contains(CharacterRaceRegistry.HALF_TROLL));
    }

    @Test
    public void aRecordThatIsOneStillReadsAsOne() {
        // The registry answering for the id is what keeps the codec from
        // repairing such a character into a human.
        assertNotNull(CharacterRaceRegistry.get(
                CharacterRaceRegistry.HALF_TROLL));
    }

    @Test
    public void everyOtherRaceStaysChoosable() {
        List<String> selectable = selectableIds();
        assertTrue(selectable.contains(CharacterRaceRegistry.HUMAN));
        assertTrue(selectable.contains(CharacterRaceRegistry.ELF));
        assertTrue(selectable.contains(CharacterRaceRegistry.DWARF));
        assertTrue(selectable.contains(CharacterRaceRegistry.HOBBIT));
        assertTrue(selectable.contains(CharacterRaceRegistry.ORC));
        assertTrue(selectable.contains(CharacterRaceRegistry.URUK));
        assertEquals("six races, and the half-troll left out",
                CharacterRaceRegistry.getAll().size() - 1, selectable.size());
    }

    private static List<String> selectableIds() {
        List<String> ids = new java.util.ArrayList<String>();
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            if (race.isSelectable()) {
                ids.add(race.getId());
            }
        }
        return ids;
    }
}
