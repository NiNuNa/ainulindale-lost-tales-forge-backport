package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAction;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatFellowship;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
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
    public static final int IDENTITY_DEFAULT = 0;
    /** Speak as the Minecraft account, wherever the message goes. */
    public static final int IDENTITY_ACCOUNT = 1;
    /** Speak as one of the sender's own roster characters. */
    public static final int IDENTITY_CHARACTER = 2;

    // The fixed fields, the action flag and the unkept-quote flag among them.
    private static final int MAX_PACKET_BYTES = 752
            + ChatMessageValidator.MAX_UTF8_BYTES
            + ChatShareTokenParser.MAX_TOKENS
            * (ChatShareReference.MAX_MARKER_ID_BYTES + 8);
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_TARGET_BYTES = 64;
    /** An identity name is bounded like the one a line is signed with. */
    private static final int MAX_IDENTITY_BYTES = 256;

    private String channelId = "";
    private String message = "";
    /**
     * Whether the words are an action ({@code /me}) rather than
     * something said: a request the server honours only in an
     * in-character channel, with the words checked as a message's are.
     */
    private boolean action;
    private List<ChatShareReference> references = Collections.emptyList();
    /**
     * The other end of the conversation: the account a whisper is for, or
     * the id of the fellowship a fellowship line is for; empty for every
     * other channel. The server checks the fellowship is the sender's.
     */
    private String target = "";
    /**
     * The identity of that account the whisper is addressed to — a
     * character's name, or empty for the account's own conversation. A
     * request like any other: the server checks the name against that
     * player's own roster before the line is filed under it.
     */
    private String targetIdentity = "";
    /**
     * For a whisper, the id of the character it is addressed to, when the
     * client knows it; null addresses the target by name, or their account.
     * The server resolves it against the target's own roster before the
     * line is filed under it.
     */
    private UUID targetCharacterId;
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
    private int identityKind = IDENTITY_DEFAULT;
    private UUID identityCharacterId;
    /**
     * The message this one replies to, or {@link ChatMessageIds#NONE}.
     * A request like any other: the server checks the message is still
     * within reach and that this sender was one of its recipients, and
     * drops the reference otherwise — naming an id is not being shown
     * the message it names.
     */
    private long replyToMessageId = ChatMessageIds.NONE;
    /**
     * Whether this message answers a line no server named — a line of
     * the client's own, a command's echo — which the server holds no
     * record of. Nothing of that line crosses: its author and words are
     * only the client's word, so the server shows the quote as a message
     * no longer kept ({@link ChatReplyReference#UNKEPT}). Only with no
     * message id.
     */
    private boolean quotesUnkept;
    /**
     * The server's message this request carries on into the channel, or
     * {@link ChatMessageIds#NONE}. A forward has no words of its own: the
     * server takes the message's words, author and place from its own
     * record, and only for a sender who may read it.
     */
    private long forwardOf = ChatMessageIds.NONE;
    private boolean malformed;

    public LostTalesChatSendPacket() {}

    /**
     * A request to forward the server's message {@code forwardOf} into a
     * conversation, addressed as a line typed there would be.
     */
    public static LostTalesChatSendPacket forward(ChatChannel channel,
                                                  String target,
                                                  int identityKind,
                                                  UUID identityCharacterId,
                                                  String targetIdentity,
                                                  UUID targetCharacterId,
                                                  long forwardOf) {
        LostTalesChatSendPacket packet = new LostTalesChatSendPacket();
        packet.channelId = channel == null ? "" : channel.getId();
        packet.target = target == null ? "" : target.trim();
        packet.identityKind = identityKind;
        packet.identityCharacterId = identityCharacterId;
        packet.targetIdentity = targetIdentity == null ? ""
                : targetIdentity.trim();
        packet.targetCharacterId = targetCharacterId;
        packet.forwardOf = forwardOf;
        packet.validate();
        return packet;
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int identityKind,
                                   UUID identityCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity, long echoNonce,
                                   UUID targetCharacterId,
                                   boolean quotesUnkept) {
        this(channel, message, references, target, identityKind,
                identityCharacterId, replyToMessageId, targetIdentity,
                echoNonce, targetCharacterId, quotesUnkept, false);
    }

    /** As above; {@code action} sends the words as an action. */
    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int identityKind,
                                   UUID identityCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity, long echoNonce,
                                   UUID targetCharacterId,
                                   boolean quotesUnkept, boolean action) {
        this.action = action;
        this.quotesUnkept = quotesUnkept;
        this.targetCharacterId = targetCharacterId;
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
                        "invalid chat request packet size");
            }
            this.channelId = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_CHANNEL_BYTES);
            this.message = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatMessageValidator.MAX_UTF8_BYTES);
            // Whether the words are an action rather than speech.
            this.action = buffer.readBoolean();
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
                } else if (kind == ChatShareKind.QUEST) {
                    decoded.add(ChatShareReference.quest(
                            LostTalesPacketCodec.readUtf8String(buffer,
                                    ChatShareReference.MAX_QUEST_REFERENCE_BYTES)));
                } else {
                    throw new LostTalesPacketCodec.DecodeException(
                            "unknown share kind");
                }
            }
            this.references = Collections.unmodifiableList(decoded);
            this.target = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_TARGET_BYTES).trim();
            this.identityKind = buffer.readUnsignedByte();
            this.identityCharacterId =
                    this.identityKind == IDENTITY_CHARACTER
                            ? new UUID(buffer.readLong(), buffer.readLong())
                            : null;
            this.replyToMessageId = buffer.readLong();
            this.targetIdentity = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_IDENTITY_BYTES).trim();
            this.echoNonce = buffer.readLong();
            // The character the whisper is addressed to, by id: a presence
            // flag and a UUID, written whole either way.
            boolean targeted = buffer.readBoolean();
            long most = buffer.readLong();
            long least = buffer.readLong();
            this.targetCharacterId = targeted ? new UUID(most, least) : null;
            // Whether it answers a line no server holds a record of; then
            // the message a forward carries on.
            this.quotesUnkept = buffer.readBoolean();
            this.forwardOf = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.action = false;
            this.quotesUnkept = false;
            this.targetCharacterId = null;
            this.target = "";
            this.references = Collections.emptyList();
            this.identityKind = IDENTITY_DEFAULT;
            this.identityCharacterId = null;
            this.replyToMessageId = ChatMessageIds.NONE;
            this.targetIdentity = "";
            this.echoNonce = 0L;
            this.forwardOf = ChatMessageIds.NONE;
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
        buffer.writeBoolean(this.action);
        buffer.writeByte(this.references.size());
        for (ChatShareReference reference : this.references) {
            buffer.writeByte(reference.getKind().getCode());
            if (reference.getKind() == ChatShareKind.ITEM) {
                buffer.writeByte(reference.getSlot());
            } else if (reference.getKind() == ChatShareKind.MARKER) {
                LostTalesPacketCodec.writeUtf8String(buffer,
                        reference.getMarkerId(),
                        ChatShareReference.MAX_MARKER_ID_BYTES);
            } else {
                LostTalesPacketCodec.writeUtf8String(buffer,
                        reference.getQuestReference(),
                        ChatShareReference.MAX_QUEST_REFERENCE_BYTES);
            }
        }
        LostTalesPacketCodec.writeUtf8String(buffer, this.target,
                MAX_TARGET_BYTES);
        buffer.writeByte(this.identityKind);
        if (this.identityKind == IDENTITY_CHARACTER) {
            buffer.writeLong(
                    this.identityCharacterId.getMostSignificantBits());
            buffer.writeLong(
                    this.identityCharacterId.getLeastSignificantBits());
        }
        buffer.writeLong(this.replyToMessageId);
        LostTalesPacketCodec.writeUtf8String(buffer, this.targetIdentity,
                MAX_IDENTITY_BYTES);
        buffer.writeLong(this.echoNonce);
        buffer.writeBoolean(this.targetCharacterId != null);
        buffer.writeLong(this.targetCharacterId == null ? 0L
                : this.targetCharacterId.getMostSignificantBits());
        buffer.writeLong(this.targetCharacterId == null ? 0L
                : this.targetCharacterId.getLeastSignificantBits());
        buffer.writeBoolean(this.quotesUnkept);
        buffer.writeLong(this.forwardOf);
    }

    private void validate() {
        if (this.replyToMessageId != ChatMessageIds.NONE
                && !ChatMessageIds.isServerId(this.replyToMessageId)
                // A quote of a line no record holds and a message id are
                // two answers to one question.
                || (this.replyToMessageId != ChatMessageIds.NONE
                        && this.quotesUnkept)
                || this.identityKind < IDENTITY_DEFAULT
                || this.identityKind > IDENTITY_CHARACTER
                || (this.identityKind == IDENTITY_CHARACTER
                        && this.identityCharacterId == null)
                || ChatChannel.fromId(this.channelId) == null
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.target, MAX_TARGET_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.targetIdentity, MAX_IDENTITY_BYTES)
                || (this.targetIdentity.length() > 0
                        && ChatChannel.fromId(this.channelId)
                                != ChatChannel.WHISPER)
                || !ChatChannel.targetFits(ChatChannel.fromId(this.channelId),
                        this.target)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.channelId, MAX_CHANNEL_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.message, ChatMessageValidator.MAX_UTF8_BYTES)
                || (this.forwardOf == ChatMessageIds.NONE
                        ? !ChatMessageValidator.isValid(this.message)
                        : !isBareForward())
                // An action is words like a message's; an empty one is
                // no action at all.
                || (this.action && !ChatAction.isValid(this.message))
                || this.references.size() > ChatShareTokenParser.MAX_TOKENS) {
            throw new IllegalArgumentException("invalid chat request");
        }
        for (ChatShareReference reference : this.references) {
            if (reference == null) {
                throw new IllegalArgumentException("invalid share reference");
            }
        }
    }

    /**
     * A forward names a message of the server's and nothing else of its
     * own: no words, no shares, no reply, no quote, no echo and no
     * action, since the line it becomes is the server's to build.
     */
    private boolean isBareForward() {
        return ChatMessageIds.isServerId(this.forwardOf)
                && this.message.length() == 0 && this.references.isEmpty()
                && this.replyToMessageId == ChatMessageIds.NONE
                && !this.quotesUnkept && this.echoNonce == 0L
                && !this.action;
    }

    public ChatChannel getChannel() {
        return ChatChannel.fromId(this.channelId);
    }
    public String getMessage() { return this.message; }
    /** Whether the words are an action ({@code /me}) rather than speech. */
    public boolean isAction() { return this.action; }
    /** References in token order; may be shorter than the token list. */
    public List<ChatShareReference> getReferences() { return this.references; }
    /** The whisper's account name, or a fellowship line's fellowship id; empty otherwise. */
    public String getTarget() { return this.target; }
    /** The fellowship a fellowship line is for; null for every other line. */
    public UUID getFellowshipId() {
        return getChannel() == ChatChannel.FELLOWSHIP ? ChatFellowship.idOf(this.target) : null;
    }
    /** The identity of that account addressed; empty for its own. */
    public String getTargetIdentity() { return this.targetIdentity; }
    /** The character a whisper is addressed to by id; null for by name or account. */
    public UUID getTargetCharacterId() { return this.targetCharacterId; }
    /** The sender's own name for this message; zero for none. */
    public long getEchoNonce() { return this.echoNonce; }
    /** One of the {@code IDENTITY_*} constants. */
    public int getIdentityKind() { return this.identityKind; }
    /** The asked-for roster character; null unless the kind names one. */
    public UUID getIdentityCharacterId() {
        return this.identityCharacterId;
    }
    /** The message this one asks to reply to; {@code NONE} for none. */
    public long getReplyToMessageId() {
        return this.replyToMessageId;
    }
    /** Whether this one answers a line no server holds a record of. */
    public boolean quotesUnkept() { return this.quotesUnkept; }
    /** The message a forward carries on; {@code NONE} for a line of its own. */
    public long getForwardOf() { return this.forwardOf; }
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
                            LostTalesChatService.send(livePlayer, message);
                        }
                    });
            return null;
        }
    }
}
