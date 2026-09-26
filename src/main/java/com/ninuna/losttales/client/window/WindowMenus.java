package com.ninuna.losttales.client.window;

import com.ninuna.losttales.client.input.LostTalesKeyPress;
import com.ninuna.losttales.gui.style.LostTalesUiHitBox;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.lwjgl.input.Keyboard;

/**
 * Every menu the window screen opens, each in a sub-window of its own
 * ({@link MenuWindow}): the window's own — a tab's, a window's, the
 * palette, the {@code +}, the tab search, Settings — and those a system
 * adds, each kind with its {@link Source}. Each kind has one window. A
 * control opens it where the popup it replaced hung, or where the player
 * left its kind, and turns it to what it was pressed for while it stands;
 * the same control pressed again puts it away. A row that acts closes
 * its window; a switch, a pick, a question, or a row that opens another
 * window leaves it standing.
 */
public final class WindowMenus implements MenuWindow.Owner {
    /** How often an open menu reads its rows again. */
    private static final long REFRESH_NANOS = 500L * 1000000L;

    /**
     * What a kind of menu is about and what its rows do. A kind's menu
     * reads its rows from here as it opens, as it is typed into, and on
     * an interval while it stands.
     */
    public abstract static class Source {
        /** Whether what the menu is about still stands; one that does not closes. */
        public boolean stillStands(MenuWindow menu) {
            return true;
        }

        /** Opening, or turning to something else: the field it starts with. */
        public void prepare(MenuWindow menu) {}

        /** The menu's name and rows, read again from what it is about. */
        public abstract void rebuild(MenuWindow menu);

        /**
         * A row taken — with {@code back}, pressed with the right button
         * where {@link #takesBack} — answering whether the window stays.
         */
        public abstract boolean act(MenuWindow menu, MenuWindow.Entry entry,
                                    SubWindow window, boolean back);

        /** Whether typing into its field reads the rows again: a search. */
        public boolean readsAsTyped() {
            return false;
        }

        /** The row Enter takes in its field; null for none. */
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            return null;
        }

        /** Whether a right-click takes a row too, as a few-word setting steps back. */
        public boolean takesBack() {
            return false;
        }
    }

    /** Where a menu's window first opens, before the player has placed its kind. */
    public interface FirstPlace {
        /** The window it opens in; null for the bare screen. */
        String windowId();

        /** The content box the window opens round in {@code room}, for {@code menu} as it stands. */
        LostTalesUiHitBox contentBox(MenuWindow menu, LostTalesUiHitBox room);
    }

    /**
     * The sources every screen's menus fall back on: a page's, which has
     * no part of the screen to register them with. Registered once as the
     * client starts, as the kinds are; code, never world state.
     */
    private static final Map<SubWindowKind, Source> SHARED =
            new LinkedHashMap<SubWindowKind, Source>();

    private final Map<SubWindowKind, Source> sources =
            new LinkedHashMap<SubWindowKind, Source>();
    /** Each kind's menu; one that came back with the screen is the one it was. */
    private final Map<SubWindowKind, MenuWindow> menus =
            new LinkedHashMap<SubWindowKind, MenuWindow>();
    private final SubWindows windows;
    private long refreshedNanos;

    WindowMenus(SubWindows windows) {
        this.windows = windows;
    }

    /** Gives a kind of menu its source; a system adds its own as its part is made. */
    public void register(SubWindowKind kind, Source source) {
        if (kind != null && source != null) {
            this.sources.put(kind, source);
        }
    }

    /**
     * Gives a kind of menu a source every screen uses where it has none of
     * its own: a page's menus. The source keeps no screen of its own; it
     * reaches the screen it stands on through {@link WindowScreen#current}.
     */
    public static synchronized void registerShared(SubWindowKind kind,
                                                   Source source) {
        if (kind != null && source != null) {
            SHARED.put(kind, source);
        }
    }

    /** The kind's source: the screen's own, else the shared one; null for none. */
    private Source sourceOf(SubWindowKind kind) {
        Source own = this.sources.get(kind);
        if (own != null) {
            return own;
        }
        synchronized (WindowMenus.class) {
            return SHARED.get(kind);
        }
    }

    /* ---- What is open ---- */

    /** Whether the window of the kind's menu is out. */
    public boolean isOpen(SubWindowKind kind) {
        SubWindow window = this.windows.find(kind, "");
        return window != null && window.isOpen();
    }

    /**
     * Whether the kind's menu is out about {@code about}: the {@code +}
     * or the tab search for a window's id, null standing for the empty
     * screen, the tab menu for a tab. The control that opened it rests lit.
     */
    public boolean isOpenFor(SubWindowKind kind, Object about) {
        return isOpen(kind) && sameAbout(menu(kind).about(), about);
    }

    /** The kind's menu, made the first time it is asked for. */
    public MenuWindow menu(SubWindowKind kind) {
        MenuWindow menu = this.menus.get(kind);
        if (menu == null) {
            menu = new MenuWindow(kind, this);
            this.menus.put(kind, menu);
        }
        return menu;
    }

    /** The window a kind's menu stands in while it is out; null otherwise. */
    public SubWindow windowOf(SubWindowKind kind) {
        SubWindow window = this.windows.find(kind, "");
        return window != null && window.isOpen() ? window : null;
    }

    /** Two subjects are the same one by what they equal, the empty screen's null included. */
    static boolean sameAbout(Object one, Object other) {
        return one == null ? other == null : one.equals(other);
    }

    /* ---- Keeping them current ---- */

    /**
     * Keeps every open menu current, asked once a frame: on an interval
     * rather than per frame, each reads its rows again — following
     * players joining and leaving, counts changing and choices made
     * elsewhere — and one whose subject has gone, or whose rows have all
     * gone, closes.
     */
    public void refresh() {
        long now = System.nanoTime();
        if (now - this.refreshedNanos < REFRESH_NANOS) {
            return;
        }
        this.refreshedNanos = now;
        for (MenuWindow menu : new ArrayList<MenuWindow>(this.menus.values())) {
            SubWindow window = windowOf(menu.kind);
            Source source = sourceOf(menu.kind);
            if (window == null || source == null) {
                continue;
            }
            if (!source.stillStands(menu)) {
                this.windows.close(window);
                continue;
            }
            source.rebuild(menu);
            if (menu.entries().isEmpty()) {
                this.windows.close(window);
            }
        }
    }

    /**
     * A menu open as the screen last closed comes back where it stood,
     * about what it was while that still stands and with what was typed
     * into its field; one whose subject has gone stays closed. False for
     * what is no menu of a kind with a source.
     */
    public boolean restore(SubWindowPlaces.Reopening open) {
        if (!(open.state instanceof MenuWindow)) {
            return false;
        }
        MenuWindow menu = (MenuWindow)open.state;
        Source source = sourceOf(menu.kind);
        if (menu.kind != open.kind || source == null) {
            return false;
        }
        if (!source.stillStands(menu)) {
            return true;
        }
        menu.setOwner(this);
        this.menus.put(menu.kind, menu);
        source.rebuild(menu);
        if (!menu.entries().isEmpty()) {
            this.windows.reopen(open, menu);
        }
        return true;
    }

    /** Reads the kind's rows again at once where its window is out: what one window changed shows in another. */
    public void rebuildIfOpen(SubWindowKind kind) {
        Source source = sourceOf(kind);
        if (isOpen(kind) && source != null) {
            source.rebuild(menu(kind));
        }
    }

    /* ---- Taking a row ---- */

    /**
     * A row taken, by a click or by Enter in its field. A row that acts
     * closes its window. A switch, a pick, a question or a row opening
     * another window leaves it standing with its rows read again, and
     * its field lets the keys go, as a picker does after a pick.
     */
    @Override
    public void take(MenuWindow menu, MenuWindow.Entry entry, boolean back) {
        SubWindow window = windowOf(menu.kind);
        Source source = sourceOf(menu.kind);
        if (window == null || source == null || entry == null
                || !entry.isTakeable() || (back && !source.takesBack())) {
            return;
        }
        if (!source.stillStands(menu)) {
            this.windows.close(window);
            return;
        }
        if (source.act(menu, entry, window, back)) {
            if (window.isOpen()) {
                source.rebuild(menu);
                menu.releaseKeys();
            }
        } else {
            this.windows.close(window);
        }
    }

    /**
     * A key for the field of the menu in front: its list takes the arrows
     * and Enter while it is out; Enter takes the row the typing found; and
     * anything else is typed into the field, a search narrowing its rows
     * as it goes.
     */
    @Override
    public void keyTyped(MenuWindow menu, LostTalesKeyPress press) {
        Source source = sourceOf(menu.kind);
        if (source == null || menu.serveList(press)) {
            return;
        }
        if (press.is(Keyboard.KEY_RETURN) || press.is(Keyboard.KEY_NUMPADENTER)) {
            MenuWindow.Entry found = source.firstFound(menu);
            if (found != null) {
                take(menu, found, false);
            }
            return;
        }
        if (menu.edit(press) && source.readsAsTyped()) {
            source.rebuild(menu);
        }
    }

    /**
     * The first row a search's typing found, none while nothing is typed:
     * what Enter takes in a search.
     */
    public static MenuWindow.Entry firstTyped(MenuWindow menu) {
        if (menu.filter().length() == 0) {
            return null;
        }
        for (MenuWindow.Entry entry : menu.entries()) {
            if (entry.isTakeable()) {
                return entry;
            }
        }
        return null;
    }

    /** Whether a name holds what has been typed, however it is cased. */
    public static boolean matchesFilter(String name, String filter) {
        return filter.length() == 0 || name.toLowerCase(Locale.ROOT)
                .contains(filter.toLowerCase(Locale.ROOT));
    }

    /** Adds a headed section, or nothing at all when it has no rows. */
    public static void addSection(List<MenuWindow.Entry> entries,
                                  String header,
                                  List<MenuWindow.Entry> rows) {
        if (rows.isEmpty()) {
            return;
        }
        entries.add(MenuWindow.Entry.header(header));
        entries.addAll(rows);
    }

    /* ---- Showing a menu ---- */

    /**
     * Shows the kind's menu about {@code about}. Out already about the
     * same thing in the same window, a {@code toggle} — its control
     * pressed again — puts it away and anything else brings it in front.
     * Out about something else, it turns to {@code about} where it
     * stands; pressed for in another window, it moves there. Not out, it
     * opens where the player left its kind, else at its {@code place};
     * with no place it does not open. A menu with no rows to show does
     * not open.
     */
    public void show(SubWindowKind kind, Object about, FirstPlace place,
                     boolean toggle) {
        Source source = sourceOf(kind);
        if (source == null) {
            return;
        }
        MenuWindow menu = menu(kind);
        SubWindow window = this.windows.find(kind, "");
        boolean out = window != null && window.isOpen();
        String parent = parentFor(place);
        boolean elsewhere = out && place != null && !window.belongsTo(parent);
        if (out && !elsewhere && sameAbout(menu.about(), about)) {
            if (toggle) {
                this.windows.close(window);
            } else {
                this.windows.focus(window);
            }
            return;
        }
        if (!out && place == null) {
            return;
        }
        menu.setAbout(about);
        menu.restart();
        menu.closeField();
        source.prepare(menu);
        source.rebuild(menu);
        if (menu.entries().isEmpty()) {
            if (out) {
                this.windows.close(window);
            }
            return;
        }
        if (out && !elsewhere) {
            this.windows.focus(window);
            this.windows.refit(window);
            return;
        }
        boolean fading = window != null;
        SubWindow opened = this.windows.open(kind, "", menu, parent,
                place.contentBox(menu, this.windows.roomOf(parent)));
        if (fading) {
            this.windows.refit(opened);
        }
    }

    /** The window a place opens in while it is drawn; else the bare screen. */
    private String parentFor(FirstPlace place) {
        String windowId = place == null ? null : place.windowId();
        return windowId != null && this.windows.roomOf(windowId) != null
                ? windowId : null;
    }

    /** Hung from a control or the pointer, in its window; none for no anchor. */
    public static FirstPlace hangingFrom(final SubWindowAnchor anchor) {
        return anchor == null ? null : new FirstPlace() {
            @Override
            public String windowId() {
                return anchor.windowId;
            }

            @Override
            public LostTalesUiHitBox contentBox(MenuWindow menu,
                                                LostTalesUiHitBox room) {
                return menu.firstContentBox(anchor, room);
            }
        };
    }

    /** Beside the sub-window whose row opened it, in the same window. */
    public static FirstPlace besideWindow(final SubWindow window) {
        return new FirstPlace() {
            @Override
            public String windowId() {
                return window.parentId;
            }

            @Override
            public LostTalesUiHitBox contentBox(MenuWindow menu,
                                                LostTalesUiHitBox room) {
                return menu.firstContentBoxBeside(window.box(), room);
            }
        };
    }

    /**
     * In the middle of a window, else of the bare screen: what a shortcut
     * opens with no control to hang from.
     */
    public static FirstPlace centredIn(final String windowId) {
        return new FirstPlace() {
            @Override
            public String windowId() {
                return windowId;
            }

            @Override
            public LostTalesUiHitBox contentBox(MenuWindow menu,
                                                LostTalesUiHitBox room) {
                return menu.firstContentBoxCentred(room);
            }
        };
    }

    /**
     * Where a window opens that takes another's place as that one
     * closes: its top left on the other's.
     */
    public static SubWindowAnchor inPlaceOf(SubWindow window) {
        int left = (int)Math.round(window.left);
        int top = (int)Math.round(window.top) - SubWindowAnchor.REACH;
        return new SubWindowAnchor(left, top, left, top, true, false,
                window.parentId);
    }
}
