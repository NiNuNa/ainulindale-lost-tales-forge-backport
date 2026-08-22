package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.server.LostTalesChatService;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Bounded channel request. Recipient selection never crosses the wire, and
 * neither does shared data: a shared item is only a slot index and a shared
 * marker only an id, both re-read by the server from its own state and
 * paired by order with the tokens the server itself parses out of the
 * message.
 */
public final class LostTalesChatSendPacket implements IMessage {
    private static final int MAX_PACKET_BYTES = 1024
            + ChatMessageValidator.MAX_UTF8_BYTES
            + ChatShareTokenParser.MAX_TOKENS
            * (ChatShareReference.MAX_MARKER_ID_BYTES + 8);
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_TARGET_BYTES = 64;

    private String channelId = "";
    private String message = "";
    private List<ChatShareReference> references = Collections.emptyList();
    /** Account name a whisper is for; empty for every other channel. */
    private String target = "";
    private boolean malformed;

    public LostTalesChatSendPacket() {}

    public LostTalesChatSendPacket(ChatChannel channel, String message) {
        this(channel, message, null);
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references) {
        this(channel, message, references, "");
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target) {
        this.target = target == null ? "" : target.trim();
        this.channelId = channel == null ? "" : channel.getId();
        this.message = message == null ? "" : message;
        this.references = references == null || references.isEmpty()
                ? Collections.<ChatShareReference>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<ChatShareReference>(references));
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat request packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.message = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatMessageValidator.MAX_UTF8_BYTES);
            int count = buffer.readUnsignedByte();
            if (count > ChatShareTokenParser.MAX_TOKENS) {
                throw new LostTalesPacketCodec.DecodeException(
                        "too many share references");
            }
            List<ChatShareReference> decoded =
                    new ArrayList<ChatShareReference>(count);
            for (int index = 0; index < count; index++) {
                ChatShareKind kind = ChatShareKind.fromCode(
                        buffer.readUnsignedByte());
                if (kind == ChatShareKind.ITEM) {
                    decoded.add(ChatShareReference.item(
                            buffer.readUnsignedByte()));
                } else if (kind == ChatShareKind.MARKER) {
                    decoded.add(ChatShareReference.marker(
                            LostTalesPacketCodec.readUtf8String(buffer,
                                    ChatShareReference.MAX_MARKER_ID_BYTES)));
                } else {
                    throw new LostTalesPacketCodec.DecodeException(
                            "unknown share kind");
                }
            }
            this.references = Collections.unmodifiableList(decoded);
            this.target = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_TARGET_BYTES).trim();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.target = "";
            this.references = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.channelId, MAX_CHANNEL_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.message, ChatMessageValidator.MAX_UTF8_BYTES);
        buffer.writeByte(this.references.size());
        for (ChatShareReference reference : this.references) {
            buffer.writeByte(reference.getKind().getCode());
            if (reference.getKind() == ChatShareKind.ITEM) {
                buffer.writeByte(reference.getSlot());
            } else {
                LostTalesPacketCodec.writeUtf8String(buffer,
                        reference.getMarkerId(),
                        ChatShareReference.MAX_MARKER_ID_BYTES);
            }
        }
        LostTalesPacketCodec.writeUtf8String(buffer, this.target,
                MAX_TARGET_BYTES);
    }

    private void validate() {
        if (ChatChannel.fromId(this.channelId) == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.target, MAX_TARGET_BYTES)
                || (ChatChannel.fromId(this.channelId) == ChatChannel.WHISPER
                        && this.target.length() == 0)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.message, ChatMessageValidator.MAX_UTF8_BYTES)
                || !ChatMessageValidator.isValid(this.message)
                || this.references.size() > ChatShareTokenParser.MAX_TOKENS) {
            throw new IllegalArgumentException("invalid chat request");
        }
        for (ChatShareReference reference : this.references) {
            if (reference == null) {
                throw new IllegalArgumentException("invalid share reference");
            }
        }
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    public String getMessage() { return this.message; }
    /** References in token order; may be shorter than the token list. */
    public List<ChatShareReference> getReferences() { return this.references; }
    /** The whisper's account name; empty otherwise. */
    public String getTarget() { return this.target; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatSendPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatSendPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(
                    player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_MESSAGE,
                    message.isMalformed(),
                    "LostTalesChatSendPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP livePlayer) {
                            LostTalesChatService.send(
                                    livePlayer, message.getChannel(),
                                    message.getMessage(),
                                    message.getReferences(),
                                    message.getTarget());
                        }
                    });
            return null;
        }
    }
}
