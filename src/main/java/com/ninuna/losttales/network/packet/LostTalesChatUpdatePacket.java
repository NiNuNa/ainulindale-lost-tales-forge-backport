package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatMessageValidator;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.Collections;
import java.util.List;

/**
 * Server-to-client: something has happened to a message already on
 * screen — it now says something else, or it is gone.
 *
 * <p>Sent to exactly the accounts the original was sent to, so a
 * message that was never shown to someone is never mentioned to them
 * either. The line is found by its id rather than described again: the
 * client already holds everything else about it — who signed it, in
 * what colours, under which tab — and an edit changes only the words,
 * the component a Discord member's words come with, and whom and which
 * roles they name, read again from the new words.
 */
public final class LostTalesChatUpdatePacket implements IMessage {
    private static final int MAX_PACKET_BYTES =
            64 + ChatMessageValidator.MAX_UTF8_BYTES
                    + 4 + LostTalesChatMessagePacket.MAX_BODY_BYTES
                    + LostTalesChatNamedPlayerCodec.MAX_BYTES
                    + LostTalesChatRoleMentionCodec.MAX_BYTES;

    private long messageId = ChatMessageIds.NONE;
    /** Whether the message is gone rather than rewritten. */
    private boolean removed;
    private String message = "";
    /**
     * The new words as words each game translates, as the chat JSON a
     * line carries ({@link LostTalesChatMessagePacket#getBodyJson}): what
     * a Discord member's edited line comes with when its words hold a
     * mark. Empty for none, and always for a removal; a client applies it
     * only to a line that may carry one.
     */
    private String bodyJson = "";
    /** Whom the new words name; none for a removal. */
    private List<ChatNamedPlayer> namedPlayers = Collections.emptyList();
    /** The roles the new words call, by id; none for a removal. */
    private List<String> mentionedRoles = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatUpdatePacket() {}

    /**
     * The message now reads {@code message}, with {@code bodyJson} for
     * its component (empty for none), naming {@code named} and calling
     * the roles {@code roleIds}.
     */
    public static LostTalesChatUpdatePacket edited(long messageId,
                                                   String message,
                                                   String bodyJson,
                                                   List<ChatNamedPlayer> named,
                                                   List<String> roleIds) {
        return new LostTalesChatUpdatePacket(messageId, false, message, bodyJson,
                named, roleIds);
    }

    /** The message is gone. */
    public static LostTalesChatUpdatePacket removed(long messageId) {
        return new LostTalesChatUpdatePacket(messageId, true, "", "", null, null);
    }

    private LostTalesChatUpdatePacket(long messageId, boolean removed,
                                      String message, String bodyJson,
                                      List<ChatNamedPlayer> named,
                                      List<String> roleIds) {
        this.messageId = messageId;
        this.removed = removed;
        this.message = message == null ? "" : message;
        this.bodyJson = bodyJson == null ? "" : bodyJson;
        this.namedPlayers = named == null ? Collections.<ChatNamedPlayer>emptyList()
                : Collections.unmodifiableList(named);
        this.mentionedRoles = roleIds == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(roleIds);
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat update packet size");
            }
            this.messageId = buffer.readLong();
            this.removed = buffer.readBoolean();
            this.message = LostTalesPacketCodec.readUtf8String(
                    buffer, ChatMessageValidator.MAX_UTF8_BYTES);
            this.bodyJson = LostTalesPacketCodec.readUtf8String(
                    buffer, LostTalesChatMessagePacket.MAX_BODY_BYTES);
            this.namedPlayers = LostTalesChatNamedPlayerCodec.read(buffer);
            this.mentionedRoles = LostTalesChatRoleMentionCodec.read(buffer);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.removed = false;
            this.message = "";
            this.bodyJson = "";
            this.namedPlayers = Collections.emptyList();
            this.mentionedRoles = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeLong(this.messageId);
        buffer.writeBoolean(this.removed);
        LostTalesPacketCodec.writeUtf8String(buffer, this.message,
                ChatMessageValidator.MAX_UTF8_BYTES);
        LostTalesPacketCodec.writeUtf8String(buffer, this.bodyJson,
                LostTalesChatMessagePacket.MAX_BODY_BYTES);
        LostTalesChatNamedPlayerCodec.write(buffer, this.namedPlayers);
        LostTalesChatRoleMentionCodec.write(buffer, this.mentionedRoles);
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId)
                || (this.removed ? this.message.length() > 0
                        || this.bodyJson.length() > 0
                        || !this.namedPlayers.isEmpty()
                        || !this.mentionedRoles.isEmpty()
                        : !ChatMessageValidator.isValid(this.message))
                || !LostTalesChatRoleMentionCodec.isValid(this.mentionedRoles)
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.bodyJson,
                        LostTalesChatMessagePacket.MAX_BODY_BYTES)
                || this.namedPlayers.size() > ChatNamedPlayer.MAX_PER_LINE) {
            throw new IllegalArgumentException("invalid chat update");
        }
    }

    /**
     * The same edit as {@code reader}, who is not shown which account plays
     * a character, is sent it: whom the new words name as characters is
     * named without the account, unless it is the reader.
     */
    public LostTalesChatUpdatePacket withoutAccountsFor(java.util.UUID reader) {
        if (this.removed || this.namedPlayers.isEmpty()) {
            return this;
        }
        List<ChatNamedPlayer> named = new java.util.ArrayList<ChatNamedPlayer>(
                this.namedPlayers.size());
        for (ChatNamedPlayer player : this.namedPlayers) {
            named.add(reader != null && reader.equals(player.getPlayerId())
                    ? player : player.withoutAccount());
        }
        return new LostTalesChatUpdatePacket(this.messageId, false, this.message,
                this.bodyJson, named, this.mentionedRoles);
    }

    /** The message this is about. */
    public long getMessageId() { return this.messageId; }
    /** Whether it is gone rather than rewritten. */
    public boolean isRemoved() { return this.removed; }
    /** What it says now; empty when it is gone. */
    public String getMessage() { return this.message; }
    /** The new words' component as chat JSON; empty for none. */
    public String getBodyJson() { return this.bodyJson; }
    /** Whom the new words name. */
    public List<ChatNamedPlayer> getNamedPlayers() { return this.namedPlayers; }
    /** The roles the new words call, by id. */
    public List<String> getMentionedRoles() { return this.mentionedRoles; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatUpdatePacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatUpdatePacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatUpdate(message);
                }
            });
            return null;
        }
    }
}
