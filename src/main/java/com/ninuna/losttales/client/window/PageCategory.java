package com.ninuna.losttales.client.window;

import net.minecraft.util.StatCollector;

/**
 * What kind of page a page is, and the views the screen swaps between. The
 * New Page lists the pages by category; a category's key, or its button on
 * the Views sub-window, swaps the screen to its view, its own windows; a
 * page opens in a window of its category; and a category with no window
 * opens its first window, at the category's own place and locked but the
 * New Page's: the map filling the screen, every other category in the
 * middle at two thirds. The Lost Tales Menu ({@link #MENU}) is a view and
 * no page's category: its windows hold whatever the player opens there.
 * The New Page is a category and no view.
 *
 * <p>Whispers and the consoles are the channels' subcategories. Whispers
 * stand with the channels, in their view and their windows; the consoles
 * have a view and windows of their own, the command key's.</p>
 */
public enum PageCategory {
    /**
     * The Lost Tales Menu's view, which its key opens: windows of its own
     * holding whatever the player opened there, any category mixed. No
     * page is of it.
     */
    MENU("menu", null, true, Window.ScreenFill.NONE, false),
    /**
     * The New Page, every page's way in: the {@code +} opens it as a
     * window's new page, and the main menu's first button in a window of
     * its own.
     */
    NEW_PAGE("new_page", null, true, Window.ScreenFill.NONE, false),
    /** The channels: Global, OOC, a faction's, a fellowship's, a server's own. */
    CHANNELS("channels", null, true, Window.ScreenFill.NONE, true),
    /** The Console and the Server Log, the command key's. */
    CONSOLES("consoles", CHANNELS, true, Window.ScreenFill.NONE, true),
    /** Whispers, with players and with NPCs. */
    WHISPERS("whispers", CHANNELS, false, Window.ScreenFill.NONE, true),
    /** The map, and the waystones that travel on it. */
    MAP("map", null, true, Window.ScreenFill.FULL, true),
    /** The journal, and the missive boards and letters that hand out quests. */
    QUEST_JOURNAL("quest_journal", null, true, Window.ScreenFill.NONE, true),
    FELLOWSHIPS("fellowships", null, true, Window.ScreenFill.NONE, true),
    /** The characters you play and their profiles. */
    PROFILE("profile", null, true, Window.ScreenFill.NONE, true),
    /** Client Settings, Server Settings, the HUD Placement page, the Motion Lab. */
    SETTINGS("settings", null, true, Window.ScreenFill.NONE, true);

    private final String id;
    private final PageCategory parent;
    private final boolean ownView;
    private final Window.ScreenFill firstFill;
    private final boolean firstLocked;

    PageCategory(String id, PageCategory parent, boolean ownView,
                 Window.ScreenFill firstFill, boolean firstLocked) {
        this.id = id;
        this.parent = parent;
        this.ownView = ownView;
        this.firstFill = firstFill;
        this.firstLocked = firstLocked;
    }

    /** Its code name, as the layout file keeps it: {@code channels}, {@code map}. */
    public String id() {
        return this.id;
    }

    /** The category a code name names; null for none. */
    public static PageCategory fromId(String id) {
        for (PageCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    /** The category this one is a subcategory of; null for one of its own. */
    public PageCategory parent() {
        return this.parent;
    }

    /**
     * The category whose view and windows this one's pages share: itself,
     * or the channels for whispers.
     */
    public PageCategory home() {
        return this.ownView ? this : this.parent;
    }

    /** The part of the screen its first window fills: the whole of it for the map, none for the rest. */
    public Window.ScreenFill firstFill() {
        return this.firstFill;
    }

    /**
     * Whether its first window opens locked: every category's but the
     * New Page's, whose page picked takes the New Page's place there.
     */
    public boolean firstLocked() {
        return this.firstLocked;
    }

    /**
     * Whether it is a view of its own, which a key and a button on the
     * Views sub-window swap the screen to: the Lost Tales Menu and every
     * category but the New Page and the whispers, which stand with the
     * channels.
     */
    public boolean isView() {
        return this.ownView && this != NEW_PAGE;
    }

    /** Every view, in the order the Views sub-window shows their buttons: the Lost Tales Menu first. */
    public static java.util.List<PageCategory> views() {
        java.util.List<PageCategory> views =
                new java.util.ArrayList<PageCategory>();
        for (PageCategory category : values()) {
            if (category.isView()) {
                views.add(category);
            }
        }
        return views;
    }

    /** Its name, as the {@code +} heads its pages. */
    public String title() {
        return StatCollector.translateToLocal(
                "gui.losttales.window.category." + this.id);
    }
}
