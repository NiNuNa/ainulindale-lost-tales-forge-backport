package com.ninuna.losttales.accessory;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** What each slot takes and each accessory does, looked up by item and by id (ACC-01). */
public final class AccessoryCatalogueTest {

    @Test
    public void slotsAndEffectsAreLookedUp() {
        AccessoryDefinition oneRing = ring("losttales:the_one_ring", "losttales:the_one_ring",
                AccessoryEffect.CONCEALMENT, AccessoryEffect.WRAITH_WORLD);
        AccessoryDefinition gold = ring("lotr:gold_ring", "lotr:item.goldRing");
        AccessoryCatalogue catalogue = AccessoryCatalogue.of(list(oneRing, gold), null);

        assertSame(oneRing, catalogue.find(AccessorySlotType.RING, "losttales:the_one_ring"));
        assertSame(gold, catalogue.find(AccessorySlotType.RING, "lotr:item.goldRing"));
        assertSame(gold, catalogue.byId("lotr:gold_ring"));
        assertNull(catalogue.find(AccessorySlotType.RING, "lotr:item.silverRing"));
        assertNull(catalogue.find(null, "lotr:item.goldRing"));
        assertNull(catalogue.find(AccessorySlotType.RING, null));
        assertNull(catalogue.byId("lotr:silver_ring"));
        assertTrue(catalogue.byId("losttales:the_one_ring").has(AccessoryEffect.CONCEALMENT));
        assertTrue(catalogue.byId("lotr:gold_ring").getEffects().isEmpty());
    }

    @Test
    public void anItemTakenOrAnIdUsedIsLeftOut() {
        List<String> problems = new ArrayList<String>();
        AccessoryCatalogue catalogue = AccessoryCatalogue.of(list(
                ring("a:first", "lotr:item.goldRing"),
                ring("a:second", "lotr:item.goldRing"),
                ring("a:first", "lotr:item.silverRing"),
                new AccessoryDefinition("a:slotless", null, "lotr:item.mithrilRing", null)),
                problems);

        assertEquals(1, catalogue.getDefinitions().size());
        assertEquals("a:first", catalogue.find(AccessorySlotType.RING, "lotr:item.goldRing")
                .getId());
        assertEquals(3, problems.size());
        assertTrue(problems.get(0), problems.get(0).contains("a:first is already"));
        assertTrue(problems.get(1), problems.get(1).contains("earlier accessory"));
        assertTrue(problems.get(2), problems.get(2).contains("no slot"));
    }

    @Test
    public void theCatalogueIsBounded() {
        List<AccessoryDefinition> many = new ArrayList<AccessoryDefinition>();
        for (int index = 0; index <= AccessoryCatalogue.MAX_ACCESSORIES; index++) {
            many.add(ring("a:ring_" + index, "a:item_" + index));
        }
        List<String> problems = new ArrayList<String>();
        AccessoryCatalogue catalogue = AccessoryCatalogue.of(many, problems);

        assertEquals(AccessoryCatalogue.MAX_ACCESSORIES, catalogue.getDefinitions().size());
        assertEquals(1, problems.size());
        assertNull(catalogue.byId("a:ring_" + AccessoryCatalogue.MAX_ACCESSORIES));
    }

    @Test
    public void theEffectsAreTheCodesList() {
        assertSame(AccessoryEffect.CONCEALMENT, AccessoryEffect.byId("losttales:concealment"));
        assertSame(AccessoryEffect.WRAITH_WORLD, AccessoryEffect.byId("losttales:wraith_world"));
        assertNull("no effect but the code's two", AccessoryEffect.byId("losttales:one_ring"));
        assertNull(AccessoryEffect.byId(null));
        assertSame(AccessorySlotType.RING, AccessorySlotType.byName("ring"));
        assertNull(AccessorySlotType.byName("RING"));
    }

    private static AccessoryDefinition ring(String id, String item, AccessoryEffect... effects) {
        EnumSet<AccessoryEffect> set = EnumSet.noneOf(AccessoryEffect.class);
        for (AccessoryEffect effect : effects) {
            set.add(effect);
        }
        return new AccessoryDefinition(id, AccessorySlotType.RING, item, set);
    }

    private static List<AccessoryDefinition> list(AccessoryDefinition... definitions) {
        List<AccessoryDefinition> list = new ArrayList<AccessoryDefinition>();
        for (AccessoryDefinition definition : definitions) {
            list.add(definition);
        }
        return list;
    }
}
