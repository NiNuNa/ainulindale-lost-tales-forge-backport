package com.ninuna.losttales.chat.server;

import com.ninuna.losttales.chat.ChatConsoleEvent;
import com.ninuna.losttales.network.packet.LostTalesChatConsoleSyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatHistorySyncPacket;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * The packets a player who has just joined is caught up with, in the
 * order things happened. The kept messages they may read and, for staff,
 * the kept console entries are two streams, but messages and entries
 * take their ids from one clock ({@link ChatMessageIdAllocator}), so
 * merged by id they are one: the Server starting comes before a player
 * joining, whichever stream each is kept in. The merge is cut into
 * packets of one kind each, in its order, and the client shows each
 * packet as it arrives, so it prints the two streams interleaved as they
 * happened. Every packet says where the player arrived. Pure: no server
 * and no network.
 */
final class ChatLoginReplay {
    private ChatLoginReplay() {}

    /**
     * The replay of {@code lines} and {@code events}, each oldest first,
     * merged by id into packets of at most a packet's worth of one kind.
     * A message goes before an entry with the same id, which the clock
     * never hands out twice anyway.
     */
    static List<IMessage> packets(List<LostTalesChatMessagePacket> lines,
                                  List<ChatConsoleEvent> events,
                                  long arrivalId) {
        List<IMessage> packets = new ArrayList<IMessage>();
        List<LostTalesChatMessagePacket> lineRun =
                new ArrayList<LostTalesChatMessagePacket>();
        List<ChatConsoleEvent> eventRun = new ArrayList<ChatConsoleEvent>();
        int lineCount = lines == null ? 0 : lines.size();
        int eventCount = events == null ? 0 : events.size();
        int lineIndex = 0;
        int eventIndex = 0;
        while (lineIndex < lineCount || eventIndex < eventCount) {
            LostTalesChatMessagePacket line = lineIndex < lineCount
                    ? lines.get(lineIndex) : null;
            ChatConsoleEvent event = eventIndex < eventCount
                    ? events.get(eventIndex) : null;
            if (lineIndex < lineCount && line == null) {
                lineIndex++;
                continue;
            }
            if (eventIndex < eventCount && event == null) {
                eventIndex++;
                continue;
            }
            if (event == null || (line != null
                    && line.getMessageId() <= event.getId())) {
                flushEvents(packets, eventRun, arrivalId);
                lineRun.add(line);
                lineIndex++;
                if (lineRun.size() >= LostTalesChatHistorySyncPacket.MAX_MESSAGES) {
                    flushLines(packets, lineRun, arrivalId);
                }
            } else {
                flushLines(packets, lineRun, arrivalId);
                eventRun.add(event);
                eventIndex++;
                if (eventRun.size() >= LostTalesChatConsoleSyncPacket.MAX_EVENTS) {
                    flushEvents(packets, eventRun, arrivalId);
                }
            }
        }
        flushLines(packets, lineRun, arrivalId);
        flushEvents(packets, eventRun, arrivalId);
        return packets;
    }

    private static void flushLines(List<IMessage> packets,
                                   List<LostTalesChatMessagePacket> run,
                                   long arrivalId) {
        if (!run.isEmpty()) {
            packets.add(new LostTalesChatHistorySyncPacket(run, arrivalId));
            run.clear();
        }
    }

    private static void flushEvents(List<IMessage> packets,
                                    List<ChatConsoleEvent> run,
                                    long arrivalId) {
        if (!run.isEmpty()) {
            packets.add(new LostTalesChatConsoleSyncPacket(run, arrivalId));
            run.clear();
        }
    }
}
