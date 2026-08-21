package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * Map-marker browser on the shared picker frame: a search field and every
 * marker the map would show, listed by category (points of interest,
 * waystones, plain markers) in collapsible sections, each row an icon and
 * the name. Choosing a row inserts its {@code [m:Name]} token; the server
 * re-checks the marker and the sender's right to see it on send.
 */
final class ChatMapMarkerPicker extends ChatPickerPanel {
    private static final int ROW_WIDTH = 116;
    private static final int ROW_HEIGHT = 12;
    /** Marker lists are refreshed at most this often while open. */
    private static final long REFRESH_INTERVAL_NANOS = 500L * 1000000L;
    private static final String BUTTON_ICON =
            LostTalesCompassMarkerIcon.POINT_OF_INTEREST.name();

    private List<ChatShareCandidates.MarkerEntry> markers =
            new ArrayList<ChatShareCandidates.MarkerEntry>();
    private long markersBuiltNanos;

    void refresh() {
        if (!isOpen()) {
            return;
        }
        long now = System.nanoTime();
        if (this.markersBuiltNanos != 0L
                && now - this.markersBuiltNanos < REFRESH_INTERVAL_NANOS) {
            return;
        }
        this.markersBuiltNanos = now;
        this.markers = ChatShareCandidates.markers();
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
        Map<String, List<Entry>> byCategory =
                new LinkedHashMap<String, List<Entry>>();
        String lowered = query.toLowerCase(Locale.ROOT);
        for (ChatShareCandidates.MarkerEntry marker : this.markers) {
            if (lowered.length() > 0 && !ChatShareTokenParser.normalizeName(
                    marker.name).contains(lowered)) {
                continue;
            }
            String category = marker.marker.getCategoryName();
            if (category == null || category.trim().length() == 0) {
                category = StatCollector.translateToLocal(
                        "gui.losttales.chat.marker.category");
            }
            List<Entry> entries = byCategory.get(category);
            if (entries == null) {
                entries = new ArrayList<Entry>();
                byCategory.put(category, entries);
            }
            entries.add(new Entry(marker));
        }
        List<Section> sections = new ArrayList<Section>(byCategory.size());
        for (Map.Entry<String, List<Entry>> category
                : byCategory.entrySet()) {
            sections.add(new Section(category.getKey(), true,
                    category.getValue()));
        }
        if (sections.isEmpty()) {
            sections.add(new Section(null, false, null));
        }
        return sections;
    }

    @Override
    void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                   int alpha, boolean hovered) {
        ChatShareCandidates.MarkerEntry marker =
                (ChatShareCandidates.MarkerEntry)entry.value;
        LostTalesMapMarkerData data = marker.marker;
        ChatMapMarkerRenderer.drawShadow(minecraft, data.getIconName(),
                x + 1 + LostTalesChatVisualStyle.SHADOW_OFFSET,
                y + 1 + LostTalesChatVisualStyle.SHADOW_OFFSET,
                ChatMapMarkerRenderer.ICON_SIZE,
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(alpha));
        ChatMapMarkerRenderer.draw(minecraft, data.getIconName(),
                data.getColorName(), x + 1, y + 1,
                ChatMapMarkerRenderer.ICON_SIZE, alpha);
        String label = LostTalesSkyrimUiStyle.trimToWidth(
                minecraft.fontRenderer, marker.label(),
                ROW_WIDTH - (int)ChatMapMarkerRenderer.ICON_SIZE - 6);
        LostTalesChatVisualStyle.drawPlain(minecraft.fontRenderer, label,
                x + (int)ChatMapMarkerRenderer.ICON_SIZE + 4, y + 2,
                hovered ? alpha : Math.min(alpha, 220));
    }

    @Override
    String tooltip(Entry entry) {
        ChatShareCandidates.MarkerEntry marker =
                (ChatShareCandidates.MarkerEntry)entry.value;
        return marker.label() + "  X " + Math.round(marker.marker.getX())
                + "  Z " + Math.round(marker.marker.getZ());
    }

    @Override
    String insertionText(Entry entry) {
        return ((ChatShareCandidates.MarkerEntry)entry.value).token() + " ";
    }

    @Override
    void drawButtonIcon(Minecraft minecraft, int left, int top) {
        ChatMapMarkerRenderer.drawShadow(minecraft, BUTTON_ICON,
                left + 1 + LostTalesChatVisualStyle.SHADOW_OFFSET,
                top + 1 + LostTalesChatVisualStyle.SHADOW_OFFSET,
                ChatMapMarkerRenderer.ICON_SIZE,
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(255));
        ChatMapMarkerRenderer.draw(minecraft, BUTTON_ICON, "",
                left + 1, top + 1, ChatMapMarkerRenderer.ICON_SIZE, 255);
    }
}
