package com.ninuna.losttales.character.registry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Locks the body model catalogue to the race catalogue it serves. */
public final class CharacterBodyModelRegistryTest {

    @Test
    public void everyRaceHasARegisteredDefaultModel() {
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            String modelId = CharacterBodyModelRegistry.getDefaultModelId(race.getId());
            assertFalse(race.getId() + " has no default model", modelId.isEmpty());
            assertNotNull(race.getId() + " default model is not registered",
                    CharacterBodyModelRegistry.get(modelId));
            assertTrue(CharacterBodyModelRegistry.isCompatible(race.getId(), modelId));
        }
    }

    @Test
    public void chestVariantsExistExactlyForGenderedRaces() {
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            CharacterBodyModelDefinition model = CharacterBodyModelRegistry.get(
                    CharacterBodyModelRegistry.getDefaultModelId(race.getId()));
            assertEquals(race.getId() + " chest variant",
                    race.hasGenderedModels(), model.hasChestVariant());
            assertTrue(race.getId() + " body types", model.supportsBodyTypes());
        }
    }

    @Test
    public void everyBodyOffersArmWidthsAndOnlyOffsetShouldersLosePivots() {
        for (CharacterBodyModelDefinition model : CharacterBodyModelRegistry.getAll()) {
            String id = model.getId();
            boolean offsetShoulders = CharacterBodyModelRegistry.LOTR_HOBBIT.equals(id)
                    || CharacterBodyModelRegistry.LOTR_HALF_TROLL.equals(id);
            boolean classicLayout = CharacterBodyModelRegistry.LOTR_ORC.equals(id)
                    || CharacterBodyModelRegistry.LOTR_URUK.equals(id);
            boolean player = CharacterBodyModelRegistry.LOSTTALES_PLAYER.equals(id);
            assertTrue(id, model.supportsBodyTypes());
            assertEquals(id, !offsetShoulders, model.hasVanillaArmPivots());
            CharacterSkinLayout expected = player
                    ? CharacterSkinLayout.MINECRAFT_64X64
                    : classicLayout ? CharacterSkinLayout.LOTR_64X32
                            : CharacterSkinLayout.LOTR_64X64;
            assertEquals(id, expected, model.getLayout());
        }
        // Only humans may wear the player body so far; it also serves
        // characterless players.
        for (CharacterRaceDefinition race : CharacterRaceRegistry.getAll()) {
            assertEquals(race.getId(),
                    CharacterRaceRegistry.HUMAN.equals(race.getId()),
                    CharacterBodyModelRegistry.isCompatible(
                            race.getId(), CharacterBodyModelRegistry.LOSTTALES_PLAYER));
        }
    }

    @Test
    public void lookupNormalizesAndRejectsForeignModels() {
        assertNotNull(CharacterBodyModelRegistry.get(" LOTR:Human "));
        assertNull(CharacterBodyModelRegistry.get("lotr:balrog"));
        assertEquals("", CharacterBodyModelRegistry.getDefaultModelId("losttales:balrog"));
        assertFalse(CharacterBodyModelRegistry.isCompatible(
                CharacterRaceRegistry.ELF, CharacterBodyModelRegistry.LOTR_HUMAN));
        assertFalse(CharacterBodyModelRegistry.isCompatible(
                CharacterRaceRegistry.ELF, ""));
        assertTrue(CharacterBodyModelRegistry.isCompatible(
                CharacterRaceRegistry.LEGACY_TROLL,
                CharacterBodyModelRegistry.LOTR_HALF_TROLL));
    }
}
