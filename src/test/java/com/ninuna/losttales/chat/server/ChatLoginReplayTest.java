package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.chat.ChatReplyReference;
import com.ninuna.losttales.network.packet.LostTalesChatConsoleSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatHistorySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The login replay merges the kept messages and the kept console entries
 * by id, the one clock both take theirs from, so a client printing each
 * packet as it arrives prints them in the order they happened.
 */
public final class ChatLoginReplayTest {

    @Test
    public void theTwoStreamsArriveInTheOrderTheyHappened() {
        List<IMessage> packets = ChatLoginReplay.packets(
                Arrays.asList(line(10L), line(30L), line(31L), line(50L)),
                Arrays.asList(entry(20L), entry(40L)), 31L);
        assertEquals(5, packets.size());
        assertLines(packets.get(0), 10L);
        assertEntries(packets.get(1), 20L);
        assertLines(packets.get(2), 30L, 31L);
        assertEntries(packets.get(3), 40L);
        assertLines(packets.get(4), 50L);

        // Every packet says where the reader arrived: at their join line.
        LostTalesChatHistorySyncPacket joined =
                (LostTalesChatHistorySyncPacket)packets.get(2);
        assertEquals(31L, joined.getArrivalId());
        assertTrue(joined.saidBeforeArrival(joined.getMessages().get(0)));
        assertFalse(joined.saidBeforeArrival(joined.getMessages().get(1)));
        LostTalesChatConsoleSyncPacket started =
                (LostTalesChatConsoleSyncPacket)packets.get(1);
        assertTrue(started.isReplay());
        assertTrue(started.saidBeforeArrival(started.getEvents().get(0)));
        LostTalesChatConsoleSyncPacket after =
                (LostTalesChatConsoleSyncPacket)packets.get(3);
        assertFalse(after.saidBeforeArrival(after.getEvents().get(0)));
    }

    @Test
    public void aLongRunIsCutIntoFullPackets() {
        List<LostTalesChatMessagePacket> lines =
                new ArrayList<LostTalesChatMessagePacket>();
        for (int index = 1; index <= LostTalesChatHistorySyncPacket.MAX_MESSAGES + 4;
             index++) {
            lines.add(line(index));
        }
        List<ChatConsoleEvent> events = new ArrayList<ChatConsoleEvent>();
        for (int index = 0; index <= LostTalesChatConsoleSyncPacket.MAX_EVENTS;
             index++) {
            events.add(entry(1000L + index));
        }
        List<IMessage> packets = ChatLoginReplay.packets(lines, events, 1L);
        assertEquals(4, packets.size());
        assertEquals(LostTalesChatHistorySyncPacket.MAX_MESSAGES,
                ((LostTalesChatHistorySyncPacket)packets.get(0)).getMessages().size());
        assertEquals(4,
                ((LostTalesChatHistorySyncPacket)packets.get(1)).getMessages().size());
        assertEquals(LostTalesChatConsoleSyncPacket.MAX_EVENTS,
                ((LostTalesChatConsoleSyncPacket)packets.get(2)).getEvents().size());
        assertEquals(1,
                ((LostTalesChatConsoleSyncPacket)packets.get(3)).getEvents().size());
    }

    @Test
    public void nothingKeptSendsNothingAndGapsArePassedOver() {
        assertTrue(ChatLoginReplay.packets(
                Collections.<LostTalesChatMessagePacket>emptyList(), null,
                5L).isEmpty());
        List<IMessage> packets = ChatLoginReplay.packets(
                Arrays.<LostTalesChatMessagePacket>asList(null, line(3L)),
                Arrays.<ChatConsoleEvent>asList((ChatConsoleEvent)null), 5L);
        assertEquals(1, packets.size());
        assertLines(packets.get(0), 3L);
    }

    private static void assertLines(IMessage packet, long... ids) {
        assertTrue(packet instanceof LostTalesChatHistorySyncPacket);
        List<LostTalesChatMessagePacket> lines =
                ((LostTalesChatHistorySyncPacket)packet).getMessages();
        assertEquals(ids.length, lines.size());
        for (int index = 0; index < ids.length; index++) {
            assertEquals(ids[index], lines.get(index).getMessageId());
        }
    }

    private static void assertEntries(IMessage packet, long... ids) {
        assertTrue(packet instanceof LostTalesChatConsoleSyncPacket);
        List<ChatConsoleEvent> events =
                ((LostTalesChatConsoleSyncPacket)packet).getEvents();
        assertEquals(ids.length, events.size());
        for (int index = 0; index < ids.length; index++) {
            assertEquals(ids[index], events.get(index).getId());
        }
    }

    private static LostTalesChatMessagePacket line(long id) {
        return new LostTalesChatMessagePacket(ChatChannel.OOC, UUID.randomUUID(),
                "Aldric", "alice", "", 0, 0, "words " + id, 5000L, "", null, "",
                "", 0, true, id, ChatReplyReference.NONE, "");
    }

    private static ChatConsoleEvent entry(long id) {
        return new ChatConsoleEvent(id, 5000L, ChatConsoleEvent.Kind.SERVER,
                ChatConsoleEvent.Severity.INFO, "", "entry " + id);
    }
}
