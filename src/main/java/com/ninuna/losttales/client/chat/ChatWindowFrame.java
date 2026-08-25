package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ChatLine;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.client.gui.ScaledResolution;

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
    /** The display the scale factor below was measured for. */
    private static int measuredWidth;
    private static int measuredHeight;
    private static int measuredFactor = 1;

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
    /**
     * Top of the drawn message stack (screen y, motion included): the
     * edge the tab row stands a padding above. A full window cuts its
     * topmost line where its box runs out of room, so this is the box's
     * own edge rather than the top of the last whole line; a window
     * still filling up sits on the lines it has.
     */
    double stackTop;
    /** Message-line room of the box drawn this frame, in pixels. */
    int room;
    /**
     * The scroll offset the window was drawn at this frame, in lines.
     * The typing line reads it: the trailing strip belongs to the
     * history while the view is scrolled back, and to the typing line
     * only while the view rests on the newest message.
     */
    double renderedScrollLines;
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

    /** Frames of windows drawn this frame, back to front. */
    static synchronized List<ChatWindowFrame> drawnFrames() {
        List<ChatWindowFrame> result = new ArrayList<ChatWindowFrame>();
        for (ChatWindow window : ChatWindowLayout.stacked()) {
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

    /**
     * The feed's filter: every channel the player can see and has not
     * muted or hidden from the feed, whether or not it has a tab —
     * closing a tab hides the tab, not the channel's messages — plus
     * every open conversation tab under the same preferences.
     * Conversations are read from their open tabs only: a closed one is
     * hidden until its next message reopens it.
     */
    static ChatLineFilter feedFilter() {
        List<ChatTab> audible = new ArrayList<ChatTab>();
        for (ChatChannel channel : ChatChannel.presentationOrder()) {
            ChatTab tab = ChatTab.of(channel);
            if (ClientChatChannelState.isAvailable(tab)
                    && ChatWindowLayout.isInFeed(tab)) {
                audible.add(tab);
            }
        }
        List<ChatWindow> windows = ChatWindowLayout.windows();
        for (int index = 0; index < windows.size(); index++) {
            List<ChatTab> tabs = windows.get(index).getTabs();
            for (int tab = 0; tab < tabs.size(); tab++) {
                if (tabs.get(tab).isWhisper()
                        && ChatWindowLayout.isInFeed(tabs.get(tab))) {
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

    /** Width of a window's box: the chat width at chat scale. */
    static int boxWidth(GuiNewChat chat) {
        return LostTalesChatOverlayRenderer.historyRight(chat);
    }

    /**
     * Starts the window's frame from its placement box. The box is
     * dragged in fractions of a GUI pixel so it follows the mouse
     * exactly, and laid on whole display pixels to be drawn: the text,
     * the heads and the emojis in it are pixel art, and between two
     * pixels they crawl. A display pixel is the finest step the screen
     * has, so the motion loses nothing by landing on one.
     */
    void begin(ChatWindowPlacement.Box box, float chatScale,
               float openingMotionX, float openingMotionY) {
        this.boxLeft = snapToDisplayPixels(box.x);
        this.boxTop = snapToDisplayPixels(box.y);
        this.boxRight = this.boxLeft + box.width;
        this.boxBottom = this.boxTop + box.height;
        this.baseline = snapToDisplayPixels(box.baseline());
        this.restingBarTop = snapToDisplayPixels(box.barTop());
        this.scale = chatScale <= 0.0F ? 1.0F : chatScale;
        this.motionX = openingMotionX;
        this.motionY = openingMotionY;
        this.room = box.room;
        this.renderedScrollLines = 0.0D;
        // Until the draw says otherwise the stack fills the box; an
        // empty window is corrected to its one placeholder line.
        this.stackTop = this.baseline + this.motionY - box.room;
    }

    /**
     * A GUI-space position rounded to a whole display pixel, which is
     * the finest step anything drawn can actually take.
     */
    static double snapToDisplayPixels(double position) {
        int factor = displayScaleFactor();
        return factor <= 1 ? Math.round(position)
                : Math.round(position * factor) / (double)factor;
    }

    /**
     * Display pixels per GUI pixel. Measured once per display size: this
     * is asked several times for every window of every frame, and the
     * answer only changes when the window does.
     */
    private static int displayScaleFactor() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0) {
            return 1;
        }
        if (minecraft.displayWidth == measuredWidth
                && minecraft.displayHeight == measuredHeight) {
            return measuredFactor;
        }
        try {
            measuredFactor = Math.max(1, new ScaledResolution(minecraft,
                    minecraft.displayWidth,
                    minecraft.displayHeight).getScaleFactor());
            measuredWidth = minecraft.displayWidth;
            measuredHeight = minecraft.displayHeight;
        } catch (RuntimeException unavailable) {
            return 1;
        }
        return measuredFactor;
    }

    /** Records where the drawn message stack ended up this frame. */
    void setStackTop(double screenY) {
        this.stackTop = screenY;
    }

    /**
     * Left edge as drawn this frame, opening motion included and laid on
     * a whole display pixel. Everything the window draws is measured
     * from here, so everything in it lands on the same grid: the pixel
     * art especially, which is sampled one texel to one pixel and shows
     * every fraction of a pixel as a texel of the wrong width.
     */
    double drawnLeft() {
        return snapToDisplayPixels(this.boxLeft + this.motionX);
    }

    /** The edge the newest message sits on, as drawn this frame. */
    double drawnBaseline() {
        return snapToDisplayPixels(this.baseline + this.motionY);
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
     * Bottom of the tab row (screen y, fractional): the top of the drawn
     * message stack, which the row stands directly on — its last pixel
     * row is the window's top rule, and the first content pixel lies
     * directly below it. A full window's stack ends on its box's own
     * edge, so the row and the box top never drift apart whatever the
     * chat scale is; a window with fewer lines than it has room for
     * carries its row down onto them.
     */
    double tabRowBottom() {
        return this.stackTop;
    }

    /** Whether the point is inside this window's box. */
    boolean contains(double x, double y) {
        return x >= this.boxLeft && x < this.boxRight
                && y >= this.boxTop && y < this.boxBottom;
    }
}
