package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The window each view opens with: as a new player first sees it, once
 * every window of it has been closed, and after Reset View. Every view
 * opens one window at the default place, its pages as tabs, the first in
 * front: a category's view locked, a custom view's New Page unlocked, so
 * the page picked there takes its tab.
 *
 * <ul>
 * <li>Chat: Global and OOC, and the conversations waiting for a window.</li>
 * <li>Consoles: every console the player may read.</li>
 * <li>Map: the map.</li>
 * <li>Quest Journal: the journal, then the map.</li>
 * <li>Fellowships: the Fellowships page, the map, then the conversation
 * of every fellowship of the character played.</li>
 * <li>Profile: the Characters page.</li>
 * <li>Settings: every settings page.</li>
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

    /** The window a view opens with: its pages, the first in front, and its padlock. */
    static final class Defaults {
        final Pages pages;
        final boolean locked;

        Defaults(Pages pages, boolean locked) {
            this.pages = pages;
            this.locked = locked;
        }
    }

    private static final Map<PageCategory, Defaults> OWN =
            new EnumMap<PageCategory, Defaults>(PageCategory.class);
    /** The sets the systems give by name: the chat's. */
    private static final Map<String, Pages> NAMED = new HashMap<String, Pages>();
    /** A custom view's window. */
    private static final Defaults CUSTOM = new Defaults(page(NewPage.PAGE_ID),
            false);

    static {
        declare(PageCategory.CHANNELS, named(CHAT));
        declare(PageCategory.CONSOLES, category(PageCategory.CONSOLES));
        declare(PageCategory.MAP, page(MAP));
        declare(PageCategory.QUEST_JOURNAL, page(JOURNAL), page(MAP));
        declare(PageCategory.FELLOWSHIPS, page(FELLOWSHIPS), page(MAP),
                named(FELLOWSHIP_CHATS));
        declare(PageCategory.PROFILE, page(CHARACTERS));
        declare(PageCategory.SETTINGS, category(PageCategory.SETTINGS));
    }

    private ViewDefaults() {}

    /** Gives a set of pages a name a view's window opens with: the chat's. */
    public static synchronized void addPages(String name, Pages pages) {
        if (name != null && pages != null) {
            NAMED.put(name, pages);
        }
    }

    /** The window {@code view} opens with; null for none. */
    static synchronized Defaults of(View view) {
        if (view == null) {
            return null;
        }
        return view.isCustom() ? CUSTOM : OWN.get(view.category());
    }

    /** A category's view's window, locked, holding {@code sets} one after the other. */
    private static void declare(PageCategory category, final Pages... sets) {
        OWN.put(category, new Defaults(new Pages() {
            @Override
            public List<? extends WindowPage> pages() {
                List<WindowPage> pages = new ArrayList<WindowPage>();
                for (Pages set : sets) {
                    pages.addAll(set.pages());
                }
                return pages;
            }
        }, true));
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
