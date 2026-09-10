package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Server-to-client: entries of the shared operator console, oldest
 * first — one as it happens, a batch when a staff member joins. Sent
 * only to players the server has found to hold {@code chat.console.read}
 * at that moment; the client is told nothing it may not read and
 * decides nothing about who may. At most {@link #MAX_EVENTS} per packet.
 */
public final class LostTalesChatConsoleSyncPacket implements IMessage {
    public static final int MAX_EVENTS = 32;
    private static final int MAX_ACTOR_BYTES = ChatConsoleEvent.MAX_ACTOR_LENGTH * 4;
    private static final int MAX_TEXT_BYTES = ChatConsoleEvent.MAX_TEXT_LENGTH * 4;
    private static final int MAX_CONTEXT_BYTES = ChatConsoleEvent.MAX_CONTEXT_LENGTH * 4;
    private static final int MAX_PACKET_BYTES = 4
            + MAX_EVENTS * (8 + 8 + 1 + 1 + 4 + MAX_ACTOR_BYTES + 4 + MAX_TEXT_BYTES
                    + 4 + MAX_CONTEXT_BYTES);

    private List<ChatConsoleEvent> events = Collections.emptyList();
    private boolean malformed;

    public LostTalesChatConsoleSyncPacket() {}

    public LostTalesChatConsoleSyncPacket(List<ChatConsoleEvent> events) {
        List<ChatConsoleEvent> kept = new ArrayList<ChatConsoleEvent>();
        if (events != null) {
            for (ChatConsoleEvent event : events) {
                if (event != null && kept.size() < MAX_EVENTS) {
                    kept.add(event);
                }
            }
        }
        this.events = Collections.unmodifiableList(kept);
    }

    @Override
    public void fromBytes(ByteBuf buffer) {
        this.malformed = false;
        try {
            if (buffer == null || buffer.readableBytes() > MAX_PACKET_BYTES) {
                throw new LostTalesPacketCodec.DecodeException(
                        "invalid chat console packet size");
            }
            int count = LostTalesPacketCodec.readCount(buffer, MAX_EVENTS, "events");
            List<ChatConsoleEvent> decoded = new ArrayList<ChatConsoleEvent>(count);
            for (int index = 0; index < count; index++) {
                long id = buffer.readLong();
                long timestamp = buffer.readLong();
                ChatConsoleEvent.Kind kind =
                        ChatConsoleEvent.Kind.fromOrdinal(buffer.readUnsignedByte());
                ChatConsoleEvent.Severity severity =
                        ChatConsoleEvent.Severity.fromOrdinal(buffer.readUnsignedByte());
                String actor = LostTalesPacketCodec.readUtf8String(buffer, MAX_ACTOR_BYTES);
                String text = LostTalesPacketCodec.readUtf8String(buffer, MAX_TEXT_BYTES);
                String context = LostTalesPacketCodec.readUtf8String(buffer, MAX_CONTEXT_BYTES);
                if (id <= 0L || kind == null || severity == null
                        || actor.length() > ChatConsoleEvent.MAX_ACTOR_LENGTH
                        || text.trim().length() == 0
                        || text.length() > ChatConsoleEvent.MAX_TEXT_LENGTH
                        || context.length() > ChatConsoleEvent.MAX_CONTEXT_LENGTH
                        || !ChatConsoleEvent.isContext(context)) {
                    throw new LostTalesPacketCodec.DecodeException("invalid console event");
                }
                decoded.add(new ChatConsoleEvent(id, timestamp, kind, severity, actor, text,
                        context));
            }
            LostTalesPacketCodec.requireFinished(buffer);
            this.events = Collections.unmodifiableList(decoded);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.events = Collections.emptyList();
            LostTalesPacketCodec.discardRemaining(buffer);
        }
    }

    @Override
    public void toBytes(ByteBuf buffer) {
        LostTalesPacketCodec.writeCount(buffer, this.events.size(), MAX_EVENTS, "events");
        for (ChatConsoleEvent event : this.events) {
            buffer.writeLong(event.getId());
            buffer.writeLong(event.getTimestampMillis());
            buffer.writeByte(event.getKind().ordinal());
            buffer.writeByte(event.getSeverity().ordinal());
            LostTalesPacketCodec.writeUtf8String(buffer, event.getActor(), MAX_ACTOR_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, event.getText(), MAX_TEXT_BYTES);
            LostTalesPacketCodec.writeUtf8String(buffer, event.getContext(), MAX_CONTEXT_BYTES);
        }
    }

    /** The entries, oldest first; empty for a malformed payload. */
    public List<ChatConsoleEvent> getEvents() {
        return this.events;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesChatConsoleSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatConsoleSyncPacket message,
                                  MessageContext context) {
            if (message == null || message.isMalformed()) {
                return null;
            }
            LostTalesMod.proxy.scheduleClientTask(new Runnable() {
                @Override
                public void run() {
                    LostTalesMod.proxy.handleChatConsole(message);
                }
            });
            return null;
        }
    }
}
