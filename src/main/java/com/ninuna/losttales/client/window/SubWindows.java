package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
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
 * The window screen's sub-windows: the pickers, the cards and the menus —
 * whatever opens on a click and stays until it is put away. Each belongs to
 * the window whose control opened it and lives inside it, as a window
 * lives on a screen: it is drawn with that window, so a window lying over
 * it covers it too, rides along as that window moves, and is moved by its
 * title strip and resized by its edges anywhere inside it, never past its
 * edges and never sticking to anything. The sub-windows of the window
 * being typed in are drawn over every window, as its input bar is; one
 * opened with no window open stands on the bare screen.
 *
 * <p>A window closes by its cross, by Escape while it is the one in front,
 * or by the control that opened it; it closes with its window, too.
 * One of each kind is open at most, but a card for each person: pressed
 * for in another window, a kind's window moves there. None counts
 * toward the eight windows there may be.</p>
 *
 * <p>"In front" is the window the player last pressed or opened; a press
 * anywhere else on the screen leaves no sub-window in front, and Escape
 * then closes the screen, taking the sub-windows with it until it opens
 * again.</p>
 */
public final class SubWindows {
    /** The tab's accent and name, which no channel colours here. */
    static final int ACCENT_RGB = LostTalesUiInk.IVORY;
    /**
     * How far in from its window's edges a sub-window's room lies:
     * its own frame and two clear pixels, so a window pushed against the
     * edge keeps a framed button's clearance from its window's frame.
     */
    static final int ROOM_INSET = LostTalesUiWindowFrame.WIDTH + 2;
    /** Offset a window of a kind already standing at that place opens at: a window's cascade. */
    private static final int CASCADE_STEP = 24;

    /** Back to front. */
    private final List<SubWindow> stack = new ArrayList<SubWindow>();
    private SubWindow focused;
    private Gesture gesture;
    private int screenWidth;
    private int screenHeight;
    /** The window whose sub-windows are drawn over every window this frame. */
    private String topParentId;

    /** Takes the screen's size; called on every layout, which also runs on a resize. */
    public void bind(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * The room the sub-windows of window {@code parentId} have: that
     * window's box as drawn, {@link #ROOM_INSET} in from its edges; for
     * none, the screen inside its margins. Null while the window is
     * not drawn.
     */
    public LostTalesUiHitBox roomOf(String parentId) {
        if (parentId == null) {
            double margin = WindowPlacement.EDGE_MARGIN;
            return new LostTalesUiHitBox(margin, margin,
                    Math.max(0.0D, this.screenWidth - 2.0D * margin),
                    Math.max(0.0D, this.screenHeight - 2.0D * margin));
        }
        WindowFrame frame = WindowFrame.find(parentId);
        if (frame == null || !frame.drawn
                || WindowLayout.window(parentId) == null) {
            return null;
        }
        LostTalesUiHitBox box = frame.drawnBox();
        return new LostTalesUiHitBox(box.left + ROOM_INSET,
                box.top + ROOM_INSET,
                Math.max(0.0D, box.width - 2.0D * ROOM_INSET),
                Math.max(0.0D, box.height - 2.0D * ROOM_INSET));
    }

    /** The window of that kind and key, open or still fading out; null for none. */
    public SubWindow find(SubWindowKind kind, String key) {
        String wanted = key == null ? "" : key;
        for (SubWindow window : this.stack) {
            if (window.kind == kind && window.key.equals(wanted)) {
                return window;
            }
        }
        return null;
    }

    /**
     * Opens a window of {@code kind} holding {@code content} in window
     * {@code parentId}, in front: where the player left one of its kind
     * last — at the size they gave it, or else its content's own — and
     * otherwise round {@code firstContentBox}, a screen box, where the
     * popup it replaces always opened; a step down and along from another
     * of its kind standing there already. The window of that kind and key
     * already out, or still fading, comes forward instead, moved into
     * {@code parentId} when it stood in another. A window that is not
     * drawn leaves the window to the bare screen.
     */
    public SubWindow open(SubWindowKind kind, String key,
                         SubWindowContent content, String parentId,
                         LostTalesUiHitBox firstContentBox) {
        String parent = roomOf(parentId) == null ? null : parentId;
        LostTalesUiHitBox room = roomOf(parent);
        SubWindow window = find(kind, key);
        if (window != null) {
            if (!window.isOpen()) {
                window.setOpen(true);
                window.content.opened();
            }
            if (!window.belongsTo(parent)) {
                window.parentId = parent;
                place(window, room, firstContentBox);
            }
            focus(window);
            return window;
        }
        window = new SubWindow(kind, key, content, parent);
        place(window, room, firstContentBox);
        cascade(window, room);
        return add(window);
    }

    /**
     * Puts a window in its room where the player left its kind, or round
     * {@code firstContentBox} while they have not.
     */
    private static void place(SubWindow window, LostTalesUiHitBox room,
                              LostTalesUiHitBox firstContentBox) {
        SubWindowPlaces.Placement placed =
                SubWindowPlaces.of(window.kind);
        if (placed != null) {
            window.sized = placed.isSized();
            if (window.sized) {
                window.wantedWidth = placed.width;
                window.wantedHeight = placed.height;
            } else {
                takeContentSize(window);
            }
            window.layOut(room);
            window.x = placed.x(room.width, window.width);
            window.y = placed.y(room.height, window.height);
        } else {
            window.wantedWidth = (int)Math.round(firstContentBox.width);
            window.wantedHeight = (int)Math.round(firstContentBox.height)
                    + SubWindow.STRIP_HEIGHT;
            window.x = firstContentBox.left - room.left;
            window.y = firstContentBox.top - SubWindow.STRIP_HEIGHT
                    - room.top;
        }
        window.layOut(room);
    }

    /**
     * Opens a window exactly where it stood as the screen closed, in the
     * window it stood in: what comes back with the screen. One whose
     * window has closed since stays closed.
     */
    public SubWindow reopen(SubWindowPlaces.Reopening where,
                           SubWindowContent content) {
        SubWindow window = find(where.kind, where.key);
        if (window != null) {
            return window;
        }
        LostTalesUiHitBox room = roomOf(where.parentId);
        if (room == null) {
            return null;
        }
        window = new SubWindow(where.kind, where.key, content,
                where.parentId);
        window.x = where.x;
        window.y = where.y;
        window.wantedWidth = where.width;
        window.wantedHeight = where.height;
        window.sized = where.sized;
        window.layOut(room);
        return add(window);
    }

    /**
     * Gives a window its content's own size again, its top left kept:
     * a menu turned to something else. A window the player has sized
     * keeps its size.
     */
    public void refit(SubWindow window) {
        if (window == null || window.sized) {
            return;
        }
        takeContentSize(window);
        if (window.room != null) {
            window.layOut(window.room);
        }
    }

    /** The content's own size, and the strip over it. */
    private static void takeContentSize(SubWindow window) {
        window.wantedWidth = window.content.naturalWidth();
        window.wantedHeight = SubWindow.STRIP_HEIGHT
                + window.content.naturalHeight(window.wantedWidth);
    }

    private SubWindow add(SubWindow window) {
        this.stack.add(window);
        window.content.opened();
        focus(window);
        return window;
    }

    /** Steps the window down and along past any open window of its kind at its place in the same room. */
    private void cascade(SubWindow window, LostTalesUiHitBox room) {
        for (int step = 0; step < this.stack.size(); step++) {
            boolean taken = false;
            for (SubWindow other : this.stack) {
                if (other.kind == window.kind && other.isOpen()
                        && other.belongsTo(window.parentId)
                        && Math.abs(other.x - window.x) < 1.0D
                        && Math.abs(other.y - window.y) < 1.0D) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return;
            }
            window.x += CASCADE_STEP;
            window.y += CASCADE_STEP;
            window.layOut(room);
            window.x = window.left - room.left;
            window.y = window.top - room.top;
        }
    }

    /** Closes the window: it fades out where it stands. */
    public void close(SubWindow window) {
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

    /**
     * What a window's control does: opens the window of that kind and key
     * in window {@code parentId}, or closes it where it is already out
     * there; out in another window, it moves over.
     */
    public void toggle(SubWindowKind kind, String key,
                SubWindowContent content, String parentId,
                LostTalesUiHitBox firstContentBox) {
        String parent = roomOf(parentId) == null ? null : parentId;
        SubWindow window = find(kind, key);
        if (window != null && window.isOpen() && window.belongsTo(parent)) {
            close(window);
        } else {
            open(kind, key, content, parent, firstContentBox);
        }
    }

    /**
     * Puts the window in front of the others and gives it the keys' turn:
     * a field that takes the keys as its window comes in front takes them.
     */
    public void focus(SubWindow window) {
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

    /** Leaves no sub-window in front: a press went elsewhere. */
    public void blur() {
        if (this.focused != null) {
            this.focused.content.releaseKeys();
        }
        this.focused = null;
    }

    /** The window in front, or null; one waiting undrawn is not. */
    public SubWindow focused() {
        return this.focused != null && this.focused.isOpen()
                && !this.focused.hidden ? this.focused : null;
    }

    /** Closes the window in front; answers whether there was one. */
    public boolean closeFocused() {
        SubWindow front = focused();
        if (front == null) {
            return false;
        }
        close(front);
        return true;
    }

    /** The windows open, back to front, and where they stand: what comes back when the screen opens again. */
    public List<SubWindowPlaces.Reopening> openWindows() {
        List<SubWindowPlaces.Reopening> open =
                new ArrayList<SubWindowPlaces.Reopening>();
        for (SubWindow window : this.stack) {
            if (window.isOpen()) {
                open.add(new SubWindowPlaces.Reopening(window.kind,
                        window.key, window.content.sessionState(),
                        window.parentId, window.x, window.y,
                        window.wantedWidth, window.wantedHeight,
                        window.sized));
            }
        }
        return open;
    }

    /* ---- The pointer ---- */

    /**
     * What a point is on among the sub-windows, front to back: a list a
     * window's field opens, over everything; then an edge, a strip's
     * cross, a strip, or the content, which answers for itself. A window
     * a window in front of its own covers at the point is not there;
     * null where no sub-window is.
     */
    public WindowHover hoverAt(double x, double y) {
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            SubWindow window = this.stack.get(index);
            if (!window.isOpen() || window.hidden) {
                continue;
            }
            WindowHover popup = window.content.popupHoverAt(
                    x - window.fractionX, y - window.fractionY);
            if (popup != null) {
                popup.subWindow = window;
                return popup;
            }
        }
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            SubWindow window = this.stack.get(index);
            if (!window.isOpen() || window.hidden || covered(window, x, y)) {
                continue;
            }
            WindowGestures.ResizeEdge edge = window.edgeAt(x, y);
            if (edge != null) {
                WindowHover hover = new WindowHover(
                        WindowHover.Kind.SUB_WINDOW_RESIZE);
                hover.subWindow = window;
                hover.subEdge = edge;
                return hover;
            }
            if (!window.contains(x, y)) {
                continue;
            }
            WindowHover hover;
            if (window.closeContains(x, y)) {
                hover = new WindowHover(WindowHover.Kind.SUB_WINDOW_CLOSE);
            } else if (window.stripContains(x, y)) {
                hover = new WindowHover(WindowHover.Kind.SUB_WINDOW_STRIP);
            } else {
                hover = window.content.hoverAt(window.wholeContentBox(),
                        x - window.fractionX, y - window.fractionY);
                if (hover == null) {
                    hover = new WindowHover(WindowHover.Kind.SUB_WINDOW);
                }
            }
            hover.subWindow = window;
            return hover;
        }
        return null;
    }

    /**
     * Whether a window lying over the window's own covers the point:
     * the sub-windows of the window being typed in, and those on the bare
     * screen, are drawn over every window and never are.
     */
    private boolean covered(SubWindow window, double x, double y) {
        if (window.parentId == null
                || window.parentId.equals(this.topParentId)) {
            return false;
        }
        WindowFrame front = WindowFrame.drawnAt(x, y);
        return front != null && !front.windowId.equals(window.parentId);
    }

    /* ---- Drawing ---- */

    /**
     * Readies the windows for a frame whose sub-windows of window
     * {@code topParentId} are drawn over every window: a window faded out
     * altogether goes, one whose window has closed closes with it, and
     * every window waits undrawn until its window draws it.
     */
    public void beginFrame(String topParentId) {
        this.topParentId = topParentId;
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            SubWindow window = this.stack.get(index);
            if (window.parentId != null
                    && WindowLayout.window(window.parentId) == null) {
                close(window);
                this.stack.remove(index);
                continue;
            }
            if (window.isGone()) {
                this.stack.remove(index);
                continue;
            }
            window.hidden = true;
        }
    }

    /**
     * Draws the sub-windows of window {@code parentId} — null for
     * the bare screen — back to front, laid out in its room as it was just
     * drawn, each registering the box it covers and its resize band; then
     * what their fields open, over them. {@code hover} says what the
     * pointer is on, at {@code pointerX}/{@code pointerY}. With
     * {@code barless} no window is open, and a window that works on
     * the input bar waits undrawn.
     */
    public void draw(Minecraft minecraft, FontRenderer font,
              PointerRegions regions, WindowHover hover, double pointerX,
              double pointerY, boolean barless, String parentId) {
        LostTalesUiHitBox room = roomOf(parentId);
        if (room == null) {
            return;
        }
        long now = System.nanoTime();
        List<SubWindow> drawn = new ArrayList<SubWindow>();
        for (SubWindow window
                : new ArrayList<SubWindow>(this.stack)) {
            if (!window.belongsTo(parentId)
                    || (barless && window.content.needsInputBar())) {
                continue;
            }
            window.hidden = false;
            window.layOut(room);
            drawWindow(minecraft, font, regions, window, hover, pointerX,
                    pointerY, now);
            drawn.add(window);
        }
        for (SubWindow window : drawn) {
            if (!window.isOpen()) {
                continue;
            }
            GL11.glPushMatrix();
            try {
                GL11.glTranslatef(window.fractionX, window.fractionY, 0.0F);
                window.content.drawPopups(minecraft, regions,
                        pointerX - window.fractionX,
                        pointerY - window.fractionY);
            } finally {
                GL11.glPopMatrix();
            }
        }
    }

    private void drawWindow(Minecraft minecraft, FontRenderer font,
                            PointerRegions regions,
                            SubWindow window, WindowHover hover,
                            double pointerX, double pointerY, long now) {
        float share = window.advanceShare(now);
        window.shownShare = share;
        int alpha = Math.round(255.0F * share);
        if (alpha < LostTalesUiInk.MIN_VISIBLE_ALPHA) {
            return;
        }
        // Laid on the display's grid, rising the last few pixels into
        // place as it opens; the content is laid out on whole pixels and
        // drawn in a matrix moved by the rest.
        double exactLeft = LostTalesDisplayPixels.snap(window.left);
        double exactTop = LostTalesDisplayPixels.snap(window.top
                + (1.0F - share) * SubWindow.RISE);
        int wholeLeft = (int)Math.floor(exactLeft);
        int wholeTop = (int)Math.floor(exactTop);
        window.drawnLeft = exactLeft;
        window.drawnTop = exactTop;
        window.fractionX = (float)(exactLeft - wholeLeft);
        window.fractionY = (float)(exactTop - wholeTop);
        int surface = WindowStyle.insetArgb(share
                * WindowStyle.opacity(minecraft));
        boolean mine = hover != null && hover.subWindow == window;
        boolean onClose = mine && hover.is(WindowHover.Kind.SUB_WINDOW_CLOSE);
        window.closeMotion.advance(now, onClose, onClose,
                onClose && Mouse.isButtonDown(0));
        boolean onContent = mine && !onClose
                && !hover.is(WindowHover.Kind.SUB_WINDOW_STRIP)
                && !hover.is(WindowHover.Kind.SUB_WINDOW_RESIZE);
        float left = wholeLeft;
        float top = wholeTop;
        float right = left + window.width;
        float bottom = top + window.height;
        int rowBottom = wholeTop + SubWindow.STRIP_HEIGHT;
        // A window its room cannot hold is cut by the room's edge, and so
        // is whatever its content cuts for itself.
        boolean cut = window.overflowsRoom();
        boolean clipped = false;
        if (cut) {
            SubWindowContent.setOuterClip(window.room);
            clipped = SubWindowContent.beginClip(minecraft,
                    window.room.left, window.room.top, window.room.width,
                    window.room.height);
        }
        GL11.glPushMatrix();
        GL11.glTranslatef(window.fractionX, window.fractionY, 0.0F);
        try {
            window.tab = TabRow.layOutLoneTab(font, left, right,
                    rowBottom, window.title(),
                    window.content.stripIcon() != null);
            // One surface, the strip's and the content's, with the
            // frame's ring round it and the tab's footprint left out: the
            // tab wears its own surface in a single layer. The frame's
            // edges lie over the ring.
            fillAroundTab(left, top, right, bottom, window.tab, surface);
            LostTalesUiWindowFrame.drawSurface(left, top, right, bottom,
                    surface);
            TabRow.drawLoneTab(font, window.tab, left, right,
                    rowBottom, window.content.stripIcon(), ACCENT_RGB,
                    window.closeMotion, alpha);
            window.content.draw(minecraft, window.wholeContentBox(),
                    exactLeft, exactTop + SubWindow.STRIP_HEIGHT,
                    onContent ? pointerX - window.fractionX : WindowHover.AWAY,
                    onContent ? pointerY - window.fractionY : WindowHover.AWAY,
                    alpha, surface >>> 24);
            LostTalesUiWindowFrame.drawEdges(left, top, right, bottom, alpha);
        } finally {
            GL11.glPopMatrix();
            if (cut) {
                SubWindowContent.endClip(clipped);
                SubWindowContent.setOuterClip(null);
            }
        }
        if (window.isOpen()) {
            int border = WindowGestures.RESIZE_BORDER;
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
                                      TabRow.LoneTab tab,
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

    /** The tip of what the pointer rests on in a sub-window, over everything. */
    public void drawTip(Minecraft minecraft, WindowHover hover, int tipX, int tipY) {
        if (hover != null && hover.subWindow != null
                && hover.subWindow.isOpen()) {
            hover.subWindow.content.drawTip(minecraft, tipX, tipY,
                    this.screenWidth);
        }
    }

    /* ---- Moving and resizing ---- */

    /** Takes hold of a window's strip: it moves once the pointer travels. */
    public void armMove(SubWindow window, double x, double y) {
        focus(window);
        this.gesture = new Gesture(window, null, x, y);
    }

    /** Takes hold of a window's edge: it resizes as the pointer moves. */
    public void armResize(SubWindow window,
                   WindowGestures.ResizeEdge edge, double x, double y) {
        focus(window);
        this.gesture = new Gesture(window, edge, x, y);
        this.gesture.active = true;
    }

    /** Whether a window is held, moving or not yet. */
    public boolean isHolding() {
        return this.gesture != null;
    }

    /** Whether a window is being moved or resized. */
    public boolean isDragging() {
        return this.gesture != null && this.gesture.active;
    }

    /** The edge of the window being resized; null while one is moved or none is held. */
    public WindowGestures.ResizeEdge heldEdge() {
        return this.gesture == null ? null : this.gesture.edge;
    }

    /**
     * Follows the pointer with the window held, inside its room: asked on
     * every frame as well as on every move of the pointer, so the window
     * glides with it as a window does.
     */
    public void drag(double x, double y) {
        Gesture held = this.gesture;
        if (held == null || held.window.room == null) {
            return;
        }
        if (!held.active) {
            if (Math.abs(x - held.pressX) < WindowGestures.DRAG_THRESHOLD
                    && Math.abs(y - held.pressY)
                            < WindowGestures.DRAG_THRESHOLD) {
                return;
            }
            held.active = true;
        }
        SubWindow window = held.window;
        LostTalesUiHitBox room = window.room;
        double dx = x - held.pressX;
        double dy = y - held.pressY;
        if (held.edge == null) {
            window.x = SubWindow.held(held.startX + dx,
                    room.width - window.width);
            window.y = SubWindow.held(held.startY + dy,
                    room.height - window.height);
            window.layOut(room);
            return;
        }
        resize(window, held, dx, dy, room);
    }

    /**
     * Resizes the window by the pointer's travel, its far sides where they
     * were, its moving sides held inside the room.
     */
    private static void resize(SubWindow window, Gesture held,
                               double dx, double dy, LostTalesUiHitBox room) {
        WindowGestures.ResizeEdge edge = held.edge;
        double left = held.startX;
        double top = held.startY;
        double right = held.startX + held.startWidth;
        double bottom = held.startY + held.startHeight;
        if (edge.horizontal) {
            if (edge.fromLeft) {
                left = Math.max(0.0D, left + dx);
            } else {
                right = Math.min(room.width, right + dx);
            }
        }
        if (edge.vertical) {
            if (edge.fromTop) {
                top = Math.max(0.0D, top + dy);
            } else {
                bottom = Math.min(room.height, bottom + dy);
            }
        }
        int width = SubWindow.fitted((int)Math.round(right - left),
                window.minWidth(), (int)Math.floor(room.width));
        int height = SubWindow.fitted((int)Math.round(bottom - top),
                window.minHeight(), (int)Math.floor(room.height));
        window.wantedWidth = width;
        window.wantedHeight = height;
        window.x = edge.horizontal && edge.fromLeft ? right - width
                : held.startX;
        window.y = edge.vertical && edge.fromTop ? bottom - height
                : held.startY;
        window.layOut(room);
    }

    /**
     * Lets go of the window held; a move is remembered as where the
     * player left the kind in its room, and a resize as the size they
     * gave it too.
     */
    public void release() {
        Gesture held = this.gesture;
        this.gesture = null;
        if (held == null || !held.active || held.window.room == null) {
            return;
        }
        SubWindow window = held.window;
        if (held.edge != null) {
            window.sized = true;
        }
        window.x = window.left - window.room.left;
        window.y = window.top - window.room.top;
        SubWindowPlaces.remember(window.kind, window.x, window.y,
                window.width, window.height, window.sized,
                window.room.width, window.room.height);
    }

    /** Puts the window held back where it was taken from. */
    public void cancel() {
        Gesture held = this.gesture;
        this.gesture = null;
        if (held != null) {
            held.window.x = held.startX;
            held.window.y = held.startY;
            held.window.wantedWidth = held.startWantedWidth;
            held.window.wantedHeight = held.startWantedHeight;
        }
    }

    /** A window held by its strip or an edge, from where it stood in its room. */
    private static final class Gesture {
        final SubWindow window;
        /** The edge held; null while the window is moved by its strip. */
        final WindowGestures.ResizeEdge edge;
        final double pressX;
        final double pressY;
        final double startX;
        final double startY;
        final int startWidth;
        final int startHeight;
        final int startWantedWidth;
        final int startWantedHeight;
        boolean active;

        Gesture(SubWindow window, WindowGestures.ResizeEdge edge,
                double pressX, double pressY) {
            this.window = window;
            this.edge = edge;
            this.pressX = pressX;
            this.pressY = pressY;
            LostTalesUiHitBox room = window.room;
            this.startX = room == null ? window.x : window.left - room.left;
            this.startY = room == null ? window.y : window.top - room.top;
            this.startWidth = window.width;
            this.startHeight = window.height;
            this.startWantedWidth = window.wantedWidth;
            this.startWantedHeight = window.wantedHeight;
        }
    }
}
