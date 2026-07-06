package com.ninuna.losttales.creativetab;

import com.ninuna.losttales.block.ELostTalesBlock;
import com.ninuna.losttales.item.ELostTalesItem;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

public enum ELostTalesCreativeTabs {
    BLOCKS_BUILDING(new CreativeTabs("creativeTabBlocksBuilding") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesBlock.TILE_CERAMIC.getBlock()).getItem();
        }
    }),

    BLOCKS_DECORATION(new CreativeTabs("creativeTabBlocksDecoration") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesBlock.URN_AMPHORA.getBlock()).getItem();
        }
    }),

    PLUSHIES(new CreativeTabs("creativeTabPlushies") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesBlock.PLUSHIE_BEAR.getBlock()).getItem();
        }
    }),

    FOOD(new CreativeTabs("creativeTabFood") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesItem.PEAR.getItem()).getItem();
        }
    }),

    COMBAT(new CreativeTabs("creativeTabCombat") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesItem.ARNORIAN_HELMET_HEAVY_2.getItem()).getItem();
        }
    }),

    STORY(new CreativeTabs("creativeTabStory") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesItem.DAINS_HAMMER.getItem()).getItem();
        }
    }),

    MISC(new CreativeTabs("creativeTabMisc") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesItem.OROCARNI_DAGGER.getItem()).getItem();
        }
    }),

    COMMUNITY(new CreativeTabs("creativeTabCommunity") {
        @Override
        public Item getTabIconItem() {
            return new ItemStack(ELostTalesItem.COMMUNITY_MORIA_GOBLIN_SCIMITAR.getItem()).getItem();
        }
    });

    private final CreativeTabs creativeTab;

    ELostTalesCreativeTabs(CreativeTabs creativeTab) {
        this.creativeTab = creativeTab;
    }

    public CreativeTabs getCreativeTab() {
        return creativeTab;
    }
}