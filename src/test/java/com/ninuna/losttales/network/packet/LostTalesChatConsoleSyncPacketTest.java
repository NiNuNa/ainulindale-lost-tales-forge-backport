package com.ninuna.losttales.network.packet;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        LostTalesPacketCodec.writeUtf8String(buffer, "all\u0001", 2048);
        LostTalesChatConsoleSyncPacket decoded = new LostTalesChatConsoleSyncPacket();
        decoded.fromBytes(buffer);
        assertTrue(decoded.isMalformed());
        assertTrue(decoded.getEvents().isEmpty());
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
}
