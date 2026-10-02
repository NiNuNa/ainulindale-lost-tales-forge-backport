package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * The window's own menus: a tab's options, behind the three dots on the
 * tab or under a right-click on it; a window's, Window
 * Options, behind the three dots at the end of its row; the {@code +},
 * listing what can be opened again; the tab search over every tab, open
 * or not; and the quick switcher, the tab search with what the pages find
 * besides. What a tab's options hold is the tab's own
 * ({@link WindowPage#options}, which its tool strip also shows as
 * buttons) and then its kind's settings; what can be
 * opened is each system's ({@link ScreenPart#addOpenable}) and the pages
 * no window holds; what a page finds is its own ({@link PageContent#find}).
 * The tool strip's cog opens the settings of the tab's kind at once.
 */
final class TabMenus {
    /** The switch that pins a window to the HUD, where it stays while playing. */
    private static final String ENTRY_PIN_HUD = "window:pin_hud";
    /** The switch that pins a window to the GUI, where every view shows it. */
    private static final String ENTRY_PIN_GUI = "window:pin_gui";
    /** Marks a search row that jumps to a tab already open. */
    private static final String ENTRY_OPEN_PREFIX = "open:";
    /** Marks a row a page found: this, the page's id, a colon, and the row's own id. */
    private static final String ENTRY_FIND_PREFIX = "find:";
    private static final String ENTRY_WINDOW_RESET = "window_reset";
    /** The row of a tab's options that opens its split view beside the menu. */
    private static final String SPLIT_VIEW = "split_view";
    /** The rows of the split view, and the page a split is made with after this. */
    private static final String SPLIT_WITH_PREFIX = "split_with:";
    /** A row of the split view that opens a closed page or channel beside the page: this and its id. */
    private static final String SPLIT_OPEN_PREFIX = "split_open:";
    private static final String SPLIT_SWAP = "split:swap";
    private static final String SPLIT_TURN = "split:turn";
    private static final String SPLIT_CLOSE_FIRST = "split:close_first";
    private static final String SPLIT_CLOSE_SECOND = "split:close_second";
    private static final String SPLIT_SEPARATE = "split:separate";
    /** Marks a row that opens settings beside its menu: this and the place's name. */
    private static final String SETTINGS_PREFIX = "settings:";

    private final WindowScreen screen;
    private final WindowMenus menus;

    TabMenus(WindowScreen screen, WindowMenus menus) {
        this.screen = screen;
        this.menus = menus;
        menus.register(SubWindowKind.TAB, new TabSource());
        menus.register(SubWindowKind.PICK, new PickSource());
        menus.register(SubWindowKind.SPLIT, new SplitSource());
        menus.register(SubWindowKind.WINDOW, new WindowSource());
        menus.register(SubWindowKind.OPEN, new OpenSource());
        menus.register(SubWindowKind.TAB_SEARCH, new SearchSource(
                SubWindowKind.TAB_SEARCH));
        menus.register(SubWindowKind.SWITCHER, new SearchSource(
                SubWindowKind.SWITCHER));
        menus.register(SubWindowKind.HELP, new HelpSource());
    }

    /* ---- What opens them ---- */

    /**
     * The tab's options, behind the three dots on the tab — a switch, as
     * the dots are — or under a right-click on the tab, which only opens
     * it or turns it to the tab: the tab's own options, then the row that
     * opens its kind's settings. Closing is the cross on the tab
     * and nothing else: a row that only repeats the button beside it is a
     * second way to lose a tab by accident.
     */
    void showTabMenu(WindowPage tab, SubWindowAnchor anchor, boolean toggle) {
        if (tab != null && hasRows(tab)) {
            this.menus.show(SubWindowKind.TAB, tab,
                    WindowMenus.hangingFrom(anchor), toggle);
        }
    }

    /**
     * Whether a tab's options hold anything: the page's own choices, the
     * settings of its kind, or a split view. The dots of one that holds
     * nothing stay, greyed, and no menu opens for it.
     */
    static boolean hasRows(WindowPage tab) {
        return tab.hasOptions() || tab.settingsPlace() != null
                || canSplit(tab);
    }

    /**
     * Whether the split view does anything for {@code tab}: it stands in a
     * split, another open page could stand beside it, or something closed
     * could open beside it. Asked every frame by the split view button.
     */
    static boolean canSplit(WindowPage tab) {
        Window window = WindowLayout.windowOf(tab);
        if (window == null) {
            return false;
        }
        if (window.splitOf(tab) != null) {
            return true;
        }
        for (Window from : WindowLayout.windows()) {
            for (WindowPage other : from.getTabs()) {
                if (canSplitWith(window, tab, from, other)) {
                    return true;
                }
            }
        }
        return hasClosed();
    }

    /** Whether the {@code +} has anything to open: a closed channel, someone to whisper to, a page no window holds. */
    private static boolean hasClosed() {
        for (WindowPages.Page page : WindowPages.all()) {
            if (WindowPages.isOffered(page)) {
                return true;
            }
        }
        WindowScreen screen = WindowScreen.current();
        if (screen != null) {
            for (ScreenPart part : screen.parts()) {
                if (part.hasRestorable()) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Whether {@code other}, in {@code from}, can come to stand beside
     * {@code tab}, in {@code window}, in a new split: it is shown, in no
     * split, and its window lets it go.
     */
    private static boolean canSplitWith(Window window, WindowPage tab,
                                        Window from, WindowPage other) {
        return !other.equals(tab) && from.splitOf(other) == null
                && (from == window || !from.isLocked())
                && WindowView.isShown(other);
    }

    /**
     * The split view's rows: for a tab in a split, Swap Sides, the other
     * way, closing either side and separating them; for a tab in none,
     * the pages that can stand beside it, this window's first, then the
     * other windows'. Greyed while the window is locked.
     */
    static List<MenuWindow.Entry> splitRows(WindowPage tab) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        Window window = WindowLayout.windowOf(tab);
        if (window == null) {
            return rows;
        }
        WindowSplit split = window.splitOf(tab);
        if (split != null) {
            boolean stacked = split.isStacked();
            rows.add(splitRow(SPLIT_SWAP, "gui.losttales.window.split.swap", window));
            MenuWindow.Entry turn = splitRow(SPLIT_TURN, stacked
                    ? "gui.losttales.window.split.side_by_side"
                    : "gui.losttales.window.split.one_over_other", window);
            rows.add(!stacked && !(split.first() instanceof OtherPage
                    && split.second() instanceof OtherPage)
                    ? turn.unavailable(StatCollector.translateToLocal(
                            "gui.losttales.window.split.conversation_beside"))
                    : turn);
            rows.add(splitRow(SPLIT_CLOSE_FIRST, stacked ? "gui.losttales.window.split.close_top"
                    : "gui.losttales.window.split.close_left", window));
            rows.add(splitRow(SPLIT_CLOSE_SECOND, stacked ? "gui.losttales.window.split.close_bottom"
                    : "gui.losttales.window.split.close_right", window));
            rows.add(splitRow(SPLIT_SEPARATE, "gui.losttales.window.split.separate", window));
            return rows;
        }
        List<MenuWindow.Entry> here = new ArrayList<MenuWindow.Entry>();
        List<MenuWindow.Entry> elsewhere = new ArrayList<MenuWindow.Entry>();
        for (Window from : WindowLayout.windows()) {
            for (WindowPage other : from.getTabs()) {
                if (canSplitWith(window, tab, from, other)) {
                    (from == window ? here : elsewhere).add(heldWhileLocked(
                            new MenuWindow.Entry(SPLIT_WITH_PREFIX + other.id(),
                                    other.title(), false, other.tone(), other),
                            window));
                }
            }
        }
        WindowMenus.addSection(rows, StatCollector.translateToLocal(
                "gui.losttales.window.split.here"), here);
        WindowMenus.addSection(rows, StatCollector.translateToLocal(
                "gui.losttales.window.split.elsewhere"), elsewhere);
        if (rows.isEmpty()) {
            rows.add(MenuWindow.Entry.passive(StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.split.nothing", tab.title())));
        }
        return rows;
    }

    /**
     * The row of a tab's options that opens its split view beside the
     * menu, as the split view button does: greyed while the padlock holds
     * it, and saying why while no page can stand beside the tab.
     */
    private static MenuWindow.Entry splitViewRow(WindowPage tab) {
        Window window = WindowLayout.windowOf(tab);
        MenuWindow.Entry row = new MenuWindow.Entry(SPLIT_VIEW,
                StatCollector.translateToLocal("gui.losttales.window.split.title"))
                .withSprite(LostTalesUiSheet.SPLIT, LostTalesUiSheet.SPLIT_LIT,
                        window != null && window.splitOf(tab) != null);
        if (!canSplit(tab)) {
            return row.unavailable(StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.split.nothing", tab.title()));
        }
        return heldWhileLocked(row, window);
    }

    /**
     * The tab's split view, in a sub-window of its own at {@code place}:
     * a switch, as its button is.
     */
    void toggleSplit(WindowPage tab, WindowMenus.FirstPlace place) {
        if (tab != null && canSplit(tab)) {
            this.menus.show(SubWindowKind.SPLIT, tab, place, true);
        }
    }

    /** Whether the tab's split view is out, which lights its button. */
    boolean splitOut(WindowPage tab) {
        return tab != null && this.menus.isOpenFor(SubWindowKind.SPLIT, tab);
    }

    private static MenuWindow.Entry splitRow(String id, String labelKey, Window window) {
        return heldWhileLocked(new MenuWindow.Entry(id,
                StatCollector.translateToLocal(labelKey)), window);
    }

    /**
     * Does what a row of {@code tab}'s split view says. The layout
     * refuses what the padlock holds.
     */
    private static void actOnSplit(WindowPage tab, String id) {
        Window window = WindowLayout.windowOf(tab);
        WindowSplit split = window == null ? null : window.splitOf(tab);
        if (id.startsWith(SPLIT_WITH_PREFIX)) {
            WindowPage other = openTab(id.substring(SPLIT_WITH_PREFIX.length()));
            if (other != null) {
                WindowLayout.split(tab, other);
            }
        } else if (split == null) {
            return;
        } else if (SPLIT_SWAP.equals(id)) {
            WindowLayout.swapSides(tab);
        } else if (SPLIT_TURN.equals(id)) {
            WindowLayout.turnSplit(tab, !split.isStacked());
        } else if (SPLIT_CLOSE_FIRST.equals(id)) {
            WindowLayout.close(split.first());
        } else if (SPLIT_CLOSE_SECOND.equals(id)) {
            WindowLayout.close(split.second());
        } else if (SPLIT_SEPARATE.equals(id)) {
            WindowLayout.separate(tab);
        }
    }

    /**
     * Opens {@code closed} and shows it beside {@code tab}: it opens where
     * the {@code +} would put it and comes over into a split, and takes the
     * keys. Nothing while the padlock holds the window.
     */
    private void openBeside(WindowPage tab, WindowPage closed) {
        Window window = WindowLayout.windowOf(tab);
        if (closed == null || window == null || window.isLocked()) {
            return;
        }
        WindowPage opened = WindowLayout.openTab(closed, window.getId());
        if (opened == null) {
            opened = WindowLayout.openInNewWindow(closed);
        }
        if (opened != null && WindowLayout.split(tab, opened)) {
            this.screen.jumpToTab(opened);
        }
    }

    /** The open tab with {@code id}, in any window, or null. */
    private static WindowPage openTab(String id) {
        for (Window window : WindowLayout.windows()) {
            for (WindowPage each : window.getTabs()) {
                if (each.id().equals(id)) {
                    return each;
                }
            }
        }
        return null;
    }

    /**
     * The settings of the tab's kind, behind the tool strip's cog, hung
     * from it: a switch, as the cog is. Every conversation opens the one
     * Chat Settings. A tab of no kind with settings opens nothing.
     */
    void toggleSettings(WindowPage tab, SubWindowAnchor anchor) {
        Settings.Place place = tab == null ? null : tab.settingsPlace();
        if (place != null) {
            this.screen.settings().toggle(place,
                    WindowMenus.hangingFrom(anchor));
        }
    }

    /**
     * The tab's help, hung from the question mark pressed, else — from
     * F1 — in the middle of its window: a switch, as the question mark is.
     */
    void toggleHelp(WindowPage tab, SubWindowAnchor anchor) {
        if (tab == null) {
            return;
        }
        Window window = WindowLayout.windowOf(tab);
        this.menus.show(SubWindowKind.HELP, tab, anchor != null
                        ? WindowMenus.hangingFrom(anchor)
                        : WindowMenus.centredIn(window == null ? null
                                : window.getId()), true);
    }

    /** Whether the tab's help is out, which lights its question mark. */
    boolean helpOut(WindowPage tab) {
        return tab != null && this.menus.isOpenFor(SubWindowKind.HELP, tab);
    }

    /** Whether the tab's options are out, which lights the three dots on the tab. */
    boolean optionsOut(WindowPage tab) {
        return tab != null && this.menus.isOpenFor(SubWindowKind.TAB, tab);
    }

    /**
     * The words one of the tab's options picks from, in a sub-window of
     * their own at {@code place}: a switch, as the option's button is.
     */
    void togglePick(WindowPage tab, PageOption option,
                    WindowMenus.FirstPlace place) {
        if (tab != null && option != null && option.kind == PageOption.Kind.PICK) {
            this.menus.show(SubWindowKind.PICK, new Picking(tab, option.id),
                    place, true);
        }
    }

    /** The option of the tab whose words are out, which lights its button; empty for none. */
    String pickOut(WindowPage tab) {
        if (tab == null || !this.menus.isOpen(SubWindowKind.PICK)) {
            return "";
        }
        Object about = this.menus.menu(SubWindowKind.PICK).about();
        return about instanceof Picking && ((Picking)about).tab.equals(tab)
                ? ((Picking)about).optionId : "";
    }

    /** The option of the tab with {@code id}, or null for one it no longer has. */
    private static PageOption optionOf(WindowPage tab, String id) {
        for (PageOption option : tab.options()) {
            if (option.id.equals(id)) {
                return option;
            }
        }
        return null;
    }

    /** Whether the settings of the tab's kind are out, which lights its cog. */
    boolean settingsOut(WindowPage tab) {
        Settings.Place place = tab == null ? null : tab.settingsPlace();
        return place != null
                && this.menus.isOpenFor(SubWindowKind.SETTINGS, place);
    }

    /** The row that opens a place's settings beside the menu it stands in. */
    private static MenuWindow.Entry settingsRow(Settings.Place place) {
        return new MenuWindow.Entry(SETTINGS_PREFIX + place.name(),
                StatCollector.translateToLocal(place.titleKey)).withSprite(
                LostTalesUiSheet.COG, LostTalesUiSheet.COG_HOVER, false);
    }

    /**
     * A tab's options as its menu lists them: each its row, a hairline
     * between two groups, and a group's heading over it where it has one.
     */
    static List<MenuWindow.Entry> optionRows(List<PageOption> options) {
        List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
        String group = null;
        for (PageOption option : options) {
            if (!option.group().equals(group)) {
                if (group != null) {
                    rows.add(MenuWindow.Entry.separator());
                }
                group = option.group();
                if (option.headingKey().length() > 0) {
                    rows.add(MenuWindow.Entry.header(
                            StatCollector.translateToLocal(option.headingKey())));
                }
            }
            rows.add(option.row());
        }
        return rows;
    }

    /** A settings row taken: its settings open beside the menu, or go away while they are out. */
    private void toggleSettingsBeside(MenuWindow.Entry entry,
                                      SubWindow menuWindow) {
        this.screen.settings().toggle(Settings.Place.valueOf(
                entry.id.substring(SETTINGS_PREFIX.length())),
                WindowMenus.besideWindow(menuWindow));
    }

    /** One of a window's two pins, named by {@code labelKey}, reading On or Off, its pin lit while on. */
    private static MenuWindow.Entry pinRow(String id, String labelKey,
                                           boolean on) {
        return new MenuWindow.Entry(id,
                StatCollector.translateToLocal(labelKey)).withValue(
                StatCollector.translateToLocal(on
                        ? "gui.losttales.window.settings.on"
                        : "gui.losttales.window.settings.off"))
                .withSprite(LostTalesUiSheet.PIN, LostTalesUiSheet.PIN_LIT, on);
    }

    /**
     * A row that changes a window's place or its tabs: greyed while the
     * window is locked, with nothing said, since the lit padlock says it.
     */
    private static MenuWindow.Entry heldWhileLocked(MenuWindow.Entry row,
                                                    Window window) {
        return window != null && window.isLocked() ? row.held() : row;
    }

    /**
     * The window's own menu, Window Options, behind the three dots at the
     * end of its row — a switch, as the dots are — or under a right-click
     * on the row anywhere but a tab: what a window has that nothing else
     * on the row offers. Locking
     * and closing are not among them, since the padlock and the cross
     * stand beside the dots. Its two pins come first, then Reset Window
     * Layout, and last Window Settings, which opens beside it.
     */
    void showWindowMenu(Window window, SubWindowAnchor anchor,
                        boolean toggle) {
        if (window != null) {
            this.menus.show(SubWindowKind.WINDOW, window.getId(),
                    WindowMenus.hangingFrom(anchor), toggle);
        }
    }

    /**
     * The {@code +} menu for a window, hung from its control, or — from
     * the keyboard ({@code anchor} null) — from the row of the window, else
     * from the middle of the bare screen; a switch like its control.
     * Nothing opens while there is nothing to open.
     */
    void toggleOpen(Window window, SubWindowAnchor anchor) {
        String windowId = window == null ? null : window.getId();
        this.menus.show(SubWindowKind.OPEN, windowId,
                placeFor(windowId, anchor), true);
    }

    /**
     * The tab search for a window, hung as the {@code +} is. A switch:
     * out for this window already, it goes away; out for another, it
     * turns to this one and starts afresh.
     */
    void toggleSearch(Window window, SubWindowAnchor anchor) {
        String windowId = window == null ? null : window.getId();
        this.menus.show(SubWindowKind.TAB_SEARCH, windowId,
                placeFor(windowId, anchor), true);
    }

    /**
     * Where the {@code +} or the tab search opens: from the control
     * pressed, else from the left end of the window's row as the keyboard
     * opens it, else in the middle of the bare screen.
     */
    private WindowMenus.FirstPlace placeFor(String windowId,
                                            SubWindowAnchor anchor) {
        if (anchor != null) {
            return WindowMenus.hangingFrom(anchor);
        }
        return windowId == null ? WindowMenus.centredIn(null)
                : WindowMenus.hangingFrom(keyboardAnchor(windowId));
    }

    /**
     * The quick switcher, in the middle of the window the keys are in, or
     * of the screen with none: a switch, as every shortcut's menu is.
     */
    void toggleSwitcher(Window window) {
        String windowId = window == null ? null : window.getId();
        this.menus.show(SubWindowKind.SWITCHER, windowId,
                WindowMenus.centredIn(windowId), true);
    }

    /**
     * Where a menu the keyboard opens for a window hangs: the left end of
     * its tab row, where the row's first controls stand.
     */
    private SubWindowAnchor keyboardAnchor(String windowId) {
        WindowFrame frame = WindowFrame.find(windowId);
        if (frame == null || !frame.drawn) {
            return null;
        }
        int left = (int)Math.floor(frame.drawnLeft()) + 2;
        int rowBottom = (int)Math.floor(frame.tabRowBottom());
        return SubWindowAnchor.inward(left, TabRow.rowTop(rowBottom),
                left + 8, rowBottom, frame, this.screen.width,
                this.screen.height);
    }

    /* ---- The rows ---- */

    /**
     * What can be opened again: each system's closed tabs, then the pages
     * no window holds, narrowed by {@code filter}; in the {@code search},
     * leaving out what is open already.
     */
    private List<MenuWindow.Entry> openRows(String filter, boolean search) {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        for (ScreenPart part : this.screen.parts()) {
            part.addOpenable(entries, filter, search);
        }
        List<MenuWindow.Entry> pages = new ArrayList<MenuWindow.Entry>();
        for (WindowPages.Page page : WindowPages.all()) {
            OtherPage tab = page.tab();
            if (WindowPages.isOffered(page)
                    && WindowMenus.matchesFilter(page.title(), filter)) {
                pages.add(new MenuWindow.Entry(tab.id(), page.title(), false,
                        -1, tab));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.window.open.pages"), pages);
        if (!search) {
            // The tabs the view hides wait in their windows: picking one
            // shows it here until the screen closes. The search lists
            // every open tab itself.
            List<MenuWindow.Entry> hidden = new ArrayList<MenuWindow.Entry>();
            for (Window window : WindowLayout.windows()) {
                for (WindowPage tab : window.getTabs()) {
                    if (tab.isAvailable() && !WindowView.shows(tab)
                            && WindowMenus.matchesFilter(tab.title(),
                                    filter)) {
                        hidden.add(new MenuWindow.Entry(
                                ENTRY_OPEN_PREFIX + tab.id(), tab.title(),
                                tab.isMuted(), tab.tone(), tab));
                    }
                }
            }
            WindowMenus.addSection(entries, StatCollector.translateToLocal(
                    "gui.losttales.window.open.hidden"), hidden);
        }
        return entries;
    }

    /**
     * The tab search's rows: every tab open across the windows, so a
     * search jumps to one, then what can be opened again, so it opens
     * one; for the quick switcher, once something is typed, what each page
     * finds under the page's heading. A section with nothing left in it is
     * dropped, and a filter that matches nothing says so rather than
     * closing the window under the hand that is typing.
     */
    private List<MenuWindow.Entry> searchRows(String filter,
                                              boolean findInPages) {
        List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>();
        List<MenuWindow.Entry> open = new ArrayList<MenuWindow.Entry>();
        for (Window window : WindowLayout.windows()) {
            // Every open tab, those the view hides too: a search finds a
            // tab wherever it waits, and going to it shows it.
            for (WindowPage tab : window.getTabs()) {
                String name = tab.title();
                if (tab.isAvailable()
                        && WindowMenus.matchesFilter(name, filter)) {
                    open.add(new MenuWindow.Entry(ENTRY_OPEN_PREFIX + tab.id(),
                            name, tab.isMuted(), tab.tone(), tab));
                }
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.window.search.open"), open);
        entries.addAll(openRows(filter, true));
        if (findInPages && filter.trim().length() > 0) {
            for (WindowPages.Page page : WindowPages.all()) {
                addFound(entries, page, filter.trim());
            }
        }
        if (entries.isEmpty()) {
            entries.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                    findInPages ? "gui.losttales.window.switcher.none"
                            : "gui.losttales.window.search.none")));
        }
        return entries;
    }

    /** What a page finds by the words, under its heading, each row's id naming the page. */
    private static void addFound(List<MenuWindow.Entry> entries,
                                 WindowPages.Page page, String words) {
        PageContent content = page.content();
        if (!page.tab().isAvailable() || content.findHeading().length() == 0) {
            return;
        }
        List<MenuWindow.Entry> found = new ArrayList<MenuWindow.Entry>();
        for (MenuWindow.Entry row : content.find(words)) {
            found.add(row.renamed(ENTRY_FIND_PREFIX + page.id + ":" + row.id));
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                content.findHeading()), found);
    }

    /**
     * One row of the {@code +} or of the tab search: the tab joins the
     * window the menu was opened for, or — when that window is gone — the
     * first window, or a window of its own when none is left. A locked
     * window takes nothing: asked of one, a page opens where it last
     * stood, and a conversation in an unlocked window of conversations,
     * else in a window of its own. The tab then takes the keys.
     */
    private void openFromMenu(String windowId, MenuWindow.Entry entry) {
        open(windowId, WindowPage.fromId(entry.id));
    }

    private void open(String windowId, WindowPage tab) {
        if (tab == null) {
            return;
        }
        Window target = WindowLayout.window(windowId);
        if (target == null) {
            target = WindowLayout.firstWindow();
        }
        if (tab instanceof OtherPage && (target == null || target.isLocked())) {
            WindowLayout.showPage((OtherPage)tab);
            this.screen.jumpToTab(tab);
            return;
        }
        WindowPage opened = target == null
                ? WindowLayout.openInNewWindow(tab)
                : WindowLayout.openTab(tab, target.getId());
        if (opened != null) {
            this.screen.jumpToTab(opened);
        }
    }

    /* ---- The sources ---- */

    /**
     * A tab's options, named as its dots are, in the tool strip's order:
     * the page's own choices, a hairline, the row that opens the settings
     * of its kind and the row that opens its split view.
     */
    private final class TabSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof WindowPage
                    && WindowLayout.isOpen((WindowPage)menu.about());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            WindowPage tab = (WindowPage)menu.about();
            menu.setTitle(tab.optionsTitle(), LostTalesUiSheet.MORE);
            List<MenuWindow.Entry> rows = optionRows(tab.options());
            if (!rows.isEmpty()) {
                rows.add(MenuWindow.Entry.separator());
            }
            if (tab.settingsPlace() != null) {
                rows.add(settingsRow(tab.settingsPlace()));
            }
            rows.add(splitViewRow(tab));
            menu.setRows(rows);
        }

        /**
         * The settings row opens its settings beside the menu, the split
         * view row the split view, and a pick's row its words, the menu
         * staying; the rest are the tab's: its switches stay, its actions
         * are done with it.
         */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            if (entry.id.startsWith(SETTINGS_PREFIX)) {
                toggleSettingsBeside(entry, window);
                return true;
            }
            WindowPage tab = (WindowPage)menu.about();
            if (SPLIT_VIEW.equals(entry.id)) {
                toggleSplit(tab, WindowMenus.besideWindow(window));
                return true;
            }
            PageOption option = optionOf(tab, entry.id);
            if (option != null && option.kind == PageOption.Kind.PICK) {
                togglePick(tab, option, WindowMenus.besideWindow(window));
                return true;
            }
            return tab.takeOption(entry.id);
        }
    }

    /** One of a tab's options whose words a sub-window shows. */
    private static final class Picking {
        final WindowPage tab;
        final String optionId;

        Picking(WindowPage tab, String optionId) {
            this.tab = tab;
            this.optionId = optionId;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Picking)) {
                return false;
            }
            Picking picking = (Picking)other;
            return this.tab.equals(picking.tab)
                    && this.optionId.equals(picking.optionId);
        }

        @Override
        public int hashCode() {
            return this.tab.hashCode() * 31 + this.optionId.hashCode();
        }
    }

    /**
     * The words one of a tab's options picks from, named as the option
     * is: each a row with its glyph and what it does under the pointer,
     * the one chosen marked. A pick stays, so another word can be tried.
     */
    private final class PickSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            if (!(menu.about() instanceof Picking)) {
                return false;
            }
            Picking picking = (Picking)menu.about();
            return WindowLayout.isOpen(picking.tab)
                    && optionOf(picking.tab, picking.optionId) != null;
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Picking picking = (Picking)menu.about();
            PageOption option = optionOf(picking.tab, picking.optionId);
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
            if (option != null) {
                menu.setTitle(option.label, option.glyph.sprite());
                for (PageOption word : option.choices()) {
                    rows.add(word.row());
                }
            }
            menu.setRows(rows);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            boolean stays = ((Picking)menu.about()).tab.takeOption(entry.id);
            TabMenus.this.menus.rebuildIfOpen(SubWindowKind.TAB);
            return stays;
        }
    }

    /**
     * A tab's split view, named as its button is: the pages that can stand
     * beside it, or, while it stands in a split, the split's own rows.
     */
    private final class SplitSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof WindowPage
                    && WindowLayout.isOpen((WindowPage)menu.about());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            WindowPage tab = (WindowPage)menu.about();
            menu.setTitle(StatCollector.translateToLocal(
                    "gui.losttales.window.split.title"), LostTalesUiSheet.SPLIT);
            List<MenuWindow.Entry> rows = splitRows(tab);
            Window window = WindowLayout.windowOf(tab);
            if (window != null && window.splitOf(tab) == null) {
                addOpenBeside(rows, window);
            }
            menu.setRows(rows);
        }

        /**
         * Under *Open Beside*, what the {@code +} would open, each opening
         * straight beside the page: closed channels, people to whisper
         * to, pages no window holds. Greyed while the padlock holds the
         * window.
         */
        private void addOpenBeside(List<MenuWindow.Entry> rows, Window window) {
            List<MenuWindow.Entry> closed = new ArrayList<MenuWindow.Entry>();
            for (MenuWindow.Entry row : openRows("", true)) {
                if (!row.header && !row.separator && !row.passive) {
                    closed.add(heldWhileLocked(
                            row.renamed(SPLIT_OPEN_PREFIX + row.id), window));
                }
            }
            if (closed.isEmpty()) {
                return;
            }
            if (rows.size() == 1 && rows.get(0).passive) {
                // "No other page can stand beside" gives way to what can.
                rows.clear();
            }
            WindowMenus.addSection(rows, StatCollector.translateToLocal(
                    "gui.losttales.window.split.open_beside"), closed);
        }

        /** Swapping and turning stay for another try; the rest are done with it. */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            WindowPage tab = (WindowPage)menu.about();
            if (entry.id.startsWith(SPLIT_OPEN_PREFIX)) {
                openBeside(tab, WindowPage.fromId(
                        entry.id.substring(SPLIT_OPEN_PREFIX.length())));
                return false;
            }
            actOnSplit(tab, entry.id);
            TabMenus.this.menus.rebuildIfOpen(SubWindowKind.TAB);
            return SPLIT_SWAP.equals(entry.id) || SPLIT_TURN.equals(entry.id);
        }
    }

    /**
     * A tab's help, named as its question mark is: the guide, a hairline,
     * then the tab's own keys and the keys every page shares, under a
     * field that finds keys by their words or their names. While words
     * stand in the field the guide steps aside.
     */
    private final class HelpSource extends WindowMenus.Source {
        /** Rows the help opens with at most; it reads, so it opens taller than a menu. */
        private static final int VISIBLE_ROWS = 20;

        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof WindowPage
                    && WindowLayout.isOpen((WindowPage)menu.about());
        }

        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(
                            "gui.losttales.window.help.search"),
                    new int[] {Keyboard.KEY_F1}, LostTalesUiSheet.SEARCH,
                    MenuWindow.MAX_FILTER_LENGTH, false);
            menu.setRowHeight(MenuWindow.TALL_ROW_HEIGHT);
            menu.setVisibleRows(VISIBLE_ROWS);
        }

        @Override
        public void rebuild(MenuWindow menu) {
            WindowPage tab = (WindowPage)menu.about();
            menu.setTitle(StatCollector.translateToLocalFormatted(
                    "gui.losttales.window.help.title", tab.title()),
                    LostTalesUiSheet.QUESTION);
            PageHelp help = tab.help();
            List<PageKeys.Area> areas =
                    new ArrayList<PageKeys.Area>(help.areas);
            areas.addAll(PageKeys.windowAreas());
            String filter = menu.filter().trim();
            List<MenuWindow.Entry> rows = new ArrayList<MenuWindow.Entry>();
            if (filter.length() == 0) {
                for (String paragraph : help.guide) {
                    rows.add(MenuWindow.Entry.note(paragraph));
                }
                if (!rows.isEmpty()) {
                    rows.add(MenuWindow.Entry.separator());
                }
            }
            List<MenuWindow.Entry> keys = PageKeys.rows(areas, filter);
            if (keys.isEmpty()) {
                rows.add(MenuWindow.Entry.passive(StatCollector.translateToLocal(
                        "gui.losttales.window.help.none")));
            }
            rows.addAll(keys);
            menu.setRows(rows);
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        /** Nothing in a help is taken: it is read. */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            return true;
        }
    }

    /** A window's own menu. */
    private final class WindowSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            Window window = menu.about() instanceof String
                    ? WindowLayout.window((String)menu.about()) : null;
            return window != null;
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Window window = WindowLayout.window((String)menu.about());
            menu.setTitle(StatCollector.translateToLocal(
                    "gui.losttales.window.menu"), LostTalesUiSheet.MORE);
            List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>(6);
            if (window != null) {
                entries.add(pinRow(ENTRY_PIN_HUD,
                        "gui.losttales.window.menu.pin_hud",
                        window.isPinnedToHud()));
                entries.add(pinRow(ENTRY_PIN_GUI,
                        "gui.losttales.window.menu.pin_gui",
                        window.isPinnedToGui()));
                entries.add(MenuWindow.Entry.separator());
                entries.add(heldWhileLocked(new MenuWindow.Entry(
                        ENTRY_WINDOW_RESET, StatCollector.translateToLocal(
                                "gui.losttales.window.menu.reset")), window));
                entries.add(MenuWindow.Entry.separator());
                entries.add(settingsRow(Settings.Place.WINDOWS));
            }
            menu.setRows(entries);
        }

        /**
         * Window Settings opens beside the menu, or goes away while it is
         * out, and the menu stays; so do the pins; the rest are done with
         * it.
         */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow menuWindow, boolean back) {
            if (entry.id.startsWith(SETTINGS_PREFIX)) {
                if (!back) {
                    toggleSettingsBeside(entry, menuWindow);
                }
                return true;
            }
            Window window = WindowLayout.window((String)menu.about());
            if (window == null) {
                return false;
            }
            if (ENTRY_PIN_HUD.equals(entry.id)) {
                WindowLayout.setPinnedToHud(window.getId(),
                        !window.isPinnedToHud());
                return true;
            }
            if (ENTRY_PIN_GUI.equals(entry.id)) {
                WindowLayout.setPinnedToGui(window.getId(),
                        !window.isPinnedToGui());
                return true;
            }
            if (ENTRY_WINDOW_RESET.equals(entry.id)
                    && WindowLayout.resetWindow(window.getId())) {
                ContentView.leave(window);
                for (WindowPage tab : window.getTabs()) {
                    tab.resetIn(window);
                }
            }
            return false;
        }
    }

    /** The {@code +}: what can be opened again. */
    private final class OpenSource extends WindowMenus.Source {
        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.PLUS);
            menu.setRows(openRows("", false));
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            if (entry.id.startsWith(ENTRY_OPEN_PREFIX)) {
                // A tab the view hides: it shows where it stands.
                TabMenus.this.screen.jumpToTab(WindowPage.fromId(
                        entry.id.substring(ENTRY_OPEN_PREFIX.length())));
            } else {
                openFromMenu((String)menu.about(), entry);
            }
            return false;
        }
    }

    /**
     * The tab search, every tab, open or not, narrowed as it is typed; or
     * the quick switcher, which finds in the pages besides.
     */
    private final class SearchSource extends WindowMenus.Source {
        private final boolean switcher;

        SearchSource(SubWindowKind kind) {
            this.switcher = kind == SubWindowKind.SWITCHER;
        }

        @Override
        public void prepare(MenuWindow menu) {
            menu.openField(StatCollector.translateToLocal(this.switcher
                            ? "gui.losttales.window.switcher.prompt"
                            : "gui.losttales.window.search.prompt"),
                    this.switcher ? WindowKeys.withCommand(Keyboard.KEY_K)
                            : WindowKeys.withCommand(Keyboard.KEY_LSHIFT,
                                    Keyboard.KEY_A),
                    LostTalesUiSheet.SEARCH, MenuWindow.MAX_FILTER_LENGTH,
                    false);
        }

        @Override
        public void rebuild(MenuWindow menu) {
            menu.setTitle(null, LostTalesUiSheet.SEARCH);
            menu.setRows(searchRows(menu.filter(), this.switcher));
        }

        @Override
        public boolean readsAsTyped() {
            return true;
        }

        @Override
        public MenuWindow.Entry firstFound(MenuWindow menu) {
            return WindowMenus.firstTyped(menu);
        }

        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            if (entry.id.startsWith(ENTRY_OPEN_PREFIX)) {
                TabMenus.this.screen.jumpToTab(WindowPage.fromId(
                        entry.id.substring(ENTRY_OPEN_PREFIX.length())));
            } else if (entry.id.startsWith(ENTRY_FIND_PREFIX)) {
                showFound((String)menu.about(), entry.id.substring(
                        ENTRY_FIND_PREFIX.length()));
            } else {
                openFromMenu((String)menu.about(), entry);
            }
            return false;
        }
    }

    /**
     * A row a page found: the page comes forward with the keys — opened
     * where the menu was, if no window holds it — and shows what was
     * found.
     */
    private void showFound(String windowId, String pageAndId) {
        int colon = pageAndId.indexOf(':');
        OtherPage tab = colon < 0 ? null
                : WindowPages.tab(pageAndId.substring(0, colon));
        if (tab == null) {
            return;
        }
        if (WindowLayout.isOpen(tab)) {
            this.screen.jumpToTab(tab);
        } else {
            open(windowId, tab);
        }
        tab.content().show(pageAndId.substring(colon + 1));
    }
}
