package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * Inventory browser on the shared picker frame: a search field and the
 * player's own stacks in three collapsible sections — Hotbar, Inventory,
 * Armour — with the display name on hover. Choosing a stack inserts its
 * {@code [i:Name]} token; the server still re-reads the slot on send.
 */
final class ChatItemPicker extends ChatPickerPanel {
    private static final int CELL_SIZE = 14;
    private static final int COLUMNS = 6;
    private static final ItemStack BUTTON_ICON = new ItemStack(Items.iron_sword);
    /** Inventory snapshots are refreshed at most this often while open. */
    private static final long REFRESH_INTERVAL_NANOS = 250L * 1000000L;

    private List<ChatShareCandidates.ItemEntry> items =
            new ArrayList<ChatShareCandidates.ItemEntry>();
    private long itemsBuiltNanos;

    /** Refreshes the slot snapshot on an interval; cheap and allocation-light. */
    void refresh(net.minecraft.entity.player.EntityPlayer player) {
        if (!isOpen()) {
            return;
        }
        long now = System.nanoTime();
        if (this.itemsBuiltNanos != 0L
                && now - this.itemsBuiltNanos < REFRESH_INTERVAL_NANOS) {
            return;
        }
        this.itemsBuiltNanos = now;
        List<ChatShareCandidates.ItemEntry> built =
                ChatShareCandidates.items(player);
        if (!ChatShareCandidates.sameItems(built, this.items)) {
            this.items = built;
        }
    }

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
        List<Entry> hotbar = new ArrayList<Entry>();
        List<Entry> inventory = new ArrayList<Entry>();
        List<Entry> armor = new ArrayList<Entry>();
        for (ChatShareCandidates.ItemEntry item : this.items) {
            if (query.length() > 0 && !ChatShareTokenParser.normalizeName(
                    item.name).contains(query.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Entry entry = new Entry(item);
            if (item.slot <= ChatShareCandidates.HOTBAR_END) {
                hotbar.add(entry);
            } else if (item.slot >= ChatShareCandidates.ARMOR_START) {
                armor.add(entry);
            } else {
                inventory.add(entry);
            }
        }
        // An empty section is not shown; a fully empty inventory leaves
        // the panel with its search row and one blank result row.
        List<Section> sections = new ArrayList<Section>(3);
        if (!hotbar.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.share.hotbar"), true, hotbar));
        }
        if (!inventory.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.share.inventory"), true, inventory));
        }
        if (!armor.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.share.armor"), true, armor));
        }
        if (sections.isEmpty()) {
            sections.add(new Section(null, false, null));
        }
        return sections;
    }

    @Override
    void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                   int alpha, boolean hovered) {
        ChatShareCandidates.ItemEntry item =
                (ChatShareCandidates.ItemEntry)entry.value;
        ChatInlineIcons.drawItem(minecraft, item.stack,
                x + (CELL_SIZE - ChatInlineIcons.CONTENT_SIZE) / 2.0F,
                y + (CELL_SIZE - ChatInlineIcons.CONTENT_SIZE) / 2.0F,
                ChatInlineIcons.CONTENT_SIZE, alpha);
    }

    @Override
    String tooltip(Entry entry) {
        return ((ChatShareCandidates.ItemEntry)entry.value).label();
    }

    @Override
    String insertionText(Entry entry) {
        return ((ChatShareCandidates.ItemEntry)entry.value).token() + " ";
    }

    @Override
    void drawButtonIcon(Minecraft minecraft, int left, int top,
                        boolean lifted) {
        ChatInlineIcons.drawItemButton(minecraft, BUTTON_ICON, left, top,
                BUTTON_SIZE);
    }
}
