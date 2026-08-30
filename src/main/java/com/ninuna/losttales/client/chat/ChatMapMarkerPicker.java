package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.share.ChatShareTokenParser;
import com.ninuna.losttales.client.mapmarker.LostTalesClientMapMarkerUsageStore;
import com.ninuna.losttales.client.mapmarker.LostTalesMapMarkerData;
import com.ninuna.losttales.gui.hud.compass.marker.LostTalesCompassMarkerIcon;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * Map-marker browser on the shared picker frame: a search field and every
 * marker the map would show, each row an icon and the name. Like the
 * emoji picker it opens with a Favourites section (right-click a row to
 * toggle) and a Recently Travelled To section, then one collapsible
 * section per marker type — the glyph the marker is drawn with, which is
 * the taxonomy the atlas already defines, so a settlement, a port and a
 * waypoint each sit under their own heading instead of one generic
 * "Point of Interest" list. Sections with nothing in them are not shown.
 * Choosing a row inserts its {@code [m:Name]} token; the server
 * re-checks the marker and the sender's right to see it on send.
 */
final class ChatMapMarkerPicker extends ChatPickerPanel {
    private static final int ROW_WIDTH = 116;
    private static final int ROW_HEIGHT = 12;
    /** Marker lists are refreshed at most this often while open. */
    private static final long REFRESH_INTERVAL_NANOS = 500L * 1000000L;
    private static final String BUTTON_ICON =
            LostTalesCompassMarkerIcon.POINT_OF_INTEREST.name();
    private static final String TYPE_KEY_PREFIX =
            "gui.losttales.chat.marker.type.";

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
        String lowered = query.toLowerCase(Locale.ROOT);
        if (lowered.length() > 0) {
            List<Entry> filtered = new ArrayList<Entry>();
            for (ChatShareCandidates.MarkerEntry marker : this.markers) {
                if (ChatShareTokenParser.normalizeName(marker.name)
                        .contains(lowered)) {
                    filtered.add(new Entry(marker));
                }
            }
            List<Section> results = new ArrayList<Section>(1);
            results.add(new Section(null, false, filtered));
            return results;
        }
        Map<String, ChatShareCandidates.MarkerEntry> byId =
                new HashMap<String, ChatShareCandidates.MarkerEntry>();
        // Marker type sections in atlas order, so the list is stable
        // from one opening to the next.
        TreeMap<Integer, List<Entry>> byType =
                new TreeMap<Integer, List<Entry>>();
        for (ChatShareCandidates.MarkerEntry marker : this.markers) {
            byId.put(marker.marker.getId(), marker);
            LostTalesCompassMarkerIcon type = typeOf(marker.marker);
            List<Entry> entries = byType.get(Integer.valueOf(type.ordinal()));
            if (entries == null) {
                entries = new ArrayList<Entry>();
                byType.put(Integer.valueOf(type.ordinal()), entries);
            }
            entries.add(new Entry(marker));
        }
        List<Section> sections = new ArrayList<Section>(byType.size() + 2);
        addIdSection(sections, "gui.losttales.chat.marker.favorites",
                LostTalesClientMapMarkerUsageStore.getFavorites(), byId);
        addIdSection(sections, "gui.losttales.chat.marker.recent",
                LostTalesClientMapMarkerUsageStore.getRecentlyTravelled(),
                byId);
        for (Map.Entry<Integer, List<Entry>> type : byType.entrySet()) {
            sections.add(new Section(typeLabel(
                    LostTalesCompassMarkerIcon.values()[type.getKey()
                            .intValue()]), true, type.getValue()));
        }
        if (sections.isEmpty()) {
            sections.add(new Section(null, false, null));
        }
        return sections;
    }

    /** A section of the listed ids, in their stored order, if any exist. */
    private static void addIdSection(
            List<Section> sections, String labelKey, List<String> ids,
            Map<String, ChatShareCandidates.MarkerEntry> byId) {
        List<Entry> entries = new ArrayList<Entry>(ids.size());
        for (int index = 0; index < ids.size(); index++) {
            ChatShareCandidates.MarkerEntry marker = byId.get(ids.get(index));
            if (marker != null) {
                entries.add(new Entry(marker));
            }
        }
        if (!entries.isEmpty()) {
            sections.add(new Section(
                    StatCollector.translateToLocal(labelKey), true, entries));
        }
    }

    /**
     * The marker's type: the atlas glyph it is drawn with, with alias
     * constants folded onto the glyph they share a cell with and the eight
     * compass directions folded onto one.
     */
    static LostTalesCompassMarkerIcon typeOf(LostTalesMapMarkerData marker) {
        LostTalesCompassMarkerIcon icon =
                LostTalesCompassMarkerIcon.fromName(marker.getIconName());
        if (isDirection(icon)) {
            return LostTalesCompassMarkerIcon.N;
        }
        for (LostTalesCompassMarkerIcon candidate
                : LostTalesCompassMarkerIcon.values()) {
            if (candidate.getU() == icon.getU()
                    && candidate.getV() == icon.getV()) {
                return candidate;
            }
        }
        return icon;
    }

    private static boolean isDirection(LostTalesCompassMarkerIcon icon) {
        return icon.ordinal() >= LostTalesCompassMarkerIcon.N.ordinal()
                && icon.ordinal() <= LostTalesCompassMarkerIcon.NW.ordinal();
    }

    private static String typeLabel(LostTalesCompassMarkerIcon type) {
        String name = isDirection(type) ? "direction"
                : type.name().toLowerCase(Locale.ROOT);
        String key = TYPE_KEY_PREFIX + name;
        String translated = StatCollector.translateToLocal(key);
        if (!key.equals(translated)) {
            return translated;
        }
        String plain = name.replace('_', ' ');
        return Character.toUpperCase(plain.charAt(0)) + plain.substring(1);
    }

    @Override
    void drawEntry(Minecraft minecraft, Entry entry, int x, int y,
                   int alpha, boolean hovered) {
        ChatShareCandidates.MarkerEntry marker =
                (ChatShareCandidates.MarkerEntry)entry.value;
        LostTalesMapMarkerData data = marker.marker;
        ChatInlineIcons.drawMarker(minecraft, data.getIconName(),
                ChatInlineIcons.markerRgb(data.getColorName()),
                ChatInlineIcons.boxLeft(x + 1, ChatInlineIcons.SLOT_WIDTH),
                ChatInlineIcons.boxTop(y + 2, ChatInlineIcons.SLOT_WIDTH),
                ChatInlineIcons.CONTENT_SIZE, alpha);
        int labelWidth = ROW_WIDTH - ChatInlineIcons.SLOT_WIDTH - 10;
        String label = LostTalesSkyrimUiStyle.trimToWidth(
                minecraft.fontRenderer, marker.label(), labelWidth);
        LostTalesChatVisualStyle.drawPlain(minecraft.fontRenderer, label,
                x + 1 + ChatInlineIcons.SLOT_WIDTH + 4, y + 2,
                hovered ? alpha : Math.min(alpha, 220));
        // The favourite heart at the row's end, exactly as the emoji
        // picker marks its cells: filled while the marker is one, plain
        // under the pointer as the control a right-click toggles.
        if (LostTalesClientMapMarkerUsageStore.isFavorite(data.getId())) {
            ChatIconSheet.HEART_FAVORITE.drawWithShadow(x + ROW_WIDTH
                    - ChatIconSheet.HEART_FAVORITE.getWidth() - 2, y + 3,
                    alpha);
        } else if (hovered) {
            ChatIconSheet.HEART.drawWithShadow(x + ROW_WIDTH
                    - ChatIconSheet.HEART.getWidth() - 2, y + 3, alpha);
        }
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

    /** Right-click favouriting; true when a row was toggled. */
    boolean toggleFavoriteAt(int mouseX, int mouseY,
                             int screenWidth, int screenHeight) {
        Entry entry = entryAt(mouseX, mouseY, screenWidth, screenHeight);
        if (entry == null) {
            return false;
        }
        LostTalesClientMapMarkerUsageStore.toggleFavorite(
                ((ChatShareCandidates.MarkerEntry)entry.value).marker.getId());
        return true;
    }

    @Override
    void drawButtonIcon(Minecraft minecraft, int left, int top,
                        float lit) {
        ChatInlineIcons.drawMarkerButton(minecraft, BUTTON_ICON,
                LostTalesChatVisualStyle.IVORY, left, top, BUTTON_SIZE);
    }
}
