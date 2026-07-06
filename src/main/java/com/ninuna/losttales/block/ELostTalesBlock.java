package com.ninuna.losttales.block;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.creativetab.ELostTalesCreativeTabs;
import com.ninuna.losttales.entity.ELostTalesUser;
import com.ninuna.losttales.item.block.LostTalesItemBlockBase;
import com.ninuna.losttales.item.block.LostTalesItemBlockPlushie;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.EnumRarity;

public enum ELostTalesBlock {
    //  Blocks - Connected.
    TILE_CERAMIC(new LostTalesBlockCeramicTile().setBlockName("tile_ceramic").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_BUILDING.getCreativeTab())),

    //  Blocks - Urns.
    //  The block registry names for amphora/loutrophoros intentionally stay legacy-compatible,
    //  while the enum/API names use the newer urn terminology.
    URN_AMPHORA(new LostTalesBlockUrnBase(ELostTalesUser.SCOSHER).setBlockName("amphora").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab())),
    URN(new LostTalesBlockUrnBase(ELostTalesUser.SCOSHER).setBlockName("urn").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab())),
    URN_LOUTROPHOROS(new LostTalesBlockUrnTall(ELostTalesUser.SCOSHER).setBlockName("loutrophoros").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab())),

    //  Blocks - Statue.
    STATUE_WATCH_STONE(new LostTalesBlockStatueTall(Material.rock, ELostTalesUser.SCOSHER).setBlockName("watch_stone").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab())),

    //  Blocks - Lamp.
    LAMP_TEST(new LostTalesBlockLampTall(ELostTalesUser.CAPTAIN_CHEESE).setBlockName("test_lamp").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab())),

    //  Blocks - Food.
    CHEESE_WHEEL(new LostTalesBlockCheeseWheel().setBlockName("cheese_wheel").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab())),

    //  Blocks - Plushie.
    PLUSHIE_BEAR(new LostTalesBlockPlushie(EnumRarity.common, ELostTalesUser.NINUNA).setBlockName("plushie_bear").setCreativeTab(ELostTalesCreativeTabs.PLUSHIES.getCreativeTab())),
    PLUSHIE_FOX(new LostTalesBlockPlushie(EnumRarity.common, ELostTalesUser.NINUNA).setBlockName("plushie_fox").setCreativeTab(ELostTalesCreativeTabs.PLUSHIES.getCreativeTab())),
    PLUSHIE_GANDALF(new LostTalesBlockPlushie(EnumRarity.uncommon, ELostTalesUser.NINUNA).setBlockName("plushie_gandalf").setCreativeTab(ELostTalesCreativeTabs.PLUSHIES.getCreativeTab()));

    //  Blocks - Miscellaneous.

    /**
     * Deprecated source-level aliases kept so older internal/add-on code that still
     * references the old amphora/loutrophoros enum names can be migrated gradually.
     *
     * The old ancient urn variants were intentionally removed from the registry.
     */
    @Deprecated public static final ELostTalesBlock AMPHORA = URN_AMPHORA;
    @Deprecated public static final ELostTalesBlock LOUTROPHOROS = URN_LOUTROPHOROS;

    private final Block block;

    ELostTalesBlock(Block block) {
        this.block = block;
    }

    public static void initAndRegisterBlocks() {
        for (ELostTalesBlock b : ELostTalesBlock.values()) {
            b.getBlock().setBlockTextureName(LostTalesMetaData.MOD_ID + ":" + b.getBlock().getUnlocalizedName().substring(5));
            if (b.getBlock() instanceof LostTalesBlockPlushie) {
                GameRegistry.registerBlock(b.getBlock(), LostTalesItemBlockPlushie.class, b.getBlock().getUnlocalizedName().substring(5));
            } else {
                GameRegistry.registerBlock(b.getBlock(), LostTalesItemBlockBase.class, b.getBlock().getUnlocalizedName().substring(5));
            }
        }
    }

    public Block getBlock() {
        return block;
    }
}