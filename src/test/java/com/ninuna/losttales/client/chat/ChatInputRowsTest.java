package com.ninuna.losttales.client.chat;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * The chat field's rows: words wrap whole at the width, a paragraph
 * break ends its row, a token is never split, a word wider than a row
 * is cut, and the caret has a place at every row's end.
 */
public final class ChatInputRowsTest {

    /** Six pixels a character; tokens from {@code start} to {@code end} of their own width. */
    private static ChatInputRows.Measure measure(final Map<Integer, int[]> tokens) {
        return new ChatInputRows.Measure() {
            @Override
            public int charWidth(int index) {
                return 6;
            }

            @Override
            public int tokenEnd(int index) {
                int[] token = tokens.get(Integer.valueOf(index));
                return token == null ? -1 : token[0];
            }

            @Override
            public int tokenWidth(int index) {
                int[] token = tokens.get(Integer.valueOf(index));
                return token == null ? 0 : token[1];
            }
        };
    }

    private static int[] starts(String text, int room) {
        return ChatInputRows.starts(text, measure(new HashMap<Integer, int[]>()),
                room);
    }

    @Test
    public void wordsWrapWholeAtTheWidth() {
        String text = "aaaa bbbb cccc";
        int[] starts = starts(text, 60);
        assertArrayEquals(new int[] {0, 10}, starts);
        assertEquals(0, ChatInputRows.rowOf(starts, 9));
        assertEquals("the place a row starts at is that row's",
                1, ChatInputRows.rowOf(starts, 10));
        assertEquals(10, ChatInputRows.drawnEnd(text, starts, 0));
        assertEquals("before the space the row breaks after",
                9, ChatInputRows.caretEnd(text, starts, 0));
        assertEquals(14, ChatInputRows.caretEnd(text, starts, 1));
    }

    @Test
    public void aParagraphEndsItsRow() {
        String text = "ab\ncd";
        int[] starts = starts(text, 600);
        assertArrayEquals(new int[] {0, 3}, starts);
        assertEquals("the break draws nothing", 2,
                ChatInputRows.drawnEnd(text, starts, 0));
        assertEquals(2, ChatInputRows.caretEnd(text, starts, 0));
        assertArrayEquals("a break at the end opens an empty row",
                new int[] {0, 3}, starts("ab\n", 600));
    }

    @Test
    public void aWordWiderThanARowIsCutAndATokenStaysWhole() {
        assertArrayEquals(new int[] {0, 10}, starts("aaaaaaaaaaaaaaa", 60));
        Map<Integer, int[]> tokens = new HashMap<Integer, int[]>();
        tokens.put(Integer.valueOf(2), new int[] {9, 10});
        // "x " is 12, the token 10 more: it moves down whole, and the "y"
        // after it and its space moves down again.
        assertArrayEquals(new int[] {0, 2, 10}, ChatInputRows.starts(
                "x :smile: y", measure(tokens), 20));
    }
}
