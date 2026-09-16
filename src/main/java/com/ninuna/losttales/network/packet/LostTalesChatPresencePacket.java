package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatPresence;
import com.ninuna.losttales.chat.server.ChatPresenceService;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: the presence this player chose, or the one idle
 * time chose for them. One byte, the status's code; anything else is
 * malformed and discarded.
 */
public final class LostTalesChatPresencePacket implements IMessage {
    private ChatPresence presence = ChatPresence.ONLINE;
    private boolean malformed;

    public LostTalesChatPresencePacket() {}

    public LostTalesChatPresencePacket(ChatPresence presence) {
        this.presence = presence == null ? ChatPresence.ONLINE : presence;
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() != 1) {
                throw new LostTalesPacketCodec.DecodeException("invalid chat presence size");
            }
            ChatPresence read = ChatPresence.fromCode(buffer.readUnsignedByte());
            if (read == null) {
                throw new LostTalesPacketCodec.DecodeException("invalid chat presence");
            }
            this.presence = read;
            LostTalesPacketCodec.requireFinished(buffer);
        } catch (RuntimeException failure) {
            this.malformed = true;
            this.presence = ChatPresence.ONLINE;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeByte(this.presence.code());
    }

    public ChatPresence getPresence() {
        return this.presence;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler
            implements IMessageHandler<LostTalesChatPresencePacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatPresencePacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_PRESENCE,
                    message.isMalformed(), "LostTalesChatPresencePacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP sender) {
                            ChatPresenceService.set(sender, message.getPresence());
                        }
                    });
            return null;
        }
    }
}
