package com.ninuna.losttales.accessory.inventory;

import com.ninuna.losttales.accessory.player.AccessoryInventory;
import net.minecraft.inventory.Slot;

import java.util.List;

/** Small common-side helpers called from supported 1.7.10 bytecode hooks. */
public final class AccessoryContainerHooks {

    private AccessoryContainerHooks() {}

    /** Keeps creative pick-block's hotbar calculation on vanilla slots 36-44. */
    public static int resolveVanillaInventorySlotCount(List slots) {
        if (slots != null && slots.size()
                == LostTalesContainerPlayer.TOTAL_SLOT_COUNT) {
            Object value = slots.get(
                    LostTalesContainerPlayer.ACCESSORY_SLOT_INDEX);
            if (value instanceof Slot
                    && ((Slot)value).inventory instanceof AccessoryInventory) {
                return LostTalesContainerPlayer.VANILLA_SLOT_COUNT;
            }
        }
        return slots == null ? 0 : slots.size();
    }
}
