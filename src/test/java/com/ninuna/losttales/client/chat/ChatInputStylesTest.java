package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMarkdown;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
        // Code is in italics, as the sent line's inline code is; a
        // spoiler is a colour, not a decoration; a mark keeps the
        // decorations of the text it stands in, so it is as wide.
        assertEquals("§o", ChatInputStyles.prefixOf(ChatMarkdown.Span.CODE));
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
        // Code wears the chat's aside tone, the Console's colour.
        assertEquals(LostTalesChatVisualStyle.asideRgb(),
                ChatInputStyles.colorOf(ChatMarkdown.Span.CODE, honey));
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

    /**
     * A command is inline code as it is typed, whole, and nothing in it
     * is markup; the words a whisper verb sends are laid out as the
     * whisper they become.
     */
    @Test
    public void aCommandIsInlineCodeAndAWhispersWordsAreTheWhisper() {
        String command = "/losttales hud **bold** @Bilbo";
        int[] styles = ChatInputStyles.layout(command);
        assertEquals(command.length(), styles.length);
        for (int index = 0; index < styles.length; index++) {
            assertEquals(ChatMarkdown.Span.CODE, styles[index]);
        }
        String whisper = "/w Bilbo hi **there**";
        int[] whisperStyles = ChatInputStyles.layout(whisper);
        int words = whisper.indexOf("hi");
        for (int index = 0; index < words; index++) {
            assertTrue(ChatInputStyles.isCode(whisperStyles, index));
        }
        assertEquals(ChatMarkdown.Span.PLAIN, whisperStyles[words]);
        assertTrue(ChatInputStyles.isBold(whisperStyles,
                whisper.indexOf("there")));
        assertFalse(ChatInputStyles.isCode(whisperStyles,
                whisper.indexOf("there")));
        // The same stretch, from the start, is what a resting bar draws
        // as code.
        assertEquals(command.length(),
                ChatInputStyles.commandCodeLength(command));
        assertEquals(words, ChatInputStyles.commandCodeLength(whisper));
        assertEquals(0, ChatInputStyles.commandCodeLength("a `b`"));
        // A whisper verb with no words after its name yet is code to its
        // end.
        assertTrue(ChatInputStyles.isCode(
                ChatInputStyles.layout("/msg Bilbo "), 10));
        // A message is its markup, and plain text is nothing to style.
        assertArrayEquals(ChatMarkdown.layout("a **b**"),
                ChatInputStyles.layout("a **b**"));
        assertNull(ChatInputStyles.layout("hello"));
    }
}
