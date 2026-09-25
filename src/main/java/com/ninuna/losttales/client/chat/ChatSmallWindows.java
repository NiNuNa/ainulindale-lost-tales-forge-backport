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
 * whatever opens on a click and stays until it is put away. Each belongs to
 * the chat window whose control opened it and lives inside it, as a window
 * lives on a screen: it is drawn with that window, so a window lying over
 * it covers it too, rides along as that window moves, and is moved by its
 * title strip and resized by its edges anywhere inside it, never past its
 * edges and never sticking to anything. The small windows of the window
 * being typed in are drawn over every window, as its input bar is; one
 * opened with no chat window open stands on the bare screen.
 *
 * <p>A window closes by its cross, by Escape while it is the one in front,
 * or by the control that opened it; it closes with its chat window, too.
 * One of each kind is open at most, but a card for each person: pressed
 * for in another chat window, a kind's window moves there. None counts
 * toward the chat's eight windows.</p>
 *
 * <p>"In front" is the window the player last pressed or opened; a press
 * anywhere else in the chat leaves no small window in front, and Escape
 * then closes the chat, taking the small windows with it until it opens
 * again.</p>
 */
final class ChatSmallWindows {
    /** The tab's accent and name, which no channel colours here. */
    static final int ACCENT_RGB = LostTalesChatVisualStyle.IVORY;
    /**
     * How far in from its chat window's edges a small window's room lies:
     * its own frame and two clear pixels, so a window pushed against the
     * edge keeps a framed button's clearance from its chat window's frame.
     */
    static final int ROOM_INSET = LostTalesUiWindowFrame.WIDTH + 2;
    /** Offset a window of a kind already standing at that place opens at: a chat window's cascade. */
    private static final int CASCADE_STEP = 24;

    /** Back to front. */
    private final List<ChatSmallWindow> stack = new ArrayList<ChatSmallWindow>();
    private ChatSmallWindow focused;
    private Gesture gesture;
    private int screenWidth;
    private int screenHeight;
    /** The chat window whose small windows are drawn over every window this frame. */
    private String topParentId;

    /** Takes the screen's size; called on every layout, which also runs on a resize. */
    void bind(int screenWidth, int screenHeight) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
    }

    /**
     * The room the small windows of chat window {@code parentId} have: that
     * window's box as drawn, {@link #ROOM_INSET} in from its edges; for
     * none, the screen inside its margins. Null while the chat window is
     * not drawn.
     */
    LostTalesUiHitBox roomOf(String parentId) {
        if (parentId == null) {
            double margin = ChatWindowPlacement.EDGE_MARGIN;
            return new LostTalesUiHitBox(margin, margin,
                    Math.max(0.0D, this.screenWidth - 2.0D * margin),
                    Math.max(0.0D, this.screenHeight - 2.0D * margin));
        }
        ChatWindowFrame frame = ChatWindowFrame.find(parentId);
        if (frame == null || !frame.drawn
                || ChatWindowLayout.window(parentId) == null) {
            return null;
        }
        LostTalesUiHitBox box = frame.drawnBox();
        return new LostTalesUiHitBox(box.left + ROOM_INSET,
                box.top + ROOM_INSET,
                Math.max(0.0D, box.width - 2.0D * ROOM_INSET),
                Math.max(0.0D, box.height - 2.0D * ROOM_INSET));
    }

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
     * Opens a window of {@code kind} holding {@code content} in chat window
     * {@code parentId}, in front: where the player left one of its kind
     * last — at the size they gave it, or else its content's own — and
     * otherwise round {@code firstContentBox}, a screen box, where the
     * popup it replaces always opened; a step down and along from another
     * of its kind standing there already. The window of that kind and key
     * already out, or still fading, comes forward instead, moved into
     * {@code parentId} when it stood in another. A chat window that is not
     * drawn leaves the window to the bare screen.
     */
    ChatSmallWindow open(ChatSmallWindowKind kind, String key,
                         ChatSmallWindowContent content, String parentId,
                         LostTalesUiHitBox firstContentBox) {
        String parent = roomOf(parentId) == null ? null : parentId;
        LostTalesUiHitBox room = roomOf(parent);
        ChatSmallWindow window = find(kind, key);
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
        window = new ChatSmallWindow(kind, key, content, parent);
        place(window, room, firstContentBox);
        cascade(window, room);
        return add(window);
    }

    /**
     * Puts a window in its room where the player left its kind, or round
     * {@code firstContentBox} while they have not.
     */
    private static void place(ChatSmallWindow window, LostTalesUiHitBox room,
                              LostTalesUiHitBox firstContentBox) {
        ChatSmallWindowPlacements.Placement placed =
                ChatSmallWindowPlacements.of(window.kind);
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
                    + ChatSmallWindow.STRIP_HEIGHT;
            window.x = firstContentBox.left - room.left;
            window.y = firstContentBox.top - ChatSmallWindow.STRIP_HEIGHT
                    - room.top;
        }
        window.layOut(room);
    }

    /**
     * Opens a window exactly where it stood as the chat closed, in the
     * chat window it stood in: what comes back with the chat. One whose
     * chat window has closed since stays closed.
     */
    ChatSmallWindow reopen(ChatSmallWindowPlacements.Reopening where,
                           ChatSmallWindowContent content) {
        ChatSmallWindow window = find(where.kind, where.key);
        if (window != null) {
            return window;
        }
        LostTalesUiHitBox room = roomOf(where.parentId);
        if (room == null) {
            return null;
        }
        window = new ChatSmallWindow(where.kind, where.key, content,
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
    void refit(ChatSmallWindow window) {
        if (window == null || window.sized) {
            return;
        }
        takeContentSize(window);
        if (window.room != null) {
            window.layOut(window.room);
        }
    }

    /** The content's own size, and the strip over it. */
    private static void takeContentSize(ChatSmallWindow window) {
        window.wantedWidth = window.content.naturalWidth();
        window.wantedHeight = ChatSmallWindow.STRIP_HEIGHT
                + window.content.naturalHeight(window.wantedWidth);
    }

    private ChatSmallWindow add(ChatSmallWindow window) {
        this.stack.add(window);
        window.content.opened();
        focus(window);
        return window;
    }

    /** Steps the window down and along past any open window of its kind at its place in the same room. */
    private void cascade(ChatSmallWindow window, LostTalesUiHitBox room) {
        for (int step = 0; step < this.stack.size(); step++) {
            boolean taken = false;
            for (ChatSmallWindow other : this.stack) {
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

    /**
     * What a window's control does: opens the window of that kind and key
     * in chat window {@code parentId}, or closes it where it is already out
     * there; out in another chat window, it moves over.
     */
    void toggle(ChatSmallWindowKind kind, String key,
                ChatSmallWindowContent content, String parentId,
                LostTalesUiHitBox firstContentBox) {
        String parent = roomOf(parentId) == null ? null : parentId;
        ChatSmallWindow window = find(kind, key);
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
                        window.parentId, window.x, window.y,
                        window.wantedWidth, window.wantedHeight,
                        window.sized));
            }
        }
        return open;
    }

    /* ---- The pointer ---- */

    /**
     * What a point is on among the small windows, front to back: a list a
     * window's field opens, over everything; then an edge, a strip's
     * cross, a strip, or the content, which answers for itself. A window
     * a chat window in front of its own covers at the point is not there;
     * null where no small window is.
     */
    ChatHover hoverAt(double x, double y) {
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            ChatSmallWindow window = this.stack.get(index);
            if (!window.isOpen() || window.hidden) {
                continue;
            }
            ChatHover popup = window.content.popupHoverAt(
                    x - window.fractionX, y - window.fractionY);
            if (popup != null) {
                popup.smallWindow = window;
                return popup;
            }
        }
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            ChatSmallWindow window = this.stack.get(index);
            if (!window.isOpen() || window.hidden || covered(window, x, y)) {
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

    /**
     * Whether a chat window lying over the window's own covers the point:
     * the small windows of the window being typed in, and those on the bare
     * screen, are drawn over every window and never are.
     */
    private boolean covered(ChatSmallWindow window, double x, double y) {
        if (window.parentId == null
                || window.parentId.equals(this.topParentId)) {
            return false;
        }
        ChatWindowFrame front = ChatWindowFrame.drawnAt(x, y);
        return front != null && !front.windowId.equals(window.parentId);
    }

    /* ---- Drawing ---- */

    /**
     * Readies the windows for a frame whose small windows of chat window
     * {@code topParentId} are drawn over every window: a window faded out
     * altogether goes, one whose chat window has closed closes with it, and
     * every window waits undrawn until its chat window draws it.
     */
    void beginFrame(String topParentId) {
        this.topParentId = topParentId;
        for (int index = this.stack.size() - 1; index >= 0; index--) {
            ChatSmallWindow window = this.stack.get(index);
            if (window.parentId != null
                    && ChatWindowLayout.window(window.parentId) == null) {
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
     * Draws the small windows of chat window {@code parentId} — null for
     * the bare screen — back to front, laid out in its room as it was just
     * drawn, each registering the box it covers and its resize band; then
     * what their fields open, over them. {@code hover} says what the
     * pointer is on, at {@code pointerX}/{@code pointerY}. With
     * {@code barless} no chat window is open, and a window that works on
     * the input bar waits undrawn.
     */
    void draw(Minecraft minecraft, FontRenderer font,
              ChatPointerRegions regions, ChatHover hover, double pointerX,
              double pointerY, boolean barless, String parentId) {
        LostTalesUiHitBox room = roomOf(parentId);
        if (room == null) {
            return;
        }
        long now = System.nanoTime();
        List<ChatSmallWindow> drawn = new ArrayList<ChatSmallWindow>();
        for (ChatSmallWindow window
                : new ArrayList<ChatSmallWindow>(this.stack)) {
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
        for (ChatSmallWindow window : drawn) {
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
        // A window its room cannot hold is cut by the room's edge, and so
        // is whatever its content cuts for itself.
        boolean cut = window.overflowsRoom();
        boolean clipped = false;
        if (cut) {
            ChatSmallWindowContent.setOuterClip(window.room);
            clipped = ChatSmallWindowContent.beginClip(minecraft,
                    window.room.left, window.room.top, window.room.width,
                    window.room.height);
        }
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
            if (cut) {
                ChatSmallWindowContent.endClip(clipped);
                ChatSmallWindowContent.setOuterClip(null);
            }
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

    /**
     * Follows the pointer with the window held, inside its room: asked on
     * every frame as well as on every move of the pointer, so the window
     * glides with it as a chat window does.
     */
    void drag(double x, double y) {
        Gesture held = this.gesture;
        if (held == null || held.window.room == null) {
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
        LostTalesUiHitBox room = window.room;
        double dx = x - held.pressX;
        double dy = y - held.pressY;
        if (held.edge == null) {
            window.x = ChatSmallWindow.held(held.startX + dx,
                    room.width - window.width);
            window.y = ChatSmallWindow.held(held.startY + dy,
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
    private static void resize(ChatSmallWindow window, Gesture held,
                               double dx, double dy, LostTalesUiHitBox room) {
        ChatWindowGestures.ResizeEdge edge = held.edge;
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
        int width = ChatSmallWindow.fitted((int)Math.round(right - left),
                window.minWidth(), (int)Math.floor(room.width));
        int height = ChatSmallWindow.fitted((int)Math.round(bottom - top),
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
    void release() {
        Gesture held = this.gesture;
        this.gesture = null;
        if (held == null || !held.active || held.window.room == null) {
            return;
        }
        ChatSmallWindow window = held.window;
        if (held.edge != null) {
            window.sized = true;
        }
        window.x = window.left - window.room.left;
        window.y = window.top - window.room.top;
        ChatSmallWindowPlacements.remember(window.kind, window.x, window.y,
                window.width, window.height, window.sized,
                window.room.width, window.room.height);
    }

    /** Puts the window held back where it was taken from. */
    void cancel() {
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
        final ChatSmallWindow window;
        /** The edge held; null while the window is moved by its strip. */
        final ChatWindowGestures.ResizeEdge edge;
        final double pressX;
        final double pressY;
        final double startX;
        final double startY;
        final int startWidth;
        final int startHeight;
        final int startWantedWidth;
        final int startWantedHeight;
        boolean active;

        Gesture(ChatSmallWindow window, ChatWindowGestures.ResizeEdge edge,
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
