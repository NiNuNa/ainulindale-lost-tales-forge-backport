package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.ChatReportReason;
import com.ninuna.losttales.chat.server.ChatReports;
import com.ninuna.losttales.network.server.LostTalesRequestRateLimiter;
import com.ninuna.losttales.network.server.LostTalesServerPacketDispatcher;
import com.ninuna.losttales.network.server.LostTalesServerTaskQueue;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Client-to-server: report one message to staff. Names the message, the
 * reason and the reporter's note, and nothing more: whether the player
 * was shown the message, who said it and where are the server's record
 * to answer ({@link ChatReports}).
 */
public final class LostTalesChatReportPacket implements IMessage {
    private static final int MAX_NOTE_BYTES =
            ChatConsoleEvent.Report.MAX_NOTE_LENGTH * 4;
    private static final int MAX_PACKET_BYTES = 8 + 1 + 5 + MAX_NOTE_BYTES;

    private long messageId = ChatMessageIds.NONE;
    private ChatReportReason reason;
    private String note = "";
    private boolean malformed;

    public LostTalesChatReportPacket() {}

    public LostTalesChatReportPacket(long messageId, ChatReportReason reason,
                                     String note) {
        this.messageId = messageId;
        this.reason = reason;
        this.note = note == null ? "" : note.trim();
        validate();
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat report packet size");
            }
            this.messageId = buffer.readLong();
            this.reason = ChatReportReason.fromOrdinal(buffer.readUnsignedByte());
            this.note = LostTalesPacketCodec.readUtf8String(buffer, MAX_NOTE_BYTES);
            LostTalesPacketCodec.requireFinished(buffer);
            validate();
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messageId = ChatMessageIds.NONE;
            this.reason = null;
            this.note = "";
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        validate();
        buffer.writeLong(this.messageId);
        buffer.writeByte(this.reason.ordinal());
        LostTalesPacketCodec.writeUtf8String(buffer, this.note, MAX_NOTE_BYTES);
    }

    private void validate() {
        if (!ChatMessageIds.isServerId(this.messageId) || this.reason == null
                || this.note.length() > ChatConsoleEvent.Report.MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("invalid chat report");
        }
    }

    public long getMessageId() { return this.messageId; }
    public ChatReportReason getReason() { return this.reason; }
    /** The reporter's own words, or empty. */
    public String getNote() { return this.note; }
    public boolean isMalformed() { return this.malformed; }

    public static final class Handler implements IMessageHandler<
            LostTalesChatReportPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatReportPacket message,
                                  MessageContext context) {
            EntityPlayerMP player =
                    LostTalesServerPacketDispatcher.getPlayer(context);
            if (player == null || message == null) {
                return null;
            }
            LostTalesServerPacketDispatcher.submit(player,
                    LostTalesRequestRateLimiter.RequestType.CHAT_REPORT,
                    message.isMalformed(), "LostTalesChatReportPacket",
                    new LostTalesServerTaskQueue.PlayerTask() {
                        @Override
                        public void run(EntityPlayerMP serverPlayer) {
                            ChatReports.file(serverPlayer,
                                    message.getMessageId(),
                                    message.getReason(), message.getNote());
                        }
                    });
            return null;
        }
    }
}
