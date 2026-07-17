package com.ninuna.losttales.gameplay.projectile;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;

/**
 * Common-side projectile capability policy. Projectile simulation, ranged
 * weapon camera state, and charging are intentionally separate capabilities.
 */
public final class ThirdPersonProjectileItemPolicy {
    private static final Set<String> LOTR_PROJECTILE_ITEMS =
            new HashSet<String>(Arrays.asList(
                    "LOTRItemBow", "LOTRItemCrossbow",
                    "LOTRItemSpear", "LOTRItemThrowingAxe",
                    "LOTRItemBlowgun", "LOTRItemSling",
                    "LOTRItemFirePot",
                    "LOTRItemPebble", "LOTRItemPlate",
                    "LOTRItemConker"));
    private static final Set<String> LOTR_RANGED_WEAPONS =
            new HashSet<String>(Arrays.asList(
                    "LOTRItemBow", "LOTRItemCrossbow",
                    "LOTRItemSpear", "LOTRItemThrowingAxe",
                    "LOTRItemBlowgun", "LOTRItemSling"));
    private static final Set<Class<? extends Item>> REGISTERED_PROJECTILES =
            new CopyOnWriteArraySet<Class<? extends Item>>();
    private static final Set<Class<? extends Item>> REGISTERED_RANGED_WEAPONS =
            new CopyOnWriteArraySet<Class<? extends Item>>();
    private static final Set<Class<? extends Item>> REGISTERED_CHARGEABLE =
            new CopyOnWriteArraySet<Class<? extends Item>>();

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
        if (matches(item, REGISTERED_PROJECTILES)
                || matches(item, REGISTERED_RANGED_WEAPONS)
                || matches(item, REGISTERED_CHARGEABLE)) {
            return true;
        }
        Class<?> type = item.getClass();
        return type.getName().startsWith("lotr.common.item.")
                && LOTR_PROJECTILE_ITEMS.contains(type.getSimpleName());
    }

    /** Returns whether the item has a real held-use charge phase. */
    public static boolean isChargeable(ItemStack stack) {
        if (!isSupported(stack)) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof ItemBow
                || item.getItemUseAction(stack) == EnumAction.bow
                || matches(item, REGISTERED_CHARGEABLE);
    }

    /** Returns whether holding the item should select a weapon camera state. */
    public static boolean isRangedWeapon(ItemStack stack) {
        if (!isSupported(stack)) {
            return false;
        }
        Item item = stack.getItem();
        if (isChargeable(stack)
                || matches(item, REGISTERED_RANGED_WEAPONS)) {
            return true;
        }
        Class<?> type = item.getClass();
        return type.getName().startsWith("lotr.common.item.")
                && LOTR_RANGED_WEAPONS.contains(type.getSimpleName());
    }

    public static boolean isActivelyCharging(ItemStack stack,
                                              boolean usingItem) {
        return usingItem && isChargeable(stack);
    }

    /** Kept as the camera-facing name used by existing callers. */
    public static boolean isActivelyAiming(ItemStack stack,
                                           boolean usingItem) {
        return isActivelyCharging(stack, usingItem);
    }

    public static void registerProjectileItem(
            Class<? extends Item> itemType) {
        REGISTERED_PROJECTILES.add(requireType(itemType));
    }

    public static void registerRangedWeaponItem(
            Class<? extends Item> itemType) {
        REGISTERED_RANGED_WEAPONS.add(requireType(itemType));
    }

    public static void registerChargeableItem(
            Class<? extends Item> itemType) {
        REGISTERED_CHARGEABLE.add(requireType(itemType));
    }

    private static boolean matches(
            Item item, Set<Class<? extends Item>> types) {
        for (Class<? extends Item> type : types) {
            if (type.isInstance(item)) {
                return true;
            }
        }
        return false;
    }

    private static Class<? extends Item> requireType(
            Class<? extends Item> itemType) {
        if (itemType == null) {
            throw new IllegalArgumentException("itemType");
        }
        return itemType;
    }
}
