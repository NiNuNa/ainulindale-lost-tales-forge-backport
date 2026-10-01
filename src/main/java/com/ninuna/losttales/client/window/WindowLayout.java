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
     * the chat's one window of Global and OOC). Remembered places go too.
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
     * Adds a window holding {@code tabs}, {@code active} in front, at the
     * default place and locked: how a system lays out its windows for a
     * new player, or gives tabs a loaded layout placed nowhere a home.
     * These are the only windows that open locked. Null with no tabs; a
     * tab already open elsewhere stays where it is.
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
        window.setLocked(true);
        window.tabs().addAll(fresh);
        window.setActiveTab(active);
        WINDOWS.add(window);
        return window;
    }

    /* ---- Pages ---- */

    /**
     * Where a page's window stood as its tab last left it: its padlock,
     * its pins, and its own place and size, a size of 0 standing for the
     * default place.
     */
    static final class Place {
        final double x;
        final double y;
        final double height;
        final int width;
        /** The part of the screen the window filled; its own box for none. */
        final Window.ScreenFill fill;
        final boolean locked;
        final boolean pinnedToHud;
        final boolean pinnedToGui;

        Place(double x, double y, double height, int width,
              Window.ScreenFill fill, boolean locked, boolean pinnedToHud,
              boolean pinnedToGui) {
            this.x = x;
            this.y = y;
            this.height = height;
            this.width = width;
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
            this.locked = locked;
            this.pinnedToHud = pinnedToHud;
            this.pinnedToGui = pinnedToGui;
        }
    }

    /**
     * Brings a page forward: in the window holding its tab, the tab put in
     * front there and the window raised; else in a window of its own, in
     * front, where the page's window last stood, padlock and pins and all,
     * or a step on from the window in front, unlocked. Null for no page.
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
            created.setPinnedToHud(place.pinnedToHud);
            created.setPinnedToGui(place.pinnedToGui);
        } else {
            cascadeFrom(created, frontWindow());
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
                        window.getFill(), window.isLocked(),
                        window.isPinnedToHud(), window.isPinnedToGui()));
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
            if (window.isPinnedToHud()) {
                result.add(window);
            }
        }
        return result;
    }

    /**
     * Whether the tab shows while playing: it stands in a window pinned to
     * the HUD and is no world page.
     */
    public static synchronized boolean isOnHud(WindowTab tab) {
        Window window = windowOf(tab);
        return window != null && window.isPinnedToHud() && staysPut(tab);
    }

    /**
     * Whether every view shows the tab: it stands in a window pinned to
     * the GUI and is no world page.
     */
    public static synchronized boolean isOnGui(WindowTab tab) {
        Window window = windowOf(tab);
        return window != null && window.isPinnedToGui() && staysPut(tab);
    }

    /**
     * Whether a pinned window shows the tab: every tab but a page that
     * stands for a thing in the world, which closes as the player walks
     * away from it.
     */
    static boolean staysPut(WindowTab tab) {
        return tab != null && !(tab instanceof PageTab
                && ((PageTab)tab).page().opensFromWorld());
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
     * Whether the player may close the tab by hand: it is open and its
     * window is not locked. A locked window keeps the tabs it holds. The
     * last tab of the last window closes like any other.
     */
    public static synchronized boolean isClosable(WindowTab tab) {
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
    public static synchronized boolean close(WindowTab tab) {
        if (!isClosable(tab)) {
            return false;
        }
        removeTab(windowOf(tab), tab);
        changed();
        return true;
    }

    /**
     * Closes a window by hand, which a locked one refuses: every tab of it
     * the view shows leaves it, and the window goes with them unless tabs
     * hidden by the view stay in it for their own view. What stands behind
     * the tabs is untouched, and closing the last window is allowed.
     */
    public static synchronized boolean closeWindow(String windowId) {
        final Window window = window(windowId);
        if (window == null || window.isLocked()) {
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
     * The window a tab opening in no window of its own belongs in: the
     * window asked for (the one the player opened it from, or the one of
     * the conversation last used), else the window most recently brought
     * to the front that holds a tab of its kind, as long as its row has
     * room for one more; when none of them takes it, a new window opens a
     * step on from the window asked for, else from the one in front,
     * unlocked and in front of it.
     *
     * <p>A locked window takes nothing, whoever opens the tab: the padlock
     * keeps the tabs a window holds, so a whisper reaching a player whose
     * conversation windows are all locked opens in a window of its own.</p>
     *
     * <p>Null when none was asked for and no window holds a tab of its
     * kind: a tab never opens a window of its own by itself, and waits in
     * the {@code +} until the player opens one.</p>
     */
    private static Window receivingWindow(Window preferred, WindowTab tab) {
        // A window whose row cannot hold one more tab at its least — the
        // widest tab whole, every other down to its icon — is full.
        List<WindowTab> candidate = Collections.singletonList(tab);
        boolean asked = preferred != null && WINDOWS.contains(preferred);
        if (asked && takes(preferred, candidate)) {
            return preferred;
        }
        boolean ofItsKind = false;
        for (Window window : byRecency()) {
            if (window != preferred && holdsKindOf(window, tab)) {
                ofItsKind = true;
                if (takes(window, candidate)) {
                    return window;
                }
            }
        }
        if (!asked && !ofItsKind) {
            return null;
        }
        Window created = newWindow();
        cascadeFrom(created, asked ? preferred : frontWindow());
        WINDOWS.add(created);
        raise(created.getId());
        return created;
    }

    /** Whether the window takes the tabs: it is unlocked, and its row has room. */
    private static boolean takes(Window window,
                                 List<? extends WindowTab> tabs) {
        return !window.isLocked() && hasRoomFor(window, tabs);
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
     * Opens a tab where it belongs ({@link #receivingWindow}), never in a
     * locked window, leaving the window's front tab alone, and answers the
     * tab as the layout holds it: the one already open, if it is. Null
     * when there is no window to open it in.
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
     * Opens a tab in a window of its own, a step on from the window in
     * front, unlocked and in front of it: how a tab comes back when no
     * window is left to put it in. Refused for a tab that is already open.
     */
    public static synchronized WindowTab openInNewWindow(WindowTab tab) {
        if (tab == null || isOpen(tab)) {
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
     * that may be its own — a reorder — or another one — a dock. A locked
     * window refuses, as source and as target. A source emptied by the
     * move disappears.
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
     * and the same source window; a locked source or target refuses, a
     * reorder included, as does a target whose row has no room for the
     * tabs at their least. A source emptied by the move disappears.
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
     * just move that window. A locked window lets nothing go.
     */
    public static synchronized Window tearOff(List<? extends WindowTab> tabs,
                                              double offsetX, double offsetY,
                                              int width, double height) {
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
                window.setPinnedToHud(spec.pinnedToHud);
                window.setPinnedToGui(spec.pinnedToGui);
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
                    window.isLocked(), window.isPinnedToHud(),
                    window.isPinnedToGui(), window.getOffsetX(),
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
        final boolean pinnedToHud;
        final boolean pinnedToGui;
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
                          boolean pinnedToHud, boolean pinnedToGui,
                          double offsetX, double offsetY,
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
            this.pinnedToHud = pinnedToHud;
            this.pinnedToGui = pinnedToGui;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.height = clampWindowHeight(height);
            this.width = clampWindowWidth(width);
            this.fill = fill == null ? Window.ScreenFill.NONE : fill;
        }
    }

    /**
     * Adds a closed tab as a window's last and puts it in front. Refused
     * for a missing or locked window and a tab already open.
     */
    public static synchronized boolean addTab(String windowId, WindowTab tab) {
        Window window = window(windowId);
        if (tab == null || window == null || window.isLocked()
                || isOpen(tab)) {
            return false;
        }
        window.tabs().add(tab);
        window.setActiveTab(tab);
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

    /** The window drawn in front of the others; null with none. */
    private static Window frontWindow() {
        List<Window> order = stacked();
        return order.isEmpty() ? null : order.get(order.size() - 1);
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
     * Puts a window's layout back as it first was: at the default place,
     * in the middle of the screen at two thirds of it, filling no part of
     * the screen, and locked again. Its pages and its pins stay. A locked
     * window stays where it is, so only an unlocked one resets.
     */
    public static synchronized boolean resetWindow(String windowId) {
        Window window = window(windowId);
        if (window == null || window.isLocked()) {
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
     * A new window, at the default place and unlocked: in the middle of
     * the screen at two thirds of it until it is given a place. Only the
     * windows a new player starts with are locked ({@link #addWindow}).
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
