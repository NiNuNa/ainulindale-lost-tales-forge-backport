package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The timestamp area shows a message's time under the pointer on the
 * first row of a message with no name row — the rest of a group, a line
 * that is words from its first row — and nowhere else: a message that
 * names its speaker wears its time behind the name. The list is newest
 * first, so a message's first row stands toward the older end.
 */
public final class ChatColumnTimeTest {

    @Test
    public void aGroupedMessageShowsItsTimeOnItsFirstRow() {
        // Newest first: a grouped message on one row, then the words and
        // the name row of the message that opened the group.
        List<ChatLine> lines = list(words(2), words(1), header(1));
        assertTrue(LostTalesChatOverlayRenderer.showsTimeInColumn(lines, 0));
        assertFalse(LostTalesChatOverlayRenderer.showsTimeInColumn(lines, 1));
        assertFalse(LostTalesChatOverlayRenderer.showsTimeInColumn(lines, 2));
    }

    @Test
    public void aWrappedMessageShowsItOnceOnItsFirstRow() {
        List<ChatLine> lines = list(words(3), words(3));
        assertFalse(LostTalesChatOverlayRenderer.showsTimeInColumn(lines, 0));
        assertTrue(LostTalesChatOverlayRenderer.showsTimeInColumn(lines, 1));
    }

    @Test
    public void quotesReactionsAndBlanksShowNone() {
        List<ChatLine> quote = list(words(4), quote(4));
        assertFalse(LostTalesChatOverlayRenderer.showsTimeInColumn(quote, 1));
        List<ChatLine> blank = list(words(5),
                new ChatLine(0, ChatWindowLines.SPACER, 0), words(6));
        assertFalse(LostTalesChatOverlayRenderer.showsTimeInColumn(blank, 1));
        assertTrue(LostTalesChatOverlayRenderer.showsTimeInColumn(blank, 0));
        List<ChatLine> reacted = list(reactions(7), words(7));
        assertFalse(LostTalesChatOverlayRenderer.showsTimeInColumn(reacted, 0));
        assertTrue(LostTalesChatOverlayRenderer.showsTimeInColumn(reacted, 1));
    }

    private static List<ChatLine> list(ChatLine... rows) {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (ChatLine row : rows) {
            lines.add(row);
        }
        return lines;
    }

    private static ChatLine words(int id) {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(ChatBodyMarker.separator(
                ChatLineWrapper.BODY_SEPARATOR, -1));
        row.appendSibling(new ChatComponentText("words"));
        row.appendSibling(ChatLayoutMarker.bodyRow());
        return new ChatLine(0, row, id);
    }

    private static ChatLine header(int id) {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(new ChatComponentText("Name"));
        row.appendSibling(ChatLayoutMarker.header());
        return new ChatLine(0, row, id);
    }

    private static ChatLine quote(int id) {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(ChatReplyMarker.apply(
                new ChatComponentText("quoted"), 0x336633, 9L));
        return new ChatLine(0, row, id);
    }

    private static ChatLine reactions(int id) {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(ChatReactionMarker.create("smile", 1, false, 1L, 6));
        return new ChatLine(0, row, id);
    }
}
