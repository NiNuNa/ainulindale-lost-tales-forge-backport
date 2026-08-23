package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * Quest browser on the shared picker frame, the fourth toolbar picker
 * beside emotes, items, and map markers. The frame gives it the button,
 * open/close easing, search field, section folding and pointer regions;
 * quest entries are not shared yet, so it shows a single empty-state
 * section. When quests become shareable they become {@link Entry} values
 * here, drawn by {@link #drawEntry} and inserted by {@link #insertionText},
 * with nothing else to redesign.
 */
final class ChatQuestPicker extends ChatPickerPanel {
    private static final int ROW_WIDTH = 116;
    private static final int ROW_HEIGHT = 12;

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
        return Collections.singletonList(new Section(
                StatCollector.translateToLocal("gui.losttales.chat.quests.empty"),
                false, null));
    }

    @Override
    void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                   int alpha, boolean hovered) {
        // No quest entries exist yet.
    }

    @Override
    String tooltip(Entry entry) {
        return null;
    }

    @Override
    String insertionText(Entry entry) {
        return "";
    }

    @Override
    void drawButtonIcon(Minecraft minecraft, int left, int top,
                        boolean lifted) {
        // The quest map marker, the same glyph quests carry on the map.
        ChatInlineIcons.drawMarkerButton(minecraft,
                LostTalesCompassMarkerIcon.QUEST.name(),
                LostTalesChatVisualStyle.IVORY, left, top, BUTTON_SIZE);
    }
}
