package com.ninuna.losttales.accessory;

import net.minecraft.item.ItemStack;

/** Matches the item, metadata, and optional NBT accepted by a definition. */
public interface AccessoryStackMatcher {
    boolean matches(ItemStack stack);
}
