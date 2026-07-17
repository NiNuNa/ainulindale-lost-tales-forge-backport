package com.ninuna.losttales.client.camera;

import com.ninuna.losttales.config.client.LostTalesThirdPersonConfig;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.packet.LostTalesThirdPersonEntityActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerDestroyItemEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/** Client half of the narrowly transformed melee-attack packet path. */
public final class ThirdPersonEntityActionHooks {
    public static final String ACTIVE_PROPERTY =
            "losttales.thirdPersonEntityActionTransformer.active";

    private ThirdPersonEntityActionHooks() {}

    public static boolean shouldHandleEntityAttack(
            EntityPlayer player, Entity target) {
        return shouldHandleEntityTarget(player, target);
    }

    public static boolean shouldHandleEntityInteraction(
            EntityPlayer player, Entity target) {
        return shouldHandleEntityTarget(player, target);
    }

    private static boolean shouldHandleEntityTarget(
            EntityPlayer player, Entity target) {
        Minecraft minecraft = Minecraft.getMinecraft();
        MovingObjectPosition mouseOver = minecraft == null
                ? null : minecraft.objectMouseOver;
        Vec3 hit = mouseOver == null ? null : mouseOver.hitVec;
        return ThirdPersonCompatibilityPolicy
                .canUseAuthoritativeTargeting(
                LostTalesThirdPersonConfig.enableCameraIntentTargeting,
                Boolean.getBoolean(
                ThirdPersonTargetingHooks.ACTIVE_PROPERTY),
                Boolean.getBoolean(ACTIVE_PROPERTY))
                && minecraft != null && player != null
                && player == minecraft.thePlayer
                && target != null && minecraft.currentScreen == null
                && ThirdPersonCameraRuntime.shouldUseCamera(
                minecraft, minecraft.renderViewEntity)
                && mouseOver.typeOfHit
                == MovingObjectPosition.MovingObjectType.ENTITY
                && mouseOver.entityHit == target
                && hit != null
                && isFinite(hit.xCoord)
                && isFinite(hit.yCoord)
                && isFinite(hit.zCoord);
    }

    public static void handleAttack(
            EntityPlayer player, Entity target) {
        send(target, LostTalesThirdPersonEntityActionPacket.Action.ATTACK,
                false);
        player.attackTargetEntityWithCurrentItem(target);
    }

    public static boolean handleInteraction(
            EntityPlayer player, Entity target) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean handled = player.interactWith(target);
        boolean useItemIfInteractionDeclines = false;
        if (!handled) {
            ItemStack itemStack = player.inventory.getCurrentItem();
            if (itemStack != null) {
                PlayerInteractEvent event =
                        ForgeEventFactory.onPlayerInteract(
                        player,
                        PlayerInteractEvent.Action.RIGHT_CLICK_AIR,
                        0, 0, 0, -1, minecraft.theWorld);
                if (!event.isCanceled()) {
                    useItemIfInteractionDeclines = true;
                    if (useItemLocally(player, itemStack)
                            && minecraft.entityRenderer != null
                            && minecraft.entityRenderer.itemRenderer != null) {
                        minecraft.entityRenderer.itemRenderer
                                .resetEquippedProgress2();
                    }
                }
            }
        }
        send(target,
                LostTalesThirdPersonEntityActionPacket.Action.INTERACT,
                useItemIfInteractionDeclines);
        // The combined request already performed the local fallback. Returning
        // true prevents Minecraft from sending a second, reordered C08 packet.
        return true;
    }

    private static void send(
            Entity target,
            LostTalesThirdPersonEntityActionPacket.Action action,
            boolean useItemIfInteractionDeclines) {
        Vec3 hit = Minecraft.getMinecraft().objectMouseOver.hitVec;
        LostTalesNetworkHandler.CHANNEL.sendToServer(
                new LostTalesThirdPersonEntityActionPacket(
                        action, target.getEntityId(),
                        hit.xCoord, hit.yCoord, hit.zCoord,
                        useItemIfInteractionDeclines));
    }

    private static boolean useItemLocally(
            EntityPlayer player, ItemStack itemStack) {
        int originalSize = itemStack.stackSize;
        ItemStack result = itemStack.useItemRightClick(
                player.worldObj, player);
        if (result == itemStack
                && (result == null || result.stackSize == originalSize)) {
            return false;
        }

        player.inventory.mainInventory[player.inventory.currentItem] = result;
        if (result == null || result.stackSize <= 0) {
            player.inventory.mainInventory[player.inventory.currentItem] = null;
            MinecraftForge.EVENT_BUS.post(new PlayerDestroyItemEvent(
                    player, result == null ? itemStack : result));
        }
        return true;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
