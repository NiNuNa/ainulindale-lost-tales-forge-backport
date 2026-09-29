package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMessageValidator;
import java.util.Arrays;

/**
 * How the chat's field lays what is typed out in rows: the words wrap at
 * the field's width, a paragraph break ends its row, and a token the
 * field shows as one piece (an emoji, a share, a mention, a link) is
 * never split. Row {@code r} holds the raw text from its start up to the
 * next row's; a paragraph break is the last character of its row and
 * draws nothing, and a space a row breaks after stays on that row.
 */
final class ChatInputRows {
    /** What the field draws each raw character and each token as. */
    interface Measure {
        /** Display width of the raw character at {@code index}. */
        int charWidth(int index);

        /** Where the token starting at {@code index} ends, or -1 when none starts there. */
        int tokenEnd(int index);

        /** Display width of the token starting at {@code index}. */
        int tokenWidth(int index);
    }

    private ChatInputRows() {}

    /**
     * Where each row starts, the first at 0. A word longer than a row is
     * cut where the row ends; a token wider than a row stands alone on
     * one. A paragraph break at the very end opens an empty last row, so
     * the caret after it has a row to stand on.
     */
    static int[] starts(String text, Measure measure, int room) {
        int[] starts = new int[4];
        int count = 1;
        int length = text == null ? 0 : text.length();
        int rowStart = 0;
        int x = 0;
        int wordStart = -1;
        int index = 0;
        while (index < length) {
            char character = text.charAt(index);
            if (character == ChatMessageValidator.PARAGRAPH_BREAK) {
                starts = add(starts, count++, index + 1);
                rowStart = index + 1;
                x = 0;
                wordStart = -1;
                index++;
                continue;
            }
            int end = measure.tokenEnd(index);
            int width;
            if (end > index) {
                width = measure.tokenWidth(index);
            } else {
                end = index + 1;
                width = measure.charWidth(index);
            }
            if (character != ' ' && x + width > room && index > rowStart) {
                // The word moves down whole when the row has one before
                // it; a word that fills the row alone is cut here.
                int next = wordStart > rowStart ? wordStart : index;
                starts = add(starts, count++, next);
                rowStart = next;
                x = 0;
                wordStart = -1;
                index = next;
                continue;
            }
            x += width;
            if (character == ' ') {
                wordStart = end;
            }
            index = end;
        }
        return Arrays.copyOf(starts, count);
    }

    /** The row a position stands on: a position a row starts at is that row's. */
    static int rowOf(int[] starts, int position) {
        int row = 0;
        while (row + 1 < starts.length && starts[row + 1] <= position) {
            row++;
        }
        return row;
    }

    /** Where the row's drawn text ends: before the paragraph break that ends it. */
    static int drawnEnd(String text, int[] starts, int row) {
        int end = row + 1 < starts.length ? starts[row + 1] : text.length();
        return end > starts[row] && text.charAt(end - 1)
                == ChatMessageValidator.PARAGRAPH_BREAK ? end - 1 : end;
    }

    /**
     * The last place the caret stands on the row: after its words, or,
     * on a row the words wrap from, before the space the row breaks
     * after, since the place after it starts the next row.
     */
    static int caretEnd(String text, int[] starts, int row) {
        int end = drawnEnd(text, starts, row);
        boolean wraps = row + 1 < starts.length
                && end == starts[row + 1] && end > starts[row];
        return wraps ? end - 1 : end;
    }

    private static int[] add(int[] starts, int count, int start) {
        int[] grown = count < starts.length ? starts
                : Arrays.copyOf(starts, starts.length * 2);
        grown[count] = start;
        return grown;
    }
}
