package com.ninuna.losttales.accessory.inventory;

import com.ninuna.losttales.accessory.player.AccessoryInventory;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ContainerPlayer;
import net.minecraft.inventory.Slot;

import java.util.List;

/** Small common-side helpers called from supported 1.7.10 bytecode hooks. */
public final class AccessoryContainerHooks {

    private AccessoryContainerHooks() {}

    /**
     * The player's container with the ring slot, built where the player's
     * constructor built vanilla's. Should it fail, the coremod's guard
     * builds vanilla's instead (DATA-08).
     */
    public static ContainerPlayer playerContainer(InventoryPlayer inventory,
                                                  boolean localWorld,
                                                  EntityPlayer player) {
        return new LostTalesContainerPlayer(inventory, localWorld, player);
    }

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
