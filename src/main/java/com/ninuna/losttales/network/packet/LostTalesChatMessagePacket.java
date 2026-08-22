package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-created public presentation snapshot for one authorized recipient.
 * Shared items and markers arrive as server-validated payloads, so
 * recipients never have to trust the sender for them.
 */
public final class LostTalesChatMessagePacket implements IMessage {
    private static final int MAX_SHOWCASE_BYTES = Math.max(
            ChatShowcase.MAX_STACK_BYTES,
            ChatShowcase.MAX_MARKER_ID_BYTES
                    + ChatShowcase.MAX_MARKER_NAME_BYTES
                    + ChatShowcase.MAX_MARKER_STYLE_BYTES * 2 + 32);
    private static final int MAX_PACKET_BYTES = 2048
            + ChatMessageValidator.MAX_UTF8_BYTES
            + ChatShareTokenParser.MAX_TOKENS * (MAX_SHOWCASE_BYTES + 8);
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_IDENTITY_BYTES = 256;
    private static final int MAX_ACCOUNT_NAME_BYTES = 64;
    private static final int MAX_TITLE_BYTES = 256;
    private static final int MAX_SKIN_ID_BYTES = 128;
    private static final int MAX_FACTION_NAME_BYTES = 128;

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
    private List<ChatShowcase> showcases = Collections.emptyList();
    /** Sender's faction display name for the title; empty when untitled. */
    private String factionName = "";
    /** For a whisper, the other party's account name as this recipient sees it. */
    private String partner = "";
    private boolean malformed;

    public LostTalesChatMessagePacket() {}

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId) {
        this(channel, senderId, identityName, accountName, title,
                titleColor, nameColor, message, timestampMillis, skinId,
                null);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases) {
        this(channel, senderId, identityName, accountName, title,
                titleColor, nameColor, message, timestampMillis, skinId,
                showcases, "");
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName) {
        this(channel, senderId, identityName, accountName, title,
                titleColor, nameColor, message, timestampMillis, skinId,
                showcases, factionName, "");
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner) {
        this.partner = partner == null ? "" : partner.trim();
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
        this.showcases = showcases == null || showcases.isEmpty()
                ? Collections.<ChatShowcase>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<ChatShowcase>(showcases));
        this.factionName = factionName == null ? "" : factionName;
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
            int count = buffer.readUnsignedByte();
            if (count > ChatShareTokenParser.MAX_TOKENS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many showcases");
            }
            List<ChatShowcase> decoded = new ArrayList<ChatShowcase>(count);
            for (int index = 0; index < count; index++) {
                decoded.add(readShowcase(buffer));
            }
            this.showcases = Collections.unmodifiableList(decoded);
            this.factionName = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_FACTION_NAME_BYTES);
            this.partner = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_ACCOUNT_NAME_BYTES).trim();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.showcases = Collections.emptyList();
            this.factionName = "";
            this.partner = "";
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    private static ChatShowcase readShowcase(ByteBuf buffer) {
        int tokenIndex = buffer.readUnsignedByte();
        ChatShareKind kind = ChatShareKind.fromCode(buffer.readUnsignedByte());
        if (kind == ChatShareKind.ITEM) {
            return ChatShowcase.item(tokenIndex,
                    LostTalesPacketCodec.readBytes(
                            buffer, ChatShowcase.MAX_STACK_BYTES));
        }
        if (kind == ChatShareKind.MARKER) {
            String id = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatShowcase.MAX_MARKER_ID_BYTES);
            String name = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatShowcase.MAX_MARKER_NAME_BYTES);
            String icon = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatShowcase.MAX_MARKER_STYLE_BYTES);
            String color = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatShowcase.MAX_MARKER_STYLE_BYTES);
            int dimension = buffer.readInt();
            double x = buffer.readDouble();
            double z = buffer.readDouble();
            return ChatShowcase.marker(tokenIndex, id, name, icon, color,
                    dimension, x, z);
        }
        throw new LostTalesPacketCodec.DecodeException("unknown showcase kind");
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
        buffer.writeByte(this.showcases.size());
        for (ChatShowcase showcase : this.showcases) {
            buffer.writeByte(showcase.getTokenIndex());
            buffer.writeByte(showcase.getKind().getCode());
            if (showcase.getKind() == ChatShareKind.ITEM) {
                LostTalesPacketCodec.writeBytes(buffer,
                        showcase.getStackData(),
                        ChatShowcase.MAX_STACK_BYTES);
            } else {
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getMarkerId(),
                        ChatShowcase.MAX_MARKER_ID_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getMarkerName(),
                        ChatShowcase.MAX_MARKER_NAME_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getMarkerIcon(),
                        ChatShowcase.MAX_MARKER_STYLE_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getMarkerColor(),
                        ChatShowcase.MAX_MARKER_STYLE_BYTES);
                buffer.writeInt(showcase.getMarkerDimension());
                buffer.writeDouble(showcase.getMarkerX());
                buffer.writeDouble(showcase.getMarkerZ());
            }
        }
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.factionName, MAX_FACTION_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.partner, MAX_ACCOUNT_NAME_BYTES);
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
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.factionName, MAX_FACTION_NAME_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.partner, MAX_ACCOUNT_NAME_BYTES)
                || (ChatChannel.fromId(this.channelId) == ChatChannel.WHISPER
                        && this.partner.length() == 0)
                || this.timestampMillis <= 0L
                || this.showcases.size() > ChatShareTokenParser.MAX_TOKENS) {
            throw new IllegalArgumentException("invalid chat message");
        }
        // Each token index may carry one showcase of the token's own kind,
        // and every index must point at a token the message contains.
        List<ChatShareTokenParser.Token> tokens =
                ChatShareTokenParser.parse(this.message);
        boolean[] used = new boolean[ChatShareTokenParser.MAX_TOKENS];
        for (ChatShowcase showcase : this.showcases) {
            if (showcase == null || showcase.getTokenIndex() >= tokens.size()
                    || used[showcase.getTokenIndex()]
                    || tokens.get(showcase.getTokenIndex()).kind
                            != showcase.getKind()) {
                throw new IllegalArgumentException(
                        "invalid chat showcase index");
            }
            used[showcase.getTokenIndex()] = true;
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
    /** The sender's faction display name, or empty. */
    public String getFactionName() { return this.factionName; }
    /** For a whisper, the other party's account name; empty otherwise. */
    public String getPartner() { return this.partner; }
    /** Validated showcases keyed by token index; never null. */
    public List<ChatShowcase> getShowcases() { return this.showcases; }
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
