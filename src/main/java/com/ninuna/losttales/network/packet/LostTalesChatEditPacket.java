package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
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
 * Client-to-server: rewrite what one already-sent message says.
 *
 * <p>The message is named by the id the server stamped on it, and
 * nothing else about it is described — not the channel it was in, not
 * who saw it. Both are the server's own record, and re-deriving them
 * from the request is exactly how a player would edit a line they never
 * wrote. The new text is bounded and validated like any other message,
 * so an edit cannot smuggle in what a send would have refused.
 */
public final class LostTalesChatEditPacket implements IMessage {
    private static final int MAX_PACKET_BYTES =
            64 + ChatMessageValidator.MAX_UTF8_BYTES;

    private long messageId = ChatMessageIds.NONE;
    private String message = "";
    private boolean malformed;

    public LostTalesChatEditPacket() {}

    public LostTalesChatEditPacket(long messageId, String message) {
        this.messageId = messageId;
        this.message = message == null ? "" : message;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat edit packet size");
            }
            this.messageId = buffer.readLong();
            this.message = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatMessageValidator.MAX_UTF8_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.message = "";
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeLong(this.messageId);
        LostTalesPacketCodec.writeUtf8String(buffer, this.message,
                ChatMessageValidator.MAX_UTF8_BYTES);
    }

    private void validate() {
        // Only a message the server named can be edited: a client-local
        // id belongs to a line no server ever saw.
        if (!ChatMessageIds.isServerId(this.messageId)
                || !ChatMessageValidator.isValid(this.message)) {
            throw new IllegalArgumentException("invalid chat edit");
        }
    }

    /** The message being rewritten. */
    public long getMessageId() { return this.messageId; }
    /** What it should say instead. */
    public String getMessage() { return this.message; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatEditPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatEditPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_REVISION,
                    message.isMalformed(), "LostTalesChatEditPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            LostTalesChatService.edit(serverPlayer,
                                    message.getMessageId(),
                                    message.getMessage());
                        }
                    });
            return null;
        }
    }
}
