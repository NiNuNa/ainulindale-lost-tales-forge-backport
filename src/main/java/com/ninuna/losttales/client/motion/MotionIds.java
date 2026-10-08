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
    /** A tip beside the pointer: the rests before it shows, and its fade. */
    public static final String UI_TIP_SHOW = "ui.tip.show";

    /* ---- chat ---- */

    /** A chat line under the pointer: its chevron and its words. */
    public static final String CHAT_LINE_HOVER = "chat.line.hover";
    /** The newest message rising into the stack and sliding in. */
    public static final String CHAT_LINE_APPEAR = "chat.line.appear";
    /** A row of the stack gliding to its place in a new layout. */
    public static final String CHAT_ROW_MOVE = "chat.row.move";
    /** A row new to the layout fading in where it lands. */
    public static final String CHAT_ROW_APPEAR = "chat.row.appear";
    /** A window's timestamp area driven out or in. */
    public static final String CHAT_WINDOW_AREA = "chat.window.area";
    /** A window's member list coming out or going in. */
    public static final String CHAT_WINDOW_MEMBERS = "chat.window.members";
    /** The jump-to-present button flying in and out. */
    public static final String CHAT_JUMP_SHOW = "chat.jump.show";
    /** A message's toolbar coming up once the history rests under the pointer. */
    public static final String CHAT_TOOLBAR_SHOW = "chat.toolbar.show";
    /** The closed feed's lines rising for its typing row. */
    public static final String CHAT_FEED_TYPING = "chat.feed.typing";
    /** The scrollbar coming and going. */
    public static final String CHAT_SCROLLBAR_FADE = "chat.scrollbar.fade";

    /* ---- window: the window system every tab stands in ---- */

    /** The input bars' own entrance from below, none by default: they arrive with their window. */
    public static final String WINDOW_BAR_APPEAR = "window.bar.appear";
    /** A sub-window opening where it stands, and fading out there as it closes. */
    public static final String WINDOW_SUB_OPEN = "window.sub.open";
    /** A tab gliding to its place and width in the row. */
    public static final String WINDOW_TAB_MOVE = "window.tab.move";
    /** A tab's own controls going and coming as the row narrows. */
    public static final String WINDOW_TAB_CONTROLS = "window.tab.controls";
    /** A carried tab rising its pixel off the row, and settling back. */
    public static final String WINDOW_TAB_LIFT = "window.tab.lift";
    /** The glow a tab put down gives once, fading. */
    public static final String WINDOW_TAB_GLOW = "window.tab.glow";
    /** A name cut short read whole under a resting pointer. */
    public static final String WINDOW_TAB_MARQUEE = "window.tab.marquee";
    /** The snap assist's panes. */
    public static final String WINDOW_SNAP_ASSIST = "window.snap.assist";
    /** The snap layouts' flyout, and the snap bar's way down from its peek. */
    public static final String WINDOW_SNAP_LAYOUTS = "window.snap.layouts";
    /** The frosted preview of where a window will snap. */
    public static final String WINDOW_SNAP_PREVIEW = "window.snap.preview";
    /** The snap bar peeking further down as the pointer comes nearer it. */
    public static final String WINDOW_SNAP_BAR_PEEK = "window.snap.bar.peek";
    /** The search well's magnifier becoming its cross. */
    public static final String WINDOW_SEARCH_CLEAR = "window.search.clear";
    /** A window gliding to the part of the screen it fills. */
    public static final String WINDOW_FILL = "window.fill";
    /** A new window fading in. */
    public static final String WINDOW_APPEAR = "window.appear";
    /** A chevron control playing its run of frames. */
    public static final String WINDOW_ICON_FLIP = "window.icon.flip";
    /** A history, list or panel gliding to where it was scrolled. */
    public static final String WINDOW_SCROLL = "window.scroll";
    /** A row or control crossing to its lit shade. */
    public static final String WINDOW_HOVER_FADE = "window.hover.fade";
    /** A window or a sub-window fading as others come to lie over it, and back. */
    public static final String WINDOW_STACK_FADE = "window.stack.fade";
    /** A name cut short gliding home once the pointer leaves it. */
    public static final String WINDOW_MARQUEE_RETURN = "window.marquee.return";
    /** A short notice over a window's bar coming up, standing a moment and fading. */
    public static final String WINDOW_NOTICE = "window.notice";
    /** A page's answer over its bar coming up, standing while it is read and fading. */
    public static final String WINDOW_ANSWER = "window.answer";
    /** The line saying how to leave a page filling its window, coming and going. */
    public static final String WINDOW_VIEW_LINE = "window.view.line";
    /** An input bar growing a row taller as the words typed in it wrap, and back. */
    public static final String WINDOW_BAR_GROW = "window.bar.grow";

    /* ---- hud ---- */

    /** The HUD fading out while the chat is open. */
    public static final String HUD_CHAT_HIDE = "hud.chat.hide";
    /** The chat feed at its default place gliding as the rows over the hotbar come and go. */
    public static final String HUD_FEED_RISE = "hud.feed.rise";

    /* ---- screen ---- */

    /** A screen's content arriving as it opens. */
    public static final String SCREEN_OPEN = "screen.open";
    /** The veil and blur behind a screen fading in. */
    public static final String SCREEN_BACKDROP = "screen.backdrop";
    /** A screen's bottom control strip's own entrance, none by default: it arrives with its screen. */
    public static final String SCREEN_CONTROL_BAR = "screen.control_bar";
    /** The journal's categories folding and its search opening. */
    public static final String SCREEN_JOURNAL_FOLD = "screen.journal.fold";
    /** The journal's lists gliding to where they were scrolled. */
    public static final String SCREEN_JOURNAL_SCROLL = "screen.journal.scroll";
    /** A quest conversation's replies gliding to the chosen one. */
    public static final String SCREEN_DIALOGUE_GLIDE = "screen.dialogue.glide";
    /** The Characters page's roster and profile gliding to where they were scrolled, and its figure to its zoom. */
    public static final String SCREEN_CHARACTERS_GLIDE = "screen.characters.glide";

    /* ---- map ---- */

    /** A popup on the map coming up. */
    public static final String MAP_POPUP_OPEN = "map.popup.open";

    /* ---- inventory ---- */

    /** An item stack sliding from one slot to another. */
    public static final String INVENTORY_ITEM_SLIDE = "inventory.item.slide";

    private MotionIds() {}
}
