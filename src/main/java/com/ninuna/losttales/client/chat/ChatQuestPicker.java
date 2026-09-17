package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * Quest browser on the shared picker frame, the fourth toolbar picker
 * beside emojis, items, and map markers. The frame gives it the button,
 * open/close easing, search field, section folding and pointer regions;
 * active quests are grouped by the same natural categories as the journal.
 */
final class ChatQuestPicker extends ChatPickerPanel {
    private static final int ROW_WIDTH = 116;
    private static final int ROW_HEIGHT = 12;
    private static final long REFRESH_INTERVAL_NANOS = 500L * 1000000L;
    private List<ChatShareCandidates.QuestEntry> quests =
            new ArrayList<ChatShareCandidates.QuestEntry>();
    private long questsBuiltNanos;

    void refresh() {
        if (!isOpen()) return;
        long now = System.nanoTime();
        if (this.questsBuiltNanos != 0L
                && now - this.questsBuiltNanos < REFRESH_INTERVAL_NANOS) return;
        this.questsBuiltNanos = now;
        this.quests = ChatShareCandidates.quests();
    }

    @Override
    int columns() {
        return 1;
    }

    @Override
    int cellWidth() {
        return ROW_WIDTH;
    }

    @Override
    int cellHeight() {
        return ROW_HEIGHT;
    }

    @Override
    List<Section> buildSections(String query) {
        String lowered = query.toLowerCase(Locale.ROOT);
        Map<String, List<Entry>> grouped = new LinkedHashMap<String, List<Entry>>();
        for (ChatShareCandidates.QuestEntry candidate : this.quests) {
            if (lowered.length() > 0 && !ChatShareTokenParser.normalizeName(
                    candidate.name).contains(lowered)) continue;
            String category = candidate.quest.getCategory();
            List<Entry> entries = grouped.get(category);
            if (entries == null) {
                entries = new ArrayList<Entry>();
                grouped.put(category, entries);
            }
            entries.add(new Entry(candidate));
        }
        List<Section> sections = new ArrayList<Section>();
        for (Map.Entry<String, List<Entry>> section : grouped.entrySet()) {
            sections.add(new Section(section.getKey(), true, section.getValue()));
        }
        if (sections.isEmpty()) {
            sections.add(new Section(StatCollector.translateToLocal(
                    "gui.losttales.chat.quests.empty"), false, null));
        }
        return sections;
    }

    @Override
    void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                   int alpha, boolean hovered) {
        ChatShareCandidates.QuestEntry quest =
                (ChatShareCandidates.QuestEntry)entry.value;
        LostTalesUiSheet.QUEST.drawWithShadow(x + 1, y + 1, alpha);
        String label = LostTalesSkyrimUiStyle.trimToWidth(
                minecraft.fontRenderer, quest.label(), ROW_WIDTH - 18);
        LostTalesChatVisualStyle.drawPlain(minecraft.fontRenderer, label,
                x + 15, y + 2, hovered ? alpha : Math.min(alpha, 220));
    }

    @Override
    String tooltip(Entry entry) {
        ChatShareCandidates.QuestEntry quest =
                (ChatShareCandidates.QuestEntry)entry.value;
        return quest.label() + " - " + quest.quest.getCategory();
    }

    @Override
    String insertionText(Entry entry) {
        return ((ChatShareCandidates.QuestEntry)entry.value).token() + " ";
    }

    @Override
    LostTalesUiSheet buttonGlyph() {
        return LostTalesUiSheet.QUEST;
    }

    @Override
    LostTalesUiSheet buttonGlyphLit() {
        return LostTalesUiSheet.QUEST_HOVER;
    }
}
