package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The chat screen's small windows: the pickers, the cards and the menus —
 * whatever opens on a click and stays until it is put away. They float
 * above every chat window, stacked among themselves, the one last pressed
 * in front; they are moved by their title strip and resized by their
 * edges, and stick to the screen's margins and to other windows as they
 * go. A window closes by its cross, by Escape while it is the one in
 * front, or by the control that opened it; one of each kind is open at
 * most, but a card for each person, and none counts toward the chat's
 * eight windows. With no chat window open the pickers wait, undrawn, for
 * a bar to write into; everything else stands as it was.
 *
 * <p>"In front" is the window the player last pressed or opened; a press
 * anywhere else in the chat leaves no small window in front, and Escape
 * then closes the chat, taking the small windows with it until it opens
 * again.</p>
 */
final class ChatSmallWindows {
    /** The tab's accent and name, which no channel colours here. */
    static final int ACCENT_RGB = LostTalesChatVisualStyle.IVORY;

    /** Back to front. */
    private final List<ChatSmallWindow> stack = new ArrayList<ChatSmallWindow>();
    private ChatSmallWindow focused;
    private Gesture gesture;
    private int screenWidth;
    private int screenHeight;

    /** Takes the screen's size; called on every layout, which also runs on a resize. */
    void bind(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        for (ChatSmallWindow window : this.stack) {
            fit(window);
        }
    }

    /** Offset a window of a kind already standing at that place opens at: a chat window's cascade. */
    private static final int CASCADE_STEP = 24;

    /** The window of that kind and key, open or still fading out; null for none. */
    ChatSmallWindow find(ChatSmallWindowKind kind, String key) {
        String wanted = key == null ? "" : key;
        for (ChatSmallWindow window : this.stack) {
            if (window.kind == kind && window.key.equals(wanted)) {
                return window;
            }
        }
        return null;
    }

    /**
     * Opens a window of {@code kind} holding {@code content}, in front:
     * where the player left one of its kind last — at the size they gave
     * it, or else its content's own — and otherwise round
     * {@code firstContentBox}, where the popup it replaces always opened;
     * a step down and along from another of its kind standing there
     * already. The window of that kind and key already out, or still
     * fading, comes forward instead.
     */
    ChatSmallWindow open(ChatSmallWindowKind kind, String key,
                         ChatSmallWindowContent content,
                         LostTalesUiHitBox firstContentBox) {
        ChatSmallWindow window = find(kind, key);
        if (window != null) {
            if (!window.isOpen()) {
                window.setOpen(true);
                window.content.opened();
            }
            focus(window);
            return window;
        }
        ChatSmallWindowPlacements.Placement placed =
                ChatSmallWindowPlacements.of(kind);
        if (placed != null) {
            window = new ChatSmallWindow(kind, key, content, 0.0D, 0.0D,
                    placed.width, placed.height);
            window.sized = placed.isSized();
            if (!window.sized) {
                takeContentSize(window);
            }
            fitSize(window);
            window.left = placed.left(this.screenWidth, window.width);
            window.top = placed.top(this.screenHeight, window.height);
        } else {
            window = new ChatSmallWindow(kind, key, content,
                    firstContentBox.left,
                    firstContentBox.top - ChatSmallWindow.STRIP_HEIGHT,
                    (int)Math.round(firstContentBox.width),
                    (int)Math.round(firstContentBox.height)
                            + ChatSmallWindow.STRIP_HEIGHT);
        }
        fit(window);
        cascade(window);
        return add(window);
    }

    /**
     * Opens a window exactly where it stood as the chat closed, held on
     * the screen: what comes back with the chat.
     */
    ChatSmallWindow reopen(ChatSmallWindowPlacements.Reopening where,
                           ChatSmallWindowContent content) {
        ChatSmallWindow window = find(where.kind, where.key);
        if (window != null) {
            return window;
        }
        window = new ChatSmallWindow(where.kind, where.key, content,
                where.left, where.top, where.width, where.height);
        window.sized = where.sized;
        fit(window);
        return add(window);
    }

    /**
     * Gives a window its content's own size again, its top left kept
     * where the screen allows: a menu turned to something else. A window
     * the player has sized keeps its size.
     */
    void refit(ChatSmallWindow window) {
        if (window == null || window.sized) {
            return;
        }
        takeContentSize(window);
        fit(window);
    }

    /** The content's own size, and the strip over it. */
    private static void takeContentSize(ChatSmallWindow window) {
        window.width = window.content.naturalWidth();
        window.height = ChatSmallWindow.STRIP_HEIGHT
                + window.content.naturalHeight(window.width);
    }

    private ChatSmallWindow add(ChatSmallWindow window) {
        this.stack.add(window);
        window.content.opened();
        focus(window);
        return window;
    }

    /** Steps the window down and along past any open window of its kind at its place. */
    private void cascade(ChatSmallWindow window) {
        for (int step = 0; step < this.stack.size(); step++) {
            boolean taken = false;
            for (ChatSmallWindow other : this.stack) {
                if (other.kind == window.kind && other.isOpen()
                        && Math.abs(other.left - window.left) < 1.0D
                        && Math.abs(other.top - window.top) < 1.0D) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return;
            }
            window.left += CASCADE_STEP;
            window.top += CASCADE_STEP;
            holdOnScreen(window);
        }
    }

    /** Closes the window: it fades out where it stands. */
    void close(ChatSmallWindow window) {
        if (window == null || !window.isOpen()) {
            return;
        }
        window.setOpen(false);
        window.content.closed();
        if (this.focused == window) {
            this.focused = null;
        }
        if (this.gesture != null && this.gesture.window == window) {
            this.gesture = null;
        }
    }

    /** Opens the window of that kind and key, or closes it: what its control does. */
    void toggle(ChatSmallWindowKind kind, String key,
                ChatSmallWindowContent content,
                LostTalesUiHitBox firstContentBox) {
        ChatSmallWindow window = find(kind, key);
        if (window != null && window.isOpen()) {
            close(window);
        } else {
            open(kind, key, content, firstContentBox);
        }
    }

    /**
     * Puts the window in front of the others and gives it the keys' turn:
     * a field that takes the keys as its window comes in front takes them.
     */
    void focus(ChatSmallWindow window) {
        if (window == null) {
            return;
        }
        if (this.stack.remove(window)) {
            this.stack.add(window);
        }
        if (this.focused != window && this.focused != null) {
            this.focused.content.releaseKeys();
        }
        this.focused = window;
        window.content.takeKeys();
    }

    /** Leaves no small window in front: a press went elsewhere. */
    void blur() {
        if (this.focused != null) {
            this.focused.content.releaseKeys();
        }
        this.focused = null;
    }

    /** The window in front, or null; one waiting undrawn is not. */
    ChatSmallWindow focused() {
        return this.focused != null && this.focused.isOpen()
                && !this.focused.hidden ? this.focused : null;
    }

    /** Closes the window in front; answers whether there was one. */
    boolean closeFocused() {
        ChatSmallWindow front = focused();
        if (front == null) {
            return false;
        }
        close(front);
        return true;
    }

    /** The windows open, back to front, and where they stand: what comes back when the chat opens again. */
    List<ChatSmallWindowPlacements.Reopening> openWindows() {
        List<ChatSmallWindowPlacements.Reopening> open =
                new ArrayList<ChatSmallWindowPlacements.Reopening>();
        for (ChatSmallWindow window : this.stack) {
            if (window.isOpen()) {
                open.add(new ChatSmallWindowPlacements.Reopening(window.kind,
                        window.key, window.content.sessionState(),
                        window.left, window.top, window.width,
                        window.height, window.sized));
            }
        }
        return open;
    }

    /* ---- The pointer ---- */

    /**
     * What a point is on among the small windows, front to back: an edge,
     * a strip's cross, a strip, or the content, which answers for itself;
     * null where no small window is.
     */
    ChatHover hoverAt(double x, double y) {
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            ChatSmallWindow window = this.stack.get(index);
            if (!window.isOpen() || window.hidden) {
                continue;
            }
            ChatWindowGestures.ResizeEdge edge = window.edgeAt(x, y);
            if (edge != null) {
                ChatHover hover = new ChatHover(
                        ChatHover.Kind.SMALL_WINDOW_RESIZE);
                hover.smallWindow = window;
                hover.smallEdge = edge;
                return hover;
            }
            if (!window.contains(x, y)) {
                continue;
            }
            ChatHover hover;
            if (window.closeContains(x, y)) {
                hover = new ChatHover(ChatHover.Kind.SMALL_WINDOW_CLOSE);
            } else if (window.stripContains(x, y)) {
                hover = new ChatHover(ChatHover.Kind.SMALL_WINDOW_STRIP);
            } else {
                hover = window.content.hoverAt(window.wholeContentBox(),
                        x - window.fractionX, y - window.fractionY);
                if (hover == null) {
                    hover = new ChatHover(ChatHover.Kind.SMALL_WINDOW);
                }
            }
            hover.smallWindow = window;
            return hover;
        }
        return null;
    }

    /* ---- Drawing ---- */

    /**
     * Draws every small window, back to front, each registering the box
     * it covers and its resize band as above the chat windows; a window
     * faded out altogether goes. {@code hover} says what the pointer is
     * on, at {@code pointerX}/{@code pointerY}. With {@code barless} no
     * chat window is open, and a window that works on the input bar
     * waits undrawn.
     */
    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, ChatHover hover, double pointerX,
              double pointerY, boolean barless) {
        long now = System.nanoTime();
        List<ChatSmallWindow> windows =
                new ArrayList<ChatSmallWindow>(this.stack);
        for (ChatSmallWindow window : windows) {
            window.hidden = barless && window.content.needsInputBar();
            if (!window.hidden) {
                drawWindow(minecraft, font, regions, window, hover, pointerX,
                        pointerY, now);
            }
        }
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            if (this.stack.get(index).isGone()) {
                this.stack.remove(index);
            }
        }
    }

    private void drawWindow(Minecraft minecraft, FontRenderer font,
                            ChatPointerRegions regions,
                            ChatSmallWindow window, ChatHover hover,
                            double pointerX, double pointerY, long now) {
        float share = window.advanceShare(now);
        window.shownShare = share;
        int alpha = Math.round(255.0F * share);
        if (alpha < LostTalesChatVisualStyle.MIN_VISIBLE_ALPHA) {
            return;
        }
        // Laid on the display's grid, rising the last few pixels into
        // place as it opens; the content is laid out on whole pixels and
        // drawn in a matrix moved by the rest.
        double exactLeft = ChatWindowFrame.snapToDisplayPixels(window.left);
        double exactTop = ChatWindowFrame.snapToDisplayPixels(window.top
                + (1.0F - share) * ChatSmallWindow.RISE);
        int wholeLeft = (int)Math.floor(exactLeft);
        int wholeTop = (int)Math.floor(exactTop);
        window.drawnLeft = exactLeft;
        window.drawnTop = exactTop;
        window.fractionX = (float)(exactLeft - wholeLeft);
        window.fractionY = (float)(exactTop - wholeTop);
        int surface = LostTalesChatVisualStyle.insetArgb(share
                * LostTalesChatVisualStyle.chatOpacity(minecraft));
        boolean mine = hover != null && hover.smallWindow == window;
        boolean onClose = mine && hover.is(ChatHover.Kind.SMALL_WINDOW_CLOSE);
        window.closeMotion.advance(now, onClose, onClose,
                onClose && Mouse.isButtonDown(0));
        boolean onContent = mine && !onClose
                && !hover.is(ChatHover.Kind.SMALL_WINDOW_STRIP)
                && !hover.is(ChatHover.Kind.SMALL_WINDOW_RESIZE);
        float left = wholeLeft;
        float top = wholeTop;
        float right = left + window.width;
        float bottom = top + window.height;
        int rowBottom = wholeTop + ChatSmallWindow.STRIP_HEIGHT;
        GL11.glPushMatrix();
        GL11.glTranslatef(window.fractionX, window.fractionY, 0.0F);
        try {
            window.tab = ChatChannelTabBar.layOutLoneTab(font, left, right,
                    rowBottom, window.title(),
                    window.content.stripIcon() != null);
            // One surface, the strip's and the content's, with the
            // frame's ring round it and the tab's footprint left out: the
            // tab wears its own surface in a single layer. The frame's
            // edges lie over the ring.
            fillAroundTab(left, top, right, bottom, window.tab, surface);
            LostTalesUiWindowFrame.drawSurface(left, top, right, bottom,
                    surface);
            ChatChannelTabBar.drawLoneTab(font, window.tab, left, right,
                    rowBottom, window.content.stripIcon(), ACCENT_RGB,
                    window.closeMotion, alpha);
            window.content.draw(minecraft, window.wholeContentBox(),
                    exactLeft, exactTop + ChatSmallWindow.STRIP_HEIGHT,
                    onContent ? pointerX - window.fractionX : ChatHover.AWAY,
                    onContent ? pointerY - window.fractionY : ChatHover.AWAY,
                    alpha, surface >>> 24);
            LostTalesUiWindowFrame.drawEdges(left, top, right, bottom, alpha);
        } finally {
            GL11.glPopMatrix();
        }
        if (window.isOpen()) {
            int border = ChatWindowGestures.RESIZE_BORDER;
            regions.addScreen(wholeLeft - border, wholeTop - border,
                    (int)Math.ceil(exactLeft + window.width) + border,
                    (int)Math.ceil(exactTop + window.height) + border);
        }
    }

    /**
     * The window's surface over its box, leaving out the footprint the
     * strip's tab paints: its chamfered top row and its body down to the
     * strip's rule.
     */
    private static void fillAroundTab(float left, float top, float right,
                                      float bottom,
                                      ChatChannelTabBar.LoneTab tab,
                                      int surface) {
        float tabTop = tab.top;
        float tabBottom = tab.bottom();
        LostTalesUiInk.fillRect(left, top, right, tabTop, surface);
        LostTalesUiInk.fillRect(left, tabTop, tab.left + 1, tabTop + 1,
                surface);
        LostTalesUiInk.fillRect(tab.right - 1, tabTop, right, tabTop + 1,
                surface);
        LostTalesUiInk.fillRect(left, tabTop + 1, tab.left, tabBottom,
                surface);
        LostTalesUiInk.fillRect(tab.right, tabTop + 1, right, tabBottom,
                surface);
        LostTalesUiInk.fillRect(left, tabBottom, right, bottom, surface);
    }

    /** The tip of what the pointer rests on in a small window, over everything. */
    void drawTip(Minecraft minecraft, ChatHover hover, int tipX, int tipY) {
        if (hover != null && hover.smallWindow != null
                && hover.smallWindow.isOpen()) {
            hover.smallWindow.content.drawTip(minecraft, tipX, tipY,
                    this.screenWidth);
        }
    }

    /* ---- Moving and resizing ---- */

    /** Takes hold of a window's strip: it moves once the pointer travels. */
    void armMove(ChatSmallWindow window, double x, double y) {
        focus(window);
        this.gesture = new Gesture(window, null, x, y);
    }

    /** Takes hold of a window's edge: it resizes as the pointer moves. */
    void armResize(ChatSmallWindow window,
                   ChatWindowGestures.ResizeEdge edge, double x, double y) {
        focus(window);
        this.gesture = new Gesture(window, edge, x, y);
        this.gesture.active = true;
    }

    /** Whether a window is held, moving or not yet. */
    boolean isHolding() {
        return this.gesture != null;
    }

    /** Whether a window is being moved or resized. */
    boolean isDragging() {
        return this.gesture != null && this.gesture.active;
    }

    /** The edge of the window being resized; null while one is moved or none is held. */
    ChatWindowGestures.ResizeEdge heldEdge() {
        return this.gesture == null ? null : this.gesture.edge;
    }

    /** Follows the pointer with the window held. */
    void drag(double x, double y) {
        Gesture held = this.gesture;
        if (held == null) {
            return;
        }
        if (!held.active) {
            if (Math.abs(x - held.pressX) < ChatWindowGestures.DRAG_THRESHOLD
                    && Math.abs(y - held.pressY)
                            < ChatWindowGestures.DRAG_THRESHOLD) {
                return;
            }
            held.active = true;
        }
        ChatSmallWindow window = held.window;
        double dx = x - held.pressX;
        double dy = y - held.pressY;
        List<LostTalesUiHitBox> others = othersThan(window);
        if (held.edge == null) {
            LostTalesUiHitBox moved = ChatSmallWindowSnap.moved(
                    new LostTalesUiHitBox(held.startLeft + dx,
                            held.startTop + dy, window.width, window.height),
                    others, this.screenWidth, this.screenHeight);
            window.left = moved.left;
            window.top = moved.top;
            holdOnScreen(window);
            return;
        }
        resize(window, held, dx, dy, others);
    }

    /** Resizes the window by the pointer's travel, its far sides where they were. */
    private void resize(ChatSmallWindow window, Gesture held, double dx,
                        double dy, List<LostTalesUiHitBox> others) {
        ChatWindowGestures.ResizeEdge edge = held.edge;
        double left = held.startLeft;
        double top = held.startTop;
        double right = held.startLeft + held.startWidth;
        double bottom = held.startTop + held.startHeight;
        if (edge.horizontal) {
            if (edge.fromLeft) {
                left += dx;
            } else {
                right += dx;
            }
        }
        if (edge.vertical) {
            if (edge.fromTop) {
                top += dy;
            } else {
                bottom += dy;
            }
        }
        LostTalesUiHitBox stuck = ChatSmallWindowSnap.resized(
                new LostTalesUiHitBox(left, top, right - left, bottom - top),
                edge, others, this.screenWidth, this.screenHeight);
        int width = clampSize((int)Math.round(stuck.width), window.minWidth(),
                this.screenWidth);
        int height = clampSize((int)Math.round(stuck.height),
                window.minHeight(), this.screenHeight);
        window.width = width;
        window.height = height;
        window.left = edge.horizontal && edge.fromLeft ? right - width
                : held.startLeft;
        window.top = edge.vertical && edge.fromTop ? bottom - height
                : held.startTop;
        holdOnScreen(window);
    }

    /**
     * Lets go of the window held; a move is remembered as where the
     * player left the kind, and a resize as the size they gave it too.
     */
    void release() {
        Gesture held = this.gesture;
        this.gesture = null;
        if (held != null && held.active) {
            ChatSmallWindow window = held.window;
            if (held.edge != null) {
                window.sized = true;
            }
            ChatSmallWindowPlacements.remember(window.kind, window.left,
                    window.top, window.width, window.height, window.sized,
                    this.screenWidth, this.screenHeight);
        }
    }

    /** Puts the window held back where it was taken from. */
    void cancel() {
        Gesture held = this.gesture;
        this.gesture = null;
        if (held != null) {
            held.window.left = held.startLeft;
            held.window.top = held.startTop;
            held.window.width = held.startWidth;
            held.window.height = held.startHeight;
        }
    }

    /** The boxes a window may stick to: every chat window's and every other small window's. */
    private List<LostTalesUiHitBox> othersThan(ChatSmallWindow window) {
        List<LostTalesUiHitBox> others = new ArrayList<LostTalesUiHitBox>();
        for (ChatWindowFrame frame : ChatWindowFrame.drawnFrames()) {
            others.add(frame.drawnBox());
        }
        for (ChatSmallWindow other : this.stack) {
            if (other != window && other.isOpen()) {
                others.add(other.box());
            }
        }
        return others;
    }

    /** Keeps the window's size within the screen and its place on it. */
    private void fit(ChatSmallWindow window) {
        fitSize(window);
        holdOnScreen(window);
    }

    private void fitSize(ChatSmallWindow window) {
        window.width = clampSize(window.width, window.minWidth(),
                this.screenWidth);
        window.height = clampSize(window.height, window.minHeight(),
                this.screenHeight);
    }

    /** A size no smaller than {@code min} and no larger than the screen leaves. */
    private static int clampSize(int size, int min, int screen) {
        int max = Math.max(min, screen - 2 * ChatWindowPlacement.EDGE_MARGIN);
        return Math.max(min, Math.min(max, size));
    }

    /** The whole window on the screen, inside its margins, strip first where it cannot be. */
    private void holdOnScreen(ChatSmallWindow window) {
        double margin = ChatWindowPlacement.EDGE_MARGIN;
        window.left = Math.max(margin, Math.min(this.screenWidth - margin
                - window.width, window.left));
        window.top = Math.max(margin, Math.min(this.screenHeight - margin
                - window.height, window.top));
    }

    /** A window held by its strip or an edge. */
    private static final class Gesture {
        final ChatSmallWindow window;
        /** The edge held; null while the window is moved by its strip. */
        final ChatWindowGestures.ResizeEdge edge;
        final double pressX;
        final double pressY;
        final double startLeft;
        final double startTop;
        final int startWidth;
        final int startHeight;
        boolean active;

        Gesture(ChatSmallWindow window, ChatWindowGestures.ResizeEdge edge,
                double pressX, double pressY) {
            this.window = window;
            this.edge = edge;
            this.pressX = pressX;
            this.pressY = pressY;
            this.startLeft = window.left;
            this.startTop = window.top;
            this.startWidth = window.width;
            this.startHeight = window.height;
        }
    }
}
