package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatAccountRole;
import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelIconSpec;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatRolePresentation;
import com.ninuna.losttales.chat.ChatRoleCatalog;
import com.ninuna.losttales.chat.profanity.ChatProfanityWords;
import com.ninuna.losttales.chat.share.ChatShareReference;
import com.ninuna.losttales.chat.share.ChatShowcase;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Builds the chat packets for tests. Each builder takes what a test names
 * and passes the packet's full constructor what the server or the client
 * sends when nothing says otherwise.
 */
public final class ChatPacketFixtures {

    private ChatPacketFixtures() {}

    /** A chat line; see {@link Line} for what it carries unless told. */
    public static Line line(ChatChannel channel, String identityName,
                            String accountName, String message) {
        return new Line(channel, identityName, accountName, message);
    }

    /** A send request; see {@link Send} for what it carries unless told. */
    public static Send send(ChatChannel channel, String message) {
        return new Send(channel, message);
    }

    /** An access statement; see {@link Access} for what it carries unless told. */
    public static Access access(int roleMask) {
        return new Access(roleMask);
    }

    /** A typing notice that speaks as the channel's default identity. */
    public static LostTalesChatTypingPacket typing(ChatChannel channel,
                                                   String target,
                                                   boolean typing) {
        return new LostTalesChatTypingPacket(channel, target, typing,
                LostTalesChatSendPacket.IDENTITY_DEFAULT, null, "", null);
    }

    /**
     * A line from a random sender with no title, a white title and name,
     * said at 1 with no skin, nothing shared, no faction, no whisper
     * partner, no roles, no message id, no quote and no echo. It wears the
     * account on a channel that shows roles and a character on any other.
     */
    public static final class Line {
        private final ChatChannel channel;
        private final String identityName;
        private final String accountName;
        private final String message;
        private UUID senderId = UUID.randomUUID();
        private String title = "";
        private int titleColor = 0xFFFFFF;
        private int nameColor = 0xFFFFFF;
        private long timestampMillis = 1L;
        private String skinId = "";
        private List<ChatShowcase> showcases;
        private String factionName = "";
        private String partner = "";
        private int roles;
        private boolean accountLine;
        private long messageId = ChatMessageIds.NONE;
        private long echoNonce;
        private UUID identityCharacterId;

        private Line(ChatChannel channel, String identityName,
                     String accountName, String message) {
            this.channel = channel;
            this.identityName = identityName;
            this.accountName = accountName;
            this.message = message;
            this.accountLine = channel != null
                    && ChatRolePresentation.showsRoles(channel);
        }

        public Line sender(UUID senderId) {
            this.senderId = senderId;
            return this;
        }

        public Line title(String title) {
            this.title = title;
            return this;
        }

        public Line colors(int titleColor, int nameColor) {
            this.titleColor = titleColor;
            this.nameColor = nameColor;
            return this;
        }

        public Line at(long timestampMillis) {
            this.timestampMillis = timestampMillis;
            return this;
        }

        public Line skin(String skinId) {
            this.skinId = skinId;
            return this;
        }

        public Line showcases(List<ChatShowcase> showcases) {
            this.showcases = showcases;
            return this;
        }

        public Line faction(String factionName) {
            this.factionName = factionName;
            return this;
        }

        public Line partner(String partner) {
            this.partner = partner;
            return this;
        }

        public Line roles(int roles) {
            this.roles = roles;
            return this;
        }

        public Line accountLine(boolean accountLine) {
            this.accountLine = accountLine;
            return this;
        }

        public Line messageId(long messageId) {
            this.messageId = messageId;
            return this;
        }

        public Line echoNonce(long echoNonce) {
            this.echoNonce = echoNonce;
            return this;
        }

        /** The id of the character the line wears. */
        public Line character(UUID identityCharacterId) {
            this.identityCharacterId = identityCharacterId;
            return this;
        }

        public LostTalesChatMessagePacket build() {
            return new LostTalesChatMessagePacket(this.channel, this.senderId,
                    this.identityName, this.accountName, this.title,
                    this.titleColor, this.nameColor, this.message,
                    this.timestampMillis, this.skinId, this.showcases,
                    this.factionName, this.partner, this.roles,
                    this.accountLine, this.messageId, ChatReplyReference.NONE,
                    "", this.echoNonce, this.identityCharacterId);
        }
    }

    /**
     * A request with nothing shared, no whisper target, the channel's
     * default identity, no reply, no quote and no echo.
     */
    public static final class Send {
        private final ChatChannel channel;
        private final String message;
        private List<ChatShareReference> references;
        private String target = "";
        private String targetIdentity = "";
        private UUID targetCharacterId;
        private int identityKind = LostTalesChatSendPacket.IDENTITY_DEFAULT;
        private UUID identityCharacterId;
        private long replyToMessageId = ChatMessageIds.NONE;
        private long echoNonce;
        private boolean quotesUnkept;
        private boolean action;

        private Send(ChatChannel channel, String message) {
            this.channel = channel;
            this.message = message;
        }

        public Send sharing(List<ChatShareReference> references) {
            this.references = references;
            return this;
        }

        /** The account a whisper is for. */
        public Send to(String target) {
            this.target = target;
            return this;
        }

        /** The identity of the whisper's target, and that character's id. */
        public Send toCharacter(String targetIdentity, UUID targetCharacterId) {
            this.targetIdentity = targetIdentity;
            this.targetCharacterId = targetCharacterId;
            return this;
        }

        /** The identity asked to speak as. */
        public Send as(int identityKind, UUID identityCharacterId) {
            this.identityKind = identityKind;
            this.identityCharacterId = identityCharacterId;
            return this;
        }

        public Send replyingTo(long messageId) {
            this.replyToMessageId = messageId;
            return this;
        }

        /** A reply to a line no server holds a record of. */
        public Send quotingUnkept() {
            this.quotesUnkept = true;
            return this;
        }

        public Send echoNonce(long echoNonce) {
            this.echoNonce = echoNonce;
            return this;
        }

        /** The words as an action ({@code /me}). */
        public Send action() {
            this.action = true;
            return this;
        }

        public LostTalesChatSendPacket build() {
            return new LostTalesChatSendPacket(this.channel, this.message,
                    this.references, this.target, this.identityKind,
                    this.identityCharacterId, this.replyToMessageId,
                    this.targetIdentity, this.echoNonce, this.targetCharacterId,
                    this.quotesUnkept, this.action);
        }
    }

    /**
     * A statement with no role holders and no mutes, the catalogue in force
     * when it is built, every channel readable and sendable, no moderation
     * or config rights, no capabilities, the account wearing all its
     * roles, no character roles, no Proximity radius, no channel icons and
     * no profanity words of the server's own.
     */
    public static final class Access {
        private final int roleMask;
        private List<LostTalesChatAccessPacket.RoleHolder> holders =
                Collections.<LostTalesChatAccessPacket.RoleHolder>emptyList();
        private List<ChatAccountRole> catalog;
        private List<String> readable = LostTalesChatAccessPacket.allChannelIds();
        private List<String> sendable = LostTalesChatAccessPacket.allChannelIds();
        private boolean canModerate;
        private List<String> capabilities = Collections.<String>emptyList();
        private int accountRoleMask;
        private Map<UUID, Integer> characterRoleMasks =
                Collections.<UUID, Integer>emptyMap();
        private int proximityRadius;

        private Access(int roleMask) {
            this.roleMask = roleMask;
            this.accountRoleMask = roleMask;
        }

        public Access holders(List<LostTalesChatAccessPacket.RoleHolder> holders) {
            this.holders = holders;
            return this;
        }

        public Access catalog(List<ChatAccountRole> catalog) {
            this.catalog = catalog;
            return this;
        }

        public Access channels(List<String> readable, List<String> sendable) {
            this.readable = readable;
            this.sendable = sendable;
            return this;
        }

        public Access canModerate() {
            this.canModerate = true;
            return this;
        }

        public Access capabilities(List<String> capabilities) {
            this.capabilities = capabilities;
            return this;
        }

        /** The account's own roles and each own character's, apart. */
        public Access accountRoles(int accountRoleMask,
                                   Map<UUID, Integer> characterRoleMasks) {
            this.accountRoleMask = accountRoleMask;
            this.characterRoleMasks = characterRoleMasks;
            return this;
        }

        public Access proximity(int radius) {
            this.proximityRadius = radius;
            return this;
        }

        public LostTalesChatAccessPacket build() {
            return new LostTalesChatAccessPacket(
                    this.roleMask, this.holders,
                    Collections.<UUID>emptyList(),
                    this.catalog != null ? this.catalog
                            : ChatRoleCatalog.current().roles(),
                    this.readable, this.sendable, this.canModerate, false,
                    this.capabilities, this.accountRoleMask,
                    this.characterRoleMasks, this.proximityRadius,
                    Collections.<String, ChatChannelIconSpec>emptyMap(),
                    ChatProfanityWords.NONE);
        }
    }
}
