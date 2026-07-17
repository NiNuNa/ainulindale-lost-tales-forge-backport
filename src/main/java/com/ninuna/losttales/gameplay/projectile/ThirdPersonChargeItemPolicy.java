package com.ninuna.losttales.gameplay.projectile;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lotr.common.item.LOTRItemBow;
import lotr.common.item.LOTRItemCrossbow;
import lotr.common.item.LOTRItemSpear;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemStack;

/** Common-side opt-in policy for post-full-draw charge tiers. */
public final class ThirdPersonChargeItemPolicy {
    private static final int VANILLA_BOW_DRAW_TICKS = 20;
    private static final Map<Class<? extends Item>, Integer>
            REGISTERED_FULL_DRAW_TICKS =
            new ConcurrentHashMap<Class<? extends Item>, Integer>();

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
                || item instanceof LOTRItemSpear
                || registeredDrawTicks(item) > 0;
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
        if (item instanceof ItemBow) {
            return VANILLA_BOW_DRAW_TICKS;
        }
        return registeredDrawTicks(item);
    }

    public static void registerChargeTierItem(
            Class<? extends Item> itemType, int fullDrawTicks) {
        if (itemType == null) {
            throw new IllegalArgumentException("itemType");
        }
        if (fullDrawTicks <= 0 || fullDrawTicks > 1200) {
            throw new IllegalArgumentException(
                    "fullDrawTicks must be in [1, 1200]");
        }
        REGISTERED_FULL_DRAW_TICKS.put(
                itemType, Integer.valueOf(fullDrawTicks));
    }

    private static int registeredDrawTicks(Item item) {
        for (Map.Entry<Class<? extends Item>, Integer> entry
                : REGISTERED_FULL_DRAW_TICKS.entrySet()) {
            if (entry.getKey().isInstance(item)) {
                return entry.getValue().intValue();
            }
        }
        return 0;
    }
}
