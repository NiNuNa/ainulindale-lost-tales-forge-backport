package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.network.LostTalesNetworkHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;

public class LostTalesQuickLootRequestPacket implements IMessage {
    private int x;
    private int y;
    private int z;

    public LostTalesQuickLootRequestPacket() {}

    public LostTalesQuickLootRequestPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.x = buf.readInt();
        this.y = buf.readInt();
        this.z = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.x);
        buf.writeInt(this.y);
        buf.writeInt(this.z);
    }

    public static class Handler implements IMessageHandler<LostTalesQuickLootRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesQuickLootRequestPacket message, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null) return null;

            double dx = player.posX - ((double) message.x + 0.5D);
            double dy = player.posY - ((double) message.y + 0.5D);
            double dz = player.posZ - ((double) message.z + 0.5D);
            if (dx * dx + dy * dy + dz * dz > 64.0D) return null;

            TileEntity tileEntity = player.worldObj.getTileEntity(message.x, message.y, message.z);
            if (!(tileEntity instanceof IInventory)) return null;

            LostTalesNetworkHandler.CHANNEL.sendTo(
                    LostTalesQuickLootContainerSyncPacket.fromInventory(message.x, message.y, message.z, (IInventory) tileEntity),
                    player
            );
            return null;
        }
    }
}
