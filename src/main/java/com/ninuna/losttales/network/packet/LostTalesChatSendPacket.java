package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
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

/** Bounded channel request. Recipient selection never crosses the wire. */
public final class LostTalesChatSendPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 512;
    private static final int MAX_CHANNEL_BYTES = 16;

    private String channelId = "";
    private String message = "";
    private boolean malformed;

    public LostTalesChatSendPacket() {}

    public LostTalesChatSendPacket(ChatChannel channel, String message) {
        this.channelId = channel == null ? "" : channel.getId();
        this.message = message == null ? "" : message;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat request packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.message = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatMessageValidator.MAX_UTF8_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.channelId, MAX_CHANNEL_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.message, ChatMessageValidator.MAX_UTF8_BYTES);
    }

    private void validate() {
        if (ChatChannel.fromId(this.channelId) == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.message, ChatMessageValidator.MAX_UTF8_BYTES)
                || !ChatMessageValidator.isValid(this.message)) {
            throw new IllegalArgumentException("invalid chat request");
        }
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    public String getMessage() { return this.message; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatSendPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatSendPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE,
                    message.isMalformed(),
                    "LostTalesChatSendPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesChatService.send(
                                    livePlayer, message.getChannel(),
                                    message.getMessage());
                        }
                    });
            return null;
        }
    }
}
