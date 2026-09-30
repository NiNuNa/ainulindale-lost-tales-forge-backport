package com.ninuna.losttales.quest;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * How a gather objective counts: what is held, the main inventory and
 * the cursor together, taken when an item really arrives, never going
 * down and never past the target.
 */
public final class GatherCountTest {
    private final Item wheat = new Item();
    private final Item stone = new Item();

    private final GatherCount.Check isWheat = new GatherCount.Check() {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.getItem() == GatherCountTest.this.wheat;
        }
    };

    @Test
    public void whatIsHeldCountsTheCursorToo() {
        ItemStack[] inventory = new ItemStack[36];
        inventory[0] = new ItemStack(this.wheat, 5);
        inventory[4] = new ItemStack(this.stone, 64);
        inventory[9] = new ItemStack(this.wheat, 3);
        assertEquals(8, GatherCount.held(inventory, null, this.isWheat));
        assertEquals("a craft lands on the cursor first", 12,
                GatherCount.held(inventory, new ItemStack(this.wheat, 4),
                        this.isWheat));
        assertEquals(0, GatherCount.held(null, null, this.isWheat));
    }

    @Test
    public void anEmptiedStackCountsNothing() {
        ItemStack[] inventory = new ItemStack[] {new ItemStack(this.wheat, 0)};
        assertEquals(0, GatherCount.held(inventory, null, this.isWheat));
    }

    /**
     * An item lying on the ground is not held, however often the player
     * touches it: only what reached the inventory counts, so standing on
     * a stack that does not fit adds nothing.
     */
    @Test
    public void onlyWhatArrivedCounts() {
        ItemStack[] full = new ItemStack[] {new ItemStack(this.stone, 64)};
        int recorded = 0;
        for (int touch = 0; touch < 20; touch++) {
            recorded = GatherCount.raised(recorded,
                    GatherCount.held(full, null, this.isWheat), 10);
        }
        assertEquals(0, recorded);
    }

    @Test
    public void aCountNeverGoesDownNorPastItsTarget() {
        assertEquals(6, GatherCount.raised(6, 2, 10));
        assertEquals(9, GatherCount.raised(6, 9, 10));
        assertEquals(10, GatherCount.raised(6, 30, 10));
    }
}
