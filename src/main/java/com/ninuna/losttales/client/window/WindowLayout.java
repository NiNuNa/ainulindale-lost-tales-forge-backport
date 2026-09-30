package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
    /** The ids of the tabs kept in every view ({@link WindowView}). */
    private static final Set<String> KEPT = new LinkedHashSet<String>();
    /** The ids of the tabs pinned one by one to stay on screen while playing. */
    private static final Set<String> PINNED = new LinkedHashSet<String>();
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
     * the chat's one window of Global and OOC). Remembered places go too.
     */
    public static synchronized void reset() {
        PLACES.clear();
        KEPT.clear();
        PINNED.clear();
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
     * Adds a window holding {@code tabs}, {@code active} in front, at the
     * default place and locked, as every new window opens: how a system
     * lays out its windows for a new player, or gives tabs a loaded layout
     * placed nowhere a home. Null with no tabs; a tab already open
     * elsewhere stays where it is.
     */
    public static synchronized Window addWindow(List<? extends WindowTab> tabs,
                                                WindowTab active) {
        List<WindowTab> fresh = new ArrayList<WindowTab>();
        if (tabs != null) {
            for (WindowTab tab : tabs) {
                if (tab != null && !isOpen(tab) && !fresh.contains(tab)) {
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
        WINDOWS.add(window);
        return window;
    }

    /* ---- Pages ---- */

    /**
     * Where a page's window stood as its tab last left it: its padlock,
     * and its own place and size, a size of 0 standing for the default
     * place.
     */
    static final class Place {
        final double x;
        final double y;
        final double height;
        final int width;
        /** The part of the screen the window filled; its own box for none. */
        final Window.ScreenFill fill;
        final boolean locked;

        Place(double x, double y, double height, int width,
              Window.ScreenFill fill, boolean locked) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
            this.locked = locked;
        }
    }

    /**
     * Brings a page forward: in the window holding its tab, the tab put in
     * front there and the window raised; else in a window of its own,
     * where the page's window last stood, or at the default place,
     * locked. Null for no page.
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
        Window created = newWindow();
        Place place = PLACES.get(page.id());
        if (place != null) {
            created.setOffsets(place.x, place.y);
            created.setOwnHeight(clampWindowHeight(place.height));
            created.setOwnWidth(clampWindowWidth(place.width));
            created.setFill(place.fill);
            created.setLocked(place.locked);
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
                        window.getFill(), window.isLocked()));
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

    /* ---- Keeping ---- */

    /**
     * Whether the tab stays on screen whichever key opened it: kept in
     * every view. A tab keeps it while closed, for when it opens again.
     */
    public static synchronized boolean isKept(WindowTab tab) {
        return tab != null && KEPT.contains(tab.id());
    }

    /** Keeps the tab in every view, or lets it go back to its own. */
    public static synchronized boolean setKept(WindowTab tab, boolean kept) {
        if (tab == null || isKept(tab) == kept) {
            return false;
        }
        if (kept) {
            KEPT.add(tab.id());
        } else {
            KEPT.remove(tab.id());
        }
        changed();
        return true;
    }

    /** The ids of the tabs kept in every view, for the layout file. */
    static synchronized Set<String> kept() {
        return new LinkedHashSet<String>(KEPT);
    }

    /** What the layout file said of the kept tabs. */
    static synchronized void loadKept(Set<String> ids) {
        KEPT.clear();
        if (ids != null) {
            KEPT.addAll(ids);
        }
    }

    /* ---- Pinning ---- */

    /**
     * Whether the tab stays on screen while playing, as part of the HUD:
     * pinned itself, or standing in a window pinned whole. A pinned tab
     * also stays in every view.
     */
    public static synchronized boolean isPinned(WindowTab tab) {
        if (!staysPut(tab)) {
            return false;
        }
        if (PINNED.contains(tab.id())) {
            return true;
        }
        Window window = windowOf(tab);
        return window != null && window.isPinned();
    }

    /**
     * Whether the tab can be kept or pinned: every tab but a page that
     * stands for a thing in the world, which closes as the player walks
     * away from it.
     */
    static boolean staysPut(WindowTab tab) {
        return tab != null && !(tab instanceof PageTab
                && ((PageTab)tab).page().opensFromWorld());
    }

    /**
     * Pins the tab to stay on screen while playing, or lets it go. Letting
     * go of one tab of a window pinned whole leaves the window's other
     * tabs pinned, each by itself.
     */
    public static synchronized boolean setPinned(WindowTab tab,
                                                 boolean pinned) {
        if (!staysPut(tab) || isPinned(tab) == pinned) {
            return false;
        }
        if (pinned) {
            PINNED.add(tab.id());
        } else {
            PINNED.remove(tab.id());
            Window window = windowOf(tab);
            if (window != null && window.isPinned()) {
                window.setPinned(false);
                for (WindowTab other : window.tabs()) {
                    if (!other.equals(tab) && staysPut(other)) {
                        PINNED.add(other.id());
                    }
                }
            }
        }
        changed();
        return true;
    }

    /**
     * Pins a whole window, every tab it holds now and later, or lets all
     * of it go. Either way its tabs stop being pinned one by one: the
     * window's pin says it for them, or nothing of it is pinned.
     */
    public static synchronized boolean setWindowPinned(String windowId,
                                                       boolean pinned) {
        Window window = window(windowId);
        if (window == null) {
            return false;
        }
        boolean any = window.isPinned() != pinned;
        window.setPinned(pinned);
        for (WindowTab tab : window.tabs()) {
            any |= PINNED.remove(tab.id());
        }
        if (any) {
            changed();
        }
        return any;
    }

    /** The windows with something pinned, back to front: what stays on screen while playing. */
    public static synchronized List<Window> pinnedWindows() {
        List<Window> result = new ArrayList<Window>();
        for (Window window : stacked()) {
            if (window.isPinned()) {
                result.add(window);
                continue;
            }
            for (WindowTab tab : window.tabs()) {
                if (PINNED.contains(tab.id())) {
                    result.add(window);
                    break;
                }
            }
        }
        return result;
    }

    /** The ids of the tabs pinned one by one, for the layout file. */
    static synchronized Set<String> pinned() {
        return new LinkedHashSet<String>(PINNED);
    }

    /** What the layout file said of the pinned tabs. */
    static synchronized void loadPinned(Set<String> ids) {
        PINNED.clear();
        if (ids != null) {
            PINNED.addAll(ids);
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
     * Whether {@link #close} would remove the tab: whether it is open. The
     * padlock holds a window's place and size, never its tabs, and the
     * last tab of the last window closes like any other.
     */
    public static synchronized boolean isClosable(WindowTab tab) {
        return windowOf(tab) != null;
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
     * Closes a window, locked or not: every tab of it the view shows
     * leaves it, and the window goes with them unless tabs hidden by the
     * view stay in it for their own view. What stands behind the tabs is
     * untouched, and closing the last window is allowed.
     */
    public static synchronized boolean closeWindow(String windowId) {
        final Window window = window(windowId);
        if (window == null) {
            return false;
        }
        List<WindowTab> leaving = new ArrayList<WindowTab>();
        for (WindowTab tab : window.tabs()) {
            if (WindowView.shows(tab)) {
                leaving.add(tab);
            }
        }
        if (leaving.size() < window.tabs().size()) {
            final List<WindowTab> taken = leaving;
            removeTabs(new TabFilter() {
                @Override
                public boolean matches(WindowTab tab) {
                    return taken.contains(tab) && window.contains(tab);
                }
            });
            changed();
            return true;
        }
        rememberPlaces(window, window.tabs());
        window.tabs().clear();
        window.setActiveTab(null);
        dropWindow(window);
        changed();
        return true;
    }

    /** Whether a window holds a tab the view hides now, which the {@code +} offers. */
    public static synchronized boolean hasHidden() {
        for (Window window : WINDOWS) {
            for (WindowTab tab : window.tabs()) {
                if (tab.isAvailable() && !WindowView.shows(tab)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Picks tabs out of the layout; see {@link #removeTabs}. */
    public interface TabFilter {
        boolean matches(WindowTab tab);
    }

    /**
     * Takes every tab the filter picks out of its window, a window left
     * empty going with it, and answers the tabs taken. A page's tab
     * remembers where its window stood, as one closed by hand does, and a
     * front tab taken hands the front to its neighbour
     * ({@link #successor}). The listener is not told; the caller writes
     * the change when it is done.
     */
    public static synchronized List<WindowTab> removeTabs(TabFilter filter) {
        List<WindowTab> removed = new ArrayList<WindowTab>();
        Iterator<Window> iterator = WINDOWS.iterator();
        while (iterator.hasNext()) {
            Window window = iterator.next();
            WindowTab active = window.getActiveTab();
            int activeIndex = window.tabs().indexOf(active);
            int before = 0;
            List<WindowTab> taken = new ArrayList<WindowTab>();
            for (int index = 0; index < window.tabs().size(); index++) {
                WindowTab tab = window.tabs().get(index);
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
            rememberPlaces(window, taken);
            window.tabs().removeAll(taken);
            removed.addAll(taken);
            if (window.tabs().isEmpty()) {
                iterator.remove();
            } else if (taken.contains(active)) {
                window.setActiveTab(successor(window.tabs(),
                        activeIndex - before));
            }
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
    static WindowTab successor(List<WindowTab> tabs, int index) {
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
     * The window a tab opening in no window of its own belongs in, locked
     * or not, since the padlock holds a window's place, not its tabs: the
     * window asked for (the one the player opened it from, or the one of
     * the conversation last used), else the window most recently brought
     * to the front that holds a tab of its kind, as long as its row has
     * room for one more; when they are all full, a new window opens at
     * the default place.
     *
     * <p>Null when none was asked for and no window holds a tab of its
     * kind: a tab never opens a window of its own by itself, and waits in
     * the {@code +} until the player opens one.</p>
     */
    public static synchronized Window receivingWindow(Window preferred,
                                                      WindowTab tab) {
        // A window whose row cannot hold one more tab at its least — the
        // widest tab whole, every other down to its icon — is full.
        List<WindowTab> candidate = Collections.singletonList(tab);
        boolean asked = preferred != null && WINDOWS.contains(preferred);
        if (asked && hasRoomFor(preferred, candidate)) {
            return preferred;
        }
        boolean ofItsKind = false;
        for (Window window : byRecency()) {
            if (window != preferred && holdsKindOf(window, tab)) {
                ofItsKind = true;
                if (hasRoomFor(window, candidate)) {
                    return window;
                }
            }
        }
        if (!asked && !ofItsKind) {
            return null;
        }
        Window created = newWindow();
        WINDOWS.add(created);
        // A window that has just opened stands in front of the rest.
        raise(created.getId());
        return created;
    }

    /** Whether the window holds a tab of the same kind as {@code tab}: a conversation, or a page. */
    private static boolean holdsKindOf(Window window, WindowTab tab) {
        for (WindowTab held : window.tabs()) {
            if (held.getClass() == tab.getClass()) {
                return true;
            }
        }
        return false;
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
     * Opens a tab in a window of its own at the default place, locked:
     * how a tab comes back when no window is left to put it in. Refused
     * for a tab that is already open.
     */
    public static synchronized WindowTab openInNewWindow(WindowTab tab) {
        if (tab == null || isOpen(tab)) {
            return null;
        }
        Window created = newWindow();
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
     * that may be its own — a reorder — or another one — a dock, locked
     * or not. A source emptied by the move disappears.
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
     * and the same source window; a target whose row has no room for the
     * tabs at their least refuses, and a source emptied by the move
     * disappears.
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
        if (moved.isEmpty() || target == null) {
            return false;
        }
        Window source = windowOf(moved.get(0));
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
        int activeIndex = list.indexOf(active);
        int before = countBefore(moved, source, activeIndex);
        list.removeAll(moved);
        if (list.isEmpty()) {
            dropWindow(source);
        } else if (active != null && moved.contains(active)) {
            source.setActiveTab(successor(list, activeIndex - before));
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
     * Tears tabs off their window by hand into a new window at the given
     * percent position, {@code width} by {@code height}: the size of the
     * window they came from. The player placed it, so it opens unlocked.
     * The tabs keep their relative order. A window's only tabs dragged out
     * just move that window, which its padlock refuses.
     */
    public static synchronized Window tearOff(List<? extends WindowTab> tabs,
                                              double offsetX, double offsetY,
                                              int width, double height) {
        List<WindowTab> moved = sameWindowTabs(tabs);
        if (moved.isEmpty()) {
            return null;
        }
        Window source = windowOf(moved.get(0));
        if (source.tabs().size() == moved.size()) {
            // Everything the window held: the window itself moves,
            // rather than an empty one being left behind.
            if (source.isLocked()) {
                return null;
            }
            source.setOffsets(clampWindowPercent(offsetX),
                    clampWindowPercent(offsetY));
            changed();
            return source;
        }
        Window window = takeOut(source, moved);
        window.setLocked(false);
        window.setOwnWidth(clampWindowWidth(width));
        window.setOwnHeight(clampWindowHeight(height));
        window.setOffsets(clampWindowPercent(offsetX),
                clampWindowPercent(offsetY));
        changed();
        return window;
    }

    /**
     * Moves a tab into a window of its own at the default place, locked,
     * as every new window opens; null for a tab closed or alone in its
     * window.
     */
    public static synchronized Window moveToOwnWindow(WindowTab tab) {
        Window source = windowOf(tab);
        if (source == null || source.tabs().size() < 2) {
            return null;
        }
        Window window = takeOut(source,
                Collections.<WindowTab>singletonList(tab));
        raise(window.getId());
        changed();
        return window;
    }

    /**
     * Lifts {@code moved} out of {@code source} into a new window, the
     * last of them in front there, and hands the source's front to a
     * neighbour where one of them held it. Not written.
     */
    private static Window takeOut(Window source, List<WindowTab> moved) {
        WindowTab active = source.getActiveTab();
        int activeIndex = source.tabs().indexOf(active);
        int before = countBefore(moved, source, activeIndex);
        source.tabs().removeAll(moved);
        if (active != null && moved.contains(active)) {
            source.setActiveTab(successor(source.tabs(),
                    activeIndex - before));
        }
        Window window = newWindow();
        window.tabs().addAll(moved);
        window.setActiveTab(moved.get(moved.size() - 1));
        WINDOWS.add(window);
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

    /**
     * Takes one tab out of its window; a window left empty goes, and a
     * tab that was in front hands the front to its neighbour
     * ({@link #successor}).
     */
    private static void removeTab(Window window, WindowTab tab) {
        rememberPlaces(window, Collections.singletonList(tab));
        int index = window.tabs().indexOf(tab);
        window.tabs().remove(tab);
        if (window.tabs().isEmpty()) {
            dropWindow(window);
            return;
        }
        if (tab.equals(window.getActiveTab())) {
            window.setActiveTab(successor(window.tabs(), index));
        }
    }

    /**
     * How many of {@code moved} stand before {@code index} in the
     * window's row, read before they leave it.
     */
    private static int countBefore(List<WindowTab> moved, Window window,
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
                if (spec == null) {
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
                window.setPinned(spec.pinned);
                window.setFill(spec.fill);
                window.setOwnHeight(clampWindowHeight(spec.height));
                window.setOwnWidth(clampWindowWidth(spec.width));
                window.setActiveTab(spec.activeTab);
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
                    window.isLocked(), window.isPinned(), window.getOffsetX(),
                    window.getOffsetY(), window.getOwnHeight(),
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
        final boolean pinned;
        final double offsetX;
        final double offsetY;
        /** The window's own height in GUI pixels; 0 stands at the default place. */
        final double height;
        /** The window's own width in GUI pixels; 0 stands at the default place. */
        final int width;
        /** The part of the screen the window fills; none in its own box. */
        final Window.ScreenFill fill;

        public WindowSpec(String id, List<? extends WindowTab> tabs,
                          WindowTab activeTab, boolean locked,
                          boolean pinned, double offsetX, double offsetY,
                          double height, int width, Window.ScreenFill fill) {
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
            this.pinned = pinned;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.height = clampWindowHeight(height);
            this.width = clampWindowWidth(width);
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
        }
    }

    /**
     * Adds a closed tab as a window's last and puts it in front. Refused
     * for a missing window and a tab already open.
     */
    public static synchronized boolean addTab(String windowId, WindowTab tab) {
        Window window = window(windowId);
        if (tab == null || window == null || isOpen(tab)) {
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
     * Puts a window back as it first opened: at the default place, in
     * the middle of the screen at two thirds of it, filling no part of
     * the screen, and locked.
     */
    public static synchronized boolean resetWindow(String windowId) {
        Window window = window(windowId);
        if (window == null) {
            return false;
        }
        window.setOwnWidth(0);
        window.setOwnHeight(0.0D);
        window.setFill(Window.ScreenFill.NONE);
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
     * A new window, at the default place and locked: every window opens
     * in the middle of the screen at two thirds of it, and stays there
     * until the player unlocks it and moves it.
     */
    private static Window newWindow() {
        Window window = new Window(ID_PREFIX + nextWindowNumber++);
        window.setOffsets(50.0D, 50.0D);
        window.setLocked(true);
        return window;
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
