package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatChannel;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/**
 * Server-to-client: someone is, or has stopped, typing into a channel
 * the receiving player reads. Carries the name the message would show
 * (the character's in role-play channels, the account's otherwise) and,
 * for a whisper, the account name of the partner whose tab it belongs
 * in. Presence is short-lived on the client: a state that is not
 * refreshed expires on its own, so a lost stop never leaves a ghost.
 */
public final class LostTalesChatTypingSyncPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 256;
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_NAME_BYTES = 96;

    private String channelId = "";
    /** The whisper tab this belongs in; empty for every other channel. */
    private String partner = "";
    private String identityName = "";
    private boolean typing;
    private boolean malformed;

    public LostTalesChatTypingSyncPacket() {}

    public LostTalesChatTypingSyncPacket(ChatChannel channel, String partner,
                                         String identityName,
                                         boolean typing) {
        this.channelId = channel == null ? "" : channel.getId();
        this.partner = partner == null ? "" : partner.trim();
        this.identityName = identityName == null ? "" : identityName.trim();
        this.typing = typing;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat typing sync packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.partner = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_NAME_BYTES).trim();
            this.identityName = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_NAME_BYTES).trim();
            this.typing = buffer.readBoolean();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.channelId = "";
            this.partner = "";
            this.identityName = "";
            this.typing = false;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(buffer, this.channelId,
                MAX_CHANNEL_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.partner,
                MAX_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.identityName,
                MAX_NAME_BYTES);
        buffer.writeBoolean(this.typing);
    }

    private void validate() {
        ChatChannel channel = ChatChannel.fromId(this.channelId);
        if (channel == null || this.identityName.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.partner, MAX_NAME_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.identityName, MAX_NAME_BYTES)
                || (channel == ChatChannel.WHISPER
                        && this.partner.length() == 0)
                || (channel != ChatChannel.WHISPER
                        && this.partner.length() > 0)) {
            throw new IllegalArgumentException("invalid chat typing sync");
        }
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    /** The whisper partner's account name; empty otherwise. */
    public String getPartner() { return this.partner; }
    /** The name the typist's messages show in this channel. */
    public String getIdentityName() { return this.identityName; }
    public boolean isTyping() { return this.typing; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatTypingSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatTypingSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatTyping(message);
                }
            });
            return null;
        }
    }
}
