package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.emoji.ChatEmoji;
import com.ninuna.losttales.chat.ChatMessageIds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;

/**
 * Emoji browser on the shared picker frame: a Favorites section
 * (right-click a cell to toggle), a Frequently Used section, and the full
 * grid, each collapsible, with a shortcode tooltip on hover. Selection
 * returns the emoji; inserting its shortcode is the chat screen's job.
 * The bar's emoji picker inserts; a second one, in the Reactions window,
 * reacts to the message it is aimed at.
 */
final class ChatEmojiPicker extends ChatPickerPanel {
    static final int FREQUENT_LIMIT = 6;
    private static final int CELL_SIZE = 14;
    private static final int COLUMNS = 6;
    /**
     * The message a pick reacts to, or NONE while the picker inserts into
     * the field. Only the Reactions window's picker is ever aimed; closing
     * it ends the aim.
     */
    private long reactionTarget = ChatMessageIds.NONE;

    /** Aims the picker at {@code messageId}: every pick reacts to it. */
    void aimAt(long messageId) {
        this.reactionTarget = messageId;
    }

    /** The message a pick reacts to, or NONE while the picker inserts. */
    long reactionTarget() {
        return isOpen() ? this.reactionTarget : ChatMessageIds.NONE;
    }

    @Override
    public void closed() {
        super.closed();
        this.reactionTarget = ChatMessageIds.NONE;
    }

    /** The Reactions window comes back with the chat aimed where it was. */
    @Override
    public Object sessionState() {
        long target = reactionTarget();
        return target == ChatMessageIds.NONE ? null : Long.valueOf(target);
    }

    @Override
    int naturalColumns() {
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
                   int width, int alpha, boolean hovered) {
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
            LostTalesUiSheet.HEART_FAVORITE.drawWithShadow(x + CELL_SIZE
                    - LostTalesUiSheet.HEART_FAVORITE.getWidth() - 1, y + 1,
                    alpha);
        } else if (hovered) {
            LostTalesUiSheet.HEART.drawWithShadow(x + CELL_SIZE
                    - LostTalesUiSheet.HEART.getWidth() - 1, y + 1, alpha);
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
    LostTalesUiSheet buttonGlyph() {
        return LostTalesUiSheet.EMOJI;
    }

    @Override
    LostTalesUiSheet buttonGlyphLit() {
        return LostTalesUiSheet.EMOJI_HOVER;
    }

    /** Right-click favoriting of the cell under the point; true when one was toggled. */
    boolean toggleFavoriteAt(LostTalesUiHitBox box, double x, double y) {
        Entry entry = entryAt(box, x, y);
        if (entry == null) {
            return false;
        }
        ChatEmojiUsageStore.toggleFavorite((ChatEmoji)entry.value);
        return true;
    }
}
