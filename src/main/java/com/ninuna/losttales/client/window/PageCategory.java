package com.ninuna.losttales.client.window;

import net.minecraft.util.StatCollector;

/**
 * What kind of page a page is. The New Page lists the pages by category,
 * and every category but the New Page and the whispers has a view of its
 * own ({@link Views#of}), which its key opens: the view a page the game
 * opens by itself goes to.
 *
 * <p>Whispers and the consoles are the channels' subcategories. Whispers
 * stand with the channels, in their view; the consoles have a view of
 * their own, the command key's.</p>
 */
public enum PageCategory {
    /**
     * The New Page, every page's way in: the {@code +} opens it as a
     * window's new page, and a custom view opens with one.
     */
    NEW_PAGE("new_page", null, true),
    /** The channels: Global, OOC, a faction's, a fellowship's, a server's own. */
    CHANNELS("channels", null, true),
    /** The Console and the Server Log, the command key's. */
    CONSOLES("consoles", CHANNELS, true),
    /** Whispers, with players and with NPCs. */
    WHISPERS("whispers", CHANNELS, false),
    /** The map, and the waystones that travel on it. */
    MAP("map", null, true),
    /** The journal, and the missive boards and letters that hand out quests. */
    QUEST_JOURNAL("quest_journal", null, true),
    FELLOWSHIPS("fellowships", null, true),
    /** The characters you play and their profiles. */
    PROFILE("profile", null, true),
    /** Client Settings, Server Settings, the HUD Placement page, the Motion Lab. */
    SETTINGS("settings", null, true);

    private final String id;
    private final PageCategory parent;
    private final boolean ownView;

    PageCategory(String id, PageCategory parent, boolean ownView) {
        this.id = id;
        this.parent = parent;
        this.ownView = ownView;
    }

    /** Its code name, as the layout file keeps it: {@code channels}, {@code map}. */
    public String id() {
        return this.id;
    }

    /** The category this one is a subcategory of; null for one of its own. */
    public PageCategory parent() {
        return this.parent;
    }

    /**
     * The category whose view this one's pages go to: itself, or the
     * channels for whispers.
     */
    public PageCategory home() {
        return this.ownView ? this : this.parent;
    }

    /**
     * Whether it has a view of its own: every category but the New Page
     * and the whispers, which stand with the channels.
     */
    public boolean isView() {
        return this.ownView && this != NEW_PAGE;
    }

    /** Its name, as the {@code +} heads its pages and its view is called. */
    public String title() {
        return StatCollector.translateToLocal(
                "gui.losttales.window.category." + this.id);
    }
}
