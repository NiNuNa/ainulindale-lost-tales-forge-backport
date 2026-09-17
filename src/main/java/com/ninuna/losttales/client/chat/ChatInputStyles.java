package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMarkdown;
import com.ninuna.losttales.gui.style.LostTalesColors;
import java.util.Arrays;
import net.minecraft.util.EnumChatFormatting;

/**
 * How the input field shows the chat's markup while it is typed, read
 * from {@link ChatMarkdown#layout}, and a command as the chat's inline
 * code: which decoration codes a run is drawn with, what colour a mark,
 * a code span or a spoiler wears, and where a glyph is bold and so a
 * pixel wider. Pure: the field asks, these answer, and a test can ask
 * the same.
 */
final class ChatInputStyles {
    /** The marker characters, dimmed: there, but not the words. */
    private static final int MARK_RGB =
            LostTalesColors.rgb(LostTalesColors.TEXT_DIM);
    /** Spoiler text, subdued rather than hidden: it is still being written. */
    private static final int SPOILER_RGB =
            LostTalesColors.rgb(LostTalesColors.TEXT_MUTED);

    private ChatInputStyles() {}

    /**
     * Every character's style as the field shows it, or null for text
     * with nothing to style, the common case. A command is the chat's
     * inline code, whole, as a command is wherever the chat shows one,
     * and nothing in it is markup; only the words a whisper verb sends
     * after its name are laid out, as the whisper they become.
     */
    static int[] layout(String text) {
        int codeEnd = commandCodeLength(text);
        if (codeEnd == 0) {
            return ChatMarkdown.hasMarkup(text)
                    ? ChatMarkdown.layout(text) : null;
        }
        int[] styles = new int[text.length()];
        Arrays.fill(styles, 0, codeEnd, ChatMarkdown.Span.CODE);
        String words = text.substring(codeEnd);
        if (ChatMarkdown.hasMarkup(words)) {
            System.arraycopy(ChatMarkdown.layout(words), 0, styles, codeEnd,
                    words.length());
        }
        return styles;
    }

    /**
     * How much of the text, from its start, is a command shown as inline
     * code: all of a command, a whisper verb up to the words it sends,
     * none of a message.
     */
    static int commandCodeLength(String text) {
        if (!ChatInputRules.isCommand(text)) {
            return 0;
        }
        return ChatInputRules.isWhisperCommand(text)
                ? ChatInputRules.whisperMessageStart(text) : text.length();
    }

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
     * empty for none. Code is in italics, as the sent line's inline code
     * is; a spoiler is a colour, not a decoration; and a mark wears the
     * decorations of the text around it so its width matches theirs.
     */
    static String prefixOf(int style) {
        StringBuilder prefix = new StringBuilder();
        if ((style & ChatMarkdown.Span.BOLD) != 0) {
            prefix.append(EnumChatFormatting.BOLD);
        }
        if ((style & (ChatMarkdown.Span.ITALIC
                | ChatMarkdown.Span.CODE)) != 0) {
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
     * mention pass chose for it: a mark is always dimmed, code always in
     * the chat's aside tone, as the sent line's inline code is — nothing
     * inside it is a mention — and a spoiler subdued unless it holds a
     * mention, which keeps its colour as it does in the sent line.
     */
    static int colorOf(int style, int mentionColor) {
        if ((style & ChatMarkdown.Span.MARK) != 0) {
            return MARK_RGB;
        }
        if ((style & ChatMarkdown.Span.CODE) != 0) {
            return LostTalesChatVisualStyle.asideRgb();
        }
        if ((style & ChatMarkdown.Span.SPOILER) != 0
                && mentionColor == LostTalesChatVisualStyle.IVORY) {
            return SPOILER_RGB;
        }
        return mentionColor;
    }
}
