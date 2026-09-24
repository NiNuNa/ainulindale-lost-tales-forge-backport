package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatNamedPlayer;
import com.ninuna.losttales.chat.ChatReportReason;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Console entries cross whole, in order, and a bad one refuses the batch. */
public final class LostTalesChatConsoleSyncPacketTest {

    @Test
    public void aBatchRoundTripsInOrder() {
        ChatConsoleEvent command = new ChatConsoleEvent(10L, 5000L,
                ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO, "Steve",
                "/gamemode 1", "faction|scope:gondor");
        ChatConsoleEvent warning = new ChatConsoleEvent(11L, 6000L,
                ChatConsoleEvent.Kind.WARNING, ChatConsoleEvent.Severity.WARNING, "",
                "the mute list could not be read");
        LostTalesChatConsoleSyncPacket packet = new LostTalesChatConsoleSyncPacket(
                Arrays.asList(command, warning));
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getEvents().size());
        ChatConsoleEvent first = decoded.getEvents().get(0);
        assertEquals(10L, first.getId());
        assertEquals(5000L, first.getTimestampMillis());
        assertEquals(ChatConsoleEvent.Kind.COMMAND, first.getKind());
        assertEquals("Steve", first.getActor());
        assertEquals("/gamemode 1", first.getText());
        assertEquals("faction|scope:gondor", first.getContext());
        ChatConsoleEvent second = decoded.getEvents().get(1);
        assertEquals(ChatConsoleEvent.Severity.WARNING, second.getSeverity());
        assertEquals("", second.getActor());
        assertEquals("", second.getContext());
    }

    @Test
    public void aContextThatIsNoTabIdRefusesTheBatch() {
        ByteBuf buffer = Unpooled.buffer();
        buffer.writeInt(1);
        buffer.writeLong(1L);
        buffer.writeLong(1L);
        buffer.writeByte(ChatConsoleEvent.Kind.COMMAND.ordinal());
        buffer.writeByte(ChatConsoleEvent.Severity.INFO.ordinal());
        LostTalesPacketCodec.writeUtf8String(buffer, "Steve", 256);
        LostTalesPacketCodec.writeUtf8String(buffer, "/gamemode 1", 2048);
        LostTalesPacketCodec.writeUtf8String(buffer, "global\u0001", 2048);
        buffer.writeBoolean(false);
        buffer.writeBoolean(false);
        buffer.writeLong(Long.MIN_VALUE);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getEvents().isEmpty());
    }

    /**
     * An entry carries its actor's account as the server knew it, by its
     * id, so the client can open their card after they have gone; a
     * record naming another account is dropped.
     */
    @Test
    public void theActorsAccountCrossesWithTheEntry() {
        UUID steve = UUID.randomUUID();
        ChatConsoleEvent command = new ChatConsoleEvent(10L, 5000L,
                ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO,
                "Steve", "/gamemode 1", "",
                ChatNamedPlayer.account(steve, "Steve"));
        assertEquals(steve, command.getActorIdentity().getPlayerId());
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatConsoleSyncPacket(Arrays.asList(command))
                .toBytes(buffer);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        ChatNamedPlayer actor = decoded.getEvents().get(0).getActorIdentity();
        assertEquals(steve, actor.getPlayerId());
        assertEquals("Steve", actor.getAccount());
        assertNull(new ChatConsoleEvent(11L, 5000L,
                ChatConsoleEvent.Kind.COMMAND, ChatConsoleEvent.Severity.INFO,
                "Alex", "/gamemode 1", "",
                ChatNamedPlayer.account(steve, "Steve"))
                .getActorIdentity());
    }

    /**
     * A report crosses with its entry whole: the reason, the note, and
     * the message it is about, its speaker, their colour, its words and
     * its link. A report on any other kind of entry is no entry at all.
     */
    @Test
    public void aReportCrossesWithItsEntry() {
        ChatConsoleEvent.Report report = new ChatConsoleEvent.Report(
                ChatReportReason.HARASSMENT, "kept at it\nfor an hour",
                1757522000000L, "Aldric", 0xA94B54, "you again", "#gondor/1757522000000");
        assertEquals("line breaks never reach the log",
                "kept at it for an hour", report.getNote());
        ChatConsoleEvent entry = new ChatConsoleEvent(12L, 6000L,
                ChatConsoleEvent.Kind.REPORT, ChatConsoleEvent.Severity.NOTICE,
                "Steve", "reported a message", "", null, report);
        ByteBuf buffer = Unpooled.buffer();
        new LostTalesChatConsoleSyncPacket(Arrays.asList(entry)).toBytes(buffer);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        ChatConsoleEvent.Report read = decoded.getEvents().get(0).getReport();
        assertEquals(ChatReportReason.HARASSMENT, read.getReason());
        assertEquals("kept at it for an hour", read.getNote());
        assertEquals(1757522000000L, read.getMessageId());
        assertEquals("Aldric", read.getAuthor());
        assertEquals(0xA94B54, read.getAuthorColor());
        assertEquals("you again", read.getExcerpt());
        assertEquals("#gondor/1757522000000", read.getLink());
        assertEquals(1757522000000L, read.quote().getMessageId());
        try {
            new ChatConsoleEvent(13L, 6000L, ChatConsoleEvent.Kind.WARNING,
                    ChatConsoleEvent.Severity.NOTICE, "Steve", "odd", "",
                    null, report);
            fail("only a report entry carries a report");
        } catch (IllegalArgumentException expected) {
        }
        try {
            new ChatConsoleEvent(14L, 6000L, ChatConsoleEvent.Kind.REPORT,
                    ChatConsoleEvent.Severity.NOTICE, "Steve", "odd", "");
            fail("a report entry carries its report");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    public void theCapIsKeptAndBadPayloadsAreRefused() {
        List<ChatConsoleEvent> many = new ArrayList<ChatConsoleEvent>();
        for (int index = 0; index < LostTalesChatConsoleSyncPacket.MAX_EVENTS + 3; index++) {
            many.add(new ChatConsoleEvent(100L + index, 1L, ChatConsoleEvent.Kind.SERVER,
                    ChatConsoleEvent.Severity.INFO, "", "entry " + index));
        }
        assertEquals(LostTalesChatConsoleSyncPacket.MAX_EVENTS,
                new LostTalesChatConsoleSyncPacket(many).getEvents().size());

        // An unknown kind.
        ByteBuf badKind = Unpooled.buffer();
        badKind.writeInt(1);
        badKind.writeLong(1L);
        badKind.writeLong(1L);
        badKind.writeByte(200);
        badKind.writeByte(0);
        LostTalesPacketCodec.writeUtf8String(badKind, "Steve", 256);
        LostTalesPacketCodec.writeUtf8String(badKind, "text", 2048);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(badKind);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getEvents().isEmpty());

        // A zero id, which the server never hands out.
        ByteBuf zeroId = Unpooled.buffer();
        zeroId.writeInt(1);
        zeroId.writeLong(0L);
        zeroId.writeLong(1L);
        zeroId.writeByte(0);
        zeroId.writeByte(0);
        LostTalesPacketCodec.writeUtf8String(zeroId, "Steve", 256);
        LostTalesPacketCodec.writeUtf8String(zeroId, "text", 2048);
        decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(zeroId);
        assertTrue(decoded.isMalformed());

        // Trailing bytes.
        LostTalesChatConsoleSyncPacket good = new LostTalesChatConsoleSyncPacket(
                many.subList(0, 1));
        ByteBuf trailing = Unpooled.buffer();
        good.toBytes(trailing);
        trailing.writeByte(7);
        decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(trailing);
        assertTrue(decoded.isMalformed());
    }

    /**
     * An entry sent as it happens is news; the replay on joining says
     * where its reader arrived, as the id of their join line, and an
     * entry with a smaller id is history to them.
     */
    @Test
    public void aReplaySaysWhereItsReaderArrived() {
        ChatConsoleEvent started = new ChatConsoleEvent(20L, 1000L,
                ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO,
                "Server", "Server started");
        ChatConsoleEvent later = new ChatConsoleEvent(22L, 900L,
                ChatConsoleEvent.Kind.SERVER, ChatConsoleEvent.Severity.INFO,
                "", "a new entry");
        LostTalesChatConsoleSyncPacket live = new LostTalesChatConsoleSyncPacket(
                Arrays.asList(started));
        assertFalse(live.isReplay());
        assertFalse(live.saidBeforeArrival(started));

        LostTalesChatConsoleSyncPacket replay = new LostTalesChatConsoleSyncPacket(
                Arrays.asList(started, later), 21L);
        ByteBuf buffer = Unpooled.buffer();
        replay.toBytes(buffer);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue(decoded.isReplay());
        assertEquals(21L, decoded.getArrivalId());
        assertTrue(decoded.saidBeforeArrival(decoded.getEvents().get(0)));
        assertFalse(decoded.saidBeforeArrival(decoded.getEvents().get(1)));

        // Entries without it are malformed.
        buffer = Unpooled.buffer();
        replay.toBytes(buffer);
        LostTalesChatConsoleSyncPacket shortened = new LostTalesChatConsoleSyncPacket();
        shortened.fromBytes(buffer.slice(0, buffer.readableBytes() - 8));
        assertTrue(shortened.isMalformed());
        assertTrue(shortened.getEvents().isEmpty());
    }
}
