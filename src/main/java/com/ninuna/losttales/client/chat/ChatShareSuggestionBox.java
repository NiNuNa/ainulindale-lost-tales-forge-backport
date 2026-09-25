package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.window.PointerRegions;
import com.ninuna.losttales.client.window.WindowStyle;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiItemIcon;
import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.share.ChatShareKind;
import com.ninuna.losttales.chat.share.ChatShareSuggester;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
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
    /** The rows stand two pixels inside the frame's ink, as a framed button's content does. */
    private static final int PADDING = WindowStyle.POPUP_INSET;
    /** Between the glyph's slot and its name. */
    private static final int ICON_GAP = 4;
    /**
     * How far above the input anchor ({@link ChatInputBar#inputAnchor})
     * the box ends: one pixel clear of the bar's top.
     */
    private static final int BOTTOM_MARGIN = 15;
    /** Candidate snapshots are refreshed at most this often while open. */
    private static final long REFRESH_INTERVAL_NANOS = 250L * 1000000L;

    private List<ChatShareCandidates.ItemEntry> items =
            Collections.emptyList();
    private List<ChatShareCandidates.MarkerEntry> markers =
            Collections.emptyList();
    private List<ChatShareCandidates.QuestEntry> quests =
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
                    : found.kind == ChatShareKind.QUEST ? this.quests
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
        List<ChatShareCandidates.QuestEntry> builtQuests =
                ChatShareCandidates.quests();
        boolean changed = !ChatShareCandidates.sameItems(
                builtItems, this.items)
                || builtMarkers.size() != this.markers.size()
                || builtQuests.size() != this.quests.size();
        this.items = builtItems;
        this.markers = builtMarkers;
        this.quests = builtQuests;
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

    boolean contains(FontRenderer font, double mouseX, double mouseY,
                     int screenHeight, int inputX) {
        if (!isActive()) {
            return false;
        }
        int top = boxTop(screenHeight);
        return LostTalesUiHitBox.contains(mouseX, mouseY, inputX, top,
                boxWidth(font), screenHeight - BOTTOM_MARGIN - top);
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
    ChatShareCandidates.Entry at(int row) {
        return row >= 0 && row < this.matches.size()
                ? this.matches.get(row) : null;
    }

    void draw(Minecraft minecraft, FontRenderer font,
              PointerRegions regions, int screenHeight,
              int inputX, double mouseX, double mouseY) {
        if (!isActive()) {
            return;
        }
        int width = boxWidth(font);
        int top = boxTop(screenHeight);
        int bottom = screenHeight - BOTTOM_MARGIN;
        int hoveredRow = rowAt(font, mouseX, mouseY, screenHeight, inputX);
        if (hoveredRow >= 0) {
            this.selectedIndex = hoveredRow;
        }
        regions.add(inputX, top, inputX + width, bottom);
        WindowStyle.drawPopupList(inputX, top, inputX + width,
                bottom, top + PADDING, ROW_HEIGHT,
                this.selectedIndex < this.matches.size()
                        ? this.selectedIndex : -1);
        for (int row = 0; row < this.matches.size(); row++) {
            ChatShareCandidates.Entry entry = this.matches.get(row);
            int rowTop = top + PADDING + row * ROW_HEIGHT;
            drawIcon(minecraft, entry, inputX + PADDING, rowTop + 1);
            LostTalesChatVisualStyle.drawPlain(font, entry.label(),
                    inputX + PADDING + ICON_SLOT + ICON_GAP, rowTop + 2,
                    row == this.selectedIndex ? 255 : 200);
        }
    }

    private static final int ICON_SLOT = ChatInlineIcons.SLOT_WIDTH;

    /** The row's glyph, on the same box the text line would give it. */
    private static void drawIcon(Minecraft minecraft,
                                 ChatShareCandidates.Entry entry,
                                 int x, int y) {
        float boxX = ChatInlineIcons.boxLeft(x, ICON_SLOT);
        float boxY = ChatInlineIcons.boxTop(y + 1, ICON_SLOT);
        if (entry instanceof ChatShareCandidates.ItemEntry) {
            LostTalesUiItemIcon.drawFitted(minecraft,
                    ((ChatShareCandidates.ItemEntry)entry).stack,
                    boxX, boxY, ChatInlineIcons.CONTENT_SIZE, 255);
        } else if (entry instanceof ChatShareCandidates.MarkerEntry) {
            ChatShareCandidates.MarkerEntry marker =
                    (ChatShareCandidates.MarkerEntry)entry;
            ChatInlineIcons.drawMarker(minecraft, marker.marker.getIconName(),
                    ChatInlineIcons.markerRgb(marker.marker.getColorName()),
                    boxX, boxY, ChatInlineIcons.CONTENT_SIZE, 255);
        } else if (entry instanceof ChatShareCandidates.QuestEntry) {
            LostTalesUiSheet.QUEST.drawWithShadow(boxX, boxY, 255);
        }
    }

    private int boxWidth(FontRenderer font) {
        int width = 0;
        for (int index = 0; index < this.matches.size(); index++) {
            width = Math.max(width,
                    font.getStringWidth(this.matches.get(index).label()));
        }
        return PADDING + ICON_SLOT + ICON_GAP + width + PADDING;
    }

    private int boxTop(int screenHeight) {
        return screenHeight - BOTTOM_MARGIN
                - this.matches.size() * ROW_HEIGHT - PADDING * 2;
    }
}
