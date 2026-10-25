package com.ninuna.losttales.character.registry;

import com.ninuna.losttales.character.model.CharacterRoster;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import com.ninuna.losttales.character.validation.CharacterValidator;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The mod's races are six, and the half-troll is none of them (CHAR-09). */
public final class CharacterRaceCatalogueTest {

    private static final UUID OWNER =
            UUID.fromString("71000000-0000-0000-0000-000000000017");

    @Test
    public void theRacesAreSixAndEveryOneMayBeChosen() {
        List<String> ids = new ArrayList<String>();
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            ids.add(race.getId());
        }
        assertEquals(Arrays.asList(CharacterRaceRegistry.HUMAN,
                CharacterRaceRegistry.ELF, CharacterRaceRegistry.DWARF,
                CharacterRaceRegistry.HOBBIT, CharacterRaceRegistry.ORC,
                CharacterRaceRegistry.URUK), ids);
    }

    @Test
    public void thereIsNoHalfTroll() {
        assertNull(CharacterRaceRegistry.get("losttales:half_troll"));
        assertNull(CharacterBodyModelRegistry.get("lotr:half_troll"));
        assertEquals(CharacterErrorId.INVALID_RACE,
                CharacterValidator.validateAppearance(new CharacterRoster(OWNER), null,
                        "Bogdal", "losttales:half_troll",
                        CharacterBodyTypeRegistry.NEUTRAL, "losttales:half_troll_0",
                        CharacterArmsRegistry.BROAD,
                        CharacterChestTypeRegistry.CLASSIC, "", 25).getErrorId());
    }
}
