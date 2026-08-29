package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: take one already-sent message back.
 *
 * <p>Names the message and nothing more, for the same reason
 * {@link LostTalesChatEditPacket} does: whether the asking player wrote
 * it, and who has to be told it is gone, are the server's record to
 * answer.
 */
public final class LostTalesChatDeletePacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 16;

    private long messageId = ChatMessageIds.NONE;
    private boolean malformed;

    public LostTalesChatDeletePacket() {}

    public LostTalesChatDeletePacket(long messageId) {
        this.messageId = messageId;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat delete packet size");
            }
            this.messageId = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeLong(this.messageId);
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId)) {
            throw new IllegalArgumentException("invalid chat delete");
        }
    }

    /** The message being taken back. */
    public long getMessageId() { return this.messageId; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatDeletePacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatDeletePacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_REVISION,
                    message.isMalformed(), "LostTalesChatDeletePacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            LostTalesChatService.delete(serverPlayer,
                                    message.getMessageId());
                        }
                    });
            return null;
        }
    }
}
