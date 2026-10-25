package com.ninuna.losttales.accessory;

import java.util.Locale;

/** The accessory slots, named in the accessory files by their lower-case names. */
public enum AccessorySlotType {
    RING;

    /** The name a file writes: {@code ring}. */
    public String getName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** The slot a file names; null for none. */
    public static AccessorySlotType byName(String name) {
        for (AccessorySlotType slot : values()) {
            if (slot.getName().equals(name)) {
                return slot;
            }
        }
        return null;
    }
}
