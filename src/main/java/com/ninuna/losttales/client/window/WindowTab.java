package com.ninuna.losttales.client.window;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * One thing a window holds: a conversation, the quest journal, the party.
 * A window keeps its tabs in a row and shows the one in front; the tab
 * says how it looks in that row, its words, its colour, its icon and
 * what waits in the icon's corner, and each kind of tab lives with the
 * system it belongs to. Tabs are values: two tabs with the same id are
 * the same tab.
 */
public abstract class WindowTab {
    /** Reads a tab back from the id the layout file keeps it by. */
    public interface Reader {
        /** The tab {@code id} names; null for an id this reader does not know. */
        WindowTab read(String id);
    }

    private static final List<Reader> READERS = new CopyOnWriteArrayList<Reader>();

    /** Adds a kind of tab the layout file can name. */
    public static void addReader(Reader reader) {
        if (reader != null && !READERS.contains(reader)) {
            READERS.add(reader);
        }
    }

    /** The tab an id names, asked of every kind in turn; null for an unknown one. */
    public static WindowTab fromId(String id) {
        if (id == null) {
            return null;
        }
        for (Reader reader : READERS) {
            WindowTab tab = reader.read(id);
            if (tab != null) {
                return tab;
            }
        }
        return null;
    }

    /** The id the layout file keeps the tab by: {@code global}, {@code page:journal}. */
    public abstract String id();

    /** The words on the tab. */
    public abstract String title();

    /** The colour the tab wears: its accent, its glow and its lit words. */
    public abstract int tone();

    /** Whether the tab shows an icon before its words. */
    public boolean hasIcon() {
        return true;
    }

    /**
     * Draws the icon with its top-left at the point, {@link TabIcons#SIZE}
     * square, wearing {@code mark} in its corner.
     */
    public abstract void drawIcon(Minecraft minecraft, float x, float y,
                                  int alpha, TabMark mark);

    /** What waits in the icon's corner. */
    public TabMark mark() {
        return TabMark.NONE;
    }

    /** Whether the tab holds words not sent yet: the row shows its draft mark. */
    public boolean hasDraft() {
        return false;
    }

    /** Whether the tab's news is kept out of the way: the row writes its name in italics. */
    public boolean isMuted() {
        return false;
    }

    /**
     * Whether the layout file keeps the tab where it stands. A
     * conversation that ends with the session is left out of it.
     */
    public boolean isKeptInLayout() {
        return true;
    }

    /** Whether the tab can be shown now; one that cannot waits in its window unseen. */
    public boolean isAvailable() {
        return true;
    }

    /* ---- What the window's tool strip offers while the tab is in front ---- */

    /**
     * The rows the tab puts in its menu behind the tool strip's cog: a
     * page's choices, a conversation's switches. The menu adds a window of
     * its own for the tab after them.
     */
    public List<MenuWindow.Entry> menuRows() {
        return Collections.emptyList();
    }

    /** One of its menu's rows taken; answers whether the menu stays open. */
    public boolean takeMenuRow(String id) {
        return false;
    }

    /** The panel button at the strip's left end; null for none. */
    public ToolStrip.Panel panel() {
        return null;
    }

    /** Whether the panel is out in {@code window}. */
    public boolean isPanelOut(Window window) {
        return false;
    }

    /** Drives the panel out of {@code window}, or back in. */
    public void togglePanel(Window window) {}

    /** Whether the strip offers the member list's button. */
    public boolean hasMemberList() {
        return false;
    }

    /** Whether the member list is out in {@code window}. */
    public boolean isMemberListOut(Window window) {
        return false;
    }

    /** Puts the member list away in {@code window}, or brings it out. */
    public void toggleMemberList(Window window) {}

    /** What the well says while nothing is typed in it. */
    public String searchPrompt() {
        return "";
    }

    /**
     * Whether a standing search is walked, match by match, with the
     * chevrons; else the well only counts what it found.
     */
    public boolean walksSearch() {
        return false;
    }

    /** How many things a standing search found. */
    public int searchFound() {
        return 0;
    }

    /** The count a standing search shows in the well. */
    public String searchCount() {
        return String.valueOf(Math.max(0, searchFound()));
    }

    /** What the cog says under the pointer. */
    public String settingsTip() {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.page.settings", title());
    }

    @Override
    public String toString() {
        return id();
    }
}
