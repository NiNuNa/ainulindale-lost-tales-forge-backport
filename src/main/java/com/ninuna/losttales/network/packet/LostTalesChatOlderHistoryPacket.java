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
 * Client-to-server: what was said in a channel before the oldest line
 * the client holds of it. A player scrolled to the top of a tab asks
 * for the page before it, the way a messenger loads older messages as
 * the reader reaches them; the login replay hands over only the newest
 * of each channel, and the server's kept history reaches further back.
 *
 * <p>It names the channel, the conversation for a channel that has more
 * than one (a normalized faction id, a party id; empty otherwise), and
 * the oldest message the client already holds, so the answer carries
 * only what comes before it. The server answers with the lines newest
 * first, so the client can lay each one above the last.</p>
 *
 * <p>Nothing here is trusted. The server re-derives what the account
 * may read, exactly as it does for the login replay, and answers with
 * nothing when the request names a conversation the account is not in.</p>
 */
public final class LostTalesChatOlderHistoryPacket implements IMessage {
    private static final int MAX_CHANNEL_ID_BYTES = 64;
    private static final int MAX_SCOPE_VALUE_BYTES = 128;
    private static final int MAX_PACKET_BYTES =
            MAX_CHANNEL_ID_BYTES + MAX_SCOPE_VALUE_BYTES + 32;

    private String channelId = "";
    private String scopeValue = "";
    private long beforeMessageId = ChatMessageIds.NONE;
    private boolean malformed;

    public LostTalesChatOlderHistoryPacket() {}

    public LostTalesChatOlderHistoryPacket(ChatChannel channel, String scopeValue,
                                           long beforeMessageId) {
        this.channelId = channel == null ? "" : channel.getId();
        this.scopeValue = scopeValue == null ? "" : scopeValue.trim();
        this.beforeMessageId = beforeMessageId;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat older history packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_ID_BYTES);
            this.scopeValue = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_SCOPE_VALUE_BYTES);
            this.beforeMessageId = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.channelId = "";
            this.scopeValue = "";
            this.beforeMessageId = ChatMessageIds.NONE;
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
        buffer.writeLong(this.beforeMessageId);
    }

    /**
     * A request names a channel the server has, a conversation exactly
     * when the channel has more than one, and a line the server named to
     * reach back from; a whisper is asked about by nobody, since its
     * lines are two accounts' own.
     */
    private void validate() {
        ChatChannel channel = ChatChannel.fromId(this.channelId);
        if (channel == null || channel == ChatChannel.WHISPER
                || channel.isScoped() != (this.scopeValue.length() > 0)
                || !ChatMessageIds.isServerId(this.beforeMessageId)) {
            throw new IllegalArgumentException("invalid chat older history request");
        }
    }

    public ChatChannel getChannel() { return ChatChannel.fromId(this.channelId); }
    /** The conversation asked about, or empty for a channel with one. */
    public String getScopeValue() { return this.scopeValue; }
    /** The oldest message the client already holds of it. */
    public long getBeforeMessageId() { return this.beforeMessageId; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatOlderHistoryPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatOlderHistoryPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_HISTORY,
                    message.isMalformed(), "LostTalesChatOlderHistoryPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            LostTalesChatService.sendOlderHistory(serverPlayer,
                                    message.getChannel(), message.getScopeValue(),
                                    message.getBeforeMessageId());
                        }
                    });
            return null;
        }
    }
}
