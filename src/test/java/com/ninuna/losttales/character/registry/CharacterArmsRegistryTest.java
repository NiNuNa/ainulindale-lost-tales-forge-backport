package com.ninuna.losttales.character.registry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterArmsRegistryTest {

    @Test
    public void theBodyTypeOnlyPicksTheDefault() {
        assertEquals(CharacterArmsRegistry.SLENDER,
                CharacterArmsRegistry.defaultFor(CharacterBodyTypeRegistry.FEMININE));
        assertEquals(CharacterArmsRegistry.BROAD,
                CharacterArmsRegistry.defaultFor(CharacterBodyTypeRegistry.MASCULINE));
        assertEquals(CharacterArmsRegistry.BROAD,
                CharacterArmsRegistry.defaultFor(CharacterBodyTypeRegistry.NEUTRAL));
        assertEquals(CharacterArmsRegistry.BROAD,
                CharacterArmsRegistry.defaultFor(null));
    }

    @Test
    public void unknownValuesNormalizeToBroad() {
        assertTrue(CharacterArmsRegistry.contains(" LOSTTALES:SLENDER "));
        assertFalse(CharacterArmsRegistry.contains("slender"));
        assertFalse(CharacterArmsRegistry.contains("losttales:slim"));
        assertEquals(CharacterArmsRegistry.SLENDER,
                CharacterArmsRegistry.normalizeOrBroad("losttales:slender"));
        assertEquals(CharacterArmsRegistry.BROAD,
                CharacterArmsRegistry.normalizeOrBroad("losttales:huge"));
        assertEquals(CharacterArmsRegistry.BROAD,
                CharacterArmsRegistry.normalizeOrBroad(null));
        assertEquals(2, CharacterArmsRegistry.getAll().size());
    }
}
