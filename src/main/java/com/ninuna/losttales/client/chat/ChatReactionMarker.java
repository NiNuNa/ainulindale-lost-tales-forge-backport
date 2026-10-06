package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiFramedButton;
import com.ninuna.losttales.chat.ChatMessageIds;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatForeignEmoji;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.event.ClickEvent;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.IChatComponent;

/**
 * One reaction chip under a message: the emoji, how many reacted with
 * it, which of the reader's identities are among them, and the message
 * it belongs to. The line is shared by every copy of its conversation,
 * so the chip stands lit in a copy whose identity reacted
 * ({@link Data#mineFor}); the copy being drawn is {@link #reader}.
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
    /** The button a reaction row ends on, adding another reaction. */
    private static final String ADD_PREFIX = "losttales-chat-reaction-add:";
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
    /**
     * The add button's width: the emoji's box with the frame's inset
     * either side, a square as tall as a chip.
     */
    static final int ADD_WIDTH = PAD + ICON + PAD;
    /** A digit's advance when no font can be asked: the game's own. */
    private static final int DIGIT_WIDTH = 6;

    /** Between two of the reader's identities in the marker. */
    private static final String MINE_SEPARATOR = ",";

    /** The copy whose lines are being drawn; null for the closed feed. */
    private static ConversationPage reader;

    private ChatReactionMarker() {}

    /** The copy whose lines are drawn from now on, its chips lit for its identity; null for the feed. */
    static void readAs(ConversationPage copy) {
        reader = copy;
    }

    /** The copy whose lines are being drawn; null for the closed feed. */
    static ConversationPage reader() {
        return reader;
    }

    /**
     * A chip for the emoji with reaction key {@code emoji}, those of the
     * reader's identities who reacted with it {@code mineAs}.
     */
    static ChatComponentText create(String emoji, int count, List<UUID> mineAs,
                                    long messageId, int countWidth) {
        int width = PAD + ICON + GAP + Math.max(0, countWidth) + TRAIL;
        StringBuilder mine = new StringBuilder();
        if (mineAs != null) {
            for (UUID id : mineAs) {
                if (mine.length() > 0) {
                    mine.append(MINE_SEPARATOR);
                }
                mine.append(id);
            }
        }
        ChatComponentText marker = new ChatComponentText("");
        ChatStyle style = marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        PREFIX + count + ':' + mine + ':'
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
            List<UUID> mineAs = new ArrayList<UUID>();
            if (fields[1].length() > 0) {
                for (String id : fields[1].split(MINE_SEPARATOR)) {
                    mineAs.add(UUID.fromString(id));
                }
            }
            return new Data(fields[4], count, mineAs, messageId, width);
        } catch (IllegalArgumentException ignored) {
            // A number or an id this build never wrote names no chip.
            return null;
        }
    }

    static boolean isMarker(IChatComponent component) {
        return decode(component) != null;
    }

    /**
     * The width the chip declares, or the add button's, or -1 when the
     * run is neither.
     */
    static int widthOf(IChatComponent component) {
        if (isAddButton(component)) {
            return ADD_WIDTH;
        }
        Data data = decode(component);
        return data == null ? -1 : data.width;
    }

    /**
     * The button a message's reaction row ends on, the way Discord's
     * does: a press opens the emoji picker aimed at the message, so
     * another reaction is added without reaching for the message's
     * toolbar.
     */
    static ChatComponentText addButton(long messageId) {
        ChatComponentText marker = new ChatComponentText("");
        ChatStyle style = marker.getChatStyle().setChatClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND,
                        ADD_PREFIX + messageId));
        marker.setChatStyle(style);
        return marker;
    }

    static boolean isAddButton(IChatComponent component) {
        return addButtonMessageId(component) != ChatMessageIds.NONE;
    }

    /**
     * The message an add button reacts to, or {@link ChatMessageIds#NONE}
     * when the run is not one.
     */
    static long addButtonMessageId(IChatComponent component) {
        if (component == null || component.getChatStyle() == null) {
            return ChatMessageIds.NONE;
        }
        ClickEvent event = component.getChatStyle().getChatClickEvent();
        String value = event == null ? null : event.getValue();
        if (event == null || event.getAction()
                != ClickEvent.Action.SUGGEST_COMMAND
                || value == null || !value.startsWith(ADD_PREFIX)) {
            return ChatMessageIds.NONE;
        }
        try {
            long messageId = Long.parseLong(
                    value.substring(ADD_PREFIX.length()));
            return ChatMessageIds.isServerId(messageId) ? messageId
                    : ChatMessageIds.NONE;
        } catch (NumberFormatException ignored) {
            return ChatMessageIds.NONE;
        }
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
        /** The reader's identities among those who reacted. */
        final List<UUID> mineAs;
        final long messageId;
        final int width;

        private Data(String key, int count, List<UUID> mineAs, long messageId,
                     int width) {
            this.key = key;
            this.emoji = ChatEmoji.fromName(key);
            this.count = count;
            this.mineAs = Collections.unmodifiableList(mineAs);
            this.messageId = messageId;
            this.width = width;
        }

        /**
         * Whether the identity {@code copy} reacts as is among those who
         * reacted: the chip stands lit there, and a click takes the
         * reaction back. For no copy, the feed, the identity played.
         */
        boolean mineFor(ConversationPage copy) {
            UUID id = ClientChatIdentities.reactorIdOf(copy);
            return id != null && this.mineAs.contains(id);
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
