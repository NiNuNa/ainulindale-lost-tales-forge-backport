package com.ninuna.losttales.gui.style;

import java.util.ArrayList;
import java.util.List;

/**
 * What a text of several lines holds and how it is edited: its words, a
 * caret and the end of a selection, a limit on its length, and the lines
 * it wraps to at a width. Words wrap at a space, which the line before
 * keeps and nobody sees; a word wider than the whole line breaks where it
 * must. A new line starts a paragraph.
 *
 * <p>Free of Minecraft: a {@link Measure} says how wide words are, so a
 * test measures in characters and the field in the font's pixels.</p>
 */
public final class LostTalesUiTextModel {
    /** How wide a run of words is drawn. */
    public interface Measure {
        int width(String text);
    }

    /** One line as drawn: the characters from {@code start} up to {@code end}. */
    public static final class Line {
        public final int start;
        public final int end;

        Line(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private final int limit;
    private String text = "";
    private int caret;
    /** The selection's other end; the caret's own place while nothing is selected. */
    private int anchor;

    public LostTalesUiTextModel(int limit) {
        this.limit = Math.max(0, limit);
    }

    public String text() {
        return this.text;
    }

    /** Puts in {@code text}, as far as the limit holds, the caret after it. */
    public void setText(String words) {
        String kept = words == null ? "" : words;
        this.text = kept.length() > this.limit
                ? kept.substring(0, this.limit) : kept;
        this.caret = this.text.length();
        this.anchor = this.caret;
    }

    public int caret() {
        return this.caret;
    }

    public int anchor() {
        return this.anchor;
    }

    public boolean hasSelection() {
        return this.caret != this.anchor;
    }

    public int selectionStart() {
        return Math.min(this.caret, this.anchor);
    }

    public int selectionEnd() {
        return Math.max(this.caret, this.anchor);
    }

    public String selected() {
        return this.text.substring(selectionStart(), selectionEnd());
    }

    public void selectAll() {
        this.anchor = 0;
        this.caret = this.text.length();
    }

    /** Puts the caret at {@code index}, taking the selection along when {@code select}. */
    public void moveTo(int index, boolean select) {
        this.caret = Math.max(0, Math.min(this.text.length(), index));
        if (!select) {
            this.anchor = this.caret;
        }
    }

    /**
     * Writes {@code typed} in place of the selection, as much of it as the
     * limit leaves room for; answers whether the text changed. A character
     * the field does not take is left out by the caller.
     */
    public boolean type(String typed) {
        String words = typed == null ? "" : typed;
        int start = selectionStart();
        int end = selectionEnd();
        int room = this.limit - (this.text.length() - (end - start));
        if (room <= 0 && start == end) {
            return false;
        }
        String fitted = words.length() > room
                ? words.substring(0, Math.max(0, room)) : words;
        if (fitted.length() == 0 && start == end) {
            return false;
        }
        this.text = this.text.substring(0, start) + fitted
                + this.text.substring(end);
        this.caret = start + fitted.length();
        this.anchor = this.caret;
        return true;
    }

    /** Takes back the selection, or the character (or the word) before the caret. */
    public boolean backspace(boolean word) {
        if (hasSelection()) {
            return type("");
        }
        if (this.caret == 0) {
            return false;
        }
        this.anchor = word ? wordStartBefore(this.caret) : this.caret - 1;
        return type("");
    }

    /** Takes out the selection, or the character (or the word) after the caret. */
    public boolean delete(boolean word) {
        if (hasSelection()) {
            return type("");
        }
        if (this.caret >= this.text.length()) {
            return false;
        }
        this.anchor = word ? wordEndAfter(this.caret) : this.caret + 1;
        return type("");
    }

    /** One step left: a character, or to the start of the word. */
    public void left(boolean select, boolean word) {
        if (!select && hasSelection()) {
            moveTo(selectionStart(), false);
            return;
        }
        moveTo(word ? wordStartBefore(this.caret) : this.caret - 1, select);
    }

    /** One step right: a character, or past the end of the word. */
    public void right(boolean select, boolean word) {
        if (!select && hasSelection()) {
            moveTo(selectionEnd(), false);
            return;
        }
        moveTo(word ? wordEndAfter(this.caret) : this.caret + 1, select);
    }

    private int wordStartBefore(int index) {
        int at = index;
        while (at > 0 && Character.isWhitespace(this.text.charAt(at - 1))) {
            at--;
        }
        while (at > 0 && !Character.isWhitespace(this.text.charAt(at - 1))) {
            at--;
        }
        return at;
    }

    private int wordEndAfter(int index) {
        int at = index;
        int length = this.text.length();
        while (at < length && Character.isWhitespace(this.text.charAt(at))) {
            at++;
        }
        while (at < length && !Character.isWhitespace(this.text.charAt(at))) {
            at++;
        }
        return at;
    }

    /* ---- Lines ---- */

    /**
     * The lines the text wraps to at {@code width}: each paragraph broken
     * at the last space that fits, a word wider than the line where it
     * must. An empty text is one empty line.
     */
    public static List<Line> wrap(String text, int width, Measure measure) {
        List<Line> lines = new ArrayList<Line>();
        int paragraph = 0;
        int length = text.length();
        while (true) {
            int stop = text.indexOf('\n', paragraph);
            if (stop < 0) {
                stop = length;
            }
            wrapParagraph(text, paragraph, stop, Math.max(1, width), measure,
                    lines);
            if (stop >= length) {
                break;
            }
            paragraph = stop + 1;
        }
        return lines;
    }

    private static void wrapParagraph(String text, int from, int to, int width,
                                      Measure measure, List<Line> lines) {
        int start = from;
        if (start >= to) {
            lines.add(new Line(start, start));
            return;
        }
        while (start < to) {
            int end = start;
            int lastSpace = -1;
            while (end < to && measure.width(text.substring(start, end + 1))
                    <= width) {
                if (text.charAt(end) == ' ') {
                    lastSpace = end;
                }
                end++;
            }
            if (end >= to) {
                lines.add(new Line(start, to));
                return;
            }
            if (text.charAt(end) == ' ') {
                lines.add(new Line(start, end));
                start = end + 1;
            } else if (lastSpace >= start) {
                lines.add(new Line(start, lastSpace));
                start = lastSpace + 1;
            } else {
                int cut = Math.max(start + 1, end);
                lines.add(new Line(start, cut));
                start = cut;
            }
        }
    }

    /** The line the caret (or any index) stands on: the last one starting at or before it. */
    public static int lineOf(List<Line> lines, int index) {
        int found = 0;
        for (int at = 0; at < lines.size(); at++) {
            if (lines.get(at).start <= index) {
                found = at;
            }
        }
        return found;
    }

    /** The index on a line nearest {@code x} pixels from its start. */
    public static int indexAt(String text, Line line, int x, Measure measure) {
        int best = line.start;
        int bestDistance = Integer.MAX_VALUE;
        for (int at = line.start; at <= line.end; at++) {
            int distance = Math.abs(measure.width(text.substring(line.start,
                    at)) - x);
            if (distance < bestDistance) {
                best = at;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** The caret a line up ({@code step} -1) or down (+1), keeping its place across. */
    public void vertical(int step, boolean select, List<Line> lines,
                         Measure measure) {
        int at = lineOf(lines, this.caret);
        int target = at + step;
        if (target < 0) {
            moveTo(0, select);
            return;
        }
        if (target >= lines.size()) {
            moveTo(this.text.length(), select);
            return;
        }
        Line line = lines.get(at);
        int x = measure.width(this.text.substring(line.start,
                Math.min(this.caret, line.end)));
        moveTo(indexAt(this.text, lines.get(target), x, measure), select);
    }

    /** The caret to the start ({@code end} false) or the end of its line. */
    public void lineEdge(boolean end, boolean select, List<Line> lines) {
        Line line = lines.get(lineOf(lines, this.caret));
        moveTo(end ? line.end : line.start, select);
    }
}
