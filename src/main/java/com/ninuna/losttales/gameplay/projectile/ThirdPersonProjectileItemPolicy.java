package com.ninuna.losttales.gameplay.projectile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;

/** Common-side whitelist for items which create player-aimed projectiles. */
public final class ThirdPersonProjectileItemPolicy {
    private static final Set<String> LOTR_PROJECTILE_ITEMS =
            new HashSet<String>(Arrays.asList(
                    "LOTRItemBow", "LOTRItemCrossbow",
                    "LOTRItemSpear", "LOTRItemThrowingAxe",
                    "LOTRItemBlowgun", "LOTRItemSling",
                    "LOTRItemFirePot",
                    "LOTRItemPebble", "LOTRItemPlate",
                    "LOTRItemConker"));

    private ThirdPersonProjectileItemPolicy() {}

    public static boolean isSupported(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof ItemBow || item instanceof ItemSnowball
                || item instanceof ItemEgg
                || item instanceof ItemEnderPearl) {
            return true;
        }
        if (item instanceof ItemPotion) {
            return ItemPotion.isSplash(stack.getItemDamage());
        }
        if (item.getItemUseAction(stack) == EnumAction.bow) {
            return true;
        }
        Class<?> type = item.getClass();
        return type.getName().startsWith("lotr.common.item.")
                && LOTR_PROJECTILE_ITEMS.contains(type.getSimpleName());
    }

    public static boolean isActivelyAiming(ItemStack stack,
                                           boolean usingItem) {
        return usingItem && isSupported(stack)
                && (stack.getItem() instanceof ItemBow
                || stack.getItem().getItemUseAction(stack)
                == EnumAction.bow);
    }
}
