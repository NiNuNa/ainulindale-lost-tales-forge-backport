package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Every row of the stack is drawn at a whole number of display pixels
 * per font pixel and is as tall as the text it is drawn in, and each
 * stands on the line a message's words stand on, so the room a row gains
 * or gives back is above it. Which step each kind of row takes is the
 * config's, asked apart for the open window and the closed feed: the
 * window shows the speaker a step up, and the feed shows the voice plain
 * and takes what it says a step down. A row is laid out against the room
 * its own size leaves it, and a message's stamp is centred in the rows of
 * the words it stamps, whatever heights they have.
 */
public final class ChatRowSizesTest {

    private static final int LINE = ChatStackRows.LINE_HEIGHT;
    private static final int TEXT_OFFSET =
            LostTalesChatOverlayRenderer.TEXT_OFFSET;

    /** Six pixels per character, which is what the fake font measures. */
    private static final ChatLineWrapper.TextMetrics METRICS =
            new ChatLineWrapper.TextMetrics() {
                @Override
                public int width(String text) {
                    return text.length() * 6;
                }
            };

    @After
    public void resetSizes() {
        LostTalesConfig.chatSpeakerSize = LostTalesConfig.CHAT_SIZE_LARGER;
        LostTalesConfig.chatFeedSpeakerSize =
                LostTalesConfig.CHAT_SIZE_LARGER;
        LostTalesConfig.chatFeedMessageSize =
                LostTalesConfig.CHAT_SIZE_SMALLER;
        LostTalesConfig.chatQuoteSize = LostTalesConfig.CHAT_SIZE_SMALLER;
        LostTalesConfig.chatFeedQuoteSize = LostTalesConfig.CHAT_SIZE_SAME;
    }

    /** One ladder: whole display pixels per font pixel, either way. */
    @Test
    public void everySizeIsAWholeDisplayPixelStep() {
        assertEquals(2, LostTalesChatVisualStyle.rowPixels(1.0F, 3, -1));
        assertEquals(3, LostTalesChatVisualStyle.rowPixels(1.0F, 3, 0));
        assertEquals(4, LostTalesChatVisualStyle.rowPixels(1.0F, 3, 1));
        // A step down at GUI scale 1 has nowhere to go, so the words
        // keep their size; a step up is there at every scale.
        assertEquals(1, LostTalesChatVisualStyle.rowPixels(1.0F, 1, -1));
        assertEquals(2, LostTalesChatVisualStyle.rowPixels(1.0F, 1, 1));
        // A chat Scale below full takes both steps down with the words.
        assertEquals(2, LostTalesChatVisualStyle.rowPixels(0.87F, 3, -1));
        assertEquals(3, LostTalesChatVisualStyle.rowPixels(0.87F, 3, 1));
        // The named steps are the same ladder.
        for (int factor = 1; factor <= 4; factor++) {
            assertEquals("factor " + factor,
                    LostTalesChatVisualStyle.rowPixels(1.0F, factor, -1),
                    LostTalesChatVisualStyle.smallPixels(1.0F, factor));
            assertEquals("factor " + factor,
                    LostTalesChatVisualStyle.rowPixels(1.0F, factor, 1),
                    LostTalesChatVisualStyle.largePixels(1.0F, factor));
        }
    }

    /** The words the options are written in, read where they are read. */
    @Test
    public void aSizeOptionNamesItsStep() {
        assertEquals(-1, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.CHAT_SIZE_SMALLER));
        assertEquals(0, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.CHAT_SIZE_SAME));
        assertEquals(1, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.CHAT_SIZE_LARGER));
        // A file naming nothing the chat knows keeps the words' size.
        assertEquals(0, LostTalesChatVisualStyle.sizeStep(null));
        assertEquals(0, LostTalesChatVisualStyle.sizeStep("HUGE"));
        assertEquals(LostTalesConfig.CHAT_SIZE_LARGER,
                LostTalesConfig.normalizeSize("larger",
                        LostTalesConfig.CHAT_SIZE_SAME));
        assertEquals(LostTalesConfig.CHAT_SIZE_SAME,
                LostTalesConfig.normalizeSize("HUGE",
                        LostTalesConfig.CHAT_SIZE_SAME));
    }

    /**
     * Shipped: the window shows the speaker a step above their words and
     * a reply's quote a step under them, and the feed the other way
     * about — the voice plain and what it says a step down, a quote as
     * large as the words it stands over — so a glance reads the voices
     * first.
     */
    @Test
    public void theFeedShowsTheVoicePlainAndShrinksWhatItSays() {
        assertEquals(1, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.chatSpeakerSize));
        assertEquals(1, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.chatFeedSpeakerSize));
        assertEquals(-1, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.chatFeedMessageSize));
        assertEquals(-1, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.chatQuoteSize));
        assertEquals(0, LostTalesChatVisualStyle.sizeStep(
                LostTalesConfig.chatFeedQuoteSize));
        // The feed moves the words down and the speaker and the quote go
        // with them, so the feed's voice lands where the window's words
        // are and its quote where the feed's words are.
        assertEquals(0, LostTalesChatVisualStyle.messageStep(true));
        assertEquals(-1, LostTalesChatVisualStyle.messageStep(false));
        assertEquals(1, LostTalesChatVisualStyle.messageStep(true)
                + LostTalesChatVisualStyle.sizeStep(
                        LostTalesConfig.chatSpeakerSize));
        assertEquals(0, LostTalesChatVisualStyle.messageStep(false)
                + LostTalesChatVisualStyle.sizeStep(
                        LostTalesConfig.chatFeedSpeakerSize));
        assertEquals(-1, LostTalesChatVisualStyle.messageStep(true)
                + LostTalesChatVisualStyle.sizeStep(
                        LostTalesConfig.chatQuoteSize));
        assertEquals(-1, LostTalesChatVisualStyle.messageStep(false)
                + LostTalesChatVisualStyle.sizeStep(
                        LostTalesConfig.chatFeedQuoteSize));
        // And two steps down is a real size at every scale that has one.
        assertEquals(1, LostTalesChatVisualStyle.rowPixels(1.0F, 3, -2));
        assertEquals(2, LostTalesChatVisualStyle.rowPixels(1.0F, 4, -2));
        assertEquals(1, LostTalesChatVisualStyle.rowPixels(1.0F, 2, -2));
        // The open window's words are what every other size is measured
        // against, so they are never stepped.
        assertEquals(1.0F, LostTalesChatVisualStyle.messageRowScale(true),
                0.0F);
    }

    /** An option chosen is the size drawn. */
    @Test
    public void theOptionsChangeTheSizes() {
        LostTalesConfig.chatSpeakerSize = LostTalesConfig.CHAT_SIZE_SAME;
        assertEquals(1.0F, LostTalesChatVisualStyle.speakerRowScale(true),
                0.0F);
        assertEquals(LINE, ChatStackRows.speakerRowHeight(true));
        LostTalesConfig.chatSpeakerSize = LostTalesConfig.CHAT_SIZE_LARGER;
        assertTrue(LostTalesChatVisualStyle.speakerRowScale(true) > 1.0F);
        assertTrue(ChatStackRows.speakerRowHeight(true) > LINE);
    }

    /**
     * A row grown or shrunk keeps the words' own line and moves its top
     * edge by exactly what the size change costs it, so the rows below
     * it never shift.
     */
    @Test
    public void everyRowStandsOnTheLineTheWordsDo() {
        for (int factor = 1; factor <= 4; factor++) {
            int large = LostTalesChatVisualStyle.rowPixels(1.0F, factor, 1);
            int small = LostTalesChatVisualStyle.rowPixels(1.0F, factor, -1);
            // The speaker's row rises by the whole pixel it gained, in
            // display pixels; a quote drops by the one it gave up.
            assertEquals("factor " + factor, TEXT_OFFSET,
                    LostTalesChatVisualStyle.rowRise(1.0F, factor, large));
            assertEquals("factor " + factor,
                    factor == 1 ? 0 : -TEXT_OFFSET,
                    LostTalesChatVisualStyle.rowRise(1.0F, factor, small));
            // Which is the row's own change of height, all of it above
            // the line it stands on.
            float largeScale = LostTalesChatVisualStyle.largeTextScale(
                    factor);
            assertEquals("factor " + factor,
                    TEXT_OFFSET * (largeScale - 1.0F),
                    LostTalesChatVisualStyle.rowRise(1.0F, factor, large)
                            / (float)factor, 0.0001F);
        }
    }

    /** A row is the words' row drawn at its own size, and no taller. */
    @Test
    public void aRowIsAsTallAsTheTextItIsDrawnIn() {
        assertEquals(24, ChatStackRows.rowHeight(2.0F));
        assertEquals(18, ChatStackRows.rowHeight(1.5F));
        assertEquals(16, ChatStackRows.rowHeight(4.0F / 3.0F));
        assertEquals(15, ChatStackRows.rowHeight(1.25F));
        assertEquals(LINE, ChatStackRows.rowHeight(1.0F));
        assertEquals(9, ChatStackRows.rowHeight(0.75F));
        assertEquals(8, ChatStackRows.rowHeight(2.0F / 3.0F));
        assertEquals(6, ChatStackRows.rowHeight(0.5F));
        // Whole pixels of the stack at every GUI scale, so nothing below
        // a row lands between them.
        for (int factor = 1; factor <= 4; factor++) {
            float large = LostTalesChatVisualStyle.largeTextScale(factor);
            float small = LostTalesChatVisualStyle.smallTextScale(factor);
            assertEquals("factor " + factor, LINE * large,
                    ChatStackRows.rowHeight(large), 0.0001F);
            assertEquals("factor " + factor, LINE * small,
                    ChatStackRows.rowHeight(small), 0.0001F);
        }
    }

    /**
     * The wrapper says which rows name the speaker and which carry the
     * words, so each can be drawn and measured at its own size.
     */
    @Test
    public void theWrapperMarksTheSpeakersRowsAndTheWords() {
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS,
                message("<Aldric> ", "aaa bbb ccc ddd eee fff ggg hhh"),
                100, true);
        assertNotNull(lines);
        assertTrue(lines.size() > 2);
        assertTrue(ChatLayoutMarker.isHeaderRow(lines.get(0)));
        assertFalse(ChatLayoutMarker.isBodyRow(lines.get(0)));
        for (int index = 1; index < lines.size(); index++) {
            assertFalse("row " + index,
                    ChatLayoutMarker.isHeaderRow(lines.get(index)));
            assertTrue("row " + index,
                    ChatLayoutMarker.isBodyRow(lines.get(index)));
        }
    }

    /**
     * A reply stands on three rows of three heights: the quote it opens
     * with, the name, and the words. The quote is neither the speaker's
     * row nor the words, so neither mark reaches it.
     */
    @Test
    public void aReplyKeepsItsQuoteItsNameAndItsWordsApart() {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatReplyMarker.applyIcon(
                new ChatComponentText(""), 0x9C807E, 42L));
        root.appendSibling(ChatReplyMarker.apply(
                new ChatComponentText("<Beren> the road"), 0xFCECD1, 42L));
        root.appendSibling(ChatLayoutMarker.lineBreak());
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text("<Aldric> "));
        root.appendSibling(ChatLayoutMarker.bodyBreak(0xFFFFFF));
        root.appendSibling(text("hello"));
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS, root, 200,
                true);
        assertEquals(3, lines.size());
        assertTrue(ChatReplyMarker.isQuoteRow(lines.get(0)));
        assertFalse(ChatLayoutMarker.isHeaderRow(lines.get(0)));
        assertFalse(ChatLayoutMarker.isBodyRow(lines.get(0)));
        assertTrue(ChatLayoutMarker.isHeaderRow(lines.get(1)));
        assertTrue(ChatLayoutMarker.isBodyRow(lines.get(2)));
        assertEquals(ChatStackRows.quoteRowHeight(true),
                ChatStackRows.heightOf(new ChatLine(0, lines.get(0), 1)));
        assertEquals(ChatStackRows.speakerRowHeight(true),
                ChatStackRows.heightOf(new ChatLine(0, lines.get(1), 1)));
        assertEquals(ChatStackRows.messageRowHeight(true),
                ChatStackRows.heightOf(new ChatLine(0, lines.get(2), 1)));
    }

    /**
     * A grouped continuation names nobody, so it has no speaker's row:
     * it is words from its first row down.
     */
    @Test
    public void aGroupedContinuationIsAllWords() {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(ChatLayoutMarker.bodyBreak(0xFFFFFF));
        root.appendSibling(text("and on, down from the door"));
        List<IChatComponent> lines = ChatLineWrapper.wrap(METRICS, root,
                100, true);
        assertNotNull(lines);
        for (int index = 0; index < lines.size(); index++) {
            assertFalse("row " + index,
                    ChatLayoutMarker.isHeaderRow(lines.get(index)));
            assertTrue("row " + index,
                    ChatLayoutMarker.isBodyRow(lines.get(index)));
        }
    }

    /**
     * A row is laid out against the room its own size leaves it: less
     * for the speaker's row in the window, more for the words in the
     * feed, so what is laid out is what is drawn.
     */
    @Test
    public void aRowIsLaidOutAgainstTheRoomItsSizeLeavesIt() {
        assertEquals(100, ChatLineWrapper.roomFor(100, 1.0F));
        assertEquals(66, ChatLineWrapper.roomFor(100, 1.5F));
        assertEquals(75, ChatLineWrapper.roomFor(100, 4.0F / 3.0F));
        assertEquals(150, ChatLineWrapper.roomFor(100, 2.0F / 3.0F));
        // A width of one is the floor, so a layout always makes progress.
        assertEquals(1, ChatLineWrapper.roomFor(1, 2.0F));

        // "<Aldric Grey> " is 84 pixels: it fits 100 whole, but not the
        // 66 the row has when it is drawn half again as big.
        ChatComponentText root = message("<Aldric Grey> ", "hello");
        List<IChatComponent> whole = ChatLineWrapper.wrap(METRICS, root,
                100, true, 1.0F, 1.0F);
        List<IChatComponent> large = ChatLineWrapper.wrap(METRICS, root,
                100, true, 1.5F, 1.0F);
        assertEquals(2, whole.size());
        assertEquals(3, large.size());
        assertTrue(ChatLayoutMarker.isHeaderRow(large.get(0)));
        assertTrue(ChatLayoutMarker.isHeaderRow(large.get(1)));
        assertTrue(ChatLayoutMarker.isBodyRow(large.get(2)));

        // Words drawn smaller get more of their own text on a row: a
        // body that breaks three times at the words' size is one row in
        // the feed, and the name's row stands over it either way.
        ChatComponentText long_ = message("<A> ", "aaaaaa bbbbbb cccccc");
        assertEquals(4, ChatLineWrapper.wrap(METRICS, long_, 78, true,
                1.0F, 1.0F).size());
        assertEquals(2, ChatLineWrapper.wrap(METRICS, long_, 78, true,
                1.0F, 0.5F).size());
    }

    /**
     * The stamp's capitals stand on the capitals of the words it stamps,
     * not on the row that happens to carry it: a name of one size over
     * words of another leaves it beside the words.
     */
    @Test
    public void aStampIsCentredInTheRowsOfTheWords() {
        // A message on one row: capitals of one size on capitals of the
        // same size stand exactly where the words do.
        assertEquals(-TEXT_OFFSET,
                LostTalesChatOverlayRenderer.stampTextTop(0, LINE, 0, 1.0F),
                0.0001F);
        // A speaker's row of sixteen over words of twelve: the stamp is
        // written on the name's row and moves down beside the words.
        assertEquals(-TEXT_OFFSET,
                LostTalesChatOverlayRenderer.stampTextTop(-12, 16, 14, 1.0F),
                0.0001F);
        // Smaller capitals cannot share the words' middle row exactly:
        // they stand within a display pixel of it, and never below it.
        float small = 2.0F / 3.0F;
        float top = LostTalesChatOverlayRenderer.stampTextTop(0, LINE, 0,
                small);
        float wordsMiddle = -TEXT_OFFSET
                + LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT / 2.0F;
        float stampMiddle = top
                + LostTalesChatOverlayRenderer.GLYPH_CAP_HEIGHT * small / 2.0F;
        assertTrue(stampMiddle <= wordsMiddle + 0.0001F);
        assertTrue(stampMiddle > wordsMiddle - 1.0F);
    }

    /** A message: the anchor, the sender, the body break, then the words. */
    private static ChatComponentText message(String sender, String body) {
        ChatComponentText root = new ChatComponentText("");
        root.appendSibling(ChatLayoutMarker.anchor());
        root.appendSibling(text(sender));
        root.appendSibling(ChatLayoutMarker.bodyBreak(0xFFFFFF));
        root.appendSibling(text(body));
        return root;
    }

    private static ChatComponentText text(String value) {
        ChatComponentText component = new ChatComponentText(value);
        component.setChatStyle(new ChatStyle().setColor(
                EnumChatFormatting.WHITE));
        return component;
    }
}
