package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatChannel;
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
    /** A presence flag and a UUID: the appended target-character tail. */
    static final int TARGET_ID_TAIL_BYTES = 1 + 2 * 8;
    /**
     * Whose line an unnamed quote is, as far as the sender can say: a
     * line of somebody the server cannot vouch for, which is quoted
     * without a head.
     */
    public static final int QUOTE_OTHER = 0;
    /**
     * A line of the sender's own — the echo of a command they ran, say —
     * which the server draws with the head it signs the sender with, and
     * only when the quote names an identity of theirs.
     */
    public static final int QUOTE_OWN = 1;
    /**
     * A line of the Server or of the Client itself, which wears the
     * console mark: a mark that claims no more than the name beside it.
     */
    public static final int QUOTE_SYSTEM = 2;

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
     * For a whisper, the id of the character it is addressed to, when the
     * client knows it; null addresses the target by name, or their account.
     * The server resolves it against the target's own roster before the
     * line is filed under it. Appended; null from an older client.
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
    /**
     * The quote of a line nobody named — an announcement, a death
     * message, a console notice, a command's echo — that this message
     * answers: its author and its words, as this client saw them, since
     * no server ever distributed the line and none can resolve it. Only
     * with no message id; empty otherwise. The server bounds and strips
     * both like any other text off the wire.
     */
    private String quoteAuthor = "";
    private String quoteExcerpt = "";
    /**
     * Whose that line is ({@link #QUOTE_OTHER}, {@link #QUOTE_OWN} or
     * {@link #QUOTE_SYSTEM}): a claim the server checks against what it
     * knows before it draws any head for it. Only with a quote.
     */
    private int quoteSource = QUOTE_OTHER;
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
        this(channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, targetIdentity, echoNonce,
                null);
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity, long echoNonce,
                                   UUID targetCharacterId) {
        this(channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, targetIdentity,
                echoNonce, targetCharacterId, "", "");
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity, long echoNonce,
                                   UUID targetCharacterId,
                                   String quoteAuthor, String quoteExcerpt) {
        this(channel, message, references, target, appearanceKind,
                appearanceCharacterId, replyToMessageId, targetIdentity,
                echoNonce, targetCharacterId, quoteAuthor, quoteExcerpt,
                QUOTE_OTHER);
    }

    public LostTalesChatSendPacket(ChatChannel channel, String message,
                                   List<ChatShareReference> references,
                                   String target, int appearanceKind,
                                   UUID appearanceCharacterId,
                                   long replyToMessageId,
                                   String targetIdentity, long echoNonce,
                                   UUID targetCharacterId,
                                   String quoteAuthor, String quoteExcerpt,
                                   int quoteSource) {
        this.quoteAuthor = quoteAuthor == null ? "" : quoteAuthor.trim();
        this.quoteExcerpt = quoteExcerpt == null ? "" : quoteExcerpt.trim();
        this.quoteSource = this.quoteAuthor.length() == 0 ? QUOTE_OTHER
                : quoteSource;
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
            // Appended: the character the whisper is addressed to, by id,
            // a fixed tail so a payload cut short inside it stays
            // malformed rather than reading as an older layout.
            this.targetCharacterId = null;
            boolean targetTail = false;
            if (buffer.readableBytes() >= TARGET_ID_TAIL_BYTES) {
                boolean present = buffer.readBoolean();
                long most = buffer.readLong();
                long least = buffer.readLong();
                this.targetCharacterId = present ? new UUID(most, least) : null;
                targetTail = true;
            }
            // Appended after that: the quote of a line nobody named,
            // author then words, written only when there is one. It can
            // only follow a whole target tail, so a payload cut short
            // inside that tail is never read as a quote.
            this.quoteAuthor = "";
            this.quoteExcerpt = "";
            this.quoteSource = QUOTE_OTHER;
            if (targetTail && buffer.readableBytes() >= 1) {
                this.quoteAuthor = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatReplyReference.MAX_AUTHOR_BYTES).trim();
                this.quoteExcerpt = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatReplyReference.MAX_EXCERPT_BYTES).trim();
                // Appended after the quote: whose line it is. A quote
                // written before it names nobody the server could vouch
                // for, and is drawn without a head as it was then.
                if (buffer.readableBytes() >= 1) {
                    this.quoteSource = buffer.readUnsignedByte();
                }
            }
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.quoteAuthor = "";
            this.quoteExcerpt = "";
            this.quoteSource = QUOTE_OTHER;
            this.targetCharacterId = null;
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
        buffer.writeBoolean(this.targetCharacterId != null);
        buffer.writeLong(this.targetCharacterId == null ? 0L
                : this.targetCharacterId.getMostSignificantBits());
        buffer.writeLong(this.targetCharacterId == null ? 0L
                : this.targetCharacterId.getLeastSignificantBits());
        if (this.quoteAuthor.length() > 0) {
            LostTalesPacketCodec.writeUtf8String(buffer, this.quoteAuthor,
                    ChatReplyReference.MAX_AUTHOR_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, this.quoteExcerpt,
                    ChatReplyReference.MAX_EXCERPT_BYTES);
            buffer.writeByte(this.quoteSource);
        }
    }

    /**
     * Whose line an unnamed quote is, from the head the quote wears on
     * the sender's own screen: a line of the Server's or the Client's,
     * one of the sender's own, or somebody else's — an NPC included,
     * whose portrait means nothing to anyone else.
     */
    public static int quoteSourceOf(ChatReplyReference reply, UUID sender) {
        if (reply == null || !reply.hasHead() || reply.isNpcLine()) {
            return QUOTE_OTHER;
        }
        if (LostTalesChatMessagePacket.isSystemSender(reply.getSenderId())) {
            return QUOTE_SYSTEM;
        }
        return reply.getSenderId().equals(sender) ? QUOTE_OWN : QUOTE_OTHER;
    }

    private void validate() {
        if (this.quoteSource < QUOTE_OTHER || this.quoteSource > QUOTE_SYSTEM
                || (this.quoteSource != QUOTE_OTHER
                        && this.quoteAuthor.length() == 0)
                || this.replyToMessageId != ChatMessageIds.NONE
                && !ChatMessageIds.isServerId(this.replyToMessageId)
                // A quote of an unnamed line and a message id are two
                // answers to one question.
                || (this.replyToMessageId != ChatMessageIds.NONE
                        && (this.quoteAuthor.length() > 0
                                || this.quoteExcerpt.length() > 0))
                || (this.quoteAuthor.length() == 0
                        && this.quoteExcerpt.length() > 0)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.quoteAuthor, ChatReplyReference.MAX_AUTHOR_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.quoteExcerpt,
                        ChatReplyReference.MAX_EXCERPT_BYTES)
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
    /** The character a whisper is addressed to by id; null for by name or account. */
    public UUID getTargetCharacterId() { return this.targetCharacterId; }
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
    /** Who signed the unnamed line this one quotes; empty for none. */
    public String getQuoteAuthor() { return this.quoteAuthor; }
    /** What the unnamed line this one quotes said; empty for none. */
    public String getQuoteExcerpt() { return this.quoteExcerpt; }
    /** Whose that line is, as the sender claims: one of the {@code QUOTE_*} constants. */
    public int getQuoteSource() { return this.quoteSource; }
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
