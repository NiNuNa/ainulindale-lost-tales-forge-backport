package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A long message folds: past its four rows and one more it keeps four
 * and a Read more under them, above its reactions; opened it keeps every
 * row and a Show less. A message one row longer stays whole. A click
 * opens it in every view, and the view keeps the row being read.
 */
public final class ChatFoldMarkerTest {
    private static final int LINE_ID = -5;

    /** Six pixels a character. */
    private static final ChatLineWrapper.TextMetrics METRICS =
            new ChatLineWrapper.TextMetrics() {
                @Override
                public int width(String text) {
                    return text.length() * 6;
                }
            };

    @After
    public void forget() {
        ChatFoldMarker.clear();
    }

    private static ChatComponentText text(String value) {
        ChatComponentText component = new ChatComponentText(value);
        component.setChatStyle(new ChatStyle().setColor(
                EnumChatFormatting.WHITE));
        return component;
    }

    /**
     * A message of {@code words} four-letter words and a reaction. At 200
     * pixels behind the 12-pixel chevron a row holds six of them.
     */
    private static IChatComponent message(int words) {
        StringBuilder body = new StringBuilder();
        for (int index = 0; index < words; index++) {
            body.append(index == 0 ? "" : " ").append("aaaa");
        }
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text("<N>"));
        root.appendSibling(ChatLayoutMarker.bodyBreak(0xFFFFFF));
        root.appendSibling(text(body.toString()));
        root.appendSibling(ChatLayoutMarker.rowBreak());
        root.appendSibling(ChatReactionMarker.create(
                ChatEmoji.SMILE.getName(), 1, java.util.Collections.<java.util.UUID>emptyList(), 12345L, 6));
        return root;
    }

    private static List<IChatComponent> wrap(int words, boolean open) {
        return ChatLineWrapper.wrap(METRICS, message(words), 200, true,
                1.0F, 1.0F, 1.0F, null, new ChatLineWrapper.Fold(
                        ChatFoldMarker.KEPT_ROWS, open,
                        ChatFoldMarker.toggle(LINE_ID, open)));
    }

    private static IChatComponent toggleOf(IChatComponent row) {
        for (Object value : row) {
            if (ChatFoldMarker.isMarker((IChatComponent)value)) {
                return (IChatComponent)value;
            }
        }
        return null;
    }

    private static boolean isBodyRow(IChatComponent row) {
        for (Object value : row) {
            if (ChatLayoutMarker.decode((IChatComponent)value)
                    == ChatLayoutMarker.Data.BODY_ROW) {
                return true;
            }
        }
        return false;
    }

    private static int indentOf(IChatComponent row) {
        for (Object value : row) {
            ChatLayoutMarker.Data data =
                    ChatLayoutMarker.decode((IChatComponent)value);
            if (data != null && !data.anchor
                    && data != ChatLayoutMarker.Data.HEADER
                    && data != ChatLayoutMarker.Data.BODY_ROW) {
                return data.indent(true);
            }
        }
        return -1;
    }

    @Test
    public void aLongMessageKeepsFourRowsAndReadMoreAboveItsReactions() {
        // Eight rows of words: the name, four of them, Read more, chips.
        List<IChatComponent> rows = wrap(48, false);
        assertEquals(7, rows.size());
        for (int row = 1; row <= 4; row++) {
            assertNull(toggleOf(rows.get(row)));
        }
        IChatComponent toggle = toggleOf(rows.get(5));
        assertEquals(Integer.valueOf(LINE_ID), ChatFoldMarker.lineIdOf(toggle));
        assertEquals("inset like the words", 12, indentOf(rows.get(5)));
        assertTrue(isBodyRow(rows.get(5)));
        assertTrue(ChatReactionMarker.isReactionRow(rows.get(6)));
    }

    @Test
    public void anOpenMessageKeepsEveryRowAndShowLess() {
        List<IChatComponent> rows = wrap(48, true);
        assertEquals(1 + 8 + 1 + 1, rows.size());
        assertTrue(ChatFoldMarker.isMarker(toggleOf(rows.get(9))));
        assertTrue(ChatReactionMarker.isReactionRow(rows.get(10)));
    }

    @Test
    public void aMessageOneRowLongerStaysWhole() {
        List<IChatComponent> rows = wrap(30, false);
        assertEquals(1 + 5 + 1, rows.size());
        for (IChatComponent row : rows) {
            assertNull(toggleOf(row));
        }
    }

    /**
     * A paragraph starts a row of its own, inset under the words as a
     * wrapped row is, and its rows count toward the fold like any other.
     */
    @Test
    public void aParagraphStartsARowOfItsOwnAndFoldsLikeOne() {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text("<N>"));
        root.appendSibling(ChatLayoutMarker.bodyBreak(0xFFFFFF));
        root.appendSibling(text("one\ntwo"));
        List<IChatComponent> rows = ChatLineWrapper.wrap(METRICS, root, 200,
                true, 1.0F, 1.0F, 1.0F, null);
        assertEquals(3, rows.size());
        assertEquals(12, indentOf(rows.get(2)));

        ChatComponentText six = new ChatComponentText("");
        six.appendSibling(ChatLayoutMarker.anchor());
        six.appendSibling(text("<N>"));
        six.appendSibling(ChatLayoutMarker.bodyBreak(0xFFFFFF));
        six.appendSibling(text("a\nb\nc\nd\ne\nf"));
        List<IChatComponent> folded = ChatLineWrapper.wrap(METRICS, six, 200,
                true, 1.0F, 1.0F, 1.0F, null, new ChatLineWrapper.Fold(
                        ChatFoldMarker.KEPT_ROWS, false,
                        ChatFoldMarker.toggle(LINE_ID, false)));
        assertEquals("the name, four paragraphs and Read more", 6,
                folded.size());
        assertTrue(ChatFoldMarker.isMarker(toggleOf(folded.get(5))));
    }

    @Test
    public void aClickOpensTheMessageAndAnotherFoldsIt() {
        IChatComponent readMore = ChatFoldMarker.toggle(LINE_ID, false);
        assertEquals("even with chat links off",
                ChatInteractions.Action.FOLD,
                ChatInteractions.actionOf(readMore, false));
        assertNull(ChatInteractions.genuineClick(readMore));
        assertFalse(ChatFoldMarker.isOpen(LINE_ID));
        assertTrue(ChatFoldMarker.flip(LINE_ID));
        assertTrue(ChatFoldMarker.foldFor(LINE_ID).open);
        assertFalse(ChatFoldMarker.flip(LINE_ID));
        assertFalse(ChatFoldMarker.isOpen(LINE_ID));
        assertNull("vanilla's lines share id 0", ChatFoldMarker.foldFor(0));
        assertFalse(ChatFoldMarker.isMarker(text("Read more")));
    }

    /**
     * The view keeps a message's top row while it opens and its foot
     * while it folds: rows are newest first, a message's foot first.
     */
    @Test
    public void theKeptRowIsTheMessagesTopOrItsFoot() {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        lines.add(new ChatLine(0, text("newer"), -1));
        for (int row = 0; row < 3; row++) {
            lines.add(new ChatLine(0, text("row " + row), LINE_ID));
        }
        lines.add(new ChatLine(0, text("older"), -9));
        assertEquals(3, ClientChatChannelViews.rowOf(lines, LINE_ID, true));
        assertEquals(1, ClientChatChannelViews.rowOf(lines, LINE_ID, false));
        assertEquals(-1, ClientChatChannelViews.rowOf(lines, -7, true));
    }
}
