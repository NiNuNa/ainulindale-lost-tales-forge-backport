package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReactionSummary;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.chat.share.ChatShowcase;
import com.ninuna.losttales.chat.ChatNarrator;
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
    /**
     * The bridge's own sender id: derived from a name no account owns.
     * It is the author every line from Discord is recorded under, which
     * is what lets the bridge — and nobody else — edit or take such a
     * line back, and its upper half is the namespace every Discord
     * member's own sender id lives in ({@link #discordSenderId}).
     */
    public static final UUID DISCORD_SENDER_ID = UUID.nameUUIDFromBytes(
            "losttales:discord".getBytes(
                    java.nio.charset.Charset.forName("UTF-8")));

    /**
     * The sender id a line from one Discord member carries: the bridge's
     * namespace over the member's own Discord id, which is a 64-bit
     * snowflake and fits the lower half exactly. Two members never share
     * one, and the same member always has the same, so a client can
     * ignore them and the server can mute them the way it does an
     * account — while {@link #isDiscordSender} still tells every one of
     * them from a player. A member whose id cannot be read gets the
     * bridge's own id, the one every Discord line carried before ids
     * were kept.
     */
    public static UUID discordSenderId(String discordUserId) {
        if (discordUserId == null) {
            return DISCORD_SENDER_ID;
        }
        try {
            return new UUID(DISCORD_SENDER_ID.getMostSignificantBits(),
                    Long.parseUnsignedLong(discordUserId.trim()));
        } catch (NumberFormatException malformed) {
            return DISCORD_SENDER_ID;
        }
    }

    /** Whether a sender id names the Discord bridge or one of its members. */
    public static boolean isDiscordSender(UUID senderId) {
        return senderId != null && senderId.getMostSignificantBits()
                == DISCORD_SENDER_ID.getMostSignificantBits();
    }

    /**
     * The sender id of the server's own lines: a command's answer, and
     * the Server Console's word on who ran what. Nobody's account, so
     * it is never ignored, muted, whispered or looked up a skin for; a
     * client shows it with the console mark for a head. Such lines are
     * built on the client from what the server sent and never travel
     * as messages, so no packet off the wire is expected to carry it.
     */
    public static final UUID SERVER_SENDER_ID = UUID.nameUUIDFromBytes(
            "losttales:server".getBytes(
                    java.nio.charset.Charset.forName("UTF-8")));

    /** Whether a sender id names the server itself. */
    public static boolean isServerSender(UUID senderId) {
        return SERVER_SENDER_ID.equals(senderId);
    }

    /**
     * The sender id of the client's own lines: what the game prints
     * for itself without any server saying it — a game mode notice, a
     * saved screenshot, another mod's local print. Shown like the
     * server's lines, under its own name, so a reader can tell the two
     * apart. Never travels: nobody but this client ever builds one.
     */
    public static final UUID CLIENT_SENDER_ID = UUID.nameUUIDFromBytes(
            "losttales:client".getBytes(
                    java.nio.charset.Charset.forName("UTF-8")));

    /** Whether a sender id names the client itself. */
    public static boolean isClientSender(UUID senderId) {
        return CLIENT_SENDER_ID.equals(senderId);
    }

    /**
     * Whether a sender id names the server or the client rather than
     * anyone: a line with no account behind it, no skin, no card of a
     * person, no whisper, nothing to ignore or mute.
     */
    public static boolean isSystemSender(UUID senderId) {
        return isServerSender(senderId) || isClientSender(senderId);
    }


    /**
     * The Discord id of the member a sender id stands for; empty for a
     * player, and for the bridge's own id.
     */
    public static String discordUserIdOf(UUID senderId) {
        if (!isDiscordSender(senderId) || DISCORD_SENDER_ID.equals(senderId)) {
            return "";
        }
        return Long.toUnsignedString(senderId.getLeastSignificantBits());
    }

    /**
     * The most a server line's own component takes, as the JSON the
     * game itself sends chat in: an achievement with its hover, a
     * death naming a weapon with its item, a join. A line past it
     * travels as its words alone.
     */
    public static final int MAX_BODY_BYTES = 8192;
    /** An optional id on the wire: a presence flag and a UUID, written whole either way. */
    static final int IDENTITY_ID_TAIL_BYTES = 1 + 16;
    /** The most one line takes on the wire; the history batch reads it too. */
    public static final int MAX_PACKET_BYTES = 2048
            + ChatMessageValidator.MAX_UTF8_BYTES
            + ChatShowcase.MAX_TOTAL_BYTES
            + ChatReplyReference.MAX_AUTHOR_BYTES
            + ChatReplyReference.MAX_EXCERPT_BYTES
            // The quoted author's name colour, at the payload's tail.
            + 4
            // The quoted sender's head: id, account flag and skin.
            + 1 + 16 + 1 + 4 + ChatReplyReference.MAX_SKIN_ID_BYTES
            // A server line's own component, and the players it names.
            + 4 + MAX_BODY_BYTES
            + 1 + ChatNamedPlayer.MAX_PER_LINE * (IDENTITY_ID_TAIL_BYTES
                    + 4 + ChatNamedPlayer.MAX_ACCOUNT_BYTES
                    + IDENTITY_ID_TAIL_BYTES
                    + 4 + ChatNamedPlayer.MAX_IDENTITY_BYTES
                    + 4 + ChatNamedPlayer.MAX_SKIN_ID_BYTES)
            // The reactions on the line as this reader is shown them.
            + LostTalesChatReactionCodec.MAX_BYTES;
    private static final int MAX_CHANNEL_BYTES = 16;
    private static final int MAX_IDENTITY_BYTES = 256;
    private static final int MAX_ACCOUNT_NAME_BYTES = 64;
    private static final int MAX_TITLE_BYTES = 256;
    private static final int MAX_SKIN_ID_BYTES = 128;
    private static final int MAX_FACTION_NAME_BYTES = 128;
    /** A scope value is a normalized faction id; well past any of them. */
    private static final int MAX_SCOPE_VALUE_BYTES = 128;
    /** The most bytes a tab id may take: the console's own bound on a context. */
    private static final int MAX_TAB_ID_BYTES = ChatConsoleEvent.MAX_CONTEXT_LENGTH;

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
    /**
     * For a whisper, the identity of that other party the conversation
     * is with — the character they were speaking as, or their account's
     * own name. What the tab is kept under, so one player's characters
     * are separate conversations.
     */
    private String partnerIdentity = "";
    /**
     * The sender's own name for this message, handed back so they can
     * recognise the line they already have on screen. Set only on the
     * copy that goes to the sender; every other copy carries zero.
     */
    private long echoNonce;
    /**
     * The sender's {@link ChatAccountRole}s as a mask, tagged ahead of
     * the name. Set only on lines of the account-identity channels,
     * where the server also colours the name by the primary role; a line
     * of an in-character channel carries none whoever speaks it
     * ({@code ChatRolePresentation}).
     */
    private int roles;
    /**
     * Whether the line wears the account identity. The channel
     * does not decide this — a character may speak in OOC and
     * the account in Global — so the line says it itself; heads and skin
     * caching follow it.
     */
    private boolean accountLine;
    /**
     * The stable id of the character the line wears, or null for a line
     * worn by the account or by a sender who has no characters (the
     * Discord bridge). Names are presentation; this is what a client
     * keys conversations, mentions and cards by.
     */
    private UUID identityCharacterId;
    /**
     * Which conversation on a scoped channel the line belongs to: the
     * faction it was spoken to, normalized. Empty on every channel that
     * is one conversation. The client files the line under the tab of
     * the identity that conversation is read as, so an account with
     * characters in two factions keeps the two apart.
     */
    private String scopeValue = "";
    /**
     * For a line of the server's own that answers a command, the id of
     * the tab the command was typed in, as the client reported it: the
     * client files the answer under that tab, wherever the line's
     * channel would have put it. Empty on every other line, and carried
     * only by a system sender.
     */
    private String tabId = "";
    /**
     * For a whisper, the character of the receiving party this copy is
     * held as — the sender's worn character on the sender's copy, the
     * addressed character on the partner's — or null for the account.
     * What the client files the conversation under, so one player's
     * characters keep separate threads.
     */
    private UUID ownCharacterId;
    /**
     * For a whisper, the character of the other party the conversation
     * is with, or null for their account. What a reply is addressed to
     * by id rather than by name.
     */
    private UUID partnerCharacterId;
    /**
     * The server's name for this message, or {@link ChatMessageIds#NONE}
     * for a line nobody can name. Anything that refers to a message
     * afterwards refers to it by this rather than by a chat line id,
     * which is each client's own and reused as its history trims.
     */
    private long messageId;
    /**
     * The message this line replies to, quoted as the server resolved
     * it; {@link ChatReplyReference#NONE} when the line replies to
     * nothing. The quote travels here rather than being looked up by
     * each client, so every recipient is shown the same one whether or
     * not they hold the original.
     */
    private ChatReplyReference reply = ChatReplyReference.NONE;
    /**
     * A server line's own component as the game's chat JSON — its
     * hover, its colours, its links — so a replay from the history
     * shows an achievement or a death exactly as the live line was
     * shown; empty for every line of a player's, and for a server line
     * whose component would not fit.
     */
    private String bodyJson = "";
    /**
     * The players a server line names, as the server knew them when
     * the line was said, so a replay names each by the identity they
     * were playing whether or not they are still online.
     */
    private List<ChatNamedPlayer> namedPlayers = Collections.emptyList();
    /**
     * The reactions on the line as the reader it is sent to is shown
     * them: added by the server to the copy it hands a reader, never
     * part of what it keeps, since "is it mine" is each reader's own.
     */
    private ChatReactionSummary reactions = ChatReactionSummary.EMPTY;
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
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, 0);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles) {
        this(channel, senderId, identityName, accountName, title,
                titleColor, nameColor, message, timestampMillis, skinId,
                showcases, factionName, partner, roles,
                // A caller that does not say takes the channel's word:
                // out of character reads as the account, in character as
                // a character. The server always says.
                channel != null && ChatRolePresentation.showsRoles(channel));
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, roles, accountLine,
                ChatMessageIds.NONE);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, roles, accountLine, messageId, null);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId, ChatReplyReference reply) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, roles, accountLine, messageId, reply,
                "");
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId, ChatReplyReference reply,
            String partnerIdentity) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, roles, accountLine, messageId, reply,
                partnerIdentity, 0L);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId, ChatReplyReference reply,
            String partnerIdentity, long echoNonce) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, roles, accountLine, messageId, reply,
                partnerIdentity, echoNonce, null);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId, ChatReplyReference reply,
            String partnerIdentity, long echoNonce,
            UUID identityCharacterId) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases,
                factionName, partner, roles, accountLine, messageId, reply,
                partnerIdentity, echoNonce, identityCharacterId, null, null);
    }

    public LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId, ChatReplyReference reply,
            String partnerIdentity, long echoNonce,
            UUID identityCharacterId, UUID ownCharacterId,
            UUID partnerCharacterId) {
        this(channel, senderId, identityName, accountName, title, titleColor,
                nameColor, message, timestampMillis, skinId, showcases, factionName,
                partner, roles, accountLine, messageId, reply, partnerIdentity,
                echoNonce, identityCharacterId, ownCharacterId, partnerCharacterId,
                "");
    }

    private LostTalesChatMessagePacket(
            ChatChannel channel, UUID senderId, String identityName,
            String accountName, String title,
            int titleColor, int nameColor,
            String message, long timestampMillis, String skinId,
            List<ChatShowcase> showcases, String factionName,
            String partner, int roles, boolean accountLine,
            long messageId, ChatReplyReference reply,
            String partnerIdentity, long echoNonce,
            UUID identityCharacterId, UUID ownCharacterId,
            UUID partnerCharacterId, String scopeValue) {
        this.scopeValue = scopedChannel(channel) && scopeValue != null
                ? scopeValue.trim() : "";
        this.identityCharacterId = accountLine ? null : identityCharacterId;
        boolean whisper = channel == ChatChannel.WHISPER;
        this.ownCharacterId = whisper ? ownCharacterId : null;
        this.partnerCharacterId = whisper ? partnerCharacterId : null;
        this.echoNonce = echoNonce;
        this.partnerIdentity = partnerIdentity == null ? ""
                : partnerIdentity.trim();
        this.reply = reply == null ? ChatReplyReference.NONE : reply;
        this.messageId = messageId;
        this.accountLine = accountLine;
        this.roles = roles;
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
            this.accountLine = buffer.readBoolean();
            this.messageId = buffer.readLong();
            long replyTo = buffer.readLong();
            // The quote's words follow these two on the wire.
            this.partnerIdentity = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_IDENTITY_BYTES).trim();
            this.echoNonce = buffer.readLong();
            this.reply = replyTo == ChatMessageIds.NONE
                    ? ChatReplyReference.NONE
                    : ChatReplyReference.of(replyTo,
                            LostTalesPacketCodec.readUtf8String(buffer,
                                    ChatReplyReference.MAX_AUTHOR_BYTES),
                            LostTalesPacketCodec.readUtf8String(buffer,
                                    ChatReplyReference.MAX_EXCERPT_BYTES));
            if (ChatMessageIds.isLocalId(replyTo)
                    || (replyTo != ChatMessageIds.NONE
                            && !this.reply.exists())) {
                // A local id is a client's own name for a line it wrote
                // itself; one arriving here names nothing this server
                // ever distributed.
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat reply reference");
            }
            if (this.messageId < ChatMessageIds.NONE) {
                // Negative ids are the receiving client's own, for lines
                // it wrote itself; one arriving over the wire is not a
                // message this server ever named.
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat message id");
            }
            // The sender's role mask.
            this.roles = buffer.readInt();
            // The worn character's id, then a whisper's two conversation
            // ids, each a presence flag and a UUID.
            this.identityCharacterId = readOptionalUuid(buffer);
            if (this.identityCharacterId != null && this.accountLine) {
                throw new LostTalesPacketCodec.DecodeException(
                        "an account line names a character");
            }
            this.ownCharacterId = readOptionalUuid(buffer);
            this.partnerCharacterId = readOptionalUuid(buffer);
            if ((this.ownCharacterId != null || this.partnerCharacterId != null)
                    && ChatChannel.fromId(this.channelId) != ChatChannel.WHISPER) {
                throw new LostTalesPacketCodec.DecodeException(
                        "conversation ids on a line that is not a whisper");
            }
            // Which conversation on a scoped channel the line belongs to;
            // empty on a channel that is only one.
            String scope = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_SCOPE_VALUE_BYTES);
            if (scope.length() > 0 && !scopedChannel(getChannel())) {
                throw new LostTalesPacketCodec.DecodeException(
                        "a conversation on a channel that has only one");
            }
            this.scopeValue = scope;
            // The colour the quoted author's name was drawn in; a line that
            // names no message carries, in its place, the quote of a line
            // nobody named: its author, its words and the author's colour.
            // An empty author is no quote.
            if (this.reply.isAnchored()) {
                this.reply = ChatReplyReference.of(
                        this.reply.getMessageId(), this.reply.getAuthor(),
                        this.reply.getExcerpt(), buffer.readInt());
            } else {
                String quoteAuthor = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatReplyReference.MAX_AUTHOR_BYTES);
                String quoteExcerpt = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatReplyReference.MAX_EXCERPT_BYTES);
                int quoteColor = buffer.readInt();
                this.reply = ChatReplyReference.unanchored(quoteAuthor,
                        quoteExcerpt, quoteColor);
            }
            // The head the quote wears — a sender id, whether the line
            // wore the account, its skin — then a server line's own
            // component and the players it names.
            UUID quotedSender = readOptionalUuid(buffer);
            boolean quotedAccountLine = buffer.readBoolean();
            String quotedSkin = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatReplyReference.MAX_SKIN_ID_BYTES);
            if (quotedSender != null) {
                this.reply = this.reply.withHead(quotedSender,
                        quotedAccountLine, quotedSkin);
            }
            String body = LostTalesPacketCodec.readUtf8String(
                    buffer, MAX_BODY_BYTES);
            if (body.length() > 0 && !isSystemSender(this.senderId)) {
                throw new LostTalesPacketCodec.DecodeException(
                        "a component on a line that is not the server's");
            }
            this.bodyJson = body;
            int named = LostTalesPacketCodec.readCount(buffer,
                    ChatNamedPlayer.MAX_PER_LINE, "named players");
            List<ChatNamedPlayer> players =
                    new ArrayList<ChatNamedPlayer>(named);
            for (int index = 0; index < named; index++) {
                UUID namedId = readOptionalUuid(buffer);
                String namedAccount = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatNamedPlayer.MAX_ACCOUNT_BYTES);
                UUID namedCharacter = readOptionalUuid(buffer);
                String namedIdentity = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatNamedPlayer.MAX_IDENTITY_BYTES);
                String namedSkin = LostTalesPacketCodec.readUtf8String(
                        buffer, ChatNamedPlayer.MAX_SKIN_ID_BYTES);
                ChatNamedPlayer player = new ChatNamedPlayer(namedId,
                        namedAccount, namedCharacter, namedIdentity,
                        namedSkin);
                if (!player.isValid()) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "a named player without an account");
                }
                players.add(player);
            }
            this.namedPlayers = players.isEmpty()
                    ? Collections.<ChatNamedPlayer>emptyList()
                    : Collections.unmodifiableList(players);
            // The reactions as this reader is shown them, then the tab a
            // command's answer is filed under.
            this.reactions = LostTalesChatReactionCodec.read(buffer);
            this.tabId = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_TAB_ID_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.bodyJson = "";
            this.namedPlayers = Collections.emptyList();
            this.reactions = ChatReactionSummary.EMPTY;
            this.showcases = Collections.emptyList();
            this.factionName = "";
            this.partner = "";
            this.roles = 0;
            this.accountLine = false;
            this.identityCharacterId = null;
            this.ownCharacterId = null;
            this.partnerCharacterId = null;
            this.scopeValue = "";
            this.tabId = "";
            this.messageId = ChatMessageIds.NONE;
            this.reply = ChatReplyReference.NONE;
            this.partnerIdentity = "";
            this.echoNonce = 0L;
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
        if (kind == ChatShareKind.QUEST) {
            String reference = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatShowcase.MAX_QUEST_REFERENCE_BYTES);
            String title = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatShowcase.MAX_QUEST_TITLE_BYTES);
            String category = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatShowcase.MAX_QUEST_CATEGORY_BYTES);
            String objective = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES);
            String reward = LostTalesPacketCodec.readUtf8String(buffer,
                    ChatShowcase.MAX_QUEST_REWARD_BYTES);
            return ChatShowcase.quest(tokenIndex, reference, title, category,
                    objective, reward, buffer.readBoolean());
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
            } else if (showcase.getKind() == ChatShareKind.MARKER) {
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
            } else {
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getQuestReference(),
                        ChatShowcase.MAX_QUEST_REFERENCE_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getQuestTitle(),
                        ChatShowcase.MAX_QUEST_TITLE_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getQuestCategory(),
                        ChatShowcase.MAX_QUEST_CATEGORY_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getQuestObjective(),
                        ChatShowcase.MAX_QUEST_OBJECTIVE_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer,
                        showcase.getQuestReward(),
                        ChatShowcase.MAX_QUEST_REWARD_BYTES);
                buffer.writeBoolean(showcase.isQuestJoinable());
            }
        }
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.factionName, MAX_FACTION_NAME_BYTES);
        LostTalesPacketCodec.writeUtf8String(
                buffer, this.partner, MAX_ACCOUNT_NAME_BYTES);
        buffer.writeBoolean(this.accountLine);
        buffer.writeLong(this.messageId);
        buffer.writeLong(this.reply.getMessageId());
        LostTalesPacketCodec.writeUtf8String(buffer, this.partnerIdentity,
                MAX_IDENTITY_BYTES);
        buffer.writeLong(this.echoNonce);
        if (this.reply.isAnchored()) {
            LostTalesPacketCodec.writeUtf8String(buffer,
                    this.reply.getAuthor(),
                    ChatReplyReference.MAX_AUTHOR_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    this.reply.getExcerpt(),
                    ChatReplyReference.MAX_EXCERPT_BYTES);
        }
        buffer.writeInt(this.roles);
        writeOptionalUuid(buffer, this.identityCharacterId);
        writeOptionalUuid(buffer, this.ownCharacterId);
        writeOptionalUuid(buffer, this.partnerCharacterId);
        LostTalesPacketCodec.writeUtf8String(buffer, this.scopeValue,
                MAX_SCOPE_VALUE_BYTES);
        if (this.reply.isAnchored()) {
            buffer.writeInt(this.reply.getAuthorColor());
        } else {
            // A quote of a line nobody named: author, words and colour.
            // Written whether or not there is one, so the tail behind
            // it stands at one place: an empty author is no quote.
            LostTalesPacketCodec.writeUtf8String(buffer,
                    this.reply.getAuthor(),
                    ChatReplyReference.MAX_AUTHOR_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer,
                    this.reply.getExcerpt(),
                    ChatReplyReference.MAX_EXCERPT_BYTES);
            buffer.writeInt(this.reply.getAuthorColor());
        }
        // The quote's head, the server line's component, the players it
        // names: see fromBytes.
        writeOptionalUuid(buffer, this.reply.getSenderId());
        buffer.writeBoolean(this.reply.isAccountLine());
        LostTalesPacketCodec.writeUtf8String(buffer, this.reply.getSkinId(),
                ChatReplyReference.MAX_SKIN_ID_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.bodyJson,
                MAX_BODY_BYTES);
        LostTalesPacketCodec.writeCount(buffer, this.namedPlayers.size(),
                ChatNamedPlayer.MAX_PER_LINE, "named players");
        for (ChatNamedPlayer player : this.namedPlayers) {
            writeOptionalUuid(buffer, player.getPlayerId());
            LostTalesPacketCodec.writeUtf8String(buffer, player.getAccount(),
                    ChatNamedPlayer.MAX_ACCOUNT_BYTES);
            writeOptionalUuid(buffer, player.getCharacterId());
            LostTalesPacketCodec.writeUtf8String(buffer,
                    player.getIdentityName(),
                    ChatNamedPlayer.MAX_IDENTITY_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, player.getSkinId(),
                    ChatNamedPlayer.MAX_SKIN_ID_BYTES);
        }
        LostTalesChatReactionCodec.write(buffer, this.reactions);
        LostTalesPacketCodec.writeUtf8String(buffer, this.tabId, MAX_TAB_ID_BYTES);
    }

    /** A presence flag and a UUID, always {@link #IDENTITY_ID_TAIL_BYTES} long. */
    private static void writeOptionalUuid(ByteBuf buffer, UUID value) {
        buffer.writeBoolean(value != null);
        buffer.writeLong(value == null ? 0L : value.getMostSignificantBits());
        buffer.writeLong(value == null ? 0L : value.getLeastSignificantBits());
    }

    private static UUID readOptionalUuid(ByteBuf buffer) {
        boolean present = buffer.readBoolean();
        long most = buffer.readLong();
        long least = buffer.readLong();
        return present ? new UUID(most, least) : null;
    }

    /**
     * Whether the line passes the checks {@link #toBytes} makes: what a
     * server asks before handing a kept line to the encoder, since an
     * exception there ends the connection. A line built under a channel
     * or a role the server no longer has answers false.
     */
    public boolean isWellFormed() {
        try {
            validate();
            return true;
        } catch (RuntimeException malformed) {
            return false;
        }
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
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.reply.getSkinId(),
                        ChatReplyReference.MAX_SKIN_ID_BYTES)
                || !LostTalesPacketCodec.isUtf8WithinLimit(
                        this.bodyJson, MAX_BODY_BYTES)
                || (this.bodyJson.length() > 0
                        && !isSystemSender(this.senderId))
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.tabId,
                        MAX_TAB_ID_BYTES)
                || !ChatConsoleEvent.isContext(this.tabId)
                || (this.tabId.length() > 0 && !isSystemSender(this.senderId))
                || this.namedPlayers.size() > ChatNamedPlayer.MAX_PER_LINE
                || (ChatChannel.fromId(this.channelId) == ChatChannel.WHISPER
                        && this.partner.length() == 0)
                || this.timestampMillis <= 0L
                || !ChatAccountRole.isValidMask(this.roles)
                || this.showcases.size() > ChatShareTokenParser.MAX_TOKENS
                || ChatShowcase.serializedBytes(this.showcases)
                        > ChatShowcase.MAX_TOTAL_BYTES) {
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

    /**
     * The same message saying something else: what an edit hands the
     * client so the line can be built again exactly as it was, down to
     * the colours and the head, with only the words replaced.
     */
    public LostTalesChatMessagePacket withMessage(String message) {
        return rebuild(message, this.partner, this.partnerIdentity, this.reply,
                this.echoNonce);
    }

    /**
     * The same line quoting differently: what a client rebuilds a reply
     * with when the message it quotes has been edited under it.
     */
    public LostTalesChatMessagePacket withReply(ChatReplyReference reply) {
        return rebuild(this.message, this.partner, this.partnerIdentity, reply,
                this.echoNonce);
    }

    /**
     * The same whisper as the other party is sent it: filed under the
     * partner and identity that party sees the conversation as.
     */
    public LostTalesChatMessagePacket withPartner(String partner,
                                                  String partnerIdentity) {
        return rebuild(this.message, partner, partnerIdentity, this.reply,
                this.echoNonce);
    }

    /**
     * The same whisper held as, and addressed to, the given characters:
     * the receiving party's own, and the other party's; null for an
     * account on either side.
     */
    public LostTalesChatMessagePacket withConversation(UUID ownCharacterId,
                                                       UUID partnerCharacterId) {
        return rebuild(this.message, this.partner, this.partnerIdentity, this.reply,
                this.echoNonce, ownCharacterId, partnerCharacterId);
    }

    private LostTalesChatMessagePacket rebuild(String message, String partner,
                                               String partnerIdentity,
                                               ChatReplyReference reply,
                                               long echoNonce) {
        return rebuild(message, partner, partnerIdentity, reply, echoNonce,
                this.ownCharacterId, this.partnerCharacterId);
    }

    /** Every field as it is but for the ones named; the one place they are all listed. */
    private LostTalesChatMessagePacket rebuild(String message, String partner,
                                               String partnerIdentity,
                                               ChatReplyReference reply,
                                               long echoNonce, UUID ownCharacterId,
                                               UUID partnerCharacterId) {
        return carrying(new LostTalesChatMessagePacket(getChannel(), this.senderId,
                this.identityName, this.accountName, this.title,
                this.titleColor, this.nameColor, message,
                this.timestampMillis, this.skinId, this.showcases,
                this.factionName, partner, this.roles,
                this.accountLine, this.messageId, reply,
                partnerIdentity, echoNonce, this.identityCharacterId,
                ownCharacterId, partnerCharacterId, this.scopeValue));
    }

    /**
     * The copy with this line's own component and named players: the
     * one place the appended presentation tail is carried across a
     * copy, so every {@code with} form keeps it.
     */
    private LostTalesChatMessagePacket carrying(LostTalesChatMessagePacket copy) {
        copy.bodyJson = this.bodyJson;
        copy.namedPlayers = this.namedPlayers;
        copy.reactions = this.reactions;
        copy.tabId = this.tabId;
        return copy;
    }

    /**
     * The same line filed under the tab {@code tabId} names on the
     * client: what a line of the server's own that answers a command
     * carries. Anything but a system sender keeps none, whatever it is
     * handed, and so does an id that would not be a context.
     */
    public LostTalesChatMessagePacket withTabId(String tabId) {
        LostTalesChatMessagePacket copy = withNameColor(this.nameColor);
        String id = tabId == null ? "" : tabId.trim();
        copy.tabId = isSystemSender(this.senderId)
                && ChatConsoleEvent.isContext(id)
                && LostTalesPacketCodec.isUtf8WithinLimit(id, MAX_TAB_ID_BYTES)
                ? id : "";
        return copy;
    }

    /** The tab a command's answer is filed under; empty for every other line. */
    public String getTabId() { return this.tabId; }

    /** The same line wearing {@code reactions}, as one reader is shown them. */
    public LostTalesChatMessagePacket withReactions(ChatReactionSummary reactions) {
        LostTalesChatMessagePacket copy = withNameColor(this.nameColor);
        copy.reactions = reactions == null ? ChatReactionSummary.EMPTY
                : reactions;
        return copy;
    }

    /** The reactions on the line as its reader is shown them; never null. */
    public ChatReactionSummary getReactions() { return this.reactions; }

    /**
     * The same line carrying its own component as the game's chat
     * JSON, and the players it names as the server knew them. Only a
     * line of the server's own may carry a component; anything else
     * keeps none, whatever it is handed.
     */
    public LostTalesChatMessagePacket withServerBody(
            String componentJson, List<ChatNamedPlayer> named) {
        LostTalesChatMessagePacket copy = carrying(withNameColor(
                this.nameColor));
        String body = componentJson == null ? "" : componentJson;
        copy.bodyJson = isSystemSender(this.senderId)
                && LostTalesPacketCodec.isUtf8WithinLimit(body,
                        MAX_BODY_BYTES) ? body : "";
        List<ChatNamedPlayer> players = new ArrayList<ChatNamedPlayer>();
        for (int index = 0; named != null && index < named.size()
                && players.size() < ChatNamedPlayer.MAX_PER_LINE; index++) {
            ChatNamedPlayer player = named.get(index);
            if (player != null && player.isValid()
                    && LostTalesPacketCodec.isUtf8WithinLimit(
                            player.getAccount(),
                            ChatNamedPlayer.MAX_ACCOUNT_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(
                            player.getIdentityName(),
                            ChatNamedPlayer.MAX_IDENTITY_BYTES)
                    && LostTalesPacketCodec.isUtf8WithinLimit(
                            player.getSkinId(),
                            ChatNamedPlayer.MAX_SKIN_ID_BYTES)) {
                players.add(player);
            }
        }
        copy.namedPlayers = players.isEmpty()
                ? Collections.<ChatNamedPlayer>emptyList()
                : Collections.unmodifiableList(players);
        return copy;
    }

    /**
     * The same line naming {@code named} as the server knows them now:
     * the players a message's {@code @names} reach as it is said, kept
     * with it so a replay shows each mention as the live line did once
     * the player has gone. The line's component, if it has one, stays.
     */
    public LostTalesChatMessagePacket withNamedPlayers(
            List<ChatNamedPlayer> named) {
        return withServerBody(this.bodyJson, named);
    }

    /** The server line's own component as chat JSON; empty for none. */
    public String getBodyJson() { return this.bodyJson; }

    /**
     * The players the line names as the server knew them when it was
     * said — a server line's players, a message's mentions; never null.
     */
    public List<ChatNamedPlayer> getNamedPlayers() { return this.namedPlayers; }

    /**
     * The same line, said in one conversation of a scoped channel: the
     * faction it was spoken to. Ignored on a channel that is only ever
     * one conversation.
     */
    public LostTalesChatMessagePacket withScope(String scopeValue) {
        return carrying(new LostTalesChatMessagePacket(getChannel(), this.senderId,
                this.identityName, this.accountName, this.title,
                this.titleColor, this.nameColor, this.message,
                this.timestampMillis, this.skinId, this.showcases,
                this.factionName, this.partner, this.roles,
                this.accountLine, this.messageId, this.reply,
                this.partnerIdentity, this.echoNonce, this.identityCharacterId,
                this.ownCharacterId, this.partnerCharacterId, scopeValue));
    }

    /** The same line with its name drawn in another colour. */
    public LostTalesChatMessagePacket withNameColor(int color) {
        return carrying(new LostTalesChatMessagePacket(getChannel(), this.senderId,
                this.identityName, this.accountName, this.title,
                this.titleColor, color, this.message,
                this.timestampMillis, this.skinId, this.showcases,
                this.factionName, this.partner, this.roles,
                this.accountLine, this.messageId, this.reply,
                this.partnerIdentity, this.echoNonce, this.identityCharacterId,
                this.ownCharacterId, this.partnerCharacterId, this.scopeValue));
    }

    /** Which conversation on a scoped channel the line is in; empty for one. */
    public String getScopeValue() { return this.scopeValue; }

    /** Whether the line is the Narrator's: told, not said. */
    public boolean isNarrator() { return ChatNarrator.isNarratorSkin(this.skinId); }

    /** Whether the channel is as many conversations as it has scope values. */
    private static boolean scopedChannel(ChatChannel channel) {
        return channel != null && channel.isScoped();
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
    /**
     * For a whisper, the identity of that other party; empty when the
     * conversation is with their account rather than a character.
     */
    public String getPartnerIdentity() { return this.partnerIdentity; }
    /** The sender's own name for this message; zero on every other copy. */
    public long getEchoNonce() { return this.echoNonce; }

    /**
     * The same line with the sender's private name for it taken off:
     * what everyone but the sender is sent, since the name means
     * nothing to them and is not theirs to carry.
     */
    public LostTalesChatMessagePacket withoutEcho() {
        return this.echoNonce == 0L ? this
                : rebuild(this.message, this.partner, this.partnerIdentity,
                        this.reply, 0L);
    }
    /**
     * The stable id of the character the line wears; null for an account
     * line and a bridge line.
     */
    public UUID getIdentityCharacterId() { return this.identityCharacterId; }
    /**
     * For a whisper, the receiving party's own character this copy is
     * held as; null for the account.
     */
    public UUID getOwnCharacterId() { return this.ownCharacterId; }
    /**
     * For a whisper, the other party's character the conversation is
     * with; null for their account.
     */
    public UUID getPartnerCharacterId() { return this.partnerCharacterId; }
    /** The sender's role mask, as the server states it; see {@link ChatAccountRole}. */
    public int getRoles() { return this.roles; }
    /** Whether the line wears the account identity rather than a
     *  character's; heads and skin caching follow this. */
    public boolean isAccountLine() { return this.accountLine; }
    /**
     * The server's name for this message, {@link ChatMessageIds#NONE}
     * when it has none. Stable across every recipient, so it is what
     * anything referring to a message refers to it by.
     */
    public long getMessageId() { return this.messageId; }
    /**
     * The message this line replies to, with the author and excerpt the
     * server resolved for it; never null, and
     * {@link ChatReplyReference#exists()} is false when the line replies
     * to nothing.
     */
    public ChatReplyReference getReply() { return this.reply; }
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
