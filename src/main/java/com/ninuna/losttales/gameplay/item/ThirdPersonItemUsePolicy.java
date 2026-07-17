package com.ninuna.losttales.gameplay.item;

import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;

/** Common-side policy for item states which require body-facing alignment. */
public final class ThirdPersonItemUsePolicy {
    private static final Set<Class<? extends Item>> HELD_DIRECTIONAL_ITEMS =
            new CopyOnWriteArraySet<Class<? extends Item>>();
    private static final Set<Class<? extends Item>> ACTIVE_DIRECTIONAL_ITEMS =
            new CopyOnWriteArraySet<Class<? extends Item>>();

    private ThirdPersonItemUsePolicy() {}

    public static boolean shouldFaceAim(ItemStack stack,
                                        boolean usingItem) {
        return isDirectionalWhileHeld(stack)
                || usingItem && isDirectionalWhileUsing(stack);
    }

    public static boolean isDirectionalWhileHeld(ItemStack stack) {
        if (!isUsable(stack)) {
            return false;
        }
        Item item = stack.getItem();
        return item instanceof ItemFishingRod
                || ThirdPersonProjectileItemPolicy.isSupported(stack)
                || matches(item, HELD_DIRECTIONAL_ITEMS);
    }

    public static boolean isDirectionalWhileUsing(ItemStack stack) {
        if (!isUsable(stack)) {
            return false;
        }
        Item item = stack.getItem();
        if (isDirectionalWhileHeld(stack)
                || matches(item, ACTIVE_DIRECTIONAL_ITEMS)) {
            return true;
        }
        EnumAction action = item.getItemUseAction(stack);
        return action == EnumAction.bow
                || action == EnumAction.block
                || action == EnumAction.drink;
    }

    public static void registerHeldDirectionalItem(
            Class<? extends Item> itemType) {
        HELD_DIRECTIONAL_ITEMS.add(requireType(itemType));
    }

    public static void registerActiveDirectionalItem(
            Class<? extends Item> itemType) {
        ACTIVE_DIRECTIONAL_ITEMS.add(requireType(itemType));
    }

    private static boolean isUsable(ItemStack stack) {
        return stack != null && stack.getItem() != null;
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
