package com.ninuna.losttales.block.custom;

import com.ninuna.losttales.block.ELostTalesBlock;
import net.minecraft.block.Block;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Either half drops the waystone only when broken with a stone pickaxe or better (WAY-19). */
public final class LostTalesBlockWaystoneDropTest {

    @Test
    public void bothHalvesAskAStonePickaxe() {
        Block waystone = ELostTalesBlock.WAYSTONE.getBlock();
        for (int metadata : new int[] {0, 1, 2, 3,
                LostTalesBlockWaystone.UPPER_METADATA}) {
            assertEquals("pickaxe", waystone.getHarvestTool(metadata));
            assertEquals("stone and better", 1,
                    waystone.getHarvestLevel(metadata));
        }
    }

    @Test
    public void itDropsOnlyWhenTheToolHarvestsIt() {
        assertTrue(LostTalesBlockWaystone.drops(false, true, true));
        assertFalse("by hand or with a wooden pickaxe",
                LostTalesBlockWaystone.drops(false, true, false));
        assertFalse("in creative nothing drops",
                LostTalesBlockWaystone.drops(true, true, true));
        assertFalse("a breaking that harvests nothing drops nothing",
                LostTalesBlockWaystone.drops(false, false, true));
    }
}
