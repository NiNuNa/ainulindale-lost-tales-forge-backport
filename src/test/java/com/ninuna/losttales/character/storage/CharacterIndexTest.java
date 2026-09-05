package com.ninuna.losttales.character.storage;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * The world data's character index answers by id for every roster, is
 * rebuilt after a roster is written back, and treats an id two rosters
 * hold as nobody's.
 */
public final class CharacterIndexTest {

    private static final UUID OWNER_A = UUID.fromString(
            "a3000000-0000-0000-0000-00000000003a");
    private static final UUID OWNER_B = UUID.fromString(
            "b3000000-0000-0000-0000-00000000003b");
    private static final UUID CHARACTER = UUID.fromString(
            "c3000000-0000-0000-0000-00000000003c");
    private static final UUID OTHER = UUID.fromString(
            "d3000000-0000-0000-0000-00000000003d");

    @Test
    public void aWrittenRosterIsFoundByCharacterIdAndByOwner() {
        CharacterWorldData data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterRoster roster = data.getOrCreateRoster(OWNER_A);
        assertNull(data.findCharacter(CHARACTER));
        assertFalse(data.containsCharacter(CHARACTER));

        assertTrue(roster.addCharacter(character(CHARACTER, OWNER_A, 0)));
        data.saveRoster(roster);

        CharacterIndex index = data.characterIndex();
        assertNotNull(data.findCharacter(CHARACTER));
        assertTrue(data.containsCharacter(CHARACTER));
        assertSame(roster, index.rosterOf(CHARACTER));
        assertEquals(1, index.countOf(CHARACTER));
        assertEquals(0, index.countOf(OTHER));
        assertTrue(index.isAccountOwner(OWNER_A));
        assertTrue(index.hasOwner(OWNER_A));
        assertTrue(index.hasOwner(CHARACTER));
        assertFalse(index.hasOwner(OTHER));
    }

    @Test
    public void theIndexFollowsARemovalOnceTheRosterIsWrittenBack() {
        CharacterWorldData data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterRoster roster = data.getOrCreateRoster(OWNER_A);
        roster.addCharacter(character(CHARACTER, OWNER_A, 0));
        data.saveRoster(roster);
        assertTrue(data.containsCharacter(CHARACTER));

        roster.removeCharacter(CHARACTER);
        data.saveRoster(roster);
        assertFalse(data.containsCharacter(CHARACTER));
        assertNull(data.findCharacter(CHARACTER));
    }

    @Test
    public void anIdHeldByTwoRostersIsAmbiguousAndAnswersNobody() {
        CharacterWorldData data = new CharacterWorldData(CharacterWorldData.DATA_NAME);
        CharacterRoster first = data.getOrCreateRoster(OWNER_A);
        first.addCharacter(character(CHARACTER, OWNER_A, 0));
        data.saveRoster(first);
        CharacterRoster second = data.getOrCreateRoster(OWNER_B);
        second.addCharacter(character(CHARACTER, OWNER_B, 0));
        data.saveRoster(second);

        CharacterIndex index = data.characterIndex();
        assertNull(index.find(CHARACTER));
        assertNull(index.rosterOf(CHARACTER));
        assertTrue(index.isAmbiguous(CHARACTER));
        assertTrue(index.contains(CHARACTER));
        assertEquals(2, index.countOf(CHARACTER));
    }

    private static RoleplayCharacter character(UUID id, UUID owner, int slot) {
        return RoleplayCharacter.builder(id, owner)
                .slot(slot)
                .name("Index " + slot)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_bree_male_0")
                .build();
    }
}
