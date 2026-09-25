package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one authoritative window layout: which windows exist, which tabs
 * each holds in what order, which tab is in front, whether a window is
 * locked, where each window sits and how big it is, which window stands
 * in front of which, and where a page's window last stood. A tab lives
 * in at most one window. Every window is equal: one that loses its last
 * tab disappears, and the layout with no windows left at all is a valid
 * state. A window dropped against another's edge <em>links</em> to it
 * and from then on keeps that gap as the other grows, shrinks or moves;
 * a link is one window's, and dragging the linked window away breaks
 * it.
 *
 * <p>What the tabs stand for is their systems' business: the chat keeps
 * its channels' preferences and the windows a new player starts with in
 * {@code ChatLayout}. Every mutation is reported to the registered
 * listener, which the file-backed store uses to persist the layout;
 * position changes made while dragging are reported only when the
 * caller asks for it.</p>
 */
public final class WindowLayout {
    /** Bound on windows; more than this is a broken file, not a layout. */
    public static final int MAX_WINDOWS = 8;
    /**
     * Absolute floor on a stored width or height, in GUI pixels, so a
     * hand-edited file cannot leave a window nothing fits in. What a drag
     * may actually reach is {@code WindowPlacement}'s least box.
     */
    public static final int MIN_WINDOW_SIZE = 40;
    /** Widest and tallest a stored size may be; no screen is anywhere near this. */
    public static final int MAX_WINDOW_SIZE = 4096;
    /**
     * The screen a window is cascaded on when there is no client to
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
    /** By a page tab's id, where its window last stood. */
    private static final Map<String, Place> PLACES =
            new LinkedHashMap<String, Place>();
    private static int nextWindowNumber = 1;
    private static Runnable changeListener;
    /** What lays the windows out for a new player; see {@link #reset}. */
    private static Runnable defaults;

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
     * the chat's console window and conversation window). Remembered
     * places go too.
     */
    public static synchronized void reset() {
        PLACES.clear();
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
     * Adds a window holding {@code tabs}, {@code active} in front, at a
     * place in percent of the screen's travel: how a system lays out its
     * windows for a new player, or gives tabs a loaded layout placed
     * nowhere a home. Null past the cap or with no tabs.
     */
    public static synchronized Window addWindow(List<? extends WindowTab> tabs,
                                                WindowTab active,
                                                double offsetX,
                                                double offsetY) {
        if (tabs == null || tabs.isEmpty() || WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        Window window = newWindow();
        window.tabs().addAll(tabs);
        window.setActiveTab(active);
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
        WINDOWS.add(window);
        return window;
    }

    /* ---- Pages ---- */

    /** The height a page's window first opens at, in GUI pixels: a page wants more room than a conversation. */
    public static final double PAGE_HEIGHT = 292.0D;
    /** The width a page's window first opens at, in GUI pixels. */
    public static final int PAGE_WIDTH = 366;

    /** Where a page's window stood as its tab last left it. */
    static final class Place {
        final double x;
        final double y;
        final double height;
        final int width;
        /** The part of the screen the window filled; its own box for none. */
        final Window.ScreenFill fill;

        Place(double x, double y, double height, int width,
              Window.ScreenFill fill) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
        }
    }

    /**
     * Brings a page forward: in the window holding its tab, the tab put in
     * front there and the window raised; else in a window of its own,
     * where the page's window last stood, or cascaded from the front
     * window at a page's size. Null when no window can open for it: every
     * window the layout may have is out.
     */
    public static synchronized Window showPage(PageTab page) {
        if (page == null) {
            return null;
        }
        Window holding = windowOf(page);
        if (holding != null) {
            holding.setActiveTab(page);
            raise(holding.getId());
            changed();
            return holding;
        }
        if (WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        Window created = newWindow();
        Place place = PLACES.get(page.id());
        if (place != null) {
            created.setOffsets(place.x, place.y);
            created.setOwnHeight(clampWindowHeight(place.height));
            created.setOwnWidth(clampWindowWidth(place.width));
            created.setFill(place.fill);
        } else {
            // A page's size is what the window goes back to from a first
            // fill, as from any other.
            cascadeFrom(created, frontWindow());
            created.setOwnHeight(PAGE_HEIGHT);
            created.setOwnWidth(PAGE_WIDTH);
            created.setFill(page.page().firstFill());
        }
        created.tabs().add(page);
        created.setActiveTab(page);
        WINDOWS.add(created);
        raise(created.getId());
        changed();
        return created;
    }

    /** Whether the window has a page in front. */
    public static synchronized boolean showsPage(Window window) {
        return window != null && window.getActiveTab() instanceof PageTab;
    }

    /** Notes where the window stands for every page tab in {@code tabs} that is leaving it. */
    private static void rememberPlaces(Window window,
                                       List<? extends WindowTab> tabs) {
        for (WindowTab tab : tabs) {
            if (tab instanceof PageTab) {
                PLACES.put(tab.id(), new Place(
                        window.getOffsetX(), window.getOffsetY(),
                        window.getOwnHeight(), window.getOwnWidth(),
                        window.getFill()));
            }
        }
    }

    /** Where each page's window last stood, by its tab's id, for the layout file. */
    static synchronized Map<String, Place> places() {
        return new LinkedHashMap<String, Place>(PLACES);
    }

    /** What the layout file said of the pages' places. */
    static synchronized void loadPlaces(Map<String, Place> places) {
        PLACES.clear();
        if (places != null) {
            PLACES.putAll(places);
        }
    }

    /* ---- Tabs ---- */

    /** The window holding the tab, or null when it is closed. */
    public static synchronized Window windowOf(WindowTab tab) {
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

    public static synchronized boolean isOpen(WindowTab tab) {
        return windowOf(tab) != null;
    }

    /** Every open tab in window order, each window's tabs in row order. */
    public static synchronized List<WindowTab> order() {
        List<WindowTab> result = new ArrayList<WindowTab>();
        for (int index = 0; index < WINDOWS.size(); index++) {
            result.addAll(WINDOWS.get(index).tabs());
        }
        return result;
    }

    /**
     * Whether {@link #close} would remove the tab: it is open and its
     * window is unlocked. A locked window keeps the tabs it has — that
     * is what locking it is for — so no cross is offered on its row and
     * no shortcut closes one either. Nothing else is refused: the last
     * tab of the last window closes like any other.
     */
    public static synchronized boolean isClosable(WindowTab tab) {
        Window window = windowOf(tab);
        return window != null && !window.isLocked();
    }

    /**
     * Removes the tab from its window; a window emptied this way is
     * dropped, and the layout may end up with no windows at all.
     */
    public static synchronized boolean close(WindowTab tab) {
        if (!isClosable(tab)) {
            return false;
        }
        removeTab(windowOf(tab), tab);
        changed();
        return true;
    }

    /**
     * Closes a whole window: every tab it holds leaves it and the window
     * itself goes, windows stuck to it letting go. What stands behind the
     * tabs is untouched, and closing the last window is allowed. A
     * locked window is refused, as its individual tabs are.
     */
    public static synchronized boolean closeWindow(String windowId) {
        Window window = window(windowId);
        if (window == null || window.isLocked()) {
            return false;
        }
        rememberPlaces(window, window.tabs());
        window.tabs().clear();
        window.setActiveTab(null);
        dropWindow(window);
        changed();
        return true;
    }

    /** Picks tabs out of the layout; see {@link #removeTabs}. */
    public interface TabFilter {
        boolean matches(WindowTab tab);
    }

    /**
     * Takes every tab the filter picks out of its window, a window left
     * empty going with it, and answers the tabs taken. The listener is
     * not told; the caller writes the change when it is done.
     */
    public static synchronized List<WindowTab> removeTabs(TabFilter filter) {
        List<WindowTab> removed = new ArrayList<WindowTab>();
        Iterator<Window> iterator = WINDOWS.iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            Iterator<WindowTab> tabs = window.tabs().iterator();
            while (tabs.hasNext()) {
                WindowTab tab = tabs.next();
                if (filter.matches(tab)) {
                    tabs.remove();
                    removed.add(tab);
                }
            }
            if (window.tabs().isEmpty()) {
                iterator.remove();
                for (Window other : WINDOWS) {
                    if (window.getId().equals(other.getLinkTarget())) {
                        other.setLink(null, false);
                    }
                }
            }
            if (window.getActiveTab() == null
                    || !window.tabs().contains(window.getActiveTab())) {
                window.setActiveTab(null);
            }
        }
        return removed;
    }

    /**
     * The window a tab that opens by itself — a conversation, the
     * console answering a command — belongs in. The window it is asked
     * for takes it when that window is unlocked and has room for it;
     * otherwise the most recently used window that does, front of the
     * stack first; then, while the layout has room for another, a new
     * window cascaded from the one asked for, or from the front window.
     * With no room for another, the front-most unlocked window takes it
     * anyway, since losing the tab would be worse than crowding a row,
     * and a layout of locked windows alone hands it to the first.
     *
     * <p>Null once every window has been closed: a tab never opens the
     * windows back up by itself. The player decides when a window comes
     * back.</p>
     */
    public static synchronized Window receivingWindow(Window preferred,
                                                      WindowTab tab) {
        if (isEmpty()) {
            return null;
        }
        List<WindowTab> candidate = Collections.singletonList(tab);
        if (preferred != null && WINDOWS.contains(preferred)
                && !preferred.isLocked() && hasRoomFor(preferred, candidate)) {
            return preferred;
        }
        // A window whose row cannot hold one more tab at its least — the
        // widest tab whole, every other down to its icon — is full: the
        // window last brought to the front with room takes the tab, and
        // when every unlocked window is full a new window opens instead.
        List<Window> byRecency = byRecency();
        for (int index = 0; index < byRecency.size(); index++) {
            Window window = byRecency.get(index);
            if (!window.isLocked() && hasRoomFor(window, candidate)) {
                return window;
            }
        }
        if (WINDOWS.size() < MAX_WINDOWS) {
            Window created = newWindow();
            cascadeFrom(created, preferred != null ? preferred : frontWindow());
            WINDOWS.add(created);
            // A window that has just opened stands in front of the rest.
            raise(created.getId());
            return created;
        }
        for (int index = 0; index < byRecency.size(); index++) {
            if (!byRecency.get(index).isLocked()) {
                return byRecency.get(index);
            }
        }
        return firstWindow();
    }

    /**
     * Whether the window's row has room for these tabs besides the ones
     * it shows. The width model is the tab row's; without a renderer to
     * measure with — headless tests, a broken frame — the answer is yes.
     */
    private static boolean hasRoomFor(Window window,
                                      List<? extends WindowTab> tabs) {
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
     * Opens a tab where a tab that opens by itself belongs
     * ({@link #receivingWindow}), leaving the window's front tab alone,
     * and answers the tab as the layout holds it: the one already open,
     * if it is. Null when there is no window left to open it in.
     */
    public static synchronized WindowTab openTab(WindowTab tab,
                                                 String preferredWindowId) {
        if (tab == null) {
            return null;
        }
        Window existing = windowOf(tab);
        if (existing != null) {
            // The tab as first opened, with the name's original casing.
            return existing.getTabs().get(existing.getTabs().indexOf(tab));
        }
        // The window asked for takes the tab first; a locked or a full
        // one hands it to another with room, or to a new one cascaded
        // from it.
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
     * Opens a tab in a window of its own, cascaded from the front
     * window: how a tab comes back when no window is left to put it
     * in, and what the screen's empty state offers. Refused for a tab
     * that is already open and once {@link #MAX_WINDOWS} exist.
     */
    public static synchronized WindowTab openInNewWindow(WindowTab tab) {
        if (tab == null || isOpen(tab) || WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        Window created = newWindow();
        cascadeFrom(created, frontWindow());
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
     * that may be its own — a reorder — or another one — a dock. A
     * locked source or target refuses. A source emptied by the move
     * disappears.
     */
    public static synchronized boolean moveTab(WindowTab tab,
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
     * and the same source window; a locked source or target refuses, so
     * does a target whose row has no room for the tabs at their least,
     * and a source emptied by the move disappears.
     */
    public static synchronized boolean moveTabs(List<? extends WindowTab> tabs,
                                                String targetWindowId,
                                                int index) {
        return moveTabs(tabs, targetWindowId, index, true);
    }

    /**
     * As above; {@code persist} is false while a drag is in progress, so
     * a tab sliding along its row writes the file once, on release,
     * rather than every time it passes a neighbour.
     */
    public static synchronized boolean moveTabs(List<? extends WindowTab> tabs,
                                                String targetWindowId,
                                                int index, boolean persist) {
        List<WindowTab> moved = sameWindowTabs(tabs);
        Window target = window(targetWindowId);
        if (moved.isEmpty() || target == null || target.isLocked()) {
            return false;
        }
        Window source = windowOf(moved.get(0));
        if (source.isLocked()) {
            return false;
        }
        WindowTab active = source.getActiveTab();
        List<WindowTab> list = source.tabs();
        if (source == target) {
            List<WindowTab> reordered = new ArrayList<WindowTab>(list);
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
        list.removeAll(moved);
        if (list.isEmpty()) {
            dropWindow(source);
        } else if (active != null && moved.contains(active)) {
            source.setActiveTab(null);
        }
        int to = Math.max(0, Math.min(target.tabs().size(), index));
        target.tabs().addAll(to, moved);
        target.setActiveTab(moved.get(moved.size() - 1));
        if (persist) {
            changed();
        }
        return true;
    }

    /**
     * The given tabs in their window's own row order, or empty when any
     * of them is closed or they do not all live in one window. The order
     * is the window's, never the caller's, so a group keeps the order it
     * was shown in however it came to be selected.
     */
    private static List<WindowTab> sameWindowTabs(
            List<? extends WindowTab> tabs) {
        List<WindowTab> result = new ArrayList<WindowTab>();
        if (tabs == null || tabs.isEmpty()) {
            return result;
        }
        Window window = windowOf(tabs.get(0));
        if (window == null) {
            return result;
        }
        for (WindowTab tab : window.tabs()) {
            if (tabs.contains(tab)) {
                result.add(tab);
            }
        }
        return result.size() == new HashSet<WindowTab>(tabs).size()
                ? result : new ArrayList<WindowTab>();
    }

    /**
     * Takes the tab out of its window into a new window at the given
     * percent position, as tall and as wide as the window it came from;
     * a page's own tab takes the size its window last had, or a page's.
     * A window's only tab dragged out just moves that window. Refused
     * for a locked source and once {@link #MAX_WINDOWS} exist.
     */
    public static synchronized Window detach(WindowTab tab, double offsetX,
                                             double offsetY) {
        return detach(Collections.singletonList(tab), offsetX, offsetY);
    }

    /** As above for a group of tabs, which keep their relative order. */
    public static synchronized Window detach(List<? extends WindowTab> tabs,
                                             double offsetX,
                                             double offsetY) {
        List<WindowTab> moved = sameWindowTabs(tabs);
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
        if (WINDOWS.size() >= MAX_WINDOWS) {
            return null;
        }
        WindowTab active = source.getActiveTab();
        source.tabs().removeAll(moved);
        if (active != null && moved.contains(active)) {
            source.setActiveTab(null);
        }
        Window window = newWindow();
        Place place = moved.size() == 1 && moved.get(0) instanceof PageTab
                ? PLACES.get(moved.get(0).id()) : null;
        if (place != null) {
            window.setOwnHeight(clampWindowHeight(place.height));
            window.setOwnWidth(clampWindowWidth(place.width));
        } else if (moved.size() == 1 && moved.get(0) instanceof PageTab) {
            window.setOwnHeight(PAGE_HEIGHT);
            window.setOwnWidth(PAGE_WIDTH);
        } else {
            // The size the player gave the window the tabs came out of,
            // so a tab taken out of a tall window is read in a window as
            // tall, rather than in whatever the game's settings say.
            window.setOwnHeight(source.getOwnHeight());
            window.setOwnWidth(source.getOwnWidth());
        }
        window.tabs().addAll(moved);
        window.setActiveTab(moved.get(moved.size() - 1));
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
        WINDOWS.add(window);
        changed();
        return window;
    }

    /** Brings a tab to the front of its own window; not a layout change. */
    public static synchronized boolean setActiveTab(WindowTab tab) {
        Window window = windowOf(tab);
        if (window == null || tab.equals(window.getActiveTab())) {
            return false;
        }
        window.setActiveTab(tab);
        changed();
        return true;
    }

    private static void removeTab(Window window, WindowTab tab) {
        rememberPlaces(window, Collections.singletonList(tab));
        window.tabs().remove(tab);
        if (window.tabs().isEmpty()) {
            dropWindow(window);
            return;
        }
        if (tab.equals(window.getActiveTab())) {
            window.setActiveTab(null);
        }
    }

    /* ---- The layout file ---- */

    /**
     * Rebuilds the windows from a loaded description, recovering from
     * anything stale: unknown tabs and duplicate windows are ignored, a
     * tab listed twice keeps its first place, a tab its kind does not
     * keep in the layout is left out, empty windows and windows past the
     * cap are dropped, and percents are clamped. Whatever the systems
     * then add for tabs the file placed nowhere is theirs to do. The
     * listener is not notified; the caller decides whether a repaired
     * layout is written back.
     */
    static synchronized void load(List<WindowSpec> specs) {
        WINDOWS.clear();
        STACK.clear();
        Set<WindowTab> placed = new HashSet<WindowTab>();
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
                if (spec == null || WINDOWS.size() >= MAX_WINDOWS) {
                    continue;
                }
                String id = spec.id;
                if (!isWindowId(id) || window(id) != null) {
                    continue;
                }
                Window window = new Window(id);
                for (WindowTab tab : spec.tabs) {
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
                window.setFill(spec.fill);
                window.setOwnHeight(clampWindowHeight(spec.height));
                window.setOwnWidth(clampWindowWidth(spec.width));
                window.setActiveTab(spec.activeTab);
                WINDOWS.add(window);
                if (spec.linkTarget != null) {
                    window.setLink(spec.linkTarget, spec.linkSide);
                }
            }
        }
        // A link needs its target; two windows never hold each other.
        for (Window window : WINDOWS) {
            Window target = window.isLinked()
                    ? window(window.getLinkTarget()) : null;
            if (target == null || target == window) {
                window.setLink(null, Window.LinkSide.BELOW);
            } else if (window.getId().equals(target.getLinkTarget())) {
                target.setLink(null, Window.LinkSide.BELOW);
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
            List<WindowTab> tabs = new ArrayList<WindowTab>();
            for (WindowTab tab : window.tabs()) {
                if (tab.isKeptInLayout()) {
                    tabs.add(tab);
                }
            }
            if (tabs.isEmpty()) {
                continue;
            }
            WindowTab active = window.getActiveTab();
            result.add(new WindowSpec(window.getId(), tabs,
                    active != null && active.isKeptInLayout() ? active : null,
                    window.isLocked(), window.getOffsetX(),
                    window.getOffsetY(), window.getLinkTarget(),
                    window.getLinkSide(), window.getOwnHeight(),
                    window.getOwnWidth(), window.getFill()));
        }
        return result;
    }

    /** Plain description of one window, used by load and describe. */
    public static final class WindowSpec {
        final String id;
        final List<WindowTab> tabs;
        final WindowTab activeTab;
        final boolean locked;
        final double offsetX;
        final double offsetY;
        final String linkTarget;
        /** Which side of its target it is stuck to. */
        final Window.LinkSide linkSide;
        /** The window's own height in GUI pixels; 0 follows the game's settings. */
        final double height;
        /** The window's own width in GUI pixels; 0 follows the game's settings. */
        final int width;
        /** The part of the screen the window fills; none in its own box. */
        final Window.ScreenFill fill;

        public WindowSpec(String id, List<? extends WindowTab> tabs,
                          WindowTab activeTab, boolean locked,
                          double offsetX, double offsetY) {
            this(id, tabs, activeTab, locked, offsetX, offsetY, null,
                    Window.LinkSide.BELOW, 0.0D, 0, Window.ScreenFill.NONE);
        }

        public WindowSpec(String id, List<? extends WindowTab> tabs,
                          WindowTab activeTab, boolean locked,
                          double offsetX, double offsetY, String linkTarget,
                          Window.LinkSide linkSide, double height,
                          int width, Window.ScreenFill fill) {
            this.id = id;
            List<WindowTab> kept = new ArrayList<WindowTab>();
            if (tabs != null) {
                for (WindowTab tab : tabs) {
                    if (tab != null) {
                        kept.add(tab);
                    }
                }
            }
            this.tabs = kept;
            this.activeTab = activeTab;
            this.locked = locked;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.linkTarget = linkTarget;
            this.linkSide = linkSide == null
                    ? Window.LinkSide.BELOW : linkSide;
            this.height = clampWindowHeight(height);
            this.width = clampWindowWidth(width);
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
        }
    }

    /**
     * Adds a closed tab as a window's last and puts it in front. Refused
     * for a locked or missing window and a tab already open.
     */
    public static synchronized boolean addTab(String windowId, WindowTab tab) {
        Window window = window(windowId);
        if (tab == null || window == null || window.isLocked() || isOpen(tab)) {
            return false;
        }
        window.tabs().add(tab);
        window.setActiveTab(tab);
        changed();
        return true;
    }

    /**
     * Adds tabs to the end of a window's row, leaving its front tab alone:
     * where a loaded layout's systems put tabs the file placed nowhere.
     * Not written; the caller decides.
     */
    public static synchronized void appendTabs(Window window,
                                               List<? extends WindowTab> tabs) {
        if (window == null || tabs == null || !WINDOWS.contains(window)) {
            return;
        }
        for (WindowTab tab : tabs) {
            if (tab != null && !isOpen(tab)) {
                window.tabs().add(tab);
            }
        }
    }

    /**
     * Gives a window its own width, in GUI pixels, or 0 to follow the
     * game's chat-width setting again. {@code persist} is false while a
     * resize is in progress so the file is written once, on release.
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
        if (window(windowId) == null) {
            return;
        }
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

    /** Open tabs across every window; zero once they are all closed. */
    public static synchronized int openTabCount() {
        int count = 0;
        for (int index = 0; index < WINDOWS.size(); index++) {
            count += WINDOWS.get(index).tabs().size();
        }
        return count;
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

    public static synchronized boolean setLocked(String windowId,
                                                 boolean locked) {
        Window window = window(windowId);
        if (window == null || window.isLocked() == locked) {
            return false;
        }
        window.setLocked(locked);
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
     * Gives a window its own height in GUI pixels, or 0 to follow the
     * game's chat settings again. The height is fractional: a window
     * keeps the exact size it was dragged to. {@code persist} is false
     * while a resize is in progress so the file is written once, on
     * release.
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

    /**
     * Links a window to another it sits directly above or below. A
     * window linked the other way round to this one lets go first, so
     * two windows never hold each other.
     */
    public static synchronized boolean link(String windowId, String targetId,
                                            boolean above) {
        return link(windowId, targetId, above
                ? Window.LinkSide.ABOVE : Window.LinkSide.BELOW);
    }

    /**
     * Sticks a window to one side of another. A window stuck the other
     * way round to this one lets go first, so two windows never hold
     * each other, and a chain never closes on itself.
     */
    public static synchronized boolean link(String windowId, String targetId,
                                            Window.LinkSide side) {
        Window window = window(windowId);
        Window target = window(targetId);
        if (window == null || target == null || window == target
                || side == null) {
            return false;
        }
        if (windowId.equals(target.getLinkTarget())) {
            target.setLink(null, Window.LinkSide.BELOW);
        }
        window.setLink(targetId, side);
        changed();
        return true;
    }

    /**
     * Every window stuck to this one, however many hops away and in
     * whichever direction the sticking runs, the window itself included.
     * A stuck group moves as one piece, so a drag carries all of them.
     */
    public static synchronized List<Window> linkedGroup(
            Window window) {
        List<Window> group = new ArrayList<Window>();
        if (window == null) {
            return group;
        }
        group.add(window);
        for (int pass = 0; pass < MAX_WINDOWS; pass++) {
            boolean grew = false;
            for (Window candidate : WINDOWS) {
                if (group.contains(candidate)) {
                    continue;
                }
                for (int index = 0; index < group.size(); index++) {
                    Window member = group.get(index);
                    if (candidate.getId().equals(member.getLinkTarget())
                            || member.getId().equals(
                                    candidate.getLinkTarget())) {
                        group.add(candidate);
                        grew = true;
                        break;
                    }
                }
            }
            if (!grew) {
                break;
            }
        }
        return group;
    }

    /**
     * The window at the head of a stuck chain — the one whose stored
     * position the others are placed from. A window that is stuck to
     * nothing is its own root, and a chain that somehow closed on itself
     * stops short of the window it started from.
     */
    public static synchronized Window linkRoot(Window window) {
        Window root = window;
        for (int step = 0; step < MAX_WINDOWS && root != null
                && root.isLinked(); step++) {
            Window target = window(root.getLinkTarget());
            if (target == null || target == window) {
                break;
            }
            root = target;
        }
        return root == null ? window : root;
    }

    public static synchronized boolean unlink(String windowId) {
        Window window = window(windowId);
        if (window == null || !window.isLinked()) {
            return false;
        }
        window.setLink(null, false);
        changed();
        return true;
    }

    /** Windows linked to the given one, which follow it when it moves. */
    public static synchronized List<Window> linkedTo(String windowId) {
        List<Window> result = new ArrayList<Window>();
        for (int index = 0; index < WINDOWS.size(); index++) {
            if (windowId != null
                    && windowId.equals(WINDOWS.get(index).getLinkTarget())) {
                result.add(WINDOWS.get(index));
            }
        }
        return result;
    }

    /** Writes the current state through the listener, if any. */
    public static synchronized void persist() {
        changed();
    }

    /** The window last brought to the front, else the first; null with none. */
    private static Window frontWindow() {
        List<Window> order = byRecency();
        return order.isEmpty() ? null : order.get(0);
    }

    /**
     * Puts a window opened by itself one cascade step right and down
     * from {@code reference}, at that window's size, so windows opened
     * that way stack as desktop windows do and the same layout
     * always gives the same place. With no window to cascade from, the
     * first one lands where the default layout puts its conversation
     * window. Measured in the boxes the windows are drawn in when there
     * is a client to measure with, and otherwise on a screen of a fixed
     * size: the rule is the same either way, only the pixels differ.
     */
    private static void cascadeFrom(Window created, Window reference) {
        if (reference == null) {
            created.setOffsets(0.0D, 100.0D);
            return;
        }
        // The size the player gave the window it comes from, as a
        // detached tab's window takes it.
        created.setOwnHeight(reference.getOwnHeight());
        created.setOwnWidth(reference.getOwnWidth());
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
        // The new window is placed by its tab row: the corner is where
        // the row lands at the size the window opens at, the way a tab
        // torn off a row is placed, so the new window's row stands a step
        // below the reference's whatever height it opens at. A reference
        // filling the screen is measured by the box it goes back to.
        WindowPlacement.Box from = WindowPlacement.restingBounds(
                reference, minecraft, screenWidth, screenHeight);
        int width = WindowPlacement.windowWidth(created, minecraft);
        double height = WindowPlacement.currentHeight(created, minecraft);
        WindowCascade.Corner corner = WindowCascade.place(
                from.x, from.y, width, height, screenWidth,
                screenHeight, WindowPlacement.EDGE_MARGIN,
                WindowCascade.STEP);
        double baseline = WindowPlacement.baselineForRowTop(
                created, minecraft, corner.y);
        created.setOffsets(
                clampWindowPercent(WindowPlacement.windowPercentX(
                        created, corner.x, minecraft, screenWidth)),
                clampWindowPercent(WindowPlacement.windowPercentY(
                        created, baseline, minecraft, screenHeight)));
    }

    /** The running client, or null headlessly or before it exists. */
    private static net.minecraft.client.Minecraft clientMinecraft() {
        try {
            return net.minecraft.client.Minecraft.getMinecraft();
        } catch (RuntimeException unavailable) {
            return null;
        } catch (LinkageError unavailable) {
            return null;
        }
    }

    private static Window newWindow() {
        return new Window(ID_PREFIX + nextWindowNumber++);
    }

    /** Takes an emptied window out of the layout; its holders let go. */
    private static void dropWindow(Window window) {
        Iterator<Window> iterator = WINDOWS.iterator();
        while (iterator.hasNext()) {
            Window other = iterator.next();
            if (other == window) {
                iterator.remove();
            } else if (window.getId().equals(other.getLinkTarget())) {
                other.setLink(null, false);
            }
        }
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

    static double clampPercent(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, value));
    }

    /**
     * A window's percent: between the margins as {@link #clampPercent},
     * and past them by up to the window's own size either way, which is
     * how far a window may hang off the screen
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
