package com.ninuna.losttales.client.chat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * The pages a chat window can hold beside its conversations, by code name.
 * A system with a page registers it once as the client starts — the quest
 * journal, the party — with the words its tab reads, the item its tab
 * wears and how its content is made; nothing in the chat names a page
 * itself. Each page is one tab, so one window holds it at most, and its
 * content is made the first time it is shown and kept until the player
 * leaves the world.
 */
public final class ChatPages {
    /** Makes a page's content. */
    public interface Factory {
        ChatPageContent create();
    }

    /** One page: its code name, its tab's words and item, and its content once made. */
    public static final class Page {
        public final String id;
        private final String titleKey;
        private final ItemStack icon;
        private final Factory factory;
        private ChatPageContent content;

        Page(String id, String titleKey, ItemStack icon, Factory factory) {
            this.id = id;
            this.titleKey = titleKey;
            this.icon = icon;
            this.factory = factory;
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
        public synchronized ChatPageContent content() {
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

    private ChatPages() {}

    /**
     * Registers a page under {@code id}, a code name as a channel's is:
     * lower-case letters, digits and underscores. A second page under an
     * id already taken is refused.
     */
    public static synchronized void register(String id, String titleKey,
                                             ItemStack icon, Factory factory) {
        if (id == null || !id.matches("[a-z0-9_]{1,24}") || titleKey == null
                || icon == null || factory == null || PAGES.containsKey(id)) {
            throw new IllegalArgumentException("Not a page, or one twice: " + id);
        }
        PAGES.put(id, new Page(id, titleKey, icon, factory));
    }

    /** The page registered under {@code id}; null for none. */
    public static synchronized Page byId(String id) {
        return id == null ? null : PAGES.get(id);
    }

    /** The content of a page tab; null for any other tab. */
    static ChatPageContent contentOf(ChatTab tab) {
        Page page = tab == null ? null : tab.page();
        return page == null ? null : page.content();
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
