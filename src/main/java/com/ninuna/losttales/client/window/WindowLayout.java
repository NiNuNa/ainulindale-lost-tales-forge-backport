package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one authoritative window layout: which windows exist, which tabs
 * each holds in what order, which tab is in front, whether a window is
 * locked, where each window sits and how big it is, and which window
 * stands in front of which. Every window stands in one view: its
 * category's, or the Lost Tales Menu's, whose windows hold anything
 * ({@link #viewOf}). A tab lives in at most one window, and a page the
 * game or a key opens goes to a window of its category's view; one opened
 * by hand on the screen goes to the view the screen shows
 * ({@link #openByHand}). A page may
 * stand open more than once, each copy a tab of its own; asked about a
 * page, the layout answers with the copy used last ({@link #lastUsed}).
 * Every window is equal: one that loses its last
 * tab disappears, and the layout with no windows left at all is a valid
 * state. Windows stand on their own: one dragged against another's edge
 * lines up with it and stays where it was put.
 *
 * <p>What the tabs stand for is their systems' business: the chat keeps
 * its channels' preferences and the windows a new player starts with in
 * {@code ChatLayout}. Every mutation is reported to the registered
 * listener, which the file-backed store uses to persist the layout;
 * position changes made while dragging are reported only when the
 * caller asks for it.</p>
 */
public final class WindowLayout {
    /**
     * Absolute floor on a stored width or height, in GUI pixels, so a
     * hand-edited file cannot leave a window nothing fits in. What a drag
     * may actually reach is {@code WindowPlacement}'s least box.
     */
    public static final int MIN_WINDOW_SIZE = 40;
    /** Widest and tallest a stored size may be; no screen is anywhere near this. */
    public static final int MAX_WINDOW_SIZE = 4096;
    /**
     * The screen a new window is placed on when there is no client to
     * measure one: 854 by 480 at GUI scale two, the smallest window the
     * game opens at. Only ever used off the client.
     */
    private static final int HEADLESS_SCREEN_WIDTH = 427;
    private static final int HEADLESS_SCREEN_HEIGHT = 240;
    private static final String ID_PREFIX = "w";

    private static final List<Window> WINDOWS = new ArrayList<Window>();
    private static final List<Window> WINDOWS_VIEW =
            Collections.unmodifiableList(WINDOWS);
    /**
     * Stacking order, back to front, by window id: the window last
     * brought to the front draws last and is hit first. Session state,
     * never stored; windows not listed sit at the back in layout order.
     */
    private static final List<String> STACK = new ArrayList<String>();
    /**
     * Where each category's first window opens, once a window of it was
     * locked by hand; a category with none opens at its shipped defaults.
     */
    private static final Map<PageCategory, Place> CATEGORY_PLACES =
            new EnumMap<PageCategory, Place>(PageCategory.class);
    private static int nextWindowNumber = 1;
    private static Runnable changeListener;
    /** What lays the windows out for a new player; see {@link #reset}. */
    private static Runnable defaults;
    /**
     * Each page that took another tab's place in its row, by the tab it
     * replaced: a page picked on a New Page tab ({@link #replaceTab}). The
     * row draws it on from where that tab stood, as Chrome's new tab turns
     * into the site picked, so nothing closes and nothing opens. Session
     * state, never stored.
     */
    private static final Map<WindowPage, WindowPage> REPLACED =
            new HashMap<WindowPage, WindowPage>();
    /** The pages a category's first window opens with besides the one asked for. */
    private static final Map<PageCategory, FirstPages> FIRST_PAGES =
            new EnumMap<PageCategory, FirstPages>(PageCategory.class);

    /** The pages a category's first window opens with: the chat's Global and OOC. */
    public interface FirstPages {
        List<? extends WindowPage> pages();
    }

    /**
     * Every page of a category a player can open now, in its own order:
     * what a category's first window holds when its key opens it.
     */
    public interface ViewPages {
        List<? extends WindowPage> pagesOf(PageCategory category);
    }

    /** What answers for each category's pages: the windows' own pages, the chat's consoles. */
    private static final List<ViewPages> VIEW_PAGES = new ArrayList<ViewPages>();

    /** Adds what answers for some categories' pages ({@link #openView}). */
    public static synchronized void addViewPages(ViewPages pages) {
        if (pages != null && !VIEW_PAGES.contains(pages)) {
            VIEW_PAGES.add(pages);
        }
    }

    /** Gives a category the pages its first window opens with, besides the one asked for. */
    public static synchronized void setFirstPages(PageCategory category,
                                                  FirstPages pages) {
        if (category != null) {
            FIRST_PAGES.put(category, pages);
        }
    }

    static {
        reset();
    }

    private WindowLayout() {}

    /** Called after every persisted mutation; the store saves here. */
    public static synchronized void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    /**
     * Back to the layout a player starts with: no window at all, then the
     * windows the systems lay out for a new player ({@link #setDefaults}:
     * the chat's one window of Global and OOC).
     */
    public static synchronized void reset() {
        CATEGORY_PLACES.clear();
        REPLACED.clear();
        WINDOWS.clear();
        STACK.clear();
        nextWindowNumber = 1;
        if (defaults != null) {
            defaults.run();
        }
    }

    /** What lays the windows out for a new player, run by {@link #reset}. */
    public static synchronized void setDefaults(Runnable filler) {
        defaults = filler;
    }

    /**
     * Adds a window holding {@code tabs}, {@code active} in front, as a
     * category's first window stands: at the default place, filling the
     * part of the screen its category's first window fills
     * ({@link PageCategory#firstFill}), and locked but the New Page's
     * ({@link PageCategory#firstLocked}). How a system lays out its windows
     * for a new player, how a category with no window opens one, and how
     * tabs a loaded layout placed nowhere find a home. These are the only
     * windows that open locked. Null with no tabs; a tab already open
     * elsewhere stays where it is.
     */
    public static synchronized Window addWindow(List<? extends WindowPage> tabs,
                                                WindowPage active) {
        return addWindowIn(null, tabs, active);
    }

    /**
     * As {@link #addWindow}, the window standing in {@code view}: the first
     * window of that view. Null for the view of the page in front.
     */
    private static Window addWindowIn(PageCategory view,
                                      List<? extends WindowPage> tabs,
                                      WindowPage active) {
        List<WindowPage> fresh = new ArrayList<WindowPage>();
        if (tabs != null) {
            for (WindowPage tab : tabs) {
                if (tab != null && !holds(tab) && !fresh.contains(tab)) {
                    fresh.add(tab);
                }
            }
        }
        if (fresh.isEmpty()) {
            return null;
        }
        Window window = newWindow();
        window.tabs().addAll(fresh);
        window.setActiveTab(active);
        window.setView(view != null ? view : derivedView(window));
        PageCategory category = viewOf(window);
        window.setLocked(category.firstLocked());
        Place place = CATEGORY_PLACES.get(category);
        if (place == null || !staysPut(window.getActiveTab())) {
            window.setFill(firstFillOf(window));
        } else {
            place.applyTo(window);
        }
        WINDOWS.add(window);
        return window;
    }

    /**
     * Where a category's first window stands once a window of it was
     * locked by hand: its place, its size and the part of the screen it
     * filled.
     */
    static final class Place {
        final double x;
        final double y;
        final double height;
        final int width;
        final Window.ScreenFill fill;

        Place(double x, double y, double height, int width,
              Window.ScreenFill fill) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
        }

        static Place of(Window window) {
            return new Place(window.getOffsetX(), window.getOffsetY(),
                    window.getOwnHeight(), window.getOwnWidth(),
                    window.getFill());
        }

        void applyTo(Window window) {
            window.setOffsets(this.x, this.y);
            window.setOwnHeight(clampWindowHeight(this.height));
            window.setOwnWidth(this.width <= 0 ? 0 : clampWindowWidth(this.width));
            window.setFill(this.fill);
        }
    }

    /**
     * The view a window stands in: the one it was given, else its front
     * page's category's (the Lost Tales Menu's for a window of New Pages).
     */
    public static PageCategory viewOf(Window window) {
        return window.getView() != null ? window.getView() : derivedView(window);
    }

    /**
     * Whether a window stands in {@code view}: one of the Lost Tales Menu's
     * for its view; for a category's, a window of a category's view holding
     * a page of it, or of one sharing its windows (the channels and the
     * whispers), as a window carried pages of two categories by hand does.
     */
    public static boolean standsIn(Window window, PageCategory view) {
        if (view == PageCategory.MENU || viewOf(window) == PageCategory.MENU) {
            return viewOf(window) == view;
        }
        if (viewOf(window) == view) {
            return true;
        }
        for (WindowPage held : window.tabs()) {
            if (held.category().home() == view) {
                return true;
            }
        }
        return false;
    }

    /** The view a window's pages stand for: its front page's, else its first page's category's. */
    private static PageCategory derivedView(Window window) {
        WindowPage front = window.getActiveTab() != null
                ? window.getActiveTab()
                : window.tabs().isEmpty() ? null : window.tabs().get(0);
        PageCategory home = front == null ? null : front.category().home();
        return home == null || home == PageCategory.NEW_PAGE
                ? PageCategory.MENU : home;
    }

    /** Where each category's first window opens, for the layout file. */
    static synchronized Map<PageCategory, Place> categoryPlaces() {
        return new EnumMap<PageCategory, Place>(CATEGORY_PLACES);
    }

    /** What the layout file said of the categories' places. */
    static synchronized void loadCategoryPlaces(Map<PageCategory, Place> places) {
        CATEGORY_PLACES.clear();
        if (places != null) {
            CATEGORY_PLACES.putAll(places);
        }
    }

    /**
     * What a window fills as its category's first window, by the page in
     * front, else its first: what the category's first window fills, but
     * for a page standing for a thing in the world, a waystone's or a
     * missive board's, which stands at the default place, filling nothing.
     */
    private static Window.ScreenFill firstFillOf(Window window) {
        WindowPage front = window.getActiveTab() != null
                ? window.getActiveTab()
                : window.tabs().isEmpty() ? null : window.tabs().get(0);
        return front == null || !staysPut(front) ? Window.ScreenFill.NONE
                : viewOf(window).firstFill();
    }

    /* ---- Pages ---- */

    /**
     * Brings a page forward where a page opened by hand opens
     * ({@link #openByHand}), in front and raised. Null for no page.
     */
    public static synchronized Window showPage(OtherPage page) {
        return bringForward(openByHand(page, null));
    }

    /**
     * Brings a page forward as its key does ({@link #openView}): with no
     * window of its category, that category's first window opens with
     * every page of it. Null for no page.
     */
    public static synchronized Window showView(OtherPage page) {
        return bringForward(openView(page));
    }

    /**
     * Opens a page as its key does: the copy used last where one stands;
     * else where its category keeps its pages; and with no window of its
     * category, in its category's first window holding every page of the
     * category the player can open now, {@code tab} in front: the two
     * consoles, the settings pages. The chat's first window keeps Global
     * and OOC ({@link #setFirstPages}). Answers the page as the layout
     * holds it; null for none.
     */
    public static synchronized WindowPage openView(WindowPage tab) {
        if (tab == null) {
            return null;
        }
        PageCategory home = tab.category().home();
        WindowPage open = lastUsedIn(tab, home);
        if (open != null) {
            return open;
        }
        WindowPage placed = placeable(tab);
        if (placed == null) {
            return lastUsed(tab);
        }
        tab = placed;
        for (Window window : WINDOWS) {
            if (standsIn(window, home)) {
                return placeIn(tab, home, null);
            }
        }
        List<WindowPage> pages = new ArrayList<WindowPage>();
        for (ViewPages source : VIEW_PAGES) {
            for (WindowPage page : source.pagesOf(home)) {
                if (page != null && !isOpen(page) && !pages.contains(page)) {
                    pages.add(page);
                }
            }
        }
        for (WindowPage page : firstPagesWith(tab)) {
            if (!pages.contains(page)) {
                pages.add(page);
            }
        }
        Window first = addWindow(pages, tab);
        raise(first.getId());
        changed();
        return tab;
    }

    /** The window holding {@code shown}, with it in front and the window raised; null for none. */
    private static Window bringForward(WindowPage shown) {
        if (shown == null) {
            return null;
        }
        Window holding = windowOf(shown);
        holding.setActiveTab(shown);
        raise(holding.getId());
        changed();
        return holding;
    }

    /**
     * Opens a page where its category keeps its pages: the copy standing
     * in its category's view used last; else in the window asked for while
     * that window stands in its category's view and takes it; else in the
     * unlocked window of that view last brought to the front, with room;
     * else in a new window a step on from the window of that view last in
     * front, unlocked; and with no window of that view at all, in its
     * category's first window ({@link #addWindow}). A page that opens once
     * and stands in another view comes forward there. Answers the page as
     * the layout holds it; null for none.
     */
    public static synchronized WindowPage openInCategory(WindowPage tab,
                                                         String askedWindowId) {
        return openIn(tab, tab == null ? null : tab.category().home(),
                askedWindowId);
    }

    /**
     * Opens a page the player asked for on the screen where the screen
     * shows it ({@link WindowView#handView}): in its category's view while
     * that view is shown, else in the Lost Tales Menu's, as
     * {@link #openInCategory} does for a view. With no screen open, in its
     * category's view.
     */
    public static synchronized WindowPage openByHand(WindowPage tab,
                                                     String askedWindowId) {
        return openIn(tab, tab == null ? null : WindowView.handView(tab),
                askedWindowId);
    }

    private static WindowPage openIn(WindowPage tab, PageCategory view,
                                     String askedWindowId) {
        if (tab == null) {
            return null;
        }
        WindowPage open = lastUsedIn(tab, view);
        if (open != null) {
            return open;
        }
        WindowPage placed = placeable(tab);
        return placed == null ? lastUsed(tab)
                : placeIn(placed, view, askedWindowId);
    }

    /**
     * Opens a new copy of {@code tab}'s page where the player asked for it
     * ({@link #openByHand}), however many copies stand open already: the
     * New Page's way, Page Search's and Duplicate Page's. A page that opens
     * once is brought forward instead. Answers the copy; null for none.
     */
    public static synchronized WindowPage openCopy(WindowPage tab,
                                                   String askedWindowId) {
        WindowPage copy = freshCopy(tab);
        return copy == null ? openByHand(tab, askedWindowId)
                : placeIn(copy, WindowView.handView(copy), askedWindowId);
    }

    /**
     * The tab itself to put in a view while no window holds it, else a new
     * copy of its page; null for a page that opens once and stands open.
     */
    private static WindowPage placeable(WindowPage tab) {
        return holds(tab) ? freshCopy(tab) : tab;
    }

    /**
     * Opens a new copy of {@code tab}'s page at the end of a window's row,
     * in front, whatever the window's kind: the {@code +}'s new page. A
     * locked window, or one with no room, has it open in a new window a
     * step on from it, unlocked and in front. Null for no such window.
     */
    public static synchronized WindowPage openCopyIn(WindowPage tab,
                                                     String windowId) {
        Window window = window(windowId);
        WindowPage copy = freshCopy(tab);
        if (window == null || copy == null) {
            return null;
        }
        if (!takes(window, Collections.singletonList(copy))) {
            return openInNewWindow(copy, window);
        }
        window.tabs().add(copy);
        window.setActiveTab(copy);
        changed();
        return copy;
    }

    /**
     * Opens another copy of {@code tab}'s page right after it, in front, as
     * Chrome's Duplicate does: a view of its own of the same page. A
     * locked window, or one with no room, has it open where a page opened
     * by hand from there would. Null for a page that opens once.
     */
    public static synchronized WindowPage duplicate(WindowPage tab) {
        Window window = exactWindowOf(tab);
        if (window == null || !tab.opensMoreThanOnce()) {
            return null;
        }
        WindowPage copy = freshCopy(tab);
        if (copy == null) {
            return null;
        }
        copy.duplicatedFrom(tab);
        if (!takes(window, Collections.singletonList(copy))) {
            return placeIn(copy, viewOf(window), window.getId());
        }
        window.tabs().add(window.tabs().indexOf(tab) + 1, copy);
        window.setActiveTab(copy);
        settleSplits(window);
        changed();
        return copy;
    }

    /**
     * Puts a new copy of {@code page}'s page in {@code old}'s place, in
     * front: a New Page tab becomes the page picked on it, whatever the
     * window's category, as a tab carried there by hand may. The row draws
     * the copy on from where {@code old} stood ({@link #replacedBy}).
     * Refused in a locked window and for a tab no window holds. Answers
     * the copy; null when refused.
     */
    public static synchronized WindowPage replaceTab(WindowPage old,
                                                     WindowPage page) {
        Window window = old == null ? null : exactWindowOf(old);
        WindowPage copy = freshCopy(page);
        if (window == null || window.isLocked() || copy == null) {
            return null;
        }
        int index = window.tabs().indexOf(old);
        window.tabs().set(index, copy);
        for (int at = 0; at < window.splits().size(); at++) {
            WindowSplit split = window.splits().get(at);
            if (old.equals(split.first()) || old.equals(split.second())) {
                window.splits().set(at, split.replacing(old, copy));
            }
        }
        window.setActiveTab(copy);
        settleSplits(window);
        // Only the newest turn of each tab is worth keeping: what replaced
        // a tab that came back since, or went, is forgotten.
        Iterator<Map.Entry<WindowPage, WindowPage>> kept =
                REPLACED.entrySet().iterator();
        while (kept.hasNext()) {
            Map.Entry<WindowPage, WindowPage> entry = kept.next();
            if (holds(entry.getValue()) || !holds(entry.getKey())) {
                kept.remove();
            }
        }
        REPLACED.put(copy, old);
        changed();
        return copy;
    }

    /**
     * The tab {@code tab} took the place of in its row, by
     * {@link #replaceTab}, while that tab is open nowhere; null for none.
     */
    static synchronized WindowPage replacedBy(WindowPage tab) {
        WindowPage old = tab == null ? null : REPLACED.get(tab);
        return old == null || holds(old) ? null : old;
    }

    /**
     * A copy of {@code tab}'s page no window holds: the first free number,
     * starting afresh past the first. The page itself for a page that
     * opens once and stands nowhere; null for one open already.
     */
    private static WindowPage freshCopy(WindowPage tab) {
        if (tab == null) {
            return null;
        }
        WindowPage first = tab.firstInstance();
        if (!first.opensMoreThanOnce()) {
            return holds(first) ? null : first;
        }
        for (int instance = 1; instance <= WindowPage.MAX_INSTANCE; instance++) {
            WindowPage copy = first.withInstance(instance);
            if (copy != null && !holds(copy)) {
                copy.forgetCopy();
                return copy;
            }
        }
        return null;
    }

    /**
     * Where a page goes in {@code view}: the window asked for while it
     * stands in that view and takes it, else where the view keeps its pages
     * ({@link #openInCategory}); with no window of the view, its first
     * window: a category's with the pages it opens with, the Lost Tales
     * Menu's with the page alone.
     */
    private static WindowPage placeIn(WindowPage tab, PageCategory view,
                                      String askedWindowId) {
        Window asked = window(askedWindowId);
        Window window = receivingWindow(
                asked != null && standsIn(asked, view) ? asked : null, tab,
                view);
        if (window == null) {
            Window first = view == PageCategory.MENU
                    ? addWindowIn(view, Collections.singletonList(tab), tab)
                    : addWindowIn(view, firstPagesWith(tab), tab);
            raise(first.getId());
            changed();
            return tab;
        }
        window.tabs().add(tab);
        if (window.getActiveTab() == null) {
            window.setActiveTab(tab);
        }
        changed();
        return tab;
    }

    /**
     * The pages a category's first window opens with: those its category
     * gives ({@link #setFirstPages}) that no window holds, then {@code tab}.
     */
    private static List<WindowPage> firstPagesWith(WindowPage tab) {
        List<WindowPage> pages = new ArrayList<WindowPage>();
        FirstPages first = FIRST_PAGES.get(tab.category().home());
        if (first != null) {
            for (WindowPage page : first.pages()) {
                if (page != null && !isOpen(page) && !pages.contains(page)) {
                    pages.add(page);
                }
            }
        }
        if (!pages.contains(tab)) {
            pages.add(tab);
        }
        return pages;
    }

    /** Whether the window has a page in front. */
    public static synchronized boolean showsPage(Window window) {
        return window != null && window.getActiveTab() instanceof OtherPage;
    }

    /* ---- Pinning ---- */

    /**
     * Pins the window to the HUD, where it stays while playing with every
     * page it holds now and later, or lets it go. Answers whether anything
     * changed.
     */
    public static synchronized boolean setPinnedToHud(String windowId,
                                                      boolean pinned) {
        Window window = window(windowId);
        if (window == null || window.isPinnedToHud() == pinned) {
            return false;
        }
        window.setPinnedToHud(pinned);
        changed();
        return true;
    }

    /**
     * Pins the window to the GUI, where every view of the window screen
     * shows it whichever key opened the screen, or lets it go. Answers
     * whether anything changed.
     */
    public static synchronized boolean setPinnedToGui(String windowId,
                                                      boolean pinned) {
        Window window = window(windowId);
        if (window == null || window.isPinnedToGui() == pinned) {
            return false;
        }
        window.setPinnedToGui(pinned);
        changed();
        return true;
    }

    /** The windows pinned to the HUD, back to front: what stays on screen while playing. */
    public static synchronized List<Window> hudWindows() {
        List<Window> result = new ArrayList<Window>();
        for (Window window : stacked()) {
            if (window.isPinnedToHud() && !window.isClosed()) {
                result.add(window);
            }
        }
        return result;
    }

    /**
     * Whether the tab shows while playing: it stands in a window pinned to
     * the HUD and is no world page.
     */
    public static synchronized boolean isOnHud(WindowPage tab) {
        Window window = windowOf(tab);
        return window != null && window.isPinnedToHud() && !window.isClosed()
                && staysPut(tab);
    }

    /**
     * Whether every view shows the tab: it stands in a window pinned to
     * the GUI and is no world page.
     */
    public static synchronized boolean isOnGui(WindowPage tab) {
        Window window = windowOf(tab);
        return window != null && window.isPinnedToGui() && staysPut(tab);
    }

    /**
     * Whether a pinned window shows the tab: every tab but a page that
     * stands for a thing in the world, which closes as the player walks
     * away from it.
     */
    static boolean staysPut(WindowPage tab) {
        return tab != null && !(tab instanceof OtherPage
                && ((OtherPage)tab).page().opensFromWorld());
    }

    /* ---- Tabs ---- */

    /**
     * The window holding the tab, or, while that copy is closed, the window
     * of the copy of its page used last; null when no copy is open.
     */
    public static synchronized Window windowOf(WindowPage tab) {
        Window exact = exactWindowOf(tab);
        if (exact != null || tab == null) {
            return exact;
        }
        WindowPage copy = lastUsed(tab);
        return copy == null ? null : exactWindowOf(copy);
    }

    /** The window holding exactly this copy; null when it is closed. */
    private static Window exactWindowOf(WindowPage tab) {
        if (tab == null) {
            return null;
        }
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (WINDOWS.get(index).contains(tab)) {
                return WINDOWS.get(index);
            }
        }
        return null;
    }

    /** Whether some copy of the tab's page is open. */
    public static synchronized boolean isOpen(WindowPage tab) {
        return windowOf(tab) != null;
    }

    /** Whether a window holds exactly this copy. */
    public static synchronized boolean holds(WindowPage tab) {
        return exactWindowOf(tab) != null;
    }

    /**
     * The open copy of {@code tab}'s page used last, whichever copy
     * {@code tab} is: the copy in the window brought to the front last,
     * the one in front there first. What a key, a link or a page's news
     * reaches. Null with no copy open.
     */
    public static synchronized WindowPage lastUsed(WindowPage tab) {
        return lastUsedIn(tab, null);
    }

    /** As {@link #lastUsed}, among the windows of {@code view} alone; every window for null. */
    static synchronized WindowPage lastUsedIn(WindowPage tab, PageCategory view) {
        if (tab == null) {
            return null;
        }
        for (Window window : byRecency()) {
            if (view != null && !standsIn(window, view)) {
                continue;
            }
            WindowPage active = window.getActiveTab();
            if (active != null && active.isCopyOf(tab)) {
                return active;
            }
            for (WindowPage held : window.tabs()) {
                if (held.isCopyOf(tab)) {
                    return held;
                }
            }
        }
        return null;
    }

    /** Every open copy of {@code tab}'s page, in window and row order. */
    public static synchronized List<WindowPage> copiesOf(WindowPage tab) {
        List<WindowPage> copies = new ArrayList<WindowPage>();
        if (tab == null) {
            return copies;
        }
        for (Window window : WINDOWS) {
            for (WindowPage held : window.tabs()) {
                if (held.isCopyOf(tab)) {
                    copies.add(held);
                }
            }
        }
        return copies;
    }

    /**
     * The copy a request about {@code tab} reaches: the tab itself while a
     * window holds it, else the copy of its page used last.
     */
    private static WindowPage resolved(WindowPage tab) {
        if (holds(tab)) {
            return tab;
        }
        WindowPage open = lastUsed(tab);
        return open == null ? tab : open;
    }

    /** Every open tab in window order, each window's tabs in row order. */
    public static synchronized List<WindowPage> order() {
        List<WindowPage> result = new ArrayList<WindowPage>();
        for (int index = 0; index < WINDOWS.size(); index++) {
            result.addAll(WINDOWS.get(index).tabs());
        }
        return result;
    }

    /**
     * Whether the player may close the tab by hand: it is open and its
     * window is not locked. A locked window keeps the tabs it holds. The
     * last tab of the last window closes like any other.
     */
    public static synchronized boolean isClosable(WindowPage tab) {
        Window window = windowOf(tab);
        return window != null && !window.isLocked();
    }

    /**
     * Removes the tab from its window by hand, which a locked window
     * refuses; a window emptied this way is dropped, and the layout may end
     * up with no windows at all. A tab that ends by itself (a world page
     * walked away from, a whisper as the session ends) leaves through
     * {@link #removeTabs}, locked window or not.
     */
    public static synchronized boolean close(WindowPage tab) {
        if (!isClosable(tab)) {
            return false;
        }
        WindowPage held = resolved(tab);
        removeTab(exactWindowOf(held), held);
        changed();
        return true;
    }

    /**
     * Closes a window by hand. A locked window keeps everything it holds:
     * it only goes out of sight, and comes back as it was, where it was,
     * the next time its view opens or one of its pages is asked for
     * ({@link #reopen}). An unlocked window lets its pages go: every tab
     * of it the view shows leaves it, and the window goes with them unless
     * tabs hidden by the view stay in it for their own view; its category
     * forgets the place a padlock gave it unless a locked window of it
     * still stands, so the next first window opens at the default place.
     * What stands behind the tabs is untouched, and closing the last
     * window is allowed.
     */
    public static synchronized boolean closeWindow(String windowId) {
        final Window window = window(windowId);
        if (window == null || window.isClosed()) {
            return false;
        }
        if (window.isLocked()) {
            window.setClosed(true);
            STACK.remove(window.getId());
            changed();
            return true;
        }
        PageCategory category = viewOf(window);
        if (!hasLockedWindowOf(category, window)) {
            CATEGORY_PLACES.remove(category);
        }
        List<WindowPage> leaving = new ArrayList<WindowPage>();
        for (WindowPage tab : window.tabs()) {
            if (WindowView.shows(tab)) {
                leaving.add(tab);
            }
        }
        if (leaving.size() < window.tabs().size()) {
            final List<WindowPage> taken = leaving;
            removeTabs(new TabFilter() {
                @Override
                public boolean matches(WindowPage tab) {
                    return taken.contains(tab) && window.contains(tab);
                }
            });
            changed();
            return true;
        }
        window.tabs().clear();
        window.setActiveTab(null);
        dropWindow(window);
        changed();
        return true;
    }

    /** Whether a locked window of view {@code category} other than {@code but} stands, open or closed. */
    private static boolean hasLockedWindowOf(PageCategory category, Window but) {
        for (Window window : WINDOWS) {
            if (window != but && window.isLocked()
                    && viewOf(window) == category) {
                return true;
            }
        }
        return false;
    }

    /**
     * Brings back every window closed while locked that holds a page
     * {@code shows} answers for: the windows of a view as it opens.
     */
    static synchronized void reopen(TabFilter shows) {
        for (Window window : WINDOWS) {
            if (!window.isClosed()) {
                continue;
            }
            for (WindowPage tab : window.tabs()) {
                if (shows.matches(tab)) {
                    window.setClosed(false);
                    break;
                }
            }
        }
    }

    /** Whether the tab stands in a window closed while locked, out of sight. */
    public static synchronized boolean isClosedAway(WindowPage tab) {
        Window window = windowOf(tab);
        return window != null && window.isClosed();
    }

    /** Picks tabs out of the layout; see {@link #removeTabs}. */
    public interface TabFilter {
        boolean matches(WindowPage tab);
    }

    /**
     * Takes every tab the filter picks out of its window, a window left
     * empty going with it, and answers the tabs taken. A front tab taken
     * hands the front to its neighbour
     * ({@link #successor}). The listener is not told; the caller writes
     * the change when it is done.
     */
    public static synchronized List<WindowPage> removeTabs(TabFilter filter) {
        List<WindowPage> removed = new ArrayList<WindowPage>();
        Iterator<Window> iterator = WINDOWS.iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            WindowPage active = window.getActiveTab();
            int activeIndex = window.tabs().indexOf(active);
            int before = 0;
            List<WindowPage> taken = new ArrayList<WindowPage>();
            for (int index = 0; index < window.tabs().size(); index++) {
                WindowPage tab = window.tabs().get(index);
                if (filter.matches(tab)) {
                    taken.add(tab);
                    if (index < activeIndex) {
                        before++;
                    }
                }
            }
            if (taken.isEmpty()) {
                continue;
            }
            window.tabs().removeAll(taken);
            removed.addAll(taken);
            if (window.tabs().isEmpty()) {
                iterator.remove();
            } else if (taken.contains(active)) {
                window.setActiveTab(successor(window.tabs(),
                        activeIndex - before));
            }
            settleSplits(window);
        }
        return removed;
    }

    /**
     * The tab that comes forward when the one in front at {@code index}
     * leaves a row that holds {@code tabs} without it, as a browser's
     * tabs do: the tab that stood to its right, else the one to its
     * left, a tab not on screen now passed over while one that is stands
     * further along. Null for an empty row.
     */
    static WindowPage successor(List<WindowPage> tabs, int index) {
        if (tabs.isEmpty()) {
            return null;
        }
        int from = Math.max(0, Math.min(index, tabs.size()));
        for (int right = from; right < tabs.size(); right++) {
            if (WindowView.isShown(tabs.get(right))) {
                return tabs.get(right);
            }
        }
        for (int left = from - 1; left >= 0; left--) {
            if (WindowView.isShown(tabs.get(left))) {
                return tabs.get(left);
            }
        }
        return tabs.get(Math.min(from, tabs.size() - 1));
    }

    /**
     * The window a tab opening in no window of its own belongs in: the
     * window asked for (the one the player opened it from, or the one of
     * the conversation last used), else the window most recently brought
     * to the front that holds a page of its category, as long as its row
     * has room for one more; when none of them takes it, a new window opens
     * a step on from the window asked for, else from the window of its
     * category last in front, unlocked and in front of it.
     *
     * <p>A locked window takes nothing, whoever opens the tab: the padlock
     * keeps the tabs a window holds, so a whisper reaching a player whose
     * conversation windows are all locked opens in a window of its own.</p>
     *
     * <p>Null when none was asked for and no window holds a page of its
     * category: a conversation never opens a window of its own by itself,
     * and waits in the {@code +} until the player opens one; a page opened
     * by hand opens its category's first window
     * ({@link #openInCategory}).</p>
     */
    private static Window receivingWindow(Window preferred, WindowPage tab) {
        return receivingWindow(preferred, tab, tab.category().home());
    }

    /** As above, among the windows of {@code view}. */
    private static Window receivingWindow(Window preferred, WindowPage tab,
                                          PageCategory view) {
        // A window whose row cannot hold one more tab at its least — the
        // widest tab whole, every other down to its icon — is full.
        List<WindowPage> candidate = Collections.singletonList(tab);
        boolean asked = preferred != null && WINDOWS.contains(preferred);
        if (asked && takes(preferred, candidate)) {
            return preferred;
        }
        Window kin = null;
        for (Window window : byRecency()) {
            if (window != preferred && standsIn(window, view)) {
                if (kin == null) {
                    kin = window;
                }
                if (takes(window, candidate)) {
                    return window;
                }
            }
        }
        if (!asked && kin == null) {
            return null;
        }
        Window created = newWindow();
        created.setView(view);
        cascadeFrom(created, asked ? preferred : kin);
        WINDOWS.add(created);
        raise(created.getId());
        return created;
    }

    /** Whether the window takes the tabs: it is unlocked, and its row has room. */
    private static boolean takes(Window window,
                                 List<? extends WindowPage> tabs) {
        return !window.isLocked() && hasRoomFor(window, tabs);
    }

    /**
     * Whether the window's row has room for these tabs besides the ones
     * it shows. The width model is the tab row's; without a renderer to
     * measure with — headless tests, a broken frame — the answer is yes.
     */
    private static boolean hasRoomFor(Window window,
                                      List<? extends WindowPage> tabs) {
        try {
            return TabRow.rowHasRoomFor(
                    net.minecraft.client.Minecraft.getMinecraft(), window,
                    tabs);
        } catch (RuntimeException unavailable) {
            return true;
        } catch (LinkageError unavailable) {
            return true;
        }
    }

    /**
     * Opens a tab where it belongs ({@link #receivingWindow}), never in a
     * locked window, leaving the window's front tab alone, and answers the
     * tab as the layout holds it: the one already open, if it is. Null
     * when there is no window to open it in.
     */
    public static synchronized WindowPage openTab(WindowPage tab,
                                                 String preferredWindowId) {
        if (tab == null) {
            return null;
        }
        WindowPage open = lastUsed(tab);
        if (open != null) {
            // The tab as first opened, with the name's original casing.
            Window existing = exactWindowOf(open);
            return existing.getTabs().get(existing.getTabs().indexOf(open));
        }
        Window window = receivingWindow(window(preferredWindowId), tab);
        if (window == null) {
            return null;
        }
        window.tabs().add(tab);
        if (window.getActiveTab() == null) {
            window.setActiveTab(tab);
        }
        changed();
        return tab;
    }

    /**
     * Opens a tab in a window of its own, a step on from {@code reference},
     * else from the window in front, unlocked and in front of it: where a
     * new page goes when the window it was asked for takes none. Refused
     * for a tab a window holds.
     */
    public static synchronized WindowPage openInNewWindow(WindowPage tab,
                                                          Window reference) {
        if (tab == null || holds(tab)) {
            return null;
        }
        Window created = newWindow();
        Window from = reference != null && WINDOWS.contains(reference)
                ? reference : frontWindow();
        // It stands in the view of the window it steps on from.
        created.setView(from != null ? viewOf(from) : WindowView.handView(tab));
        cascadeFrom(created, from);
        created.tabs().add(tab);
        created.setActiveTab(tab);
        WINDOWS.add(created);
        raise(created.getId());
        changed();
        return tab;
    }

    /**
     * Moves a tab to {@code index} of the target window: the place it
     * ends up at once the move is done, clamped to the row, in a window
     * that may be its own — a reorder — or another one — a dock. A locked
     * window refuses, as source and as target. A source emptied by the
     * move disappears.
     */
    public static synchronized boolean moveTab(WindowPage tab,
                                               String targetWindowId,
                                               int index) {
        return moveTabs(Collections.singletonList(tab), targetWindowId,
                index);
    }

    /**
     * Moves a run of tabs to {@code index} of the target window, keeping
     * their relative order — what dragging a group of marked tabs
     * does. The index is the place the run ends up at once the tabs have
     * been lifted out, so a non-contiguous selection lands as one run
     * and a reorder is described by where the tabs go rather than by
     * which neighbour they land beside. Every tab must be open in one
     * and the same source window; a locked source or target refuses, a
     * reorder included, as does a target whose row has no room for the
     * tabs at their least. A source emptied by the move disappears.
     */
    public static synchronized boolean moveTabs(List<? extends WindowPage> tabs,
                                                String targetWindowId,
                                                int index) {
        return moveTabs(tabs, targetWindowId, index, true);
    }

    /**
     * As above; {@code persist} is false while a drag is in progress, so
     * a tab sliding along its row writes the file once, on release,
     * rather than every time it passes a neighbour.
     */
    public static synchronized boolean moveTabs(List<? extends WindowPage> tabs,
                                                String targetWindowId,
                                                int index, boolean persist) {
        List<WindowPage> moved = sameWindowTabs(tabs);
        Window target = window(targetWindowId);
        if (moved.isEmpty() || target == null || target.isLocked()) {
            return false;
        }
        Window source = windowOf(moved.get(0));
        if (source.isLocked()) {
            return false;
        }
        WindowPage active = source.getActiveTab();
        List<WindowPage> list = source.tabs();
        if (source == target) {
            List<WindowPage> reordered = new ArrayList<WindowPage>(list);
            reordered.removeAll(moved);
            reordered.addAll(Math.max(0,
                    Math.min(reordered.size(), index)), moved);
            if (reordered.equals(list)) {
                // The tabs are already where the drop asked for them;
                // nothing moved, so nothing is written.
                return false;
            }
            list.clear();
            list.addAll(reordered);
            source.setActiveTab(active);
            settleSplits(source);
            if (persist) {
                changed();
            }
            return true;
        }
        // A dock is refused where the row could not hold the tabs at
        // their least; the drag carries on and the tabs stay where they
        // are, rather than a row showing fewer tabs than it holds.
        if (!hasRoomFor(target, moved)) {
            return false;
        }
        int activeIndex = list.indexOf(active);
        int before = countBefore(moved, source, activeIndex);
        List<WindowSplit> carried = splitsWhollyIn(source, moved);
        list.removeAll(moved);
        if (list.isEmpty()) {
            dropWindow(source);
        } else if (active != null && moved.contains(active)) {
            source.setActiveTab(successor(list, activeIndex - before));
        }
        settleSplits(source);
        int to = Math.max(0, Math.min(target.tabs().size(), index));
        target.tabs().addAll(to, moved);
        target.splits().addAll(carried);
        target.setActiveTab(moved.get(moved.size() - 1));
        settleSplits(target);
        if (persist) {
            changed();
        }
        return true;
    }

    /** The splits of {@code window} both of whose pages are among {@code tabs}: they go where the tabs go. */
    private static List<WindowSplit> splitsWhollyIn(Window window, List<WindowPage> tabs) {
        List<WindowSplit> whole = new ArrayList<WindowSplit>();
        for (WindowSplit split : window.splits()) {
            if (tabs.contains(split.first()) && tabs.contains(split.second())) {
                whole.add(split);
            }
        }
        return whole;
    }

    /**
     * Keeps a window's splits whole: a split one of whose pages left the
     * window goes, a page stands in one split at most, and each split's
     * second page stands right after its first in the row.
     */
    private static void settleSplits(Window window) {
        Set<WindowPage> taken = new HashSet<WindowPage>();
        Iterator<WindowSplit> splits = window.splits().iterator();
        while (splits.hasNext()) {
            WindowSplit split = splits.next();
            if (!window.contains(split.first()) || !window.contains(split.second())
                    || taken.contains(split.first()) || taken.contains(split.second())) {
                splits.remove();
                continue;
            }
            taken.add(split.first());
            taken.add(split.second());
            List<WindowPage> tabs = window.tabs();
            tabs.remove(split.second());
            tabs.add(tabs.indexOf(split.first()) + 1, split.second());
        }
    }

    /* ---- Split view ---- */

    /**
     * Shows {@code second} beside {@code first} in first's window: the
     * second page moves right after the first, from wherever it stood, and
     * comes to the front, side by side and sharing the room evenly.
     * Refused while either window is locked, for a page already in a
     * split, and where first's window has no room for the second.
     */
    public static synchronized boolean split(WindowPage first, WindowPage second) {
        first = resolved(first);
        second = resolved(second);
        Window window = exactWindowOf(first);
        Window from = exactWindowOf(second);
        if (window == null || from == null || first.equals(second)
                || window.isLocked() || from.isLocked()
                || window.splitOf(first) != null || from.splitOf(second) != null) {
            return false;
        }
        if (from != window && !moveTabs(Collections.singletonList(second),
                window.getId(), window.tabs().indexOf(first) + 1, false)) {
            return false;
        }
        window.splits().add(new WindowSplit(first, second, false, 0.5D));
        settleSplits(window);
        window.setActiveTab(second);
        changed();
        return true;
    }

    /** The split {@code tab} stands in, and its window; refused while it is locked. */
    private static Window splitWindow(WindowPage tab, boolean evenLocked) {
        Window window = exactWindowOf(tab);
        return window == null || window.splitOf(tab) == null
                || (window.isLocked() && !evenLocked) ? null : window;
    }

    /** The two pages of {@code tab}'s split become two tabs again, side by side in the row. */
    public static synchronized boolean separate(WindowPage tab) {
        tab = resolved(tab);
        Window window = splitWindow(tab, false);
        if (window == null) {
            return false;
        }
        window.splits().remove(window.splitOf(tab));
        changed();
        return true;
    }

    /** Changes the two sides of {@code tab}'s split round. */
    public static synchronized boolean swapSides(WindowPage tab) {
        tab = resolved(tab);
        Window window = splitWindow(tab, false);
        if (window == null) {
            return false;
        }
        replaceSplit(window, window.splitOf(tab), window.splitOf(tab).swapped());
        changed();
        return true;
    }

    /** Turns {@code tab}'s split side by side, or one over the other. */
    public static synchronized boolean turnSplit(WindowPage tab, boolean stacked) {
        tab = resolved(tab);
        Window window = splitWindow(tab, false);
        if (window == null || window.splitOf(tab).isStacked() == stacked) {
            return false;
        }
        replaceSplit(window, window.splitOf(tab), window.splitOf(tab).turned(stacked));
        changed();
        return true;
    }

    /**
     * Shares the room of {@code tab}'s split at {@code share}, a locked
     * window's too, as the member list's edge moves in one.
     * {@code persist} is false while the divider is being dragged.
     */
    public static synchronized boolean shareSplit(WindowPage tab, double share,
                                                  boolean persist) {
        tab = resolved(tab);
        Window window = splitWindow(tab, true);
        if (window == null) {
            return false;
        }
        replaceSplit(window, window.splitOf(tab), window.splitOf(tab).sharedAt(share));
        if (persist) {
            changed();
        }
        return true;
    }

    private static void replaceSplit(Window window, WindowSplit before, WindowSplit after) {
        int index = window.splits().indexOf(before);
        window.splits().set(index, after);
        settleSplits(window);
    }

    /**
     * The given tabs in their window's own row order, or empty when any
     * of them is closed or they do not all live in one window. The order
     * is the window's, never the caller's, so a group keeps the order it
     * was shown in however it came to be selected.
     */
    private static List<WindowPage> sameWindowTabs(
            List<? extends WindowPage> tabs) {
        List<WindowPage> result = new ArrayList<WindowPage>();
        if (tabs == null || tabs.isEmpty()) {
            return result;
        }
        Window window = windowOf(tabs.get(0));
        if (window == null) {
            return result;
        }
        for (WindowPage tab : window.tabs()) {
            if (tabs.contains(tab)) {
                result.add(tab);
            }
        }
        return result.size() == new HashSet<WindowPage>(tabs).size()
                ? result : new ArrayList<WindowPage>();
    }

    /**
     * Tears tabs off their window by hand into a new window at the given
     * percent position, {@code width} by {@code height}: the size of the
     * window they came from. The player placed it, so it opens unlocked.
     * The tabs keep their relative order. A window's only tabs dragged out
     * just move that window. A locked window lets nothing go.
     */
    public static synchronized Window tearOff(List<? extends WindowPage> tabs,
                                              double offsetX, double offsetY,
                                              int width, double height) {
        List<WindowPage> moved = sameWindowTabs(tabs);
        if (moved.isEmpty()) {
            return null;
        }
        Window source = windowOf(moved.get(0));
        if (source.isLocked()) {
            return null;
        }
        if (source.tabs().size() == moved.size()) {
            // Everything the window held: the window itself moves,
            // rather than an empty one being left behind.
            source.setOffsets(clampWindowPercent(offsetX),
                    clampWindowPercent(offsetY));
            changed();
            return source;
        }
        Window window = takeOut(source, moved);
        window.setOwnWidth(clampWindowWidth(width));
        window.setOwnHeight(clampWindowHeight(height));
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
        changed();
        return window;
    }

    /**
     * Lifts {@code moved} out of {@code source} into a new window, the
     * last of them in front there, and hands the source's front to a
     * neighbour where one of them held it. Not written.
     */
    private static Window takeOut(Window source, List<WindowPage> moved) {
        WindowPage active = source.getActiveTab();
        int activeIndex = source.tabs().indexOf(active);
        int before = countBefore(moved, source, activeIndex);
        source.tabs().removeAll(moved);
        if (active != null && moved.contains(active)) {
            source.setActiveTab(successor(source.tabs(),
                    activeIndex - before));
        }
        List<WindowSplit> carried = splitsWhollyIn(source, moved);
        settleSplits(source);
        Window window = newWindow();
        window.setView(viewOf(source));
        window.tabs().addAll(moved);
        window.splits().addAll(carried);
        window.setActiveTab(moved.get(moved.size() - 1));
        settleSplits(window);
        WINDOWS.add(window);
        return window;
    }

    /** Brings a tab to the front of its own window; not a layout change. */
    public static synchronized boolean setActiveTab(WindowPage tab) {
        tab = resolved(tab);
        Window window = exactWindowOf(tab);
        if (window == null || tab.equals(window.getActiveTab())) {
            return false;
        }
        window.setActiveTab(tab);
        changed();
        return true;
    }

    /**
     * Takes one tab out of its window; a window left empty goes, and a
     * tab that was in front hands the front to its neighbour
     * ({@link #successor}).
     */
    private static void removeTab(Window window, WindowPage tab) {
        int index = window.tabs().indexOf(tab);
        window.tabs().remove(tab);
        if (window.tabs().isEmpty()) {
            dropWindow(window);
            return;
        }
        if (tab.equals(window.getActiveTab())) {
            window.setActiveTab(successor(window.tabs(), index));
        }
        settleSplits(window);
    }

    /**
     * How many of {@code moved} stand before {@code index} in the
     * window's row, read before they leave it.
     */
    private static int countBefore(List<WindowPage> moved, Window window,
                                   int index) {
        int count = 0;
        for (int at = 0; at < index && at < window.tabs().size(); at++) {
            if (moved.contains(window.tabs().get(at))) {
                count++;
            }
        }
        return count;
    }

    /* ---- The layout file ---- */

    /**
     * Rebuilds the windows from a loaded description, recovering from
     * anything stale: unknown tabs and duplicate windows are ignored, a
     * tab listed twice keeps its first place, a tab its kind does not
     * keep in the layout is left out, empty windows are dropped, and
     * percents are clamped. Whatever the systems
     * then add for tabs the file placed nowhere is theirs to do. The
     * listener is not notified; the caller decides whether a repaired
     * layout is written back.
     */
    static synchronized void load(List<WindowSpec> specs) {
        WINDOWS.clear();
        STACK.clear();
        Set<WindowPage> placed = new HashSet<WindowPage>();
        int highestNumber = 0;
        if (specs != null) {
            for (WindowSpec spec : specs) {
                if (spec != null) {
                    highestNumber = Math.max(highestNumber,
                            windowNumber(spec.id));
                }
            }
        }
        nextWindowNumber = highestNumber + 1;
        if (specs != null) {
            for (WindowSpec spec : specs) {
                if (spec == null) {
                    continue;
                }
                String id = spec.id;
                if (!isWindowId(id) || window(id) != null) {
                    continue;
                }
                Window window = new Window(id);
                window.setView(spec.view);
                for (WindowPage tab : spec.tabs) {
                    if (tab != null && tab.isKeptInLayout()
                            && placed.add(tab)) {
                        window.tabs().add(tab);
                    }
                }
                if (window.tabs().isEmpty()) {
                    continue;
                }
                window.setOffsets(clampWindowPercent(spec.offsetX),
                        clampWindowPercent(spec.offsetY));
                window.setLocked(spec.locked);
                // Only a locked window is closed and kept.
                window.setClosed(spec.locked && spec.closed);
                window.setPinnedToHud(spec.pinnedToHud);
                window.setPinnedToGui(spec.pinnedToGui);
                window.setFill(spec.fill);
                window.setOwnHeight(clampWindowHeight(spec.height));
                window.setOwnWidth(clampWindowWidth(spec.width));
                window.setActiveTab(spec.activeTab);
                window.splits().addAll(spec.splits);
                settleSplits(window);
                WINDOWS.add(window);
            }
        }
    }

    /**
     * A serialisable description of the current layout. A tab its kind
     * does not keep in the layout is left out — a conversation ends with
     * the session — and a window holding nothing else is not described.
     */
    static synchronized List<WindowSpec> describe() {
        List<WindowSpec> result = new ArrayList<WindowSpec>(WINDOWS.size());
        for (Window window : WINDOWS) {
            List<WindowPage> tabs = new ArrayList<WindowPage>();
            for (WindowPage tab : window.tabs()) {
                if (tab.isKeptInLayout()) {
                    tabs.add(tab);
                }
            }
            if (tabs.isEmpty()) {
                continue;
            }
            WindowPage active = window.getActiveTab();
            List<WindowSplit> splits = new ArrayList<WindowSplit>();
            for (WindowSplit split : window.splits()) {
                if (tabs.contains(split.first()) && tabs.contains(split.second())) {
                    splits.add(split);
                }
            }
            result.add(new WindowSpec(window.getId(), tabs,
                    active != null && active.isKeptInLayout() ? active : null,
                    window.isLocked(), window.isClosed(), window.isPinnedToHud(),
                    window.isPinnedToGui(),
                    // Written only where its pages do not say it already.
                    viewOf(window) == derivedView(window) ? null : viewOf(window),
                    window.getOffsetX(),
                    window.getOffsetY(), window.getOwnHeight(),
                    window.getOwnWidth(), window.getFill(), splits));
        }
        return result;
    }

    /** Plain description of one window, used by load and describe. */
    public static final class WindowSpec {
        final String id;
        final List<WindowPage> tabs;
        final WindowPage activeTab;
        final boolean locked;
        /** Closed while locked: out of sight until its view opens. */
        final boolean closed;
        final boolean pinnedToHud;
        final boolean pinnedToGui;
        /** The view it stands in; null to take its pages' category's. */
        final PageCategory view;
        final double offsetX;
        final double offsetY;
        /** The window's own height in GUI pixels; 0 stands at the default place. */
        final double height;
        /** The window's own width in GUI pixels; 0 stands at the default place. */
        final int width;
        /** The part of the screen the window fills; none in its own box. */
        final Window.ScreenFill fill;
        /** The splits that show two of its pages together. */
        final List<WindowSplit> splits;

        public WindowSpec(String id, List<? extends WindowPage> tabs,
                          WindowPage activeTab, boolean locked, boolean closed,
                          boolean pinnedToHud, boolean pinnedToGui,
                          PageCategory view, double offsetX, double offsetY,
                          double height, int width, Window.ScreenFill fill,
                          List<WindowSplit> splits) {
            this.id = id;
            List<WindowPage> kept = new ArrayList<WindowPage>();
            if (tabs != null) {
                for (WindowPage tab : tabs) {
                    if (tab != null) {
                        kept.add(tab);
                    }
                }
            }
            this.tabs = kept;
            this.activeTab = activeTab;
            this.locked = locked;
            this.closed = closed;
            this.pinnedToHud = pinnedToHud;
            this.pinnedToGui = pinnedToGui;
            this.view = view;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.height = clampWindowHeight(height);
            this.width = clampWindowWidth(width);
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
            this.splits = splits == null ? new ArrayList<WindowSplit>()
                    : new ArrayList<WindowSplit>(splits);
        }
    }

    /**
     * Adds a closed tab as a window's last and puts it in front. Refused
     * for a missing or locked window and a tab already open.
     */
    public static synchronized boolean addTab(String windowId, WindowPage tab) {
        Window window = window(windowId);
        if (tab == null || window == null || window.isLocked()
                || holds(tab)) {
            return false;
        }
        window.tabs().add(tab);
        window.setActiveTab(tab);
        settleSplits(window);
        changed();
        return true;
    }

    /**
     * Adds tabs to the end of a window's row, locked or not, leaving its
     * front tab alone: how a layout being put back as it was gets the tabs
     * its file placed nowhere, and the whispers a window held on this
     * server. Not written; the caller decides.
     */
    public static synchronized void appendTabs(Window window,
                                               List<? extends WindowPage> tabs) {
        if (window == null || tabs == null || !WINDOWS.contains(window)) {
            return;
        }
        for (WindowPage tab : tabs) {
            if (tab != null && !holds(tab)) {
                window.tabs().add(tab);
            }
        }
    }

    /**
     * Gives a window its own width, in GUI pixels. {@code persist} is
     * false while a resize is in progress so the file is written once, on
     * release.
     */
    public static synchronized boolean setWindowWidth(String windowId,
                                                      int width,
                                                      boolean persist) {
        Window window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setOwnWidth(clampWindowWidth(width));
        if (persist) {
            changed();
        }
        return true;
    }

    /** A width inside the bounds a layout may hold; 0 stays 0. */
    static int clampWindowWidth(int width) {
        if (width <= 0) {
            return 0;
        }
        return Math.max(MIN_WINDOW_SIZE, Math.min(MAX_WINDOW_SIZE, width));
    }

    /** Windows in order, empty when every one has been closed. */
    public static synchronized List<Window> windows() {
        return WINDOWS_VIEW;
    }

    /**
     * Windows back to front: the ones never raised first, in layout
     * order, then the raised ones, the most recent last. Draw in this
     * order; hit test in reverse.
     */
    public static synchronized List<Window> stacked() {
        List<Window> result = new ArrayList<Window>(WINDOWS.size());
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (!STACK.contains(WINDOWS.get(index).getId())) {
                result.add(WINDOWS.get(index));
            }
        }
        for (int index = 0; index < STACK.size(); index++) {
            Window window = window(STACK.get(index));
            if (window != null) {
                result.add(window);
            }
        }
        return result;
    }

    /**
     * Sends a window behind every other one, the reverse of
     * {@link #raise}. The whole order is written down first: windows
     * that were never raised sit at the back in layout order, so a
     * window pushed under them has to be listed with them to really be
     * behind them.
     */
    public static synchronized void lower(String windowId) {
        if (window(windowId) == null) {
            return;
        }
        List<Window> order = stacked();
        STACK.clear();
        for (int index = 0; index < order.size(); index++) {
            String id = order.get(index).getId();
            if (!id.equals(windowId)) {
                STACK.add(id);
            }
        }
        STACK.add(0, windowId);
    }

    /** Brings a window to the front of the stack; not a layout change. */
    public static synchronized void raise(String windowId) {
        Window raised = window(windowId);
        if (raised == null) {
            return;
        }
        // A window asked for comes back if it was closed while locked.
        raised.setClosed(false);
        STACK.remove(windowId);
        STACK.add(windowId);
        // Ids of windows that have since gone are dropped here.
        Iterator<String> iterator = STACK.iterator();
        while (iterator.hasNext()) {
            if (window(iterator.next()) == null) {
                iterator.remove();
            }
        }
    }

    /** Whether no window is left; a valid state, not an error. */
    public static synchronized boolean isEmpty() {
        return WINDOWS.isEmpty();
    }

    /** The first window, or null once every one has been closed. */
    public static synchronized Window firstWindow() {
        return isEmpty() ? null : WINDOWS.get(0);
    }

    public static synchronized Window window(String id) {
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (WINDOWS.get(index).getId().equals(id)) {
                return WINDOWS.get(index);
            }
        }
        return null;
    }

    /**
     * The window drawn in front of the others that the view shows: a new
     * window steps on from it. A window the view hides is not seen, so
     * one that would step on from it opens at the default place instead.
     * Null with none.
     */
    private static Window frontWindow() {
        List<Window> order = stacked();
        for (int index = order.size() - 1; index >= 0; index--) {
            for (WindowPage tab : order.get(index).tabs()) {
                if (WindowView.shows(tab)) {
                    return order.get(index);
                }
            }
        }
        return null;
    }

    /**
     * Windows most recently brought to the front first, then the ones
     * never raised in layout order: the order a tab that opens by
     * itself asks them in.
     */
    private static List<Window> byRecency() {
        List<Window> result = new ArrayList<Window>(WINDOWS.size());
        for (int index = STACK.size() - 1; index >= 0; index--) {
            Window window = window(STACK.get(index));
            if (window != null) {
                result.add(window);
            }
        }
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (!STACK.contains(WINDOWS.get(index).getId())) {
                result.add(WINDOWS.get(index));
            }
        }
        return result;
    }

    /**
     * Locks a window by hand, or unlocks it. Locking makes where the window
     * stands its category's first place: that category's first window
     * opens there from now on, as a sub-window's padlock keeps its kind's
     * place. A world page's window keeps no place.
     */
    public static synchronized boolean setLocked(String windowId,
                                                 boolean locked) {
        Window window = window(windowId);
        if (window == null || window.isLocked() == locked) {
            return false;
        }
        window.setLocked(locked);
        if (locked && staysPut(window.getActiveTab())) {
            CATEGORY_PLACES.put(viewOf(window), Place.of(window));
        }
        changed();
        return true;
    }

    /**
     * Puts a window's layout back as its category's first window first
     * stood: at the default place, in the middle of the screen at two
     * thirds of it, filling what that first window fills (the map the
     * whole screen), pinned nowhere, its splits parted, and locked again.
     * The category forgets the place a padlock gave it. Its pages stay;
     * what each lays out is put back by the page
     * ({@link WindowPage#resetView}), and the settings keep their own reset.
     * A locked window stays where it is, so only an unlocked one resets.
     */
    public static synchronized boolean resetWindow(String windowId) {
        Window window = window(windowId);
        if (window == null || window.isLocked()) {
            return false;
        }
        CATEGORY_PLACES.remove(viewOf(window));
        window.setOwnWidth(0);
        window.setOwnHeight(0.0D);
        window.setFill(firstFillOf(window));
        window.setPinnedToHud(false);
        window.setPinnedToGui(false);
        window.splits().clear();
        window.setLocked(true);
        changed();
        return true;
    }

    /**
     * A window takes a part of the screen, or gives it back with
     * {@link Window.ScreenFill#NONE}, and comes forward.
     */
    public static void takeFill(Window window, Window.ScreenFill fill) {
        if (window == null) {
            return;
        }
        raise(window.getId());
        setFill(window.getId(), fill, true);
    }

    /**
     * Lets a window fill a part of the screen — the whole of it, a half
     * or a quarter — or gives it back its own box: its position, width
     * and height are kept as they were throughout, and are what it
     * returns to. {@code persist} is false while a drag that took a
     * window out of the screen is still moving, so the file is written
     * once, on release.
     */
    public static synchronized boolean setFill(String windowId,
                                               Window.ScreenFill fill,
                                               boolean persist) {
        Window window = window(windowId);
        Window.ScreenFill wanted = fill == null
                ? Window.ScreenFill.NONE : fill;
        if (window == null || window.getFill().equals(wanted)) {
            return false;
        }
        window.setFill(wanted);
        if (persist) {
            changed();
        }
        return true;
    }

    /**
     * Positions a window. {@code persist} is false while a drag is in
     * progress so the file is written once, on release.
     */
    public static synchronized boolean setPosition(String windowId,
                                                   double offsetX,
                                                   double offsetY,
                                                   boolean persist) {
        Window window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
        if (persist) {
            changed();
        }
        return true;
    }

    /**
     * Gives a window its own height in GUI pixels. The height is
     * fractional: a window keeps the exact size it was dragged to.
     * {@code persist} is false while a resize is in progress so the file
     * is written once, on release.
     */
    public static synchronized boolean setWindowHeight(String windowId,
                                                       double height,
                                                       boolean persist) {
        Window window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setOwnHeight(clampWindowHeight(height));
        if (persist) {
            changed();
        }
        return true;
    }

    /** A window height inside the bounds a layout may hold; 0 stays 0. */
    static double clampWindowHeight(double height) {
        if (!(height > 0.0D)) {
            return 0.0D;
        }
        return Math.max(MIN_WINDOW_SIZE, Math.min(MAX_WINDOW_SIZE, height));
    }

    /** Writes the current state through the listener, if any. */
    public static synchronized void persist() {
        changed();
    }

    /**
     * A new window, at the default place and unlocked: in the middle of
     * the screen at two thirds of it until it is given a place. Only a
     * category's first window is locked ({@link #addWindow}).
     */
    private static Window newWindow() {
        Window window = new Window(ID_PREFIX + nextWindowNumber++);
        window.setOffsets(50.0D, 50.0D);
        return window;
    }

    /**
     * Puts a new window one step right and down from {@code reference}, at
     * that window's size, the way desktop windows stack: the window behind
     * keeps its tab row in view. A reference filling a part of the screen
     * is measured by the box it goes back to. With no reference the new
     * window stays at the default place. Measured in the boxes the windows
     * are drawn in when there is a client, else on a screen of a fixed
     * size: the rule is the same, only the pixels differ.
     */
    private static void cascadeFrom(Window created, Window reference) {
        if (reference == null) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = clientMinecraft();
        int screenWidth = HEADLESS_SCREEN_WIDTH;
        int screenHeight = HEADLESS_SCREEN_HEIGHT;
        if (minecraft != null) {
            try {
                net.minecraft.client.gui.ScaledResolution resolution =
                        new net.minecraft.client.gui.ScaledResolution(minecraft,
                                minecraft.displayWidth, minecraft.displayHeight);
                screenWidth = resolution.getScaledWidth();
                screenHeight = resolution.getScaledHeight();
            } catch (RuntimeException unavailable) {
                minecraft = null;
            }
        }
        WindowPlacement.Box from = WindowPlacement.restingBounds(
                reference, minecraft, screenWidth, screenHeight);
        created.setOwnWidth(clampWindowWidth(from.width));
        created.setOwnHeight(clampWindowHeight(from.height));
        int width = WindowPlacement.windowWidth(created, minecraft);
        double height = WindowPlacement.currentHeight(created, minecraft);
        WindowCascade.Corner corner = WindowCascade.place(
                from.x, from.y, width, height, screenWidth, screenHeight,
                WindowPlacement.EDGE_MARGIN, WindowCascade.STEP);
        double baseline = WindowPlacement.baselineForRowTop(
                created, minecraft, corner.y);
        created.setOffsets(
                clampWindowPercent(WindowPlacement.windowPercentX(
                        created, corner.x, minecraft, screenWidth)),
                clampWindowPercent(WindowPlacement.windowPercentY(
                        created, baseline, minecraft, screenHeight)));
    }

    /** The running client, or null off the client or before it exists. */
    private static net.minecraft.client.Minecraft clientMinecraft() {
        try {
            return net.minecraft.client.Minecraft.getMinecraft();
        } catch (RuntimeException unavailable) {
            return null;
        } catch (LinkageError unavailable) {
            return null;
        }
    }

    /** Takes an emptied window out of the layout. */
    private static void dropWindow(Window window) {
        WINDOWS.remove(window);
    }

    private static void changed() {
        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }

    static boolean isWindowId(String id) {
        return windowNumber(id) > 0;
    }

    private static int windowNumber(String id) {
        if (id == null || !id.startsWith(ID_PREFIX)
                || id.length() <= ID_PREFIX.length()
                || id.length() > ID_PREFIX.length() + 6) {
            return -1;
        }
        try {
            int number = Integer.parseInt(id.substring(ID_PREFIX.length()));
            return number > 0 ? number : -1;
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * A window's percent: between the margins, 0 to 100, and past them
     * by up to the window's own size either way, which is how far a
     * window may hang off the screen
     * ({@link WindowPlacement#position}). A safety bound; where a
     * window really stops is the screen's hold on it.
     */
    static double clampWindowPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(-100.0D, Math.min(200.0D, value));
    }

}
