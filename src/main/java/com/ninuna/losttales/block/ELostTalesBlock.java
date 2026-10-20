package com.ninuna.losttales.block;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.block.custom.LostTalesBlockCeramicTile;
import com.ninuna.losttales.block.custom.LostTalesBlockCheeseWheel;
import com.ninuna.losttales.block.custom.LostTalesBlockLampTall;
import com.ninuna.losttales.block.custom.LostTalesBlockMissiveBoard;
import com.ninuna.losttales.block.custom.LostTalesBlockPlushie;
import com.ninuna.losttales.block.custom.LostTalesBlockStatueTall;
import com.ninuna.losttales.block.custom.LostTalesBlockUrnBase;
import com.ninuna.losttales.block.custom.LostTalesBlockUrnTall;
import com.ninuna.losttales.block.custom.LostTalesBlockWaystone;
import com.ninuna.losttales.creativetab.ELostTalesCreativeTabs;
import com.ninuna.losttales.user.ELostTalesUser;
import com.ninuna.losttales.item.ELostTalesItem;
import com.ninuna.losttales.item.block.LostTalesItemBlockBase;
import com.ninuna.losttales.item.block.LostTalesItemBlockPlushie;
import com.ninuna.losttales.item.block.LostTalesItemBlockUrn;
import com.ninuna.losttales.block.custom.LostTalesBlockResetStatue;
import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.EnumRarity;

/**
 * The blocks, each with the kind its Ctrl tooltip names: the kind of the
 * creative tab it stands in.
 */
public enum ELostTalesBlock {
    //  Blocks - Connected.
    TILE_CERAMIC(new LostTalesBlockCeramicTile().setBlockName("tile_ceramic").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_BUILDING.getCreativeTab()), ELostTalesItem.Type.BLOCK_BUILDING),

    //  Blocks - Vessels. Each registers under its vessel's own name.
    URN_AMPHORA(new LostTalesBlockUrnBase(ELostTalesUser.SCOSHER).setBlockName("amphora").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),
    URN(new LostTalesBlockUrnBase(ELostTalesUser.SCOSHER).setBlockName("urn").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),
    URN_LOUTROPHOROS(new LostTalesBlockUrnTall(ELostTalesUser.SCOSHER).setBlockName("loutrophoros").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),

    //  Blocks - Statue.
    STATUE_WATCH_STONE(new LostTalesBlockStatueTall(Material.rock, ELostTalesUser.SCOSHER).setBlockName("watch_stone").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),
    WAYSTONE(new LostTalesBlockWaystone().setBlockName("waystone").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),

    //  Blocks - Lamp.
    LAMP_TALL(new LostTalesBlockLampTall(ELostTalesUser.CAPTAIN_CHEESE).setBlockName("tall_lamp").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),

    //  Blocks - Food.
    CHEESE_WHEEL(new LostTalesBlockCheeseWheel().setBlockName("cheese_wheel").setCreativeTab(ELostTalesCreativeTabs.FOOD.getCreativeTab()), ELostTalesItem.Type.CONSUMABLE_FOOD),

    //  Blocks - Plushie.
    PLUSHIE_BEAR(new LostTalesBlockPlushie(EnumRarity.uncommon, ELostTalesUser.NINUNA).setBlockName("plushie_bear").setCreativeTab(ELostTalesCreativeTabs.PLUSHIES.getCreativeTab()), ELostTalesItem.Type.BLOCK_PLUSHIE),
    PLUSHIE_FOX(new LostTalesBlockPlushie(EnumRarity.uncommon, ELostTalesUser.NINUNA).setBlockName("plushie_fox").setCreativeTab(ELostTalesCreativeTabs.PLUSHIES.getCreativeTab()), ELostTalesItem.Type.BLOCK_PLUSHIE),
    PLUSHIE_GANDALF(new LostTalesBlockPlushie(EnumRarity.rare, ELostTalesUser.NINUNA).setBlockName("plushie_gandalf").setCreativeTab(ELostTalesCreativeTabs.PLUSHIES.getCreativeTab()), ELostTalesItem.Type.BLOCK_PLUSHIE),

    //  Blocks - Miscellaneous.
    MISSIVE_BOARD(new LostTalesBlockMissiveBoard().setBlockName("missive_board").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION),
    // Placed by operators alone; never crafted, never generated (PROG-12).
    RESET_STATUE(new LostTalesBlockResetStatue().setBlockName("reset_statue").setCreativeTab(ELostTalesCreativeTabs.BLOCKS_DECORATION.getCreativeTab()), ELostTalesItem.Type.BLOCK_DECORATION);

    private final Block block;
    private final ELostTalesItem.Type type;

    ELostTalesBlock(Block block, ELostTalesItem.Type type) {
        this.block = block;
        this.type = type;
    }

    public static void initAndRegisterBlocks() {
        for (ELostTalesBlock b : ELostTalesBlock.values()) {
            b.getBlock().setBlockTextureName(LostTalesMetaData.MOD_ID + ":" + b.getBlock().getUnlocalizedName().substring(5));
            if (b.getBlock() instanceof LostTalesBlockPlushie) {
                GameRegistry.registerBlock(b.getBlock(), LostTalesItemBlockPlushie.class, b.getBlock().getUnlocalizedName().substring(5));
            } else if (b.getBlock() instanceof LostTalesBlockUrnBase) {
                GameRegistry.registerBlock(b.getBlock(), LostTalesItemBlockUrn.class, b.getBlock().getUnlocalizedName().substring(5));
            } else {
                GameRegistry.registerBlock(b.getBlock(), LostTalesItemBlockBase.class, b.getBlock().getUnlocalizedName().substring(5));
            }
        }
        // The waystone is drawn in the world by its tile-entity renderer; its
        // icon in inventories and its particles use the missive board's texture.
        WAYSTONE.getBlock().setBlockTextureName(
                LostTalesMetaData.MOD_ID + ":missive_board");
        // The reset statue is drawn in the Watch Stone's model until it has
        // its own (PROG-12); its icon and particles stand in the missive
        // board's texture, as the waystone's do.
        RESET_STATUE.getBlock().setBlockTextureName(
                LostTalesMetaData.MOD_ID + ":missive_board");
    }

    public Block getBlock() {
        return block;
    }

    /** The kind the block's tooltip names. */
    public ELostTalesItem.Type getType() {
        return type;
    }

    /** The kind of a Lost Tales block, or null for any other block. */
    public static ELostTalesItem.Type typeOf(Block block) {
        for (ELostTalesBlock candidate : values()) {
            if (candidate.block == block) {
                return candidate.type;
            }
        }
        return null;
    }
}
