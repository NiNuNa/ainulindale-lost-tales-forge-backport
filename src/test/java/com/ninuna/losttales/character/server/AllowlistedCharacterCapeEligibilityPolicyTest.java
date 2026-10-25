package com.ninuna.losttales.character.server;

import com.ninuna.losttales.character.cape.CharacterCapeCatalog;
import com.ninuna.losttales.character.cape.CharacterCapeDefinition;
import com.ninuna.losttales.character.model.RoleplayCharacter;
import com.ninuna.losttales.character.registry.CharacterBodyTypeRegistry;
import com.ninuna.losttales.character.registry.CharacterRaceRegistry;
import com.ninuna.losttales.character.validation.CharacterErrorId;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.UUID;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The server's cape check follows the cape files (CHAR-39): no cape and an
 * open cape are always allowed; a cape not in the list is refused; one
 * kept for races or factions is refused to anyone else, the pledge counting
 * as the faction while there is one.
 */
public final class AllowlistedCharacterCapeEligibilityPolicyTest {

    private static final UUID OWNER = UUID.fromString("10000000-0000-0000-0000-000000000001");

    @After
    public void bundledAgain() {
        CharacterCapeCatalog.resetToBundled();
    }

    @Test
    public void noCapeAndAnOpenCapeAreAllowed() {
        RoleplayCharacter human = character("lotr:gondor", "");
        assertTrue(AllowlistedCharacterCapeEligibilityPolicy.validate(human, "").isValid());
        assertTrue(AllowlistedCharacterCapeEligibilityPolicy.validate(human,
                "losttales:rohan").isValid());
    }

    @Test
    public void aCapeNotInTheListIsRefused() {
        assertEquals(CharacterErrorId.INVALID_CAPE,
                AllowlistedCharacterCapeEligibilityPolicy.validate(
                        character("lotr:gondor", ""), "myserver:none").getErrorId());
    }

    @Test
    public void aLimitedCapeIsRefusedToAnyoneElse() {
        CharacterCapeCatalog.set(Arrays.asList(new CharacterCapeDefinition("myserver:white_tree",
                "White Tree", "lotr:GONDOR", null,
                new LinkedHashSet<String>(Arrays.asList("lotr:gondor")))));

        assertTrue(AllowlistedCharacterCapeEligibilityPolicy.validate(
                character("lotr:gondor", ""), "myserver:white_tree").isValid());
        assertEquals(CharacterErrorId.CAPE_NOT_ELIGIBLE,
                AllowlistedCharacterCapeEligibilityPolicy.validate(
                        character("lotr:rohan", ""), "myserver:white_tree").getErrorId());
        assertTrue("the pledge is the faction while there is one",
                AllowlistedCharacterCapeEligibilityPolicy.validate(
                        character("lotr:rohan", "lotr:gondor"), "myserver:white_tree").isValid());
    }

    private static RoleplayCharacter character(String startingFaction, String pledge) {
        return RoleplayCharacter.builder(UUID.randomUUID(), OWNER)
                .slot(0).name("Beregond").race(CharacterRaceRegistry.HUMAN)
                .bodyType(CharacterBodyTypeRegistry.MASCULINE).skin("losttales:human_gondor_masculine_0")
                .startingFaction(startingFaction).pledgedFaction(pledge).build();
    }
}
