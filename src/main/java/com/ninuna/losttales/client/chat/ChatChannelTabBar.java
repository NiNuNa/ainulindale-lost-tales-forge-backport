package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Folder-divider style tabs of one chat window, like the tabs on a
 * folder. Neighbouring tabs overlap by a few pixels like file dividers;
 * the selected tab is drawn last, lifted, and brighter, and its body runs
 * into the box so the two read as one sheet. Each tab eases its own
 * prominence toward its target from wherever it currently is, so rapid
 * switching never waits on a previous transition. A tab with unread
 * messages carries textual counters after its name: {@code (p)} pings in
 * salmon, then {@code (x)} other unread lines in honey; a muted tab is
 * drawn dim and italic.
 *
 * <p>The selected tab always carries its controls — a settings cog and
 * a close cross — and the row ends with the
 * window's lock and, while channels are closed, a restore control. The
 * row is the window's title strip: the space right of the controls is
 * the grip that moves the window. Geometry is computed once per change
 * of inputs and reused by drawing and hit testing, so a frame allocates
 * nothing.</p>
 *
 * <p>The row is positioned by its window: {@code rowBottom} and
 * {@code offsetX} come from the frame that drew the window, so the row
 * enters, settles and fades with the same shared motion state as the
 * history it stands on.</p>
 */
final class ChatChannelTabBar {
    /** Body height of a resting tab; the selected tab rises above it. */
    static final int HEIGHT = 11;
    /** Pixels the selected tab rises out of the row. */
    static final int LIFT = 2;
    /** Full height of the row: a resting tab plus the lift. */
    static final int ROW_HEIGHT = HEIGHT + LIFT;
    /** Horizontal overlap between neighbouring dividers. */
    private static final int OVERLAP = 3;
    private static final int PADDING_X = 6;
    /** A dragged tab's ghost never grows past this. */
    private static final int GHOST_LABEL_WIDTH = 120;
    private static final int MIN_LABEL_WIDTH = 14;
    /** Gap between the label and a counter, and between the counters. */
    private static final int COUNTER_GAP = 3;
    /** Hit square of a control inside the selected tab. */
    static final int CONTROL_SIZE = 7;
    private static final int CONTROL_GAP = 2;
    /** Hit square of the lock and restore controls after the row. */
    static final int END_CONTROL_SIZE = 9;
    private static final int END_CONTROL_GAP = 3;
    /** Minimum grip width a row keeps for dragging the window. */
    static final int MIN_GRIP_WIDTH = 12;
    /** Easing state is kept for this many tabs before the oldest go. */
    private static final int MAX_EASED_TABS = 32;
    private static final int PING_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int UNREAD_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    private static final int CLOSE_HOVER_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int LOCKED_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);
    private static final int RESTORE_RGB =
            LostTalesColors.rgb(LostTalesColors.MEADOW_GREEN);
    private static final int DROP_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    /** Per-tab easing toward selected (1) or resting (0). */
    private static final class Ease {
        float prominence;
        float fromValue;
        boolean target;
        long startNanos;
    }

    private final Map<ChatTab, Ease> eases = new HashMap<ChatTab, Ease>();
    private boolean initialized;

    private List<Tab> cachedTabs = Collections.emptyList();
    private List<ChatTab> cachedChannels = Collections.emptyList();
    private ChatTab cachedSelected;
    private final Map<ChatTab, String> cachedLabels =
            new HashMap<ChatTab, String>();
    private final Map<ChatTab, Integer> cachedPings =
            new HashMap<ChatTab, Integer>();
    private final Map<ChatTab, Integer> cachedOther =
            new HashMap<ChatTab, Integer>();
    private final Map<ChatTab, Boolean> cachedMuted =
            new HashMap<ChatTab, Boolean>();
    private FontRenderer cachedFont;
    private int cachedLeft = Integer.MIN_VALUE;
    private int cachedRight = Integer.MIN_VALUE;
    private boolean cachedShowClose;
    private boolean cachedShowRestore;
    /** Right edge of the last tab, before the end controls. */
    private int tabsRight;
    private int lockX = -1;
    private int restoreX = -1;
    /** Right edge of the end controls; the grip starts here. */
    private int controlsRight;
    private float alphaScale = 1.0F;

    /** What a point in the row resolves to. */
    enum HitKind { TAB, CLOSE, SETTINGS, LOCK, RESTORE, GRIP }

    static final class Hit {
        final HitKind kind;
        final ChatTab tab;

        Hit(HitKind kind, ChatTab tab) {
            this.kind = kind;
            this.tab = tab;
        }
    }

    /** The per-frame inputs of one window's row, filled by the screen. */
    static final class Row {
        List<ChatTab> tabs = Collections.emptyList();
        ChatTab selected;
        /** Resting left edge of the first tab, screen space. */
        int left;
        /** Right limit the row may not cross, screen space. */
        int right;
        /** Screen y the tab bodies stand on. */
        int rowBottom;
        /** Horizontal motion the whole row is drawn with. */
        int offsetX;
        boolean locked;
        /** Whether a close cross is offered on the selected tab. */
        boolean closable;
        /** Whether the restore control is offered after the row. */
        boolean showRestore;
        /** Tab being dragged out of this row, drawn faint; or null. */
        ChatTab dragging;
        /** Insertion index to indicate during a drag, or -1. */
        int dropIndex = -1;
    }

    /** Advances every tab's easing toward the current selection. */
    private void update(List<ChatTab> tabs, ChatTab selected) {
        long now = System.nanoTime();
        for (ChatTab tab : tabs) {
            boolean wanted = tab.equals(selected);
            Ease ease = this.eases.get(tab);
            if (ease == null || !this.initialized) {
                if (ease == null) {
                    ease = new Ease();
                    this.eases.put(tab, ease);
                }
                ease.target = wanted;
                ease.prominence = wanted ? 1.0F : 0.0F;
                ease.fromValue = ease.prominence;
                ease.startNanos = 0L;
                continue;
            }
            if (ease.target != wanted) {
                ease.target = wanted;
                ease.fromValue = ease.prominence;
                ease.startNanos = now;
            }
            float goal = wanted ? 1.0F : 0.0F;
            if (!LostTalesConfig.enableChatAnimations
                    || ease.startNanos == 0L) {
                ease.prominence = goal;
                continue;
            }
            long duration = Math.max(1,
                    LostTalesConfig.chatSelectorAnimationDurationMillis)
                    * 1000000L;
            float elapsed = Math.min(1.0F,
                    (now - ease.startNanos) / (float)duration);
            float eased = LostTalesChatMotion.menuProgress(elapsed);
            ease.prominence = ease.fromValue
                    + (goal - ease.fromValue) * eased;
        }
        this.initialized = true;
        while (this.eases.size() > MAX_EASED_TABS) {
            Iterator<ChatTab> iterator = this.eases.keySet().iterator();
            iterator.next();
            iterator.remove();
        }
    }

    private float prominence(ChatTab tab) {
        Ease ease = this.eases.get(tab);
        return ease == null ? 0.0F : ease.prominence;
    }

    /** Highest pixel any tab can reach (the selected tab fully lifted). */
    static int rowTop(int rowBottom) {
        return rowBottom - ROW_HEIGHT;
    }

    /** Whether a GUI-space point lies in the row's vertical band. */
    static boolean inRowBand(Row row, int mouseY) {
        return mouseY >= rowTop(row.rowBottom) && mouseY < row.rowBottom;
    }

    /**
     * What lies under a GUI-space point: a tab, one of the selected tab's
     * controls, an end control, the grip, or nothing.
     */
    Hit hitAt(FontRenderer font, Row row, int mouseX, int mouseY) {
        if (!inRowBand(row, mouseY)) {
            return null;
        }
        List<Tab> tabs = layout(font, row);
        int localX = mouseX - row.offsetX;
        if (tabs.isEmpty()) {
            return null;
        }
        // Later tabs overlap earlier ones, so the last hit wins, except
        // the selected tab, which is on top of everything.
        Tab selectedTab = null;
        Tab hit = null;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX >= tab.x && localX < tab.x + tab.width) {
                if (tab.tab.equals(row.selected)) {
                    selectedTab = tab;
                } else {
                    hit = tab;
                }
            }
        }
        if (selectedTab != null) {
            if (selectedTab.closeX >= 0
                    && localX >= selectedTab.closeX - 1
                    && localX < selectedTab.closeX + CONTROL_SIZE + 1) {
                return new Hit(HitKind.CLOSE, selectedTab.tab);
            }
            if (selectedTab.settingsX >= 0
                    && localX >= selectedTab.settingsX - 1
                    && localX < selectedTab.settingsX + CONTROL_SIZE + 1) {
                return new Hit(HitKind.SETTINGS, selectedTab.tab);
            }
            return new Hit(HitKind.TAB, selectedTab.tab);
        }
        if (hit != null) {
            return new Hit(HitKind.TAB, hit.tab);
        }
        if (this.lockX >= 0 && localX >= this.lockX
                && localX < this.lockX + END_CONTROL_SIZE) {
            return new Hit(HitKind.LOCK, null);
        }
        if (this.restoreX >= 0 && localX >= this.restoreX
                && localX < this.restoreX + END_CONTROL_SIZE) {
            return new Hit(HitKind.RESTORE, null);
        }
        if (localX >= this.controlsRight && localX < row.right) {
            return new Hit(HitKind.GRIP, null);
        }
        return null;
    }

    /**
     * Insertion index for a tab dropped at {@code mouseX}: before the
     * first tab whose centre lies right of the pointer, else at the end.
     */
    int dropIndexAt(FontRenderer font, Row row, int mouseX) {
        List<Tab> tabs = layout(font, row);
        int localX = mouseX - row.offsetX;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX < tab.x + tab.width / 2) {
                return index;
            }
        }
        return tabs.size();
    }

    /** Screen x where the row's tabs end, before the end controls. */
    int tabsRight(FontRenderer font, Row row) {
        layout(font, row);
        return row.offsetX + this.tabsRight;
    }

    /**
     * Draws the row. {@code offsetX} and {@code alphaScale} are the
     * window's own motion, so tabs and lines arrive and fade together;
     * the rectangle registered with {@code regions} is the one painted.
     */
    void draw(FontRenderer font, ChatPointerRegions regions, Row row,
              int mouseX, int mouseY, float alphaScale) {
        update(row.tabs, row.selected);
        List<Tab> tabs = layout(font, row);
        if (tabs.isEmpty()) {
            return;
        }
        this.alphaScale = Math.max(0.0F, Math.min(1.0F, alphaScale));
        Hit hovered = hitAt(font, row, mouseX, mouseY);
        int bottom = row.rowBottom;
        // The window's title strip: tabs stand on it and the empty
        // stretch after the controls is the grip.
        Gui.drawRect(row.offsetX + row.left - 2, rowTop(bottom),
                row.offsetX + row.right + 2, bottom,
                LostTalesChatVisualStyle.argb(
                        LostTalesChatVisualStyle.SURFACE_RGB,
                        scaled(0x70)));
        drawGrip(row.offsetX + this.controlsRight, row.offsetX + row.right,
                bottom, hovered != null && hovered.kind == HitKind.GRIP);
        Tab selectedTab = null;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (tab.tab.equals(row.selected)) {
                selectedTab = tab;
                continue;
            }
            drawTab(font, tab, row, bottom, hovered, false);
        }
        if (selectedTab != null) {
            drawTab(font, selectedTab, row, bottom, hovered, true);
        }
        if (row.dropIndex >= 0) {
            drawDropIndicator(tabs, row, bottom);
        }
        int controlTop = bottom - HEIGHT + (HEIGHT - END_CONTROL_SIZE) / 2;
        if (this.lockX >= 0) {
            drawLock(row.offsetX + this.lockX, controlTop, row.locked,
                    hovered != null && hovered.kind == HitKind.LOCK);
        }
        if (this.restoreX >= 0) {
            drawRestore(row.offsetX + this.restoreX, controlTop,
                    hovered != null && hovered.kind == HitKind.RESTORE);
        }
        regions.addScreen(row.offsetX + row.left - 2, rowTop(bottom),
                row.offsetX + row.right + 2, bottom);
    }

    /** A dragged tab following the pointer, above everything else. */
    void drawGhost(FontRenderer font, ChatTab tab, int x, int y) {
        if (font == null || tab == null) {
            return;
        }
        String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                ClientChatChannelState.displayName(tab), GHOST_LABEL_WIDTH);
        int width = font.getStringWidth(label) + PADDING_X * 2;
        int top = y - HEIGHT / 2;
        int surface = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB, 0xD0);
        Gui.drawRect(x + 1, top, x + width - 1, top + 1, surface);
        Gui.drawRect(x, top + 1, x + width, top + HEIGHT, surface);
        Gui.drawRect(x + 1, top + 1, x + width - 1, top + 2,
                LostTalesChatVisualStyle.argb(
                        ClientChatChannelState.displayColor(tab), 0xFF));
        LostTalesChatVisualStyle.drawPlain(font, label, x + PADDING_X,
                top + 3, 230);
    }

    private int scaled(int alpha) {
        int result = Math.round(alpha * this.alphaScale);
        return result < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                ? 0 : result;
    }

    private void drawTab(FontRenderer font, Tab tab, Row row,
                         int rowBottom, Hit hovered, boolean selected) {
        boolean hoveredTab = hovered != null && tab.tab.equals(hovered.tab);
        boolean faint = tab.tab.equals(row.dragging);
        float rise = prominence(tab.tab);
        int lift = Math.round(LIFT * rise);
        int top = rowBottom - HEIGHT - lift;
        // The selected tab joins the history box; resting tabs keep a one
        // pixel seam so they read as sitting behind it.
        int bottom = selected ? rowBottom + 1 : rowBottom;
        int left = row.offsetX + tab.x;
        int right = left + tab.width;
        float dim = faint ? 0.4F : 1.0F;

        int surfaceAlpha = scaled(Math.round(
                (0x90 + (0xE6 - 0x90) * rise) * dim));
        int surfaceRgb = blend(LostTalesChatVisualStyle.SURFACE_RGB,
                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                hoveredTab ? Math.max(0.35F, rise) : rise * 0.55F);
        int surface = (surfaceAlpha << 24) | surfaceRgb;
        // Chamfered top corners: the top row is inset one pixel per side.
        Gui.drawRect(left + 1, top, right - 1, top + 1, surface);
        Gui.drawRect(left, top + 1, right, bottom, surface);
        // Channel accent along the top edge, brighter when forward.
        int accentAlpha = scaled(Math.round(
                (0xA0 + (0xFF - 0xA0) * rise) * dim));
        Gui.drawRect(left + 1, top + 1, right - 1, top + 2,
                (accentAlpha << 24)
                        | ClientChatChannelState.displayColor(tab.tab));
        if (!selected) {
            // Shaded right edge suggests the next divider stacked above.
            Gui.drawRect(right - 1, top + 1, right, bottom,
                    LostTalesChatVisualStyle.argb(
                            LostTalesChatVisualStyle.SHADOW, scaled(0x90)));
        }
        int textAlpha = scaled(Math.round(
                (185 + (255 - 185) * rise) * dim
                        * (tab.muted ? 0.6F : 1.0F)));
        int textX = left + PADDING_X;
        int textY = top + 3;
        LostTalesChatVisualStyle.drawPlain(font,
                tab.muted ? "§o" + tab.label : tab.label,
                textX, textY, textAlpha);
        textX += tab.labelWidth;
        if (tab.pingText.length() > 0) {
            textX += COUNTER_GAP;
            LostTalesChatVisualStyle.drawColored(font, tab.pingText,
                    textX, textY, PING_COUNTER_RGB, textAlpha);
            textX += tab.pingWidth;
        }
        if (tab.otherText.length() > 0) {
            textX += COUNTER_GAP;
            LostTalesChatVisualStyle.drawColored(font, tab.otherText,
                    textX, textY, UNREAD_COUNTER_RGB, textAlpha);
        }
        int controlTop = top + 2 + (HEIGHT - 2 - CONTROL_SIZE) / 2;
        if (tab.settingsX >= 0) {
            drawCog(row.offsetX + tab.settingsX, controlTop,
                    hovered != null && hovered.kind == HitKind.SETTINGS,
                    textAlpha);
        }
        if (tab.closeX >= 0) {
            drawClose(row.offsetX + tab.closeX, controlTop,
                    hovered != null && hovered.kind == HitKind.CLOSE,
                    textAlpha);
        }
    }

    private void drawDropIndicator(List<Tab> tabs, Row row, int rowBottom) {
        int index = Math.max(0, Math.min(tabs.size(), row.dropIndex));
        int x = index < tabs.size()
                ? tabs.get(index).x
                : tabs.get(tabs.size() - 1).x
                        + tabs.get(tabs.size() - 1).width;
        Gui.drawRect(row.offsetX + x - 1, rowTop(rowBottom),
                row.offsetX + x + 1, rowBottom,
                LostTalesChatVisualStyle.argb(DROP_RGB, scaled(0xFF)));
    }

    /* Control glyphs are drawn from rectangles so they stay crisp at every
       GUI scale and take the palette directly. */

    private void drawClose(int x, int y, boolean hovered, int alpha) {
        int color = LostTalesChatVisualStyle.argb(
                hovered ? CLOSE_HOVER_RGB : LostTalesChatVisualStyle.IVORY,
                hovered ? scaled(0xFF) : alpha);
        int shadow = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(alpha));
        int size = 5;
        int inset = (CONTROL_SIZE - size) / 2;
        for (int step = 0; step < size; step++) {
            int px = x + inset + step;
            int py = y + inset + step;
            int qx = x + inset + (size - 1 - step);
            Gui.drawRect(px + 1, py + 1, px + 2, py + 2, shadow);
            Gui.drawRect(qx + 1, py + 1, qx + 2, py + 2, shadow);
        }
        for (int step = 0; step < size; step++) {
            int px = x + inset + step;
            int py = y + inset + step;
            int qx = x + inset + (size - 1 - step);
            Gui.drawRect(px, py, px + 1, py + 1, color);
            Gui.drawRect(qx, py, qx + 1, py + 1, color);
        }
    }

    private void drawCog(int x, int y, boolean hovered, int alpha) {
        int color = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.IVORY,
                hovered ? scaled(0xFF) : alpha);
        int shadow = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(alpha));
        // A ring with four teeth: a 5x5 hollow square minus its corners,
        // plus one pixel out on each side.
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? shadow : color;
            int ox = x + (pass == 0 ? 1 : 0) + 1;
            int oy = y + (pass == 0 ? 1 : 0) + 1;
            Gui.drawRect(ox + 1, oy, ox + 4, oy + 1, c);
            Gui.drawRect(ox + 1, oy + 4, ox + 4, oy + 5, c);
            Gui.drawRect(ox, oy + 1, ox + 1, oy + 4, c);
            Gui.drawRect(ox + 4, oy + 1, ox + 5, oy + 4, c);
            Gui.drawRect(ox + 2, oy - 1, ox + 3, oy, c);
            Gui.drawRect(ox + 2, oy + 5, ox + 3, oy + 6, c);
            Gui.drawRect(ox - 1, oy + 2, ox, oy + 3, c);
            Gui.drawRect(ox + 5, oy + 2, ox + 6, oy + 3, c);
        }
    }

    private void drawLock(int x, int y, boolean locked, boolean hovered) {
        int rgb = locked ? LOCKED_RGB : LostTalesChatVisualStyle.IVORY;
        int alpha = scaled(hovered || locked ? 0xFF : 0xA0);
        int color = LostTalesChatVisualStyle.argb(rgb, alpha);
        int shadow = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(alpha));
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? shadow : color;
            int ox = x + 2 + (pass == 0 ? 1 : 0);
            int oy = y + (pass == 0 ? 1 : 0);
            // Body: 5 wide, 4 tall, with a one-pixel keyhole.
            Gui.drawRect(ox, oy + 4, ox + 5, oy + 8, c);
            if (pass == 1) {
                Gui.drawRect(ox + 2, oy + 5, ox + 3, oy + 7,
                        LostTalesChatVisualStyle.argb(
                                LostTalesChatVisualStyle.SHADOW, alpha));
            }
            // Shackle: closed over the body, or swung open to the right.
            if (locked) {
                Gui.drawRect(ox + 1, oy + 1, ox + 4, oy + 2, c);
                Gui.drawRect(ox, oy + 2, ox + 1, oy + 4, c);
                Gui.drawRect(ox + 3, oy + 2, ox + 4, oy + 4, c);
            } else {
                Gui.drawRect(ox + 2, oy, ox + 5, oy + 1, c);
                Gui.drawRect(ox + 1, oy + 1, ox + 2, oy + 3, c);
                Gui.drawRect(ox + 4, oy + 1, ox + 5, oy + 4, c);
            }
        }
    }

    private void drawRestore(int x, int y, boolean hovered) {
        int alpha = scaled(hovered ? 0xFF : 0xA0);
        int color = LostTalesChatVisualStyle.argb(RESTORE_RGB, alpha);
        int shadow = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.SHADOW,
                LostTalesChatVisualStyle.shadowAlpha(alpha));
        for (int pass = 0; pass < 2; pass++) {
            int c = pass == 0 ? shadow : color;
            int ox = x + 2 + (pass == 0 ? 1 : 0);
            int oy = y + 2 + (pass == 0 ? 1 : 0);
            Gui.drawRect(ox, oy + 2, ox + 5, oy + 3, c);
            Gui.drawRect(ox + 2, oy, ox + 3, oy + 5, c);
        }
    }

    private void drawGrip(int left, int right, int rowBottom,
                          boolean hovered) {
        if (right - left < MIN_GRIP_WIDTH) {
            return;
        }
        int alpha = scaled(hovered ? 0xC0 : 0x60);
        int color = LostTalesChatVisualStyle.argb(
                LostTalesChatVisualStyle.IVORY, alpha);
        // Two columns of three dots at the strip's right end: the
        // familiar drag handle, where a title bar keeps it.
        int centerX = right - 6;
        int centerY = rowBottom - HEIGHT / 2 - 1;
        for (int column = 0; column < 2; column++) {
            for (int dot = 0; dot < 3; dot++) {
                int px = centerX - 2 + column * 3;
                int py = centerY - 2 + dot * 2;
                Gui.drawRect(px, py, px + 1, py + 1, color);
            }
        }
    }

    /**
     * Resting geometry for the row, cached until the font, limits, tab
     * list, selection, a label, a counter, a mute or the control set
     * changes. Labels are trimmed when the row would overflow its limit,
     * dividing the available width evenly.
     */
    List<Tab> layout(FontRenderer font, Row row) {
        if (font == null || row == null || row.tabs == null
                || row.tabs.isEmpty()) {
            this.cachedTabs = Collections.emptyList();
            this.lockX = -1;
            this.restoreX = -1;
            this.tabsRight = row == null ? 0 : row.left;
            this.controlsRight = this.tabsRight;
            return this.cachedTabs;
        }
        boolean showClose = row.closable;
        if (isLayoutCurrent(font, row, showClose)) {
            return this.cachedTabs;
        }
        List<ChatTab> channels = row.tabs;
        int endControls = END_CONTROL_SIZE + END_CONTROL_GAP
                + (row.showRestore ? END_CONTROL_SIZE + END_CONTROL_GAP : 0)
                + MIN_GRIP_WIDTH;
        int selectedExtra = controlsWidth(showClose);
        int available = row.right - row.left - endControls
                + OVERLAP * (channels.size() - 1);
        // Labels are shown whole; only a row that would overflow its
        // window trims them, dividing the width evenly.
        int maxLabelWidth = Integer.MAX_VALUE / 4;
        if (totalWidth(font, channels, maxLabelWidth) + selectedExtra
                > available) {
            maxLabelWidth = Math.max(MIN_LABEL_WIDTH,
                    (available - selectedExtra) / channels.size()
                            - PADDING_X * 2);
        }
        List<Tab> tabs = new ArrayList<Tab>(channels.size());
        int x = row.left;
        for (int index = 0; index < channels.size(); index++) {
            ChatTab channel = channels.get(index);
            boolean selected = channel.equals(row.selected);
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.cachedLabels.get(channel), maxLabelWidth);
            String pingText = counterText(count(this.cachedPings, channel));
            String otherText = counterText(count(this.cachedOther, channel));
            int labelWidth = font.getStringWidth(label);
            int pingWidth = font.getStringWidth(pingText);
            int otherWidth = font.getStringWidth(otherText);
            int width = labelWidth + PADDING_X * 2
                    + countersWidth(pingWidth, otherWidth);
            int settingsX = -1;
            int closeX = -1;
            if (selected) {
                settingsX = x + width - PADDING_X + CONTROL_GAP;
                width += CONTROL_GAP + CONTROL_SIZE;
                if (showClose) {
                    closeX = x + width - PADDING_X + CONTROL_GAP;
                    width += CONTROL_GAP + CONTROL_SIZE;
                }
            }
            Boolean muted = this.cachedMuted.get(channel);
            tabs.add(new Tab(channel, label, labelWidth, pingText,
                    pingWidth, otherText, otherWidth, x, width,
                    settingsX, closeX, muted != null && muted.booleanValue()));
            x += width - OVERLAP;
        }
        this.tabsRight = x + OVERLAP;
        int controlX = this.tabsRight + END_CONTROL_GAP;
        this.lockX = controlX;
        controlX += END_CONTROL_SIZE + END_CONTROL_GAP;
        if (row.showRestore) {
            this.restoreX = controlX;
            controlX += END_CONTROL_SIZE + END_CONTROL_GAP;
        } else {
            this.restoreX = -1;
        }
        this.controlsRight = controlX;
        this.cachedTabs = Collections.unmodifiableList(tabs);
        this.cachedChannels = new ArrayList<ChatTab>(channels);
        this.cachedSelected = row.selected;
        this.cachedFont = font;
        this.cachedLeft = row.left;
        this.cachedRight = row.right;
        this.cachedShowClose = showClose;
        this.cachedShowRestore = row.showRestore;
        return this.cachedTabs;
    }

    private static int controlsWidth(boolean showClose) {
        return CONTROL_GAP + CONTROL_SIZE
                + (showClose ? CONTROL_GAP + CONTROL_SIZE : 0);
    }

    private static int count(Map<ChatTab, Integer> counters, ChatTab tab) {
        Integer value = counters.get(tab);
        return value == null ? 0 : value.intValue();
    }

    /** Refreshes the cache keys and reports whether the layout still holds. */
    private boolean isLayoutCurrent(FontRenderer font, Row row,
                                    boolean showClose) {
        boolean current = font == this.cachedFont
                && row.left == this.cachedLeft
                && row.right == this.cachedRight
                && (row.selected == null ? this.cachedSelected == null
                        : row.selected.equals(this.cachedSelected))
                && showClose == this.cachedShowClose
                && row.showRestore == this.cachedShowRestore
                && row.tabs.equals(this.cachedChannels);
        for (int index = 0; index < row.tabs.size(); index++) {
            ChatTab tab = row.tabs.get(index);
            String label = ClientChatChannelState.displayName(tab);
            int pings = ClientChatChannelViews.unreadPingCount(tab);
            int other = ClientChatChannelViews.unreadOtherCount(tab);
            boolean muted = ChatWindowLayout.isMuted(tab);
            Boolean cachedMute = this.cachedMuted.get(tab);
            if (!label.equals(this.cachedLabels.get(tab))
                    || pings != count(this.cachedPings, tab)
                    || other != count(this.cachedOther, tab)
                    || cachedMute == null
                    || muted != cachedMute.booleanValue()) {
                this.cachedLabels.put(tab, label);
                this.cachedPings.put(tab, Integer.valueOf(pings));
                this.cachedOther.put(tab, Integer.valueOf(other));
                this.cachedMuted.put(tab, Boolean.valueOf(muted));
                current = false;
            }
        }
        return current;
    }

    /** {@code (n)} for a positive count, {@code (99+)} past the cap, else empty. */
    static String counterText(int count) {
        if (count <= 0) {
            return "";
        }
        return "(" + (count > ClientChatChannelViews.MAX_UNREAD
                ? ClientChatChannelViews.MAX_UNREAD + "+"
                : String.valueOf(count)) + ")";
    }

    private static int countersWidth(int pingWidth, int otherWidth) {
        return (pingWidth > 0 ? COUNTER_GAP + pingWidth : 0)
                + (otherWidth > 0 ? COUNTER_GAP + otherWidth : 0);
    }

    private int totalWidth(FontRenderer font, List<ChatTab> channels,
                           int maxLabelWidth) {
        int total = 0;
        for (int index = 0; index < channels.size(); index++) {
            ChatTab tab = channels.get(index);
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.cachedLabels.get(tab), maxLabelWidth);
            total += font.getStringWidth(label) + PADDING_X * 2
                    + countersWidth(
                            font.getStringWidth(counterText(
                                    count(this.cachedPings, tab))),
                            font.getStringWidth(counterText(
                                    count(this.cachedOther, tab))));
        }
        return total;
    }

    private static int blend(int fromRgb, int toRgb, float amount) {
        float t = Math.max(0.0F, Math.min(1.0F, amount));
        int red = Math.round(((fromRgb >> 16) & 0xFF)
                + (((toRgb >> 16) & 0xFF) - ((fromRgb >> 16) & 0xFF)) * t);
        int green = Math.round(((fromRgb >> 8) & 0xFF)
                + (((toRgb >> 8) & 0xFF) - ((fromRgb >> 8) & 0xFF)) * t);
        int blue = Math.round((fromRgb & 0xFF)
                + ((toRgb & 0xFF) - (fromRgb & 0xFF)) * t);
        return (red << 16) | (green << 8) | blue;
    }

    static final class Tab {
        final ChatTab tab;
        final String label;
        final int labelWidth;
        /** {@code (p)} unread pings, or empty. */
        final String pingText;
        final int pingWidth;
        /** {@code (x)} other unread lines, or empty. */
        final String otherText;
        final int otherWidth;
        /** Resting left edge before the row's horizontal motion. */
        final int x;
        final int width;
        /** Resting left edge of the cog, or -1 when the tab has none. */
        final int settingsX;
        /** Resting left edge of the close cross, or -1. */
        final int closeX;
        final boolean muted;

        Tab(ChatTab tab, String label, int labelWidth,
            String pingText, int pingWidth, String otherText,
            int otherWidth, int x, int width, int settingsX, int closeX,
            boolean muted) {
            this.tab = tab;
            this.label = label;
            this.labelWidth = labelWidth;
            this.pingText = pingText;
            this.pingWidth = pingWidth;
            this.otherText = otherText;
            this.otherWidth = otherWidth;
            this.x = x;
            this.width = width;
            this.settingsX = settingsX;
            this.closeX = closeX;
            this.muted = muted;
        }
    }
}
