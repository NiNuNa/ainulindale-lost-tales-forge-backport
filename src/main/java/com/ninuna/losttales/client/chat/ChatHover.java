package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;

/**
 * What is under the pointer in the chat screen: the one answer a
 * frame's highlights, tips and cards are drawn from, the pointer's pose
 * is taken from, and a press acts on.
 *
 * <p>The screen finds it once per frame from the top of what is drawn
 * down, in the order a press is handled: snap assist, the snap layouts,
 * the completion lists, the small windows front to back, a window's
 * edge, the tab rows, the bar's controls, anything else painted above
 * the lines, a window's own controls, the runs of the lines, and the
 * window itself. Only the control found sees the pointer. Everything
 * under it is asked with the pointer {@link #AWAY}, so nothing lights
 * beneath a small window and nothing under it answers a press.</p>
 */
final class ChatHover {
    /**
     * The pointer handed to a control something else covers: every
     * comparison with it is false, so no hit test finds anything.
     */
    static final double AWAY = Double.NaN;

    /** What the pointer is on. */
    enum Kind {
        /** Nothing of the chat's. */
        NONE,
        /** A row of a menu's window that does something. */
        MENU_ENTRY,
        /** A menu's window where no row answers: a header, a display row, the field, padding. */
        MENU,
        /** The snap layouts under a fullscreen control: a zone, or the panel round them. */
        SNAP_LAYOUT,
        /** A card of snap assist, offering a window for an empty zone. */
        SNAP_ASSIST,
        /** The {@code +} the empty state offers. */
        EMPTY_PLUS,
        /** A row of an open completion list. */
        SUGGESTION,
        /** An open completion list's own padding. */
        SUGGESTIONS,
        /** A row of the list a small window's field opens. */
        FIELD_SUGGESTION,
        /** A page in a window: the journal, the party. */
        PAGE,
        /** The band just outside a small window, which resizes it. */
        SMALL_WINDOW_RESIZE,
        /** The cross on a small window's strip. */
        SMALL_WINDOW_CLOSE,
        /** A small window's strip, which moves it. */
        SMALL_WINDOW_STRIP,
        /** A small window's content where nothing of it answers. */
        SMALL_WINDOW,
        /** A cell of a picker's window. */
        PICKER_CELL,
        /** A section label of a picker's window that folds. */
        PICKER_LABEL,
        /** A picker's window's own content: search row, gaps, scrollbar. */
        PICKER,
        /** The border just outside an unlocked window. */
        RESIZE,
        /** A tab, a tab's control, an end control or the grip. */
        TAB_ROW,
        /**
         * A window's tool strip: the area's button, the member list's
         * button, or the message search's well and its controls.
         */
        TOOL_STRIP,
        /** The bare stretch of a tab row. */
        STRIP,
        /** The bar strip of a window other than the one typed in. */
        OTHER_BAR,
        CHARACTER_BUTTON,
        INDICATOR,
        SEND_BUTTON,
        TOOLBAR_TOGGLE,
        /** A picker's button on the bar. */
        PICKER_BUTTON,
        /** Anything else painted above the lines, answering to nothing. */
        OVERLAY,
        /** The pill that takes a scrolled view home. */
        JUMP_PILL,
        /** The cross on the reply or edit chip. */
        REPLY_CHIP,
        /** A control of the hovered message's toolbar. */
        MESSAGE_TOOLBAR,
        /** A window's scrollbar. */
        SCROLLBAR,
        /** A run of a line. */
        LINE,
        /** A window's member list: a member's row, or the list around them. */
        MEMBER_LIST,
        /** A window's member list's left edge, which resizes the list. */
        MEMBER_LIST_EDGE,
        /** A window's lines where no run stands, its timestamp area included. */
        WINDOW
    }

    static final ChatHover NONE = new ChatHover(Kind.NONE);

    final Kind kind;
    ChatMenu.Entry menuEntry;
    /** Why the menu's row under the pointer cannot be taken; empty for none. */
    String menuTip = "";
    ChatWindow window;
    ChatWindowFrame frame;
    ChatChannelTabBar.Row row;
    ChatChannelTabBar.Hit tabHit;
    /** On a tool strip, which of its controls; null anywhere else. */
    ChatToolStrip.Part stripPart;
    /** Whether the pointer is on the grip's own glyph. */
    boolean overGrip;
    /** On a small window, the window; null anywhere else. */
    ChatSmallWindow smallWindow;
    /** On a small window's resize band, the edge. */
    ChatWindowGestures.ResizeEdge smallEdge;
    ChatPickerPanel picker;
    ChatPickerPanel.Entry pickerEntry;
    ChatInputCompletion.Slot suggestion;
    /** The row of a field's list the pointer is on; -1 between rows. */
    int fieldSuggestion = -1;
    int toolbarKind = -1;
    /** On the snap layouts, which zone of the panel; -1 on the panel round them. */
    int snapZone = -1;
    /** On snap assist, the card under the pointer. */
    ChatSnapAssist.Card assistCard;
    /**
     * On a window's lines, the chat line id of the message whose
     * delivery mark is under the pointer; 0 anywhere else.
     */
    int markLineId;
    ChatWindowGestures.ResizeTarget resize;
    LostTalesChatOverlayRenderer.Hit line;
    LostTalesChatHoverCard.Found person;
    /** On a member list, the member whose row it is; null on the list around them. */
    com.ninuna.losttales.network.packet.LostTalesChatMembersPacket.Member member;
    /**
     * Whether a press on the line or the window does something: a run
     * that answers a click, a person, a window it brings forward, or a
     * stack of windows it cycles.
     */
    boolean acts;

    ChatHover(Kind kind) {
        this.kind = kind;
    }

    boolean is(Kind kind) {
        return this.kind == kind;
    }

    /** Whether the pointer is on this window's tab row, a tab or its bare stretch. */
    boolean isOnRowOf(ChatWindowFrame frame) {
        return frame != null && this.frame == frame
                && (this.kind == Kind.TAB_ROW || this.kind == Kind.STRIP
                        || this.kind == Kind.TOOL_STRIP);
    }

    /** Whether a press here does something: what earns the hand. */
    boolean acts() {
        switch (this.kind) {
            case MENU_ENTRY:
            case EMPTY_PLUS:
            case SUGGESTION:
            case FIELD_SUGGESTION:
            case PICKER_CELL:
            case PICKER_LABEL:
            case SMALL_WINDOW_CLOSE:
            case SMALL_WINDOW_STRIP:
            case TAB_ROW:
            case OTHER_BAR:
            case CHARACTER_BUTTON:
            case INDICATOR:
            case SEND_BUTTON:
            case TOOLBAR_TOGGLE:
            case PICKER_BUTTON:
            case JUMP_PILL:
            case REPLY_CHIP:
            case SCROLLBAR:
                return true;
            case MESSAGE_TOOLBAR:
                // A control that cannot be taken here only says why.
                return this.frame == null
                        || this.frame.toolbarWhy(this.toolbarKind).length() == 0;
            case STRIP:
                // A strip moves its window only while the window is not
                // locked; a locked one is inert.
                return this.window != null && !this.window.isLocked();
            case TOOL_STRIP:
                // Every control acts; the field takes the caret without
                // a hand.
                return this.stripPart != null
                        && this.stripPart != ChatToolStrip.Part.FIELD;
            case SNAP_LAYOUT:
                return this.snapZone >= 0;
            case SNAP_ASSIST:
                return this.assistCard != null;
            case LINE:
            case MEMBER_LIST:
            case WINDOW:
            case PAGE:
                return this.acts;
            default:
                return false;
        }
    }

    /** The pointer's pose over it: a resize over an edge, the hand where a press acts. */
    LostTalesMapCursor.Pose pose() {
        if (this.kind == Kind.RESIZE && this.resize != null) {
            return ChatWindowGestures.cursorPose(this.resize.edge);
        }
        if (this.kind == Kind.SMALL_WINDOW_RESIZE && this.smallEdge != null) {
            return ChatWindowGestures.cursorPose(this.smallEdge);
        }
        if (this.kind == Kind.MEMBER_LIST_EDGE) {
            return LostTalesMapCursor.Pose.RESIZE_HORIZONTAL;
        }
        return acts() ? LostTalesMapCursor.Pose.HAND
                : LostTalesMapCursor.Pose.ARROW;
    }
}
