package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.gui.style.LostTalesColors;
import net.minecraft.util.EnumChatFormatting;

/**
 * How the input field shows the chat's markup while it is typed, read
 * from {@link ChatMarkdown#layout}: which decoration codes a run is
 * drawn with, what colour a mark, a code span or a spoiler wears, and
 * where a glyph is bold and so a pixel wider. Pure: the field asks,
 * these answer, and a test can ask the same.
 */
final class ChatInputStyles {
    /** The marker characters, dimmed: there, but not the words. */
    private static final int MARK_RGB =
            LostTalesColors.rgb(LostTalesColors.TEXT_DIM);
    /** Spoiler text, subdued rather than hidden: it is still being written. */
    private static final int SPOILER_RGB =
            LostTalesColors.rgb(LostTalesColors.TEXT_MUTED);
    /** Code, in the grey the sent line shows it in. */
    private static final int CODE_RGB =
            LostTalesChatVisualStyle.paletteRgb(EnumChatFormatting.GRAY);

    private ChatInputStyles() {}

    /** The style at an index; plain for text laid out without markup. */
    static int styleAt(int[] styles, int index) {
        return styles == null || index < 0 || index >= styles.length
                ? ChatMarkdown.Span.PLAIN : styles[index];
    }

    static boolean isBold(int[] styles, int index) {
        return (styleAt(styles, index) & ChatMarkdown.Span.BOLD) != 0;
    }

    static boolean isCode(int[] styles, int index) {
        return (styleAt(styles, index) & ChatMarkdown.Span.CODE) != 0;
    }

    /**
     * The section-sign decoration codes a run of the style is drawn
     * behind: bold, italic, underline, strikethrough, in that order;
     * empty for none. Code and spoiler are colours, not decorations, and
     * a mark wears the decorations of the text around it so its width
     * matches theirs.
     */
    static String prefixOf(int style) {
        StringBuilder prefix = new StringBuilder();
        if ((style & ChatMarkdown.Span.BOLD) != 0) {
            prefix.append(EnumChatFormatting.BOLD);
        }
        if ((style & ChatMarkdown.Span.ITALIC) != 0) {
            prefix.append(EnumChatFormatting.ITALIC);
        }
        if ((style & ChatMarkdown.Span.UNDERLINE) != 0) {
            prefix.append(EnumChatFormatting.UNDERLINE);
        }
        if ((style & ChatMarkdown.Span.STRIKETHROUGH) != 0) {
            prefix.append(EnumChatFormatting.STRIKETHROUGH);
        }
        return prefix.toString();
    }

    /**
     * The colour a run of the style is drawn in, given the colour the
     * mention pass chose for it: a mark is always dimmed, code always
     * grey — nothing inside it is a mention — and a spoiler subdued
     * unless it holds a mention, which keeps its colour as it does in
     * the sent line.
     */
    static int colorOf(int style, int mentionColor) {
        if ((style & ChatMarkdown.Span.MARK) != 0) {
            return MARK_RGB;
        }
        if ((style & ChatMarkdown.Span.CODE) != 0) {
            return CODE_RGB;
        }
        if ((style & ChatMarkdown.Span.SPOILER) != 0
                && mentionColor == LostTalesChatVisualStyle.IVORY) {
            return SPOILER_RGB;
        }
        return mentionColor;
    }
}
