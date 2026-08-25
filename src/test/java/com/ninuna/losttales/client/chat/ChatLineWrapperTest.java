package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatIdentityType;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.network.packet.LostTalesChatMessagePacket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class ChatLineWrapperTest {
    private static final char SECTION = 167;

    /**
     * Six pixels per visible character, one extra while bold, formatting
     * pairs free — the same rules FontRenderer.getStringWidth applies.
     */
    private static final ChatLineWrapper.TextMetrics METRICS =
            new ChatLineWrapper.TextMetrics() {
                @Override
                public int width(String text) {
                    int width = 0;
                    boolean bold = false;
                    for (int index = 0; index < text.length(); index++) {
                        char character = text.charAt(index);
                        if (character == SECTION
                                && index + 1 < text.length()) {
                            char code = Character.toLowerCase(
                                    text.charAt(index + 1));
                            index++;
                            if (code == 'l') {
                                bold = true;
                            } else if (code == 'r'
                                    || (code >= '0' && code <= '9')
                                    || (code >= 'a' && code <= 'f')) {
                                bold = false;
                            }
                            continue;
                        }
                        width += bold ? 7 : 6;
                    }
                    return width;
                }
            };

    private static ChatComponentText text(String value) {
        ChatComponentText component = new ChatComponentText(value);
        component.setChatStyle(new ChatStyle().setColor(
                EnumChatFormatting.WHITE));
        return component;
    }

    /** {@code [p]} prefix of one marked channel part plus a name. */
    private static ChatComponentText line(String prefix, String body) {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatPrefixMarker.channel(
                text("Global: "), 0x00FF00));
        root.appendSibling(text(prefix));
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text(body));
        return root;
    }

    /** As above with a timestamp between the channel and the name. */
    private static ChatComponentText timedLine(String time, String prefix,
                                               String body) {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatPrefixMarker.channel(
                text("Global: "), 0x00FF00));
        root.appendSibling(ChatPrefixMarker.timestamp(text(time), 0xDBC9B4));
        root.appendSibling(text(prefix));
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text(body));
        return root;
    }

    /**
     * Each state pays only for the header runs it draws inline: the
     * closed feed reserves the channel prefix and never the timestamp,
     * and the open screen reserves neither — its timestamp lives in the
     * column at the window's edge, so the whole width belongs to the
     * name and body. "Global: " and "[12:34] " are 48px each,
     * "&lt;N&gt; " is 24.
     */
    @Test
    public void eachStateReservesOnlyTheHeaderRunsItDraws() {
        List<IChatComponent> closed = ChatLineWrapper.wrap(METRICS,
                timedLine("[12:34] ", "<N> ", "aaa bbb ccc ddd"),
                150, false);
        List<IChatComponent> open = ChatLineWrapper.wrap(METRICS,
                timedLine("[12:34] ", "<N> ", "aaa bbb ccc ddd"),
                150, true);
        // Closed: 48 + 24 = 72 taken, 78 left — two words of 18 and a
        // space. Open: only the name's 24 taken, so the 90px body fits
        // on the first line whole.
        assertEquals(2, closed.size());
        assertEquals(1, open.size());
        // The continuation indent is the header each state draws.
        assertEquals(72, indentOf(closed.get(1), false));
    }

    private static String plain(IChatComponent line) {
        StringBuilder plain = new StringBuilder();
        for (Object value : line) {
            plain.append(((IChatComponent)value).getUnformattedTextForChat());
        }
        return plain.toString();
    }

    private static int indentOf(IChatComponent line, boolean chatOpen) {
        for (Object value : line) {
            ChatLayoutMarker.Data data =
                    ChatLayoutMarker.decode((IChatComponent)value);
            if (data != null && !data.anchor) {
                return data.indent(chatOpen);
            }
        }
        return -1;
    }

    @Test
    public void linesWithoutAnAnchorAreLeftToVanilla() {
        ChatComponentText vanilla = new ChatComponentText("hello world");
        assertNull(ChatLineWrapper.wrap(METRICS, vanilla, 100));
    }

    @Test
    public void continuationLinesIndentUnderTheBodyInBothChatStates() {
        // "Global: " is 48px, "<Name> " is 42px: 90 closed, 42 open.
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line("<Name> ", "one two three four five six"), 150);
        assertNotNull(lines);
        assertEquals(3, lines.size());
        assertEquals("Global: <Name> one two", plain(lines.get(0)));
        assertEquals("three four", plain(lines.get(1)));
        assertEquals("five six", plain(lines.get(2)));
        assertEquals(-1, indentOf(lines.get(0), false));
        assertEquals(75, indentOf(lines.get(1), false));
        assertEquals(42, indentOf(lines.get(1), true));
        assertEquals(75, indentOf(lines.get(2), false));
        // Nothing exceeds the width in the closed state, the wider one,
        // and the next word would not have fit on either line.
        assertTrue(90 + METRICS.width("one two") <= 150);
        assertTrue(90 + METRICS.width("one two three") > 150);
        assertTrue(75 + METRICS.width("three four") <= 150);
        assertTrue(75 + METRICS.width("three four five") > 150);
    }

    /**
     * The open screen draws no channel prefix, so a line laid out for it
     * gives that width back to the message body instead of leaving it
     * blank at the end of every line. "Global: " is 48px of a 150px
     * line: closed, the body starts at 90 and gets two words on the
     * first line; open it starts at 42 and gets four.
     */
    @Test
    public void theOpenLayoutGivesTheChannelPrefixWidthToTheBody() {
        List<IChatComponent> closed = ChatLineWrapper.wrap(METRICS,
                line("<Name> ", "one two three four five six"), 150, false);
        List<IChatComponent> open = ChatLineWrapper.wrap(METRICS,
                line("<Name> ", "one two three four five six"), 150, true);
        assertNotNull(open);
        assertEquals("Global: <Name> one two", plain(closed.get(0)));
        assertEquals("Global: <Name> one two three four",
                plain(open.get(0)));
        assertEquals(2, open.size());
        assertEquals("five six", plain(open.get(1)));
        // The marker still carries both offsets: the renderer picks the
        // one the state it draws in needs.
        assertEquals(42, indentOf(open.get(1), true));
        assertEquals(75, indentOf(open.get(1), false));
        // Nothing exceeds the width the open screen actually draws in.
        assertTrue(42 + METRICS.width("one two three four") <= 150);
        assertTrue(42 + METRICS.width("one two three four five") > 150);
    }

    @Test
    public void breakSpacesAreDroppedAndNeverInsertedAsText() {
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line("<N> ", "aaaa bbbb cccc dddd eeee"), 140);
        // Prefix 48 + 24 = 72 leaves 68: "aaaa bbbb" (54) fits, a third
        // word would not; continuation lines have 70.
        assertEquals(3, lines.size());
        assertEquals("Global: <N> aaaa bbbb", plain(lines.get(0)));
        assertFalse(plain(lines.get(1)).startsWith(" "));
        assertEquals("cccc dddd", plain(lines.get(1)));
        assertEquals("eeee", plain(lines.get(2)));
    }

    @Test
    public void unbrokenTextIsCutSoLayoutAlwaysAdvances() {
        StringBuilder word = new StringBuilder();
        for (int index = 0; index < 60; index++) {
            word.append('x');
        }
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line("<N> ", word.toString()), 120);
        assertTrue(lines.size() > 1);
        StringBuilder joined = new StringBuilder();
        for (IChatComponent line : lines) {
            joined.append(plain(line));
        }
        assertEquals("Global: <N> " + word, joined.toString());
        // A cut piece fills the line rather than starting a new one empty.
        assertTrue(plain(lines.get(1)).length() > 0);
    }

    @Test
    public void activeFormattingSurvivesASplit() {
        String body = SECTION + "6gold " + SECTION + "lbold words that wrap";
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line("<N> ", body), 130);
        assertTrue(lines.size() > 1);
        // The line is filled up to the last word that fits (58 of 58).
        assertEquals("Global: <N> " + SECTION + "6gold " + SECTION + "lbold",
                plain(lines.get(0)));
        String second = plain(lines.get(1));
        // Colour and bold were both active at the break.
        assertTrue(second.startsWith(SECTION + "6" + SECTION + "lwords"));
        assertEquals(SECTION + "6" + SECTION + "l",
                ChatLineWrapper.activeFormatting(body));
        assertEquals("", ChatLineWrapper.activeFormatting(
                SECTION + "6x" + SECTION + "r"));
        assertEquals(SECTION + "a", ChatLineWrapper.activeFormatting(
                SECTION + "lx" + SECTION + "a"));
    }

    @Test
    public void formattingCodesCostNoWidth() {
        String plainBody = "abcdef abcdef";
        String codedBody = SECTION + "6abcdef " + SECTION + "cabcdef";
        List<IChatComponent> plainLines = ChatLineWrapper.wrap(METRICS,
                line("<N> ", plainBody), 130);
        List<IChatComponent> codedLines = ChatLineWrapper.wrap(METRICS,
                line("<N> ", codedBody), 130);
        assertEquals(plainLines.size(), codedLines.size());
    }

    @Test
    public void inlineGlyphsAreAtomicWords() {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(text("<N> "));
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text("hello!!"));
        root.appendSibling(ChatEmojiMarker.create(ChatEmoji.SMILE));
        root.appendSibling(text(" there"));
        // 24 + 42 = 66; the 14px bold-space slot does not fit in 76 and
        // moves down whole, never split between its two spaces; the text
        // after it, space included, still fits beside it (24+14+36).
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS, root, 76);
        assertEquals(2, lines.size());
        assertEquals("<N> hello!!", plain(lines.get(0)));
        boolean emojiOnSecond = false;
        for (Object value : lines.get(1)) {
            emojiOnSecond |= ChatEmojiMarker.isMarker((IChatComponent)value);
        }
        assertTrue(emojiOnSecond);
        assertEquals("   there", plain(lines.get(1)));
        assertTrue(ChatEmojiMarker.reservesFullSlot(
                plain(lines.get(1)).replace(" there", "")));
    }

    @Test
    public void aShortLastWordStaysOnAFilledFirstLine() {
        // Prefix 72 of 100 leaves 28 (< MIN_BODY_WIDTH) on the first line,
        // so the body starts below it; once the body has begun, short
        // pieces keep filling whatever line they are on.
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line("<N> ", "abcdef h"), 100);
        assertEquals(2, lines.size());
        assertEquals("Global: <N> ", plain(lines.get(0)));
        // 48 of the 50 available after the capped 50px indent.
        assertEquals("abcdef h", plain(lines.get(1)));
    }

    @Test
    public void aVeryLongPrefixCapsTheIndentAndStartsTheBodyBelow() {
        StringBuilder name = new StringBuilder("<");
        for (int index = 0; index < 14; index++) {
            name.append('n');
        }
        name.append("> ");
        // Prefix 48 + 102 = 150 of 160: no room, body starts a line down
        // with the indent capped at half the width.
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line(name.toString(), "short"), 160);
        assertEquals(2, lines.size());
        assertEquals("Global: " + name, plain(lines.get(0)));
        assertEquals("short", plain(lines.get(1)));
        assertEquals(80, indentOf(lines.get(1), false));
        // And one that does not even fit the width goes back to vanilla.
        assertNull(ChatLineWrapper.wrap(METRICS,
                line(name.toString(), "short"), 100));
    }

    @Test
    public void realMessagesAnchorBeforeTheSenderAndRoundTripTheirText() {
        boolean originalTimestamps = LostTalesConfig.showChatTimestamps;
        LostTalesConfig.showChatTimestamps = false;
        try {
            IChatComponent message = LostTalesChatPresentation.build(
                    new LostTalesChatMessagePacket(
                            ChatChannel.ALL, UUID.randomUUID(), "Arathorn",
                            "Ranger", "", 0x55AA55, 0x336633,
                            "The road goes ever on and on, down from the "
                                    + "door where it began.", 1L,
                            "losttales:human_ranger_male_2"));
            List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                    message, 200);
            assertNotNull(lines);
            assertTrue(lines.size() > 1);
            StringBuilder joined = new StringBuilder();
            List<String> texts = new ArrayList<String>();
            for (IChatComponent line : lines) {
                texts.add(plain(line));
                joined.append(plain(line)).append(' ');
            }
            assertEquals("Global: <  Arathorn> The road goes ever on and on, "
                    + "down from the door where it began.",
                    joined.toString().trim());
            // With timestamps off the anchor sits right after the channel
            // prefix: 48px closed, nothing when the prefix is hidden.
            assertEquals(48, indentOf(lines.get(1), false));
            assertEquals(0, indentOf(lines.get(1), true));
        } finally {
            LostTalesConfig.showChatTimestamps = originalTimestamps;
        }
    }

    /**
     * A wrapped line keeps the sender's colours: the head marker stays
     * on the first line, so every continuation line carries them itself
     * and a name that wrapped is drawn in the colour one that did not is.
     */
    @Test
    public void continuationLinesCarryTheSendersColours() {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatPrefixMarker.channel(
                text("Global: "), 0x00FF00));
        ChatComponentText head = text("  ");
        head.setChatStyle(head.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ChatHeadMarker.encode(UUID.randomUUID(),
                                ChatIdentityType.ACCOUNT, "", "hello",
                                0x123456, 0xA94B54))));
        root.appendSibling(head);
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text("one two three four five six"));
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS, root, 150);
        assertNotNull(lines);
        assertTrue(lines.size() > 1);
        for (int index = 1; index < lines.size(); index++) {
            ChatLayoutMarker.Data indent = indentMarker(lines.get(index));
            assertNotNull(indent);
            assertTrue("line " + index + " lost the sender's colours",
                    indent.hasColors());
            assertEquals(0xA94B54, indent.nameColor);
            assertEquals(0x123456, indent.titleColor);
        }
        // The first line has the head itself, so it needs no copy.
        assertNull(indentMarker(lines.get(0)));
    }

    /** A line with no head of its own carries no colours either. */
    @Test
    public void aHeadlessLineCarriesNoColours() {
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                line("<Name> ", "one two three four five six"), 150);
        assertNotNull(lines);
        ChatLayoutMarker.Data indent = indentMarker(lines.get(1));
        assertNotNull(indent);
        assertFalse(indent.hasColors());
    }

    private static ChatLayoutMarker.Data indentMarker(IChatComponent line) {
        for (Object value : line) {
            ChatLayoutMarker.Data data =
                    ChatLayoutMarker.decode((IChatComponent)value);
            if (data != null && !data.anchor) {
                return data;
            }
        }
        return null;
    }
}
