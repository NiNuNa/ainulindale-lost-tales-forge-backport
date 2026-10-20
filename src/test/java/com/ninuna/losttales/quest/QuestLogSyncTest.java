package com.ninuna.losttales.quest;

import com.ninuna.losttales.client.quest.LostTalesClientQuestProgressStore;
import com.ninuna.losttales.network.packet.LostTalesQuestProgressPacket;
import com.ninuna.losttales.network.packet.LostTalesQuestSyncPacket;
import com.ninuna.losttales.quest.progress.LostTalesQuestHistoryEntry;
import com.ninuna.losttales.quest.progress.LostTalesQuestProgress;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * A client is sent its whole quest log only when anything but a running
 * quest's counts changed, or a character switch put the log in place;
 * counts alone travel as the quests whose counts
 * moved, and an unchanged log sends nothing. A log too large for one
 * packet is refused before it reaches the wire.
 */
public final class QuestLogSyncTest {

    @After
    public void forget() {
        LostTalesClientQuestProgressStore.clear();
    }

    private static LostTalesQuestProgress quest(String id, int stage, int wolves) {
        Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
        counts.put("wolves", wolves);
        return new LostTalesQuestProgress(id, stage, "stage" + stage, counts, 100L, 0L);
    }

    private static LostTalesQuestSyncPacket log(List<LostTalesQuestHistoryEntry> history,
                                                LostTalesQuestProgress... running) {
        return new LostTalesQuestSyncPacket(Arrays.asList(running), history,
                Collections.<String>emptySet(), Collections.<String>emptySet(), "",
                null, null);
    }

    private static List<LostTalesQuestHistoryEntry> none() {
        return Collections.<LostTalesQuestHistoryEntry>emptyList();
    }

    @Test
    public void aClientSentNothingYetIsSentTheWholeLog() {
        LostTalesQuestSyncPacket first = log(none(), quest("a:hunt", 0, 1));
        QuestLogSync.Step step = QuestLogSync.step(null, first, false);
        assertSame(first, step.whole);
        assertFalse("an ordinary log brings news", step.whole.isReplacement());
        assertNull(step.counts);
    }

    /**
     * The log a character switch put in place goes whole and marked, even
     * where only counts differ from what the client was sent, so the client
     * takes it without a banner for each quest; the mark is no part of the
     * log's shape.
     */
    @Test
    public void aSwitchedLogGoesWholeAndMarked() {
        QuestLogSync.Sent sent = QuestLogSync.step(null,
                log(none(), quest("a:hunt", 0, 1)), false).sent;
        QuestLogSync.Step switched = QuestLogSync.step(sent,
                log(none(), quest("a:hunt", 0, 3)), true);
        assertNotNull(switched.whole);
        assertNull(switched.counts);
        assertTrue(switched.whole.isReplacement());
        assertTrue(Arrays.equals(sent.shape, switched.sent.shape));

        ByteBuf buffer = Unpooled.buffer();
        switched.whole.toBytes(buffer);
        LostTalesQuestSyncPacket decoded = new LostTalesQuestSyncPacket();
        decoded.fromBytes(buffer);
        assertFalse(decoded.isMalformed());
        assertTrue("the mark travels", decoded.isReplacement());
        assertEquals(Integer.valueOf(3), decoded.getActiveQuests().get(0)
                .getObjectiveProgress().get("wolves"));
    }

    @Test
    public void countsAloneTravelAsTheQuestsWhoseCountsMoved() {
        QuestLogSync.Sent sent = QuestLogSync.step(null,
                log(none(), quest("a:hunt", 0, 1), quest("a:gather", 0, 4)), false).sent;
        QuestLogSync.Step kill = QuestLogSync.step(sent,
                log(none(), quest("a:hunt", 0, 2), quest("a:gather", 0, 4)), false);
        assertNull(kill.whole);
        assertNotNull(kill.counts);
        assertEquals(1, kill.counts.getQuests().size());
        assertEquals("a:hunt", kill.counts.getQuests().get(0).getQuestId());
        assertEquals(Integer.valueOf(2), kill.counts.getQuests().get(0)
                .getObjectiveProgress().get("wolves"));

        QuestLogSync.Step again = QuestLogSync.step(kill.sent,
                log(none(), quest("a:hunt", 0, 2), quest("a:gather", 0, 4)), false);
        assertNull("an unchanged log sends nothing", again.whole);
        assertNull(again.counts);
    }

    @Test
    public void anythingButCountsSendsTheWholeLog() {
        QuestLogSync.Sent sent = QuestLogSync.step(null,
                log(none(), quest("a:hunt", 0, 1)), false).sent;
        assertNotNull("a new stage", QuestLogSync.step(sent,
                log(none(), quest("a:hunt", 1, 1)), false).whole);
        assertNotNull("a new quest", QuestLogSync.step(sent,
                log(none(), quest("a:hunt", 0, 1), quest("a:other", 0, 0)), false).whole);
        assertNotNull("a History line", QuestLogSync.step(sent,
                log(Collections.singletonList(new LostTalesQuestHistoryEntry(
                        "a:old", LostTalesQuestHistoryEntry.Outcome.COMPLETED, "", 5L,
                        Collections.<String>emptySet(), 1,
                        Collections.<String>emptyList())),
                        quest("a:hunt", 0, 1)), false).whole);
    }

    @Test
    public void aLogTooLargeForOnePacketIsRefusedBeforeTheWire() {
        StringBuilder detail = new StringBuilder();
        for (int index = 0; index < 8000; index++) {
            detail.append('x');
        }
        List<LostTalesQuestHistoryEntry> history =
                new ArrayList<LostTalesQuestHistoryEntry>();
        for (int index = 0; index < 300; index++) {
            history.add(new LostTalesQuestHistoryEntry("a:q" + index,
                    LostTalesQuestHistoryEntry.Outcome.FAILED, detail.toString(), 5L,
                    Collections.<String>emptySet(), 0,
                    Collections.<String>emptyList()));
        }
        try {
            QuestLogSync.step(null, log(history), false);
            fail("a log past the packet's bound is refused");
        } catch (IllegalStateException expected) {
            // refused
        }
    }

    @Test
    public void theCountsPacketRoundTripsAndRefusesTrailingBytes() {
        LostTalesQuestProgressPacket packet = new LostTalesQuestProgressPacket(
                Arrays.asList(quest("a:hunt", 2, 7)));
        ByteBuf buffer = Unpooled.buffer();
        packet.toBytes(buffer);
        LostTalesQuestProgressPacket decoded = new LostTalesQuestProgressPacket();
        decoded.fromBytes(buffer.copy());
        assertFalse(decoded.isMalformed());
        assertEquals(2, decoded.getQuests().get(0).getStageIndex());
        assertEquals(Integer.valueOf(7),
                decoded.getQuests().get(0).getObjectiveProgress().get("wolves"));
        buffer.writeByte(1);
        LostTalesQuestProgressPacket trailing = new LostTalesQuestProgressPacket();
        trailing.fromBytes(buffer);
        assertTrue(trailing.isMalformed());
        assertTrue(trailing.getQuests().isEmpty());
    }

    @Test
    public void theClientLaysCountsOverTheLogItHolds() {
        LostTalesClientQuestProgressStore.update(
                Arrays.asList(quest("a:hunt", 0, 1), quest("a:gather", 0, 4)),
                Collections.<LostTalesQuestHistoryEntry>emptyList(),
                Collections.singleton("a:hunt"), Collections.<String>emptySet(), "");
        List<LostTalesQuestProgress> active = new ArrayList<LostTalesQuestProgress>(
                LostTalesClientQuestProgressStore.withCounts(Arrays.asList(
                        quest("a:hunt", 0, 3), quest("a:unknown", 0, 9))));
        assertEquals(2, active.size());
        assertEquals(Integer.valueOf(3),
                active.get(0).getObjectiveProgress().get("wolves"));
        LostTalesClientQuestProgressStore.replaceActiveQuests(active);
        assertEquals(Integer.valueOf(3), LostTalesClientQuestProgressStore
                .getActiveQuest("a:hunt").getObjectiveProgress().get("wolves"));
        assertFalse(LostTalesClientQuestProgressStore.isQuestActive("a:unknown"));
        assertTrue("the rest of the log stays",
                LostTalesClientQuestProgressStore.isQuestPinned("a:hunt"));
    }
}
