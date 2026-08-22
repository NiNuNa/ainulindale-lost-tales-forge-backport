package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.StatCollector;

/**
 * Emote browser on the shared picker frame: a Favorites section
 * (right-click a cell to toggle), a Frequently Used section, and the full
 * grid, each collapsible, with a shortcode tooltip on hover. Selection
 * returns the emote; inserting its shortcode is the chat screen's job.
 */
final class ChatEmojiPicker extends ChatPickerPanel {
    static final int FREQUENT_LIMIT = 6;
    private static final int CELL_SIZE = 14;
    private static final int COLUMNS = 6;

    @Override
    int columns() {
        return COLUMNS;
    }

    @Override
    int cellWidth() {
        return CELL_SIZE;
    }

    @Override
    int cellHeight() {
        return CELL_SIZE;
    }

    @Override
    List<Section> buildSections(String query) {
        List<Section> sections = new ArrayList<Section>();
        if (query.length() > 0) {
            List<Entry> filtered = new ArrayList<Entry>();
            for (ChatEmoji emoji : ChatEmoji.values()) {
                if (emoji.getName().contains(query)) {
                    filtered.add(new Entry(emoji));
                }
            }
            sections.add(new Section(null, false, filtered));
            return sections;
        }
        List<ChatEmoji> favorites = ChatEmojiUsageStore.getFavorites();
        if (!favorites.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.emotes.favorites"), true,
                    entries(favorites)));
        }
        List<ChatEmoji> frequent =
                ChatEmojiUsageStore.getFrequentlyUsed(FREQUENT_LIMIT);
        if (!frequent.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.emotes.frequent"), true,
                    entries(frequent)));
        }
        List<ChatEmoji> all = new ArrayList<ChatEmoji>();
        for (ChatEmoji emoji : ChatEmoji.values()) {
            all.add(emoji);
        }
        sections.add(new Section(StatCollector.translateToLocal(
                "gui.losttales.chat.emotes.all"), true, entries(all)));
        return sections;
    }

    private static List<Entry> entries(List<ChatEmoji> emojis) {
        List<Entry> entries = new ArrayList<Entry>(emojis.size());
        for (ChatEmoji emoji : emojis) {
            entries.add(new Entry(emoji));
        }
        return entries;
    }

    @Override
    void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                   int alpha, boolean hovered) {
        ChatEmoji emoji = (ChatEmoji)entry.value;
        ChatInlineIcons.drawEmoji(minecraft, emoji,
                x + (CELL_SIZE - ChatInlineIcons.CONTENT_SIZE) / 2.0F,
                y + (CELL_SIZE - ChatInlineIcons.CONTENT_SIZE) / 2.0F,
                ChatInlineIcons.CONTENT_SIZE, alpha);
        if (ChatEmojiUsageStore.isFavorite(emoji)) {
            Gui.drawRect(x + CELL_SIZE - 4, y + 2,
                    x + CELL_SIZE - 2, y + 4,
                    (alpha << 24) | LostTalesSkyrimUiStyle.rgb(
                            LostTalesSkyrimUiStyle.GOLD));
        }
    }

    @Override
    String tooltip(Entry entry) {
        return ((ChatEmoji)entry.value).getShortcode();
    }

    @Override
    String insertionText(Entry entry) {
        return ((ChatEmoji)entry.value).getShortcode();
    }

    @Override
    void drawButtonIcon(Minecraft minecraft, int left, int top) {
        ChatInlineIcons.drawEmojiButton(minecraft, ChatEmoji.SMILE, left, top,
                BUTTON_SIZE);
    }

    /** The emote cell under the mouse while the picker is open, else null. */
    ChatEmoji emojiAt(int mouseX, int mouseY,
                      int screenWidth, int screenHeight) {
        Entry entry = entryAt(mouseX, mouseY, screenWidth, screenHeight);
        return entry == null ? null : (ChatEmoji)entry.value;
    }

    /** Right-click favoriting; true when a cell was toggled. */
    boolean toggleFavoriteAt(int mouseX, int mouseY,
                             int screenWidth, int screenHeight) {
        ChatEmoji emoji = emojiAt(mouseX, mouseY, screenWidth, screenHeight);
        if (emoji == null) {
            return false;
        }
        ChatEmojiUsageStore.toggleFavorite(emoji);
        return true;
    }
}
