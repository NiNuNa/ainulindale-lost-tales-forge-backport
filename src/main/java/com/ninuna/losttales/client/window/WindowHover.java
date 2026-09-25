package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;

/**
 * What is under the pointer on the window screen: the one answer a
 * frame's highlights, tips and cards are drawn from, the pointer's pose
 * is taken from, and a press acts on.
 *
 * <p>The screen finds it once per frame from the top of what is drawn
 * down, in the order a press is handled ({@link WindowScreen}). Only the
 * control found sees the pointer. Everything under it is asked with the
 * pointer {@link #AWAY}, so nothing lights beneath a sub-window and
 * nothing under it answers a press.</p>
 *
 * <p>The window's own parts answer with a kind of their own. A system
 * with work of its own on the screen ({@link ScreenPart}) answers with
 * {@link Kind#CONTENT} and a hover of its own kind, which says what of
 * its work the pointer is on.</p>
 */
public class WindowHover {
    /**
     * The pointer handed to a control something else covers: every
     * comparison with it is false, so no hit test finds anything.
     */
    public static final double AWAY = Double.NaN;

    /** What the pointer is on. */
    public enum Kind {
        /** Nothing that answers. */
        NONE,
        /** The snap layouts under a fullscreen control: a zone, or the panel round them. */
        SNAP_LAYOUT,
        /** A card of snap assist, offering a window for an empty zone. */
        SNAP_ASSIST,
        /** A page in a window: the journal, the party. */
        PAGE,
        /** The band just outside a sub-window, which resizes it. */
        SUB_WINDOW_RESIZE,
        /** The cross on a sub-window's strip. */
        SUB_WINDOW_CLOSE,
        /** A sub-window's strip, which moves it. */
        SUB_WINDOW_STRIP,
        /** A sub-window's content where nothing of it answers. */
        SUB_WINDOW,
        /** The border just outside an unlocked window. */
        RESIZE,
        /** A tab, a tab's control, an end control or the grip. */
        TAB_ROW,
        /** A window's tool strip: its panel button, its cog, its member list's button or its search. */
        TOOL_STRIP,
        /** The bare stretch of a tab row. */
        STRIP,
        /** Anything else painted over the windows, answering to nothing. */
        OVERLAY,
        /** What a screen part draws: its hover says what. */
        CONTENT
    }

    public static final WindowHover NONE = new WindowHover(Kind.NONE);

    public final Kind kind;
    public Window window;
    public WindowFrame frame;
    public TabRow.Row row;
    public TabRow.Hit tabHit;
    /** On a tool strip, which of its controls; null anywhere else. */
    public ToolStrip.Part stripPart;
    /** Whether the pointer is on the grip's own glyph. */
    public boolean overGrip;
    /** On a sub-window, the sub-window; null anywhere else. */
    public SubWindow subWindow;
    /** On a sub-window's resize band, the edge. */
    public WindowGestures.ResizeEdge subEdge;
    /** On the snap layouts, which zone of the panel; -1 on the panel round them. */
    public int snapZone = -1;
    /** On snap assist, the card under the pointer. */
    public SnapAssist.Card assistCard;
    public WindowGestures.ResizeTarget resize;
    /** Whether a press on the page or the content does something. */
    public boolean acts;

    public WindowHover(Kind kind) {
        this.kind = kind;
    }

    public boolean is(Kind kind) {
        return this.kind == kind;
    }

    /** Whether the pointer is on this window's tab row, a tab, its bare stretch or its tool strip. */
    public boolean isOnRowOf(WindowFrame frame) {
        return frame != null && this.frame == frame
                && (this.kind == Kind.TAB_ROW || this.kind == Kind.STRIP
                        || this.kind == Kind.TOOL_STRIP);
    }

    /** Whether a press here does something: what earns the hand. */
    public boolean acts() {
        switch (this.kind) {
            case SUB_WINDOW_CLOSE:
            case SUB_WINDOW_STRIP:
            case TAB_ROW:
                return true;
            case STRIP:
                // A strip moves its window only while the window is not
                // locked; a locked one is inert.
                return this.window != null && !this.window.isLocked();
            case TOOL_STRIP:
                // Every control acts; the field takes the caret without
                // a hand.
                return this.stripPart != null
                        && this.stripPart != ToolStrip.Part.FIELD;
            case SNAP_LAYOUT:
                return this.snapZone >= 0;
            case SNAP_ASSIST:
                return this.assistCard != null;
            case PAGE:
            case CONTENT:
                return this.acts;
            default:
                return false;
        }
    }

    /** The pointer's pose over it: a resize over an edge, the hand where a press acts. */
    public LostTalesMapCursor.Pose pose() {
        if (this.kind == Kind.RESIZE && this.resize != null) {
            return WindowGestures.cursorPose(this.resize.edge);
        }
        if (this.kind == Kind.SUB_WINDOW_RESIZE && this.subEdge != null) {
            return WindowGestures.cursorPose(this.subEdge);
        }
        return acts() ? LostTalesMapCursor.Pose.HAND
                : LostTalesMapCursor.Pose.ARROW;
    }
}
