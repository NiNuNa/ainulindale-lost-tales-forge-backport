package com.ninuna.losttales.compat.lotr;

import java.util.Random;
import lotr.common.LOTRConfig;
import lotr.common.LOTRLevelData;
import lotr.common.LOTRMod;
import lotr.common.LOTRPlayerData;
import lotr.common.LOTRReflection;
import lotr.common.enchant.LOTREnchantment;
import lotr.common.enchant.LOTREnchantmentHelper;
import lotr.common.entity.item.LOTREntityArrowPoisoned;
import lotr.common.entity.npc.LOTREntityNPCRideable;
import lotr.common.entity.projectile.LOTREntityProjectileBase;
import lotr.common.item.LOTRItemBow;
import lotr.common.item.LOTRItemCoin;
import lotr.common.item.LOTRItemCrossbow;
import lotr.common.item.LOTRItemMug;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.init.Items;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.world.World;

/**
 * What the perks ask of LOTR through its public API (PROG-06): a bow's
 * draw time, the item an arrow came from, a mug, LOTR's friendly fire,
 * its item modifiers for forged pieces, its fast-travel clock, a mount's
 * temper and a horse's jump.
 */
public final class LotrPerkHooks {
    /** What a bow takes to draw in vanilla. */
    private static final int VANILLA_DRAW_TICKS = 20;
    /** LOTR's mark that it rolled an item's random modifiers. */
    private static final String RANDOM_ROLLED = "LOTRRandomEnch";

    private LotrPerkHooks() {}

    /** The ticks a bow or crossbow takes to draw or load fully. */
    public static int drawTicks(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof LOTRItemBow) {
            return ((LOTRItemBow) stack.getItem()).getMaxDrawTime();
        }
        if (stack != null && stack.getItem() instanceof LOTRItemCrossbow) {
            return ((LOTRItemCrossbow) stack.getItem()).getMaxDrawTime();
        }
        return VANILLA_DRAW_TICKS;
    }

    /** The item an arrow is picked up as: LOTR's poisoned arrow, or a plain one. */
    public static ItemStack arrowItem(EntityArrow arrow) {
        return new ItemStack(arrow instanceof LOTREntityArrowPoisoned
                ? LOTRMod.arrowPoisoned : Items.arrow);
    }

    /** Whether the item is one of LOTR's drinks. */
    public static boolean isMug(ItemStack stack) {
        return stack != null && stack.getItem() instanceof LOTRItemMug;
    }

    /** Whether LOTR lets the player strike the target: its friendly fire, fellowships and hired units. */
    public static boolean mayStrike(EntityPlayer player, EntityLivingBase target) {
        return LOTRMod.canPlayerAttackEntity(player, target, false);
    }

    /** Whether LOTR's item modifiers are on in this world. */
    public static boolean modifiersOn(World world) {
        return LOTRConfig.isLOTREnchantingEnabled(world);
    }

    /** Whether LOTR has not rolled this item's random modifiers yet: a piece just made. */
    public static boolean isUnrolled(ItemStack stack) {
        return stack != null && (stack.getTagCompound() == null
                || !stack.getTagCompound().getBoolean(RANDOM_ROLLED));
    }

    /**
     * Gives a piece just forged LOTR's random modifiers now, as LOTR would
     * once it lands in an inventory, then {@code durable} (Tempered) and,
     * for {@code fine}, one more good modifier (Masterwork): Strong for a
     * weapon, Protection for armour, Tool Speed for a tool. Answers
     * whether the piece came out fine.
     */
    public static boolean forge(ItemStack stack, Random random, boolean durable, boolean fine) {
        if (stack == null || !LOTREnchantmentHelper.isReforgeable(stack)) {
            return false;
        }
        if (isUnrolled(stack)) {
            LOTREnchantmentHelper.applyRandomEnchantments(stack, random, false, false);
        }
        if (durable) {
            add(stack, LOTREnchantment.durable1);
        }
        if (fine) {
            LOTREnchantment good = stack.getItem() instanceof ItemArmor ? LOTREnchantment.protect1
                    : stack.getItem() instanceof ItemTool ? LOTREnchantment.toolSpeed1
                    : LOTREnchantment.strong1;
            return add(stack, good);
        }
        return false;
    }

    private static boolean add(ItemStack stack, LOTREnchantment modifier) {
        if (LOTREnchantmentHelper.hasEnchant(stack, modifier)
                || !modifier.canApply(stack, false)
                || !LOTREnchantmentHelper.checkEnchantCompatible(stack, modifier)) {
            return false;
        }
        LOTREnchantmentHelper.setHasEnchant(stack, modifier);
        return true;
    }

    /** Moves the played character's fast-travel clock on, so its wait ends sooner. */
    public static void hastenFastTravel(EntityPlayer player, int ticks) {
        LOTRPlayerData data = LOTRLevelData.getData(player);
        if (data != null && ticks > 0) {
            data.setTimeSinceFT(data.getTimeSinceFT() + ticks);
        }
    }

    /** Calms a mount being tamed: a horse's temper, or a LOTR beast's. */
    public static void calm(Entity mount, int amount) {
        if (mount instanceof EntityHorse && !((EntityHorse) mount).isTame()) {
            ((EntityHorse) mount).increaseTemper(amount);
        } else if (mount instanceof LOTREntityNPCRideable
                && !((LOTREntityNPCRideable) mount).isNPCTamed()) {
            ((LOTREntityNPCRideable) mount).increaseNPCTemper(amount);
        }
    }

    /** Who threw a LOTR projectile; null for anything else. */
    public static Entity throwerOf(Entity projectile) {
        return projectile instanceof LOTREntityProjectileBase
                ? ((LOTREntityProjectileBase) projectile).shootingEntity : null;
    }

    /** Puts the item a LOTR projectile carries back as {@code stack}. */
    public static void setProjectileItem(Entity projectile, ItemStack stack) {
        if (projectile instanceof LOTREntityProjectileBase) {
            ((LOTREntityProjectileBase) projectile).setProjectileItem(stack);
        }
    }

    /** The worth of the LOTR coins the player carries. */
    public static int coinsCarried(EntityPlayer player) {
        return LOTRItemCoin.getInventoryValue(player, false);
    }

    /** Takes coins worth {@code value} from the player, as LOTR's traders take them. */
    public static void takeCoins(EntityPlayer player, int value) {
        if (value > 0) {
            LOTRItemCoin.takeCoins(value, player);
        }
    }

    /** A horse's jump strength, as LOTR reaches it. */
    public static IAttribute horseJump() {
        return LOTRReflection.getHorseJumpStrength();
    }
}
