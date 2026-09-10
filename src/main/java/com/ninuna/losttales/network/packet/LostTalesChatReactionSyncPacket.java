package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReactionSummary;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: the reactions on a message already on screen, as
 * this reader is shown them now. The whole summary, not a change to it:
 * a client that missed one update is right again with the next.
 *
 * <p>Sent to the message's readers alone, so a message never shown to
 * someone is never mentioned to them either.</p>
 */
public final class LostTalesChatReactionSyncPacket implements IMessage {
    private static final int MAX_PACKET_BYTES =
            8 + LostTalesChatReactionCodec.MAX_BYTES;

    private long messageId = ChatMessageIds.NONE;
    private ChatReactionSummary reactions = ChatReactionSummary.EMPTY;
    private boolean malformed;

    public LostTalesChatReactionSyncPacket() {}

    public LostTalesChatReactionSyncPacket(long messageId,
                                           ChatReactionSummary reactions) {
        this.messageId = messageId;
        this.reactions = reactions == null ? ChatReactionSummary.EMPTY
                : reactions;
        if (!ChatMessageIds.isServerId(messageId)) {
            throw new IllegalArgumentException("invalid chat reaction sync");
        }
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat reaction sync size");
            }
            long id = buffer.readLong();
            if (!ChatMessageIds.isServerId(id)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat message id");
            }
            ChatReactionSummary decoded = LostTalesChatReactionCodec.read(buffer);
            LostTalesPacketCodec.requireFinished(buffer);
            this.messageId = id;
            this.reactions = decoded;
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.reactions = ChatReactionSummary.EMPTY;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        buffer.writeLong(this.messageId);
        LostTalesChatReactionCodec.write(buffer, this.reactions);
    }

    public long getMessageId() { return this.messageId; }
    public ChatReactionSummary getReactions() { return this.reactions; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatReactionSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatReactionSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatReactions(message);
                }
            });
            return null;
        }
    }
}
