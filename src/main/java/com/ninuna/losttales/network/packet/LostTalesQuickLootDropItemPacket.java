package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.inventory.LostTalesQuickLootInventoryHelper;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

public class LostTalesQuickLootDropItemPacket implements IMessage {
    private int x;
    private int y;
    private int z;
    private int slot;
    private boolean malformed;

    public LostTalesQuickLootDropItemPacket() {}

    public LostTalesQuickLootDropItemPacket(int x, int y, int z, int slot) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            this.slot = buffer.readInt();
            LostTalesPacketCodec.requireFinished(buffer);
            if (!LostTalesPacketCodec.isValidBlockPosition(this.x, this.y, this.z)
                    || !LostTalesPacketCodec.isReasonableInventorySlot(this.slot)) {
                throw new LostTalesPacketCodec.DecodeException("invalid quick-loot drop request");
            }
        } catch (RuntimeException exception) {
            this.malformed = true;
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeInt(this.x);
        buffer.writeInt(this.y);
        buffer.writeInt(this.z);
        buffer.writeInt(this.slot);
    }

    private static void execute(
            EntityPlayerMP player, int expectedDimension, int x, int y, int z, int slot) {
        if (player.worldObj.provider == null
                || player.worldObj.provider.dimensionId != expectedDimension) {
            return;
        }
        LostTalesQuickLootInventoryHelper.InventoryAccess access =
                LostTalesQuickLootInventoryHelper.resolve(player.worldObj, x, y, z);
        if (!LostTalesQuickLootInventoryHelper.isUsableBy(player, access) || access.isSealed()) {
            return;
        }

        IInventory inventory = access.getInventory();
        if (slot < 0 || slot >= inventory.getSizeInventory()) {
            return;
        }

        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack == null || stack.stackSize <= 0) {
            return;
        }

        ItemStack removed = inventory.decrStackSize(slot, stack.stackSize);
        if (removed == null || removed.stackSize <= 0) {
            return;
        }

        inventory.markDirty();
        player.worldObj.markBlockForUpdate(access.getX(), access.getY(), access.getZ());

        EntityItem entityItem = new EntityItem(
                player.worldObj,
                (double) access.getX() + 0.5D,
                (double) access.getY() + 1.0D,
                (double) access.getZ() + 0.5D,
                removed.copy()
        );
        entityItem.delayBeforeCanPickup = 10;
        player.worldObj.spawnEntityInWorld(entityItem);
        player.worldObj.playSoundEffect(
                (double) access.getX() + 0.5D,
                (double) access.getY() + 0.5D,
                (double) access.getZ() + 0.5D,
                "random.pop", 0.5F, 1.0F);

        LostTalesNetworkHandler.CHANNEL.sendTo(
                LostTalesQuickLootContainerSyncPacket.fromInventory(
                        access.getX(), access.getY(), access.getZ(), inventory),
                player
        );
    }

    public static class Handler implements IMessageHandler<LostTalesQuickLootDropItemPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuickLootDropItemPacket message, MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || player.worldObj == null
                    || player.worldObj.provider == null || message == null) {
                return null;
            }

            final int expectedDimension = player.worldObj.provider.dimensionId;
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            final int slot = message.slot;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.QUICK_LOOT_MUTATION,
                    message.malformed,
                    "LostTalesQuickLootDropItemPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            execute(livePlayer, expectedDimension, x, y, z, slot);
                        }
                    }
            );
            return null;
        }
    }
}
