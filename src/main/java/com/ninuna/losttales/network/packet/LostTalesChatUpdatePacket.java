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
 * what colours, under which tab — and an edit changes only the words and
 * whom they name, read again from the new words.
 */
public final class LostTalesChatUpdatePacket implements IMessage {
    private static final int MAX_PACKET_BYTES =
            64 + ChatMessageValidator.MAX_UTF8_BYTES
                    + LostTalesChatNamedPlayerCodec.MAX_BYTES;

    private long messageId = ChatMessageIds.NONE;
    /** Whether the message is gone rather than rewritten. */
    private boolean removed;
    private String message = "";
    /** Whom the new words name; none for a removal. */
    private List<ChatNamedPlayer> namedPlayers = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatUpdatePacket() {}

    /** The message now reads {@code message}, naming {@code named}. */
    public static LostTalesChatUpdatePacket edited(long messageId,
                                                   String message,
                                                   List<ChatNamedPlayer> named) {
        return new LostTalesChatUpdatePacket(messageId, false, message, named);
    }

    /** The message is gone. */
    public static LostTalesChatUpdatePacket removed(long messageId) {
        return new LostTalesChatUpdatePacket(messageId, true, "", null);
    }

    private LostTalesChatUpdatePacket(long messageId, boolean removed,
                                      String message, List<ChatNamedPlayer> named) {
        this.messageId = messageId;
        this.removed = removed;
        this.message = message == null ? "" : message;
        this.namedPlayers = named == null ? Collections.<ChatNamedPlayer>emptyList()
                : Collections.unmodifiableList(named);
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
            this.namedPlayers = LostTalesChatNamedPlayerCodec.read(buffer);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.removed = false;
            this.message = "";
            this.namedPlayers = Collections.emptyList();
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
        LostTalesChatNamedPlayerCodec.write(buffer, this.namedPlayers);
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId)
                || (this.removed ? this.message.length() > 0
                        || !this.namedPlayers.isEmpty()
                        : !ChatMessageValidator.isValid(this.message))
                || this.namedPlayers.size() > ChatNamedPlayer.MAX_PER_LINE) {
            throw new IllegalArgumentException("invalid chat update");
        }
    }

    /** The message this is about. */
    public long getMessageId() { return this.messageId; }
    /** Whether it is gone rather than rewritten. */
    public boolean isRemoved() { return this.removed; }
    /** What it says now; empty when it is gone. */
    public String getMessage() { return this.message; }
    /** Whom the new words name. */
    public List<ChatNamedPlayer> getNamedPlayers() { return this.namedPlayers; }
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
