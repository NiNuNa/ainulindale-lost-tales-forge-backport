package com.ninuna.losttales.armour;

import java.util.Locale;

/**
 * The four armour slots, as a named set's file names them (CONTENT-07).
 * Each knows where the game keeps it: its index in a player's armour
 * inventory (boots first) and the armour type an armour item is made for
 * (helmet first).
 */
public enum ArmourSlot {
    HELMET(3, 0),
    CHESTPLATE(2, 1),
    LEGGINGS(1, 2),
    BOOTS(0, 3);

    private final int inventoryIndex;
    private final int armourType;

    ArmourSlot(int inventoryIndex, int armourType) {
        this.inventoryIndex = inventoryIndex;
        this.armourType = armourType;
    }

    /** The slot's index in {@code InventoryPlayer.armorInventory}. */
    public int getInventoryIndex() {
        return this.inventoryIndex;
    }

    /** The {@code ItemArmor.armorType} of a piece made for the slot. */
    public int getArmourType() {
        return this.armourType;
    }

    /** Whether a heavy piece here slows as a helmet or boots do, rather than as a chestplate. */
    public boolean isSmall() {
        return this == HELMET || this == BOOTS;
    }

    public String word() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The slot a file's word names; null for a word no slot has. */
    public static ArmourSlot fromWord(String word) {
        if (word == null) {
            return null;
        }
        for (ArmourSlot slot : values()) {
            if (slot.word().equals(word)) {
                return slot;
            }
        }
        return null;
    }

    /** The slot at an armour inventory index; null outside 0 to 3. */
    public static ArmourSlot ofInventoryIndex(int index) {
        for (ArmourSlot slot : values()) {
            if (slot.inventoryIndex == index) {
                return slot;
            }
        }
        return null;
    }

    /** The slot an armour type is made for; null outside 0 to 3. */
    public static ArmourSlot ofArmourType(int type) {
        for (ArmourSlot slot : values()) {
            if (slot.armourType == type) {
                return slot;
            }
        }
        return null;
    }
}
