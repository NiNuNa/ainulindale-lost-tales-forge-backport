package com.ninuna.losttales.compat.lotr;

import lotr.common.LOTRMod;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * Runtime references to the four armor pieces used by LOTR half-troll NPCs.
 * No LOTR assets or implementation code are copied into Lost Tales.
 */
public final class LotrHalfTrollArmorAdapter {

    public static final int SLOT_BOOTS = 1;
    public static final int SLOT_LEGGINGS = 2;
    public static final int SLOT_CHESTPLATE = 3;
    public static final int SLOT_HELMET = 4;

    private LotrHalfTrollArmorAdapter() {}

    /** Returns true for an empty slot or the matching half-troll armor piece. */
    public static boolean isAllowedInEquipmentSlot(
            ItemStack stack, int equipmentSlot) {
        if (stack == null) {
            return true;
        }
        Item requiredItem = getRequiredItem(equipmentSlot);
        return requiredItem != null && stack.getItem() == requiredItem;
    }

    public static boolean isHalfTrollArmor(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Item item = stack.getItem();
        return item == LOTRMod.bootsHalfTroll
                || item == LOTRMod.legsHalfTroll
                || item == LOTRMod.bodyHalfTroll
                || item == LOTRMod.helmetHalfTroll;
    }

    public static Item getRequiredItem(int equipmentSlot) {
        switch (equipmentSlot) {
            case SLOT_BOOTS:
                return LOTRMod.bootsHalfTroll;
            case SLOT_LEGGINGS:
                return LOTRMod.legsHalfTroll;
            case SLOT_CHESTPLATE:
                return LOTRMod.bodyHalfTroll;
            case SLOT_HELMET:
                return LOTRMod.helmetHalfTroll;
            default:
                return null;
        }
    }
}
