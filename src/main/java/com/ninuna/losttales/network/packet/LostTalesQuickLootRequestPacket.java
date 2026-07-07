package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.inventory.LostTalesQuickLootInventoryHelper;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

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
            if (message == null || ctx == null || ctx.getServerHandler() == null) {
                return null;
            }

            EntityPlayerMP player = ctx.getServerHandler().playerEntity;
            if (player == null || player.worldObj == null || player.worldObj.isRemote) return null;

            LostTalesQuickLootInventoryHelper.InventoryAccess access = LostTalesQuickLootInventoryHelper.resolve(player.worldObj, message.x, message.y, message.z);
            if (!LostTalesQuickLootInventoryHelper.isUsableBy(player, access)) return null;

            LostTalesNetworkHandler.CHANNEL.sendTo(
                    LostTalesQuickLootContainerSyncPacket.fromInventory(access.getX(), access.getY(), access.getZ(), access.getInventory()),
                    player
            );
            return null;
        }
    }
}
