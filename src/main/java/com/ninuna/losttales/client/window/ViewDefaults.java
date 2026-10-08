package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The windows each view opens with: as a new player first sees it, once
 * every window of it has been closed, and after Reset View. Every
 * category's view opens its windows locked, each filling its part of the
 * screen; a custom view opens a New Page in an unlocked window at the
 * default place, so the page picked there takes its tab.
 *
 * <ul>
 * <li>Chat: Global and OOC in one window, the bottom-left quarter.</li>
 * <li>Consoles: every console the player may read in one window, the
 * top-left quarter.</li>
 * <li>Map: the map, the whole screen.</li>
 * <li>Quest Journal: the journal on the left half, the map on the
 * right.</li>
 * <li>Fellowships: the fellowships' conversations in the left quarter
 * column, the Fellowships page in the middle half, the map in the right
 * quarter column.</li>
 * <li>Profile: the Characters page, the whole screen.</li>
 * <li>Settings: every settings page in one window at the default
 * place.</li>
 * </ul>
 *
 * <p>A page another view holds already opens here as a copy of its own.
 * The chat's pages come from the sets it gives ({@link #addPages}).</p>
 */
public final class ViewDefaults {
    /** The chat's set: Global and OOC, and the conversations waiting for a window. */
    public static final String CHAT = "chat";
    /** The chat's set: the conversation of every fellowship of the character played. */
    public static final String FELLOWSHIP_CHATS = "fellowship_chats";
    /** The pages the views open with, by their systems' ids. */
    private static final String MAP = "map";
    private static final String JOURNAL = "journal";
    private static final String FELLOWSHIPS = "fellowship";
    private static final String CHARACTERS = "characters";

    /** Some pages a window opens with, read as the view opens. */
    public interface Pages {
        List<? extends WindowPage> pages();
    }

    /** One window a view opens with: its pages, the part of the screen it fills, and its padlock. */
    static final class Slot {
        final Pages pages;
        final Window.ScreenFill fill;
        final boolean locked;

        Slot(Pages pages, Window.ScreenFill fill, boolean locked) {
            this.pages = pages;
            this.fill = fill;
            this.locked = locked;
        }
    }

    private static final Map<PageCategory, List<Slot>> OWN =
            new EnumMap<PageCategory, List<Slot>>(PageCategory.class);
    /** The sets the systems give by name: the chat's. */
    private static final Map<String, Pages> NAMED = new HashMap<String, Pages>();
    /** A custom view's one window. */
    private static final List<Slot> CUSTOM = Collections.singletonList(
            new Slot(page(NewPage.PAGE_ID), Window.ScreenFill.NONE, false));

    static {
        declare(PageCategory.CHANNELS,
                locked(named(CHAT), Window.ScreenFill.BOTTOM_LEFT));
        declare(PageCategory.CONSOLES, locked(category(PageCategory.CONSOLES),
                Window.ScreenFill.TOP_LEFT));
        declare(PageCategory.MAP, locked(page(MAP), Window.ScreenFill.FULL));
        // The window that should take the keys goes last: it is raised last.
        declare(PageCategory.QUEST_JOURNAL,
                locked(page(MAP), Window.ScreenFill.RIGHT),
                locked(page(JOURNAL), Window.ScreenFill.LEFT));
        declare(PageCategory.FELLOWSHIPS,
                locked(named(FELLOWSHIP_CHATS), Window.ScreenFill.LEFT_QUARTER),
                locked(page(MAP), Window.ScreenFill.RIGHT_QUARTER),
                locked(page(FELLOWSHIPS), Window.ScreenFill.CENTRE_HALF));
        declare(PageCategory.PROFILE,
                locked(page(CHARACTERS), Window.ScreenFill.FULL));
        declare(PageCategory.SETTINGS, locked(category(PageCategory.SETTINGS),
                Window.ScreenFill.NONE));
    }

    private ViewDefaults() {}

    /** Gives a set of pages a name a view's window opens with: the chat's. */
    public static synchronized void addPages(String name, Pages pages) {
        if (name != null && pages != null) {
            NAMED.put(name, pages);
        }
    }

    /** The windows {@code view} opens with, in the order they open: the last stands in front. */
    static synchronized List<Slot> slotsOf(View view) {
        if (view == null) {
            return Collections.emptyList();
        }
        if (view.isCustom()) {
            return CUSTOM;
        }
        List<Slot> slots = OWN.get(view.category());
        return slots == null ? Collections.<Slot>emptyList() : slots;
    }

    /**
     * The part of the screen filled by the window of {@code view}'s
     * defaults that holds one of {@code tabs}' pages, the first of them
     * one holds; none where none does. Where Reset Window Layout puts a
     * window back.
     */
    static synchronized Window.ScreenFill homeFillOf(View view,
                                                     List<WindowPage> tabs) {
        for (WindowPage tab : tabs) {
            for (Slot slot : slotsOf(view)) {
                for (WindowPage page : slot.pages.pages()) {
                    if (page != null && page.isCopyOf(tab)) {
                        return slot.fill;
                    }
                }
            }
        }
        return Window.ScreenFill.NONE;
    }

    private static void declare(PageCategory category, Slot... slots) {
        OWN.put(category, Collections.unmodifiableList(Arrays.asList(slots)));
    }

    private static Slot locked(Pages pages, Window.ScreenFill fill) {
        return new Slot(pages, fill, true);
    }

    /** A page by the id it was registered under. */
    private static Pages page(final String id) {
        return new Pages() {
            @Override
            public List<? extends WindowPage> pages() {
                OtherPage page = WindowPages.tab(id);
                return page == null ? Collections.<WindowPage>emptyList()
                        : Collections.singletonList(page);
            }
        };
    }

    /** Every page of a category the player may open now, in its own order. */
    private static Pages category(final PageCategory category) {
        return new Pages() {
            @Override
            public List<? extends WindowPage> pages() {
                return WindowLayout.pagesOf(category);
            }
        };
    }

    /** A set a system gave by name; nothing while none is given. */
    private static Pages named(final String name) {
        return new Pages() {
            @Override
            public List<? extends WindowPage> pages() {
                Pages pages;
                synchronized (ViewDefaults.class) {
                    pages = NAMED.get(name);
                }
                return pages == null ? Collections.<WindowPage>emptyList()
                        : new ArrayList<WindowPage>(pages.pages());
            }
        };
    }
}
