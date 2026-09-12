package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * The timestamp stands at the middle of the whole message: a name on
 * one row and its words on the next are stamped between them, a reply's
 * quote, name and words on their middle row, and a message on one row
 * where it always was. The list is newest first; the wrapped rows of a
 * message stand toward the newer end of its stamped row and a leading
 * quote row toward the older.
 */
public final class ChatTimestampCentreTest {

    private static final int LINE = ChatStackRows.LINE_HEIGHT;

    @Test
    public void aMessageOnOneRowIsStampedWhereItStands() {
        List<ChatLine> lines = lines(new int[] {2, 1}, new boolean[] {false, false});
        ChatStackRows rows = rows(lines);
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 0, rows, -1));
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 1, rows, -1));
    }

    @Test
    public void aNameRowAndItsWordsAreStampedBetweenThem() {
        // Newest first: the words of message 1 (a wrapped continuation),
        // then its header row, then an older message on one row.
        List<ChatLine> lines = lines(new int[] {1, 1, 0},
                new boolean[] {false, false, false});
        ChatStackRows rows = rows(lines);
        // The header at index 1 owns the stamp: one row of words below
        // it, none above, so the stamp moves down half a row.
        assertEquals(LINE / 2, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 1, rows, -1));
        // Three rows of words: down a row and a half.
        List<ChatLine> longer = lines(new int[] {1, 1, 1, 1},
                new boolean[] {false, false, false, false});
        assertEquals(LINE * 3 / 2, LostTalesChatOverlayRenderer.messageCentreShift(
                longer, 3, rows(longer), -1));
    }

    @Test
    public void aReplyWithItsQuoteAboveIsStampedOnItsMiddleRow() {
        // Newest first: words, header, quote row — all one message.
        List<ChatLine> lines = lines(new int[] {1, 1, 1},
                new boolean[] {false, false, false});
        ChatStackRows rows = rows(lines);
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 1, rows, -1));
    }

    @Test
    public void aBlankRowOrAnotherMessageEndsTheSpan() {
        // Newest first: another message, a blank row, then the header of
        // message 1 with nothing wrapped under it.
        List<ChatLine> lines = lines(new int[] {2, 0, 1},
                new boolean[] {false, true, false});
        ChatStackRows rows = rows(lines);
        assertEquals(0, LostTalesChatOverlayRenderer.messageCentreShift(
                lines, 2, rows, -1));
        // The divider's own row between the rows of one message changes
        // nothing about the message's height.
        List<ChatLine> divided = lines(new int[] {1, 1, 0},
                new boolean[] {false, false, false});
        assertEquals(LINE / 2, LostTalesChatOverlayRenderer.messageCentreShift(
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

    private static List<ChatLine> lines(int[] ids, boolean[] spacers) {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (int index = 0; index < ids.length; index++) {
            lines.add(spacers[index]
                    ? new ChatLine(0, ChatWindowLines.SPACER, ids[index])
                    : new ChatLine(0, new ChatComponentText("x"), ids[index]));
        }
        return lines;
    }
}
