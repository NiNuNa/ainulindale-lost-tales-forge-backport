package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;

/**
 * The quiet mark of a character spoken as in a copy while its player
 * plays another: here, but not in the world (CHAT-05). A hollow square in
 * the aside tone, casting the one shadow, after the name on a line's name
 * row and at the end of a member row's name, before its role-play mark.
 * Drawn from its own pixels, standing in until its artwork is painted;
 * no other mark is square, so it never reads as a status.
 *
 * <p>On a line the mark stands in a run of its own that holds no text and
 * declares its room ({@link ChatInlineIcons#declaredWidth}), so every walk
 * over the row agrees where the title after it starts; the renderer draws
 * the square where the run stands ({@link #drawIn}).</p>
 */
final class ChatWorldMark {
    /** The mark's box. */
    static final int SIZE = 5;
    /** Between the name and the mark. */
    static final int GAP = 3;
    /** The room the mark's run takes in a name row: its gap and its box. */
    static final int RUN_WIDTH = GAP + SIZE;
    private static final String PAYLOAD = "losttales-chat-world";

    private static final String[] SQUARE = {
            "#####",
            "#...#",
            "#...#",
            "#...#",
            "#####"};

    private ChatWorldMark() {}

    /** The run that stands for the mark in a line's name row. */
    static ChatComponentText run() {
        ChatComponentText marker = new ChatComponentText("");
        marker.setChatStyle(marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, PAYLOAD)));
        return marker;
    }

    /** Whether the component is the mark's run. */
    static boolean isMarker(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return false;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        return event != null && event.getAction()
                == ClickEvent.Action.SUGGEST_COMMAND
                && PAYLOAD.equals(event.getValue());
    }

    /**
     * Draws the mark wherever {@code row}, drawn from its own origin in its
     * own text space, holds its run: past the gap, its box centred on the
     * capitals by the one centring rule.
     */
    static void drawIn(FontRenderer font, IChatComponent row, boolean chatOpen,
                       int alpha) {
        if (font == null || row == null || alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        float x = 0.0F;
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (ChatPrefixMarker.isHidden(part, chatOpen)) {
                continue;
            }
            if (isMarker(part)) {
                draw(x + GAP, LostTalesUiInk.centredStart(
                        LostTalesUiInk.CAP_HEIGHT, SIZE), alpha);
                return;
            }
            x += LostTalesChatVisualStyle.partWidth(font, part, chatOpen);
        }
    }

    /** Draws the mark from {@code (x, y)}, over its shadow, at {@code alpha}. */
    static void draw(float x, float y, int alpha) {
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        int shadow = LostTalesUiInk.shadowAlpha(alpha);
        if (shadow > 0) {
            drawPixels(x + LostTalesUiInk.SHADOW_OFFSET,
                    y + LostTalesUiInk.SHADOW_OFFSET,
                    LostTalesUiInk.argb(LostTalesUiInk.SHADOW, shadow));
        }
        drawPixels(x, y, LostTalesUiInk.argb(
                LostTalesChatVisualStyle.asideRgb(), alpha));
        LostTalesUiInk.beginContent();
    }

    /** The square's pixels in one colour, each run of a row one quad, so no pixel is laid down twice. */
    private static void drawPixels(float x, float y, int argb) {
        for (int row = 0; row < SQUARE.length; row++) {
            String pixels = SQUARE[row];
            int column = 0;
            while (column < pixels.length()) {
                if (pixels.charAt(column) != '#') {
                    column++;
                    continue;
                }
                int start = column;
                while (column < pixels.length() && pixels.charAt(column) == '#') {
                    column++;
                }
                LostTalesUiInk.fillRect(x + start, y + row, x + column,
                        y + row + 1.0F, argb);
            }
        }
    }
}
