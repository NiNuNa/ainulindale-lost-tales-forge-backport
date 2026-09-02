package com.ninuna.losttales.character.registry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public final class CharacterChestTypeRegistryTest {

    @Test
    public void sexOnlyPicksTheDefault() {
        assertEquals(CharacterChestTypeRegistry.ROUNDED_MEDIUM,
                CharacterChestTypeRegistry.defaultFor(CharacterGenderRegistry.FEMALE));
        assertEquals(CharacterChestTypeRegistry.NONE,
                CharacterChestTypeRegistry.defaultFor(CharacterGenderRegistry.MALE));
        assertEquals(CharacterChestTypeRegistry.NONE,
                CharacterChestTypeRegistry.defaultFor(CharacterGenderRegistry.NON_BINARY));
    }

    @Test
    public void everyTypeHasAShapeAndSizesGrowWithinAShape() {
        assertEquals(8, CharacterChestTypeRegistry.getAll().size());
        for (CharacterChestTypeDefinition definition : CharacterChestTypeRegistry.getAll()) {
            assertNotNull(definition.getShape());
            assertEquals(definition.isNone(),
                    definition.getShape() == CharacterChestTypeRegistry.Shape.NONE);
        }
        assertTrue(size(CharacterChestTypeRegistry.ROUNDED_SMALL)
                < size(CharacterChestTypeRegistry.ROUNDED_MEDIUM));
        assertTrue(size(CharacterChestTypeRegistry.ROUNDED_MEDIUM)
                < size(CharacterChestTypeRegistry.ROUNDED_LARGE));
        assertTrue(size(CharacterChestTypeRegistry.FULL_SMALL)
                < size(CharacterChestTypeRegistry.FULL_MEDIUM));
        assertTrue(size(CharacterChestTypeRegistry.FULL_MEDIUM)
                < size(CharacterChestTypeRegistry.FULL_LARGE));
        assertEquals(CharacterChestTypeRegistry.Shape.CLASSIC,
                CharacterChestTypeRegistry.get(CharacterChestTypeRegistry.CLASSIC).getShape());
    }

    @Test
    public void unknownValuesNormalizeToNone() {
        assertTrue(CharacterChestTypeRegistry.contains(" LOSTTALES:FULL_LARGE "));
        assertFalse(CharacterChestTypeRegistry.contains("rounded_medium"));
        assertEquals(CharacterChestTypeRegistry.NONE,
                CharacterChestTypeRegistry.normalizeOrNone("losttales:huge"));
        assertEquals(CharacterChestTypeRegistry.NONE,
                CharacterChestTypeRegistry.normalizeOrNone(null));
        assertEquals(CharacterChestTypeRegistry.FULL_LARGE,
                CharacterChestTypeRegistry.normalizeOrNone("losttales:full_large"));
    }

    private static float size(String id) {
        return CharacterChestTypeRegistry.get(id).getSize();
    }
}
