package com.ninuna.losttales.network.server;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S02PacketChat;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.WorldServer;

/** Server-thread authority boundary for offset-camera block actions. */
public final class LostTalesThirdPersonBlockActionService {
    private static final double REACH_TOLERANCE = 0.25D;
    private static final double RAY_EXTENSION = 0.01D;

    private LostTalesThirdPersonBlockActionService() {}

    public static void execute(
            EntityPlayerMP player, int expectedDimension,
            int x, int y, int z, int side,
            float hitOffsetX, float hitOffsetY, float hitOffsetZ,
            int expectedHotbarSlot, ItemStack expectedHeldItem) {
        if (!hasUsablePlayerContext(player, expectedDimension)
                || !isValidBlockPosition(x, y, z)
                || !isValidSide(side)
                || !areValidOffsets(
                hitOffsetX, hitOffsetY, hitOffsetZ)) {
            return;
        }

        WorldServer world = (WorldServer)player.worldObj;
        Vec3 eye = Vec3.createVectorHelper(
                player.posX,
                player.posY + (double)player.getEyeHeight(),
                player.posZ);
        Vec3 hit = Vec3.createVectorHelper(
                (double)x + hitOffsetX,
                (double)y + hitOffsetY,
                (double)z + hitOffsetZ);
        double reach = player.theItemInWorldManager
                .getBlockReachDistance();
        if (!isWithinReach(eye, hit, reach)) {
            return;
        }

        if (!hasExpectedHeldItem(
                player, expectedHotbarSlot, expectedHeldItem)) {
            sendBlockCorrections(player, world, x, y, z, side);
            sendHeldItemCorrection(player, expectedHotbarSlot);
            return;
        }

        boolean validTarget = rayMatchesBlock(
                world, eye, hit, x, y, z, side);
        int selectedSlotAtExecution = player.inventory.currentItem;
        player.inventory.currentItem = expectedHotbarSlot;
        try {
            player.func_143004_u();
            boolean placeResult = false;
            if (validTarget) {
                MinecraftServer server = player.mcServer;
                if (isAboveBuildLimit(server, y, side)) {
                    sendBuildLimitWarning(player, server.getBuildLimit());
                } else if (!server.isBlockProtected(
                        world, x, y, z, player)) {
                    placeResult = player.theItemInWorldManager
                            .activateBlockOrUseItem(
                                    player, world,
                                    player.inventory.getCurrentItem(),
                                    x, y, z, side,
                                    hitOffsetX, hitOffsetY, hitOffsetZ);
                }
            }

            sendBlockCorrections(player, world, x, y, z, side);
            synchronizeHeldItem(
                    player, expectedHotbarSlot,
                    expectedHeldItem, placeResult);
        } finally {
            if (selectedSlotAtExecution != expectedHotbarSlot
                    && player.inventory.currentItem
                    == expectedHotbarSlot) {
                player.inventory.currentItem = selectedSlotAtExecution;
            }
        }
    }

    private static boolean hasUsablePlayerContext(
            EntityPlayerMP player, int expectedDimension) {
        return player != null && player.worldObj instanceof WorldServer
                && player.mcServer != null
                && player.worldObj.provider != null
                && player.worldObj.provider.dimensionId == expectedDimension
                && player.isEntityAlive() && !player.isDead
                && !player.isPlayerSleeping()
                && player.openContainer == player.inventoryContainer;
    }

    private static boolean hasExpectedHeldItem(
            EntityPlayerMP player, int expectedHotbarSlot,
            ItemStack expectedHeldItem) {
        return expectedHotbarSlot >= 0 && expectedHotbarSlot < 9
                && expectedHotbarSlot
                < player.inventory.mainInventory.length
                && ItemStack.areItemStacksEqual(
                expectedHeldItem,
                player.inventory.mainInventory[expectedHotbarSlot]);
    }

    static boolean isWithinReach(Vec3 eye, Vec3 hit, double reach) {
        if (eye == null || hit == null || !isFinite(reach)
                || reach < 0.0D
                || !isFinite(eye.xCoord) || !isFinite(eye.yCoord)
                || !isFinite(eye.zCoord) || !isFinite(hit.xCoord)
                || !isFinite(hit.yCoord) || !isFinite(hit.zCoord)) {
            return false;
        }
        double permitted = reach + REACH_TOLERANCE;
        return eye.squareDistanceTo(hit) <= permitted * permitted;
    }

    private static boolean rayMatchesBlock(
            WorldServer world, Vec3 eye, Vec3 hit,
            int x, int y, int z, int side) {
        Vec3 inside = extendIntoBlock(hit, side);
        MovingObjectPosition result = world.func_147447_a(
                eye, inside, false, false, true);
        return result != null && result.typeOfHit
                == MovingObjectPosition.MovingObjectType.BLOCK
                && result.blockX == x && result.blockY == y
                && result.blockZ == z && result.sideHit == side;
    }

    static Vec3 extendIntoBlock(Vec3 hit, int side) {
        double x = hit.xCoord;
        double y = hit.yCoord;
        double z = hit.zCoord;
        switch (side) {
            case 0:
                y += RAY_EXTENSION;
                break;
            case 1:
                y -= RAY_EXTENSION;
                break;
            case 2:
                z += RAY_EXTENSION;
                break;
            case 3:
                z -= RAY_EXTENSION;
                break;
            case 4:
                x += RAY_EXTENSION;
                break;
            case 5:
                x -= RAY_EXTENSION;
                break;
            default:
                break;
        }
        return Vec3.createVectorHelper(x, y, z);
    }

    private static boolean isAboveBuildLimit(
            MinecraftServer server, int y, int side) {
        int limit = server.getBuildLimit();
        return y >= limit - 1 && (side == 1 || y >= limit);
    }

    private static void sendBuildLimitWarning(
            EntityPlayerMP player, int buildLimit) {
        ChatComponentTranslation message = new ChatComponentTranslation(
                "build.tooHigh", Integer.valueOf(buildLimit));
        message.getChatStyle().setColor(EnumChatFormatting.RED);
        player.playerNetServerHandler.sendPacket(
                new S02PacketChat(message));
    }

    private static void sendBlockCorrections(
            EntityPlayerMP player, WorldServer world,
            int x, int y, int z, int side) {
        player.playerNetServerHandler.sendPacket(
                new S23PacketBlockChange(x, y, z, world));
        int adjacentX = x;
        int adjacentY = y;
        int adjacentZ = z;
        switch (side) {
            case 0:
                --adjacentY;
                break;
            case 1:
                ++adjacentY;
                break;
            case 2:
                --adjacentZ;
                break;
            case 3:
                ++adjacentZ;
                break;
            case 4:
                --adjacentX;
                break;
            case 5:
                ++adjacentX;
                break;
            default:
                return;
        }
        player.playerNetServerHandler.sendPacket(
                new S23PacketBlockChange(
                        adjacentX, adjacentY, adjacentZ, world));
    }

    private static void synchronizeHeldItem(
            EntityPlayerMP player, int hotbarSlot,
            ItemStack expectedHeldItem, boolean placeResult) {
        ItemStack current = player.inventory.mainInventory[hotbarSlot];
        if (current != null && current.stackSize == 0) {
            player.inventory.mainInventory[hotbarSlot] = null;
            current = null;
        }
        if (current != null && current.getMaxItemUseDuration() != 0) {
            return;
        }

        player.isChangingQuantityOnly = true;
        Slot slot;
        try {
            player.inventory.mainInventory[hotbarSlot] =
                    ItemStack.copyItemStack(current);
            slot = player.openContainer.getSlotFromInventory(
                    player.inventory, hotbarSlot);
            player.openContainer.detectAndSendChanges();
        } finally {
            player.isChangingQuantityOnly = false;
        }
        current = player.inventory.mainInventory[hotbarSlot];
        if (slot != null && (!ItemStack.areItemStacksEqual(
                current, expectedHeldItem) || !placeResult)) {
            player.playerNetServerHandler.sendPacket(
                    new S2FPacketSetSlot(
                            player.openContainer.windowId,
                            slot.slotNumber, current));
        }
    }

    private static void sendHeldItemCorrection(
            EntityPlayerMP player, int hotbarSlot) {
        if (hotbarSlot < 0 || hotbarSlot >= 9
                || hotbarSlot >= player.inventory.mainInventory.length) {
            return;
        }
        Slot slot = player.openContainer.getSlotFromInventory(
                player.inventory, hotbarSlot);
        if (slot != null) {
            player.playerNetServerHandler.sendPacket(
                    new S2FPacketSetSlot(
                            player.openContainer.windowId,
                            slot.slotNumber,
                            player.inventory.mainInventory[hotbarSlot]));
        }
    }

    private static boolean isValidBlockPosition(int x, int y, int z) {
        return y >= 0 && y <= 255
                && x >= -30000000 && x <= 30000000
                && z >= -30000000 && z <= 30000000;
    }

    private static boolean isValidSide(int side) {
        return side >= 0 && side <= 5;
    }

    private static boolean areValidOffsets(
            float x, float y, float z) {
        return isUnitInterval(x) && isUnitInterval(y)
                && isUnitInterval(z);
    }

    private static boolean isUnitInterval(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value)
                && value >= 0.0F && value <= 1.0F;
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }
}
