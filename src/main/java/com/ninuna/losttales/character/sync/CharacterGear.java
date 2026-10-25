package com.ninuna.losttales.character.sync;

import net.minecraft.item.ItemStack;

/**
 * What a character wears and holds in its saved life: its four armour
 * stacks, boots first as {@code InventoryPlayer.armorInventory} holds
 * them, and the stack in its hand (CHAR-42). A slot may be empty. The
 * stage dresses the player in it for drawing only.
 *
 * <p>The stacks are copies taken as it is made. They are handed out as
 * they are kept, for the renderer to draw every frame without copying,
 * and nobody changes them.</p>
 */
public final class CharacterGear {
    /** The armour slots, boots, legs, chest and helmet, then the hand. */
    public static final int SLOTS = 5;
    /** How many of the slots are armour. */
    public static final int ARMOUR_SLOTS = 4;
    /** The hand's slot, after the armour. */
    public static final int HELD = 4;
    /** Nothing at all: no armour and an empty hand. */
    public static final CharacterGear NONE = new CharacterGear(new ItemStack[SLOTS]);

    private final ItemStack[] stacks;

    private CharacterGear(ItemStack[] stacks) {
        this.stacks = stacks;
    }

    /**
     * The gear of {@code stacks}, one for each slot in order, copied; an
     * array of any other length is refused.
     */
    public static CharacterGear of(ItemStack[] stacks) {
        if (stacks == null || stacks.length != SLOTS) {
            throw new IllegalArgumentException("gear needs one stack for each slot");
        }
        ItemStack[] copies = new ItemStack[SLOTS];
        boolean empty = true;
        for (int slot = 0; slot < SLOTS; slot++) {
            copies[slot] = stacks[slot] == null ? null : stacks[slot].copy();
            empty &= copies[slot] == null;
        }
        return empty ? NONE : new CharacterGear(copies);
    }

    /** The stack in {@code slot}, as kept; null for an empty slot. */
    public ItemStack get(int slot) {
        return slot < 0 || slot >= SLOTS ? null : this.stacks[slot];
    }

    /** Whether every slot is empty. */
    public boolean isEmpty() {
        for (ItemStack stack : this.stacks) {
            if (stack != null) {
                return false;
            }
        }
        return true;
    }
}
