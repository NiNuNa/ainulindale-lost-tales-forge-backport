package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.inventory.LostTalesQuickLootInventoryHelper;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
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

    public LostTalesQuickLootDropItemPacket() {}

    public LostTalesQuickLootDropItemPacket(int x, int y, int z, int slot) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.slot = slot;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
        this.slot = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
        buf.writeInt(this.slot);
    }

    public static class Handler implements IMessageHandler<LostTalesQuickLootDropItemPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesQuickLootDropItemPacket message, MessageContext ctx) {
            if (message == null || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null || player.worldObj.isRemote) return null;

            LostTalesQuickLootInventoryHelper.InventoryAccess access = LostTalesQuickLootInventoryHelper.resolve(player.worldObj, message.x, message.y, message.z);
            if (!LostTalesQuickLootInventoryHelper.isUsableBy(player, access)) return null;
            if (access.isSealed()) return null;

            IInventory inventory = access.getInventory();
            if (message.slot < 0 || message.slot >= inventory.getSizeInventory()) return null;

            ItemStack stack = inventory.getStackInSlot(message.slot);
            if (stack == null || stack.stackSize <= 0) return null;

            ItemStack removed = inventory.decrStackSize(message.slot, stack.stackSize);
            if (removed == null || removed.stackSize <= 0) return null;

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
            player.worldObj.playSoundEffect((double) access.getX() + 0.5D, (double) access.getY() + 0.5D, (double) access.getZ() + 0.5D, "random.pop", 0.5F, 1.0F);

            LostTalesNetworkHandler.CHANNEL.sendTo(
                    LostTalesQuickLootContainerSyncPacket.fromInventory(access.getX(), access.getY(), access.getZ(), inventory),
                    player
            );
            return null;
        }
    }
}
