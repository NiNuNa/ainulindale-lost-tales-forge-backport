package com.ninuna.losttales.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.block.tileentity.ELostTalesTileEntity;
import com.ninuna.losttales.entity.ELostTalesEntity;
import com.ninuna.losttales.item.material.ELostTalesItemMaterial;
import com.ninuna.losttales.util.EnglishWords;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lotr.common.fac.LOTRFaction;
import org.junit.Test;

/**
 * What the content is called and what its tooltips say: no item, block,
 * entity or tile entity keeps a test name (MOD-16), each has its English
 * name, and no line promises what the mod does not have (CONTENT-01).
 */
public final class ContentNamesTest {
    /** The marks of a name given for testing. */
    private static final String[] TEST_MARKS = {"test_", "_test", "testperson"};

    /** Lines of things the mod does not have. */
    private static final String[] GONE = {
            "item.creation_tool_loot_respawner.name",
            "item.creation_tool_loot_respawner.lore.line1",
            "item.creation_tool_loot_respawner.lore.line2",
            "item.losttales.tooltip.set.heavy.bonus",
            "item.losttales.tooltip.set.light.bonus",
            "item.losttales.tooltip.set.no_bonus",
            "item.losttales.type.tool"};

    @Test
    public void noContentIsNamedAsATest() {
        List<String> names = new ArrayList<String>();
        for (ELostTalesItem item : ELostTalesItem.values()) {
            names.add(item.name());
            names.add(registryName(item.getItem().getUnlocalizedName()));
        }
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            names.add(block.name());
            names.add(registryName(block.getBlock().getUnlocalizedName()));
        }
        for (ELostTalesEntity entity : ELostTalesEntity.values()) {
            names.add(entity.name());
            names.add(entity.getName());
        }
        for (ELostTalesTileEntity tileEntity : ELostTalesTileEntity.values()) {
            names.add(tileEntity.name());
            names.add(tileEntity.getId());
        }
        for (String name : names) {
            String lower = name.toLowerCase(Locale.ROOT);
            for (String mark : TEST_MARKS) {
                assertFalse(name, lower.contains(mark));
            }
        }
    }

    @Test
    public void theRenamedContentKeepsItsEnglishWords() {
        assertEquals("practice_horn",
                registryName(ELostTalesItem.PRACTICE_HORN.getItem().getUnlocalizedName()));
        assertEquals("tall_lamp",
                registryName(ELostTalesBlock.LAMP_TALL.getBlock().getUnlocalizedName()));
        assertEquals("BlueGoblinSoldier", ELostTalesEntity.BLUE_GOBLIN_SOLDIER.getName());
        assertEquals(7000, ELostTalesEntity.BLUE_GOBLIN_SOLDIER.getId());

        assertEquals("Practice Horn", EnglishWords.INSTANCE.format("item.practice_horn.name"));
        assertTrue(EnglishWords.INSTANCE.has("item.practice_horn.lore.line1"));
        assertTrue(EnglishWords.INSTANCE.has("item.practice_horn.lore.line2"));
        assertEquals("Tall Lamp", EnglishWords.INSTANCE.format("tile.tall_lamp.name"));
        assertEquals("Blue Goblin Soldier",
                EnglishWords.INSTANCE.format("entity.losttales.BlueGoblinSoldier.name"));
    }

    @Test
    public void everyItemBlockAndEntityHasAnEnglishName() {
        for (ELostTalesItem item : ELostTalesItem.values()) {
            String key = item.getItem().getUnlocalizedName() + ".name";
            assertTrue(key, EnglishWords.INSTANCE.has(key));
        }
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            String key = block.getBlock().getUnlocalizedName() + ".name";
            assertTrue(key, EnglishWords.INSTANCE.has(key));
        }
        for (ELostTalesEntity entity : ELostTalesEntity.values()) {
            String key = "entity.losttales." + entity.getName() + ".name";
            assertTrue(key, EnglishWords.INSTANCE.has(key));
        }
    }

    @Test
    public void noLinePromisesTheRespawnerOrASetBonus() {
        for (String key : GONE) {
            assertFalse(key, EnglishWords.INSTANCE.has(key));
        }
    }

    /**
     * The Arnorian arms' Ctrl tooltip names LOTR's Rangers of the North,
     * under the line LOTR's lang file keeps for them.
     */
    @Test
    public void theArnorianArmsNameTheRangersOfTheNorth() {
        assertSame(LOTRFaction.RANGER_NORTH, ELostTalesItemMaterial.ARNOR.getFaction());
        assertEquals("RANGER_NORTH", ELostTalesItemMaterial.ARNOR.getFaction().codeName());
    }

    /** The registry name an item or block registers under. */
    private static String registryName(String unlocalizedName) {
        return unlocalizedName.substring(unlocalizedName.indexOf('.') + 1);
    }
}
