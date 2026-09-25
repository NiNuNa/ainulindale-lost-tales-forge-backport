package com.ninuna.losttales.client.chat;

import com.ninuna.losttales.client.mapmarker.LostTalesMapCursor;
import com.ninuna.losttales.client.window.WindowHover;

/**
 * What of the chat is under the pointer: the bar and its lists, the
 * menus and pickers, and a conversation's lines, member list, toolbar,
 * scrollbar and jump pill. The window screen finds it through the chat's
 * part ({@link ChatScreenPart}) at the points of its search where the
 * chat's things are drawn: the completion lists over everything, the
 * bar's controls under the windows' rows, and the conversation's own
 * controls and lines under anything painted over them.
 */
public final class ChatHover extends WindowHover {
    /** What of the chat the pointer is on. */
    public enum Kind {
        /** A row of a menu's window that does something. */
        MENU_ENTRY,
        /** A menu's window where no row answers: a header, a display row, the field, padding. */
        MENU,
        /** The {@code +} the empty state offers. */
        EMPTY_PLUS,
        /** A row of an open completion list. */
        SUGGESTION,
        /** An open completion list's own padding. */
        SUGGESTIONS,
        /** A row of the list a menu's field opens. */
        FIELD_SUGGESTION,
        /** A cell of a picker's window. */
        PICKER_CELL,
        /** A section label of a picker's window that folds. */
        PICKER_LABEL,
        /** A picker's window's own content: search row, gaps, scrollbar. */
        PICKER,
        /** The bar strip of a window other than the one typed in. */
        OTHER_BAR,
        CHARACTER_BUTTON,
        INDICATOR,
        SEND_BUTTON,
        TOOLBAR_TOGGLE,
        /** A picker's button on the bar. */
        PICKER_BUTTON,
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

    final Kind chatKind;
    ChatMenu.Entry menuEntry;
    /** Why the menu's row under the pointer cannot be taken; empty for none. */
    String menuTip = "";
    ChatPickerPanel picker;
    ChatPickerPanel.Entry pickerEntry;
    ChatInputCompletion.Slot suggestion;
    /** The row of a field's list the pointer is on; -1 between rows. */
    int fieldSuggestion = -1;
    int toolbarKind = -1;
    /**
     * On a window's lines, the chat line id of the message whose
     * delivery mark is under the pointer; 0 anywhere else.
     */
    int markLineId;
    LostTalesChatOverlayRenderer.Hit line;
    LostTalesChatHoverCard.Found person;
    /** On a member list, the member whose row it is; null on the list around them. */
    com.ninuna.losttales.network.packet.LostTalesChatMembersPacket.Member member;

    public ChatHover(Kind kind) {
        super(WindowHover.Kind.CONTENT);
        this.chatKind = kind;
    }

    public boolean is(Kind kind) {
        return this.chatKind == kind;
    }

    /** Whether the screen's hover is the chat's, and of this kind. */
    static boolean is(WindowHover hover, Kind kind) {
        return hover instanceof ChatHover && ((ChatHover)hover).chatKind == kind;
    }

    /** The screen's hover as the chat's, or null when it is not the chat's. */
    static ChatHover of(WindowHover hover) {
        return hover instanceof ChatHover ? (ChatHover)hover : null;
    }

    /** The frame the hover is on, which is the chat's kind of frame. */
    ChatFrame chatFrame() {
        return (ChatFrame)this.frame;
    }

    /** Whether a press here does something: what earns the hand. */
    @Override
    public boolean acts() {
        switch (this.chatKind) {
            case MENU_ENTRY:
            case EMPTY_PLUS:
            case SUGGESTION:
            case FIELD_SUGGESTION:
            case PICKER_CELL:
            case PICKER_LABEL:
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
                return this.frame == null || chatFrame().toolbarWhy(
                        this.toolbarKind).length() == 0;
            case LINE:
            case MEMBER_LIST:
            case WINDOW:
                return this.acts;
            default:
                return false;
        }
    }

    /** The pointer's pose over it: a resize over the member list's edge, else as any hover's. */
    @Override
    public LostTalesMapCursor.Pose pose() {
        if (this.chatKind == Kind.MEMBER_LIST_EDGE) {
            return LostTalesMapCursor.Pose.RESIZE_HORIZONTAL;
        }
        return super.pose();
    }
}
