package com.ninuna.losttales.client.window;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The window each view opens with: as a new player first sees it, once
 * every window of it has been closed, and after Reset View. What each
 * window holds, whether it opens locked, where it stands and whether the
 * view is a stage are the view files' ({@link ViewFiles}, WIN-49, WIN-50);
 * the rules are the code's. Every view opens one window at its place, its
 * pages as tabs, the first in front.
 * A page another view holds already opens here as a copy of its own, and
 * a page the player cannot open now is left out as the window opens. The
 * chat's pages come from the sets it gives ({@link #addPages}).
 */
public final class ViewDefaults {
    /** The chat's set: Global and OOC, and the conversations waiting for a window. */
    public static final String CHAT = "chat";
    /** The chat's set: the conversation of every fellowship of the character played. */
    public static final String FELLOWSHIP_CHATS = "fellowship_chats";

    /** Some pages a window opens with, read as the view opens. */
    public interface Pages {
        List<? extends WindowPage> pages();
    }

    /** The window a view opens with: its pages, the first in front, its padlock and its place. */
    static final class Defaults {
        final Pages pages;
        final boolean locked;
        final Window.ScreenFill place;

        Defaults(Pages pages, boolean locked, Window.ScreenFill place) {
            this.pages = pages;
            this.locked = locked;
            this.place = place;
        }
    }

    /** The sets the systems give by name: the chat's. */
    private static final Map<String, Pages> NAMED = new HashMap<String, Pages>();
    /**
     * The view files in force, by the view each is: a category's id, or
     * custom. Null until first asked for or read.
     */
    private static Map<String, ViewFile> files;

    private ViewDefaults() {}

    /** Gives a set of pages a name a view's window opens with: the chat's. */
    public static synchronized void addPages(String name, Pages pages) {
        if (name != null && pages != null) {
            NAMED.put(name, pages);
        }
    }

    /** Whether a system gives a set of pages under {@code name}. */
    static synchronized boolean isSet(String name) {
        return name != null && NAMED.containsKey(name);
    }

    /** Takes the view files the windows open with from now on; a view no file names opens with none. */
    static synchronized void use(List<ViewFile> found) {
        Map<String, ViewFile> byView = new LinkedHashMap<String, ViewFile>();
        if (found != null) {
            for (ViewFile file : found) {
                byView.put(file.view, file);
            }
        }
        files = Collections.unmodifiableMap(byView);
    }

    /** Lets the files in force go: the mod's own are read from its jar when next asked for. */
    static synchronized void forget() {
        files = null;
    }

    /** The file {@code view} opens with: a category's id, or custom; null for none. */
    static synchronized ViewFile fileOf(String view) {
        return files().get(view);
    }

    /** The window {@code view} opens with; null for none. */
    static synchronized Defaults of(View view) {
        ViewFile file = fileOf(view);
        return file == null ? null : defaultsOf(file);
    }

    /**
     * Where {@code view}'s windows stand at their place: the part of the
     * screen its file names, none for the middle or a view no file names.
     */
    static synchronized Window.ScreenFill placeOf(View view) {
        ViewFile file = fileOf(view);
        return file == null ? Window.ScreenFill.NONE : file.fill();
    }

    /**
     * Whether {@code view} is a stage: while it is shown, the camera turns
     * round to face the player's character in the part of the screen its
     * windows leave free (WIN-50).
     */
    public static synchronized boolean isStage(View view) {
        ViewFile file = fileOf(view);
        return file != null && file.stage;
    }

    private static ViewFile fileOf(View view) {
        return view == null ? null : files().get(view.isCustom()
                ? ViewFile.CUSTOM
                : view.category() == null ? "" : view.category().id());
    }

    /**
     * The files in force. Until the resources are first read, which checks
     * every file against the game ({@link ViewFiles#reload}), the mod's own
     * as its jar holds them, checked for their shape.
     */
    private static Map<String, ViewFile> files() {
        if (files == null) {
            use(ViewFiles.classpathFiles());
        }
        return files;
    }

    /** A file's window: its entries' pages one after the other, locked as it says. */
    private static Defaults defaultsOf(ViewFile file) {
        final List<Pages> sets = new ArrayList<Pages>();
        for (ViewFile.Entry entry : file.pages) {
            sets.add(pagesOf(entry, file.category()));
        }
        return new Defaults(new Pages() {
            @Override
            public List<? extends WindowPage> pages() {
                List<WindowPage> pages = new ArrayList<WindowPage>();
                for (Pages set : sets) {
                    pages.addAll(set.pages());
                }
                return pages;
            }
        }, file.locked, file.fill());
    }

    private static Pages pagesOf(ViewFile.Entry entry, PageCategory category) {
        switch (entry.kind) {
            case SET:
                return named(entry.name);
            case CATEGORY:
                return category(category);
            default:
                return page(entry.name);
        }
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
                return category == null ? Collections.<WindowPage>emptyList()
                        : WindowLayout.pagesOf(category);
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
