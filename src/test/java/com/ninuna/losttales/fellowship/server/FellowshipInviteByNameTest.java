package com.ninuna.losttales.fellowship.server;

import com.ninuna.losttales.character.validation.CharacterNames;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterGenderRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * An invitation names its character by name: the server finds the one
 * character on the server that goes by it, played or not, as names are
 * compared, and takes its id and owner from the roster that holds it.
 */
public final class FellowshipInviteByNameTest {

    private static final UUID ACCOUNT_A = new UUID(1L, 1L);
    private static final UUID ACCOUNT_B = new UUID(2L, 2L);
    private static final UUID HURIN = new UUID(10L, 10L);
    private static final UUID BEREN = new UUID(11L, 11L);

    @Test
    public void theCharacterOfThatNameIsFoundInAnyRoster() {
        List<CharacterRoster> rosters = rosters();

        RoleplayCharacter found = CharacterNames.named(rosters, "Hurin");
        assertEquals(HURIN, found.getCharacterId());
        assertEquals(ACCOUNT_B, found.getOwnerId());
    }

    /** Names are compared as everywhere: case, accents and marks aside. */
    @Test
    public void theNameIsComparedAsNamesAre() {
        assertEquals(HURIN, CharacterNames.named(rosters(), " húrin ")
                .getCharacterId());
        assertEquals(BEREN, CharacterNames.named(rosters(), "BE-REN")
                .getCharacterId());
    }

    @Test
    public void aNameNobodyGoesByFindsNobody() {
        assertNull(CharacterNames.named(rosters(), "Turin"));
        assertNull(CharacterNames.named(rosters(), "  "));
        assertNull(CharacterNames.named(rosters(), null));
        assertNull(CharacterNames.named(null, "Hurin"));
    }

    /** Two characters sharing a name, which only an edited save holds, answer nobody. */
    @Test
    public void aNameTwoCharactersShareFindsNobody() {
        List<CharacterRoster> rosters = rosters();
        CharacterRoster third = new CharacterRoster(new UUID(3L, 3L));
        third.addCharacter(character(new UUID(12L, 12L), new UUID(3L, 3L), "Hurin"));
        rosters.add(third);

        assertNull(CharacterNames.named(rosters, "Hurin"));
    }

    private static List<CharacterRoster> rosters() {
        List<CharacterRoster> rosters = new ArrayList<CharacterRoster>();
        CharacterRoster first = new CharacterRoster(ACCOUNT_A);
        first.addCharacter(character(BEREN, ACCOUNT_A, "Beren"));
        rosters.add(first);
        CharacterRoster second = new CharacterRoster(ACCOUNT_B);
        second.addCharacter(character(HURIN, ACCOUNT_B, "Húrin"));
        rosters.add(second);
        return rosters;
    }

    private static RoleplayCharacter character(UUID id, UUID owner, String name) {
        return RoleplayCharacter.builder(id, owner)
                .slot(0)
                .name(name)
                .race(CharacterRaceRegistry.HUMAN)
                .gender(CharacterGenderRegistry.MALE)
                .skin("losttales:human_bree_male_0")
                .build();
    }
}
