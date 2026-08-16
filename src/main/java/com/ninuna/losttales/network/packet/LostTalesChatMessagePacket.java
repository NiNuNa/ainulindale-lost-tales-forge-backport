package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.UUID;

/** Server-created public presentation snapshot for one authorized recipient. */
public final class LostTalesChatMessagePacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 2048;
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_IDENTITY_BYTES = 256;
    private static final int MAX_ACCOUNT_NAME_BYTES = 64;
    private static final int MAX_TITLE_BYTES = 256;
    private static final int MAX_SKIN_ID_BYTES = 128;

    private String channelId = "";
    private UUID senderId;
    private String identityName = "";
    private String accountName = "";
    private String title = "";
    private int titleColor;
    private int nameColor;
    private String message = "";
    private long timestampMillis;
    private String skinId = "";
    private boolean malformed;

    public LostTalesChatMessagePacket() {}

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId) {
        this.channelId = channel == null ? "" : channel.getId();
        this.senderId = senderId;
        this.identityName = identityName == null ? "" : identityName;
        this.accountName = accountName == null ? "" : accountName;
        this.title = title == null ? "" : title;
        this.titleColor = titleColor;
        this.nameColor = nameColor;
        this.message = message == null ? "" : message;
        this.timestampMillis = timestampMillis;
        this.skinId = skinId == null ? "" : skinId;
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat message packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.senderId = new UUID(buffer.readLong(), buffer.readLong());
            this.identityName = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_IDENTITY_BYTES);
            this.accountName = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_ACCOUNT_NAME_BYTES);
            this.title = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_TITLE_BYTES);
            this.titleColor = buffer.readInt();
            this.nameColor = buffer.readInt();
            this.message = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatMessageValidator.MAX_UTF8_BYTES);
            this.timestampMillis = buffer.readLong();
            this.skinId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_SKIN_ID_BYTES);
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
        buffer.writeLong(this.senderId.getMostSignificantBits());
        buffer.writeLong(this.senderId.getLeastSignificantBits());
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.identityName, MAX_IDENTITY_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.accountName, MAX_ACCOUNT_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.title, MAX_TITLE_BYTES);
        buffer.writeInt(this.titleColor);
        buffer.writeInt(this.nameColor);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.message, ChatMessageValidator.MAX_UTF8_BYTES);
        buffer.writeLong(this.timestampMillis);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.skinId, MAX_SKIN_ID_BYTES);
    }

    private void validate() {
        if (ChatChannel.fromId(this.channelId) == null
                || this.senderId == null
                || this.identityName.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.identityName, MAX_IDENTITY_BYTES)
                || this.accountName.length() == 0
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.accountName, MAX_ACCOUNT_NAME_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.title, MAX_TITLE_BYTES)
                || !ChatMessageValidator.isValid(this.message)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.skinId, MAX_SKIN_ID_BYTES)
                || this.timestampMillis <= 0L) {
            throw new IllegalArgumentException("invalid chat message");
        }
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    public UUID getSenderId() { return this.senderId; }
    public String getIdentityName() { return this.identityName; }
    public String getAccountName() { return this.accountName; }
    public String getTitle() { return this.title; }
    public int getTitleColor() { return this.titleColor & 0xFFFFFF; }
    public int getNameColor() { return this.nameColor & 0xFFFFFF; }
    public String getMessage() { return this.message; }
    public long getTimestampMillis() { return this.timestampMillis; }
    public String getSkinId() { return this.skinId; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatMessagePacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatMessagePacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatMessage(message);
                }
            });
            return null;
        }
    }
}
