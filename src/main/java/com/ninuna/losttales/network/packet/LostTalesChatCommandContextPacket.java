package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.server.ChatCommandContexts;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: the page the command about to follow was typed in.
 * Sent just ahead of the vanilla chat packet that carries the command,
 * so the console line naming who ran what can also say where; the
 * command itself still travels vanilla's own way and is run by
 * vanilla. The id is the client's own page id, a string the server
 * bounds, keeps for a few seconds and shows console readers as a link.
 * It decides one thing, the page the command's answers are kept under,
 * and the server checks that first: the page stands only while the
 * player may read its conversation, else the answers go to their Client
 * Console ({@code ChatCommandContexts}).
 */
public final class LostTalesChatCommandContextPacket implements IMessage {
    public static final int MAX_PAGE_ID_CHARACTERS = ChatConsoleEvent.MAX_CONTEXT_LENGTH;
    private static final int MAX_PAGE_ID_BYTES = MAX_PAGE_ID_CHARACTERS * 4;
    private static final int MAX_PACKET_BYTES = 5 + MAX_PAGE_ID_BYTES;

    private String pageId = "";
    private boolean malformed;

    public LostTalesChatCommandContextPacket() {}

    public LostTalesChatCommandContextPacket(String pageId) {
        this.pageId = pageId == null ? "" : pageId.trim();
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat command context packet size");
            }
            this.pageId = LostTalesPacketCodec.readUtf8String(buffer,
                    MAX_PAGE_ID_BYTES).trim();
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.pageId = "";
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        LostTalesPacketCodec.writeUtf8String(buffer, this.pageId, MAX_PAGE_ID_BYTES);
    }

    private void validate() {
        if (this.pageId.length() == 0
                || this.pageId.length() > MAX_PAGE_ID_CHARACTERS
                || !LostTalesPacketCodec.isUtf8WithinLimit(this.pageId, MAX_PAGE_ID_BYTES)
                || !ChatConsoleEvent.isContext(this.pageId)) {
            throw new IllegalArgumentException("invalid chat command context");
        }
    }

    public String getPageId() {
        return this.pageId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    /**
     * Handled where it is read, on the network thread, rather than
     * queued to the server tick: the chat packet carrying the command
     * follows this one on the same connection and is run on the server
     * thread as soon as it is processed, and a note queued to the tick
     * could arrive after it. The note touches nothing but its own
     * store, which is built for both threads.
     */
    public static final class Handler implements IMessageHandler<
            LostTalesChatCommandContextPacket, IMessage> {
        @Override
        public IMessage onMessage(LostTalesChatCommandContextPacket message,
                                  MessageContext context) {
            EntityPlayerMP player = LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            if (!LostTalesRequestRateLimiter.allow(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_COMMAND_CONTEXT)) {
                LostTalesRequestRateLimiter.logRateLimited(player,
                        LostTalesRequestRateLimiter.RequestType.CHAT_COMMAND_CONTEXT,
                        "LostTalesChatCommandContextPacket");
                return null;
            }
            if (message.isMalformed()) {
                LostTalesRequestRateLimiter.logMalformed(player,
                        "LostTalesChatCommandContextPacket");
                return null;
            }
            ChatCommandContexts.note(player.getUniqueID(), message.getPageId(),
                    System.currentTimeMillis());
            return null;
        }
    }
}
