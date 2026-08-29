package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatMessageIds;
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
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Bounded channel request. Recipient selection never crosses the wire, and
 * neither does shared data: a shared item is only a slot index and a shared
 * marker only an id, both re-read by the server from its own state and
 * paired by order with the tokens the server itself parses out of the
 * message.
 */
public final class LostTalesChatSendPacket implements IMessage {
    /** Speak as the channel's default identity. */
    public static final int APPEARANCE_DEFAULT = 0;
    /** Speak as the Minecraft account, wherever the message goes. */
    public static final int APPEARANCE_ACCOUNT = 1;
    /** Speak as one of the sender's own roster characters. */
    public static final int APPEARANCE_CHARACTER = 2;

    private static final int MAX_PACKET_BYTES = 1300
            + ChatMessageValidator.MAX_UTF8_BYTES
            + ChatShareTokenParser.MAX_TOKENS
            * (ChatShareReference.MAX_MARKER_ID_BYTES + 8);
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_TARGET_BYTES = 64;
    /** An identity name is bounded like the one a line is signed with. */
    private static final int MAX_IDENTITY_BYTES = 256;

    private String channelId = "";
    private String message = "";
    private List<ChatShareReference> references = Collections.emptyList();
    /** Account name a whisper is for; empty for every other channel. */
    private String target = "";
    /**
     * The identity of that account the whisper is addressed to — a
     * character's name, or empty for the account's own conversation. A
     * request like any other: the server checks the name against that
     * player's own roster before the line is filed under it.
     */
    private String targetIdentity = "";
    /**
     * The sender's own name for this message, so the copy that comes
     * back can be recognised as the line already on their screen.
     * Meaningless anywhere else and never read anywhere else: the
     * server hands it back only to the sender, and the sender matches
     * it only against messages it signed itself. Zero is no name at
     * all, which is what every client that does not show its messages
     * early sends.
     */
    private long echoNonce;
    /**
     * The identity the sender asks to speak as. A request, never a
     * fact: the server resolves a character id against the sender's own
     * roster and refuses one it does not hold.
     */
    private int appearanceKind = APPEARANCE_DEFAULT;
    private UUID appearanceCharacterId;
    /**
     * The message this one replies to, or {@link ChatMessageIds#NONE}.
     * A request like any other: the server checks the message is still
     * within reach and that this sender was one of its recipients, and
     * drops the reference otherwise — naming an id is not being shown
     * the message it names.
     */
    private long replyToMessageId = ChatMessageIds.NONE;
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
        this(channel, message, references, target, APPEARANCE_DEFAULT, null);
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId) {
        this(channel, message, references, target, appearanceKind,
                appearanceCharacterId, ChatMessageIds.NONE);
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId,
                                   long replyToMessageId) {
        this(channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, "");
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity) {
        this(channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, targetIdentity, 0L);
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity, long echoNonce) {
        this.echoNonce = echoNonce;
        this.targetIdentity = targetIdentity == null ? ""
                : targetIdentity.trim();
        this.replyToMessageId = replyToMessageId;
        this.target = target == null ? "" : target.trim();
        this.channelId = channel == null ? "" : channel.getId();
        this.message = message == null ? "" : message;
        this.references = references == null || references.isEmpty()
                ? Collections.<ChatShareReference>emptyList()
                : Collections.unmodifiableList(
                        new ArrayList<ChatShareReference>(references));
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
            this.appearanceKind = buffer.readUnsignedByte();
            this.appearanceCharacterId =
                    this.appearanceKind == APPEARANCE_CHARACTER
                            ? new UUID(buffer.readLong(), buffer.readLong())
                            : null;
            this.replyToMessageId = buffer.readLong();
            this.targetIdentity = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_IDENTITY_BYTES).trim();
            this.echoNonce = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.target = "";
            this.references = Collections.emptyList();
            this.appearanceKind = APPEARANCE_DEFAULT;
            this.appearanceCharacterId = null;
            this.replyToMessageId = ChatMessageIds.NONE;
            this.targetIdentity = "";
            this.echoNonce = 0L;
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
        buffer.writeByte(this.appearanceKind);
        if (this.appearanceKind == APPEARANCE_CHARACTER) {
            buffer.writeLong(
                    this.appearanceCharacterId.getMostSignificantBits());
            buffer.writeLong(
                    this.appearanceCharacterId.getLeastSignificantBits());
        }
        buffer.writeLong(this.replyToMessageId);
        LostTalesPacketCodec.writeUtf8String(buffer, this.targetIdentity,
                MAX_IDENTITY_BYTES);
        buffer.writeLong(this.echoNonce);
    }

    private void validate() {
        if (this.replyToMessageId != ChatMessageIds.NONE
                && !ChatMessageIds.isServerId(this.replyToMessageId)
                || this.appearanceKind < APPEARANCE_DEFAULT
                || this.appearanceKind > APPEARANCE_CHARACTER
                || (this.appearanceKind == APPEARANCE_CHARACTER
                        && this.appearanceCharacterId == null)
                || ChatChannel.fromId(this.channelId) == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.target, MAX_TARGET_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.targetIdentity, MAX_IDENTITY_BYTES)
                || (this.targetIdentity.length() > 0
                        && ChatChannel.fromId(this.channelId)
                                != ChatChannel.WHISPER)
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
    /** The identity of that account addressed; empty for its own. */
    public String getTargetIdentity() { return this.targetIdentity; }
    /** The sender's own name for this message; zero for none. */
    public long getEchoNonce() { return this.echoNonce; }
    /** One of the {@code APPEARANCE_*} constants. */
    public int getAppearanceKind() { return this.appearanceKind; }
    /** The asked-for roster character; null unless the kind names one. */
    public UUID getAppearanceCharacterId() {
        return this.appearanceCharacterId;
    }
    /** The message this one asks to reply to; {@code NONE} for none. */
    public long getReplyToMessageId() {
        return this.replyToMessageId;
    }
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
                                    message.getTarget(),
                                    message.getAppearanceKind(),
                                    message.getAppearanceCharacterId(),
                                    message.getReplyToMessageId(),
                                    message.getTargetIdentity(),
                                    message.getEchoNonce());
                        }
                    });
            return null;
        }
    }
}
