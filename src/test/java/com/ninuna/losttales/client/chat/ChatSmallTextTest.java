package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.util.ChatComponentText;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The chat's small text and what it is drawn for: one whole display pixel
 * less per font pixel than the words, centred on their capitals; a reply's
 * quote known by its first run and shrunk from where that run starts; and
 * a highlighted line under the pointer a shade lighter.
 */
public final class ChatSmallTextTest {

    @After
    public void resetColours() {
        LostTalesConfig.chatSelectedLineColor = "MAUVE";
        LostTalesConfig.chatMentionLineColor = "MULBERRY";
        LostTalesConfig.chatSelectedMentionColor =
                LostTalesConfig.CHAT_COLOR_AUTOMATIC;
        LostTalesConfig.chatReplyHighlightColor = "APRICOT";
    }

    @Test
    public void smallTextIsOneDisplayPixelLessPerFontPixel() {
        assertEquals(1, LostTalesChatVisualStyle.smallPixels(1.0F, 1));
        assertEquals(1, LostTalesChatVisualStyle.smallPixels(1.0F, 2));
        assertEquals(2, LostTalesChatVisualStyle.smallPixels(1.0F, 3));
        assertEquals(3, LostTalesChatVisualStyle.smallPixels(1.0F, 4));
    }

    /** Words at a pixel and a half per font pixel have small text at one. */
    @Test
    public void aSmallerChatScaleTakesSmallTextDownWithIt() {
        assertEquals(1, LostTalesChatVisualStyle.smallPixels(0.5F, 3));
        assertEquals(2, LostTalesChatVisualStyle.smallPixels(0.87F, 3));
    }

    @Test
    public void smallCapitalsAreCentredOnTheWordsOddPixelBelow() {
        assertEquals(0, LostTalesChatVisualStyle.smallDrop(1.0F, 1));
        assertEquals(3, LostTalesChatVisualStyle.smallDrop(1.0F, 2));
        assertEquals(3, LostTalesChatVisualStyle.smallDrop(1.0F, 3));
        assertEquals(3, LostTalesChatVisualStyle.smallDrop(1.0F, 4));
        assertEquals(1, LostTalesChatVisualStyle.smallDrop(0.5F, 3));
    }

    @Test
    public void aQuoteRowIsKnownByItsFirstRunAndShrinksFromIt() {
        ChatComponentText row = new ChatComponentText("");
        row.appendSibling(ChatLayoutMarker.indent(10, 12));
        row.appendSibling(ChatReplyMarker.applyIcon(
                new ChatComponentText(""), 0x9C807E, 42L));
        row.appendSibling(ChatReplyMarker.apply(
                new ChatComponentText("<Aldric> "), 0xFCECD1, 42L));
        assertTrue(ChatReplyMarker.isQuoteRow(row));
        assertEquals(12, LostTalesChatVisualStyle.contentStart(row, true));
        assertEquals(10, LostTalesChatVisualStyle.contentStart(row, false));
        assertFalse(ChatReplyMarker.isQuoteRow(
                new ChatComponentText("Just words")));
    }

    @Test
    public void aMentionUnderThePointerIsItsColourAShadeLighter() {
        LostTalesConfig.chatMentionLineColor = "ORCHID";
        assertEquals(LostTalesColors.rgb(LostTalesColors.SALMON),
                LostTalesChatVisualStyle.selectedMentionLineRgb());
    }

    @Test
    public void aRampsLightestMentionTakesTheSelectedLineColour() {
        LostTalesConfig.chatMentionLineColor = "SALMON";
        assertEquals(LostTalesColors.rgb(LostTalesColors.MAUVE),
                LostTalesChatVisualStyle.selectedMentionLineRgb());
    }

    @Test
    public void aChosenSelectedMentionStandsAsChosen() {
        LostTalesConfig.chatSelectedMentionColor = "TEAL";
        assertEquals(LostTalesColors.rgb(LostTalesColors.TEAL),
                LostTalesChatVisualStyle.selectedMentionLineRgb());
    }

    @Test
    public void aReplysLightUnderThePointerIsAShadeLighter() {
        assertEquals(LostTalesColors.rgb(LostTalesColors.HONEY),
                LostTalesChatVisualStyle.selectedReplyHighlightRgb());
        LostTalesConfig.chatReplyHighlightColor = "HONEY";
        assertEquals(LostTalesColors.rgb(LostTalesColors.MAUVE),
                LostTalesChatVisualStyle.selectedReplyHighlightRgb());
    }
}
