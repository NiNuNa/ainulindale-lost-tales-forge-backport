package com.ninuna.losttales.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.compat.lotr.LotrSpawnEggs;
import com.ninuna.losttales.creativetab.ELostTalesCreativeTabs;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneGuard;
import com.ninuna.losttales.entity.npc.LostTalesEntityOdaneMan;
import com.ninuna.losttales.util.EnglishWords;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lotr.common.LOTRCreativeTabs;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Test;

/**
 * The spawn eggs of the Lost Tales creatures (CONTENT-04): one egg per
 * creature, two colours each, a name from the lang file, and a place in
 * LOTR's spawn-egg tab that falls back to the mod's own.
 */
public final class LostTalesItemSpawnEggTest {
    private static final Item EGG = ELostTalesItem.SPAWN_EGG.getItem();

    @Test
    public void everyCreatureHasAnEgg() {
        List<ItemStack> eggs = eggs();
        assertEquals(ELostTalesEntity.values().length, eggs.size());
        Set<ELostTalesEntity> hatched = new HashSet<ELostTalesEntity>();
        for (int index = 0; index < eggs.size(); index++) {
            ItemStack egg = eggs.get(index);
            assertSame(EGG, egg.getItem());
            assertEquals(ELostTalesEntity.values()[index].getId(), egg.getItemDamage());
            hatched.add(LostTalesItemSpawnEgg.creatureOf(egg));
        }
        assertEquals(ELostTalesEntity.values().length, hatched.size());
    }

    @Test
    public void everyEggHasItsCreaturesTwoColours() {
        for (ELostTalesEntity creature : ELostTalesEntity.values()) {
            ItemStack egg = new ItemStack(EGG, 1, creature.getId());
            assertTrue(creature.name(), isColour(creature.getEggColor()));
            assertTrue(creature.name(), isColour(creature.getEggSpotColor()));
            assertNotEquals(creature.name(), creature.getEggColor(), creature.getEggSpotColor());
            assertEquals(creature.getEggColor(), EGG.getColorFromItemStack(egg, 0));
            assertEquals(creature.getEggSpotColor(), EGG.getColorFromItemStack(egg, 1));
        }
    }

    /** The four pairs the creatures had before their eggs existed. */
    @Test
    public void theCreaturesKeepTheirEggColours() {
        assertColours(ELostTalesEntity.BLUE_GOBLIN_SOLDIER, 13521151, 373075);
        assertColours(ELostTalesEntity.NIA, 854606, 6097486);
        assertColours(ELostTalesEntity.ODANE_MAN, 0x8A5A38, 0xB45A32);
        assertColours(ELostTalesEntity.ODANE_GUARD, 0x4F5A33, 0xC89B5A);
    }

    @Test
    public void everyEggIsNamedFromTheLangFile() {
        assertTrue(EnglishWords.INSTANCE.has(EGG.getUnlocalizedName() + ".name"));
        for (ELostTalesEntity creature : ELostTalesEntity.values()) {
            String creatureName = EnglishWords.INSTANCE.format(creature.getNameKey());
            assertEquals("Spawn " + creatureName,
                    LostTalesItemSpawnEgg.nameOf(creature, EnglishWords.INSTANCE));
        }
        assertEquals("Spawn Blue Goblin Soldier",
                LostTalesItemSpawnEgg.nameOf(ELostTalesEntity.BLUE_GOBLIN_SOLDIER, EnglishWords.INSTANCE));
        assertEquals("entity.losttales.OdaneGuard.name", ELostTalesEntity.ODANE_GUARD.getNameKey());
    }

    @Test
    public void anEggOfNoCreatureIsWhiteAndHatchesNothing() {
        ItemStack blank = new ItemStack(EGG, 1, 0);
        assertNull(LostTalesItemSpawnEgg.creatureOf(blank));
        assertNull(LostTalesItemSpawnEgg.creatureOf(null));
        assertEquals(0xFFFFFF, EGG.getColorFromItemStack(blank, 0));
        assertEquals(0xFFFFFF, EGG.getColorFromItemStack(blank, 1));
    }

    /** A guard is a guard, though its class derives from the Odane man's. */
    @Test
    public void creaturesAreFoundByIdAndExactClass() {
        assertSame(ELostTalesEntity.ODANE_GUARD, ELostTalesEntity.byId(7003));
        assertSame(ELostTalesEntity.ODANE_GUARD, ELostTalesEntity.byClass(LostTalesEntityOdaneGuard.class));
        assertSame(ELostTalesEntity.ODANE_MAN, ELostTalesEntity.byClass(LostTalesEntityOdaneMan.class));
        assertNull(ELostTalesEntity.byId(0));
        assertNull(ELostTalesEntity.byClass(Object.class));
        assertNull(LostTalesItemSpawnEgg.eggOf(null));
    }

    @Test
    public void theEggsStandInLotrsSpawnEggTab() {
        assertSame(LOTRCreativeTabs.tabSpawn, EGG.getCreativeTab());
    }

    @Test
    public void withoutLotrsTabTheEggsStandInTheModsOwn() {
        LOTRCreativeTabs lotrTab = LOTRCreativeTabs.tabSpawn;
        CreativeTabs misc = ELostTalesCreativeTabs.MISC.getCreativeTab();
        try {
            LOTRCreativeTabs.tabSpawn = null;
            assertSame(misc, LotrSpawnEggs.tabOr(misc));
        } finally {
            LOTRCreativeTabs.tabSpawn = lotrTab;
        }
        assertSame(lotrTab, LotrSpawnEggs.tabOr(misc));
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> eggs() {
        List list = new ArrayList();
        EGG.getSubItems(EGG, EGG.getCreativeTab(), list);
        return (List<ItemStack>)list;
    }

    private static boolean isColour(int rgb) {
        return rgb >= 0 && rgb <= 0xFFFFFF;
    }

    private static void assertColours(ELostTalesEntity creature, int shell, int spots) {
        assertEquals(creature.name(), shell, creature.getEggColor());
        assertEquals(creature.name(), spots, creature.getEggSpotColor());
    }
}
