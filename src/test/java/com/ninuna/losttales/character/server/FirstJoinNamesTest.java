package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * An account joining for the first time under a name a character holds
 * is named in the Server Log with the character and its owner (CHAR-11).
 */
public final class FirstJoinNamesTest {

    private static final UUID ARAGORN_PLAYER = new UUID(1L, 1L);
    private static final UUID NEWCOMER = new UUID(2L, 2L);
    private static final UUID STRIDER = new UUID(10L, 10L);

    @Test
    public void anotherAccountsCharacterOfTheNameIsFound() {
        RoleplayCharacter holder = FirstJoinNames.holderOf(rosters(), NEWCOMER, "strider");

        assertEquals(STRIDER, holder.getCharacterId());
        assertEquals(ARAGORN_PLAYER, holder.getOwnerId());
    }

    /** Names are compared as everywhere, so a look-alike clashes too. */
    @Test
    public void aNameThatOnlyLooksTheSameClashes() {
        assertEquals(STRIDER, FirstJoinNames.holderOf(rosters(), NEWCOMER, "STRIDER")
                .getCharacterId());
    }

    @Test
    public void noClashForAFreeNameOrTheAccountsOwnCharacter() {
        assertNull(FirstJoinNames.holderOf(rosters(), NEWCOMER, "Gimli"));
        assertNull(FirstJoinNames.holderOf(rosters(), ARAGORN_PLAYER, "Strider"));
        assertNull(FirstJoinNames.holderOf(new ArrayList<CharacterRoster>(), NEWCOMER, "Strider"));
    }

    @Test
    public void theLineNamesTheAccountTheCharacterAndItsOwner() {
        assertEquals("Strider joined for the first time under the name of the character"
                        + " Strider, whose account is Ranger1",
                FirstJoinNames.line("Strider", "Strider", "Ranger1"));
    }

    private static List<CharacterRoster> rosters() {
        List<CharacterRoster> rosters = new ArrayList<CharacterRoster>();
        CharacterRoster roster = new CharacterRoster(ARAGORN_PLAYER);
        roster.addCharacter(RoleplayCharacter.builder(STRIDER, ARAGORN_PLAYER)
                .slot(0)
                .name("Strider")
                .race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE)
                .skin("losttales:human_bree_masculine_0")
                .build());
        rosters.add(roster);
        return rosters;
    }
}
