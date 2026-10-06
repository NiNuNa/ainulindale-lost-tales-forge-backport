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
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: react to a message with an emoji, or take the
 * reaction back.
 *
 * <p>Names the message, the emoji, which of the two and the identity
 * the copy reacted in speaks as, and nothing more: whether the player may
 * react as it, whether they may read the message and who has to be told
 * are the server's record to answer. The
 * emoji is a reaction key; a well-formed foreign key passes here, and
 * the server takes it only where the message already carries it.</p>
 */
public final class LostTalesChatReactPacket implements IMessage {
    private static final int MAX_PACKET_BYTES =
            8 + 2 + ChatReactionSummary.MAX_EMOJI_BYTES + 1 + 1 + 16;

    private long messageId = ChatMessageIds.NONE;
    private String emoji = "";
    private boolean add;
    /**
     * Who the reaction is made as, named as a line's identity is: the
     * identity the copy reacted in speaks as. A request the server checks
     * against the player's roster.
     */
    private int identityKind = LostTalesChatSendPacket.IDENTITY_DEFAULT;
    private UUID identityCharacterId;
    private boolean malformed;

    public LostTalesChatReactPacket() {}

    public LostTalesChatReactPacket(long messageId, String emoji,
                                    boolean add, int identityKind,
                                    UUID identityCharacterId) {
        this.messageId = messageId;
        this.emoji = emoji == null ? "" : emoji;
        this.add = add;
        this.identityKind = identityKind;
        this.identityCharacterId = identityCharacterId;
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
            this.identityKind = buffer.readUnsignedByte();
            this.identityCharacterId = this.identityKind
                    == LostTalesChatSendPacket.IDENTITY_CHARACTER
                    ? new UUID(buffer.readLong(), buffer.readLong()) : null;
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.emoji = "";
            this.add = false;
            this.identityKind = LostTalesChatSendPacket.IDENTITY_DEFAULT;
            this.identityCharacterId = null;
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
        buffer.writeByte(this.identityKind);
        if (this.identityKind == LostTalesChatSendPacket.IDENTITY_CHARACTER) {
            buffer.writeLong(this.identityCharacterId.getMostSignificantBits());
            buffer.writeLong(this.identityCharacterId.getLeastSignificantBits());
        }
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId)
                || !ChatForeignEmoji.isReactionKey(this.emoji)
                || this.identityKind < LostTalesChatSendPacket.IDENTITY_DEFAULT
                || this.identityKind > LostTalesChatSendPacket.IDENTITY_CHARACTER
                || (this.identityKind == LostTalesChatSendPacket.IDENTITY_CHARACTER)
                        != (this.identityCharacterId != null)) {
            throw new IllegalArgumentException("invalid chat reaction");
        }
    }

    public long getMessageId() { return this.messageId; }
    /** The emoji's reaction key. */
    public String getEmoji() { return this.emoji; }
    /** Whether the reaction is added rather than taken back. */
    public boolean isAdd() { return this.add; }
    /** One of the send packet's {@code IDENTITY_*} constants. */
    public int getIdentityKind() { return this.identityKind; }
    /** The character the reaction is made as; null unless the kind names one. */
    public UUID getIdentityCharacterId() { return this.identityCharacterId; }
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
                                    message.getEmoji(), message.isAdd(),
                                    message.getIdentityKind(),
                                    message.getIdentityCharacterId());
                        }
                    });
            return null;
        }
    }
}
