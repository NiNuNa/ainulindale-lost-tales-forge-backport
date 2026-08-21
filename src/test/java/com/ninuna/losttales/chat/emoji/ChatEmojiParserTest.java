package com.ninuna.losttales.chat.emoji;

import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class ChatEmojiParserTest {

    @Test
    public void plainTextStaysOneLiteralSegment() {
        List<ChatEmojiParser.Segment> segments =
                ChatEmojiParser.split("Hello everyone!");
        assertEquals(1, segments.size());
        assertFalse(segments.get(0).isEmoji());
        assertEquals("Hello everyone!", segments.get(0).getText());
    }

    @Test
    public void nullAndEmptyMessagesYieldOneEmptySegment() {
        assertEquals("", ChatEmojiParser.split(null).get(0).getText());
        assertEquals("", ChatEmojiParser.split("").get(0).getText());
        assertEquals(1, ChatEmojiParser.split(null).size());
    }

    @Test
    public void singleShortcodeInTheMiddleSplitsInThree() {
        List<ChatEmojiParser.Segment> segments =
                ChatEmojiParser.split("Hello :smile: everyone!");
        assertEquals(3, segments.size());
        assertEquals("Hello ", segments.get(0).getText());
        assertSame(ChatEmoji.SMILE, segments.get(1).getEmoji());
        assertEquals(" everyone!", segments.get(2).getText());
    }

    @Test
    public void shortcodeAtStartAndEnd() {
        List<ChatEmojiParser.Segment> start =
                ChatEmojiParser.split(":smile: hello");
        assertSame(ChatEmoji.SMILE, start.get(0).getEmoji());
        assertEquals(" hello", start.get(1).getText());

        List<ChatEmojiParser.Segment> end =
                ChatEmojiParser.split("goodbye :sad:");
        assertEquals("goodbye ", end.get(0).getText());
        assertSame(ChatEmoji.SAD, end.get(1).getEmoji());
    }

    @Test
    public void adjacentAndRepeatedShortcodes() {
        List<ChatEmojiParser.Segment> adjacent =
                ChatEmojiParser.split(":smile::joy:");
        assertEquals(2, adjacent.size());
        assertSame(ChatEmoji.SMILE, adjacent.get(0).getEmoji());
        assertSame(ChatEmoji.JOY, adjacent.get(1).getEmoji());

        List<ChatEmojiParser.Segment> repeated =
                ChatEmojiParser.split(":smile: :smile:");
        assertEquals(3, repeated.size());
        assertSame(ChatEmoji.SMILE, repeated.get(0).getEmoji());
        assertEquals(" ", repeated.get(1).getText());
        assertSame(ChatEmoji.SMILE, repeated.get(2).getEmoji());
    }

    @Test
    public void unknownAndIncompleteShortcodesStayLiteral() {
        assertLiteral(":does_not_exist:");
        assertLiteral(":sm");
        assertLiteral(":");
        assertLiteral("::");
        assertLiteral("10:30 o'clock");
        assertLiteral(":SMILE:");
        assertLiteral(": smile :");
    }

    @Test
    public void extraLeadingColonStillMatchesTheInnerShortcode() {
        List<ChatEmojiParser.Segment> segments =
                ChatEmojiParser.split("::smile:");
        assertEquals(2, segments.size());
        assertEquals(":", segments.get(0).getText());
        assertSame(ChatEmoji.SMILE, segments.get(1).getEmoji());
    }

    @Test
    public void unicodeTextAroundShortcodesIsPreserved() {
        List<ChatEmojiParser.Segment> segments = ChatEmojiParser.split(
                "H\u00e4rte :smile: \u00c6\u00d8\u2026");
        assertEquals("H\u00e4rte ", segments.get(0).getText());
        assertSame(ChatEmoji.SMILE, segments.get(1).getEmoji());
        assertEquals(" \u00c6\u00d8\u2026", segments.get(2).getText());
    }

    @Test
    public void segmentsAlwaysReconstructTheOriginalMessage() {
        String[] messages = new String[] {
                "Hello :smile: everyone!", ":smile: hello",
                "goodbye :sad:", ":smile::joy:", ":smile: :smile:",
                ":does_not_exist: and :sm and :", "::smile::",
                "just text", ":frown::unknown::frown:"
        };
        for (String message : messages) {
            StringBuilder rebuilt = new StringBuilder();
            for (ChatEmojiParser.Segment segment
                    : ChatEmojiParser.split(message)) {
                rebuilt.append(segment.isEmoji()
                        ? segment.getEmoji().getShortcode()
                        : segment.getText());
            }
            assertEquals(message, rebuilt.toString());
        }
    }

    @Test
    public void everyRegisteredShortcodeRoundTrips() {
        for (ChatEmoji emoji : ChatEmoji.values()) {
            List<ChatEmojiParser.Segment> segments =
                    ChatEmojiParser.split(emoji.getShortcode());
            assertEquals(1, segments.size());
            assertSame(emoji, segments.get(0).getEmoji());
            assertSame(emoji, ChatEmoji.fromName(emoji.getName()));
        }
        assertNull(ChatEmoji.fromName("does_not_exist"));
        assertNull(ChatEmoji.fromName(null));
    }

    private static void assertLiteral(String message) {
        List<ChatEmojiParser.Segment> segments =
                ChatEmojiParser.split(message);
        assertEquals(message, 1, segments.size());
        assertFalse(message, segments.get(0).isEmoji());
        assertEquals(message, segments.get(0).getText());
    }
}
