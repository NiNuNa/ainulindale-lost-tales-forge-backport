package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
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
 * told apart from every other row by its own marker. The rule is a
 * chat element like a run: it ends the run it lands in and stands
 * between two blank rows, which take the place of the blank row
 * between the runs either side of it.
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
        // The rule's own row is a whole line; its gaps are blank rows
        // laid beside it.
        assertEquals(ChatStackRows.LINE_HEIGHT, ChatStackRows.heightOf(rule));
    }

    /** Newest first: Tuesday's message, then Monday's two, the oldest last. */
    @Test
    public void aDaysRuleStandsBetweenTwoGaps() {
        List<ChatLine> lines = ChatWindowLines.assemble(pieces(3, 2, 1),
                new String[] {"Tuesday", null, "Monday"},
                new boolean[] {true, false, false}, false);
        // 3, gap, Tuesday, gap, 2, 1, gap, Monday: the blank row the two
        // runs asked for is the rule's gap, not a third blank row.
        assertEquals(8, lines.size());
        assertEquals(3, lines.get(0).getChatLineID());
        assertTrue(ChatWindowLines.isSpacer(lines.get(1)));
        assertEquals("Tuesday", ChatWindowLines.dateDividerLabel(lines.get(2)));
        assertTrue(ChatWindowLines.isSpacer(lines.get(3)));
        assertEquals(2, lines.get(4).getChatLineID());
        assertEquals(1, lines.get(5).getChatLineID());
        assertTrue(ChatWindowLines.isSpacer(lines.get(6)));
        // The oldest rule opens the history: nothing above it, no gap.
        assertEquals("Monday", ChatWindowLines.dateDividerLabel(lines.get(7)));
        // The rule and its gaps come in with the day's first message.
        assertEquals(30, lines.get(1).getUpdatedCounter());
        assertEquals(30, lines.get(2).getUpdatedCounter());
        assertEquals(30, lines.get(3).getUpdatedCounter());

        int line = ChatStackRows.LINE_HEIGHT;
        int gap = ChatStackRows.SPACER_HEIGHT;
        ChatStackRows rows = new ChatStackRows();
        rows.reset(lines, -1);
        assertEquals(gap, rows.height(1));
        assertEquals(line, rows.height(2));
        assertEquals(gap, rows.height(3));
        assertEquals(line, rows.height(7));
        assertEquals(rows.top(7) + line, rows.total());
        // The rule's one pixel, measured up the stack, against the top of
        // the newer message and the bottom of the older one: gap, line
        // and gap leave it an odd number of clear rows, so the spare one
        // stands below it — the rule sits half a pixel above its line's
        // middle, as the capitals sit in theirs. Measured to the
        // capitals of the newer message and the older one, it stands
        // exactly as far from each.
        int ruleBottom = rows.top(3)
                - LostTalesChatOverlayRenderer.DIVIDER_RULE_OFFSET - 1;
        int clearBelow = ruleBottom - rows.top(1);
        int clearAbove = rows.top(4) - (ruleBottom + 1);
        assertEquals(14, clearBelow);
        assertEquals(13, clearAbove);
        assertEquals(clearBelow + WindowStyle.ROW_TEXT_TOP,
                clearAbove + LostTalesChatOverlayRenderer.TEXT_OFFSET
                        - LostTalesUiInk.CAP_HEIGHT);
    }

    /** The rule's gaps are its own: laid whether the runs asked for a blank row or not. */
    @Test
    public void aRulesGapsDoNotDependOnTheRunsBesideIt() {
        List<ChatLine> asked = ChatWindowLines.assemble(pieces(2, 1),
                new String[] {"Tuesday", "Monday"},
                new boolean[] {true, false}, false);
        List<ChatLine> unasked = ChatWindowLines.assemble(pieces(2, 1),
                new String[] {"Tuesday", "Monday"},
                new boolean[] {false, false}, false);
        assertEquals(7, asked.size());
        assertEquals(asked.size(), unasked.size());
        for (int index = 0; index < asked.size(); index++) {
            assertEquals(ChatWindowLines.isSpacer(asked.get(index)),
                    ChatWindowLines.isSpacer(unasked.get(index)));
            assertEquals(ChatWindowLines.dateDividerLabel(asked.get(index)),
                    ChatWindowLines.dateDividerLabel(unasked.get(index)));
        }
    }

    /** Without rules the blank row between runs is laid as it always is. */
    @Test
    public void withoutARuleOnlyTheBlankRowBetweenRunsIsLaid() {
        List<ChatLine> lines = ChatWindowLines.assemble(pieces(2, 1), null,
                new boolean[] {true, false}, false);
        assertEquals(3, lines.size());
        assertTrue(ChatWindowLines.isSpacer(lines.get(1)));
        assertEquals(20, lines.get(1).getUpdatedCounter());
        // The feed ages it with the older run, the first to fade.
        List<ChatLine> fading = ChatWindowLines.assemble(pieces(2, 1), null,
                new boolean[] {true, false}, true);
        assertEquals(10, fading.get(1).getUpdatedCounter());
        assertEquals(2, ChatWindowLines.assemble(pieces(2, 1), null,
                new boolean[] {false, false}, false).size());
    }

    /**
     * A rule standing over the first unread message carries the unread
     * divider: it is found past its own gap, and no row of its own is
     * added for the divider then.
     */
    @Test
    public void aRuleOverTheFirstUnreadMessageCarriesTheDivider() {
        List<ChatLine> lines = ChatWindowLines.assemble(pieces(3, 2, 1),
                new String[] {"Tuesday", null, "Monday"},
                new boolean[] {true, false, false}, false);
        assertEquals(2, ChatWindowLines.dateDividerOver(lines, 0));
        assertEquals(-1, ChatWindowLines.dateDividerOver(lines, 4));
        assertEquals(7, ChatWindowLines.dateDividerOver(lines, 5));
        assertEquals(-1, ChatWindowLines.dateDividerOver(lines, 7));
        assertEquals(-1, ChatWindowLines.dateDividerOver(null, 0));
        assertEquals(-1, ChatWindowLines.dateDividerOver(lines, -1));
        ChatFrame frame = ChatFrame.feed();
        try {
            frame.resolveDividerRow(lines, Integer.valueOf(3));
            assertEquals(-1, frame.dividerLineIndex);
            assertEquals(2, frame.dividerDateLineIndex);
            // Not the first message of its day: a row of its own.
            frame.resolveDividerRow(lines, Integer.valueOf(2));
            assertEquals(4, frame.dividerLineIndex);
            assertEquals(-1, frame.dividerDateLineIndex);
        } finally {
            ChatFrame.clear();
        }
    }

    /** A day's first message opens a run whatever came before the rule. */
    @Test
    public void aDaysRuleOpensARun() {
        boolean[] opens = ChatWindowLines.runsOpenedByDays(
                new String[] {null, "Tuesday", null, "Monday"});
        assertFalse(opens[0]);
        assertTrue(opens[1]);
        assertFalse(opens[2]);
        assertTrue(opens[3]);
        assertEquals(0, ChatWindowLines.runsOpenedByDays(null).length);
    }

    /** One single-line message per id, newest first, on a clock of ten ticks per id. */
    private static List<ChatWindowLines.Piece> pieces(int... chatLineIds) {
        List<ChatWindowLines.Piece> pieces =
                new ArrayList<ChatWindowLines.Piece>();
        for (int index = 0; index < chatLineIds.length; index++) {
            int id = chatLineIds[index];
            List<IChatComponent> wrapped = new ArrayList<IChatComponent>();
            wrapped.add(new ChatComponentText("message " + id));
            pieces.add(new ChatWindowLines.Piece(false, 10 * id, wrapped, id));
        }
        return pieces;
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
