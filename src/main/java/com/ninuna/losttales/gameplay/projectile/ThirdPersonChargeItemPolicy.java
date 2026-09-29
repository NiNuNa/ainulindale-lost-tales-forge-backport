package com.ninuna.losttales.gameplay.projectile;

import lotr.common.item.LOTRItemBow;
import lotr.common.item.LOTRItemCrossbow;
import lotr.common.item.LOTRItemSpear;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

/** Common-side policy for post-full-draw charge tiers. */
public final class ThirdPersonChargeItemPolicy {
    private static final int VANILLA_BOW_DRAW_TICKS = 20;

    private ThirdPersonChargeItemPolicy() {}

    public static boolean supportsChargeTiers(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof LOTRItemCrossbow) {
            return false;
        }
        return item instanceof ItemBow
                || item instanceof LOTRItemSpear;
    }

    public static int getFullDrawTicks(ItemStack stack) {
        if (!supportsChargeTiers(stack)) {
            return 0;
        }
        Item item = stack.getItem();
        if (item instanceof LOTRItemSpear) {
            return Math.max(1,
                    ((LOTRItemSpear)item).getMaxDrawTime());
        }
        if (item instanceof LOTRItemBow) {
            return Math.max(1,
                    ((LOTRItemBow)item).getMaxDrawTime());
        }
        return VANILLA_BOW_DRAW_TICKS;
    }
}
