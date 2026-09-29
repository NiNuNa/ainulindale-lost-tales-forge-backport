package com.ninuna.losttales.gameplay.item;

import com.ninuna.losttales.gameplay.projectile.ThirdPersonProjectileItemPolicy;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemFishingRod;
import net.minecraft.item.ItemStack;

/** Common-side policy for item states which require body-facing alignment. */
public final class ThirdPersonItemUsePolicy {
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
                || ThirdPersonProjectileItemPolicy.isSupported(stack);
    }

    public static boolean isDirectionalWhileUsing(ItemStack stack) {
        if (!isUsable(stack)) {
            return false;
        }
        if (isDirectionalWhileHeld(stack)) {
            return true;
        }
        EnumAction action = stack.getItem().getItemUseAction(stack);
        return action == EnumAction.bow
                || action == EnumAction.block
                || action == EnumAction.drink;
    }

    private static boolean isUsable(ItemStack stack) {
        return stack != null && stack.getItem() != null;
    }
}
