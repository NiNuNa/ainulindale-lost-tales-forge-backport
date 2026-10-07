package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The pages a window can hold, by code name: the journal, the fellowship, the
 * map, the Characters page, the Motion Lab, and the pages of things in
 * the world. A system with a page registers it once as the client
 * starts, with the words its tab reads, the item its tab wears, its key
 * and how its content is made; nothing in the window system names a
 * page itself.
 * A page may stand open more than once, each copy a tab
 * ({@link OtherPage}) and a content of its own, made the first time it is
 * shown and kept until the player leaves the world; a new copy past the
 * first starts afresh. A page standing for a thing in the world opens
 * once.
 */
public final class WindowPages {
    /** Makes a page's content. */
    public interface Factory {
        PageContent create();
    }

    /** One page: its code name, its tab's words and item, its key, its category, and each copy's content once made. */
    public static final class Page {
        public final String id;
        private final String titleKey;
        private final ItemStack icon;
        private final KeyBinding key;
        private final PageCategory category;
        private final Factory factory;
        private final boolean fromWorld;
        /** Whether the page opens once though the player opens it: one whose copies would share everything. */
        private final boolean once;
        private final OtherPage tab;
        /** Each copy's content once made, by the copy's number. */
        private final Map<Integer, PageContent> contents =
                new HashMap<Integer, PageContent>();

        Page(String id, String titleKey, ItemStack icon, KeyBinding key,
             PageCategory category, Factory factory, boolean fromWorld,
             boolean once) {
            this.id = id;
            this.titleKey = titleKey;
            this.icon = icon;
            this.key = key;
            this.category = category;
            this.factory = factory;
            this.fromWorld = fromWorld;
            this.once = once;
            this.tab = new OtherPage(this, 1);
        }

        /** What kind of page it is ({@link PageCategory}). */
        public PageCategory category() {
            return this.category;
        }

        /**
         * Whether the page stands for a thing in the world — a waystone —
         * and opens only from it ({@link #registerWorldPage}).
         */
        public boolean opensFromWorld() {
            return this.fromWorld;
        }

        /** Whether the page may stand open more than once: every page but one of the world's or one registered to open once. */
        boolean opensMoreThanOnce() {
            return !this.fromWorld && !this.once;
        }

        /** Whether the page's key is bound to the keyboard's {@code keyCode}. */
        boolean hasKey(int keyCode) {
            return this.key != null && keyCode > 0
                    && this.key.getKeyCode() == keyCode;
        }

        /** The page's first tab: the page itself. */
        public OtherPage tab() {
            return this.tab;
        }

        /** The tab of copy {@code instance}; the first for a page that opens once. */
        OtherPage tab(int instance) {
            return instance <= 1 || !opensMoreThanOnce() ? this.tab
                    : new OtherPage(this, Math.min(instance, WindowPage.MAX_INSTANCE));
        }

        /**
         * The copy a page's news goes to: the one open used last, else the
         * first ({@link WindowLayout#lastUsed}).
         */
        public OtherPage lastUsed() {
            WindowPage used = WindowLayout.lastUsed(this.tab);
            return used instanceof OtherPage ? (OtherPage)used : this.tab;
        }

        /** The words its tab reads. */
        public String title() {
            return StatCollector.translateToLocal(this.titleKey);
        }

        /** The item its tab wears. */
        public ItemStack icon() {
            return this.icon;
        }

        /** The content of the copy used last ({@link #lastUsed}). */
        public PageContent content() {
            return lastUsed().content();
        }

        /** Copy {@code instance}'s content, made the first time it is asked for. */
        synchronized PageContent content(int instance) {
            Integer key = Integer.valueOf(instance);
            PageContent content = this.contents.get(key);
            if (content == null) {
                content = this.factory.create();
                content.attach(tab(instance));
                this.contents.put(key, content);
            }
            return content;
        }

        /** Every copy's content made so far. */
        synchronized List<PageContent> madeContents() {
            return new ArrayList<PageContent>(this.contents.values());
        }

        /** A new copy past the first starts afresh: whatever an earlier copy of that number held goes. */
        synchronized void forgetCopy(int instance) {
            if (instance > 1) {
                this.contents.remove(Integer.valueOf(instance));
            }
        }

        synchronized void forgetContent() {
            this.contents.clear();
        }
    }

    private static final Map<String, Page> PAGES =
            new LinkedHashMap<String, Page>();

    static {
        WindowPage.addReader(new WindowPage.Reader() {
            @Override
            public WindowPage read(String id) {
                return id.startsWith(OtherPage.ID_PREFIX)
                        ? tab(id.substring(OtherPage.ID_PREFIX.length())) : null;
            }
        });
        // A category's key opens its first window with every page of it
        // the player can open, in the order they were registered.
        WindowLayout.addViewPages(new WindowLayout.ViewPages() {
            @Override
            public List<? extends WindowPage> pagesOf(PageCategory category) {
                List<OtherPage> pages = new ArrayList<OtherPage>();
                for (Page page : all()) {
                    if (page.category().home() == category && isOffered(page)) {
                        pages.add(page.tab());
                    }
                }
                return pages;
            }
        });
    }

    private WindowPages() {}

    /**
     * Registers a page under {@code id}, a code name as a channel's is:
     * lower-case letters, digits and underscores, of a {@code category}. A
     * second page under an id already taken is refused. It opens in a
     * window of its category, and its {@code key}, when it has one, turns
     * the screen to its category's view and closes it from there.
     */
    public static synchronized void register(String id, String titleKey,
                                             ItemStack icon, KeyBinding key,
                                             PageCategory category,
                                             Factory factory) {
        add(new Page(id, titleKey, icon, key, category, factory, false, false));
    }

    /**
     * Registers a page the player opens that stands open once at most:
     * one whose copies would share all they hold, as the server's
     * settings and the changes waiting on them.
     */
    public static synchronized void registerOnce(String id, String titleKey,
                                                 ItemStack icon, KeyBinding key,
                                                 PageCategory category,
                                                 Factory factory) {
        add(new Page(id, titleKey, icon, key, category, factory, false, true));
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
                                                      PageCategory category,
                                                      Factory factory) {
        add(new Page(id, titleKey, icon, null, category, factory, true, true));
    }

    private static void add(Page page) {
        if (page.id == null || !page.id.matches("[a-z0-9_]{1,24}")
                || page.titleKey == null || page.icon == null
                || page.category == null || page.factory == null
                || PAGES.containsKey(page.id)) {
            throw new IllegalArgumentException("Not a page, or one twice: "
                    + page.id);
        }
        PAGES.put(page.id, page);
    }

    /** Whether the page can be opened by hand: one the player opens, not one that opens from a thing in the world, and shown now. */
    static boolean isOffered(Page page) {
        return !page.opensFromWorld() && page.tab().isAvailable();
    }

    /**
     * Leaving the world closes every page that stands for a thing in it,
     * locked window or not; a window left empty goes. Not written: such a
     * tab is never in the layout file.
     */
    public static void closeWorldPages() {
        final List<OtherPage> bound = new ArrayList<OtherPage>();
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
            public boolean matches(WindowPage tab) {
                return bound.contains(tab);
            }
        });
    }

    /** The page registered under {@code id}; null for none. */
    public static synchronized Page byId(String id) {
        return id == null ? null : PAGES.get(id);
    }

    /** The tab of the page registered under {@code id}; null for none. */
    public static synchronized OtherPage tab(String id) {
        Page page = byId(id);
        return page == null ? null : page.tab();
    }

    /** The tab of the page whose key is bound to the keyboard's {@code keyCode}; null for none. */
    public static synchronized OtherPage tabForKey(int keyCode) {
        for (Page page : PAGES.values()) {
            if (page.hasKey(keyCode)) {
                return page.tab();
            }
        }
        return null;
    }

    /**
     * Shows a line the server sends in the chat over the bar of the page
     * it answers, by the line's lang key, while that page is shown; the
     * chat shows it as well. Answers whether a page showed it; a page not
     * shown shows nothing.
     */
    public static boolean answerOnPage(String key, String words) {
        if (key == null || key.length() == 0) {
            return false;
        }
        for (Page page : all()) {
            for (PageContent content : page.madeContents()) {
                if (content.answersLine(key) && content.tab().isShown()) {
                    content.answerLine(key, words);
                    return true;
                }
            }
        }
        return false;
    }

    /** The content of a page's tab; null for any other tab. */
    public static PageContent contentOf(WindowPage tab) {
        return tab instanceof OtherPage ? ((OtherPage)tab).content() : null;
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
