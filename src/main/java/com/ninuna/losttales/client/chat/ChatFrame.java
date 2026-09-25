package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.motion.MotionIds;
import com.ninuna.losttales.client.motion.MotionTransition;
import com.ninuna.losttales.client.motion.Motions;
import com.ninuna.losttales.client.window.Window;
import com.ninuna.losttales.client.window.WindowFrame;
import com.ninuna.losttales.client.window.WindowPlacement;
import com.ninuna.losttales.gui.style.LostTalesUiButtonMotion;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.gui.ChatLine;

/**
 * A window's frame as the chat keeps it: besides the box, the row and
 * the motion every window has, the line bands the last draw recorded,
 * the line list they index into, the stack's rows and their glide, the
 * unread divider, the member list, the message toolbar, the scrollbar
 * and the jump-to-present button. The closed feed has one of its own.
 */
public final class ChatFrame extends WindowFrame {
    ChatFrame(String windowId) {
        super(windowId);
    }

    /** Makes every window's frame a chat frame; before any frame is asked for. */
    static void install() {
        WindowFrame.setMaker(new WindowFrame.Maker() {
            @Override
            public WindowFrame make(String windowId) {
                return new ChatFrame(windowId);
            }
        });
    }

    /** Every drawn window's frame, back to front. */
    public static List<ChatFrame> drawn() {
        List<ChatFrame> frames = new java.util.ArrayList<ChatFrame>();
        for (WindowFrame frame : WindowFrame.drawnFrames()) {
            frames.add((ChatFrame)frame);
        }
        return frames;
    }

    @Override
    public void begin(WindowPlacement.Box box, float chatScale,
                      float openingMotionX, float openingMotionY) {
        super.begin(box, chatScale, openingMotionX, openingMotionY);
        this.renderedScrollLines = 0.0D;
    }

    /** A page is no conversation: the window holds no lines, no member list and no marks. */
    @Override
    protected void pageShown() {
        this.view = null;
        this.lines = Collections.<ChatLine>emptyList();
        this.renderedScrollLines = 0.0D;
        this.historyMoving = false;
        this.members.clearDrawn();
        clearMarks();
        clearAvatars();
    }

    public static ChatFrame of(Window window) {
        return (ChatFrame)WindowFrame.of(window);
    }

    public static ChatFrame find(String windowId) {
        return (ChatFrame)WindowFrame.find(windowId);
    }

    /** The frontmost drawn window under a screen point, or null. */
    public static ChatFrame drawnAt(double mouseX, double mouseY) {
        return (ChatFrame)WindowFrame.drawnAt(mouseX, mouseY);
    }

    public final ChatLineBands bands = new ChatLineBands();

    /** The window's member list: its scroll, and where its rows stood. */
    public final ChatMemberList.State members = new ChatMemberList.State();

    /**
     * Where every row of {@link #lines} starts and how tall it is, the
     * divider's row included; see {@link #resolveRows}. The scroll
     * range, the scrollbar, the window's own height and the draw all
     * measure the stack through this.
     */
    public final ChatStackRows rows = new ChatStackRows();

    /**
     * How the rows of {@link #rows} move when its lines are laid out
     * again: each glides from where it was drawn to its new place.
     */
    public final ChatRowGlide glide = new ChatRowGlide();

    /** The view's line list drawn last; the bands index into it. */
    public List<ChatLine> lines = Collections.emptyList();

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
    public int dividerLineIndex = -1;

    /**
     * Index in {@link #lines} of a day's rule standing over the first
     * unread message, with only the rule's own gap between them, or -1.
     * That row carries the unread
     * divider then — it reads as the divider, in the divider's crimson,
     * until the divider goes and the date is back on it — and
     * {@link #dividerLineIndex} is -1 for it, so the two never stand
     * together and no row of its own is added for the divider.
     */
    public int dividerDateLineIndex = -1;

    /** What {@link #dividerLineIndex} was worked out from. */
    private List<ChatLine> dividerSource;

    private int dividerSourceSize;

    private int dividerSourceLineId;

    /** The conversation shown while open; null for the closed-chat feed and for a page. */
    public ChatTab view;

    /**
     * The scroll offset the window was drawn at this frame, in lines.
     * The typing line reads it: the trailing strip belongs to the
     * history while the view is scrolled back, and to the typing line
     * only while the view rests on the newest message.
     */
    public double renderedScrollLines;

    /** Whether the history is still on its way to where it was scrolled this frame. */
    public boolean historyMoving;

    /** The timestamp area driving in and out: 1 while it stands whole. */
    private final MotionTransition areaMotion =
            new MotionTransition(MotionIds.CHAT_WINDOW_AREA, true);

    /** The member list coming out and going away: 1 while it stands whole. */
    private final MotionTransition membersMotion =
            new MotionTransition(MotionIds.CHAT_WINDOW_MEMBERS, true);

    /**
     * The delivery marks drawn this frame, each with the chat line id it
     * belongs to, in screen GUI pixels. Recorded from the draw itself,
     * like the toolbar, so the tip a mark shows answers exactly where it
     * stands.
     */
    private final LineBoxes marks = new LineBoxes();

    /** The avatars drawn this frame, each with its message's chat line id. */
    private final LineBoxes avatars = new LineBoxes();

    /**
     * The jump-to-present button drawn this frame, in screen GUI
     * pixels; width zero while none was drawn. Recorded from the draw
     * itself, like the bands, so the click and the pixels cannot
     * disagree.
     */
    public float jumpPillLeft;

    public float jumpPillTop;

    public float jumpPillRight;

    public float jumpPillBottom;

    /**
     * The button's fly-in from below the rule, eased on its motion like
     * every other glide, so it arrives rather than creeping to a stop.
     */
    public final MotionTransition jumpMotion =
            new MotionTransition(MotionIds.CHAT_JUMP_SHOW, true);

    /**
     * The hovered message's toolbar as drawn this frame, in screen GUI
     * pixels; width zero while none was drawn. Recorded from the draw
     * itself, like the jump button, so the click and the pixels cannot
     * disagree. Its controls stand side by side from
     * {@link #toolbarCellsLeft}, one {@link #toolbarCellWidth} each, in
     * the order {@link #toolbarKinds} gives, and {@link #toolbarWhy} says
     * of each why it cannot be taken on this message, or nothing.
     */
    public float toolbarLeft;

    public float toolbarTop;

    public float toolbarRight;

    public float toolbarBottom;

    public float toolbarCellsLeft;

    public float toolbarCellWidth;

    public int[] toolbarKinds = NO_KINDS;

    public String[] toolbarWhy = NO_REASONS;

    /** Why the control {@code kind} cannot be taken on the toolbar's message, or empty. */
    public String toolbarWhy(int kind) {
        for (int index = 0; index < this.toolbarKinds.length
                && index < this.toolbarWhy.length; index++) {
            if (this.toolbarKinds[index] == kind) {
                return this.toolbarWhy[index];
            }
        }
        return "";
    }

    /** The message the toolbar belongs to, by chat line id. */
    public int toolbarChatLineId;

    /**
     * The toolbar control the pointer is on this frame, by kind, or -1:
     * set from the screen's one answer about the pointer before the
     * windows are drawn ({@link #noteHoveredControls}).
     */
    public int hoveredToolbarKind = -1;

    /**
     * A beat per toolbar control, by kind. The react face, the reply
     * arrow, the link and the menu's dots rise; copying is a decisive
     * act, so its glyph answers like a switch. Kept per window rather
     * than per message, and started afresh when the toolbar moves to
     * another line.
     */
    private final LostTalesUiButtonMotion[] toolbarMotions =
            newToolbarMotions();

    private static LostTalesUiButtonMotion[] newToolbarMotions() {
        LostTalesUiButtonMotion[] motions = new LostTalesUiButtonMotion[
                LostTalesChatOverlayRenderer.TOOLBAR_KINDS.length + 1];
        for (int kind = 0; kind < motions.length; kind++) {
            motions[kind] = new LostTalesUiButtonMotion(
                    kind == LostTalesChatOverlayRenderer.TOOLBAR_COPY
                            ? LostTalesUiButtonMotion.Character.SNAP
                            : LostTalesUiButtonMotion.Character.LIFT);
        }
        return motions;
    }

    /** The message the beats above belong to; another one starts them afresh. */
    private int toolbarFadesLineId;

    /**
     * The toolbar coming up: hidden while the history moves under the
     * pointer, and brought in once it has rested on a message.
     */
    private final MotionTransition toolbarShow =
            new MotionTransition(MotionIds.CHAT_TOOLBAR_SHOW);

    /** Whether the toolbar waits for the pointer to rest before it comes up again. */
    private boolean toolbarWaiting;

    /** Since when the pointer has rested on {@link #toolbarRestLineId}; 0 while it has not. */
    private long toolbarRestSince;

    private int toolbarRestLineId;

    private static final int[] NO_KINDS = new int[0];

    private static final String[] NO_REASONS = new String[0];

    /** Whether the point lies on the toolbar drawn this frame. */
    boolean toolbarContains(double x, double y) {
        return this.drawn && this.toolbarKinds.length > 0
                && LostTalesUiHitBox.contains(x, y, this.toolbarLeft, this.toolbarTop,
                        this.toolbarRight - this.toolbarLeft,
                        this.toolbarBottom - this.toolbarTop);
    }

    /**
     * The control under the point, or -1 when the point is not on the
     * toolbar. The frame's edge belongs to the control it runs beside,
     * so the toolbar has no dead pixels.
     */
    public int toolbarKindAt(double x, double y) {
        if (!toolbarContains(x, y)) {
            return -1;
        }
        int index = (int)Math.floor((x - this.toolbarCellsLeft)
                / Math.max(0.001F, this.toolbarCellWidth));
        return this.toolbarKinds[Math.max(0,
                Math.min(this.toolbarKinds.length - 1, index))];
    }

    /**
     * Where the square of the control {@code kind} starts on screen, as
     * drawn this frame; the toolbar's own left for a control it does not
     * hold.
     */
    public float toolbarCellLeft(int kind) {
        for (int index = 0; index < this.toolbarKinds.length; index++) {
            if (this.toolbarKinds[index] == kind) {
                return this.toolbarCellsLeft + index * this.toolbarCellWidth;
            }
        }
        return this.toolbarLeft;
    }

    /**
     * How far the toolbar over {@code hoveredLineId} has come up this
     * frame, 0 to 1. While {@code historyMoving} the toolbar stays away,
     * so it never jumps from message to message under a still pointer;
     * once the history rests, it comes up after the pointer has rested
     * on one message for the motion's rest, and fades in. Moving the
     * pointer from message to message without a scroll moves it at once.
     */
    public float toolbarShare(int hoveredLineId, boolean historyMoving, long nowNanos) {
        if (historyMoving) {
            this.toolbarWaiting = true;
            this.toolbarRestSince = 0L;
            this.toolbarShow.settle(false);
            return 0.0F;
        }
        if (this.toolbarWaiting) {
            if (hoveredLineId == 0 || hoveredLineId != this.toolbarRestLineId
                    || this.toolbarRestSince == 0L) {
                this.toolbarRestLineId = hoveredLineId;
                this.toolbarRestSince = hoveredLineId == 0 ? 0L : nowNanos;
                return 0.0F;
            }
            long rest = (long)(Motions.param(MotionIds.CHAT_TOOLBAR_SHOW,
                    "rest", 150.0F) * 1000000.0F);
            if (nowNanos - this.toolbarRestSince < rest) {
                return 0.0F;
            }
            this.toolbarWaiting = false;
        }
        return this.toolbarShow.advance(nowNanos, true);
    }

    /**
     * Starts the toolbar's fades afresh when it has moved to another
     * message, so a control lit on one message's toolbar does not come
     * up lit on the next one's.
     */
    public void startToolbarFades(int chatLineId) {
        if (chatLineId != this.toolbarFadesLineId) {
            System.arraycopy(newToolbarMotions(), 0, this.toolbarMotions, 0,
                    this.toolbarMotions.length);
            this.toolbarFadesLineId = chatLineId;
        }
    }

    /**
     * Steps one toolbar control's beat and answers it, so the control is
     * drawn in the pose it has reached.
     */
    public LostTalesUiButtonMotion toolbarMotion(int kind, long nowNanos) {
        if (kind < 0 || kind >= this.toolbarMotions.length) {
            return this.toolbarMotions[0];
        }
        boolean on = kind == this.hoveredToolbarKind;
        this.toolbarMotions[kind].advance(nowNanos, on);
        return this.toolbarMotions[kind];
    }

    /** Whether the pointer is on the jump-to-present button this frame. */
    public boolean jumpHovered;

    /**
     * The jump-to-present button's beat. It is a framed button, so its
     * frame keeps its place and the chevron inside it moves.
     */
    public final LostTalesUiButtonMotion jumpButtonMotion =
            new LostTalesUiButtonMotion(
                    LostTalesUiButtonMotion.Character.LIFT);

    /**
     * The scrollbar's thumb as drawn this frame, in screen GUI pixels;
     * width zero while none was drawn. The track it slides in is the
     * message area's own height, recorded with it so a drag can map the
     * pointer onto the history without measuring the window again.
     */
    public float scrollbarLeft;

    public float scrollbarRight;

    public float scrollbarTrackTop;

    public float scrollbarTrackBottom;

    public float scrollbarThumbTop;

    public float scrollbarThumbBottom;

    /** How far the bar has faded in while the pointer rests here, 0..1. */
    public float scrollbarProgress;

    public long scrollbarNanos;

    /** Whether the pointer is in this window, so the bar should show. */
    public boolean scrollbarWanted;

    /** Whether the point lies on the scrollbar's track drawn this frame. */
    public boolean scrollbarContains(double x, double y) {
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
    public boolean jumpPillContains(double x, double y) {
        return this.drawn && this.jumpPillRight > this.jumpPillLeft
                && x >= this.jumpPillLeft && x < this.jumpPillRight
                && y >= this.jumpPillTop && y < this.jumpPillBottom;
    }

    /**
     * Notes which of the windows' floating controls the pointer is on
     * this frame — a toolbar control by kind, or a jump-to-present
     * button — and that it is on none of the others.
     */
    public static synchronized void noteHoveredControls(
            ChatFrame toolbarFrame, int toolbarKind,
            ChatFrame jumpFrame) {
        for (WindowFrame each : frames()) {
            ChatFrame frame = (ChatFrame)each;
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

    /**
     * Moves the timestamp area's and the member list's motions on to
     * this instant, toward what the window asks of them — the list only
     * while the window shows a conversation. Called once a frame before
     * the window is laid out; the first call stands them in their state.
     */
    public void advancePanels(Window window, boolean showsConversation) {
        if (window == null) {
            return;
        }
        long now = System.nanoTime();
        this.areaMotion.advance(now, !ChatLayout.isAreaHidden(window));
        this.membersMotion.advance(now, !ChatLayout.isMembersHidden(window)
                && showsConversation);
    }

    /** How far the timestamp area stands in the window, 0..1. */
    public float areaShare() {
        return this.areaMotion.clamped();
    }

    /** How far the member list stands in the window, 0..1. */
    public float membersShare() {
        return this.membersMotion.clamped();
    }

    /** Forgets the delivery marks of the frame before; the draw records its own. */
    public void clearMarks() {
        this.marks.clear();
    }

    /** Records one delivery mark as drawn, in screen GUI pixels. */
    public void recordMark(float left, float top, float right, float bottom,
                    int chatLineId) {
        this.marks.add(left, top, right, bottom, chatLineId);
    }

    /** The chat line id of the delivery mark drawn under the point, or 0. */
    public int markLineAt(double x, double y) {
        return this.drawn ? this.marks.at(x, y) : 0;
    }

    /** Forgets the avatars of the frame before. */
    public void clearAvatars() {
        this.avatars.clear();
    }

    /**
     * Records where one avatar answers the pointer, in screen GUI pixels:
     * its square, its sphere included.
     */
    public void recordAvatar(float left, float top, float right, float bottom,
                      int chatLineId) {
        this.avatars.add(left, top, right, bottom, chatLineId);
    }

    /** The chat line id of the message whose avatar is under the point, or 0. */
    public int avatarLineAt(double x, double y) {
        return this.drawn ? this.avatars.at(x, y) : 0;
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

    /**
     * Works out where this view's unread divider falls in the line list
     * and records it. The answer is kept until the list, its length or
     * the divider's message changes: it is asked for every window of
     * every frame, and the scan is only worth paying for when one of
     * those has moved.
     */
    public void resolveDividerRow(List<ChatLine> drawnLines, Integer dividerLine) {
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
    public int contentRows() {
        return (this.lines == null ? 0 : this.lines.size())
                + (this.dividerLineIndex >= 0 ? 1 : 0);
    }

    /**
     * Lays the stack's rows out for the current line list and divider,
     * when either has changed since the last draw. Called once the
     * lines and the divider are known and before anything measures the
     * stack: the window's own height, the scroll hold and the clamp.
     */
    public void resolveRows() {
        int size = this.lines == null ? 0 : this.lines.size();
        boolean open = !isFeed();
        if (!this.rows.describes(this.lines, size, this.dividerLineIndex,
                open)) {
            this.rows.reset(this.lines, this.dividerLineIndex, open);
            this.glide.relaid(this.lines, this.rows, this.dividerLineIndex,
                    System.nanoTime());
        }
    }

    /**
     * Whether this is the closed feed's frame rather than a window's:
     * the feed draws a message at sizes of its own, so its rows are laid
     * out for the feed.
     */
    boolean isFeed() {
        return this == FEED;
    }

    /** Whether {@link #rows} describes the stack as it stands. */
    private boolean rowsResolved() {
        int size = this.lines == null ? 0 : this.lines.size();
        return this.rows.describes(this.lines, size, this.dividerLineIndex,
                !isFeed())
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
    public double roomLines() {
        if (!rowsResolved()) {
            return this.room / (double)Math.max(1.0F,
                    LostTalesChatOverlayRenderer.LINE_HEIGHT * this.scale);
        }
        int count = this.rows.count();
        return count - this.rows.rowsAt(this.rows.total() - roomUnscaled());
    }

    /**
     * Pixels of stack a page key moves: the window's room less a line,
     * so the line at the edge a page leaves is the first of the next,
     * and never less than a line.
     */
    public double pagePixels() {
        return Math.max(LostTalesChatOverlayRenderer.LINE_HEIGHT,
                roomUnscaled() - LostTalesChatOverlayRenderer.LINE_HEIGHT);
    }

    /**
     * The furthest the view may scroll: the offset that stands the
     * oldest row on the window's top edge, which is what the clamp
     * holds every offset to. Unbounded while the window has not been
     * measured, so nothing is clamped against a room of nothing.
     */
    public double scrollCeiling() {
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
    public double rowsAfterScrolling(double rows, double pixels) {
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

    /** The closed-chat feed's frame; not a window, never pruned. */
    private static final ChatFrame FEED = new ChatFrame("feed");

    public static ChatFrame feed() {
        return FEED;
    }

    /** Forgets every window's frame and what the closed feed remembers. */
    public static synchronized void clear() {
        clearFrames();
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
        FEED.glide.clear();
    }
}
