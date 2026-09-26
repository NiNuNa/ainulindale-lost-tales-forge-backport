package com.ninuna.losttales.gui.style;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A text of several lines wraps at a space, breaks a word wider than the
 * line where it must, and keeps paragraphs; typing stops at the limit,
 * and the caret walks characters, words and lines.
 */
public final class LostTalesUiTextModelTest {
    /** One pixel a character, so lines are counted in characters. */
    private static final LostTalesUiTextModel.Measure CHARACTERS =
            new LostTalesUiTextModel.Measure() {
                @Override
                public int width(String text) {
                    return text.length();
                }
            };

    @Test
    public void wordsWrapAtTheLastSpaceThatFits() {
        String text = "the quick brown fox";
        List<LostTalesUiTextModel.Line> lines =
                LostTalesUiTextModel.wrap(text, 10, CHARACTERS);
        assertEquals(2, lines.size());
        assertEquals("the quick", slice(text, lines.get(0)));
        assertEquals("brown fox", slice(text, lines.get(1)));
    }

    @Test
    public void aWordWiderThanTheLineBreaksWhereItMust() {
        String text = "abcdefghij";
        List<LostTalesUiTextModel.Line> lines =
                LostTalesUiTextModel.wrap(text, 4, CHARACTERS);
        assertEquals("abcd", slice(text, lines.get(0)));
        assertEquals("efgh", slice(text, lines.get(1)));
        assertEquals("ij", slice(text, lines.get(2)));
    }

    @Test
    public void aNewLineStartsAParagraphAndAnEmptyTextIsOneLine() {
        String text = "one\n\ntwo";
        List<LostTalesUiTextModel.Line> lines =
                LostTalesUiTextModel.wrap(text, 20, CHARACTERS);
        assertEquals(3, lines.size());
        assertEquals("", slice(text, lines.get(1)));
        assertEquals("two", slice(text, lines.get(2)));
        assertEquals(1, LostTalesUiTextModel.wrap("", 20, CHARACTERS).size());
    }

    @Test
    public void typingStopsAtTheLimitAndReplacesTheSelection() {
        LostTalesUiTextModel model = new LostTalesUiTextModel(5);
        assertTrue(model.type("hello world"));
        assertEquals("hello", model.text());
        assertFalse("a full field takes nothing more", model.type("!"));
        model.selectAll();
        assertTrue(model.type("hi"));
        assertEquals("hi", model.text());
        assertEquals(2, model.caret());
    }

    @Test
    public void backspaceTakesACharacterOrAWord() {
        LostTalesUiTextModel model = new LostTalesUiTextModel(50);
        model.setText("old grey wizard");
        assertTrue(model.backspace(false));
        assertEquals("old grey wizar", model.text());
        assertTrue(model.backspace(true));
        assertEquals("old grey ", model.text());
        model.moveTo(0, false);
        assertFalse(model.backspace(false));
    }

    @Test
    public void theCaretWalksWordsAndLinesAndSelectsWithShift() {
        LostTalesUiTextModel model = new LostTalesUiTextModel(50);
        model.setText("the quick brown fox");
        model.moveTo(0, false);
        model.right(false, true);
        assertEquals(3, model.caret());
        model.right(true, true);
        assertEquals("the", model.text().substring(0, 3));
        assertEquals(" quick", model.selected());
        List<LostTalesUiTextModel.Line> lines =
                LostTalesUiTextModel.wrap(model.text(), 10, CHARACTERS);
        model.moveTo(4, false);
        model.vertical(1, false, lines, CHARACTERS);
        assertEquals("down keeps its place across", 14, model.caret());
        model.lineEdge(true, false, lines);
        assertEquals(19, model.caret());
        model.vertical(1, false, lines, CHARACTERS);
        assertEquals("down past the last line goes to the end", 19,
                model.caret());
    }

    private static String slice(String text, LostTalesUiTextModel.Line line) {
        return text.substring(line.start, line.end);
    }
}
