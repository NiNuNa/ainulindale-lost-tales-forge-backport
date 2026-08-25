package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.emoji.ChatEmoji;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * Emoji browser on the shared picker frame: a Favorites section
 * (right-click a cell to toggle), a Frequently Used section, and the full
 * grid, each collapsible, with a shortcode tooltip on hover. Selection
 * returns the emoji; inserting its shortcode is the chat screen's job.
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
                if (matchesQuery(emoji, query)) {
                    filtered.add(new Entry(emoji));
                }
            }
            sections.add(new Section(null, false, filtered));
            return sections;
        }
        List<ChatEmoji> favorites = ChatEmojiUsageStore.getFavorites();
        if (!favorites.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.emojis.favorites"), true,
                    entries(favorites)));
        }
        List<ChatEmoji> frequent =
                ChatEmojiUsageStore.getFrequentlyUsed(FREQUENT_LIMIT);
        if (!frequent.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.emojis.frequent"), true,
                    entries(frequent)));
        }
        List<ChatEmoji> all = new ArrayList<ChatEmoji>();
        for (ChatEmoji emoji : ChatEmoji.values()) {
            all.add(emoji);
        }
        sections.add(new Section(StatCollector.translateToLocal(
                "gui.losttales.chat.emojis.all"), true, entries(all)));
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
        // Lifted a pixel under the pointer, like the strip's buttons.
        ChatInlineIcons.drawEmoji(minecraft, emoji,
                x + (CELL_SIZE - ChatInlineIcons.CONTENT_SIZE) / 2.0F,
                y + (CELL_SIZE - ChatInlineIcons.CONTENT_SIZE) / 2.0F
                        - (hovered ? 1.0F : 0.0F),
                ChatInlineIcons.CONTENT_SIZE, alpha);
        // The favourite heart in the cell's corner: filled while the
        // emoji is one, plain under the pointer as the control a
        // right-click toggles.
        if (ChatEmojiUsageStore.isFavorite(emoji)) {
            ChatIconSheet.HEART_FAVORITE.drawWithShadow(x + CELL_SIZE
                    - ChatIconSheet.HEART_FAVORITE.getWidth() - 1, y + 1,
                    alpha);
        } else if (hovered) {
            ChatIconSheet.HEART.drawWithShadow(x + CELL_SIZE
                    - ChatIconSheet.HEART.getWidth() - 1, y + 1, alpha);
        }
    }

    /** The search reaches an emoji by its canonical name or any alias. */
    private static boolean matchesQuery(ChatEmoji emoji, String query) {
        if (emoji.getName().contains(query)) {
            return true;
        }
        List<String> aliases = emoji.getAliases();
        for (int index = 0; index < aliases.size(); index++) {
            if (aliases.get(index).contains(query)) {
                return true;
            }
        }
        return false;
    }

    /** {@code :flushed: :flushed_face:} — the canonical name, then every alias. */
    @Override
    String tooltip(Entry entry) {
        ChatEmoji emoji = (ChatEmoji)entry.value;
        List<String> aliases = emoji.getAliases();
        if (aliases.isEmpty()) {
            return emoji.getShortcode();
        }
        StringBuilder label = new StringBuilder(emoji.getShortcode());
        for (int index = 0; index < aliases.size(); index++) {
            label.append(" :").append(aliases.get(index)).append(':');
        }
        return label.toString();
    }

    @Override
    String insertionText(Entry entry) {
        return ((ChatEmoji)entry.value).getShortcode();
    }

    @Override
    void drawButtonIcon(Minecraft minecraft, int left, int top,
                        boolean lifted) {
        ChatIconSheet icon = lifted
                ? ChatIconSheet.EMOJI_HOVER : ChatIconSheet.EMOJI;
        icon.drawWithShadow(left + (BUTTON_SIZE - icon.getWidth()) / 2,
                top + (BUTTON_SIZE - icon.getHeight()) / 2, 255);
    }

    /** The emoji cell under the mouse while the picker is open, else null. */
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
