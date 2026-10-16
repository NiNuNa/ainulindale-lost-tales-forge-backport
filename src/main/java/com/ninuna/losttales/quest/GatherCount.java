package com.ninuna.losttales.quest;

import net.minecraft.item.ItemStack;

/**
 * How a gather objective counts and hands over: what the player holds of
 * the items it names, in the main inventory and on the cursor. The count
 * is taken when an item really reaches the inventory (picked up or made),
 * and when the quest starts, reaches a new stage, and when the player
 * logs in, respawns or changes world. A gather that keeps what it counted
 * never goes down; one that takes ({@link #takes}) follows what is held,
 * and hands it over as its stage ends, all or nothing. A delivery hands
 * its items over the same way ({@link #take}).
 */
final class GatherCount {
    private GatherCount() {}

    /** Which stacks an objective names. */
    interface Check {
        boolean matches(ItemStack stack);
    }

    /** Whether a gather objective hands over what it counted as its stage ends: a required one whose {@code take} says {@code true}. */
    static boolean takes(LostTalesQuestObjectiveDefinition objective) {
        return objective != null && LostTalesQuestObjectiveType.GATHER.is(objective)
                && !objective.isOptional()
                && "true".equals(LostTalesQuestParams.value(
                        objective.getParams(), LostTalesQuestParams.TAKE));
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

    /**
     * The count after a look at what is held, never past the target: a
     * gather that takes counts what is held now, one that keeps never
     * goes below what it counted.
     */
    static int counted(boolean takes, int recorded, int held, int target) {
        return takes ? Math.min(held, target) : raised(recorded, held, target);
    }

    /** The count after a look at what is held: never below what it was, never past the target. */
    static int raised(int recorded, int held, int target) {
        return Math.max(recorded, Math.min(held, target));
    }

    /**
     * Takes {@code amounts[i]} of the items {@code checks[i]} names, for
     * each objective in turn, from copies of the main inventory first and
     * then of the cursor; the stacks given are never changed. Whole when
     * every objective got all it asked for: only then are the copies put
     * in place of the player's own.
     */
    static Taking take(ItemStack[] inventory, ItemStack cursor, Check[] checks,
                       int[] amounts) {
        ItemStack[] left = new ItemStack[inventory == null ? 0 : inventory.length];
        for (int slot = 0; slot < left.length; slot++) {
            left[slot] = ItemStack.copyItemStack(inventory[slot]);
        }
        ItemStack hand = ItemStack.copyItemStack(cursor);
        int[] got = new int[checks.length];
        boolean whole = true;
        for (int index = 0; index < checks.length; index++) {
            int wanted = Math.max(0, amounts[index]);
            int missing = wanted;
            for (int slot = 0; slot < left.length && missing > 0; slot++) {
                ItemStack stack = left[slot];
                if (countOf(stack, checks[index]) == 0) {
                    continue;
                }
                int taken = Math.min(missing, stack.stackSize);
                stack.stackSize -= taken;
                missing -= taken;
                if (stack.stackSize <= 0) {
                    left[slot] = null;
                }
            }
            if (missing > 0 && countOf(hand, checks[index]) > 0) {
                int taken = Math.min(missing, hand.stackSize);
                hand.stackSize -= taken;
                missing -= taken;
                if (hand.stackSize <= 0) {
                    hand = null;
                }
            }
            got[index] = wanted - missing;
            whole &= missing == 0;
        }
        return new Taking(left, hand, got, whole);
    }

    private static int countOf(ItemStack stack, Check check) {
        return stack != null && stack.stackSize > 0 && check.matches(stack)
                ? stack.stackSize : 0;
    }

    /** What a hand-over came to: the inventory and cursor left after it, and what each objective got. */
    static final class Taking {
        final ItemStack[] inventory;
        final ItemStack cursor;
        final int[] got;
        final boolean whole;

        Taking(ItemStack[] inventory, ItemStack cursor, int[] got,
               boolean whole) {
            this.inventory = inventory;
            this.cursor = cursor;
            this.got = got;
            this.whole = whole;
        }
    }
}
