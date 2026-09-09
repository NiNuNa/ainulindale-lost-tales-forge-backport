package com.ninuna.losttales.client.chat;

import java.util.Calendar;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A window stands a dated rule over each day's first message, the
 * oldest stamped message included, and over nothing else: a line
 * nobody stamped neither opens a day nor closes one. The rule's row is
 * a filler like the blank row between runs — nobody's line — and is
 * told apart from every other row by its own marker.
 */
public final class ChatWindowLinesDayDividerTest {

    @Before
    public void setUp() {
        ClientChatChannelViews.clear();
    }

    @After
    public void tearDown() {
        ClientChatChannelViews.clear();
    }

    @Test
    public void aRuleStandsOverEachDaysFirstMessage() {
        long monday = at(2026, Calendar.SEPTEMBER, 7, 9, 0);
        long tuesday = at(2026, Calendar.SEPTEMBER, 8, 9, 0);
        ClientChatChannelViews.recordTime(1, monday);
        ClientChatChannelViews.recordTime(2, monday + 3600000L);
        ClientChatChannelViews.recordTime(3, tuesday);
        ClientChatChannelViews.recordTime(4, tuesday + 60000L);
        // Newest first: Tuesday(4), Tuesday(3), Monday(2), Monday(1).
        String[] labels = ChatWindowLines.dayDividersAfter(
                new int[] {4, 3, 2, 1});
        assertNull(labels[0]);
        assertEquals(ChatTimestampFormatter.formatDay(tuesday), labels[1]);
        assertNull(labels[2]);
        assertEquals(ChatTimestampFormatter.formatDay(monday), labels[3]);
    }

    @Test
    public void anUnstampedLineNeitherOpensNorClosesADay() {
        long monday = at(2026, Calendar.SEPTEMBER, 7, 9, 0);
        ClientChatChannelViews.recordTime(1, monday);
        ClientChatChannelViews.recordTime(3, monday + 60000L);
        // 2 is a line printed straight into vanilla's chat: no time.
        String[] labels = ChatWindowLines.dayDividersAfter(new int[] {3, 2, 1});
        assertNull(labels[0]);
        assertNull(labels[1]);
        assertEquals(ChatTimestampFormatter.formatDay(monday), labels[2]);
        assertEquals(0, ChatWindowLines.dayDividersAfter(null).length);
        assertNull(ChatWindowLines.dayDividersAfter(new int[] {7})[0]);
    }

    @Test
    public void theRuleIsAFillerRowKnownByItsMarker() {
        ChatLine rule = new ChatLine(0,
                ChatWindowLines.dateDivider("7 September 2026"), 0);
        assertEquals("7 September 2026", ChatWindowLines.dateDividerLabel(rule));
        assertTrue(ChatWindowLines.isDateDivider(rule));
        assertTrue(ChatWindowLines.isFiller(rule));
        assertFalse(ChatWindowLines.isSpacer(rule));
        // It draws no glyph of its own: the label rides the marker.
        assertEquals("", rule.func_151461_a().getUnformattedText());
        ChatLine blank = new ChatLine(0, ChatWindowLines.SPACER, 0);
        assertNull(ChatWindowLines.dateDividerLabel(blank));
        assertTrue(ChatWindowLines.isFiller(blank));
        ChatLine message = new ChatLine(0, new ChatComponentText("hello"), 5);
        assertNull(ChatWindowLines.dateDividerLabel(message));
        assertFalse(ChatWindowLines.isFiller(message));
        assertNull(ChatWindowLines.dateDividerLabel(null));
        // A whole row of nothing between the rule and a message is not
        // laid: the rule is the gap.
        assertEquals(ChatStackRows.LINE_HEIGHT, ChatStackRows.heightOf(rule));
    }

    /** Two moments share a day key exactly when they share a calendar day. */
    @Test
    public void dayKeysFollowTheCalendar() {
        long noon = at(2026, Calendar.SEPTEMBER, 7, 12, 0);
        long lateNight = at(2026, Calendar.SEPTEMBER, 7, 23, 59);
        long nextMorning = at(2026, Calendar.SEPTEMBER, 8, 0, 1);
        assertEquals(ChatTimestampFormatter.dayKey(noon),
                ChatTimestampFormatter.dayKey(lateNight));
        assertFalse(ChatTimestampFormatter.dayKey(noon)
                == ChatTimestampFormatter.dayKey(nextMorning));
    }

    private static long at(int year, int month, int day, int hour,
                           int minute) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }
}
