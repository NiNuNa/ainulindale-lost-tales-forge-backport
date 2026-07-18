package com.ninuna.losttales.item.block;

import com.ninuna.losttales.block.custom.LostTalesBlockPlushie;
import com.ninuna.losttales.entity.ELostTalesUser;
import net.minecraft.block.Block;
import net.minecraft.item.EnumRarity;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class LostTalesItemBlockPlushieTest {

    @Test
    public void authoredInventoryIconsUseTheItemAtlas() {
        assertEquals(1, createItem("plushie_bear").getSpriteNumber());
        assertEquals(1, createItem("plushie_fox").getSpriteNumber());
    }

    @Test
    public void plushiesWithoutAuthoredItemIconsKeepTheBlockAtlas() {
        assertEquals(0, createItem("plushie_gandalf").getSpriteNumber());
    }

    private static LostTalesItemBlockPlushie createItem(String blockName) {
        Block block = new LostTalesBlockPlushie(
                EnumRarity.common, ELostTalesUser.NULL)
                .setBlockName(blockName);
        return new LostTalesItemBlockPlushie(block);
    }
}
