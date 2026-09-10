package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.chat.ChatChannelSuggester;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * The list a typed {@code #} opens over the input: the channels the
 * prefix names, each with its icon and its {@code #name} in its own
 * colour, exactly as the emoji and mention lists open on {@code :}
 * and {@code @}. Picking one writes the channel's token into the
 * message, which is drawn as a link to the channel.
 */
final class ChatChannelSuggestionBox {
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 11;
    private static final int PADDING = 2;
    private static final int ICON_SIZE = 8;
    private static final int ICON_GAP = 3;
    /** Matches the tab row's gap above the input row. */
    private static final int BOTTOM_MARGIN = 15;

    private List<ChatChannel> matches = Collections.emptyList();
    private ChatChannelSuggester.Query query;
    private int selectedIndex;
    private int dismissedAtIndex = -1;

    /** Recomputes the query and its matches against the channels offered. */
    void update(String text, int cursor, List<ChatChannel> channels) {
        ChatChannelSuggester.Query found =
                ChatChannelSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedAtIndex = -1;
            return;
        }
        if (found.hashIndex != this.dismissedAtIndex) {
            this.dismissedAtIndex = -1;
        }
        boolean changed = this.query == null
                || this.query.hashIndex != found.hashIndex
                || !this.query.prefix.equals(found.prefix);
        this.query = found;
        if (changed) {
            this.matches = ChatChannelSuggester.matches(found.prefix,
                    channels, MAX_ROWS);
            if (this.selectedIndex >= this.matches.size()) {
                this.selectedIndex = 0;
            }
        }
    }

    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.hashIndex != this.dismissedAtIndex;
    }

    ChatChannel getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatChannelSuggester.Query getQuery() {
        return this.query;
    }

    void moveSelection(int delta) {
        if (!isActive()) {
            return;
        }
        int size = this.matches.size();
        this.selectedIndex =
                ((this.selectedIndex + delta) % size + size) % size;
    }

    /** Hides the current query's list until a new query starts. */
    void dismiss() {
        if (this.query != null) {
            this.dismissedAtIndex = this.query.hashIndex;
        }
    }

    boolean contains(FontRenderer font, double mouseX, double mouseY,
                     int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        return mouseX >= inputX && mouseX < inputX + width
                && mouseY >= top && mouseY < screenHeight - BOTTOM_MARGIN;
    }

    /**
     * The row under the point, or -1: the one test the row's highlight,
     * a press and the pointer all ask.
     */
    int rowAt(FontRenderer font, double mouseX, double mouseY,
              int screenHeight, int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return -1;
        }
        int row = (int)Math.floor((mouseY - boxTop(screenHeight) - PADDING)
                / (double)ROW_HEIGHT);
        return row >= 0 && row < this.matches.size() ? row : -1;
    }

    /** The suggestion on a row, or null. */
    ChatChannel at(int row) {
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, int screenHeight, int inputX,
              double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        int hoveredRow = rowAt(font, mouseX, mouseY, screenHeight, inputX);
        regions.add(inputX, top, inputX + width, bottom);
        Gui.drawRect(inputX, top, inputX + width, bottom,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE0));
        for (int row = 0; row < this.matches.size(); row++) {
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            boolean hovered = row == hoveredRow;
            if (hovered) {
                this.selectedIndex = row;
            }
            if (row == this.selectedIndex) {
                Gui.drawRect(inputX + 1, rowTop, inputX + width - 1,
                        rowTop + ROW_HEIGHT,
                        LostTalesChatVisualStyle.argb(
                                LostTalesChatVisualStyle
                                        .SURFACE_HIGHLIGHT_RGB, 0xC8));
            }
            ChatChannel channel = this.matches.get(row);
            ChatEmoji icon = ChatChannelIcons.iconOf(channel);
            if (icon != null) {
                ChatEmojiRenderer.draw(minecraft, icon, inputX + 4,
                        rowTop + 1, ICON_SIZE, 255);
            }
            LostTalesChatVisualStyle.drawColored(font, label(channel),
                    inputX + 4 + ICON_SIZE + ICON_GAP, rowTop + 2,
                    ClientChatChannelState.displayColor(channel), 255);
        }
    }

    /** What a row reads: the channel's shown name behind the hash. */
    static String label(ChatChannel channel) {
        return "#" + channel.getDisplayName();
    }

    private int boxWidth(FontRenderer font) {
        int widest = 0;
        for (ChatChannel channel : this.matches) {
            widest = Math.max(widest, font.getStringWidth(label(channel)));
        }
        return PADDING * 2 + 4 + ICON_SIZE + ICON_GAP + widest + 4;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN - PADDING * 2
                - this.matches.size() * ROW_HEIGHT;
    }
}
