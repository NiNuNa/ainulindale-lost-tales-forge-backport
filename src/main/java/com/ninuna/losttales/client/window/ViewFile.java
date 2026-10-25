package com.ninuna.losttales.client.window;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ninuna.losttales.data.JsonReading;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The window a view opens with, as a view file writes it (WIN-49):
 * {@code view}, a category's view by its id or {@code custom} for a new
 * custom view; {@code pages}, what the window holds in order, each a page
 * by its id, {@code set:} and a set of pages a system gives, or
 * {@code category} for every page of the view's category;
 * {@code locked}, whether the window opens locked; {@code place}, where it
 * stands: {@code middle} (two thirds of the screen, in its middle) or a
 * part of the screen by its name ({@code left_third}); and {@code stage},
 * whether the camera turns round to face the character while the view is
 * shown (WIN-50).
 *
 * <pre>
 * {
 *   "view": "quest_journal",
 *   "locked": true,
 *   "place": "middle",
 *   "stage": false,
 *   "pages": ["journal", "map"]
 * }
 * </pre>
 */
final class ViewFile {
    /** The view a new custom view opens with. */
    static final String CUSTOM = "custom";
    /** An entry naming every page of the view's category. */
    static final String CATEGORY = "category";
    /** What leads an entry naming a set of pages a system gives. */
    static final String SET_PREFIX = "set:";
    /** The most entries a window opens with. */
    static final int MAX_PAGES = 32;
    /** The place of a window at the default place: two thirds of the screen, in its middle. */
    static final String MIDDLE = "middle";

    static final Set<String> KEYS = JsonReading.keys("view", "pages", "locked",
            "place", "stage");

    /** What an entry names. */
    enum Kind {
        PAGE, SET, CATEGORY
    }

    /** One entry of a view's pages: a page, a set, or the whole category. */
    static final class Entry {
        final Kind kind;
        /** The page's id or the set's name; empty for the category. */
        final String name;

        Entry(Kind kind, String name) {
            this.kind = kind;
            this.name = name == null ? "" : name;
        }

        static Entry page(String id) {
            return new Entry(Kind.PAGE, id);
        }

        static Entry set(String name) {
            return new Entry(Kind.SET, name);
        }

        static Entry category() {
            return new Entry(Kind.CATEGORY, "");
        }

        /** The entry as a file writes it. */
        String written() {
            switch (this.kind) {
                case SET:
                    return SET_PREFIX + this.name;
                case CATEGORY:
                    return CATEGORY;
                default:
                    return this.name;
            }
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Entry)) {
                return false;
            }
            Entry entry = (Entry)other;
            return this.kind == entry.kind && this.name.equals(entry.name);
        }

        @Override
        public int hashCode() {
            return this.kind.hashCode() * 31 + this.name.hashCode();
        }

        @Override
        public String toString() {
            return written();
        }
    }

    /** What the game has for an entry to name. */
    interface Known {
        /** Whether a page is registered under {@code id}. */
        boolean isPage(String id);

        /** Whether the page under {@code id} opens only as the game opens it. */
        boolean opensByGame(String id);

        /** Whether a system gives a set of pages under {@code name}. */
        boolean isSet(String name);
    }

    final String view;
    final List<Entry> pages;
    final boolean locked;
    /** The place as written: {@code middle} or a part's name. */
    final String place;
    final boolean stage;

    ViewFile(String view, List<Entry> pages, boolean locked, String place,
             boolean stage) {
        this.view = view == null ? "" : view;
        this.pages = Collections.unmodifiableList(new ArrayList<Entry>(
                pages == null ? Collections.<Entry>emptyList() : pages));
        this.locked = locked;
        this.place = place == null ? "" : place;
        this.stage = stage;
    }

    /** The part of the screen the window fills; none for the middle, or a place no part has. */
    Window.ScreenFill fill() {
        Window.ScreenFill part = Window.ScreenFill.part(this.place);
        return part == null ? Window.ScreenFill.NONE : part;
    }

    /** The category whose view the file is; null for a custom view or none. */
    PageCategory category() {
        for (PageCategory category : PageCategory.values()) {
            if (category.id().equals(this.view)) {
                return category;
            }
        }
        return null;
    }

    /** Whether the file is a new custom view's. */
    boolean isCustom() {
        return CUSTOM.equals(this.view);
    }

    /**
     * Reads a view file: what is wrong is said, and a file with anything
     * said is left out. Null for a file that is no JSON object.
     */
    static ViewFile parse(Reader reader, String file, List<String> said) {
        if (reader == null) {
            return null;
        }
        JsonElement root = new JsonParser().parse(reader);
        if (root == null || !root.isJsonObject()) {
            return null;
        }
        JsonObject object = root.getAsJsonObject();
        JsonReading where = new JsonReading(said, file);
        where.unknownKeys(object, KEYS);
        String view = where.string(object, "view", "");
        if (view.length() == 0) {
            where.say("names no view");
        }
        if (!object.has("locked")) {
            where.say("does not say whether the window is locked");
        }
        boolean locked = where.bool(object, "locked", false);
        String place = where.string(object, "place", "");
        if (place.length() == 0) {
            where.say("does not say where the window stands");
        }
        if (!object.has("stage")) {
            where.say("does not say whether the view is a stage");
        }
        boolean stage = where.bool(object, "stage", false);
        if (!object.has("pages")) {
            where.say("lists no pages");
        }
        List<Entry> pages = new ArrayList<Entry>();
        for (String written : where.strings(object, "pages")) {
            pages.add(entryOf(written));
        }
        return new ViewFile(view, pages, locked, place, stage);
    }

    /** An entry as written: {@code category}, {@code set:} and a set's name, else a page's id. */
    static Entry entryOf(String written) {
        String text = written == null ? "" : written;
        if (CATEGORY.equals(text)) {
            return Entry.category();
        }
        if (text.startsWith(SET_PREFIX)) {
            return Entry.set(text.substring(SET_PREFIX.length()));
        }
        return Entry.page(text);
    }

    /** Every warning the file raises against what the game has. */
    static List<String> warningsOf(ViewFile file, Known known) {
        List<String> warnings = new ArrayList<String>();
        PageCategory category = file.category();
        boolean custom = file.isCustom();
        if (file.view.length() > 0 && !custom
                && (category == null || !category.isView())) {
            warnings.add("names the view " + file.view + ", which is no view;"
                    + " a view is a category's or " + CUSTOM);
        }
        if (file.place.length() > 0 && !MIDDLE.equals(file.place)
                && Window.ScreenFill.part(file.place) == null) {
            warnings.add("names the place " + file.place + ", which is no"
                    + " place; a place is " + MIDDLE + " or a part of the"
                    + " screen, such as left_third");
        }
        if (file.pages.isEmpty()) {
            warnings.add("lists no page");
        }
        if (file.pages.size() > MAX_PAGES) {
            warnings.add("lists " + file.pages.size() + " pages; a window opens"
                    + " with " + MAX_PAGES + " at most");
        }
        Set<Entry> seen = new HashSet<Entry>();
        for (Entry entry : file.pages) {
            if (!seen.add(entry)) {
                warnings.add("lists " + entry.written() + " twice");
                continue;
            }
            switch (entry.kind) {
                case CATEGORY:
                    if (custom) {
                        warnings.add("lists " + CATEGORY + ", but a custom view"
                                + " has no category of its own");
                    }
                    break;
                case SET:
                    if (!known.isSet(entry.name)) {
                        warnings.add("lists the set " + entry.name
                                + ", which no system gives");
                    }
                    break;
                default:
                    if (!known.isPage(entry.name)) {
                        warnings.add("lists the page " + entry.name
                                + ", which is no page");
                    } else if (known.opensByGame(entry.name)) {
                        warnings.add("lists the page " + entry.name
                                + ", which only the game opens");
                    }
                    break;
            }
        }
        return warnings;
    }
}
