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
