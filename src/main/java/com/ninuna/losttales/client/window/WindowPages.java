package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The pages a window can hold, by code name: the journal, the party, the
 * map, the Characters page, the Motion Lab, and the pages of things in
 * the world. A system with a page registers it once as the client
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
        private final KeyBinding key;
        private final Factory factory;
        private final boolean fromWorld;
        private final PageTab tab;
        private PageContent content;

        Page(String id, String titleKey, ItemStack icon, KeyBinding key,
             Factory factory, boolean fromWorld) {
            this.id = id;
            this.titleKey = titleKey;
            this.icon = icon;
            this.key = key;
            this.factory = factory;
            this.fromWorld = fromWorld;
            this.tab = new PageTab(this);
        }

        /**
         * Whether the page stands for a thing in the world — a waystone —
         * and opens only from it ({@link #registerWorldPage}).
         */
        public boolean opensFromWorld() {
            return this.fromWorld;
        }

        /** Whether the page's key is bound to the keyboard's {@code keyCode}. */
        boolean hasKey(int keyCode) {
            return this.key != null && keyCode > 0
                    && this.key.getKeyCode() == keyCode;
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

        /** Its content if it has been made; null before. */
        synchronized PageContent madeContent() {
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
     * id already taken is refused. Its window opens as every window does,
     * in the middle and unlocked, and its {@code key}, when it has one, opens it from another page and
     * closes it from itself.
     */
    public static synchronized void register(String id, String titleKey,
                                             ItemStack icon, KeyBinding key,
                                             Factory factory) {
        add(new Page(id, titleKey, icon, key, factory, false));
    }

    /**
     * Registers a page that stands for a thing in the world — a
     * waystone — and opens only from it, never by a key: the {@code +}
     * never offers it, the layout file leaves its tab out, and its tab
     * closes as the player leaves the world, since the thing may be gone
     * by the next visit.
     */
    public static synchronized void registerWorldPage(String id,
                                                      String titleKey,
                                                      ItemStack icon,
                                                      Factory factory) {
        add(new Page(id, titleKey, icon, null, factory, true));
    }

    private static void add(Page page) {
        if (page.id == null || !page.id.matches("[a-z0-9_]{1,24}")
                || page.titleKey == null || page.icon == null
                || page.factory == null
                || PAGES.containsKey(page.id)) {
            throw new IllegalArgumentException("Not a page, or one twice: "
                    + page.id);
        }
        PAGES.put(page.id, page);
    }

    /**
     * Whether a page no window holds can be opened again: one the player
     * opens, not one that opens from a thing in the world.
     */
    public static boolean hasClosed() {
        for (Page page : all()) {
            if (isOffered(page)) {
                return true;
            }
        }
        return false;
    }

    /** Whether the {@code +} offers the page: opened by the player, closed, and shown now. */
    static boolean isOffered(Page page) {
        return !page.opensFromWorld() && !WindowLayout.isOpen(page.tab())
                && page.tab().isAvailable();
    }

    /**
     * Leaving the world closes every page that stands for a thing in it,
     * locked window or not; a window left empty goes. Not written: such a
     * tab is never in the layout file.
     */
    public static void closeWorldPages() {
        final List<PageTab> bound = new ArrayList<PageTab>();
        for (Page page : all()) {
            if (page.opensFromWorld() && WindowLayout.isOpen(page.tab())) {
                bound.add(page.tab());
            }
        }
        if (bound.isEmpty()) {
            return;
        }
        WindowLayout.removeTabs(new WindowLayout.TabFilter() {
            @Override
            public boolean matches(WindowTab tab) {
                return bound.contains(tab);
            }
        });
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
     * Takes a line the server sends in the chat to the page it answers,
     * by the line's lang key, while that page is shown: its words stand
     * over the page's bar, and the chat never shows it. Answers whether a
     * page took it; a page not shown takes nothing, and the line stays in
     * the chat.
     */
    public static boolean claimLine(String key, String words) {
        if (key == null || key.length() == 0) {
            return false;
        }
        for (Page page : all()) {
            PageContent content = page.madeContent();
            if (content != null && content.answersLine(key)
                    && page.tab().isShown()) {
                content.answerLine(key, words);
                return true;
            }
        }
        return false;
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
