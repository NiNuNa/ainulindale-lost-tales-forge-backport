package com.ninuna.losttales.character.registry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CharacterBodyTypeRegistryTest {

    @Test
    public void sexOnlyPicksTheDefault() {
        assertEquals(CharacterBodyTypeRegistry.SLIM,
                CharacterBodyTypeRegistry.defaultFor(CharacterGenderRegistry.FEMALE));
        assertEquals(CharacterBodyTypeRegistry.WIDE,
                CharacterBodyTypeRegistry.defaultFor(CharacterGenderRegistry.MALE));
        assertEquals(CharacterBodyTypeRegistry.WIDE,
                CharacterBodyTypeRegistry.defaultFor(CharacterGenderRegistry.NON_BINARY));
        assertEquals(CharacterBodyTypeRegistry.WIDE,
                CharacterBodyTypeRegistry.defaultFor(null));
    }

    @Test
    public void unknownValuesNormalizeToWide() {
        assertTrue(CharacterBodyTypeRegistry.contains(" LOSTTALES:SLIM "));
        assertFalse(CharacterBodyTypeRegistry.contains("slim"));
        assertEquals(CharacterBodyTypeRegistry.SLIM,
                CharacterBodyTypeRegistry.normalizeOrWide("losttales:slim"));
        assertEquals(CharacterBodyTypeRegistry.WIDE,
                CharacterBodyTypeRegistry.normalizeOrWide("losttales:huge"));
        assertEquals(CharacterBodyTypeRegistry.WIDE,
                CharacterBodyTypeRegistry.normalizeOrWide(null));
        assertEquals(2, CharacterBodyTypeRegistry.getAll().size());
    }
}
