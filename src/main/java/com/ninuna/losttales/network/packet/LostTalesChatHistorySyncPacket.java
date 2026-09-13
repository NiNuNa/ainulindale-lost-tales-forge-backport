package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-to-client: a batch of recent messages the player is entitled to
 * see, oldest first, sent when they join so a conversation that went on
 * without them is not lost. Each line is a whole
 * {@link LostTalesChatMessagePacket} exactly as the server would have
 * sent it at the time, length-prefixed, so the client shows it through
 * the very path a live line takes and nothing about a replayed line is
 * decided here. The server chooses the lines ({@code ChatHistory});
 * this only carries them, at most {@link #MAX_MESSAGES} per packet.
 */
public final class LostTalesChatHistorySyncPacket implements IMessage {
    /** Lines per packet: a full replay goes out as several of these. */
    public static final int MAX_MESSAGES = 16;
    private static final int MAX_PACKET_BYTES = 4
            + MAX_MESSAGES * (3 + LostTalesChatMessagePacket.MAX_PACKET_BYTES)
            + 8;

    private List<LostTalesChatMessagePacket> messages = Collections.emptyList();
    /**
     * Where the player the lines are for arrived, as a message id: the id
     * of their own join line, the first line said once they had come in.
     * A line with a smaller id was said before they came and is history
     * to them; that line and every later one were said as they arrived.
     * Messages and console entries take their ids from one clock, so the
     * same id divides both. The login replay states it; a page asked for
     * later is all history.
     */
    private long arrivalId = Long.MAX_VALUE;
    private boolean malformed;

    public LostTalesChatHistorySyncPacket() {}

    /** A batch that is history to its reader, every line of it. */
    public LostTalesChatHistorySyncPacket(List<LostTalesChatMessagePacket> messages) {
        this(messages, Long.MAX_VALUE);
    }

    /** A batch whose lines from {@code arrivalId} on were said as its reader arrived. */
    public LostTalesChatHistorySyncPacket(List<LostTalesChatMessagePacket> messages,
                                          long arrivalId) {
        this.arrivalId = arrivalId;
        List<LostTalesChatMessagePacket> kept = new ArrayList<LostTalesChatMessagePacket>();
        if (messages != null) {
            for (LostTalesChatMessagePacket message : messages) {
                if (message != null && !message.isMalformed()
                        && kept.size() < MAX_MESSAGES) {
                    kept.add(message);
                }
            }
        }
        this.messages = Collections.unmodifiableList(kept);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat history packet size");
            }
            int count = LostTalesPacketCodec.readCount(buffer, MAX_MESSAGES, "messages");
            List<LostTalesChatMessagePacket> decoded =
                    new ArrayList<LostTalesChatMessagePacket>(count);
            for (int index = 0; index < count; index++) {
                byte[] bytes = LostTalesPacketCodec.readBytes(buffer,
                        LostTalesChatMessagePacket.MAX_PACKET_BYTES);
                LostTalesChatMessagePacket message = new LostTalesChatMessagePacket();
                message.fromBytes(Unpooled.wrappedBuffer(bytes));
                if (message.isMalformed()) {
                    throw new LostTalesPacketCodec.DecodeException(
                            "invalid chat history line");
                }
                decoded.add(message);
            }
            // Appended after the lines: where the reader arrived. A batch
            // written before it is all history, as it was then.
            this.arrivalId = buffer.readableBytes() >= 8
                    ? buffer.readLong() : Long.MAX_VALUE;
            LostTalesPacketCodec.requireFinished(buffer);
            this.messages = Collections.unmodifiableList(decoded);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.messages = Collections.emptyList();
            this.arrivalId = Long.MAX_VALUE;
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeCount(buffer, this.messages.size(), MAX_MESSAGES,
                "messages");
        for (LostTalesChatMessagePacket message : this.messages) {
            ByteBuf line = Unpooled.buffer();
            message.toBytes(line);
            byte[] bytes = new byte[line.readableBytes()];
            line.readBytes(bytes);
            LostTalesPacketCodec.writeBytes(buffer, bytes,
                    LostTalesChatMessagePacket.MAX_PACKET_BYTES);
        }
        buffer.writeLong(this.arrivalId);
    }

    /** The lines, oldest first; empty for a malformed payload. */
    public List<LostTalesChatMessagePacket> getMessages() {
        return this.messages;
    }

    /** Where the reader arrived, as a message id; see the field. */
    public long getArrivalId() {
        return this.arrivalId;
    }

    /** Whether a line of this batch was said before its reader arrived. */
    public boolean saidBeforeArrival(LostTalesChatMessagePacket line) {
        return line == null || line.getMessageId() < this.arrivalId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesChatHistorySyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatHistorySyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatHistory(message);
                }
            });
            return null;
        }
    }
}
