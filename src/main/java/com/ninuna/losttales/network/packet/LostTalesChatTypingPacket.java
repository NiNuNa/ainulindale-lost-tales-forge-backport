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
    private static final int MAX_PACKET_BYTES = 128;
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_TARGET_BYTES = 64;

    private String channelId = "";
    /** Account name a whisper is for; empty for every other channel. */
    private String target = "";
    private boolean typing;
    /** Which identity the message would wear; see the send packet. */
    private int appearanceKind = LostTalesChatSendPacket.APPEARANCE_DEFAULT;
    private UUID appearanceCharacterId;
    private boolean malformed;

    public LostTalesChatTypingPacket() {}

    public LostTalesChatTypingPacket(ChatChannel channel, String target,
                                     boolean typing) {
        this(channel, target, typing,
                LostTalesChatSendPacket.APPEARANCE_DEFAULT, null);
    }

    public LostTalesChatTypingPacket(ChatChannel channel, String target,
                                     boolean typing, int appearanceKind,
                                     UUID appearanceCharacterId) {
        this.channelId = channel == null ? "" : channel.getId();
        this.target = target == null ? "" : target.trim();
        this.typing = typing;
        this.appearanceKind = appearanceKind;
        this.appearanceCharacterId = appearanceCharacterId;
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
            // Appended after the original layout: which identity the
            // message would wear. A shorter payload states none, and the
            // server signs and routes presence with the character being
            // played, which is what it did before this was carried.
            if (buffer.isReadable()) {
                this.appearanceKind = buffer.readUnsignedByte();
                this.appearanceCharacterId = this.appearanceKind
                        == LostTalesChatSendPacket.APPEARANCE_CHARACTER
                        ? new UUID(buffer.readLong(), buffer.readLong())
                        : null;
            }
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.channelId = "";
            this.target = "";
            this.typing = false;
            this.appearanceKind = LostTalesChatSendPacket.APPEARANCE_DEFAULT;
            this.appearanceCharacterId = null;
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
        buffer.writeByte(this.appearanceKind);
        if (this.appearanceKind
                == LostTalesChatSendPacket.APPEARANCE_CHARACTER) {
            buffer.writeLong(
                    this.appearanceCharacterId.getMostSignificantBits());
            buffer.writeLong(
                    this.appearanceCharacterId.getLeastSignificantBits());
        }
    }

    private void validate() {
        ChatChannel channel = ChatChannel.fromId(this.channelId);
        if (channel == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.target, MAX_TARGET_BYTES)
                || (channel == ChatChannel.WHISPER
                        && this.target.length() == 0)
                || (channel != ChatChannel.WHISPER
                        && this.target.length() > 0)
                || this.appearanceKind
                        < LostTalesChatSendPacket.APPEARANCE_DEFAULT
                || this.appearanceKind
                        > LostTalesChatSendPacket.APPEARANCE_CHARACTER
                || (this.appearanceKind
                        == LostTalesChatSendPacket.APPEARANCE_CHARACTER
                        && this.appearanceCharacterId == null)) {
            throw new IllegalArgumentException("invalid chat typing request");
        }
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    /** The whisper's account name; empty otherwise. */
    public String getTarget() { return this.target; }
    public boolean isTyping() { return this.typing; }
    public int getAppearanceKind() { return this.appearanceKind; }
    public UUID getAppearanceCharacterId() {
        return this.appearanceCharacterId;
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
                                    message.getAppearanceKind(),
                                    message.getAppearanceCharacterId());
                        }
                    });
            return null;
        }
    }
}
