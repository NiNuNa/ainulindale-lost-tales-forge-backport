package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmojiParser;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;

/**
 * A short run of words that may hold emoji shortcodes, drawn as a message
 * line draws them: every {@code :name:} that names an emoji is its sprite
 * in the slot a message line gives one, and everything else is text with
 * the chat's shadow. What a status line is drawn with wherever it shows —
 * the member list, a card, the head button's menu.
 */
final class ChatInlineText {
    private ChatInlineText() {}

    /**
     * The run's drawn width: its text by the font, with {@code style} —
     * a formatting code such as italics, or empty — ahead of every piece,
     * and each emoji the slot it stands in.
     */
    static int width(FontRenderer font, String text, String style) {
        if (font == null || text == null) {
            return 0;
        }
        int width = 0;
        for (ChatEmojiParser.Segment segment : ChatEmojiParser.split(text)) {
            width += segment.isEmoji() ? ChatInlineIcons.SLOT_WIDTH
                    : font.getStringWidth(style + segment.getText());
        }
        return width;
    }

    /**
     * As much of the run as fits in {@code room}: the text cut where the
     * font says, an emoji whole or not at all.
     */
    static String trimToWidth(FontRenderer font, String text, String style,
                              int room) {
        if (font == null || text == null || width(font, text, style) <= room) {
            return text == null ? "" : text;
        }
        StringBuilder kept = new StringBuilder();
        int used = 0;
        for (ChatEmojiParser.Segment segment : ChatEmojiParser.split(text)) {
            if (segment.isEmoji()) {
                if (used + ChatInlineIcons.SLOT_WIDTH > room) {
                    break;
                }
                kept.append(':').append(segment.getEmoji().getName())
                        .append(':');
                used += ChatInlineIcons.SLOT_WIDTH;
                continue;
            }
            String piece = font.trimStringToWidth(style + segment.getText(),
                    room - used);
            String words = piece.startsWith(style)
                    ? piece.substring(style.length()) : piece;
            kept.append(words);
            used += font.getStringWidth(style + words);
            if (words.length() < segment.getText().length()) {
                break;
            }
        }
        return kept.toString();
    }

    /**
     * Draws the run from ({@code x}, {@code y}), the text's top, its words
     * in {@code rgb} with {@code style} ahead of every piece and its
     * emojis beside them, all at {@code alpha}.
     */
    static void draw(Minecraft minecraft, FontRenderer font, String text,
                     String style, int x, int y, int rgb, int alpha) {
        if (font == null || text == null
                || alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        List<ChatEmojiParser.Segment> segments = ChatEmojiParser.split(text);
        int cursor = x;
        for (ChatEmojiParser.Segment segment : segments) {
            if (segment.isEmoji()) {
                int slot = ChatInlineIcons.SLOT_WIDTH;
                ChatInlineIcons.drawEmoji(minecraft, segment.getEmoji(),
                        ChatInlineIcons.boxLeft(cursor, slot),
                        ChatInlineIcons.boxTop(y, slot),
                        ChatInlineIcons.contentSize(slot), alpha);
                cursor += slot;
                continue;
            }
            String piece = style + segment.getText();
            LostTalesChatVisualStyle.drawColored(font, piece, cursor, y, rgb,
                    alpha);
            cursor += font.getStringWidth(piece);
        }
    }
}
