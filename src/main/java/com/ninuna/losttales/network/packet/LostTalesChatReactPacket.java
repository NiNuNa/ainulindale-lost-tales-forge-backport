package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
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
 * Client-to-server: react to a message with an emoji, or take the
 * reaction back.
 *
 * <p>Names the message, the emoji and which of the two, and nothing
 * more: whether the player may read the message, what name they react
 * as and who has to be told are the server's record to answer. The
 * emoji is a reaction key; a well-formed foreign key passes here, and
 * the server takes it only where the message already carries it.</p>
 */
public final class LostTalesChatReactPacket implements IMessage {
    private static final int MAX_PACKET_BYTES =
            8 + 2 + ChatReactionSummary.MAX_EMOJI_BYTES + 1;

    private long messageId = ChatMessageIds.NONE;
    private String emoji = "";
    private boolean add;
    private boolean malformed;

    public LostTalesChatReactPacket() {}

    public LostTalesChatReactPacket(long messageId, String emoji,
                                    boolean add) {
        this.messageId = messageId;
        this.emoji = emoji == null ? "" : emoji;
        this.add = add;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat reaction packet size");
            }
            this.messageId = buffer.readLong();
            this.emoji = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatReactionSummary.MAX_EMOJI_BYTES);
            this.add = buffer.readBoolean();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.emoji = "";
            this.add = false;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeLong(this.messageId);
        LostTalesPacketCodec.writeUtf8String(buffer, this.emoji,
                ChatReactionSummary.MAX_EMOJI_BYTES);
        buffer.writeBoolean(this.add);
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId)
                || !ChatForeignEmoji.isReactionKey(this.emoji)) {
            throw new IllegalArgumentException("invalid chat reaction");
        }
    }

    public long getMessageId() { return this.messageId; }
    /** The emoji's reaction key. */
    public String getEmoji() { return this.emoji; }
    /** Whether the reaction is added rather than taken back. */
    public boolean isAdd() { return this.add; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatReactPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatReactPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_REACTION,
                    message.isMalformed(), "LostTalesChatReactPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            LostTalesChatService.react(serverPlayer,
                                    message.getMessageId(),
                                    message.getEmoji(), message.isAdd());
                        }
                    });
            return null;
        }
    }
}
