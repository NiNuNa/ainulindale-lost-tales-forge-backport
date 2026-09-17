package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * One reaction chip under a message: the emoji, how many reacted with
 * it, whether the reader is one of them, and the message it belongs to.
 *
 * <p>Carried like every marker, on the click event of an empty run, so
 * it survives vanilla's shallow style copies and adds nothing to a
 * copy of the line's words. The chip's width is worked out when the
 * line is built and declared in the marker, the way a head's slot is,
 * so drawing, wrapping and hit testing all advance by exactly the same
 * pixels: the padding, the emoji at its native size, a gap, the count,
 * and the padding again.</p>
 *
 * <p>The emoji is a reaction key and comes last in the marker, since a
 * custom emoji's foreign key holds a colon. An emoji the registry lacks
 * takes the emoji's cell all the same, with a question mark in it.</p>
 */
final class ChatReactionMarker {
    private static final String PREFIX = "losttales-chat-reaction:";
    /**
     * From the chip's edge to what it holds: a chip is a framed button,
     * so the frame's edge and its padding.
     */
    static final int PAD = LostTalesUiFramedButton.INSET;
    /** The emoji, drawn one texel to one pixel. */
    static final int ICON = (int)ChatInlineIcons.CONTENT_SIZE;
    /**
     * The chip's height: the framed buttons' one height, the emoji with
     * the frame's inset above and below it. Taller than a message row,
     * so a reaction row is made as tall as its chips wherever the chat's
     * small size cannot shrink them into a line
     * ({@link ChatStackRows#reactionRowHeight}).
     */
    static final int HEIGHT = LostTalesUiFramedButton.HEIGHT;
    /**
     * How far below the chip's top edge the count's text starts: a row
     * below the emoji's box, so the count's capitals stand half a pixel
     * above the box's middle.
     */
    static final int TEXT_DROP = PAD + 1;
    /** Between the emoji and the count. */
    static final int GAP = 2;
    /**
     * After the count: the font's own trailing column, standing for one
     * of the padding's pixels, then the rest of the frame's inset.
     */
    static final int TRAIL = PAD - 1;
    /** Between two chips. */
    static final int BETWEEN = 2;
    /** A digit's advance when no font can be asked: the game's own. */
    private static final int DIGIT_WIDTH = 6;

    private ChatReactionMarker() {}

    static ChatComponentText create(ChatEmoji emoji, int count, boolean mine,
                                    long messageId, int countWidth) {
        return create(emoji.getName(), count, mine, messageId, countWidth);
    }

    /** A chip for the emoji with reaction key {@code emoji}. */
    static ChatComponentText create(String emoji, int count, boolean mine,
                                    long messageId, int countWidth) {
        int width = PAD + ICON + GAP + Math.max(0, countWidth) + TRAIL;
        ChatComponentText marker = new ChatComponentText("");
        ChatStyle style = marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        PREFIX + count + ':' + (mine ? '1' : '0') + ':'
                                + messageId + ':' + width + ':' + emoji));
        marker.setChatStyle(style);
        return marker;
    }

    /** The count's width as the chip will draw it. */
    static int countWidth(FontRenderer font, int count) {
        String text = Integer.toString(Math.max(0, count));
        return font == null ? text.length() * DIGIT_WIDTH
                : font.getStringWidth(text);
    }

    static Data decode(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return null;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(PREFIX)) {
            return null;
        }
        String[] fields = value.substring(PREFIX.length()).split(":", 5);
        if (fields.length != 5 || !ChatForeignEmoji.isReactionKey(fields[4])) {
            return null;
        }
        try {
            int count = Integer.parseInt(fields[0]);
            long messageId = Long.parseLong(fields[2]);
            int width = Integer.parseInt(fields[3]);
            if (count < 1 || width < 0 || !ChatMessageIds.isServerId(messageId)) {
                return null;
            }
            return new Data(fields[4], count, "1".equals(fields[1]),
                    messageId, width);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    /** The width the chip declares, or -1 when the run is not a chip. */
    static int widthOf(IChatComponent component) {
        Data data = decode(component);
        return data == null ? -1 : data.width;
    }

    /**
     * Whether a drawn row is a message's reaction row: its first run
     * that is not layout, a gap or nothing at all is a chip.
     */
    static boolean isReactionRow(IChatComponent row) {
        if (row == null) {
            return false;
        }
        for (Object value : row) {
            if (!(value instanceof IChatComponent)) {
                continue;
            }
            IChatComponent part = (IChatComponent)value;
            if (isMarker(part)) {
                return true;
            }
            if (ChatLayoutMarker.isMarker(part)
                    || ChatSpacerMarker.isMarker(part)
                    || part.getUnformattedTextForChat().length() == 0) {
                continue;
            }
            return false;
        }
        return false;
    }

    static final class Data {
        /** The emoji's reaction key, what the server is asked by. */
        final String key;
        /** The registry emoji drawn; null for a foreign one. */
        final ChatEmoji emoji;
        final int count;
        /** Whether the reader is one of those who reacted. */
        final boolean mine;
        final long messageId;
        final int width;

        private Data(String key, int count, boolean mine, long messageId,
                     int width) {
            this.key = key;
            this.emoji = ChatEmoji.fromName(key);
            this.count = count;
            this.mine = mine;
            this.messageId = messageId;
            this.width = width;
        }

        String countText() {
            return Integer.toString(this.count);
        }

        /** The emoji's name as a card shows it, {@code :name:}. */
        String label() {
            return ChatForeignEmoji.label(this.key);
        }
    }
}
