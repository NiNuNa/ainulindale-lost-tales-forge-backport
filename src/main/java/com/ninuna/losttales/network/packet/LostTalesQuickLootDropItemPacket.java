package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.block.tileentity.LostTalesTileEntityUrn;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

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
            if (!player.worldObj.blockExists(message.x, message.y, message.z)) return null;

            double dx = player.posX - ((double) message.x + 0.5D);
            double dy = player.posY - ((double) message.y + 0.5D);
            double dz = player.posZ - ((double) message.z + 0.5D);
            if (dx * dx + dy * dy + dz * dz > 64.0D) return null;

            TileEntity tileEntity = player.worldObj.getTileEntity(message.x, message.y, message.z);
            if (!(tileEntity instanceof IInventory) || tileEntity.isInvalid()) return null;
            if (tileEntity instanceof LostTalesTileEntityUrn && ((LostTalesTileEntityUrn) tileEntity).isSealed()) return null;

            IInventory inventory = (IInventory) tileEntity;
            if (!inventory.isUseableByPlayer(player)) return null;
            if (message.slot < 0 || message.slot >= inventory.getSizeInventory()) return null;

            ItemStack stack = inventory.getStackInSlot(message.slot);
            if (stack == null || stack.stackSize <= 0) return null;

            ItemStack removed = inventory.decrStackSize(message.slot, stack.stackSize);
            if (removed == null || removed.stackSize <= 0) return null;

            inventory.markDirty();
            player.worldObj.markBlockForUpdate(message.x, message.y, message.z);

            EntityItem entityItem = new EntityItem(player.worldObj, (double) message.x + 0.5D, (double) message.y + 1.0D, (double) message.z + 0.5D, removed.copy());
            player.worldObj.spawnEntityInWorld(entityItem);
            player.worldObj.playSoundEffect((double) message.x + 0.5D, (double) message.y + 0.5D, (double) message.z + 0.5D, "random.pop", 0.5F, 1.0F);
            return null;
        }
    }
}
