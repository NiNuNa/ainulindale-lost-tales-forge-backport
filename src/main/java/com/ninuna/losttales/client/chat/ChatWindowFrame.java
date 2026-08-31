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
    /**
     * Index in {@link #lines} of the oldest wrapped row of the message
     * the unread divider stands above, or -1 while this view shows no
     * divider. The divider takes a whole row of the stack, so it is part
     * of the window's content height and not only of its drawing: the
     * scroll range, the scrollbar and the draw all read it here, which
     * is what keeps the room a view can reach and the rows it actually
     * renders the same measurement.
     */
    int dividerLineIndex = -1;
    /** What {@link #dividerLineIndex} was worked out from. */
    private List<ChatLine> dividerSource;
    private int dividerSourceSize;
    private int dividerSourceLineId;
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
    /** Message-line room of the box drawn this frame, in pixels,
     *  measured so that {@code baseline - room} is itself a snapped
     *  display position: the content top is snapped as one value rather
     *  than as the difference of two snapped ones, so it never wobbles
     *  against a moving baseline while a resize runs. */
    double room;
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
    /**
     * The jump-to-present button drawn this frame, in screen GUI
     * pixels; width zero while none was drawn. Recorded from the draw
     * itself, like the bands, so the click and the pixels cannot
     * disagree.
     */
    float jumpPillLeft;
    float jumpPillTop;
    float jumpPillRight;
    float jumpPillBottom;
    /** How far the button has flown in from below the rule, 0..1. */
    float jumpButtonProgress;
    /**
     * The hovered message's toolbar as drawn this frame, in screen GUI
     * pixels; width zero while none was drawn. Recorded from the draw
     * itself, like the jump button, so the click and the pixels cannot
     * disagree. {@link #toolbarKinds} says which control each equal
     * share of its width is, left to right, so a message offering fewer
     * of them needs no separate bookkeeping.
     */
    float toolbarLeft;
    float toolbarTop;
    float toolbarRight;
    float toolbarBottom;
    int[] toolbarKinds = NO_KINDS;
    /** The message the toolbar belongs to, by chat line id. */
    int toolbarChatLineId;

    private static final int[] NO_KINDS = new int[0];

    /** Whether the point lies on the toolbar drawn this frame. */
    boolean toolbarContains(float x, float y) {
        return this.drawn && this.toolbarRight > this.toolbarLeft
                && this.toolbarKinds.length > 0
                && x >= this.toolbarLeft && x < this.toolbarRight
                && y >= this.toolbarTop && y < this.toolbarBottom;
    }

    /** The control under the point, or -1 when the point is not on one. */
    int toolbarKindAt(float x, float y) {
        if (!toolbarContains(x, y)) {
            return -1;
        }
        float share = (this.toolbarRight - this.toolbarLeft)
                / this.toolbarKinds.length;
        int index = (int)((x - this.toolbarLeft) / Math.max(1.0F, share));
        return this.toolbarKinds[Math.max(0,
                Math.min(this.toolbarKinds.length - 1, index))];
    }
    /** When the fly-in was last advanced. */
    long jumpButtonNanos;
    /**
     * The scrollbar's thumb as drawn this frame, in screen GUI pixels;
     * width zero while none was drawn. The track it slides in is the
     * message area's own height, recorded with it so a drag can map the
     * pointer onto the history without measuring the window again.
     */
    float scrollbarLeft;
    float scrollbarRight;
    float scrollbarTrackTop;
    float scrollbarTrackBottom;
    float scrollbarThumbTop;
    float scrollbarThumbBottom;
    /** How far the bar has faded in while the pointer rests here, 0..1. */
    float scrollbarProgress;
    long scrollbarNanos;
    /** Whether the pointer is in this window, so the bar should show. */
    boolean scrollbarWanted;

    /** Whether the point lies on the scrollbar's track drawn this frame. */
    boolean scrollbarContains(float x, float y) {
        return this.drawn && this.scrollbarRight > this.scrollbarLeft
                && x >= this.scrollbarLeft && x < this.scrollbarRight
                && y >= this.scrollbarTrackTop
                && y < this.scrollbarTrackBottom;
    }

    /** Whether the point lies on the pill drawn this frame. */
    boolean jumpPillContains(float x, float y) {
        return this.drawn && this.jumpPillRight > this.jumpPillLeft
                && x >= this.jumpPillLeft && x < this.jumpPillRight
                && y >= this.jumpPillTop && y < this.jumpPillBottom;
    }

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
        // The feed's frame is never pruned, so what it remembers about
        // the history it was reading has to be let go with the history.
        FEED.dividerLineIndex = -1;
        FEED.dividerSource = null;
        FEED.dividerSourceSize = 0;
        FEED.dividerSourceLineId = 0;
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
        // The content top snapped as one value; see {@link #room}.
        this.room = this.baseline
                - snapToDisplayPixels(box.baseline() - box.room);
        this.renderedScrollLines = 0.0D;
        // Until the draw says otherwise the stack fills the box; an
        // empty window is corrected to its one placeholder line.
        this.stackTop = this.baseline + this.motionY - this.room;
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
    static int displayScaleFactor() {
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
     * Works out where this view's unread divider falls in the line list
     * and records it. The answer is kept until the list, its length or
     * the divider's message changes: it is asked for every window of
     * every frame, and the scan is only worth paying for when one of
     * those has moved.
     */
    void resolveDividerRow(List<ChatLine> drawnLines, Integer dividerLine) {
        int lineId = dividerLine == null ? 0 : dividerLine.intValue();
        int size = drawnLines == null ? 0 : drawnLines.size();
        if (drawnLines == this.dividerSource && size == this.dividerSourceSize
                && lineId == this.dividerSourceLineId
                && stillDivides(drawnLines, lineId)) {
            return;
        }
        this.dividerSource = drawnLines;
        this.dividerSourceSize = size;
        this.dividerSourceLineId = lineId;
        this.dividerLineIndex = lineId == 0 ? -1
                : lastRowOf(drawnLines, lineId);
    }

    /**
     * Whether the remembered row still holds the divider's message. The
     * list is rebuilt rather than edited whenever the history changes,
     * so its identity is the usual signal; this reads the row itself as
     * well, so a list that changed under the same identity cannot leave
     * the stack drawing a divider where there is none.
     */
    private boolean stillDivides(List<ChatLine> drawnLines, int lineId) {
        if (lineId == 0) {
            return this.dividerLineIndex < 0;
        }
        if (this.dividerLineIndex < 0) {
            return true;
        }
        return drawnLines != null && this.dividerLineIndex < drawnLines.size()
                && drawnLines.get(this.dividerLineIndex) != null
                && drawnLines.get(this.dividerLineIndex).getChatLineID()
                        == lineId;
    }

    /**
     * The oldest wrapped row of a message, which is the row the divider
     * stands above: vanilla's list runs newest first, so a message's
     * rows are a run and its last index is the one to divide at.
     */
    private static int lastRowOf(List<ChatLine> drawnLines, int chatLineId) {
        if (drawnLines == null) {
            return -1;
        }
        for (int index = 0; index < drawnLines.size(); index++) {
            ChatLine line = drawnLines.get(index);
            if (line == null || line.getChatLineID() != chatLineId) {
                continue;
            }
            while (index + 1 < drawnLines.size()
                    && drawnLines.get(index + 1) != null
                    && drawnLines.get(index + 1).getChatLineID()
                            == chatLineId) {
                index++;
            }
            return index;
        }
        return -1;
    }

    /**
     * Rows of content this view holds: its wrapped message lines plus
     * the unread divider's own row when it has one. Every measurement
     * of how far the view can scroll is taken from this, so the range
     * the player can reach is exactly the stack that is drawn.
     */
    int contentRows() {
        return (this.lines == null ? 0 : this.lines.size())
                + (this.dividerLineIndex >= 0 ? 1 : 0);
    }

    /** Rows of content the window has room for, fractions included. */
    double roomLines() {
        return this.room / (double)Math.max(1.0F,
                LostTalesChatOverlayRenderer.LINE_HEIGHT * this.scale);
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
     * Bottom of the tab row (screen y, fractional): the top margin above
     * the drawn message stack — the row's last pixel row is the window's
     * top rule, and the first content pixel lies the margin below it, so
     * the topmost line keeps clear of the rule. A full window's stack
     * ends on its box's own edge, so the row and the box top never drift
     * apart whatever the chat scale is; a window with fewer lines than
     * it has room for carries its row down onto them.
     */
    double tabRowBottom() {
        return this.stackTop - ChatWindowPlacement.HISTORY_TOP_MARGIN;
    }

    /** Whether the point is inside this window's box. */
    boolean contains(double x, double y) {
        return x >= this.boxLeft && x < this.boxRight
                && y >= this.boxTop && y < this.boxBottom;
    }
}
