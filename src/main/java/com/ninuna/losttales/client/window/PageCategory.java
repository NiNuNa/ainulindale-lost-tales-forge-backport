package com.ninuna.losttales.client.window;

import net.minecraft.util.StatCollector;

/**
 * What kind of page a page is. The New Page lists the pages by
 * category; a key shows its category's pages, its view; a page
 * opens in a window of its category; and a category with no window opens
 * its first window, at the category's own place and locked but the
 * menu's: the map and the menu filling the screen, every other category in
 * the middle at two thirds.
 *
 * <p>Whispers and the consoles are the channels' subcategories. Whispers
 * stand with the channels, in their view and their windows; the consoles
 * have a view and windows of their own, the command key's.</p>
 */
public enum PageCategory {
    /**
     * The New Page, every page's way in: its key's first window
     * fills the screen; the {@code +} opens it as a window's new page.
     */
    NEW_PAGE("new_page", null, true, Window.ScreenFill.FULL, false),
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

    /** The part of the screen its first window fills: the whole of it for the map and the menu, none for the rest. */
    public Window.ScreenFill firstFill() {
        return this.firstFill;
    }

    /**
     * Whether its first window opens locked: every category's but the
     * menu's, whose page picked takes the menu's place there.
     */
    public boolean firstLocked() {
        return this.firstLocked;
    }

    /** Its name, as the {@code +} heads its pages. */
    public String title() {
        return StatCollector.translateToLocal(
                "gui.losttales.window.category." + this.id);
    }
}
