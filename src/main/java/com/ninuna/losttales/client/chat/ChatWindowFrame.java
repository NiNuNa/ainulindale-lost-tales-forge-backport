package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;

/**
 * Per-window render state: the line bands the last draw recorded, the
 * line list they index into, the tab row, and the window's box on screen.
 * Frames are keyed by window id and outlive the chat screen, so tab
 * easing and hit testing stay continuous across opening and closing;
 * a frame whose window no longer exists is pruned on the next draw.
 *
 * <p>Every window's box is the one {@link ChatWindowPlacement} derives
 * from the window's percent offsets — the same box the HUD placement
 * editor draws and edits — with the newest line on the baseline, the
 * window's input bar below it, and the tab row standing on the topmost
 * drawn line, or on one empty line when the view is empty. The box is
 * kept in fractional pixels, and the opening motion the window was drawn
 * with is kept alongside, so the row and the bar the screen draws land on
 * exactly the same fractional position as the lines.</p>
 */
final class ChatWindowFrame {
    private static final Map<String, ChatWindowFrame> FRAMES =
            new HashMap<String, ChatWindowFrame>();
    /** The closed-chat feed's frame; not a window, never pruned. */
    private static final ChatWindowFrame FEED = new ChatWindowFrame("feed");

    final String windowId;
    final ChatLineBands bands = new ChatLineBands();
    final ChatChannelTabBar tabBar = new ChatChannelTabBar();
    /** The view's line list drawn last; the bands index into it. */
    List<ChatLine> lines = Collections.emptyList();
    /** The tab shown while open, null for the closed-chat feed. */
    ChatTab view;
    boolean drawn;
    double boxLeft;
    double boxTop;
    double boxRight;
    double boxBottom;
    /** The edge the newest message sits on; the bar hangs below it. */
    double baseline;
    /** Top of the input bar at rest, one chat line below the baseline. */
    private double restingBarTop;
    /** Chat scale the box was drawn at; sizes one empty line. */
    float scale = 1.0F;
    /** Opening motion the window was drawn with this frame. */
    float motionX;
    float motionY;

    private ChatWindowFrame(String windowId) {
        this.windowId = windowId;
    }

    static synchronized ChatWindowFrame of(ChatWindow window) {
        ChatWindowFrame frame = FRAMES.get(window.getId());
        if (frame == null) {
            frame = new ChatWindowFrame(window.getId());
            FRAMES.put(window.getId(), frame);
        }
        return frame;
    }

    static synchronized ChatWindowFrame find(String windowId) {
        return FRAMES.get(windowId);
    }

    static ChatWindowFrame feed() {
        return FEED;
    }

    /** Frames of windows drawn this frame, in window order. */
    static synchronized List<ChatWindowFrame> drawnFrames() {
        List<ChatWindowFrame> result = new ArrayList<ChatWindowFrame>();
        for (ChatWindow window : ChatWindowLayout.windows()) {
            ChatWindowFrame frame = FRAMES.get(window.getId());
            if (frame != null && frame.drawn) {
                result.add(frame);
            }
        }
        return result;
    }

    /** Drops frames of windows that no longer exist. */
    static synchronized void prune(List<ChatWindow> windows) {
        if (FRAMES.size() <= windows.size()) {
            return;
        }
        List<String> stale = new ArrayList<String>();
        for (String id : FRAMES.keySet()) {
            boolean alive = false;
            for (int index = 0; index < windows.size(); index++) {
                if (windows.get(index).getId().equals(id)) {
                    alive = true;
                    break;
                }
            }
            if (!alive) {
                stale.add(id);
            }
        }
        for (String id : stale) {
            FRAMES.remove(id);
        }
    }

    static synchronized void clear() {
        FRAMES.clear();
        FEED.lines = Collections.emptyList();
        FEED.drawn = false;
        FEED.bands.reset(null, 0, 1.0F);
    }

    /** Every open, unmuted tab the player can see: the feed's filter. */
    static ChatLineFilter feedFilter() {
        List<ChatTab> audible = new ArrayList<ChatTab>();
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            List<ChatTab> tabs = visibleTabs(windows.get(index));
            for (int tab = 0; tab < tabs.size(); tab++) {
                if (!ChatWindowLayout.isMuted(tabs.get(tab))) {
                    audible.add(tabs.get(tab));
                }
            }
        }
        return ChatLineFilter.of(audible);
    }

    /** The window's tabs the local player can currently see. */
    static List<ChatTab> visibleTabs(ChatWindow window) {
        List<ChatTab> tabs = window.getTabs();
        List<ChatTab> result = new ArrayList<ChatTab>(tabs.size());
        for (int index = 0; index < tabs.size(); index++) {
            if (ClientChatChannelState.isAvailable(tabs.get(index))) {
                result.add(tabs.get(index));
            }
        }
        return result;
    }

    /** The tab in front, or the first visible tab when it is unavailable. */
    static ChatTab activeTab(ChatWindow window,
                             List<ChatTab> visibleTabs) {
        ChatTab active = window.getActiveTab();
        if (active != null && visibleTabs.contains(active)) {
            return active;
        }
        return visibleTabs.isEmpty() ? null : visibleTabs.get(0);
    }

    /** The closed-chat feed: the window's visible tabs minus muted ones. */
    static ChatLineFilter passiveFilter(List<ChatTab> visibleTabs) {
        List<ChatTab> audible = new ArrayList<ChatTab>(
                visibleTabs.size());
        for (int index = 0; index < visibleTabs.size(); index++) {
            if (!ChatWindowLayout.isMuted(visibleTabs.get(index))) {
                audible.add(visibleTabs.get(index));
            }
        }
        return ChatLineFilter.of(audible);
    }

    /** Width of a window's box: the chat width at chat scale. */
    static int boxWidth(GuiNewChat chat) {
        return LostTalesChatOverlayRenderer.historyRight(chat);
    }

    /** Starts the window's frame from its placement box. */
    void begin(ChatWindowPlacement.Box box, float chatScale,
               float openingMotionX, float openingMotionY) {
        this.boxLeft = box.x;
        this.boxTop = box.y;
        this.boxRight = box.x + box.width;
        this.boxBottom = box.y + box.height;
        this.baseline = box.baseline();
        this.restingBarTop = box.barTop();
        this.scale = chatScale <= 0.0F ? 1.0F : chatScale;
        this.motionX = openingMotionX;
        this.motionY = openingMotionY;
    }

    /** Left edge as drawn this frame, motion included. */
    double drawnLeft() {
        return this.boxLeft + this.motionX;
    }

    /**
     * Top of the window's input bar at rest. The bar has an entrance of
     * its own and does not ride the window's opening motion, exactly as
     * the single input bar did before windows had their own.
     */
    double barTop() {
        return this.restingBarTop;
    }

    /**
     * Bottom of the tab row (screen y, fractional): the padding above
     * the topmost drawn band, or above the one empty line the view shows
     * when it has nothing yet.
     */
    double tabRowBottom() {
        double padding = LostTalesChatOverlayRenderer.LINE_PADDING
                * this.scale;
        if (this.bands.count() > 0) {
            float top = Float.MAX_VALUE;
            for (int index = 0; index < this.bands.count(); index++) {
                top = Math.min(top, this.bands.topOf(index));
            }
            return top - padding;
        }
        return this.baseline + this.motionY
                - LostTalesChatOverlayRenderer.LINE_HEIGHT * this.scale
                - padding;
    }

    /** The box as drawn, for other windows to keep off. */
    ChatWindowPlacement.Box wall() {
        return new ChatWindowPlacement.Box(this.boxLeft, this.boxTop,
                (int)Math.round(this.boxRight - this.boxLeft),
                (int)Math.round(this.boxBottom - this.boxTop), 0);
    }

    /** Whether the point is inside this window's box. */
    boolean contains(double x, double y) {
        return x >= this.boxLeft && x < this.boxRight
                && y >= this.boxTop && y < this.boxBottom;
    }
}
