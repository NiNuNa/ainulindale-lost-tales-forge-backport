package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
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
 * Client-to-server: what was said in one conversation of a channel that
 * has more than one, before this client was shown any of it. A player
 * reading the chat as another of their characters opens that character's
 * faction talk with nothing in it until this is answered.
 *
 * <p>It names the channel, the conversation — a normalized faction id —
 * and the newest message the client already holds for it, so the answer
 * carries only what is missing. That last part is what makes asking
 * safe to repeat: the client appends replayed lines without clearing,
 * so an answer that repeated what is already on screen would show every
 * line twice.</p>
 *
 * <p>Nothing here is trusted. The server re-derives whether the account
 * has a character in the conversation it asks about, exactly as it does
 * when it routes a line there, and answers with nothing when it has
 * none.</p>
 */
public final class LostTalesChatContextHistoryPacket implements IMessage {
    private static final int MAX_CHANNEL_ID_BYTES = 64;
    /** A conversation is named by a normalized faction id. */
    private static final int MAX_SCOPE_VALUE_BYTES = 128;
    private static final int MAX_PACKET_BYTES =
            MAX_CHANNEL_ID_BYTES + MAX_SCOPE_VALUE_BYTES + 32;

    private String channelId = "";
    private String scopeValue = "";
    private long sinceMessageId = ChatMessageIds.NONE;
    private boolean malformed;

    public LostTalesChatContextHistoryPacket() {}

    public LostTalesChatContextHistoryPacket(ChatChannel channel, String scopeValue,
                                             long sinceMessageId) {
        this.channelId = channel == null ? "" : channel.getId();
        this.scopeValue = scopeValue == null ? "" : scopeValue.trim();
        this.sinceMessageId = sinceMessageId;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat context history packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_ID_BYTES);
            this.scopeValue = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_SCOPE_VALUE_BYTES);
            this.sinceMessageId = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.channelId = "";
            this.scopeValue = "";
            this.sinceMessageId = ChatMessageIds.NONE;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(buffer, this.channelId,
                MAX_CHANNEL_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.scopeValue,
                MAX_SCOPE_VALUE_BYTES);
        buffer.writeLong(this.sinceMessageId);
    }

    /**
     * A request only makes sense for a channel that has more than one
     * conversation, and only when it names which; anything else is a
     * request for what the login replay already answered.
     */
    private void validate() {
        ChatChannel channel = ChatChannel.fromId(this.channelId);
        if (channel == null || !channel.isScoped()
                || this.scopeValue.length() == 0
                || this.sinceMessageId < ChatMessageIds.NONE) {
            throw new IllegalArgumentException("invalid chat context history request");
        }
    }

    public ChatChannel getChannel() { return ChatChannel.fromId(this.channelId); }
    /** The conversation asked about: a normalized faction id. */
    public String getScopeValue() { return this.scopeValue; }
    /** The newest message the client already holds for it. */
    public long getSinceMessageId() { return this.sinceMessageId; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatContextHistoryPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatContextHistoryPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_HISTORY,
                    message.isMalformed(), "LostTalesChatContextHistoryPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            LostTalesChatService.sendContextHistory(serverPlayer,
                                    message.getChannel(), message.getScopeValue(),
                                    message.getSinceMessageId());
                        }
                    });
            return null;
        }
    }
}
