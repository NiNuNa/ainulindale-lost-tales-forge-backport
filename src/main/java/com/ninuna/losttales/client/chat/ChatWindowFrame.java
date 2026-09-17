package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.chat.ChatChannel;
import com.ninuna.losttales.client.gui.animation.LostTalesUiEasing;
import com.ninuna.losttales.client.gui.animation.LostTalesUiTransition;
import com.ninuna.losttales.config.LostTalesConfig;
import java.util.ArrayList;
import java.util.Arrays;
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
 * line list they index into, the tab row, the window's box on screen,
 * and how far that box stands toward filling the screen.
 * Frames are keyed by window id and outlive the chat screen, so tab
 * easing and hit testing stay continuous across opening and closing;
 * a frame whose window no longer exists is pruned on the next draw.
 *
 * <p>Every window's box is the one {@link ChatWindowPlacement} derives
 * from the window's percent offsets, with the newest line on the
 * baseline, the
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
    private static int measuredGuiScale = -1;
    private static boolean measuredUnicode;

    final String windowId;
    final ChatLineBands bands = new ChatLineBands();
    final ChatChannelTabBar tabBar = new ChatChannelTabBar();
    /**
     * Where every row of {@link #lines} starts and how tall it is, the
     * divider's row included; see {@link #resolveRows}. The scroll
     * range, the scrollbar, the window's own height and the draw all
     * measure the stack through this.
     */
    final ChatStackRows rows = new ChatStackRows();
    /** The view's line list drawn last; the bands index into it. */
    List<ChatLine> lines = Collections.emptyList();
    /**
     * Index in {@link #lines} of the oldest wrapped row of the message
     * the unread divider stands above, or -1 while this view shows no
     * divider. The divider takes a row of the stack, its line and the
     * gaps either side of it, so it is part of the window's
     * content height and not only of its drawing: the
     * scroll range, the scrollbar and the draw all read it here, which
     * is what keeps the room a view can reach and the rows it actually
     * renders the same measurement.
     */
    int dividerLineIndex = -1;
    /**
     * Index in {@link #lines} of a day's rule standing over the first
     * unread message, with only the rule's own gap between them, or -1.
     * That row carries the unread
     * divider then — it reads as the divider, in the divider's crimson,
     * until the divider goes and the date is back on it — and
     * {@link #dividerLineIndex} is -1 for it, so the two never stand
     * together and no row of its own is added for the divider.
     */
    int dividerDateLineIndex = -1;
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
     * edge the tab row stands a padding above. An open window's is its
     * box's own edge whatever its tab in front holds — a full window
     * cuts its topmost line there, and a shorter history leaves empty
     * rows under it — so switching tabs never moves the row; the closed
     * feed's sits on the lines it has.
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
     * How far the window has come along its glide to the part of the
     * screen it fills, or back to its own box, eased from the moment it
     * was sent, so its box glides rather than jumping. Every glide is
     * one leg from the box drawn as it began ({@link #fillLegFrom},
     * null for the window's own box) to what it is bound for
     * ({@link #fillLegTo}, none for the window's own box).
     */
    private final LostTalesUiTransition fillMotion =
            new LostTalesUiTransition();
    /** Whether {@link #fillMotion} has been advanced at all yet. */
    private boolean fillSeen;
    private ChatWindowPlacement.Box fillLegFrom;
    private ChatWindow.ScreenFill fillLegFromFill = ChatWindow.ScreenFill.NONE;
    private ChatWindow.ScreenFill fillLegTo = ChatWindow.ScreenFill.NONE;
    /** The box the window was last laid in ({@link #begin}). */
    private ChatWindowPlacement.Box placed;
    /**
     * The part of the screen a drag in progress would snap the window
     * to on release, shown by the window itself: it takes that part
     * while the pointer is in the edge's zone and comes back under the
     * pointer when it leaves, on the same glide, and is filled for good
     * only when the button comes up.
     */
    ChatWindow.ScreenFill dragPreview = ChatWindow.ScreenFill.NONE;
    /**
     * How long the row still counts a finished glide as gliding. The
     * frame a glide settles on moves the edges its last step, and the
     * tabs have to take that step with them rather than ease after it.
     */
    private static final long GLIDE_TAIL_NANOS = 100L * 1000000L;
    /** When an advance last found the glide still moving; 0 before one has. */
    private long fillGlideNanos;
    /**
     * The timestamps drawn this frame, and the delivery marks, each with
     * the chat line id it belongs to, in screen GUI pixels. Recorded from
     * the draw itself, like the toolbar, so the tip either shows answers
     * exactly where it stands.
     */
    private final LineBoxes stamps = new LineBoxes();
    private final LineBoxes marks = new LineBoxes();
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
    /**
     * The button's fly-in from below the rule, eased over the chat's
     * animation duration like every other glide, so it arrives rather
     * than creeping to a stop.
     */
    final LostTalesUiTransition jumpMotion = new LostTalesUiTransition();
    /**
     * The hovered message's toolbar as drawn this frame, in screen GUI
     * pixels; width zero while none was drawn. Recorded from the draw
     * itself, like the jump button, so the click and the pixels cannot
     * disagree. {@link #toolbarKinds} says which control each button
     * is, left to right, one {@link #toolbarStride} apart, so a message
     * offering fewer of them needs no separate bookkeeping.
     */
    float toolbarLeft;
    float toolbarTop;
    float toolbarRight;
    float toolbarBottom;
    float toolbarStride;
    int[] toolbarKinds = NO_KINDS;
    /** The message the toolbar belongs to, by chat line id. */
    int toolbarChatLineId;
    /**
     * The toolbar control the pointer is on this frame, by kind, or -1:
     * set from the screen's one answer about the pointer before the
     * windows are drawn ({@link #noteHoveredControls}).
     */
    int hoveredToolbarKind = -1;
    /** How far each toolbar control has lit, by kind, and on whose toolbar. */
    private final float[] toolbarFades = new float[4];
    private int toolbarFadesLineId;
    long toolbarFadeNanos;

    private static final int[] NO_KINDS = new int[0];

    /** Whether the point lies on the toolbar drawn this frame. */
    boolean toolbarContains(double x, double y) {
        return this.drawn && this.toolbarKinds.length > 0
                && ChatHitBox.contains(x, y, this.toolbarLeft, this.toolbarTop,
                        this.toolbarRight - this.toolbarLeft,
                        this.toolbarBottom - this.toolbarTop);
    }

    /** The control under the point, or -1 when the point is not on one. */
    int toolbarKindAt(double x, double y) {
        if (!toolbarContains(x, y)) {
            return -1;
        }
        // The gap after a button belongs to it, so the toolbar has no
        // dead pixels between its buttons.
        int index = (int)((x - this.toolbarLeft)
                / Math.max(1.0F, this.toolbarStride));
        return this.toolbarKinds[Math.max(0,
                Math.min(this.toolbarKinds.length - 1, index))];
    }
    /**
     * Starts the toolbar's fades afresh when it has moved to another
     * message, so a control lit on one message's toolbar does not come
     * up lit on the next one's.
     */
    void startToolbarFades(int chatLineId) {
        if (chatLineId != this.toolbarFadesLineId) {
            Arrays.fill(this.toolbarFades, 0.0F);
            this.toolbarFadesLineId = chatLineId;
        }
    }

    /** Advances one toolbar control's fade and answers how far it has lit. */
    float toolbarFade(int kind, double elapsed) {
        if (kind < 0 || kind >= this.toolbarFades.length) {
            return 0.0F;
        }
        this.toolbarFades[kind] = LostTalesChatVisualStyle.hoverFade(
                this.toolbarFades[kind], kind == this.hoveredToolbarKind,
                elapsed);
        return this.toolbarFades[kind];
    }

    /** Whether the pointer is on the jump-to-present button this frame. */
    boolean jumpHovered;
    /** How far the jump-to-present button has lit, and when it last moved. */
    float jumpFade;
    long jumpFadeNanos;
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
    boolean scrollbarContains(double x, double y) {
        return this.drawn && this.scrollbarRight > this.scrollbarLeft
                && x >= this.scrollbarLeft && x < this.scrollbarRight
                && y >= this.scrollbarTrackTop
                && y < this.scrollbarTrackBottom;
    }

    /** Whether the point lies on the scrollbar's thumb drawn this frame. */
    boolean scrollbarThumbContains(double x, double y) {
        return scrollbarContains(x, y) && y >= this.scrollbarThumbTop
                && y < this.scrollbarThumbBottom;
    }

    /** Whether the point lies on the pill drawn this frame. */
    boolean jumpPillContains(double x, double y) {
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

    /** The frontmost drawn window under a screen point, or null. */
    static ChatWindowFrame drawnAt(double mouseX, double mouseY) {
        List<ChatWindowFrame> frames = drawnFrames();
        for (int index = frames.size() - 1; index >= 0; index--) {
            if (frames.get(index).contains(mouseX, mouseY)) {
                return frames.get(index);
            }
        }
        return null;
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
    /**
     * Notes which of the windows' floating controls the pointer is on
     * this frame — a toolbar control by kind, or a jump-to-present
     * button — and that it is on none of the others.
     */
    static synchronized void noteHoveredControls(
            ChatWindowFrame toolbarFrame, int toolbarKind,
            ChatWindowFrame jumpFrame) {
        for (ChatWindowFrame frame : FRAMES.values()) {
            frame.hoveredToolbarKind = -1;
            frame.jumpHovered = false;
        }
        if (toolbarFrame != null) {
            toolbarFrame.hoveredToolbarKind = toolbarKind;
        }
        if (jumpFrame != null) {
            jumpFrame.jumpHovered = true;
        }
    }

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
        FEED.dividerDateLineIndex = -1;
        FEED.dividerSource = null;
        FEED.dividerSourceSize = 0;
        FEED.dividerSourceLineId = 0;
        FEED.rows.reset((List<ChatLine>)null, -1);
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
                // A conversation the chat is not being read as is out
                // of the feed as well as off the row: hiding the tab and
                // still showing its lines would say two things at once.
                if (tabs.get(tab).isWhisper()
                        && ClientChatChannelState.isAvailable(tabs.get(tab))
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
        this.placed = box;
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
        // The stack fills the box until the draw lays it on the drawn
        // baseline.
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
     * Display pixels per GUI pixel. It is asked several times for every
     * window of every frame, so it is measured once for each display
     * size, GUI Scale option and font choice — everything the answer is
     * made of. The option and the font change it with the window left
     * as it is; without them in the key, everything laid on display
     * pixels would land between them after a scale change.
     */
    static int displayScaleFactor() {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft == null || minecraft.displayWidth <= 0
                || minecraft.displayHeight <= 0
                || minecraft.gameSettings == null) {
            return 1;
        }
        int guiScale = minecraft.gameSettings.guiScale;
        boolean unicode = minecraft.func_152349_b();
        if (minecraft.displayWidth == measuredWidth
                && minecraft.displayHeight == measuredHeight
                && guiScale == measuredGuiScale
                && unicode == measuredUnicode) {
            return measuredFactor;
        }
        try {
            measuredFactor = Math.max(1, new ScaledResolution(minecraft,
                    minecraft.displayWidth,
                    minecraft.displayHeight).getScaleFactor());
            measuredWidth = minecraft.displayWidth;
            measuredHeight = minecraft.displayHeight;
            measuredGuiScale = guiScale;
            measuredUnicode = unicode;
        } catch (RuntimeException unavailable) {
            return 1;
        }
        return measuredFactor;
    }

    /**
     * Moves the window's fill motion on to this instant, toward the part
     * of the screen the window fills. Called once a frame before the
     * window is measured; the first call stands the window in its state
     * rather than travelling into it. A change of fill starts a new
     * leg from the box drawn last — the window's own box, the part of
     * the screen it filled, or wherever a glide had brought it — so a
     * window sent from one fill to another sets out from where it
     * stands; a leg back to the window's own box forgets its start once
     * it arrives, so the box is the window's own again exactly.
     */
    void advanceFill(ChatWindow.ScreenFill fill) {
        long now = System.nanoTime();
        ChatWindow.ScreenFill wanted = fill == null
                || fill == ChatWindow.ScreenFill.NONE ? this.dragPreview : fill;
        if (this.fillSeen && !this.fillMotion.isSettled()) {
            this.fillGlideNanos = now;
        }
        if (this.fillSeen && wanted != this.fillLegTo) {
            this.fillLegFromFill = this.fillLegTo;
            this.fillLegFrom = this.placed;
            this.fillLegTo = wanted;
            this.fillMotion.settle(false);
        } else if (!this.fillSeen) {
            this.fillLegTo = wanted;
        }
        this.fillSeen = true;
        this.fillMotion.advance(now, true,
                LostTalesConfig.enableChatAnimations
                        ? Math.max(1, LostTalesConfig
                                .chatAnimationDurationMillis)
                        : 0,
                LostTalesUiEasing.SMOOTH);
        if (this.fillLegTo == ChatWindow.ScreenFill.NONE
                && this.fillMotion.isSettled()) {
            this.fillLegFrom = null;
            this.fillLegFromFill = ChatWindow.ScreenFill.NONE;
        }
    }

    /** Whether {@link #advanceFill} has placed the window at all yet. */
    boolean hasSeenFill() {
        return this.fillSeen;
    }

    /** The box the running leg set out from, or null for the window's own. */
    ChatWindowPlacement.Box fillLegFrom() {
        return this.fillLegFrom;
    }

    /** What the running leg is bound for; none for the window's own box. */
    ChatWindow.ScreenFill fillLegTo() {
        return this.fillLegTo;
    }

    /** How far along its leg the window has come, 0..1. */
    float fillShare() {
        return this.fillMotion.clamped();
    }

    /**
     * How far the window stands toward filling the whole screen, 0..1:
     * what the fullscreen control's glyph crosses over by, so it points
     * inward exactly as the window takes the whole screen and outward
     * as it gives it back — or fills a half or a quarter instead.
     */
    float fullShare() {
        if (this.fillLegTo == ChatWindow.ScreenFill.FULL) {
            return this.fillMotion.clamped();
        }
        return this.fillLegFromFill == ChatWindow.ScreenFill.FULL
                ? 1.0F - this.fillMotion.clamped() : 0.0F;
    }

    /**
     * Whether the window stands in its own box, and not in a part of
     * the screen or on the way to or from one. Its edges are its own to
     * resize only then.
     */
    boolean isInOwnBox() {
        return !this.fillSeen || (this.fillLegTo == ChatWindow.ScreenFill.NONE
                && this.fillLegFrom == null);
    }

    /**
     * Whether the window is gliding to or from a part of the screen, the
     * frame the glide settles on and a moment after it included.
     */
    boolean isFillGliding() {
        return this.fillSeen && (!this.fillMotion.isSettled()
                || System.nanoTime() - this.fillGlideNanos
                        < GLIDE_TAIL_NANOS);
    }

    /** Forgets the stamps of the frame before; the draw records its own. */
    void clearStamps() {
        this.stamps.clear();
    }

    /** Records one stamp as drawn, in screen GUI pixels. */
    void recordStamp(float left, float top, float right, float bottom,
                     int chatLineId) {
        this.stamps.add(left, top, right, bottom, chatLineId);
    }

    /** The chat line id of the stamp drawn under the point, or 0. */
    int stampLineAt(double x, double y) {
        return this.drawn ? this.stamps.at(x, y) : 0;
    }

    /** Forgets the delivery marks of the frame before. */
    void clearMarks() {
        this.marks.clear();
    }

    /** Records one delivery mark as drawn, in screen GUI pixels. */
    void recordMark(float left, float top, float right, float bottom,
                    int chatLineId) {
        this.marks.add(left, top, right, bottom, chatLineId);
    }

    /** The chat line id of the delivery mark drawn under the point, or 0. */
    int markLineAt(double x, double y) {
        return this.drawn ? this.marks.at(x, y) : 0;
    }

    /**
     * Boxes a draw puts on screen, four edges to a box — left, top, right,
     * bottom — each answering for one chat line.
     */
    private static final class LineBoxes {
        private float[] boxes = new float[32];
        private int[] lines = new int[8];
        private int count;

        void clear() {
            this.count = 0;
        }

        void add(float left, float top, float right, float bottom,
                 int chatLineId) {
            if (right <= left || bottom <= top) {
                return;
            }
            if (this.count == this.lines.length) {
                this.lines = Arrays.copyOf(this.lines, this.count * 2);
                this.boxes = Arrays.copyOf(this.boxes, this.count * 8);
            }
            int at = this.count * 4;
            this.boxes[at] = left;
            this.boxes[at + 1] = top;
            this.boxes[at + 2] = right;
            this.boxes[at + 3] = bottom;
            this.lines[this.count++] = chatLineId;
        }

        /** The chat line id of the box under the point, or 0. */
        int at(double x, double y) {
            for (int index = 0; index < this.count; index++) {
                int at = index * 4;
                if (x >= this.boxes[at] && x < this.boxes[at + 2]
                        && y >= this.boxes[at + 1]
                        && y < this.boxes[at + 3]) {
                    return this.lines[index];
                }
            }
            return 0;
        }
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
        this.dividerDateLineIndex = -1;
        // A day's rule standing over the first unread message, past the
        // rule's own gap, carries the divider instead.
        int rule = this.dividerLineIndex < 0 ? -1
                : ChatWindowLines.dateDividerOver(drawnLines,
                        this.dividerLineIndex);
        if (rule >= 0) {
            this.dividerDateLineIndex = rule;
            this.dividerLineIndex = -1;
        }
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

    /**
     * Lays the stack's rows out for the current line list and divider,
     * when either has changed since the last draw. Called once the
     * lines and the divider are known and before anything measures the
     * stack: the window's own height, the scroll hold and the clamp.
     */
    void resolveRows() {
        int size = this.lines == null ? 0 : this.lines.size();
        if (!this.rows.describes(this.lines, size, this.dividerLineIndex)) {
            this.rows.reset(this.lines, this.dividerLineIndex);
        }
    }

    /** Whether {@link #rows} describes the stack as it stands. */
    private boolean rowsResolved() {
        int size = this.lines == null ? 0 : this.lines.size();
        return this.rows.describes(this.lines, size, this.dividerLineIndex)
                && this.rows.count() == contentRows();
    }

    /**
     * Height of the whole stack in the chat's own (unscaled) pixels,
     * every row at its own height ({@link ChatStackRows}): blank rows
     * shorter than a line, the unread divider's row taller. Before the
     * rows are resolved every row counts as a whole line.
     */
    double contentHeight() {
        if (rowsResolved()) {
            return this.rows.total();
        }
        return contentRows() * (double)LostTalesChatOverlayRenderer.LINE_HEIGHT;
    }

    /** {@link #contentHeight} in lines and fractions of one. */
    double contentLines() {
        return contentHeight() / LostTalesChatOverlayRenderer.LINE_HEIGHT;
    }

    /** The window's message room in the chat's own (unscaled) pixels. */
    private double roomUnscaled() {
        return this.room / Math.max(1.0F, this.scale);
    }

    /**
     * Rows of content the window has room for, fractions included,
     * measured where the scroll ceiling is: from the oldest row down.
     * Rows are not all one height, so the room holds a different
     * number of them at every offset; taken at the top of the stack,
     * {@code contentRows - roomLines} is exactly the offset that puts
     * the oldest row on the window's top edge, which is what the clamp
     * needs. A stack shorter than the room answers with all its rows,
     * so the view cannot scroll into nothing.
     */
    double roomLines() {
        if (!rowsResolved()) {
            return this.room / (double)Math.max(1.0F,
                    LostTalesChatOverlayRenderer.LINE_HEIGHT * this.scale);
        }
        int count = this.rows.count();
        return count - this.rows.rowsAt(this.rows.total() - roomUnscaled());
    }

    /**
     * The furthest the view may scroll: the offset that stands the
     * oldest row on the window's top edge, which is what the clamp
     * holds every offset to. Unbounded while the window has not been
     * measured, so nothing is clamped against a room of nothing.
     */
    double scrollCeiling() {
        if (this.room <= 0.0D) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0D, contentRows() - Math.max(1.0D, roomLines()));
    }

    /**
     * The scroll offset, in rows, that stands {@code pixels} of stack
     * further up than {@code rows} does: what a wheel turn asks for, so
     * a turn moves the page the same distance whatever rows it passes —
     * a blank row between two runs is shorter than a line, and counting
     * it as a whole row would make the wheel stumble over every group.
     */
    double rowsAfterScrolling(double rows, double pixels) {
        if (!rowsResolved()) {
            return Math.max(0.0D, rows
                    + pixels / LostTalesChatOverlayRenderer.LINE_HEIGHT);
        }
        return this.rows.rowsAt(this.rows.offsetOf(rows) + pixels);
    }

    /**
     * The scroll offset, in rows, that stands {@code share} of the way
     * from the newest row to the ceiling, measured in pixels of stack:
     * what a scrollbar drag asks for, so the thumb follows the pointer
     * whatever the rows under it measure.
     */
    double scrollRowsForShare(double share) {
        double bounded = Math.max(0.0D, Math.min(1.0D, share));
        if (!rowsResolved()) {
            return bounded * Math.max(0.0D, contentRows() - roomLines());
        }
        double reach = Math.max(0.0D, this.rows.total() - roomUnscaled());
        return this.rows.rowsAt(bounded * reach);
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
     * its own and does not ride the window's opening motion.
     */
    double barTop() {
        return this.restingBarTop;
    }

    /**
     * Bottom of the tab row (screen y, fractional): the tool strip and
     * the top margin above the drawn message stack — the row's last
     * pixel row is the window's top rule, the tool strip hangs under it,
     * and the first content pixel lies the margin below that, so the
     * topmost line keeps clear of the strip. The stack of an open window
     * ends on its box's own edge, so the row and the box top never drift
     * apart whatever the chat scale is or the tab in front holds.
     */
    double tabRowBottom() {
        return historyTop() - ChatWindowPlacement.TOOL_STRIP_HEIGHT;
    }

    /**
     * Top of the history's panel (screen y, fractional): the margin
     * above the drawn message stack, under the tool strip.
     */
    double historyTop() {
        return this.stackTop - ChatWindowPlacement.HISTORY_TOP_MARGIN;
    }

    /**
     * Whether the point lies within the window as it is drawn — its
     * strip, its messages and its bar, opening motion included — from
     * its first pixel up to the pixel past its last, as everything it
     * draws is measured. The border outside it is the resize band's.
     */
    boolean contains(double x, double y) {
        return ChatHitBox.contains(x, y, drawnLeft(),
                this.boxTop + this.motionY, this.boxRight - this.boxLeft,
                this.boxBottom - this.boxTop);
    }
}
