package com.ninuna.losttales.client.motion;

/**
 * Every motion the code plays, by id. Each lives in the motion file of
 * its family, the part of the id before its first dot; a test holds that
 * every one of them is in the mod's own files with the parts and beats
 * the code asks of it.
 */
public final class MotionIds {
    /* ---- ui: the controls every screen shares ---- */

    /** A glyph button rising under the pointer, pressed and let go. */
    public static final String UI_BUTTON_LIFT = "ui.button.lift";
    /** The same, turning as it travels. */
    public static final String UI_BUTTON_TURN = "ui.button.turn";
    /** The same, further and quicker, like a switch. */
    public static final String UI_BUTTON_SNAP = "ui.button.snap";
    /** A control crossing to its lit artwork and back. */
    public static final String UI_BUTTON_LIT = "ui.button.lit";

    /* ---- chat ---- */

    /** A chat line under the pointer: its chevron and its words. */
    public static final String CHAT_LINE_HOVER = "chat.line.hover";
    /** The newest message rising into the stack and sliding in. */
    public static final String CHAT_LINE_APPEAR = "chat.line.appear";
    /** The input bars coming up from below as the chat opens. */
    public static final String CHAT_BAR_APPEAR = "chat.bar.appear";
    /** A small window opening where it stands, and fading out there as it closes. */
    public static final String CHAT_SMALL_WINDOW_OPEN = "chat.small_window.open";
    /** A tab gliding to its place and width in the row. */
    public static final String CHAT_TAB_MOVE = "chat.tab.move";
    /** A tab's own controls going and coming as the row narrows. */
    public static final String CHAT_TAB_CONTROLS = "chat.tab.controls";
    /** A row of the stack gliding to its place in a new layout. */
    public static final String CHAT_ROW_MOVE = "chat.row.move";
    /** A row new to the layout fading in where it lands. */
    public static final String CHAT_ROW_APPEAR = "chat.row.appear";
    /** The snap assist's panes. */
    public static final String CHAT_SNAP_ASSIST = "chat.snap.assist";
    /** The snap layouts' flyout, and the snap bar's fade and its way down. */
    public static final String CHAT_SNAP_LAYOUTS = "chat.snap.layouts";
    /** The frosted preview of where a window will snap. */
    public static final String CHAT_SNAP_PREVIEW = "chat.snap.preview";
    /** The search well's magnifier becoming its cross. */
    public static final String CHAT_SEARCH_CLEAR = "chat.search.clear";
    /** A window gliding to the part of the screen it fills. */
    public static final String CHAT_WINDOW_FILL = "chat.window.fill";
    /** A window's timestamp area driven out or in. */
    public static final String CHAT_WINDOW_AREA = "chat.window.area";
    /** A window's member list coming out or going in. */
    public static final String CHAT_WINDOW_MEMBERS = "chat.window.members";
    /** A new window fading in. */
    public static final String CHAT_WINDOW_APPEAR = "chat.window.appear";
    /** The jump-to-present button flying in and out. */
    public static final String CHAT_JUMP_SHOW = "chat.jump.show";
    /** A message's toolbar coming up once the history rests under the pointer. */
    public static final String CHAT_TOOLBAR_SHOW = "chat.toolbar.show";
    /** A chevron control playing its run of frames. */
    public static final String CHAT_ICON_FLIP = "chat.icon.flip";
    /** The closed feed's lines rising for its typing row. */
    public static final String CHAT_FEED_TYPING = "chat.feed.typing";
    /** A history, list or panel gliding to where it was scrolled. */
    public static final String CHAT_SCROLL = "chat.scroll";
    /** A row or control crossing to its lit shade. */
    public static final String CHAT_HOVER_FADE = "chat.hover.fade";
    /** The scrollbar coming and going. */
    public static final String CHAT_SCROLLBAR_FADE = "chat.scrollbar.fade";
    /** A name cut short gliding home once the pointer leaves it. */
    public static final String CHAT_MARQUEE_RETURN = "chat.marquee.return";
    /** The snap bar peeking further down as the pointer comes nearer it. */
    public static final String CHAT_SNAP_BAR_PEEK = "chat.snap.bar.peek";

    /* ---- hud ---- */

    /** The HUD fading out while the chat is open. */
    public static final String HUD_CHAT_HIDE = "hud.chat.hide";

    /* ---- screen ---- */

    /** A screen's content arriving as it opens. */
    public static final String SCREEN_OPEN = "screen.open";
    /** The veil and blur behind a screen fading in. */
    public static final String SCREEN_BACKDROP = "screen.backdrop";
    /** A screen's bottom control strip following its content in. */
    public static final String SCREEN_CONTROL_BAR = "screen.control_bar";
    /** The journal's categories folding and its search opening. */
    public static final String SCREEN_JOURNAL_FOLD = "screen.journal.fold";
    /** The journal's lists gliding to where they were scrolled. */
    public static final String SCREEN_JOURNAL_SCROLL = "screen.journal.scroll";
    /** A quest conversation's replies gliding to the chosen one. */
    public static final String SCREEN_DIALOGUE_GLIDE = "screen.dialogue.glide";
    /** The Characters tab's roster and profile gliding to where they were scrolled, and its figure to its zoom. */
    public static final String SCREEN_CHARACTERS_GLIDE = "screen.characters.glide";

    /* ---- map ---- */

    /** A popup on the map coming up. */
    public static final String MAP_POPUP_OPEN = "map.popup.open";

    /* ---- inventory ---- */

    /** An item stack sliding from one slot to another. */
    public static final String INVENTORY_ITEM_SLIDE = "inventory.item.slide";

    private MotionIds() {}
}
