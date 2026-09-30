package com.ninuna.losttales.quest;

import net.minecraft.item.ItemStack;

/**
 * How a gather objective counts: what the player holds of the items it
 * names, in the main inventory and on the cursor. The count is taken when
 * an item really reaches the inventory (picked up or made), and when the
 * quest starts, reaches a new stage, and when the player logs in,
 * respawns or changes world. It never goes down: what was once held
 * stays counted.
 */
final class GatherCount {
    private GatherCount() {}

    /** Which stacks an objective names. */
    interface Check {
        boolean matches(ItemStack stack);
    }

    /** How many items of the stacks the check names are held, the cursor's included. */
    static int held(ItemStack[] inventory, ItemStack cursor, Check check) {
        long count = 0L;
        if (inventory != null) {
            for (ItemStack stack : inventory) {
                count += countOf(stack, check);
            }
        }
        count += countOf(cursor, check);
        return (int)Math.min(Integer.MAX_VALUE, count);
    }

    /** The count after a look at what is held: never below what it was, never past the target. */
    static int raised(int recorded, int held, int target) {
        return Math.max(recorded, Math.min(held, target));
    }

    private static int countOf(ItemStack stack, Check check) {
        return stack != null && stack.stackSize > 0 && check.matches(stack)
                ? stack.stackSize : 0;
    }
}
