package com.ninuna.losttales.client.window;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.StatCollector;

/**
 * One thing a window holds: a conversation or a page.
 * A window keeps its tabs in a row and shows the one in front; the tab
 * says how it looks in that row, its words, its colour, its icon and
 * what waits in the icon's corner, and each kind of tab lives with the
 * system it belongs to. Tabs are values: two tabs with the same id are
 * the same tab.
 */
public abstract class WindowPage {
    /** Reads a tab back from the id the layout file keeps it by. */
    public interface Reader {
        /** The tab {@code id} names; null for an id this reader does not know. */
        WindowPage read(String id);
    }

    private static final List<Reader> READERS = new CopyOnWriteArrayList<Reader>();

    /** Adds a kind of tab the layout file can name. */
    public static void addReader(Reader reader) {
        if (reader != null && !READERS.contains(reader)) {
            READERS.add(reader);
        }
    }

    /** The tab an id names, asked of every kind in turn; null for an unknown one. */
    public static WindowPage fromId(String id) {
        if (id == null) {
            return null;
        }
        for (Reader reader : READERS) {
            WindowPage tab = reader.read(id);
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

    /** Whether nothing typed in the tab can be sent: its bar's tab button names it in italics. */
    public boolean isReadOnly() {
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

    /** Whether the tab is a console, which the command key's view shows and the chat key's does not. */
    public boolean isConsole() {
        return false;
    }

    /* ---- What the window's tool strip offers while the tab is in front ---- */

    /**
     * The tab's own options: the rows of its options menu (the three dots
     * on its tab, a right-click on it) and the buttons of its window's
     * tool strip alike ({@link PageOption}).
     */
    public List<PageOption> options() {
        return Collections.emptyList();
    }

    /**
     * The settings of the tab's kind (Chat, Quest, Map, Motion Settings),
     * which the tool strip's cog opens and the tab's options offer after
     * its own rows; every conversation shares Chat Settings. Null for a
     * tab with none.
     */
    public Settings.Place settingsPlace() {
        return null;
    }

    /**
     * Whether the tab has any option, asked every frame to grey the three
     * dots of a tab with nothing to choose; a tab whose options cost
     * something to build answers without building them.
     */
    public boolean hasOptions() {
        return !options().isEmpty();
    }

    /**
     * One of its options taken, on its row or its button, or one of a
     * pick's words in the pick's sub-window. Answers whether the menu
     * stays open.
     */
    public boolean takeOption(String id) {
        return false;
    }

    /**
     * What the tab's help says, behind the question mark at the end of
     * the tool strip and under F1: its guide and its own keys.
     */
    public PageHelp help() {
        return new PageHelp(null, null);
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

    /**
     * Puts back what the tab lays out in {@code window} as it first was —
     * its panel, its member list and that list's width — as the window's
     * layout is reset.
     */
    public void resetIn(Window window) {}

    /** What the well says while nothing is typed in it. */
    public String searchPrompt() {
        return "";
    }

    /**
     * Why the well has nothing to search while the tab is in front, for
     * its tip; empty while it searches. A well with nothing to do stays,
     * greyed.
     */
    public String searchUnavailable() {
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

    /**
     * What the options button says under the pointer, and what the menu
     * it opens is called: {@code Global Chat Options}, {@code Map Options}.
     */
    public final String optionsTitle() {
        return StatCollector.translateToLocalFormatted(
                "gui.losttales.window.page.options", title());
    }

    @Override
    public String toString() {
        return id();
    }
}
