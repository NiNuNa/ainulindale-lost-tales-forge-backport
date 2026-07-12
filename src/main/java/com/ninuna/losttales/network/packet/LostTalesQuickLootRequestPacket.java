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
import net.minecraft.entity.player.EntityPlayerMP;

public class LostTalesQuickLootRequestPacket implements IMessage {
    private int x;
    private int y;
    private int z;
    private boolean malformed;

    public LostTalesQuickLootRequestPacket() {}

    public LostTalesQuickLootRequestPacket(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        try {
            this.x = buffer.readInt();
            this.y = buffer.readInt();
            this.z = buffer.readInt();
            LostTalesPacketCodec.requireFinished(buffer);
            if (!LostTalesPacketCodec.isValidBlockPosition(this.x, this.y, this.z)) {
                throw new LostTalesPacketCodec.DecodeException("invalid quick-loot block position");
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
    }

    private static void execute(EntityPlayerMP player, int expectedDimension, int x, int y, int z) {
        if (player.worldObj.provider == null
                || player.worldObj.provider.dimensionId != expectedDimension) {
            return;
        }
        LostTalesQuickLootInventoryHelper.InventoryAccess access =
                LostTalesQuickLootInventoryHelper.resolve(player.worldObj, x, y, z);
        if (!LostTalesQuickLootInventoryHelper.isUsableBy(player, access)) {
            return;
        }

        LostTalesNetworkHandler.CHANNEL.sendTo(
                LostTalesQuickLootContainerSyncPacket.fromInventory(
                        access.getX(), access.getY(), access.getZ(), access.getInventory()),
                player
        );
    }

    public static class Handler implements IMessageHandler<LostTalesQuickLootRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesQuickLootRequestPacket message, MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || player.worldObj == null
                    || player.worldObj.provider == null || message == null) {
                return null;
            }

            final int expectedDimension = player.worldObj.provider.dimensionId;
            final int x = message.x;
            final int y = message.y;
            final int z = message.z;
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.QUICK_LOOT_SNAPSHOT,
                    message.malformed,
                    "LostTalesQuickLootRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            execute(livePlayer, expectedDimension, x, y, z);
                        }
                    }
            );
            return null;
        }
    }
}
