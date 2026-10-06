package com.ninuna.losttales.client.window;

import net.minecraft.util.StatCollector;

/**
 * What kind of page a page is. The {@code +} lists the pages by category,
 * each one folding away; a key shows its category's pages, its view; a page
 * opens in a window of its category; and a category with no window opens
 * its first window, at the category's own place and locked: the map
 * filling the screen, every other category in the middle at two thirds.
 *
 * <p>Whispers and the consoles are the channels' subcategories. Whispers
 * stand with the channels, in their view and their windows; the consoles
 * have a view and windows of their own, the command key's.</p>
 */
public enum PageCategory {
    /** The channels: Global, OOC, a faction's, a fellowship's, a server's own. */
    CHANNELS("channels", null, true, Window.ScreenFill.NONE),
    /** The Console and the Server Log, the command key's. */
    CONSOLES("consoles", CHANNELS, true, Window.ScreenFill.NONE),
    /** Whispers, with players and with NPCs. */
    WHISPERS("whispers", CHANNELS, false, Window.ScreenFill.NONE),
    /** The map, and the waystones that travel on it. */
    MAP("map", null, true, Window.ScreenFill.FULL),
    /** The journal, and the missive boards and letters that hand out quests. */
    QUEST_JOURNAL("quest_journal", null, true, Window.ScreenFill.NONE),
    FELLOWSHIPS("fellowships", null, true, Window.ScreenFill.NONE),
    /** The characters you play and their profiles. */
    PROFILE("profile", null, true, Window.ScreenFill.NONE),
    /** Client Settings, Server Settings, the HUD Placement page, the Motion Lab. */
    SETTINGS("settings", null, true, Window.ScreenFill.NONE);

    private final String id;
    private final PageCategory parent;
    private final boolean ownView;
    private final Window.ScreenFill firstFill;

    PageCategory(String id, PageCategory parent, boolean ownView,
                 Window.ScreenFill firstFill) {
        this.id = id;
        this.parent = parent;
        this.ownView = ownView;
        this.firstFill = firstFill;
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

    /** Its name, as the {@code +} heads its pages. */
    public String title() {
        return StatCollector.translateToLocal(
                "gui.losttales.window.category." + this.id);
    }
}
