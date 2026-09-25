package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The pages a window can hold, by code name: the quest journal, the
 * party, the map. A system with a page registers it once as the client
 * starts, with the words its tab reads, the item its tab wears, its key
 * and how its content is made; nothing in the window system names a
 * page itself.
 * Each page is one tab ({@link PageTab}), so one window holds it at
 * most, and its content is made the first time it is shown and kept
 * until the player leaves the world.
 */
public final class WindowPages {
    /** Makes a page's content. */
    public interface Factory {
        PageContent create();
    }

    /** One page: its code name, its tab's words and item, its key, and its content once made. */
    public static final class Page {
        public final String id;
        private final String titleKey;
        private final ItemStack icon;
        private final Window.ScreenFill firstFill;
        private final KeyBinding key;
        private final Factory factory;
        private final PageTab tab;
        private PageContent content;

        Page(String id, String titleKey, ItemStack icon,
             Window.ScreenFill firstFill, KeyBinding key, Factory factory) {
            this.id = id;
            this.titleKey = titleKey;
            this.icon = icon;
            this.firstFill = firstFill;
            this.key = key;
            this.factory = factory;
            this.tab = new PageTab(this);
        }

        /** Whether the page's key is bound to the keyboard's {@code keyCode}. */
        boolean hasKey(int keyCode) {
            return this.key != null && keyCode > 0
                    && this.key.getKeyCode() == keyCode;
        }

        /**
         * The part of the screen the page's window fills the first time it
         * opens, before it has a place of its own; none for a window of the
         * page's size.
         */
        public Window.ScreenFill firstFill() {
            return this.firstFill;
        }

        /** The page's one tab. */
        public PageTab tab() {
            return this.tab;
        }

        /** The words its tab reads. */
        public String title() {
            return StatCollector.translateToLocal(this.titleKey);
        }

        /** The item its tab wears. */
        public ItemStack icon() {
            return this.icon;
        }

        /** Its content, made the first time it is asked for. */
        public synchronized PageContent content() {
            if (this.content == null) {
                this.content = this.factory.create();
            }
            return this.content;
        }

        synchronized void forgetContent() {
            this.content = null;
        }
    }

    private static final Map<String, Page> PAGES =
            new LinkedHashMap<String, Page>();

    static {
        WindowTab.addReader(new WindowTab.Reader() {
            @Override
            public WindowTab read(String id) {
                return id.startsWith(PageTab.ID_PREFIX)
                        ? tab(id.substring(PageTab.ID_PREFIX.length())) : null;
            }
        });
    }

    private WindowPages() {}

    /**
     * Registers a page under {@code id}, a code name as a channel's is:
     * lower-case letters, digits and underscores. A second page under an
     * id already taken is refused.
     */
    public static synchronized void register(String id, String titleKey,
                                             ItemStack icon, Factory factory) {
        register(id, titleKey, icon, Window.ScreenFill.NONE, null, factory);
    }

    /**
     * As above, for a page whose window fills {@code firstFill} the first
     * time it opens, and whose {@code key}, when it has one, opens it from
     * another page and closes it from itself.
     */
    public static synchronized void register(String id, String titleKey,
                                             ItemStack icon,
                                             Window.ScreenFill firstFill,
                                             KeyBinding key,
                                             Factory factory) {
        if (id == null || !id.matches("[a-z0-9_]{1,24}") || titleKey == null
                || icon == null || firstFill == null || factory == null
                || PAGES.containsKey(id)) {
            throw new IllegalArgumentException("Not a page, or one twice: " + id);
        }
        PAGES.put(id, new Page(id, titleKey, icon, firstFill, key, factory));
    }

    /** Whether a page no window holds can be opened again. */
    public static boolean hasClosed() {
        for (Page page : all()) {
            if (!WindowLayout.isOpen(page.tab()) && page.tab().isAvailable()) {
                return true;
            }
        }
        return false;
    }

    /** The page registered under {@code id}; null for none. */
    public static synchronized Page byId(String id) {
        return id == null ? null : PAGES.get(id);
    }

    /** The tab of the page registered under {@code id}; null for none. */
    public static synchronized PageTab tab(String id) {
        Page page = byId(id);
        return page == null ? null : page.tab();
    }

    /** The tab of the page whose key is bound to the keyboard's {@code keyCode}; null for none. */
    public static synchronized PageTab tabForKey(int keyCode) {
        for (Page page : PAGES.values()) {
            if (page.hasKey(keyCode)) {
                return page.tab();
            }
        }
        return null;
    }

    /**
     * Closes every open page that closes with the screen (the map), its
     * place kept for the next time. A locked window keeps it.
     */
    public static void closeThoseClosingWithScreen() {
        boolean closed = false;
        for (Page page : all()) {
            PageTab tab = page.tab();
            if (WindowLayout.isOpen(tab) && tab.content().closesWithScreen()) {
                closed |= WindowLayout.close(tab);
            }
        }
        if (closed) {
            TabSelection.prune();
        }
    }

    /** The content of a page's tab; null for any other tab. */
    public static PageContent contentOf(WindowTab tab) {
        return tab instanceof PageTab ? ((PageTab)tab).content() : null;
    }

    /** Every page, in the order they were registered. */
    public static synchronized List<Page> all() {
        return new ArrayList<Page>(PAGES.values());
    }

    /** Leaving the world lets every page's content go; it is made fresh on the next. */
    public static synchronized void forgetContents() {
        for (Page page : PAGES.values()) {
            page.forgetContent();
        }
    }
}
