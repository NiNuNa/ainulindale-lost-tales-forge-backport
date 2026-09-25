package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiInk;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.util.StatCollector;

/**
 * A typing line's words and its three dots: who is typing, in the
 * chat's aside tone and in italics, closed by three dots that brighten
 * and dim one after another as the dots over a typing head do
 * ({@link ChatTypingDots}). The dots change brightness only, never
 * place, since slow motion on pixel art reads as a jump. The window's
 * trailing strip and the closed feed's typing row both draw it.
 */
final class ChatTypingLine {
    private static final String DOT = ".";

    private ChatTypingLine() {}

    /** Who is typing: one to three names, or how many past that. */
    static String words(List<String> names) {
        if (names.size() == 1) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.one", names.get(0));
        }
        if (names.size() == 2) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.two", names.get(0),
                    names.get(1));
        }
        if (names.size() == 3) {
            return StatCollector.translateToLocalFormatted(
                    "gui.losttales.chat.typing.three", names.get(0),
                    names.get(1), names.get(2));
        }
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.chat.typing.many",
                String.valueOf(names.size()));
    }

    /** The width of the words and the dots after them. */
    static int width(FontRenderer font, String words) {
        return font.getStringWidth(words) + dotsWidth(font);
    }

    /**
     * Draws the words, cut to leave the dots room inside {@code room},
     * with their top at {@code y}, and the dots after them. Answers the
     * width drawn.
     */
    static int draw(FontRenderer font, String words, int x, int y, int room,
                    int alpha, long nowNanos) {
        int rgb = LostTalesChatVisualStyle.asideRgb();
        String shown = font.trimStringToWidth(words,
                Math.max(0, room - dotsWidth(font)));
        LostTalesUiInk.drawText(font, "§o" + shown, x, y, rgb,
                alpha);
        int dotX = x + font.getStringWidth(shown);
        for (int index = 0; index < ChatTypingDots.COUNT; index++) {
            LostTalesUiInk.drawText(font, "§o" + DOT, dotX, y,
                    rgb, Math.round(alpha
                            * ChatTypingDots.opacity(index, nowNanos)));
            dotX += font.getStringWidth(DOT);
        }
        return dotX - x;
    }

    private static int dotsWidth(FontRenderer font) {
        return ChatTypingDots.COUNT * font.getStringWidth(DOT);
    }
}
