package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * Emote browser toggled from a small button beside the chat input: a search
 * field, a Favorites row (right-click a cell to toggle), a Frequently Used
 * row, and the full grid, with a shortcode tooltip on hover. All geometry is
 * derived from the current screen size so the picker follows GUI scale,
 * resolution, and chat-input layout changes. Selection returns the emote;
 * inserting its shortcode is the chat screen's responsibility.
 */
final class ChatEmojiPicker {
    static final int BUTTON_SIZE = 12;
    static final int FREQUENT_LIMIT = 6;
    private static final int BUTTON_MARGIN = 2;
    private static final int CELL_SIZE = 14;
    private static final int COLUMNS = 6;
    private static final int PADDING = 4;
    private static final int SEARCH_HEIGHT = 12;
    private static final int LABEL_HEIGHT = 9;
    /** Gap between the panel's bottom edge and the input row. */
    private static final int PANEL_BOTTOM_MARGIN = 15;

    private boolean targetOpen;
    private long transitionNanos;
    private GuiTextField searchField;
    private ChatEmoji hoveredEmoji;

    boolean isOpen() {
        return this.targetOpen;
    }

    void setOpen(boolean open) {
        if (this.targetOpen != open) {
            this.targetOpen = open;
            this.transitionNanos = System.nanoTime();
            if (this.searchField != null) {
                this.searchField.setText("");
                this.searchField.setFocused(false);
            }
        }
    }

    boolean isSearchFocused() {
        return this.targetOpen && this.searchField != null
                && this.searchField.isFocused();
    }

    /** Ticks the search caret blink; called from the screen's updateScreen. */
    void tick() {
        if (this.searchField != null) {
            this.searchField.updateCursorCounter();
        }
    }

    /**
     * Consumes keys owned by the picker: ESC closes it, and everything else
     * is routed into the search field while that is focused.
     */
    boolean handleKeyTyped(char typedChar, int keyCode) {
        if (!this.targetOpen) {
            return false;
        }
        if (keyCode == Keyboard.KEY_ESCAPE) {
            setOpen(false);
            return true;
        }
        return this.searchField != null
                && this.searchField.textboxKeyTyped(typedChar, keyCode);
    }

    boolean isInsideButton(int mouseX, int mouseY,
                           int screenWidth, int screenHeight) {
        int left = buttonLeft(screenWidth);
        int top = buttonTop(screenHeight);
        return mouseX >= left && mouseX < left + BUTTON_SIZE
                && mouseY >= top && mouseY < top + BUTTON_SIZE;
    }

    boolean isInsidePanel(int mouseX, int mouseY,
                          int screenWidth, int screenHeight) {
        if (!this.targetOpen) {
            return false;
        }
        Layout layout = buildLayout(screenWidth, screenHeight);
        return mouseX >= layout.left && mouseX < layout.left + panelWidth()
                && mouseY >= layout.top
                && mouseY < layout.top + layout.height;
    }

    /** Focus handling for the search field; call for clicks in the panel. */
    void mouseClicked(int mouseX, int mouseY, int button,
                      int screenWidth, int screenHeight) {
        if (this.searchField != null && this.targetOpen) {
            positionSearchField(buildLayout(screenWidth, screenHeight));
            this.searchField.mouseClicked(mouseX, mouseY, button);
        }
    }

    /** The emote cell under the mouse while the picker is open, else null. */
    ChatEmoji emojiAt(int mouseX, int mouseY,
                      int screenWidth, int screenHeight) {
        if (!this.targetOpen) {
            return null;
        }
        Layout layout = buildLayout(screenWidth, screenHeight);
        for (Cell cell : layout.cells) {
            if (mouseX >= cell.x && mouseX < cell.x + CELL_SIZE
                    && mouseY >= cell.y && mouseY < cell.y + CELL_SIZE) {
                return cell.emoji;
            }
        }
        return null;
    }

    /** Right-click favoriting; true when a cell was toggled. */
    boolean toggleFavoriteAt(int mouseX, int mouseY,
                             int screenWidth, int screenHeight) {
        ChatEmoji emoji = emojiAt(mouseX, mouseY,
                screenWidth, screenHeight);
        if (emoji == null) {
            return false;
        }
        ChatEmojiUsageStore.toggleFavorite(emoji);
        return true;
    }

    void draw(Minecraft minecraft, int screenWidth, int screenHeight,
              int mouseX, int mouseY) {
        this.hoveredEmoji = null;
        drawButton(minecraft, screenWidth, screenHeight, mouseX, mouseY);
        drawPanel(minecraft, screenWidth, screenHeight, mouseX, mouseY);
        drawTooltip(minecraft.fontRenderer, mouseX, mouseY, screenWidth);
    }

    private void drawButton(Minecraft minecraft, int screenWidth,
                            int screenHeight, int mouseX, int mouseY) {
        int left = buttonLeft(screenWidth);
        int top = buttonTop(screenHeight);
        boolean hovered = isInsideButton(mouseX, mouseY,
                screenWidth, screenHeight);
        Gui.drawRect(left, top, left + BUTTON_SIZE, top + BUTTON_SIZE,
                hovered || this.targetOpen
                        ? LostTalesChatVisualStyle.SURFACE_HOVER
                        : LostTalesChatVisualStyle.SURFACE);
        ChatEmojiRenderer.draw(minecraft, ChatEmoji.SMILE,
                left + 1, top + 1, ChatEmoji.SPRITE_SIZE, 255);
    }

    private void drawPanel(Minecraft minecraft, int screenWidth,
                           int screenHeight, int mouseX, int mouseY) {
        float progress = openProgress();
        if (progress <= 0.0F) {
            return;
        }
        FontRenderer font = minecraft.fontRenderer;
        ensureSearchField(font);
        Layout layout = buildLayout(screenWidth, screenHeight);
        positionSearchField(layout);
        int slide = Math.round((1.0F - progress) * 5.0F);
        int top = layout.top + slide;
        int backgroundAlpha = Math.max(0, Math.min(255,
                Math.round(230.0F * progress)));
        Gui.drawRect(layout.left, top, layout.left + panelWidth(),
                top + layout.height, (backgroundAlpha << 24)
                        | LostTalesChatVisualStyle.SURFACE_RGB);

        int textAlpha = Math.max(4, Math.min(255,
                Math.round(255.0F * progress)));
        drawSearchRow(font, layout, slide, textAlpha);
        for (Label label : layout.labels) {
            LostTalesChatVisualStyle.drawPlain(font, label.text,
                    label.x, label.y + slide,
                    Math.min(textAlpha, 170));
        }
        for (Cell cell : layout.cells) {
            boolean hovered = this.targetOpen && slide == 0
                    && mouseX >= cell.x && mouseX < cell.x + CELL_SIZE
                    && mouseY >= cell.y && mouseY < cell.y + CELL_SIZE;
            if (hovered) {
                this.hoveredEmoji = cell.emoji;
                Gui.drawRect(cell.x, cell.y, cell.x + CELL_SIZE,
                        cell.y + CELL_SIZE, (backgroundAlpha << 24)
                                | LostTalesChatVisualStyle
                                        .SURFACE_HIGHLIGHT_RGB);
            }
            ChatEmojiRenderer.draw(minecraft, cell.emoji,
                    cell.x + 2, cell.y + slide + 2,
                    ChatEmoji.SPRITE_SIZE, textAlpha);
            if (ChatEmojiUsageStore.isFavorite(cell.emoji)) {
                Gui.drawRect(cell.x + CELL_SIZE - 4, cell.y + slide + 2,
                        cell.x + CELL_SIZE - 2, cell.y + slide + 4,
                        (textAlpha << 24) | LostTalesSkyrimUiStyle.rgb(
                                LostTalesSkyrimUiStyle.GOLD));
            }
        }
    }

    private void drawSearchRow(FontRenderer font, Layout layout,
                               int slide, int alpha) {
        Gui.drawRect(layout.left + PADDING, layout.searchY + slide - 1,
                layout.left + panelWidth() - PADDING,
                layout.searchY + slide + SEARCH_HEIGHT - 3,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                        Math.min(alpha, 120)));
        if (this.searchField == null) {
            return;
        }
        if (this.searchField.getText().length() == 0
                && !this.searchField.isFocused()) {
            LostTalesChatVisualStyle.drawPlain(font,
                    StatCollector.translateToLocal(
                            "gui.losttales.chat.emotes.search"),
                    layout.left + PADDING + 3, layout.searchY + slide,
                    Math.min(alpha, 140));
        } else {
            this.searchField.drawTextBox();
        }
    }

    private void drawTooltip(FontRenderer font, int mouseX, int mouseY,
                             int screenWidth) {
        ChatEmoji emoji = this.hoveredEmoji;
        if (emoji == null) {
            return;
        }
        String label = emoji.getShortcode();
        int width = font.getStringWidth(label) + 6;
        int x = Math.max(2, Math.min(screenWidth - width - 2,
                mouseX - width / 2));
        int y = mouseY - 14;
        Gui.drawRect(x, y, x + width, y + 11,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB, 0xE6));
        LostTalesChatVisualStyle.drawPlain(font, label, x + 3, y + 2, 255);
    }

    private void ensureSearchField(FontRenderer font) {
        if (this.searchField == null && font != null) {
            this.searchField = new GuiTextField(font, 0, 0, 10,
                    SEARCH_HEIGHT);
            this.searchField.setMaxStringLength(16);
            this.searchField.setEnableBackgroundDrawing(false);
            this.searchField.setTextColor(LostTalesChatVisualStyle.IVORY);
        }
    }

    private void positionSearchField(Layout layout) {
        if (this.searchField != null) {
            this.searchField.xPosition = layout.left + PADDING + 3;
            this.searchField.yPosition = layout.searchY;
            this.searchField.width = panelWidth() - PADDING * 2 - 6;
            this.searchField.height = SEARCH_HEIGHT - 3;
        }
    }

    private String searchQuery() {
        return this.searchField == null ? ""
                : this.searchField.getText().trim()
                        .toLowerCase(Locale.ROOT);
    }

    /**
     * Sections and resting cell positions for the current search and
     * preference state. Rebuilt on demand; the emote count is small enough
     * that this costs nothing measurable per frame.
     */
    private Layout buildLayout(int screenWidth, int screenHeight) {
        Layout layout = new Layout();
        String query = searchQuery();
        List<SectionData> sections = new ArrayList<SectionData>();
        if (query.length() > 0) {
            List<ChatEmoji> filtered = new ArrayList<ChatEmoji>();
            for (ChatEmoji emoji : ChatEmoji.values()) {
                if (emoji.getName().contains(query)) {
                    filtered.add(emoji);
                }
            }
            sections.add(new SectionData(null, filtered));
        } else {
            List<ChatEmoji> favorites = ChatEmojiUsageStore.getFavorites();
            if (!favorites.isEmpty()) {
                sections.add(new SectionData(
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.emotes.favorites"),
                        favorites));
            }
            List<ChatEmoji> frequent =
                    ChatEmojiUsageStore.getFrequentlyUsed(FREQUENT_LIMIT);
            if (!frequent.isEmpty()) {
                sections.add(new SectionData(
                        StatCollector.translateToLocal(
                                "gui.losttales.chat.emotes.frequent"),
                        frequent));
            }
            List<ChatEmoji> all = new ArrayList<ChatEmoji>();
            for (ChatEmoji emoji : ChatEmoji.values()) {
                all.add(emoji);
            }
            sections.add(new SectionData(sections.isEmpty() ? null
                    : StatCollector.translateToLocal(
                            "gui.losttales.chat.emotes.all"), all));
        }

        int height = PADDING + SEARCH_HEIGHT;
        for (SectionData section : sections) {
            if (section.label != null) {
                height += LABEL_HEIGHT;
            }
            height += Math.max(1, (section.emojis.size() + COLUMNS - 1)
                    / COLUMNS) * CELL_SIZE;
        }
        height += PADDING;

        layout.height = height;
        layout.left = screenWidth - panelWidth() - BUTTON_MARGIN;
        layout.top = screenHeight - PANEL_BOTTOM_MARGIN - height;
        layout.searchY = layout.top + PADDING + 1;

        int cursorY = layout.top + PADDING + SEARCH_HEIGHT;
        for (SectionData section : sections) {
            if (section.label != null) {
                layout.labels.add(new Label(section.label,
                        layout.left + PADDING + 1, cursorY));
                cursorY += LABEL_HEIGHT;
            }
            for (int index = 0; index < section.emojis.size(); index++) {
                layout.cells.add(new Cell(section.emojis.get(index),
                        layout.left + PADDING
                                + (index % COLUMNS) * CELL_SIZE,
                        cursorY + (index / COLUMNS) * CELL_SIZE));
            }
            cursorY += Math.max(1, (section.emojis.size() + COLUMNS - 1)
                    / COLUMNS) * CELL_SIZE;
        }
        return layout;
    }

    private float openProgress() {
        if (!LostTalesConfig.enableChatAnimations) {
            return this.targetOpen ? 1.0F : 0.0F;
        }
        long duration = Math.max(1,
                LostTalesConfig.chatSelectorAnimationDurationMillis)
                * 1000000L;
        float elapsed = Math.min(1.0F,
                (System.nanoTime() - this.transitionNanos)
                        / (float)duration);
        float eased = LostTalesChatMotion.menuProgress(elapsed);
        return this.targetOpen ? eased : 1.0F - eased;
    }

    static int buttonLeft(int screenWidth) {
        return screenWidth - BUTTON_SIZE - BUTTON_MARGIN;
    }

    private static int buttonTop(int screenHeight) {
        return screenHeight - 14;
    }

    private static int panelWidth() {
        return COLUMNS * CELL_SIZE + PADDING * 2;
    }

    private static final class SectionData {
        final String label;
        final List<ChatEmoji> emojis;

        SectionData(String label, List<ChatEmoji> emojis) {
            this.label = label;
            this.emojis = emojis;
        }
    }

    private static final class Cell {
        final ChatEmoji emoji;
        final int x;
        final int y;

        Cell(ChatEmoji emoji, int x, int y) {
            this.emoji = emoji;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Label {
        final String text;
        final int x;
        final int y;

        Label(String text, int x, int y) {
            this.text = text;
            this.x = x;
            this.y = y;
        }
    }

    private static final class Layout {
        final List<Cell> cells = new ArrayList<Cell>();
        final List<Label> labels = new ArrayList<Label>();
        int left;
        int top;
        int height;
        int searchY;
    }
}
