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
 * <p>Half-trolls are no longer anyone's to be: the body is half again as
 * broad as a biped and eight pixels taller, built from boxes of its own,
 * and no armour in the game is cut for it. Taking one away is not the
 * same as forgetting it — a character who already is one has to keep
 * loading, rendering and playing, or their record would be repaired into
 * somebody else the next time the world was read.</p>
 */
public final class CharacterRaceSelectabilityTest {

    @Test
    public void aHalfTrollIsNoLongerAnyonesToChoose() {
        CharacterRaceDefinition halfTroll =
                CharacterRaceRegistry.get(CharacterRaceRegistry.HALF_TROLL);
        assertNotNull("the race is still known", halfTroll);
        assertFalse("but nobody may choose it", halfTroll.isSelectable());
        assertFalse(CharacterRaceRegistry.getSelectableIds()
                .contains(CharacterRaceRegistry.HALF_TROLL));
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
        List<String> selectable = CharacterRaceRegistry.getSelectableIds();
        assertTrue(selectable.contains(CharacterRaceRegistry.HUMAN));
        assertTrue(selectable.contains(CharacterRaceRegistry.ELF));
        assertTrue(selectable.contains(CharacterRaceRegistry.DWARF));
        assertTrue(selectable.contains(CharacterRaceRegistry.HOBBIT));
        assertTrue(selectable.contains(CharacterRaceRegistry.ORC));
        assertTrue(selectable.contains(CharacterRaceRegistry.URUK));
        assertEquals("six races, and the half-troll left out",
                CharacterRaceRegistry.getAll().size() - 1, selectable.size());
    }
}
