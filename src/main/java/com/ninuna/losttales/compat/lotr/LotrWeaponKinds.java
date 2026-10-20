package com.ninuna.losttales.compat.lotr;

import com.ninuna.losttales.progression.skill.WeaponKind;
import cpw.mods.fml.common.registry.GameData;
import java.util.List;
import lotr.common.entity.projectile.LOTREntityCrossbowBolt;
import lotr.common.entity.projectile.LOTREntityDart;
import lotr.common.entity.projectile.LOTREntityPebble;
import lotr.common.entity.projectile.LOTREntitySpear;
import lotr.common.entity.projectile.LOTREntityThrowingAxe;
import lotr.common.item.LOTRItemBattleaxe;
import lotr.common.item.LOTRItemBlowgun;
import lotr.common.item.LOTRItemCrossbow;
import lotr.common.item.LOTRItemDagger;
import lotr.common.item.LOTRItemHammer;
import lotr.common.item.LOTRItemLance;
import lotr.common.item.LOTRItemPolearm;
import lotr.common.item.LOTRItemPolearmLong;
import lotr.common.item.LOTRItemSling;
import lotr.common.item.LOTRItemSpear;
import lotr.common.item.LOTRItemThrowingAxe;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.item.Item;
import net.minecraft.item.ItemAxe;
import net.minecraft.item.ItemBow;
import net.minecraft.item.ItemPickaxe;
import net.minecraft.item.ItemSpade;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemSword;

/**
 * Which kind of weapon or tool an item is (PROG-01), by vanilla's and
 * LOTR's item classes, and which kind threw a projectile. LOTR keeps maces
 * among its hammers and halberds and longspears among its long polearms,
 * so a mace is a hammer and a halberd a pike.
 */
public final class LotrWeaponKinds {
    private LotrWeaponKinds() {}

    /** The item's kind, or null for an item that is no weapon or tool the skills name. */
    public static WeaponKind kindOf(ItemStack stack) {
        Item item = stack == null ? null : stack.getItem();
        if (item == null) {
            return null;
        }
        if (item instanceof LOTRItemDagger) {
            return WeaponKind.DAGGER;
        }
        if (item instanceof LOTRItemBattleaxe) {
            return WeaponKind.BATTLEAXE;
        }
        if (item instanceof LOTRItemHammer) {
            return WeaponKind.HAMMER;
        }
        if (item instanceof LOTRItemSpear) {
            return WeaponKind.SPEAR;
        }
        if (item instanceof LOTRItemLance) {
            return WeaponKind.LANCE;
        }
        if (item instanceof LOTRItemPolearmLong) {
            return WeaponKind.PIKE;
        }
        if (item instanceof LOTRItemPolearm) {
            return WeaponKind.POLEARM;
        }
        if (item instanceof ItemSword) {
            return WeaponKind.SWORD;
        }
        if (item instanceof LOTRItemCrossbow) {
            return WeaponKind.CROSSBOW;
        }
        if (item instanceof ItemBow) {
            return WeaponKind.BOW;
        }
        if (item instanceof LOTRItemThrowingAxe) {
            return WeaponKind.THROWING_AXE;
        }
        if (item instanceof LOTRItemSling) {
            return WeaponKind.SLING;
        }
        if (item instanceof LOTRItemBlowgun) {
            return WeaponKind.BLOWGUN;
        }
        if (item instanceof ItemAxe) {
            return WeaponKind.AXE;
        }
        if (item instanceof ItemPickaxe) {
            return WeaponKind.PICKAXE;
        }
        if (item instanceof ItemSpade) {
            return WeaponKind.SHOVEL;
        }
        return null;
    }

    /** The kind of weapon a projectile was loosed from, or null for one the skills do not name. */
    public static WeaponKind kindOfProjectile(Entity projectile) {
        if (projectile instanceof EntityArrow) {
            return WeaponKind.BOW;
        }
        if (projectile instanceof LOTREntityCrossbowBolt) {
            return WeaponKind.CROSSBOW;
        }
        if (projectile instanceof LOTREntitySpear) {
            return WeaponKind.SPEAR;
        }
        if (projectile instanceof LOTREntityThrowingAxe) {
            return WeaponKind.THROWING_AXE;
        }
        if (projectile instanceof LOTREntityDart) {
            return WeaponKind.BLOWGUN;
        }
        if (projectile instanceof LOTREntityPebble) {
            return WeaponKind.SLING;
        }
        return null;
    }

    /**
     * Whether a list a skill file writes names the item: its kind's word,
     * or its registry name.
     */
    public static boolean names(List<String> weapons, WeaponKind kind, ItemStack stack) {
        if (weapons == null || weapons.isEmpty()) {
            return false;
        }
        if (kind != null && weapons.contains(kind.word())) {
            return true;
        }
        String name = itemName(stack);
        return name != null && weapons.contains(name);
    }

    /** The item's registry name, {@code namespace:path}; null for none. */
    public static String itemName(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        Object name = GameData.getItemRegistry().getNameForObject(stack.getItem());
        return name == null ? null : name.toString();
    }
}
