package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.emoji.ChatEmojiSuggester;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Live shortcode completion list shown above the chat input while an
 * unclosed {@code :prefix} sits at the cursor. Selection state lives here;
 * applying a completion to the input field is the chat screen's job.
 */
final class ChatEmojiSuggestionBox {
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 12;
    private static final int PADDING = 2;
    /** Matches the channel selector's gap above the input row. */
    private static final int BOTTOM_MARGIN = 15;

    private List<ChatEmoji> matches = Collections.emptyList();
    private ChatEmojiSuggester.Query query;
    private int selectedIndex;
    private int dismissedColonIndex = -1;

    /** Recomputes the query and matches; cheap enough to run per frame. */
    void update(String text, int cursor) {
        ChatEmojiSuggester.Query found =
                ChatEmojiSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedColonIndex = -1;
            return;
        }
        if (found.colonIndex != this.dismissedColonIndex) {
            this.dismissedColonIndex = -1;
        }
        boolean changed = this.query == null
                || this.query.colonIndex != found.colonIndex
                || !this.query.prefix.equals(found.prefix);
        this.query = found;
        if (changed) {
            this.matches = ChatEmojiSuggester.matches(
                    found.prefix, MAX_ROWS);
            this.selectedIndex = 0;
        }
    }

    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.colonIndex != this.dismissedColonIndex;
    }

    ChatEmoji getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatEmojiSuggester.Query getQuery() {
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
            this.dismissedColonIndex = this.query.colonIndex;
        }
    }

    /** The suggestion under the mouse, or null. Also used for clicks. */
    ChatEmoji suggestionAt(FontRenderer font, int mouseX, int mouseY,
                           int screenHeight, int inputX) {
        if (!isActive()) {
            return null;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        if (mouseX < inputX || mouseX >= inputX + width
                || mouseY < top + PADDING) {
            return null;
        }
        int row = (mouseY - top - PADDING) / ROW_HEIGHT;
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font, int screenHeight,
              int inputX, int mouseX, int mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        Gui.drawRect(inputX, top, inputX + width, bottom,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE0));
        for (int row = 0; row < this.matches.size(); row++) {
            ChatEmoji emoji = this.matches.get(row);
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            boolean hovered = mouseX >= inputX
                    && mouseX < inputX + width
                    && mouseY >= rowTop && mouseY < rowTop + ROW_HEIGHT;
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
            ChatEmojiRenderer.draw(minecraft, emoji,
                    inputX + 3, rowTop + 1, ChatEmoji.SPRITE_SIZE, 255);
            LostTalesChatVisualStyle.drawPlain(font, emoji.getShortcode(),
                    inputX + 3 + ChatEmoji.SPRITE_SIZE + 4, rowTop + 2,
                    row == this.selectedIndex ? 255 : 200);
        }
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        for (ChatEmoji emoji : this.matches) {
            width = Math.max(width,
                    font.getStringWidth(emoji.getShortcode()));
        }
        return width + ChatEmoji.SPRITE_SIZE + 12;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
