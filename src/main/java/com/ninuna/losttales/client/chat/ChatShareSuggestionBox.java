package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareSuggester;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Live completion list shown above the chat input while an unclosed
 * {@code [i:prefix} or {@code [m:prefix} sits at the cursor, the share
 * counterpart of {@link ChatEmojiSuggestionBox}. Candidates come from
 * {@link ChatShareCandidates}, so a typed completion, the pickers, and the
 * send-time resolution all name the same stack or marker. Selection state
 * lives here; applying a completion is the chat screen's job.
 */
final class ChatShareSuggestionBox {
    static final int MAX_ROWS = 8;
    private static final int ROW_HEIGHT = 12;
    private static final int PADDING = 2;
    /** Matches the other popups' gap above the input row. */
    private static final int BOTTOM_MARGIN = 15;
    /** Candidate snapshots are refreshed at most this often while open. */
    private static final long REFRESH_INTERVAL_NANOS = 250L * 1000000L;

    private List<ChatShareCandidates.ItemEntry> items =
            Collections.emptyList();
    private List<ChatShareCandidates.MarkerEntry> markers =
            Collections.emptyList();
    private List<ChatShareCandidates.Entry> matches = Collections.emptyList();
    private ChatShareSuggester.Query query;
    private int selectedIndex;
    private int dismissedOpenIndex = -1;
    private long candidatesBuiltNanos;
    private String lastPrefix;
    private ChatShareKind lastKind;

    /** Recomputes the query, candidates, and matches. */
    void update(String text, int cursor, EntityPlayer player) {
        ChatShareSuggester.Query found =
                ChatShareSuggester.findQuery(text, cursor);
        if (found == null) {
            this.query = null;
            this.matches = Collections.emptyList();
            this.dismissedOpenIndex = -1;
            this.lastPrefix = null;
            this.lastKind = null;
            return;
        }
        if (found.openIndex != this.dismissedOpenIndex) {
            this.dismissedOpenIndex = -1;
        }
        boolean candidatesChanged = refreshCandidates(player);
        boolean changed = this.query == null
                || this.query.openIndex != found.openIndex
                || found.kind != this.lastKind
                || !found.prefix.equals(this.lastPrefix);
        this.query = found;
        if (changed || candidatesChanged) {
            this.lastPrefix = found.prefix;
            this.lastKind = found.kind;
            List<? extends ChatShareCandidates.Entry> pool =
                    found.kind == ChatShareKind.MARKER ? this.markers
                            : this.items;
            List<String> labels = new ArrayList<String>(pool.size());
            for (int index = 0; index < pool.size(); index++) {
                labels.add(pool.get(index).name);
            }
            List<Integer> indices = ChatShareSuggester.matches(
                    found.prefix, labels, MAX_ROWS);
            List<ChatShareCandidates.Entry> result =
                    new ArrayList<ChatShareCandidates.Entry>(indices.size());
            for (int index = 0; index < indices.size(); index++) {
                result.add(pool.get(indices.get(index).intValue()));
            }
            this.matches = result;
            if (changed || this.selectedIndex >= this.matches.size()) {
                this.selectedIndex = 0;
            }
        }
    }

    private boolean refreshCandidates(EntityPlayer player) {
        long now = System.nanoTime();
        if (this.candidatesBuiltNanos != 0L
                && now - this.candidatesBuiltNanos < REFRESH_INTERVAL_NANOS) {
            return false;
        }
        this.candidatesBuiltNanos = now;
        List<ChatShareCandidates.ItemEntry> builtItems =
                ChatShareCandidates.items(player);
        List<ChatShareCandidates.MarkerEntry> builtMarkers =
                ChatShareCandidates.markers();
        boolean changed = !ChatShareCandidates.sameItems(
                builtItems, this.items)
                || builtMarkers.size() != this.markers.size();
        this.items = builtItems;
        this.markers = builtMarkers;
        return changed;
    }

    boolean isActive() {
        return this.query != null && !this.matches.isEmpty()
                && this.query.openIndex != this.dismissedOpenIndex;
    }

    ChatShareCandidates.Entry getSelected() {
        return isActive() && this.selectedIndex < this.matches.size()
                ? this.matches.get(this.selectedIndex) : null;
    }

    ChatShareSuggester.Query getQuery() {
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
            this.dismissedOpenIndex = this.query.openIndex;
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

    /** The entry under the mouse, or null. Also used for clicks. */
    ChatShareCandidates.Entry suggestionAt(FontRenderer font, int mouseX,
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

    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, int screenHeight,
              int inputX, int mouseX, int mouseY) {
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
            ChatShareCandidates.Entry entry = this.matches.get(row);
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
            drawIcon(minecraft, entry, inputX + 3, rowTop + 1);
            LostTalesChatVisualStyle.drawPlain(font, entry.label(),
                    inputX + 3 + ICON_SLOT + 4, rowTop + 2,
                    row == this.selectedIndex ? 255 : 200);
        }
    }

    private static final int ICON_SLOT = 10;

    private static void drawIcon(Minecraft minecraft,
                                 ChatShareCandidates.Entry entry,
                                 int x, int y) {
        if (entry instanceof ChatShareCandidates.ItemEntry) {
            ChatShareCandidates.ItemEntry item =
                    (ChatShareCandidates.ItemEntry)entry;
            ChatItemRenderer.drawShadow(minecraft, item.stack,
                    x + 1 + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    y + 1 + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    ChatItemRenderer.ICON_SIZE,
                    LostTalesChatVisualStyle.SHADOW,
                    LostTalesChatVisualStyle.shadowAlpha(255));
            ChatItemRenderer.draw(minecraft, item.stack, x + 1, y + 1,
                    ChatItemRenderer.ICON_SIZE, 255);
        } else if (entry instanceof ChatShareCandidates.MarkerEntry) {
            ChatShareCandidates.MarkerEntry marker =
                    (ChatShareCandidates.MarkerEntry)entry;
            ChatMapMarkerRenderer.drawShadow(minecraft,
                    marker.marker.getIconName(),
                    x + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    y + LostTalesChatVisualStyle.SHADOW_OFFSET,
                    ChatMapMarkerRenderer.ICON_SIZE,
                    LostTalesChatVisualStyle.SHADOW,
                    LostTalesChatVisualStyle.shadowAlpha(255));
            ChatMapMarkerRenderer.draw(minecraft,
                    marker.marker.getIconName(),
                    marker.marker.getColorName(), x, y,
                    ChatMapMarkerRenderer.ICON_SIZE, 255);
        }
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        for (int index = 0; index < this.matches.size(); index++) {
            width = Math.max(width,
                    font.getStringWidth(this.matches.get(index).label()));
        }
        return width + ICON_SLOT + 12;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
