package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

import java.util.UUID;

/**
 * Client-to-server: the player is, or has stopped, typing into a
 * channel. Presence only — no text ever crosses with it — and the
 * server decides who hears of it with the same recipient rules as a
 * message, so a typing player is only ever shown to those who would
 * read what they send.
 *
 * <p>It states the identity the message will wear, the same way a send
 * does. Presence is routed and gated by that identity — a faction's talk
 * goes to the faction the line will be spoken to, not to the one the
 * player happens to be walking around as — so the indicator can never
 * promise a message somewhere the message will not go.</p>
 */
public final class LostTalesChatTypingPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 256;
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_TARGET_BYTES = 64;

    private String channelId = "";
    /** Account name a whisper is for; empty for every other channel. */
    private String target = "";
    private String targetIdentity = "";
    private UUID targetCharacterId;
    private boolean typing;
    /** Which identity the message would wear; see the send packet. */
    private int identityKind = LostTalesChatSendPacket.IDENTITY_DEFAULT;
    private UUID identityCharacterId;
    private boolean malformed;

    public LostTalesChatTypingPacket() {}

    public LostTalesChatTypingPacket(ChatChannel channel, String target,
                                     boolean typing) {
        this(channel, target, typing,
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null);
    }

    public LostTalesChatTypingPacket(ChatChannel channel, String target,
                                     boolean typing, int identityKind,
                                     UUID identityCharacterId) {
        this(channel, target, typing, identityKind, identityCharacterId, "", null);
    }

    public LostTalesChatTypingPacket(ChatChannel channel, String target,
                                     boolean typing, int identityKind,
                                     UUID identityCharacterId, String targetIdentity,
                                     UUID targetCharacterId) {
        this.targetIdentity = targetIdentity == null ? "" : targetIdentity.trim();
        this.targetCharacterId = targetCharacterId;
        this.channelId = channel == null ? "" : channel.getId();
        this.target = target == null ? "" : target.trim();
        this.typing = typing;
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
                        "invalid chat typing packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.target = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_TARGET_BYTES).trim();
            this.typing = buffer.readBoolean();
            this.identityKind = buffer.readUnsignedByte();
            this.identityCharacterId = this.identityKind
                    == LostTalesChatSendPacket.IDENTITY_CHARACTER
                    ? new UUID(buffer.readLong(), buffer.readLong()) : null;
            this.targetIdentity = LostTalesPacketCodec.readUtf8String(buffer, 96).trim();
            int namedCharacter = buffer.readUnsignedByte();
            if (namedCharacter > 1) {
                throw new IllegalArgumentException("invalid whisper identity flag");
            }
            this.targetCharacterId = namedCharacter == 0 ? null
                    : new UUID(buffer.readLong(), buffer.readLong());
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.channelId = "";
            this.target = "";
            this.targetIdentity = "";
            this.targetCharacterId = null;
            this.typing = false;
            this.identityKind = LostTalesChatSendPacket.IDENTITY_DEFAULT;
            this.identityCharacterId = null;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(buffer, this.channelId,
                MAX_CHANNEL_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.target,
                MAX_TARGET_BYTES);
        buffer.writeBoolean(this.typing);
        buffer.writeByte(this.identityKind);
        if (this.identityKind
                == LostTalesChatSendPacket.IDENTITY_CHARACTER) {
            buffer.writeLong(
                    this.identityCharacterId.getMostSignificantBits());
            buffer.writeLong(
                    this.identityCharacterId.getLeastSignificantBits());
        }
        LostTalesPacketCodec.writeUtf8String(buffer, this.targetIdentity, 96);
        buffer.writeBoolean(this.targetCharacterId != null);
        if (this.targetCharacterId != null) {
            buffer.writeLong(this.targetCharacterId.getMostSignificantBits());
            buffer.writeLong(this.targetCharacterId.getLeastSignificantBits());
        }
    }

    private void validate() {
        ChatChannel channel = ChatChannel.fromId(this.channelId);
        if (channel == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.targetIdentity, 96)
                || (channel != ChatChannel.WHISPER
                        && (this.targetIdentity.length() > 0 || this.targetCharacterId != null))
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.target, MAX_TARGET_BYTES)
                || (channel == ChatChannel.WHISPER
                        && this.target.length() == 0)
                || (channel != ChatChannel.WHISPER
                        && this.target.length() > 0)
                || this.identityKind
                        < LostTalesChatSendPacket.IDENTITY_DEFAULT
                || this.identityKind
                        > LostTalesChatSendPacket.IDENTITY_CHARACTER
                || (this.identityKind
                        == LostTalesChatSendPacket.IDENTITY_CHARACTER
                        && this.identityCharacterId == null)) {
            throw new IllegalArgumentException("invalid chat typing request");
        }
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    /** The whisper's account name; empty otherwise. */
    public String getTarget() { return this.target; }
    public String getTargetIdentity() { return this.targetIdentity; }
    public UUID getTargetCharacterId() { return this.targetCharacterId; }
    public boolean isTyping() { return this.typing; }
    public int getIdentityKind() { return this.identityKind; }
    public UUID getIdentityCharacterId() {
        return this.identityCharacterId;
    }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatTypingPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatTypingPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_TYPING,
                    message.isMalformed(),
                    "LostTalesChatTypingPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesChatService.typing(livePlayer,
                                    message.getChannel(),
                                    message.getTarget(),
                                    message.isTyping(),
                                    message.getIdentityKind(),
                                    message.getIdentityCharacterId(),
                                    message.getTargetIdentity(),
                                    message.getTargetCharacterId());
                        }
                    });
            return null;
        }
    }
}
