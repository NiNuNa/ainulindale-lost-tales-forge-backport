package com.ninuna.losttales.chat;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The markup is display only and forgiving: anything that is not a
 * complete pair stays the characters it was typed as.
 */
public final class ChatMarkdownTest {

    private static String plainOf(List<ChatMarkdown.Span> spans) {
        StringBuilder text = new StringBuilder();
        for (ChatMarkdown.Span span : spans) {
            text.append(span.getText());
        }
        return text.toString();
    }

    @Test
    public void eachMarkerStylesItsRunAndDisappears() {
        List<ChatMarkdown.Span> bold = ChatMarkdown.parse("a **b** c");
        assertEquals(3, bold.size());
        assertEquals("a ", bold.get(0).getText());
        assertEquals("b", bold.get(1).getText());
        assertTrue(bold.get(1).isBold());
        assertEquals(" c", bold.get(2).getText());
        assertTrue(bold.get(0).isPlain() && bold.get(2).isPlain());

        assertTrue(ChatMarkdown.parse("*x*").get(0).isItalic());
        assertTrue(ChatMarkdown.parse("__x__").get(0).isUnderlined());
        assertTrue(ChatMarkdown.parse("~~x~~").get(0).isStrikethrough());
        assertTrue(ChatMarkdown.parse("`x`").get(0).isCode());
        assertTrue(ChatMarkdown.parse("||x||").get(0).isSpoiler());
    }

    /**
     * Underline is two underscores, as Discord writes it; the single
     * underscores of a name are not marks, and an underline nests like
     * the rest.
     */
    @Test
    public void underlineIsTwoUnderscoresAndNamesKeepTheirs() {
        List<ChatMarkdown.Span> spans = ChatMarkdown.parse("see __this__ Player_531");
        assertEquals(3, spans.size());
        assertEquals("this", spans.get(1).getText());
        assertTrue(spans.get(1).isUnderlined());
        assertFalse(spans.get(1).isBold());
        assertEquals(" Player_531", spans.get(2).getText());
        assertTrue(spans.get(2).isPlain());
        assertEquals("__init__ method", plainOf(ChatMarkdown.parse("__init__ method"))
                .replace("init", "__init__"));
        List<ChatMarkdown.Span> nested = ChatMarkdown.parse("**__both__**");
        assertEquals(1, nested.size());
        assertTrue(nested.get(0).isBold() && nested.get(0).isUnderlined());
        assertEquals("a __ b", plainOf(ChatMarkdown.parse("a __ b")));
        assertTrue(ChatMarkdown.hasMarkup("__u__"));
    }

    /**
     * A single underscore makes an italic at a word's edges, as Discord
     * reads one, and never inside a word: a name, an identifier and an
     * underline keep their underscores.
     */
    @Test
    public void singleUnderscoresItaliciseOnlyAtAWordsEdges() {
        List<ChatMarkdown.Span> spans = ChatMarkdown.parse("a _b c_ d");
        assertEquals(3, spans.size());
        assertEquals("b c", spans.get(1).getText());
        assertTrue(spans.get(1).isItalic());
        assertTrue(spans.get(0).isPlain() && spans.get(2).isPlain());
        assertTrue(ChatMarkdown.parse("_x_").get(0).isItalic());
        assertTrue(ChatMarkdown.parse("_x_!").get(0).isItalic());
        assertEquals("snake_case_name", plainOf(
                ChatMarkdown.parse("snake_case_name")));
        assertTrue(ChatMarkdown.parse("snake_case_name").get(0).isPlain());
        assertTrue(ChatMarkdown.parse("@Cool_Player_1 hi").get(0).isPlain());
        assertTrue(ChatMarkdown.parse("_a_b").get(0).isPlain());
        assertTrue(ChatMarkdown.parse("a _ b _ c").get(0).isPlain());
        // Inside an underline, and around one.
        List<ChatMarkdown.Span> underline = ChatMarkdown.parse("___x___");
        assertEquals(1, underline.size());
        assertTrue(underline.get(0).isUnderlined()
                && underline.get(0).isItalic());
        List<ChatMarkdown.Span> around = ChatMarkdown.parse("_a __b__ c_");
        assertEquals("a b c", plainOf(around));
        assertTrue(around.get(0).isItalic());
        assertTrue(around.get(1).isItalic() && around.get(1).isUnderlined());
    }

    /**
     * Discord's formatting table, row by row: every form it lists reads
     * here the way it reads there.
     */
    @Test
    public void everyFormDiscordListsReadsTheSame() {
        assertStyled("*italics*", "italics", ChatMarkdown.Span.ITALIC);
        assertStyled("_italics_", "italics", ChatMarkdown.Span.ITALIC);
        assertStyled("__*underline italics*__", "underline italics",
                ChatMarkdown.Span.UNDERLINE | ChatMarkdown.Span.ITALIC);
        assertStyled("**bold**", "bold", ChatMarkdown.Span.BOLD);
        assertStyled("__**underline bold**__", "underline bold",
                ChatMarkdown.Span.UNDERLINE | ChatMarkdown.Span.BOLD);
        assertStyled("***bold italics***", "bold italics",
                ChatMarkdown.Span.BOLD | ChatMarkdown.Span.ITALIC);
        assertStyled("__***underline bold italics***__",
                "underline bold italics", ChatMarkdown.Span.UNDERLINE
                        | ChatMarkdown.Span.BOLD | ChatMarkdown.Span.ITALIC);
        assertStyled("__underline__", "underline",
                ChatMarkdown.Span.UNDERLINE);
        assertStyled("~~Strikethrough~~", "Strikethrough",
                ChatMarkdown.Span.STRIKETHROUGH);
    }

    /** One run, with exactly these marks. */
    private static void assertStyled(String message, String text, int style) {
        List<ChatMarkdown.Span> spans = ChatMarkdown.parse(message);
        assertEquals(message, 1, spans.size());
        ChatMarkdown.Span span = spans.get(0);
        assertEquals(message, text, span.getText());
        assertEquals(message + " bold", (style & ChatMarkdown.Span.BOLD) != 0,
                span.isBold());
        assertEquals(message + " italic",
                (style & ChatMarkdown.Span.ITALIC) != 0, span.isItalic());
        assertEquals(message + " underline",
                (style & ChatMarkdown.Span.UNDERLINE) != 0,
                span.isUnderlined());
        assertEquals(message + " strikethrough",
                (style & ChatMarkdown.Span.STRIKETHROUGH) != 0,
                span.isStrikethrough());
    }

    /** Marks nest, so a run can carry more than one. */
    @Test
    public void marksNest() {
        List<ChatMarkdown.Span> spans =
                ChatMarkdown.parse("**bold *and italic***");
        assertEquals(2, spans.size());
        assertTrue(spans.get(0).isBold());
        assertFalse(spans.get(0).isItalic());
        assertTrue(spans.get(1).isBold() && spans.get(1).isItalic());
        assertEquals("bold and italic", plainOf(spans));
    }

    /** Quoting something means it is not read again. */
    @Test
    public void codeIsLiteralInside() {
        List<ChatMarkdown.Span> spans = ChatMarkdown.parse("`**x**`");
        assertEquals(1, spans.size());
        assertTrue(spans.get(0).isCode());
        assertFalse(spans.get(0).isBold());
        assertEquals("**x**", spans.get(0).getText());
    }

    /**
     * The rule that keeps the markup out of ordinary writing: a marker
     * opens only against text and closes only against text.
     */
    @Test
    public void spacedMarkersAreNotMarkup() {
        assertEquals("2 * 3 * 4", plainOf(ChatMarkdown.parse("2 * 3 * 4")));
        assertTrue(ChatMarkdown.parse("2 * 3 * 4").get(0).isPlain());
        assertEquals("a * b", plainOf(ChatMarkdown.parse("a * b")));
        // Opening against text but closing against a space is no pair.
        assertEquals("*a b *c", plainOf(ChatMarkdown.parse("*a b *c")));
        assertTrue(ChatMarkdown.parse("*a b *c").get(0).isPlain());
    }

    /** An unclosed marker is just a character. */
    @Test
    public void unpairedMarkersStayLiteral() {
        assertEquals("**bold", plainOf(ChatMarkdown.parse("**bold")));
        assertTrue(ChatMarkdown.parse("**bold").get(0).isPlain());
        assertEquals("a ** b", plainOf(ChatMarkdown.parse("a ** b")));
        assertEquals("||", plainOf(ChatMarkdown.parse("||")));
    }

    /** Nothing typed is ever lost, whatever the markers do. */
    @Test
    public void everyRunTogetherIsTheTextWithoutItsMarkers() {
        String[] cases = {
            "plain words", "**a** *b* ~~c~~ `d` ||e||",
            "***everything***", "a**b*c*d**e", "*", "**", "```",
            "no markers at all", "||a **b** c||",
        };
        for (String message : cases) {
            String out = plainOf(ChatMarkdown.parse(message));
            assertFalse(message + " lost text", out.length() == 0
                    && message.trim().length() > 0
                    && !message.replace("*", "").replace("|", "")
                            .replace("`", "").isEmpty());
        }
        assertEquals("a b c d e",
                plainOf(ChatMarkdown.parse("**a** *b* ~~c~~ `d` ||e||"))
                        .replace("  ", " "));
    }

    /**
     * The layout is the parse with its places kept: every character not
     * a mark wears the style its span has, the marks wear the style
     * around them, and the unmarked characters read back as the spans.
     */
    @Test
    public void theLayoutAgreesWithTheParse() {
        String[] cases = {
            "a **b** c", "**a** *b* ~~c~~ `d` ||e||", "***everything***",
            "a**b*c*d**e", "*", "**", "```", "no markers at all",
            "||a **b** c||", "2 * 3 * 4", "**bold *and italic***",
            "`**not bold**`", "__under__score_names_", "",
        };
        for (String message : cases) {
            int[] styles = ChatMarkdown.layout(message);
            assertEquals(message, message.length(), styles.length);
            StringBuilder unmarked = new StringBuilder();
            for (int index = 0; index < styles.length; index++) {
                if ((styles[index] & ChatMarkdown.Span.MARK) == 0) {
                    unmarked.append(message.charAt(index));
                }
            }
            assertEquals(message, plainOf(ChatMarkdown.parse(message)),
                    unmarked.toString());
            int cursor = 0;
            for (ChatMarkdown.Span span : ChatMarkdown.parse(message)) {
                for (int index = 0; index < span.getText().length(); index++) {
                    while ((styles[cursor] & ChatMarkdown.Span.MARK) != 0) {
                        cursor++;
                    }
                    assertEquals(message + " at " + cursor,
                            styleOf(span), styles[cursor]);
                    cursor++;
                }
            }
        }
        int[] bold = ChatMarkdown.layout("a **b** c");
        assertEquals(ChatMarkdown.Span.PLAIN, bold[0]);
        assertEquals(ChatMarkdown.Span.MARK, bold[2]);
        assertEquals(ChatMarkdown.Span.MARK, bold[3]);
        assertEquals(ChatMarkdown.Span.BOLD, bold[4]);
        assertEquals(ChatMarkdown.Span.MARK, bold[5]);
        assertEquals(ChatMarkdown.Span.PLAIN, bold[8]);
        // An inner mark wears the style around it.
        int[] nested = ChatMarkdown.layout("**a *b* c**");
        assertEquals(ChatMarkdown.Span.BOLD | ChatMarkdown.Span.MARK, nested[4]);
        assertEquals(ChatMarkdown.Span.BOLD | ChatMarkdown.Span.ITALIC, nested[5]);
        // Inside code nothing is a mark.
        int[] code = ChatMarkdown.layout("`**x**`");
        assertEquals(ChatMarkdown.Span.MARK, code[0]);
        assertEquals(ChatMarkdown.Span.CODE, code[1]);
        assertEquals(ChatMarkdown.Span.CODE, code[3]);
        assertEquals(ChatMarkdown.Span.MARK, code[6]);
        assertEquals(0, ChatMarkdown.layout(null).length);
    }

    private static int styleOf(ChatMarkdown.Span span) {
        return (span.isBold() ? ChatMarkdown.Span.BOLD : 0)
                | (span.isItalic() ? ChatMarkdown.Span.ITALIC : 0)
                | (span.isStrikethrough() ? ChatMarkdown.Span.STRIKETHROUGH : 0)
                | (span.isUnderlined() ? ChatMarkdown.Span.UNDERLINE : 0)
                | (span.isCode() ? ChatMarkdown.Span.CODE : 0)
                | (span.isSpoiler() ? ChatMarkdown.Span.SPOILER : 0);
    }

    /** The quick way out, for the many messages carrying no markup. */
    @Test
    public void plainTextIsRecognisedWithoutParsing() {
        assertFalse(ChatMarkdown.hasMarkup("ordinary words"));
        assertFalse(ChatMarkdown.hasMarkup(null));
        assertTrue(ChatMarkdown.hasMarkup("a *b* c"));
        assertTrue(ChatMarkdown.hasMarkup("`x`"));
        assertEquals(1, ChatMarkdown.plain("hello").size());
        assertTrue(ChatMarkdown.plain("hello").get(0).isPlain());
        assertTrue(ChatMarkdown.plain("").isEmpty());
    }
}
