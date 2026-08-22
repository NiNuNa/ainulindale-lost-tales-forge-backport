package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.config.LostTalesConfig;
import com.ninuna.losttales.gui.style.LostTalesColors;
import com.ninuna.losttales.gui.style.LostTalesSkyrimUiStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;

/**
 * Folder-divider style channel tabs sitting on top of the history box
 * while the chat screen is open, like the tabs on a folder. Neighbouring
 * tabs overlap by a few pixels like file dividers; the selected tab is
 * drawn last, lifted, and brighter, and its body runs into the box so the
 * two read as one sheet. Each tab eases its own prominence toward its
 * target from wherever it currently is, so rapid switching never waits on
 * a previous transition. A tab with unread messages carries textual
 * counters after its name: {@code (p)} pings in salmon, then {@code (x)}
 * other unread lines in honey. Geometry is computed once per change of
 * inputs and reused by drawing and hit testing, so a frame allocates
 * nothing.
 *
 * <p>The row is positioned by the history it stands on: {@code boxTop}
 * and {@code offsetX} come from the bands the overlay renderer drew, so
 * the tabs enter, settle and fade with the same shared motion state as
 * the history and its backdrop rather than with the input bar.</p>
 */
final class ChatChannelTabBar {
    /** Body height of a resting tab; the selected tab rises above it. */
    static final int HEIGHT = 11;
    /** Pixels the selected tab rises out of the row. */
    private static final int LIFT = 2;
    /** Horizontal overlap between neighbouring dividers. */
    private static final int OVERLAP = 3;
    private static final int LEFT = 2;
    private static final int PADDING_X = 6;
    private static final int MAX_LABEL_WIDTH = 72;
    private static final int MIN_LABEL_WIDTH = 14;
    /** Gap between the label and a counter, and between the counters. */
    private static final int COUNTER_GAP = 3;
    private static final int PING_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.SALMON);
    private static final int UNREAD_COUNTER_RGB =
            LostTalesColors.rgb(LostTalesColors.HONEY);

    private static final int CHANNEL_COUNT = ChatChannel.values().length;
    private final float[] prominence = new float[CHANNEL_COUNT];
    private final float[] fromValue = new float[CHANNEL_COUNT];
    private final boolean[] target = new boolean[CHANNEL_COUNT];
    private final long[] startNanos = new long[CHANNEL_COUNT];
    private boolean initialized;

    private List<Tab> cachedTabs = Collections.emptyList();
    private List<ChatChannel> cachedChannels = Collections.emptyList();
    private final String[] cachedLabels = new String[CHANNEL_COUNT];
    private final int[] cachedPings = new int[CHANNEL_COUNT];
    private final int[] cachedOther = new int[CHANNEL_COUNT];
    private FontRenderer cachedFont;
    private int cachedScreenWidth = -1;
    private float alphaScale = 1.0F;

    /** Advances every tab's easing toward the current selection. */
    void update(ChatChannel selected) {
        long now = System.nanoTime();
        for (ChatChannel channel : ChatChannel.values()) {
            int index = channel.ordinal();
            boolean wanted = channel == selected;
            if (!this.initialized) {
                this.target[index] = wanted;
                this.prominence[index] = wanted ? 1.0F : 0.0F;
                this.fromValue[index] = this.prominence[index];
                this.startNanos[index] = 0L;
                continue;
            }
            if (this.target[index] != wanted) {
                this.target[index] = wanted;
                this.fromValue[index] = this.prominence[index];
                this.startNanos[index] = now;
            }
            float goal = wanted ? 1.0F : 0.0F;
            if (!LostTalesConfig.enableChatAnimations
                    || this.startNanos[index] == 0L) {
                this.prominence[index] = goal;
                continue;
            }
            long duration = Math.max(1,
                    LostTalesConfig.chatSelectorAnimationDurationMillis)
                    * 1000000L;
            float elapsed = Math.min(1.0F,
                    (now - this.startNanos[index]) / (float)duration);
            float eased = LostTalesChatMotion.menuProgress(elapsed);
            this.prominence[index] = this.fromValue[index]
                    + (goal - this.fromValue[index]) * eased;
        }
        this.initialized = true;
    }

    /** Row bottom: the tabs stand on the history box's top edge. */
    static int rowBottom(int boxTop) {
        return boxTop;
    }

    /** Highest pixel any tab can reach (the selected tab fully lifted). */
    static int rowTop(int boxTop) {
        return boxTop - HEIGHT - LIFT;
    }

    /**
     * The tab under a GUI-space point, or null. {@code offsetX} is the
     * horizontal motion the history is currently drawn with.
     */
    ChatChannel tabAt(FontRenderer font, List<ChatChannel> channels,
                      ChatChannel selected, int screenWidth, int boxTop,
                      int offsetX, int mouseX, int mouseY) {
        if (mouseY < rowTop(boxTop) || mouseY >= rowBottom(boxTop)) {
            return null;
        }
        List<Tab> tabs = layout(font, channels, screenWidth);
        // Later tabs overlap earlier ones, so the last hit wins, except
        // the selected tab, which is on top of everything.
        ChatChannel hit = null;
        int localX = mouseX - offsetX;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (localX >= tab.x && localX < tab.x + tab.width) {
                if (tab.channel == selected) {
                    return tab.channel;
                }
                hit = tab.channel;
            }
        }
        return hit;
    }

    /**
     * Draws the row on top of the history. {@code offsetX} and
     * {@code alphaScale} are the history's own opening motion, so tabs and
     * lines arrive and fade together; the rectangle registered with
     * {@code regions} is the one actually painted.
     */
    void draw(FontRenderer font, ChatPointerRegions regions,
              List<ChatChannel> channels, ChatChannel selected,
              int screenWidth, int boxTop, int offsetX,
              int mouseX, int mouseY, float alphaScale) {
        update(selected);
        List<Tab> tabs = layout(font, channels, screenWidth);
        if (tabs.isEmpty()) {
            return;
        }
        this.alphaScale = Math.max(0.0F, Math.min(1.0F, alphaScale));
        ChatChannel hovered = tabAt(font, channels, selected, screenWidth,
                boxTop, offsetX, mouseX, mouseY);
        int bottom = rowBottom(boxTop);
        Tab selectedTab = null;
        for (int index = 0; index < tabs.size(); index++) {
            Tab tab = tabs.get(index);
            if (tab.channel == selected) {
                selectedTab = tab;
                continue;
            }
            drawTab(font, tab, offsetX, bottom, tab.channel == hovered,
                    false);
        }
        if (selectedTab != null) {
            drawTab(font, selectedTab, offsetX, bottom,
                    selectedTab.channel == hovered, true);
        }
        Tab last = tabs.get(tabs.size() - 1);
        regions.addScreen(offsetX + tabs.get(0).x, rowTop(boxTop),
                offsetX + last.x + last.width, bottom);
    }

    private int scaled(int alpha) {
        int result = Math.round(alpha * this.alphaScale);
        return result < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA
                ? 0 : result;
    }

    private void drawTab(FontRenderer font, Tab tab, int offsetX,
                         int rowBottom, boolean hovered, boolean selected) {
        float rise = this.prominence[tab.channel.ordinal()];
        int lift = Math.round(LIFT * rise);
        int top = rowBottom - HEIGHT - lift;
        // The selected tab joins the history box; resting tabs keep a one
        // pixel seam so they read as sitting behind it.
        int bottom = selected ? rowBottom + 1 : rowBottom;
        int left = offsetX + tab.x;
        int right = left + tab.width;

        int surfaceAlpha = scaled(Math.round(0x90 + (0xE6 - 0x90) * rise));
        int surfaceRgb = blend(LostTalesChatVisualStyle.SURFACE_RGB,
                LostTalesChatVisualStyle.SURFACE_HIGHLIGHT_RGB,
                hovered ? Math.max(0.35F, rise) : rise * 0.55F);
        int surface = (surfaceAlpha << 24) | surfaceRgb;
        // Chamfered top corners: the top row is inset one pixel per side.
        Gui.drawRect(left + 1, top, right - 1, top + 1, surface);
        Gui.drawRect(left, top + 1, right, bottom, surface);
        // Channel accent along the top edge, brighter when forward.
        int accentAlpha = scaled(Math.round(0xA0 + (0xFF - 0xA0) * rise));
        Gui.drawRect(left + 1, top + 1, right - 1, top + 2,
                (accentAlpha << 24)
                        | ClientChatChannelState.displayColor(tab.channel));
        if (!selected) {
            // Shaded right edge suggests the next divider stacked above.
            Gui.drawRect(right - 1, top + 1, right, bottom,
                    LostTalesChatVisualStyle.argb(
                            LostTalesChatVisualStyle.SHADOW, scaled(0x90)));
        }
        int textAlpha = scaled(Math.round(185 + (255 - 185) * rise));
        int textX = left + PADDING_X;
        int textY = top + 3;
        LostTalesChatVisualStyle.drawPlain(font, tab.label, textX, textY,
                textAlpha);
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
    }

    /**
     * Resting geometry for the given channels, cached until the font,
     * screen width, channel list, a label, or an unread count changes.
     * Labels are trimmed when the row would overflow the screen, dividing
     * the available width evenly.
     */
    List<Tab> layout(FontRenderer font, List<ChatChannel> channels,
                     int screenWidth) {
        if (font == null || channels == null || channels.isEmpty()) {
            return Collections.emptyList();
        }
        if (isLayoutCurrent(font, channels, screenWidth)) {
            return this.cachedTabs;
        }
        List<Tab> tabs = new ArrayList<Tab>(channels.size());
        int available = screenWidth - LEFT * 2
                + OVERLAP * (channels.size() - 1);
        int maxLabelWidth = MAX_LABEL_WIDTH;
        if (totalWidth(font, channels, maxLabelWidth) > available) {
            maxLabelWidth = Math.max(MIN_LABEL_WIDTH,
                    available / channels.size() - PADDING_X * 2);
        }
        int x = LEFT;
        for (int index = 0; index < channels.size(); index++) {
            ChatChannel channel = channels.get(index);
            int ordinal = channel.ordinal();
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.cachedLabels[ordinal], maxLabelWidth);
            String pingText = counterText(this.cachedPings[ordinal]);
            String otherText = counterText(this.cachedOther[ordinal]);
            int labelWidth = font.getStringWidth(label);
            int pingWidth = font.getStringWidth(pingText);
            int otherWidth = font.getStringWidth(otherText);
            int width = labelWidth + PADDING_X * 2
                    + countersWidth(pingWidth, otherWidth);
            tabs.add(new Tab(channel, label, labelWidth, pingText,
                    pingWidth, otherText, otherWidth, x, width));
            x += width - OVERLAP;
        }
        this.cachedTabs = Collections.unmodifiableList(tabs);
        this.cachedChannels = new ArrayList<ChatChannel>(channels);
        this.cachedFont = font;
        this.cachedScreenWidth = screenWidth;
        return this.cachedTabs;
    }

    /** Refreshes the cache keys and reports whether the layout still holds. */
    private boolean isLayoutCurrent(FontRenderer font,
                                    List<ChatChannel> channels,
                                    int screenWidth) {
        boolean current = font == this.cachedFont
                && screenWidth == this.cachedScreenWidth
                && channels.equals(this.cachedChannels);
        for (int index = 0; index < channels.size(); index++) {
            ChatChannel channel = channels.get(index);
            int ordinal = channel.ordinal();
            String label = ClientChatChannelState.displayName(channel);
            int pings = ClientChatChannelViews.unreadPingCount(channel);
            int other = ClientChatChannelViews.unreadOtherCount(channel);
            if (!label.equals(this.cachedLabels[ordinal])
                    || pings != this.cachedPings[ordinal]
                    || other != this.cachedOther[ordinal]) {
                this.cachedLabels[ordinal] = label;
                this.cachedPings[ordinal] = pings;
                this.cachedOther[ordinal] = other;
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

    private int totalWidth(FontRenderer font, List<ChatChannel> channels,
                           int maxLabelWidth) {
        int total = 0;
        for (int index = 0; index < channels.size(); index++) {
            int ordinal = channels.get(index).ordinal();
            String label = LostTalesSkyrimUiStyle.trimToWidth(font,
                    this.cachedLabels[ordinal], maxLabelWidth);
            total += font.getStringWidth(label) + PADDING_X * 2
                    + countersWidth(
                            font.getStringWidth(counterText(
                                    this.cachedPings[ordinal])),
                            font.getStringWidth(counterText(
                                    this.cachedOther[ordinal])));
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
        final ChatChannel channel;
        final String label;
        final int labelWidth;
        /** {@code (p)} unread pings, or empty. */
        final String pingText;
        final int pingWidth;
        /** {@code (x)} other unread lines, or empty. */
        final String otherText;
        final int otherWidth;
        /** Resting left edge before the history's horizontal motion. */
        final int x;
        final int width;

        Tab(ChatChannel channel, String label, int labelWidth,
            String pingText, int pingWidth, String otherText,
            int otherWidth, int x, int width) {
            this.channel = channel;
            this.label = label;
            this.labelWidth = labelWidth;
            this.pingText = pingText;
            this.pingWidth = pingWidth;
            this.otherText = otherText;
            this.otherWidth = otherWidth;
            this.x = x;
            this.width = width;
        }
    }
}
