package com.ninuna.losttales.accessory.inventory;

import com.ninuna.losttales.util.LostTalesLog;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C10PacketCreativeInventoryAction;

/**
 * Server-authoritative handling for creative writes to appended slot 45,
 * asked by the coremod before vanilla handles the packet. It never fails
 * the packet: a failure is logged once and hands the packet back to
 * vanilla, which leaves slot 45 alone.
 */
public final class AccessoryCreativeInventoryHook {
    private static volatile boolean failureLogged;

    private AccessoryCreativeInventoryHook() {}

    /** Whether the packet was handled here; false hands it to vanilla. */
    public static boolean handle(EntityPlayerMP player,
                                 C10PacketCreativeInventoryAction packet) {
        try {
            return handled(player, packet);
        } catch (RuntimeException failure) {
            logOnce(failure);
        } catch (LinkageError incompatible) {
            logOnce(incompatible);
        }
        return false;
    }

    private static boolean handled(EntityPlayerMP player,
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

    private static void logOnce(Throwable failure) {
        if (failureLogged) {
            return;
        }
        failureLogged = true;
        LostTalesLog.warning("A creative write to the ring slot failed; vanilla handled it: %s",
                failure.toString());
    }
}
