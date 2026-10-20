package com.ninuna.losttales.item;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    /** A name that is the bare word test, or holds it between its parts. */
    private static final String TEST_WORD = "(.*[_.])?test([_.].*)?";

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
            assertFalse(name, lower.matches(TEST_WORD));
        }
    }

    /** The bare word test is caught as well as a test name's marks. */
    @Test
    public void theBareWordTestIsATestName() {
        assertTrue("test".matches(TEST_WORD));
        assertTrue("item.test".matches(TEST_WORD));
        assertFalse("contest".matches(TEST_WORD));
        assertFalse("community_moria_goblin_banner_spear".matches(TEST_WORD));
    }

    /**
     * No model, animation, model texture or item picture keeps a test name
     * (MOD-16): Dain's Hammer and the Moon Elf armour are drawn from files
     * named for them.
     */
    @Test
    public void noModelIsNamedAsATest() throws Exception {
        java.io.File assets = assetsRoot();
        for (String folder : new String[] {"geo", "animations", "textures/models",
                "textures/items", "textures/items/large", "textures/items/community",
                "textures/items/community/large"}) {
            String[] files = new java.io.File(assets, folder).list();
            assertTrue(folder, files != null && files.length > 0);
            for (String file : files) {
                assertFalse(folder + "/" + file, file.toLowerCase(Locale.ROOT)
                        .matches(TEST_WORD));
            }
        }
        for (String path : new String[] {"assets/losttales/geo/dains_hammer.geo.json",
                "assets/losttales/textures/models/dains_hammer.png",
                "assets/losttales/geo/moon_elf_armor_light.geo.json",
                "assets/losttales/textures/models/moon_elf_armor_light.png"}) {
            assertTrue(path, ContentNamesTest.class.getClassLoader().getResource(path) != null);
        }
    }

    /**
     * The Community tab's banner spear (CONTENT-03) keeps the make of the
     * spear it was and its animated picture, under its own name.
     */
    @Test
    public void theBannerSpearIsAMoriaGoblinSpear() {
        net.minecraft.item.Item spear = ELostTalesItem.COMMUNITY_MORIA_GOBLIN_BANNER_SPEAR.getItem();
        assertEquals("community_moria_goblin_banner_spear",
                registryName(spear.getUnlocalizedName()));
        assertTrue(spear instanceof com.ninuna.losttales.item.weapon.LostTalesItemSpear);
        assertEquals(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_SPEAR.getItem().getMaxDamage(),
                spear.getMaxDamage());
        assertSame(com.ninuna.losttales.creativetab.ELostTalesCreativeTabs.COMMUNITY.getCreativeTab(),
                spear.getCreativeTab());
        assertEquals("Moria Goblin Banner Spear",
                EnglishWords.INSTANCE.format("item.community_moria_goblin_banner_spear.name"));
        assertFalse(EnglishWords.INSTANCE.has("item.test.name"));

        String large = "assets/losttales/textures/items/community/large/"
                + "community_moria_goblin_banner_spear.png";
        ClassLoader loader = ContentNamesTest.class.getClassLoader();
        assertTrue(large, loader.getResource(large) != null);
        assertTrue(large + ".mcmeta", loader.getResource(large + ".mcmeta") != null);
        assertNull(loader.getResource("assets/losttales/textures/items/large/test.png"));
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

    /**
     * A block's tooltip names its own kind, the kind of its creative tab:
     * a plushie is a plushie, a vessel a decoration, the cheese wheel food.
     */
    @Test
    public void eachBlockNamesItsOwnKind() {
        assertSame(ELostTalesItem.Type.BLOCK_BUILDING,
                ELostTalesBlock.typeOf(ELostTalesBlock.TILE_CERAMIC.getBlock()));
        assertSame(ELostTalesItem.Type.BLOCK_PLUSHIE,
                ELostTalesBlock.typeOf(ELostTalesBlock.PLUSHIE_FOX.getBlock()));
        assertSame(ELostTalesItem.Type.BLOCK_DECORATION,
                ELostTalesBlock.typeOf(ELostTalesBlock.URN_AMPHORA.getBlock()));
        assertSame(ELostTalesItem.Type.CONSUMABLE_FOOD,
                ELostTalesBlock.typeOf(ELostTalesBlock.CHEESE_WHEEL.getBlock()));
        assertNull("no block, no kind", ELostTalesBlock.typeOf(null));
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            assertTrue(block.name(), EnglishWords.INSTANCE.has(block.getType().getNameKey()));
        }
    }

    /**
     * Lore comes in two lines or not at all: an item with none shows no
     * Shift hint, and one with half would show a single line.
     */
    @Test
    public void loreHasBothLinesOrNone() {
        List<String> names = new ArrayList<String>();
        for (ELostTalesItem item : ELostTalesItem.values()) {
            names.add(item.getItem().getUnlocalizedName());
        }
        for (ELostTalesBlock block : ELostTalesBlock.values()) {
            names.add(block.getBlock().getUnlocalizedName());
        }
        for (String name : names) {
            assertEquals(name, EnglishWords.INSTANCE.has(name + ".lore.line1"),
                    EnglishWords.INSTANCE.has(name + ".lore.line2"));
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

    /**
     * The mod's assets in the source tree, under the working directory as
     * Gradle runs tests, or under the nearest folder above it that has them.
     */
    private static java.io.File assetsRoot() throws java.io.IOException {
        java.io.File directory = new java.io.File(System.getProperty("user.dir"))
                .getAbsoluteFile();
        while (directory != null) {
            java.io.File candidate = new java.io.File(directory,
                    "src/main/resources/assets/losttales");
            if (candidate.isDirectory()) {
                return candidate;
            }
            directory = directory.getParentFile();
        }
        throw new java.io.IOException("No src/main/resources/assets/losttales at or above "
                + "the working directory; run this test from the repository");
    }

    /** The registry name an item or block registers under. */
    private static String registryName(String unlocalizedName) {
        return unlocalizedName.substring(unlocalizedName.indexOf('.') + 1);
    }
}
