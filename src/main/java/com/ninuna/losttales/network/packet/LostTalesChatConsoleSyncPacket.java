package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.LostTalesMetaData;
import com.ninuna.losttales.LostTalesMod;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.chat.ChatReportReason;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Server-to-client: entries of the Server Console, oldest
 * first — one as it happens, a batch when a staff member joins. Sent
 * only to players the server has found to hold {@code chat.server_console.read}
 * at that moment; the client is told nothing it may not read and
 * decides nothing about who may. At most {@link #MAX_EVENTS} per packet.
 */
public final class LostTalesChatConsoleSyncPacket implements IMessage {
    public static final int MAX_EVENTS = 32;
    private static final int MAX_ACTOR_BYTES = ChatConsoleEvent.MAX_ACTOR_LENGTH * 4;
    private static final int MAX_TEXT_BYTES = ChatConsoleEvent.MAX_TEXT_LENGTH * 4;
    private static final int MAX_CONTEXT_BYTES = ChatConsoleEvent.MAX_CONTEXT_LENGTH * 4;
    /** The actor's account as the server knew it: a flag, its id and its colour. */
    private static final int ACTOR_IDENTITY_BYTES = 1 + 16;
    private static final int MAX_NOTE_BYTES = ChatConsoleEvent.Report.MAX_NOTE_LENGTH * 4;
    private static final int MAX_AUTHOR_BYTES = ChatConsoleEvent.Report.MAX_AUTHOR_LENGTH * 4;
    private static final int MAX_LINK_BYTES = ChatConsoleEvent.Report.MAX_LINK_LENGTH * 4;
    /**
     * A report: a flag, the reason, the note, the message's id, its
     * author and their colour, its words' start and its link.
     */
    private static final int REPORT_BYTES = 1 + 1 + 4 + MAX_NOTE_BYTES + 8
            + 4 + MAX_AUTHOR_BYTES + 4 + 4 + ChatReplyReference.MAX_EXCERPT_BYTES
            + 4 + MAX_LINK_BYTES;
    private static final int MAX_PACKET_BYTES = 4
            + MAX_EVENTS * (8 + 8 + 1 + 1 + 4 + MAX_ACTOR_BYTES + 4 + MAX_TEXT_BYTES
                    + 4 + MAX_CONTEXT_BYTES + ACTOR_IDENTITY_BYTES + REPORT_BYTES)
            + 8;

    private List<ChatConsoleEvent> events = Collections.emptyList();
    /**
     * Where the player the entries are for arrived, as an id on the one
     * clock messages and entries share: the id of their own join line.
     * An entry with a smaller id happened before they came and is
     * history to them. Only the replay on joining states it; an entry
     * sent as it happens is news to everyone reading.
     */
    private long arrivalId = Long.MIN_VALUE;
    private boolean malformed;

    public LostTalesChatConsoleSyncPacket() {}

    /** Entries sent as they happen: news, every one of them. */
    public LostTalesChatConsoleSyncPacket(List<ChatConsoleEvent> events) {
        this(events, Long.MIN_VALUE);
    }

    /** Entries replayed on joining, those from {@code arrivalId} on happening as their reader arrived. */
    public LostTalesChatConsoleSyncPacket(List<ChatConsoleEvent> events,
                                          long arrivalId) {
        this.arrivalId = arrivalId;
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
                ChatNamedPlayer actorIdentity = null;
                if (buffer.readBoolean()) {
                    UUID actorId = new UUID(buffer.readLong(), buffer.readLong());
                    actorIdentity = ChatNamedPlayer.account(actorId, actor);
                }
                ChatConsoleEvent.Report report = buffer.readBoolean()
                        ? readReport(buffer) : null;
                if (id <= 0L || kind == null || severity == null
                        || actor.length() > ChatConsoleEvent.MAX_ACTOR_LENGTH
                        || text.trim().length() == 0
                        || text.length() > ChatConsoleEvent.MAX_TEXT_LENGTH
                        || context.length() > ChatConsoleEvent.MAX_CONTEXT_LENGTH
                        || !ChatConsoleEvent.isContext(context)) {
                    throw new LostTalesPacketCodec.DecodeException("invalid console event");
                }
                decoded.add(new ChatConsoleEvent(id, timestamp, kind, severity, actor, text,
                        context, actorIdentity, report));
            }
            // After the entries: where the reader arrived.
            this.arrivalId = buffer.readLong();
            LostTalesPacketCodec.requireFinished(buffer);
            this.events = Collections.unmodifiableList(decoded);
        } catch (RuntimeException exception) {
            this.malformed = true;
            this.events = Collections.emptyList();
            this.arrivalId = Long.MIN_VALUE;
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
            ChatNamedPlayer actorIdentity = event.getActorIdentity();
            buffer.writeBoolean(actorIdentity != null);
            if (actorIdentity != null) {
                buffer.writeLong(actorIdentity.getPlayerId().getMostSignificantBits());
                buffer.writeLong(actorIdentity.getPlayerId().getLeastSignificantBits());
            }
            ChatConsoleEvent.Report report = event.getReport();
            buffer.writeBoolean(report != null);
            if (report != null) {
                buffer.writeByte(report.getReason().ordinal());
                LostTalesPacketCodec.writeUtf8String(buffer, report.getNote(), MAX_NOTE_BYTES);
                buffer.writeLong(report.getMessageId());
                LostTalesPacketCodec.writeUtf8String(buffer, report.getAuthor(), MAX_AUTHOR_BYTES);
                buffer.writeInt(report.getAuthorColor());
                LostTalesPacketCodec.writeUtf8String(buffer, report.getExcerpt(),
                        ChatReplyReference.MAX_EXCERPT_BYTES);
                LostTalesPacketCodec.writeUtf8String(buffer, report.getLink(), MAX_LINK_BYTES);
            }
        }
        buffer.writeLong(this.arrivalId);
    }

    /** A report as {@link #toBytes} writes it; a malformed one throws. */
    private static ChatConsoleEvent.Report readReport(ByteBuf buffer) {
        ChatReportReason reason = ChatReportReason.fromOrdinal(buffer.readUnsignedByte());
        String note = LostTalesPacketCodec.readUtf8String(buffer, MAX_NOTE_BYTES);
        long messageId = buffer.readLong();
        String author = LostTalesPacketCodec.readUtf8String(buffer, MAX_AUTHOR_BYTES);
        int authorColor = buffer.readInt();
        String excerpt = LostTalesPacketCodec.readUtf8String(buffer,
                ChatReplyReference.MAX_EXCERPT_BYTES);
        String link = LostTalesPacketCodec.readUtf8String(buffer, MAX_LINK_BYTES);
        if (note.length() > ChatConsoleEvent.Report.MAX_NOTE_LENGTH
                || author.length() > ChatConsoleEvent.Report.MAX_AUTHOR_LENGTH
                || excerpt.length() > ChatReplyReference.MAX_EXCERPT_CHARACTERS
                || link.length() > ChatConsoleEvent.Report.MAX_LINK_LENGTH) {
            throw new LostTalesPacketCodec.DecodeException("invalid report");
        }
        return new ChatConsoleEvent.Report(reason, note, messageId, author,
                authorColor, excerpt, link);
    }

    /** The entries, oldest first; empty for a malformed payload. */
    public List<ChatConsoleEvent> getEvents() {
        return this.events;
    }

    /** Where the reader arrived, as an id; see the field. */
    public long getArrivalId() {
        return this.arrivalId;
    }

    /**
     * Whether the batch is the replay a player is sent on joining rather
     * than entries sent as they happened: what the client files against
     * where it last read the console.
     */
    public boolean isReplay() {
        return this.arrivalId != Long.MIN_VALUE;
    }

    /** Whether an entry of this batch happened before its reader arrived. */
    public boolean saidBeforeArrival(ChatConsoleEvent event) {
        return event == null || event.getId() < this.arrivalId;
    }

    public boolean isMalformed() {
        return this.malformed;
    }

    public static final class Handler implements IMessageHandler<
            LostTalesChatConsoleSyncPacket, IMessage> {
        @Override
        public IMessage onMessage(final LostTalesChatConsoleSyncPacket message,
                                  MessageContext context) {
            if (message == null) {
                return null;
            }
            if (message.isMalformed()) {
                FMLLog.warning("[%s] A batch of Server Console entries from the server could not be read and was dropped",
                        LostTalesMetaData.MOD_ID);
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
