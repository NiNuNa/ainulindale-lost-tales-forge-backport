package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The timestamp stands on the words: a name on a row of its own and a
 * reply's quote above it are left out, so a message is stamped beside
 * the rows its body stands on, centred on them when they wrap, and a
 * message on one row is stamped where it stands. The list is newest
 * first; the wrapped rows of a message stand toward the newer end of
 * its stamped row and a leading quote row toward the older.
 */
public final class ChatTimestampCentreTest {

    private static final int LINE = ChatStackRows.LINE_HEIGHT;

    @Test
    public void aMessageOnOneRowIsStampedWhereItStands() {
        List<ChatLine> lines = list(words(2), words(1));
        ChatStackRows rows = rows(lines);
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 0, rows, -1));
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 1, rows, -1));
    }

    @Test
    public void aNameOnItsOwnRowIsStampedOnTheWordsBelowIt() {
        // Newest first: the words of message 1, then its name's row,
        // then an older message on one row.
        List<ChatLine> lines = list(words(1), name(1), words(0));
        // The name's row owns the stamp; the words are the next row
        // down, so the stamp moves down a whole row onto them.
        assertEquals(LINE, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 1, rows(lines), -1));
        // Three rows of words: centred on the middle one, two rows down.
        List<ChatLine> longer = list(plain(1), plain(1), words(1), name(1));
        assertEquals(LINE * 2, LostTalesChatOverlayRenderer.messageCentreShift(
                longer, 3, rows(longer), -1));
    }

    @Test
    public void aReplyIsStampedOnItsWordsNotOnItsQuoteOrName() {
        // Newest first: words, name, quote, all one message: the third
        // row down the screen carries the stamp.
        List<ChatLine> lines = list(words(1), name(1), plain(1));
        assertEquals(LINE, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 1, rows(lines), -1));
    }

    @Test
    public void aGroupedLineAndALineWithNoNameAreCentredOnTheirOwnRows() {
        // A grouped line's words open on the stamped row itself; with
        // one wrapped row under it the stamp stands between the two.
        List<ChatLine> grouped = list(plain(1), words(1));
        assertEquals(LINE / 2, LostTalesChatOverlayRenderer.messageCentreShift(
                grouped, 1, rows(grouped), -1));
        // A system line has no chevron anywhere and is words throughout.
        List<ChatLine> system = list(plain(1), plain(1));
        assertEquals(LINE / 2, LostTalesChatOverlayRenderer.messageCentreShift(
                system, 1, rows(system), -1));
    }

    @Test
    public void reactionsAreNotTheWords() {
        // Newest first: the reaction row, the words, the name.
        List<ChatLine> lines = list(reactions(1), words(1), name(1));
        assertEquals(LINE, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 2, rows(lines), -1));
    }

    @Test
    public void aBlankRowOrAnotherMessageEndsTheSpan() {
        // Newest first: another message, a blank row, then message 1's
        // name with nothing under it.
        List<ChatLine> lines = list(words(2), spacer(0), name(1));
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 2, rows(lines), -1));
        // The divider's own row between the rows of one message changes
        // nothing about where its words are.
        List<ChatLine> divided = list(words(1), name(1), words(0));
        assertEquals(LINE, LostTalesChatOverlayRenderer.messageCentreShift(
                divided, 1, rows(divided, 2), 2));
    }

    private static ChatStackRows rows(List<ChatLine> lines) {
        return rows(lines, -1);
    }

    private static ChatStackRows rows(List<ChatLine> lines, int dividerIndex) {
        ChatStackRows rows = new ChatStackRows();
        rows.reset(lines, dividerIndex);
        return rows;
    }

    private static List<ChatLine> list(ChatLine... rows) {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (ChatLine row : rows) {
            lines.add(row);
        }
        return lines;
    }

    /** A row the body opens on: the chevron, then the words. */
    private static ChatLine words(int id) {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(ChatBodyMarker.separator(
                ChatLineWrapper.BODY_SEPARATOR, -1));
        row.appendSibling(new ChatComponentText("words"));
        return new ChatLine(0, row, id);
    }

    /** A name on a row of its own, as a message's header stands. */
    private static ChatLine name(int id) {
        return new ChatLine(0, new ChatComponentText("<Name>"), id);
    }

    /** A row with no chevron: a wrapped continuation, a quote, a system line. */
    private static ChatLine plain(int id) {
        return new ChatLine(0, new ChatComponentText("x"), id);
    }

    private static ChatLine reactions(int id) {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(ChatReactionMarker.create("smile", 1, false, 1L, 6));
        return new ChatLine(0, row, id);
    }

    private static ChatLine spacer(int id) {
        return new ChatLine(0, ChatWindowLines.SPACER, id);
    }
}
