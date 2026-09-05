package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.config.server.LostTalesServerConfigService;
import com.ninuna.losttales.network.LostTalesNetworkHandler;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * An operator's client asking for the server's config. Carries nothing;
 * the server decides on its own thread whether the asker is an operator
 * and answers with a snapshot, or with nothing at all.
 */
public final class LostTalesServerConfigRequestPacket implements IMessage {

    private boolean malformed;

    public LostTalesServerConfigRequestPacket() {}

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesServerConfigRequestPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesServerConfigRequestPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.SERVER_CONFIG,
                    message.isMalformed(),
                    "LostTalesServerConfigRequestPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            if (!LostTalesServerConfigService.isOperator(livePlayer)) {
                                return;
                            }
                            LostTalesNetworkHandler.CHANNEL.sendTo(
                                    new LostTalesServerConfigSyncPacket(
                                            LostTalesServerConfigService.snapshot()),
                                    livePlayer);
                        }
                    });
            return null;
        }
    }
}
