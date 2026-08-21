package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatMentionCandidate;
import com.ninuna.losttales.chat.ChatNameSuggester;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Live {@code @Name} completion list shown above the chat input, the
 * counterpart of {@link ChatEmojiSuggestionBox} for player mentions.
 * Selection state lives here; applying a completion is the chat screen's
 * job. Candidates are supplied on update, already shaped for the selected
 * channel (account identity in OOC, character identity otherwise), so this
 * box stays free of network and world lookups and never shows the same
 * player twice.
 */
final class ChatNameSuggestionBox {
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 11;
    private static final int PADDING = 2;
    /** Matches the tab row's gap above the input row. */
    private static final int BOTTOM_MARGIN = 15;

    private List<ChatMentionCandidate> matches = Collections.emptyList();
    private ChatNameSuggester.Query query;
    private int selectedIndex;
    private int dismissedAtIndex = -1;
    private int candidateRevision = -1;

    /**
     * Recomputes the query and matches. {@code revision} identifies the
     * candidate list build so matches are only refiltered when either the
     * typed prefix or the candidate set actually changed.
     */
    void update(String text, int cursor,
                List<ChatMentionCandidate> candidates, int revision) {
        ChatNameSuggester.Query found =
                ChatNameSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedAtIndex = -1;
            return;
        }
        if (found.atIndex != this.dismissedAtIndex) {
            this.dismissedAtIndex = -1;
        }
        boolean changed = this.query == null
                || this.query.atIndex != found.atIndex
                || !this.query.prefix.equals(found.prefix)
                || this.candidateRevision != revision;
        this.query = found;
        if (changed) {
            this.matches = ChatNameSuggester.matches(
                    found.prefix, candidates, MAX_ROWS);
            this.candidateRevision = revision;
            if (this.selectedIndex >= this.matches.size()) {
                this.selectedIndex = 0;
            }
        }
    }

    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.atIndex != this.dismissedAtIndex;
    }

    ChatMentionCandidate getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatNameSuggester.Query getQuery() {
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
            this.dismissedAtIndex = this.query.atIndex;
        }
    }

    boolean contains(FontRenderer font, int mouseX, int mouseY,
                     int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        return mouseX >= inputX && mouseX < inputX + width
                && mouseY >= top && mouseY < screenHeight - BOTTOM_MARGIN;
    }

    /** The candidate under the mouse, or null. Also used for clicks. */
    ChatMentionCandidate suggestionAt(FontRenderer font, int mouseX,
                                      int mouseY, int screenHeight,
                                      int inputX) {
        if (!contains(font, mouseX, mouseY, screenHeight, inputX)
                || mouseY < boxTop(screenHeight) + PADDING) {
            return null;
        }
        int row = (mouseY - boxTop(screenHeight) - PADDING) / ROW_HEIGHT;
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(FontRenderer font, ChatPointerRegions regions,
              int screenHeight, int inputX, int mouseX, int mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        regions.add(inputX, top, inputX + width, bottom);
        Gui.drawRect(inputX, top, inputX + width, bottom,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE0));
        for (int row = 0; row < this.matches.size(); row++) {
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
            LostTalesChatVisualStyle.drawPlain(font,
                    "@" + this.matches.get(row).getDisplayName(),
                    inputX + 4, rowTop + 2,
                    row == this.selectedIndex ? 255 : 200);
        }
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        for (int index = 0; index < this.matches.size(); index++) {
            width = Math.max(width, font.getStringWidth(
                    "@" + this.matches.get(index).getDisplayName()));
        }
        return width + 8;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
