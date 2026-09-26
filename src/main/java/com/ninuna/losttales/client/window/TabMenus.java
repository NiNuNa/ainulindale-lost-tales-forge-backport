package com.ninuna.losttales.client.window;

import com.ninuna.losttales.gui.style.LostTalesUiSheet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;
import org.lwjgl.input.Keyboard;

/**
 * The window's own menus: a tab's, behind the tool strip's cog or under a
 * right-click on the tab; a window's, behind the three dots at the end of
 * its row; the {@code +}, listing what can be opened again; the tab
 * search over every tab, open or not; and the quick switcher, the tab
 * search with what the pages find besides. What a tab's menu holds is the
 * tab's own ({@link WindowTab#menuRows}); what can be opened is each
 * system's ({@link ScreenPart#addOpenable}) and the pages no window holds;
 * what a page finds is its own ({@link PageContent#find}).
 */
final class TabMenus {
    private static final String ENTRY_DETACH = "window:detach";
    /** Marks a search row that jumps to a tab already open. */
    private static final String ENTRY_OPEN_PREFIX = "open:";
    /** Marks a row a page found: this, the page's id, a colon, and the row's own id. */
    private static final String ENTRY_FIND_PREFIX = "find:";
    private static final String ENTRY_WINDOW_UNSTICK = "window_unstick";
    private static final String ENTRY_WINDOW_RESET = "window_reset";
    /** The window menu's last row, which opens Settings beside it. */
    private static final String ENTRY_SETTINGS = "settings";
    /**
     * The message lines Reset Size gives a conversation's window: enough
     * to read a conversation back without scrolling, and a whole number,
     * so the topmost row is never a clipped one.
     */
    private static final int RESET_LINES = 10;

    private final WindowScreen screen;
    private final WindowMenus menus;

    TabMenus(WindowScreen screen, WindowMenus menus) {
        this.screen = screen;
        this.menus = menus;
        menus.register(SubWindowKind.TAB, new TabSource());
        menus.register(SubWindowKind.WINDOW, new WindowSource());
        menus.register(SubWindowKind.OPEN, new OpenSource());
        menus.register(SubWindowKind.TAB_SEARCH, new SearchSource(
                SubWindowKind.TAB_SEARCH));
        menus.register(SubWindowKind.SWITCHER, new SearchSource(
                SubWindowKind.SWITCHER));
    }

    /* ---- What opens them ---- */

    /**
     * The tab's menu, behind its cog on the tool strip — a switch, as the
     * cog is — or under a right-click on the tab, which only opens it or
     * turns it to the tab: the tab's own rows, and a window of its own for
     * it. Closing is the cross on the tab and nothing else: a row that
     * only repeats the button beside it is a second way to lose a tab by
     * accident.
     */
    void showTabMenu(WindowTab tab, SubWindowAnchor anchor, boolean toggle) {
        if (tab != null) {
            this.menus.show(SubWindowKind.TAB, tab,
                    WindowMenus.hangingFrom(anchor), toggle);
        }
    }

    /**
     * The window's own menu, behind the three dots at the end of its row —
     * a switch, as the dots are — or under a right-click on the strip: the
     * settings a window has that nothing else on the row offers. Locking
     * and closing are not among them, since the padlock and the cross
     * stand beside the dots. Unsticking is offered while the window is
     * stuck to a neighbour, and Reset Size puts it back to its tab's own
     * shape. A locked window offers no dots, and one locked while the menu
     * stands closes it. Its last row opens Settings beside it.
     */
    void showWindowMenu(Window window, SubWindowAnchor anchor,
                        boolean toggle) {
        if (window != null && !window.isLocked()) {
            this.menus.show(SubWindowKind.WINDOW, window.getId(),
                    WindowMenus.hangingFrom(anchor), toggle);
        }
    }

    /**
     * The {@code +} menu for a window, hung from its control, or — from
     * the keyboard ({@code anchor} null) — from the row of the window, else
     * from the empty screen's {@code +} ({@code emptyPlus}); a switch like
     * its control. Nothing opens while there is nothing to open.
     */
    void toggleOpen(Window window, SubWindowAnchor anchor,
                    SubWindowAnchor emptyPlus) {
        String windowId = window == null ? null : window.getId();
        this.menus.show(SubWindowKind.OPEN, windowId,
                WindowMenus.hangingFrom(anchor != null ? anchor
                        : keyboardAnchor(windowId, emptyPlus)), true);
    }

    /**
     * The tab search for a window, hung as the {@code +} is. A switch:
     * out for this window already, it goes away; out for another, it
     * turns to this one and starts afresh.
     */
    void toggleSearch(Window window, SubWindowAnchor anchor,
                      SubWindowAnchor emptyPlus) {
        String windowId = window == null ? null : window.getId();
        this.menus.show(SubWindowKind.TAB_SEARCH, windowId,
                WindowMenus.hangingFrom(anchor != null ? anchor
                        : keyboardAnchor(windowId, emptyPlus)), true);
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
     * its tab row, where the row's first controls stand; the empty
     * screen's {@code +} for no window.
     */
    private SubWindowAnchor keyboardAnchor(String windowId,
                                           SubWindowAnchor emptyPlus) {
        if (windowId == null) {
            return emptyPlus;
        }
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

    /** The window menu's name: its tab in front, which names it on its row. */
    private static String windowTitle(Window window) {
        WindowTab front = window == null ? null : WindowFrame.activeTab(
                window, WindowFrame.visibleTabs(window));
        return front == null ? null : StatCollector.translateToLocalFormatted(
                "gui.losttales.window.sub.window_of", front.title());
    }

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
            PageTab tab = page.tab();
            if (!WindowLayout.isOpen(tab) && tab.isAvailable()
                    && WindowMenus.matchesFilter(page.title(), filter)) {
                pages.add(new MenuWindow.Entry(tab.id(), page.title(), false,
                        -1, tab));
            }
        }
        WindowMenus.addSection(entries, StatCollector.translateToLocal(
                "gui.losttales.window.open.pages"), pages);
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
            for (WindowTab tab : WindowFrame.visibleTabs(window)) {
                String name = tab.title();
                if (WindowMenus.matchesFilter(name, filter)) {
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
     * window the menu was opened for, or — for the empty screen's, or
     * when that window is gone — the first window, or a window of its own
     * when none is left; a page with no window to join opens where it
     * last stood. The tab then takes the keys.
     */
    private void openFromMenu(String windowId, MenuWindow.Entry entry) {
        open(windowId, WindowTab.fromId(entry.id));
    }

    private void open(String windowId, WindowTab tab) {
        if (tab == null) {
            return;
        }
        Window target = WindowLayout.window(windowId);
        if (target == null) {
            target = WindowLayout.firstWindow();
        }
        if (tab instanceof PageTab && target == null) {
            WindowLayout.showPage((PageTab)tab);
            this.screen.focusPage((PageTab)tab);
            return;
        }
        WindowTab opened = target == null
                ? WindowLayout.openInNewWindow(tab)
                : WindowLayout.openTab(tab, target.getId());
        if (opened != null) {
            this.screen.jumpToTab(opened);
        }
    }

    /* ---- The sources ---- */

    /** A tab's menu: the tab's own rows, and a window of its own for it. */
    private final class TabSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            return menu.about() instanceof WindowTab
                    && WindowLayout.isOpen((WindowTab)menu.about());
        }

        @Override
        public void rebuild(MenuWindow menu) {
            WindowTab tab = (WindowTab)menu.about();
            menu.setTitle(tab.title(), LostTalesUiSheet.COG);
            List<MenuWindow.Entry> rows =
                    new ArrayList<MenuWindow.Entry>(tab.menuRows());
            // A layout action the row may have no room for: a window of
            // its own, offered whenever the layout would allow it.
            Window window = WindowLayout.windowOf(tab);
            if (window != null && !window.isLocked()
                    && window.getTabs().size() > 1) {
                rows.add(new MenuWindow.Entry(ENTRY_DETACH,
                        StatCollector.translateToLocal(
                                "gui.losttales.window.tab.detach")));
            }
            menu.setRows(rows);
        }

        /** The tab's switches stay; its actions and the detach are done with it. */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow window, boolean back) {
            WindowTab tab = (WindowTab)menu.about();
            if (ENTRY_DETACH.equals(entry.id)) {
                TabMenus.this.screen.detachTab(tab);
                return false;
            }
            return tab.takeMenuRow(entry.id);
        }
    }

    /** A window's own menu. */
    private final class WindowSource extends WindowMenus.Source {
        @Override
        public boolean stillStands(MenuWindow menu) {
            Window window = menu.about() instanceof String
                    ? WindowLayout.window((String)menu.about()) : null;
            return window != null && !window.isLocked();
        }

        @Override
        public void rebuild(MenuWindow menu) {
            Window window = WindowLayout.window((String)menu.about());
            menu.setTitle(windowTitle(window), LostTalesUiSheet.MORE);
            List<MenuWindow.Entry> entries = new ArrayList<MenuWindow.Entry>(3);
            if (window != null) {
                if (window.isLinked()) {
                    entries.add(new MenuWindow.Entry(ENTRY_WINDOW_UNSTICK,
                            StatCollector.translateToLocal(
                                    "gui.losttales.window.menu.unstick")));
                }
                entries.add(new MenuWindow.Entry(ENTRY_WINDOW_RESET,
                        StatCollector.translateToLocal(
                                "gui.losttales.window.menu.reset_size")));
                entries.add(new MenuWindow.Entry(ENTRY_SETTINGS,
                        StatCollector.translateToLocal(
                                "gui.losttales.window.menu.settings"))
                        .withSprite(LostTalesUiSheet.COG,
                                LostTalesUiSheet.COG_HOVER, false));
            }
            menu.setRows(entries);
        }

        /**
         * Settings opens beside the menu, or goes away while it is out,
         * and the menu stays; the rest are done with it.
         */
        @Override
        public boolean act(MenuWindow menu, MenuWindow.Entry entry,
                           SubWindow menuWindow, boolean back) {
            if (ENTRY_SETTINGS.equals(entry.id)) {
                TabMenus.this.menus.show(SubWindowKind.SETTINGS, null,
                        WindowMenus.besideWindow(menuWindow), true);
                return true;
            }
            Window window = WindowLayout.window((String)menu.about());
            if (window == null) {
                return false;
            }
            if (ENTRY_WINDOW_UNSTICK.equals(entry.id)) {
                WindowLayout.unlink(window.getId());
            } else if (ENTRY_WINDOW_RESET.equals(entry.id)) {
                resetSize(window);
            }
            return false;
        }
    }

    /**
     * A page's window goes back to a page's size. Any other takes a
     * conversation's: a whole number of message lines, so the topmost row
     * is never a clipped one, and just wide enough to show every one of
     * the window's tabs whole. A window filling a part of the screen takes
     * the size it is given.
     */
    private static void resetSize(Window window) {
        Minecraft minecraft = Minecraft.getMinecraft();
        boolean page = WindowLayout.showsPage(window);
        WindowLayout.setFill(window.getId(), Window.ScreenFill.NONE, false);
        WindowLayout.setWindowHeight(window.getId(), page
                ? WindowLayout.PAGE_HEIGHT
                : WindowPlacement.heightForRoom(
                        RESET_LINES * WindowStyle.LINE_HEIGHT, minecraft),
                false);
        WindowLayout.setWindowWidth(window.getId(), page
                ? WindowLayout.PAGE_WIDTH
                : TabRow.boxWidthForWholeRow(minecraft, window), true);
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
            openFromMenu((String)menu.about(), entry);
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
                TabMenus.this.screen.jumpToTab(WindowTab.fromId(
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
        PageTab tab = colon < 0 ? null
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
