package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.compat.minecraft.PlayerItemUseAccess;
import lotr.common.item.LOTRItemBlowgun;
import lotr.common.item.LOTRItemBow;
import lotr.common.item.LOTRItemCrossbow;
import lotr.common.item.LOTRItemSpear;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemEgg;
import net.minecraft.item.ItemEnderPearl;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemSnowball;
import net.minecraft.item.ItemStack;

/** Resolves launch physics from the exact vanilla or LOTR item behavior. */
public final class ThirdPersonProjectileBallistics {
    private static final double STANDARD_DRAG = 0.99D;
    private static final double ARROW_GRAVITY = 0.05D;
    private static final double THROWABLE_GRAVITY = 0.03D;

    private ThirdPersonProjectileBallistics() {}

    public static ProjectileBallisticsProfile resolve(
            EntityPlayer player, ItemStack stack) {
        if (player == null) {
            return null;
        }
        ItemStack activeStack = PlayerItemUseAccess.getItemInUse(player);
        boolean usingItem = activeStack != null && activeStack == stack;
        int useTicks = usingItem && stack != null
                ? Math.max(0, stack.getMaxItemUseDuration()
                - PlayerItemUseAccess.getItemInUseCount(player)) : 0;
        return resolve(stack, usingItem, useTicks);
    }

    static ProjectileBallisticsProfile resolve(
            ItemStack stack, boolean usingItem, int useTicks) {
        if (stack == null || stack.getItem() == null || useTicks < 0) {
            return null;
        }
        Item item = stack.getItem();
        if (item instanceof LOTRItemCrossbow) {
            if (!LOTRItemCrossbow.isLoaded(stack)) {
                return null;
            }
            return arrow(3.0D * LOTRItemCrossbow
                    .getCrossbowLaunchSpeedFactor(stack));
        }
        if (item instanceof LOTRItemBow) {
            if (!usingItem) {
                return null;
            }
            LOTRItemBow bow = (LOTRItemBow)item;
            double strength = drawStrength(
                    useTicks, bow.getMaxDrawTime(), 0.10D);
            return strength <= 0.0D ? null : arrow(
                    3.0D * strength
                            * LOTRItemBow.getLaunchSpeedFactor(stack));
        }
        if (item instanceof LOTRItemSpear) {
            if (!usingItem) {
                return null;
            }
            double strength = drawStrength(
                    useTicks, ((LOTRItemSpear)item).getMaxDrawTime(),
                    0.10D);
            return strength <= 0.0D ? null : arrow(3.0D * strength);
        }
        if (item instanceof LOTRItemBlowgun) {
            if (!usingItem) {
                return null;
            }
            double strength = drawStrength(
                    useTicks, ((LOTRItemBlowgun)item).getMaxDrawTime(),
                    0.65D);
            return strength <= 0.0D ? null : arrow(
                    3.0D * strength
                            * LOTRItemBlowgun
                            .getBlowgunLaunchSpeedFactor(stack));
        }
        if (item instanceof ItemBow) {
            if (!usingItem) {
                return null;
            }
            double strength = drawStrength(useTicks, 20, 0.10D);
            return strength <= 0.0D ? null : arrow(3.0D * strength);
        }
        if (item instanceof ItemPotion) {
            return ItemPotion.isSplash(stack.getItemDamage())
                    ? throwable(0.5D, 0.05D) : null;
        }
        if (item instanceof ItemSnowball || item instanceof ItemEgg
                || item instanceof ItemEnderPearl) {
            return throwable(1.5D, THROWABLE_GRAVITY);
        }

        String itemClass = item.getClass().getName();
        if (!itemClass.startsWith("lotr.common.item.")) {
            if (usingItem && item.getItemUseAction(stack)
                    == EnumAction.bow) {
                double strength = drawStrength(useTicks, 20, 0.10D);
                return strength <= 0.0D
                        ? null : arrow(3.0D * strength);
            }
            return null;
        }
        String simpleName = item.getClass().getSimpleName();
        if ("LOTRItemThrowingAxe".equals(simpleName)) {
            return arrow(3.0D);
        }
        if ("LOTRItemFirePot".equals(simpleName)) {
            return throwable(1.2D, 0.04D);
        }
        if ("LOTRItemPlate".equals(simpleName)) {
            return throwable(1.5D, 0.02D);
        }
        if ("LOTRItemPebble".equals(simpleName)
                || "LOTRItemSling".equals(simpleName)
                || "LOTRItemConker".equals(simpleName)) {
            return throwable(1.0D, 0.04D);
        }
        return null;
    }

    static double drawStrength(
            int useTicks, int maximumDrawTicks, double minimumRawDraw) {
        if (useTicks < 0 || maximumDrawTicks <= 0
                || minimumRawDraw < 0.0D || minimumRawDraw > 1.0D) {
            throw new IllegalArgumentException("invalid draw parameters");
        }
        double raw = (double)useTicks / maximumDrawTicks;
        if (raw < minimumRawDraw) {
            return 0.0D;
        }
        return Math.min(1.0D, (raw * raw + raw * 2.0D) / 3.0D);
    }

    private static ProjectileBallisticsProfile arrow(double speed) {
        return new ProjectileBallisticsProfile(
                speed, ARROW_GRAVITY, STANDARD_DRAG);
    }

    private static ProjectileBallisticsProfile throwable(
            double speed, double gravity) {
        return new ProjectileBallisticsProfile(
                speed, gravity, STANDARD_DRAG);
    }
}
