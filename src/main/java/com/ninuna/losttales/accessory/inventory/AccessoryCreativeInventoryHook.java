package com.ninuna.losttales.accessory.inventory;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;

/** Server-authoritative handling for creative writes to appended slot 45. */
public final class AccessoryCreativeInventoryHook {

    private AccessoryCreativeInventoryHook() {}

    public static boolean handle(EntityPlayerMP player,
                                 C10PacketCreativeInventoryAction packet) {
        if (packet == null || packet.func_149627_c()
                != LostTalesContainerPlayer.ACCESSORY_SLOT_INDEX) {
            return false;
        }
        if (player == null
                || !(player.inventoryContainer
                instanceof LostTalesContainerPlayer)) {
            return true;
        }

        LostTalesContainerPlayer container =
                (LostTalesContainerPlayer)player.inventoryContainer;
        Slot slot = container.getSlot(
                LostTalesContainerPlayer.ACCESSORY_SLOT_INDEX);
        ItemStack requested = packet.func_149625_d();
        boolean valid = player.theItemInWorldManager.isCreative()
                && (requested == null
                || requested.getItem() != null
                && requested.getItemDamage() >= 0
                && requested.stackSize == 1
                && slot.isItemValid(requested));
        if (valid) {
            slot.putStack(requested == null ? null : requested.copy());
            slot.onSlotChanged();
            container.setPlayerIsPresent(player, true);
        }
        container.detectAndSendChanges();
        player.sendContainerToPlayer(container);
        return true;
    }
}
