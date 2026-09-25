package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.util.ChatComponentText;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * A mention's bar fades over its whole message: strongest at the
 * message's middle, nothing at its ends, however many rows it wraps to.
 */
public final class ChatMentionBarTest {

    @Test
    public void theSpanCoversEveryRowOfTheMessage() {
        // Newest first: a one-row message, then a message of three rows,
        // then another one-row message; every row twelve pixels tall.
        List<ChatLine> lines = list(row(1), row(2), row(2), row(2), row(3));
        ChatStackRows rows = new ChatStackRows();
        rows.reset(new int[] {12, 12, 12, 12, 12});
        for (int index = 1; index <= 3; index++) {
            float[] span = LostTalesChatOverlayRenderer.mentionSpan(lines,
                    index, -1, rows, 0.0F, -1, 0.0F);
            assertEquals(-48.0F, span[0], 0.0F);
            assertEquals(-12.0F, span[1], 0.0F);
        }
        float[] single = LostTalesChatOverlayRenderer.mentionSpan(lines, 0,
                -1, rows, 0.0F, -1, 0.0F);
        assertEquals(-12.0F, single[0], 0.0F);
        assertEquals(0.0F, single[1], 0.0F);
    }

    @Test
    public void theTopmostMessageReachesIntoTheHeadRoom() {
        List<ChatLine> lines = list(row(1), row(2));
        ChatStackRows rows = new ChatStackRows();
        rows.reset(new int[] {12, 12});
        float[] span = LostTalesChatOverlayRenderer.mentionSpan(lines, 1, -1,
                rows, 0.0F, 1, 2.0F);
        assertEquals(-26.0F, span[0], 0.0F);
        assertEquals(-12.0F, span[1], 0.0F);
    }

    @Test
    public void theBarIsFullAtTheMiddleAndGoneAtTheEnds() {
        assertEquals(200, LostTalesChatOverlayRenderer.mentionBarAlpha(
                -30.0F, -30.0F, 18.0F, 200));
        assertEquals(0, LostTalesChatOverlayRenderer.mentionBarAlpha(
                -48.0F, -30.0F, 18.0F, 200));
        assertEquals(0, LostTalesChatOverlayRenderer.mentionBarAlpha(
                -12.0F, -30.0F, 18.0F, 200));
        assertEquals(100, LostTalesChatOverlayRenderer.mentionBarAlpha(
                -21.0F, -30.0F, 18.0F, 200));
    }

    private static List<ChatLine> list(ChatLine... rows) {
        List<ChatLine> lines = new ArrayList<ChatLine>();
        for (ChatLine row : rows) {
            lines.add(row);
        }
        return lines;
    }

    private static ChatLine row(int id) {
        return new ChatLine(0, new ChatComponentText("words"), id);
    }
}
