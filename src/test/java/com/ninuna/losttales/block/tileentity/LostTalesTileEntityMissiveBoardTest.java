package com.ninuna.losttales.block.tileentity;

import net.minecraft.inventory.IInventory;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

/** Only the board's own page moves its notices. */
public final class LostTalesTileEntityMissiveBoardTest {

    /**
     * Hoppers, pipes, quick loot and other mods reach any block entity
     * that is an inventory, a sided one included, and would take notices
     * down or put unsealed letters up past the board's checks.
     */
    @Test
    public void theBoardIsNoInventory() {
        assertFalse(IInventory.class.isAssignableFrom(
                LostTalesTileEntityMissiveBoard.class));
    }
}
