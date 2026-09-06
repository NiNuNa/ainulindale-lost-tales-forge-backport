package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMarkdown;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** The rules the input field previews the markup by, apart from any font. */
public final class ChatInputStylesTest {

    @Test
    public void decorationsFollowTheStyleAndMarksWearTheirSurroundings() {
        assertEquals("", ChatInputStyles.prefixOf(ChatMarkdown.Span.PLAIN));
        assertEquals("\u00a7l", ChatInputStyles.prefixOf(ChatMarkdown.Span.BOLD));
        assertEquals("\u00a7l\u00a7o\u00a7n\u00a7m", ChatInputStyles.prefixOf(
                ChatMarkdown.Span.BOLD | ChatMarkdown.Span.ITALIC
                        | ChatMarkdown.Span.UNDERLINE
                        | ChatMarkdown.Span.STRIKETHROUGH));
        // Code and spoiler are colours, not decorations; a mark keeps the
        // decorations of the text it stands in, so it is as wide.
        assertEquals("", ChatInputStyles.prefixOf(ChatMarkdown.Span.CODE));
        assertEquals("", ChatInputStyles.prefixOf(ChatMarkdown.Span.SPOILER));
        assertEquals("\u00a7l", ChatInputStyles.prefixOf(
                ChatMarkdown.Span.BOLD | ChatMarkdown.Span.MARK));
    }

    @Test
    public void subduedColoursNeverHideAMentionOutsideCode() {
        int honey = 0xF7CF91;
        int ivory = LostTalesChatVisualStyle.IVORY;
        assertEquals(ivory, ChatInputStyles.colorOf(ChatMarkdown.Span.BOLD, ivory));
        assertEquals(honey, ChatInputStyles.colorOf(ChatMarkdown.Span.BOLD, honey));
        assertEquals(honey, ChatInputStyles.colorOf(ChatMarkdown.Span.SPOILER, honey));
        assertFalse(ivory == ChatInputStyles.colorOf(ChatMarkdown.Span.SPOILER, ivory));
        assertFalse(honey == ChatInputStyles.colorOf(ChatMarkdown.Span.CODE, honey));
        assertFalse(honey == ChatInputStyles.colorOf(ChatMarkdown.Span.MARK, honey));
        assertEquals(ChatInputStyles.colorOf(ChatMarkdown.Span.MARK, honey),
                ChatInputStyles.colorOf(ChatMarkdown.Span.BOLD | ChatMarkdown.Span.MARK,
                        ivory));
    }

    @Test
    public void widthAndCodeAreReadFromTheLayoutSafely() {
        int[] styles = ChatMarkdown.layout("**a** `b`");
        assertTrue(ChatInputStyles.isBold(styles, 2));
        // The marks around a span wear the style outside it, so the
        // outermost marks are plain and as wide as plain text.
        assertFalse(ChatInputStyles.isBold(styles, 0));
        assertFalse(ChatInputStyles.isBold(styles, 5));
        assertTrue(ChatInputStyles.isCode(styles, 7));
        assertFalse(ChatInputStyles.isCode(styles, 6));
        assertFalse(ChatInputStyles.isBold(null, 2));
        assertFalse(ChatInputStyles.isBold(styles, -1));
        assertFalse(ChatInputStyles.isBold(styles, styles.length));
        assertEquals(ChatMarkdown.Span.PLAIN, ChatInputStyles.styleAt(null, 0));
    }
}
