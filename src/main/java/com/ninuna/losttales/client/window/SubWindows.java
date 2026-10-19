package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesDisplayPixels;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import com.ninuna.losttales.gui.style.LostTalesUiInk;
import com.ninuna.losttales.gui.style.LostTalesUiLayerFade;
import com.ninuna.losttales.gui.style.LostTalesUiWindowFrame;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

/**
 * The window screen's sub-windows: the pickers, the cards and the menus —
 * whatever opens on a click and stays until it is put away. Each belongs
 * to the window whose control opened it and is drawn over it, right after
 * it, so a window lying over that one covers it too.
 *
 * <p>Every sub-window opens at the place its kind was last put, else where
 * its opener puts it, measured from its window, so it rides along as the
 * window moves. It is carried by its strip or its grip and resized by its
 * edges, past its window's edges too, and stays where it is put in its
 * window's room; where it stands then, and its size, is the place its kind
 * opens at from then on. A double-click on its strip puts it back where its
 * opener put it, and its kind forgets the place (WIN-43). It has no
 * padlock. Every sub-window is drawn just over its own window, the one
 * being typed in too, so a window in front covers it; one opened with no
 * window open, or one that belongs to the screen (the Views sub-window),
 * stands on the bare screen, over every window.</p>
 *
 * <p>A window closes by its cross, by Escape while it is the one in front,
 * or by the control that opened it; it closes with its window, too.
 * One of each kind is open at most, but a card for each person: pressed
 * for in another window, a kind's window moves there and opens afresh.</p>
 *
 * <p>"In front" is the window the player last pressed or opened; a press
 * anywhere else on the screen leaves no sub-window in front, and Escape
 * then closes the screen, taking the sub-windows with it until it opens
 * again.</p>
 */
public final class SubWindows {
    /**
     * How far in from its window's edges a sub-window's room lies:
     * its own frame and two clear pixels, so a window placed against the
     * edge keeps a framed button's clearance from its window's frame.
     */
    static final int ROOM_INSET = LostTalesUiWindowFrame.WIDTH + 2;
    /** Offset a window of a kind already standing at that place opens at: a window's cascade. */
    private static final int CASCADE_STEP = 24;

    /** Back to front. */
    private final List<SubWindow> stack = new ArrayList<SubWindow>();
    /** How strongly each window and sub-window shows under the ones in front of it: the screen's one stack. */
    private final StackFade stackFade;
    /** What a sub-window others lie over is drawn into, laid back over the world at its strength. */
    private final LostTalesUiLayerFade fade = new LostTalesUiLayerFade();
    private SubWindow focused;
    private Gesture gesture;
    private int screenWidth;
    private int screenHeight;

    SubWindows(StackFade stackFade) {
        this.stackFade = stackFade;
    }

    /** Takes the screen's size; called on every layout, which also runs on a resize. */
    public void bind(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /** The screen inside its margins: where every sub-window is held. */
    LostTalesUiHitBox screenRoom() {
        double margin = WindowPlacement.EDGE_MARGIN;
        return new LostTalesUiHitBox(margin, margin,
                Math.max(0.0D, this.screenWidth - 2.0D * margin),
                Math.max(0.0D, this.screenHeight - 2.0D * margin));
    }

    /**
     * The room the sub-windows of window {@code parentId} are measured
     * from: that window's box as drawn,
     * {@link #ROOM_INSET} in from its edges; for none, the screen inside
     * its margins. Null while the window is not drawn.
     */
    public LostTalesUiHitBox roomOf(String parentId) {
        if (parentId == null) {
            return screenRoom();
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
     * {@code parentId}, in front: where its kind was last put — at the
     * size it was given, or else its content's own — and
     * otherwise round {@code firstContentBox}, a screen box its opener
     * gives, hanging from the control pressed; a step down and along from
     * another of its kind standing there already. The window of that kind
     * and key already out, or still fading, comes forward instead, and
     * opens afresh in {@code parentId} when it stood in another. A window
     * that is not drawn leaves the window to the bare screen.
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
     * Puts a window in its room where its kind was last put, or round
     * {@code firstContentBox} while it never was; either way that box is
     * where a double-click puts it back.
     */
    private void place(SubWindow window, LostTalesUiHitBox room,
                       LostTalesUiHitBox firstContentBox) {
        SubWindowPlaces.Placement placed =
                SubWindowPlaces.of(window.kind);
        LostTalesUiHitBox screen = screenRoom();
        window.homeWidth = (int)Math.round(firstContentBox.width);
        window.homeHeight = (int)Math.round(firstContentBox.height)
                + SubWindow.STRIP_HEIGHT;
        window.homeX = firstContentBox.left - room.left;
        window.homeY = firstContentBox.top - SubWindow.STRIP_HEIGHT
                - room.top;
        if (placed != null) {
            window.sized = placed.isSized();
            if (window.sized) {
                window.wantedWidth = placed.width;
                window.wantedHeight = placed.height;
            } else {
                takeContentSize(window);
            }
            window.layOut(room, screen);
            window.x = placed.x(room.width, window.width);
            window.y = placed.y(room.height, window.height);
        } else {
            goHome(window);
        }
        window.layOut(room, screen);
    }

    /** Gives a window the place and size its opener gave it. */
    private static void goHome(SubWindow window) {
        window.sized = false;
        window.wantedWidth = window.homeWidth;
        window.wantedHeight = window.homeHeight;
        window.x = window.homeX;
        window.y = window.homeY;
    }

    /**
     * A double-click on a window's strip: it goes back where its opener put
     * it, at the size it gave, and its kind forgets the place it was put.
     */
    public void putBack(SubWindow window) {
        if (window == null || !window.isOpen()) {
            return;
        }
        if (this.gesture != null && this.gesture.window == window) {
            this.gesture = null;
        }
        SubWindowPlaces.forget(window.kind);
        goHome(window);
        if (window.room != null) {
            window.layOut(window.room, screenRoom());
        }
    }

    /**
     * Opens a window exactly as it stood as the screen closed, in the
     * window it stood in: what comes back with the screen. One whose window
     * has closed since stays closed.
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
        window.homeX = where.homeX;
        window.homeY = where.homeY;
        window.homeWidth = where.homeWidth;
        window.homeHeight = where.homeHeight;
        window.wantedWidth = where.width;
        window.wantedHeight = where.height;
        window.sized = where.sized;
        window.layOut(room, screenRoom());
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
            window.layOut(window.room, screenRoom());
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
                        && Math.abs(other.left - window.left) < 1.0D
                        && Math.abs(other.top - window.top) < 1.0D) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return;
            }
            window.x += CASCADE_STEP;
            window.y += CASCADE_STEP;
            window.layOut(room, screenRoom());
            window.x = window.left - room.left;
            window.y = window.top - room.top;
        }
    }

    /** Closes the window: it fades out where it stands, and its kind keeps its place. */
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

    /** Closes the window in front; answers whether there was one that closes. */
    public boolean closeFocused() {
        SubWindow front = focused();
        if (front == null) {
            return false;
        }
        close(front);
        return true;
    }

    /**
     * The windows open, back to front, and where they stand: what comes
     * back when the screen opens again.
     */
    public List<SubWindowPlaces.Reopening> openWindows() {
        List<SubWindowPlaces.Reopening> open =
                new ArrayList<SubWindowPlaces.Reopening>();
        for (SubWindow window : this.stack) {
            if (window.isOpen()) {
                open.add(new SubWindowPlaces.Reopening(window.kind,
                        window.key, window.content.sessionState(),
                        window.parentId, window.x, window.y,
                        window.wantedWidth, window.wantedHeight,
                        window.sized, window.homeX, window.homeY,
                        window.homeWidth, window.homeHeight));
            }
        }
        return open;
    }

    /* ---- The pointer ---- */

    /**
     * What a point is on among the sub-windows, front to back: a list a
     * window's field opens, over everything; then an edge, a strip's
     * cross or grip, a strip, or the content, which answers for
     * itself. A window a window in front of its own covers at the point
     * is not there; null where no sub-window is.
     */
    public WindowHover hoverAt(double x, double y) {
        List<SubWindow> frontFirst = new ArrayList<SubWindow>(this.stack);
        Collections.reverse(frontFirst);
        for (SubWindow window : frontFirst) {
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
        for (SubWindow window : frontFirst) {
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
            } else if (window.gripContains(x, y)) {
                hover = new WindowHover(WindowHover.Kind.SUB_WINDOW_GRIP);
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
     * Whether a window lying over the window's own covers the point: a
     * window stacked in front of it, where it is drawn. Those on the bare
     * screen are drawn over every window and never are.
     */
    private boolean covered(SubWindow window, double x, double y) {
        return window.parentId != null
                && WindowFrame.coveredAbove(window.parentId, x, y);
    }

    /* ---- Drawing ---- */

    /**
     * Readies the windows for a frame: a window faded out altogether goes,
     * one whose window has closed closes with it, and every window waits
     * undrawn until its window draws it.
     */
    public void beginFrame() {
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
     * Adds the sub-windows of window {@code parentId} (null for the bare
     * screen) to the screen's stack, back to front as they are drawn, each
     * with the box it stood in when last drawn; one not drawn then neither
     * fades nor is faded.
     */
    void addToStack(String parentId, List<Object> keys,
                    List<LostTalesUiHitBox> boxes) {
        for (SubWindow sub : this.stack) {
            if (!sub.belongsTo(parentId)) {
                continue;
            }
            keys.add(sub);
            boxes.add(sub.hidden || !sub.isOpen() || sub.strip == null
                    || sub.shownShare <= 0.0F ? null
                    : new LostTalesUiHitBox(sub.drawnLeft, sub.drawnTop,
                            sub.width, sub.height));
        }
    }

    /**
     * Draws the sub-windows of window {@code parentId} — null for
     * the bare screen — back to front, laid out as it was just drawn,
     * each registering the box it covers and its resize band; then what
     * their fields open, over them. {@code hover} says what the pointer
     * is on, at {@code pointerX}/{@code pointerY}. With {@code barless}
     * no window is open, and a window that works on the input bar waits
     * undrawn.
     */
    public void draw(Minecraft minecraft, FontRenderer font,
              PointerRegions regions, WindowHover hover, double pointerX,
              double pointerY, boolean barless, String parentId) {
        LostTalesUiHitBox room = roomOf(parentId);
        if (room == null) {
            return;
        }
        LostTalesUiHitBox screen = screenRoom();
        long now = System.nanoTime();
        List<SubWindow> drawn = new ArrayList<SubWindow>();
        for (SubWindow window : new ArrayList<SubWindow>(this.stack)) {
            if (!window.belongsTo(parentId)
                    || (barless && window.content.needsInputBar())) {
                continue;
            }
            window.hidden = false;
            window.layOut(room, screen);
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
        // Laid on the display's grid and rising the last few pixels into
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
        float opacity = share * WindowStyle.opacity(minecraft);
        int surface = WindowStyle.insetArgb(opacity);
        int stripSurface = WindowStyle.stripArgb(opacity);
        boolean mine = hover != null && hover.subWindow == window;
        boolean onClose = mine && hover.is(WindowHover.Kind.SUB_WINDOW_CLOSE);
        boolean pressed = Mouse.isButtonDown(0);
        window.closeMotion.advance(now, onClose, onClose, onClose && pressed);
        boolean carried = this.gesture != null && this.gesture.window == window
                && this.gesture.edge == null && this.gesture.active;
        double sinceGrip = window.gripNanos == 0L ? 0.0D
                : (now - window.gripNanos) / 1.0E9D;
        window.gripNanos = now;
        window.gripFade = WindowStyle.hoverFade(window.gripFade, carried
                || mine && hover.is(WindowHover.Kind.SUB_WINDOW_GRIP),
                sinceGrip);
        boolean onContent = mine && !onClose
                && !hover.is(WindowHover.Kind.SUB_WINDOW_STRIP)
                && !hover.is(WindowHover.Kind.SUB_WINDOW_GRIP)
                && !hover.is(WindowHover.Kind.SUB_WINDOW_RESIZE);
        float left = wholeLeft;
        float top = wholeTop;
        float right = left + window.width;
        float bottom = top + window.height;
        // Whatever lies behind it, its own window included, is cut away
        // where it stands, and the world under it softened, as under
        // every window.
        LostTalesUiHitBox stands = new LostTalesUiHitBox(exactLeft, exactTop,
                window.width, window.height);
        WindowDrawing.cutBehind(stands, share);
        WindowDrawing.softenBehind(stands, share);
        // One that windows or sub-windows in front of it lie over fades
        // as one picture over the world it now stands on.
        float strength = this.stackFade.shareOf(window);
        int ring = WindowPlacement.FRAME_WIDTH + LostTalesUiInk.SHADOW_OFFSET;
        boolean fading = strength < 1.0F && this.fade.begin(minecraft,
                exactLeft - ring, exactTop - ring, window.width + 2 * ring,
                window.height + 2 * ring);
        GL11.glPushMatrix();
        GL11.glTranslatef(window.fractionX, window.fractionY, 0.0F);
        try {
            window.strip = SubWindowStrip.layOut(font, wholeLeft,
                    wholeLeft + window.width, wholeTop, window.title(),
                    window.content.stripIcon() != null);
            // The strip's surface and the content's side by side, never
            // one over the other, with the frame's ring round them; the
            // frame's edges lie over the ring.
            SubWindowStrip.drawSurfaces(left, top, right, bottom,
                    stripSurface, surface, alpha);
            window.strip.drawContent(font, wholeLeft, wholeTop,
                    window.content.stripIcon(), window, alpha);
            window.content.draw(minecraft, window.wholeContentBox(),
                    exactLeft, exactTop + SubWindow.STRIP_HEIGHT,
                    onContent ? pointerX - window.fractionX : WindowHover.AWAY,
                    onContent ? pointerY - window.fractionY : WindowHover.AWAY,
                    alpha, surface >>> 24);
            LostTalesUiWindowFrame.drawEdges(left, top, right, bottom, alpha);
        } finally {
            GL11.glPopMatrix();
            if (fading) {
                this.fade.end(minecraft, strength);
            }
        }
        if (window.isOpen()) {
            int border = WindowGestures.RESIZE_BORDER;
            regions.addScreen(wholeLeft - border, wholeTop - border,
                    (int)Math.ceil(exactLeft + window.width) + border,
                    (int)Math.ceil(exactTop + window.height) + border);
        }
    }

    /** The tip of what the pointer rests on in a sub-window; null for none. */
    public static PointerTip tip(FontRenderer font, WindowHover hover) {
        if (hover == null || hover.subWindow == null
                || !hover.subWindow.isOpen()) {
            return null;
        }
        return hover.subWindow.content.tip(font);
    }

    /**
     * What the pointer rests on in a sub-window, for the tip's timing: the
     * tip's words in that sub-window; empty for no tip.
     */
    public static String tipKey(WindowHover hover, PointerTip tip) {
        if (tip == null || hover == null || hover.subWindow == null) {
            return "";
        }
        return System.identityHashCode(hover.subWindow) + ":" + tip.key();
    }

    /* ---- Moving and resizing ---- */

    /** Takes hold of a window's strip or grip: it moves once the pointer travels. */
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
     * Follows the pointer with the window held, in its window's room:
     * asked on every frame as well as on every move of the pointer, so the
     * window glides with it as a window does.
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
        LostTalesUiHitBox screen = screenRoom();
        double dx = x - held.pressX;
        double dy = y - held.pressY;
        if (held.edge == null) {
            window.x = held.startLeft + dx - window.room.left;
            window.y = held.startTop + dy - window.room.top;
            window.layOut(window.room, screen);
            return;
        }
        resize(window, held, dx, dy, screen);
    }

    /**
     * Resizes the window by the pointer's travel, its far sides where they
     * were, its moving sides held on the screen.
     */
    private static void resize(SubWindow window, Gesture held,
                               double dx, double dy, LostTalesUiHitBox screen) {
        WindowGestures.ResizeEdge edge = held.edge;
        double screenRight = screen.left + screen.width;
        double screenBottom = screen.top + screen.height;
        double left = held.startLeft;
        double top = held.startTop;
        double right = held.startLeft + held.startWidth;
        double bottom = held.startTop + held.startHeight;
        if (edge.horizontal) {
            if (edge.fromLeft) {
                left = Math.max(screen.left, left + dx);
            } else {
                right = Math.min(screenRight, right + dx);
            }
        }
        if (edge.vertical) {
            if (edge.fromTop) {
                top = Math.max(screen.top, top + dy);
            } else {
                bottom = Math.min(screenBottom, bottom + dy);
            }
        }
        int width = SubWindow.fitted((int)Math.round(right - left),
                window.minWidth(), (int)Math.floor(screen.width));
        int height = SubWindow.fitted((int)Math.round(bottom - top),
                window.minHeight(), (int)Math.floor(screen.height));
        window.wantedWidth = width;
        window.wantedHeight = height;
        window.x = (edge.horizontal && edge.fromLeft ? right - width
                : held.startLeft) - window.room.left;
        window.y = (edge.vertical && edge.fromTop ? bottom - height
                : held.startTop) - window.room.top;
        window.layOut(window.room, screen);
    }

    /**
     * Lets go of the window held where it stands; a resize has given it
     * its size. Where it stands in its window's room, and its size, become
     * the place its kind opens at.
     */
    public void release() {
        Gesture held = this.gesture;
        this.gesture = null;
        if (held == null || !held.active) {
            return;
        }
        SubWindow window = held.window;
        if (held.edge != null) {
            window.sized = true;
        }
        if (window.room == null) {
            return;
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

    /** A window held by its strip or an edge, from where it stood. */
    private static final class Gesture {
        final SubWindow window;
        /** The edge held; null while the window is moved by its strip. */
        final WindowGestures.ResizeEdge edge;
        final double pressX;
        final double pressY;
        /** Where it stood on the screen. */
        final double startLeft;
        final double startTop;
        /** Where it stood in its room: what a cancelled hold gives back. */
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
            this.startLeft = window.left;
            this.startTop = window.top;
            this.startX = window.x;
            this.startY = window.y;
            this.startWidth = window.width;
            this.startHeight = window.height;
            this.startWantedWidth = window.wantedWidth;
            this.startWantedHeight = window.wantedHeight;
        }
    }
}
